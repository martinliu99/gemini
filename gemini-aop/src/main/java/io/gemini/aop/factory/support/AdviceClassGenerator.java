/*
 * Copyright © 2023 - present, the original author or authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gemini.aop.factory.support;

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.gemini.aop.AdviceKind.AspectJAdviceKind;
import io.gemini.aop.factory.support.AdviceSpec.AspectJAdviceSpec;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.TypeResolutionStrategy;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.implementation.bytecode.constant.IntegerConstant;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

/**
 * Bytecode generator that produces a concrete {@link io.gemini.api.aop.Advice} class
 * wrapping an AspectJ advice method.
 * <p>
 * The generated class holds a reference to the AspectJ aspect instance and implements
 * the appropriate {@link io.gemini.api.aop.Advice.Before}, {@link io.gemini.api.aop.Advice.After},
 * or {@link io.gemini.api.aop.Advice.Around} interface. The advice method body is generated
 * via ASM to extract pointcut-bound parameters from the {@link io.gemini.api.aop.Joinpoint.MutableJoinpoint}
 * and forward them to the original AspectJ method.
 * </p>
 *
 * @author   martin.liu
 */
enum AdviceClassGenerator {

    INSTANCE
    ;

    private static final TypeDescription JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.class);
    private static final TypeDescription MUTABLE_JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.MutableJoinpoint.class);

    private static final TypeDescription AROUND_ADVICE_TYPE = TypeDescription.ForLoadedType.of(Advice.Around.class);
    private static final String AROUND_ADVICE_METHOD_NAME = AROUND_ADVICE_TYPE.getSimpleName().toLowerCase();

    private static final TypeDescription BEFORE_ADVICE_TYPE = TypeDescription.ForLoadedType.of(Advice.Before.class);
    private static final String BEFORE_ADVICE_METHOD_NAME = BEFORE_ADVICE_TYPE.getSimpleName().toLowerCase();

    private static final TypeDescription AFTER_ADVICE_TYPE = TypeDescription.ForLoadedType.of(Advice.After.class);
    private static final String AFTER_ADVICE_METHOD_NAME = AFTER_ADVICE_TYPE.getSimpleName().toLowerCase();

    private static final String DELEGATE_CLASS_FILED = "delegateClass";
    private static final String DELEGATE_OBJECT_FILED = "delegateObject";
    private static final Constructor<Object> OBJECT_DEFAULT_CONSTRUCTOR;


    static {
        Constructor<Object> constructor = null;
        try {
            constructor = Object.class.getDeclaredConstructor();
        } catch (Exception e) { /* ignored */ }
        OBJECT_DEFAULT_CONSTRUCTOR = constructor;
    }

    @SuppressWarnings("unchecked")
    public DynamicType.Unloaded<? extends Advice> make(AspectJAdviceSpec adviceSpec, boolean autoComputeAsm) {
        AspectJAdviceKind adviceKind = adviceSpec.getAdviceKind();

        // 1.define class
        // prepare parent interfaces
        List<TypeDefinition> implementTypeDefinitions = new ArrayList<>(2);
        Generic parameterizedReturningType = adviceSpec.getParameterizedReturningType();
        Generic parameterizedThrowingType = adviceSpec.getParameterizedThrowingType();
        if (parameterizedReturningType != null) {
            if (adviceKind.isAround() == true) {
                implementTypeDefinitions.add(
                        TypeDescription.Generic.Builder.parameterizedType(AROUND_ADVICE_TYPE, parameterizedReturningType, parameterizedThrowingType).build() );
            } else if (adviceKind.isBefore() == true) {
                implementTypeDefinitions.add(
                        TypeDescription.Generic.Builder.parameterizedType(BEFORE_ADVICE_TYPE, parameterizedReturningType, parameterizedThrowingType).build() );
            } else {
                implementTypeDefinitions.add(
                        TypeDescription.Generic.Builder.parameterizedType(AFTER_ADVICE_TYPE, parameterizedReturningType, parameterizedThrowingType).build() );
            }
        } else {
            if (adviceKind.isAround() == true) {
                implementTypeDefinitions.add(AROUND_ADVICE_TYPE);
            } else if (adviceKind.isBefore() == true) {
                implementTypeDefinitions.add(BEFORE_ADVICE_TYPE);
            } else {
                implementTypeDefinitions.add(AFTER_ADVICE_TYPE);
            }
        }

        TypeDescription adviceType = adviceSpec.getDeclaringType();
        String adviceClassName = adviceSpec.getAdviceClassName();

        DynamicType.Builder<?> builder = new ByteBuddy()
                .subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                .name(adviceClassName)
                .modifiers(adviceType.getModifiers() | Opcodes.ACC_SYNTHETIC)
                .implement(implementTypeDefinitions)
                .visit( new AsmVisitorWrapper.ForDeclaredMethods()
                        .readerFlags(ClassReader.EXPAND_FRAMES)
                        .writerFlags(autoComputeAsm ? ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS : ClassWriter.COMPUTE_MAXS)
                )
                ;


        // 2.define field
        builder = builder.defineField(DELEGATE_CLASS_FILED, adviceType, Modifier.STATIC | Modifier.PRIVATE | Modifier.FINAL);
        builder = builder.defineField(DELEGATE_OBJECT_FILED, adviceType, Modifier.PRIVATE | Modifier.FINAL);


        // 3.define constructor
        builder = builder
//                .define
                .defineConstructor(Modifier.PUBLIC)
                .withParameter(adviceType, DELEGATE_OBJECT_FILED)
                .intercept(
                        MethodCall.invoke(OBJECT_DEFAULT_CONSTRUCTOR)
                        .andThen(
                                FieldAccessor.ofField(DELEGATE_OBJECT_FILED).setsArgumentAt(0)
                        )
                );


        // 4.define advice method
        String methodName = adviceKind.isAround() 
                ? AROUND_ADVICE_METHOD_NAME 
                : (adviceKind.isBefore() ? BEFORE_ADVICE_METHOD_NAME : AFTER_ADVICE_METHOD_NAME);
        builder = builder
                .defineMethod(methodName, void.class, Modifier.PUBLIC)
                .withParameter(
                        parameterizedReturningType != null
                            ? TypeDescription.Generic.Builder.parameterizedType(MUTABLE_JOINPOINT_TYPE, parameterizedReturningType, parameterizedThrowingType).build()
                            : MUTABLE_JOINPOINT_TYPE, 
                        "joinpoint"
                )
        .throwing(Throwable.class)
        .intercept( new AspectJAdviceMethodImplementation(adviceSpec) )
        ;

        return (DynamicType.Unloaded<? extends Advice>) builder.make(TypeResolutionStrategy.Lazy.INSTANCE);
    }


    /**
     * ByteBuddy {@link Implementation} and {@link ByteCodeAppender} that generates the body
     * of the advice method in the dynamically created advice class.
     * Extracts pointcut-bound parameters from the joinpoint and invokes the original AspectJ method.
     */
    static class AspectJAdviceMethodImplementation implements Implementation, ByteCodeAppender {

        private static final TypeDescription OBJECT = TypeDescription.ForLoadedType.of(Object.class);
        private static final TypeDescription VOID = TypeDescription.ForLoadedType.of(void.class);

        private static final int PARAM_INDEX0_THIS_OBJECT = 0;
        private static final int PARAM_INDEX1_MUTABLE_JOINPOINT = 1;

        private static final MethodDescription.InDefinedShape GET_ARGUMENTS_METHOD = JOINPOINT_TYPE.getDeclaredMethods().filter(named("getArguments")).getOnly();
        private static final MethodDescription.InDefinedShape GET_STATIC_PART_METHOD = JOINPOINT_TYPE.getDeclaredMethods().filter(named("getStaticPart")).getOnly();
        private static final MethodDescription.InDefinedShape GET_TARGET_OBJECT_METHOD = JOINPOINT_TYPE.getDeclaredMethods().filter(named("getTargetObject")).getOnly();

        private static final MethodDescription.InDefinedShape GET_RETURNING_METHOD = MUTABLE_JOINPOINT_TYPE.getDeclaredMethods().filter(named("getReturning")).getOnly();
        private static final MethodDescription.InDefinedShape GET_THROWING_METHOD = MUTABLE_JOINPOINT_TYPE.getDeclaredMethods().filter(named("getThrowing")).getOnly();


        private final AspectJAdviceSpec adviceSpec;


        /**
         * @param adviceSpec
         */
        public AspectJAdviceMethodImplementation(AspectJAdviceSpec adviceSpec) {
            this.adviceSpec = adviceSpec;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InstrumentedType prepare(InstrumentedType instrumentedType) {
            return instrumentedType;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ByteCodeAppender appender(Target implementationTarget) {
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Size apply(MethodVisitor methodVisitor, Context implementationContext, 
                MethodDescription instrumentedMethod) {
            TypeDescription instrumentedType = instrumentedMethod.getDeclaringType().asErasure();
            AspectJAdviceKind adviceKind = adviceSpec.getAdviceKind();
            MethodDescription adviceMethod = adviceSpec.getAdviceMethod();
            Map<String, NamedPointcutParameter> namedPointcutParameters = adviceSpec.getNamedPointcutParameters();

            int localVariableSize = instrumentedMethod.getStackSize();
            int operandsStackSize = 0;

            // 1.decide AfterReturning or AfterThrowing Advice invocation
            final Label nullCheck = new Label();
            if (adviceKind.isAfterReturning() == true || adviceKind.isAfterThrowing() == true) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MUTABLE_JOINPOINT_TYPE.getInternalName(), 
                        GET_THROWING_METHOD.getInternalName(), GET_THROWING_METHOD.getDescriptor(), true);

                // if (throwing == null) invoke AfterReturning Advice
                if (adviceKind.isAfterReturning() == true)
                    methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, nullCheck);
                // if (throwing != null) invoke AfterThrowing Advice
                if (adviceKind.isAfterThrowing() == true)
                    methodVisitor.visitJumpInsn(Opcodes.IFNULL, nullCheck);
            }


            // 2.declare and assign argument local variable
            // Object[] arguments = joinpoint.getArguments()
            int argumentsVariableOffset = -1;
            if (namedPointcutParameters.size() > 0) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, JOINPOINT_TYPE.getInternalName(), 
                        GET_ARGUMENTS_METHOD.getInternalName(), GET_ARGUMENTS_METHOD.getDescriptor(), true);

                argumentsVariableOffset = localVariableSize++;
                methodVisitor.visitVarInsn(Opcodes.ASTORE, argumentsVariableOffset);
            }


            // 3.invoke AspectJAdvice method
            // invoke delegatee.method(joinpoint, ...)
            // 3.1.push target field for non-static method
            if (adviceMethod.isStatic() == false) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX0_THIS_OBJECT);

                FieldDescription.InDefinedShape targetField = instrumentedType.getDeclaredFields().filter(named(DELEGATE_OBJECT_FILED)).getOnly();
                methodVisitor.visitFieldInsn(Opcodes.GETFIELD, instrumentedType.asErasure().getInternalName(), 
                        targetField.getInternalName(), targetField.getDescriptor());

                operandsStackSize++;
            }

            // 3.2.push arguments based on binder type
            int paramIndex = 0;
            for (Entry<String, NamedPointcutParameter> entry : namedPointcutParameters.entrySet()) {
                NamedPointcutParameter pointcutParameter = entry.getValue();
                TypeDescription parameterType = pointcutParameter.getParamType().asErasure();

                paramIndex++;
                operandsStackSize++;
                switch (pointcutParameter.getParamCategory()) {
                    case JOINPOINT_PARAM:
                    case MUTABLE_JOINPOINT_PARAM:
                    case PROCEDDING_JOINPOINT_PARAM: {
                        // first parameter is Joinpoint
                        methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);

                        break;
                    }
                    case STATIC_PART_PARAM: {
                        // first parameter is StaticPart
                        methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, JOINPOINT_TYPE.getInternalName(), 
                                GET_STATIC_PART_METHOD.getInternalName(), GET_STATIC_PART_METHOD.getDescriptor(), true);

                        break;
                    }
                    case RETURNING_ANNOTATION: {
                        if (adviceSpec.isVoidReturning()) {
                            Assigner.DEFAULT.assign(VOID.asGenericType(), parameterType.asGenericType(), Typing.DYNAMIC).apply(methodVisitor, implementationContext);
                        } else {
                            methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MUTABLE_JOINPOINT_TYPE.getInternalName(), 
                                    GET_RETURNING_METHOD.getInternalName(), GET_RETURNING_METHOD.getDescriptor(), true);
                            Assigner.DEFAULT.assign(OBJECT.asGenericType(), parameterType.asGenericType(), Typing.DYNAMIC).apply(methodVisitor, implementationContext);

                            if (parameterType.getStackSize().getSize() > 1)
                                operandsStackSize++;
                        }

                        break;
                    }
                    case THROWING_ANNOTATION: {
                        methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MUTABLE_JOINPOINT_TYPE.getInternalName(), 
                                GET_THROWING_METHOD.getInternalName(), GET_THROWING_METHOD.getDescriptor(), true);
                        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, parameterType.getInternalName());

                        break;
                    }
                    case THIS_VAR:
                    case TARGET_VAR: {
                        methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MUTABLE_JOINPOINT_TYPE.getInternalName(), 
                                GET_TARGET_OBJECT_METHOD.getInternalName(), GET_TARGET_OBJECT_METHOD.getDescriptor(), true);
                        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, parameterType.getInternalName());

                        break;
                    }
                    case ARGS_VAR: {
                        methodVisitor.visitVarInsn(Opcodes.ALOAD, argumentsVariableOffset);
                        IntegerConstant.forValue(pointcutParameter.getArgsIndex()).apply(methodVisitor, implementationContext);
                        methodVisitor.visitInsn(Opcodes.AALOAD);
                        Assigner.DEFAULT.assign(OBJECT.asGenericType(), parameterType.asGenericType(), Typing.DYNAMIC).apply(methodVisitor, implementationContext);

                        operandsStackSize++;
                        if (parameterType.getStackSize().getSize() == 1 && paramIndex < namedPointcutParameters.size())
                            operandsStackSize--;
                        break;
                    }
                    default:
                        break;
                }
            }

            // 3.3.invoke delegatee.method(joinpoint, ...)
            MethodInvocation.invoke(adviceMethod).apply(methodVisitor, implementationContext);

            // 3.4.pop returning if exists
            Generic returningType = adviceMethod.getReturnType();
            int stackSize = returningType.getStackSize().getSize();
            if (stackSize > 0) {
                methodVisitor.visitInsn(stackSize == 2 ? Opcodes.POP2 : Opcodes.POP);
            }


            // 4.mark method return label
            if (adviceKind.isAfterReturning() == true || adviceKind.isAfterThrowing() == true) {
                methodVisitor.visitLabel(nullCheck);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);     // calculate stack frame map
            }


            // 5.return
            methodVisitor.visitInsn(Opcodes.RETURN);

            return new Size(operandsStackSize, localVariableSize);      // adjust local variable table size
        }
    }
}