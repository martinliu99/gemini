/*
 * Copyright © 2023, the original author or authors. All Rights Reserved.
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

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.AdvisorContext;
import io.gemini.aop.factory.support.AdviceMethodSpec.AspectJMethodSpec;
import io.gemini.api.aop.Advice;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
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
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
interface AdviceClassMaker {

    List<Class<? extends Annotation>> ADVICE_ANNOTATIONS = Arrays.asList(
            Before.class, After.class, 
            AfterReturning.class, AfterThrowing.class, 
            Around.class);


    AspectJMethodSpec getAspectJMethodSpec();

    Class<? extends Advice> make(AdvisorContext advisorContext);


    class ByteBuddyMaker implements AdviceClassMaker {

        private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorRepository.class);

        private final String adviceClassName;
        private final AspectJMethodSpec aspectJMethodSpec;

        private ConcurrentMap<ClassLoader, WeakReference<Class<? extends Advice>>> adviceClassRefMap;
        private DynamicType.Unloaded<? extends Advice> adviceClassUnloaded;


        public ByteBuddyMaker(String adviceClassName, AspectJMethodSpec aspectJMethodSpec) {
            this.adviceClassName = adviceClassName;
            this.aspectJMethodSpec = aspectJMethodSpec;

            this.adviceClassRefMap = new ConcurrentReferenceHashMap<>();
        }


        public String getAdviceClassName() {
            return adviceClassName;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public AspectJMethodSpec getAspectJMethodSpec() {
            return this.aspectJMethodSpec;
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        public Class<? extends Advice> make(AdvisorContext advisorContext) {
            ClassLoader cacheKey = advisorContext.getClassLoader();

            // 1.try to get advice class from local cache.
            if(adviceClassRefMap.containsKey(cacheKey) == false) {
                adviceClassRefMap.computeIfAbsent(
                        cacheKey, 
                        key -> {
                            // try to generate advice class
                            long startedAt = System.nanoTime();
                            this.adviceClassUnloaded = ByteCodeGenerator.INSTANCE.make(advisorContext, this);

                            Class<? extends Advice> adviceClass = adviceClassUnloaded
                                    .load(cacheKey, new ClassLoadingStrategy.ForUnsafeInjection())
                                    .getLoaded();
                            LOGGER.info("Took '{}' seconds to inject advisorClass '{}' into classloader '{}'.", 
                                    (System.nanoTime() - startedAt) / 1e9, adviceClassName, cacheKey);
                            return new WeakReference<Class<? extends Advice>>(adviceClass);
                        }
                );
            }

            WeakReference<Class<? extends Advice>> adviceClassRef = adviceClassRefMap.get(cacheKey);
            Class<? extends Advice> adviceClass = adviceClassRef.get();
            if(adviceClass != null)
                return adviceClass;

            try {
                adviceClass = (Class<? extends Advice>) cacheKey.loadClass(adviceClassName);
                adviceClassRefMap.putIfAbsent(cacheKey, new WeakReference<Class<? extends Advice>>(adviceClass));
            } catch(Throwable t) { /* ignored */ }

            return adviceClass;
        }

    }


    enum ByteCodeGenerator {

        INSTANCE
        ;

        private static final TypeDescription JOINPOINT_TYPE = AspectJMethodSpec.JOINPOINT_TYPE;
        private static final TypeDescription MUTABLE_JOINPOINT_TYPE = AspectJMethodSpec.MUTABLE_JOINPOINT_TYPE;

        private static final TypeDescription AROUND_ADVICE_TYPE = TypeDescription.ForLoadedType.of(Advice.Around.class);
        private static final String AROUND_ADVICE_METHOD_NAME = AROUND_ADVICE_TYPE.getSimpleName().toLowerCase();

        private static final TypeDescription BEFORE_ADVICE_TYPE = TypeDescription.ForLoadedType.of(Advice.Before.class);
        private static final String BEFORE_ADVICE_METHOD_NAME = BEFORE_ADVICE_TYPE.getSimpleName().toLowerCase();

        private static final TypeDescription AFTER_ADVICE_TYPE = TypeDescription.ForLoadedType.of(Advice.After.class);
        private static final String AFTER_ADVICE_METHOD_NAME = AFTER_ADVICE_TYPE.getSimpleName().toLowerCase();

        private static final String TARGET_FILED = "target";
        private static final Constructor<Object> OBJECT_DEFAULT_CONSTRUCTOR;


        static {
            Constructor<Object> constructor = null;
            try {
                constructor = Object.class.getDeclaredConstructor();
            } catch (Throwable e) { /* ignored */ }
            OBJECT_DEFAULT_CONSTRUCTOR = constructor;
        }

        @SuppressWarnings("unchecked")
        public DynamicType.Unloaded<? extends Advice> make(AdvisorContext advisorContext, ByteBuddyMaker classMaker) {
            AspectJMethodSpec aspectJMethodSpec = classMaker.aspectJMethodSpec;

            // 1.define class
            // prepare parent interfaces
            List<TypeDefinition> implementTypeDefinitions = new ArrayList<>(2);
            Generic parameterizedReturningType = aspectJMethodSpec.getParameterizedReturningType();
            Generic parameterizedThrowingType = aspectJMethodSpec.getParameterizedThrowingType();
            if(parameterizedReturningType != null) {
                if(aspectJMethodSpec.isAround() == true) {
                    implementTypeDefinitions.add(
                            TypeDescription.Generic.Builder.parameterizedType(AROUND_ADVICE_TYPE, parameterizedReturningType, parameterizedThrowingType).build() );
                } else if(aspectJMethodSpec.isBefore() == true) {
                    implementTypeDefinitions.add(
                            TypeDescription.Generic.Builder.parameterizedType(BEFORE_ADVICE_TYPE, parameterizedReturningType, parameterizedThrowingType).build() );
                } else {
                    implementTypeDefinitions.add(
                            TypeDescription.Generic.Builder.parameterizedType(AFTER_ADVICE_TYPE, parameterizedReturningType, parameterizedThrowingType).build() );
                }
            } else {
                if(aspectJMethodSpec.isAround() == true) {
                    implementTypeDefinitions.add(AROUND_ADVICE_TYPE);
                } else if(aspectJMethodSpec.isBefore() == true) {
                    implementTypeDefinitions.add(BEFORE_ADVICE_TYPE);
                } else {
                    implementTypeDefinitions.add(AFTER_ADVICE_TYPE);
                }
            }

            String adviceClassName = classMaker.adviceClassName;
            TypeDescription adviceTypeDescription = aspectJMethodSpec.getAdviceTypeDescription();

            DynamicType.Builder<?> builder = new ByteBuddy()
                    .subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                    .name(adviceClassName)
                    .modifiers(adviceTypeDescription.getModifiers() | Opcodes.ACC_SYNTHETIC)
                    .implement(implementTypeDefinitions)
                    .visit( new AsmVisitorWrapper.AbstractBase() {
                        @Override
                        public int mergeWriter(int flags) {
                            // auto-calculate stack frame map flag if needed
                            return advisorContext.isASMAutoCompute() ? flags | ClassWriter.COMPUTE_FRAMES : flags;
                        }

                        @Override
                        public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor,
                                net.bytebuddy.implementation.Implementation.Context implementationContext, TypePool typePool,
                                FieldList<FieldDescription.InDefinedShape> fields,
                                MethodList<?> methods, int writerFlags, int readerFlags) {
                            return classVisitor;
                        }
                    } )
                    ;


            // 2.define field
            builder = builder.defineField(TARGET_FILED, adviceTypeDescription, Modifier.PRIVATE | Modifier.FINAL);


            // 3.define constructor
            builder = builder
                    .defineConstructor(Modifier.PUBLIC)
                    .withParameter(adviceTypeDescription, TARGET_FILED)
                    .intercept(
                            MethodCall.invoke(OBJECT_DEFAULT_CONSTRUCTOR)
                            .andThen(
                                    FieldAccessor.ofField(TARGET_FILED).setsArgumentAt(0)
                            )
                    );


            // 4.define advice method
            String methodName = aspectJMethodSpec.isAround() 
                    ? AROUND_ADVICE_METHOD_NAME 
                    : (aspectJMethodSpec.isBefore() ? BEFORE_ADVICE_METHOD_NAME : AFTER_ADVICE_METHOD_NAME);
            builder = builder
                    .defineMethod(methodName, void.class, Modifier.PUBLIC)
                    .withParameter(
                            parameterizedReturningType != null
                                ? TypeDescription.Generic.Builder.parameterizedType(MUTABLE_JOINPOINT_TYPE, parameterizedReturningType, parameterizedThrowingType).build()
                                : MUTABLE_JOINPOINT_TYPE, 
                            "joinpoint"
                    )
            .throwing(Throwable.class)
            .intercept( new AspectJAdviceMethodImplementation(aspectJMethodSpec) )
            ;

            return (DynamicType.Unloaded<? extends Advice>) builder.make();
        }


        static class AspectJAdviceMethodImplementation implements Implementation, ByteCodeAppender {

            private static final TypeDescription OBJECT = TypeDescription.ForLoadedType.of(Object.class);
            private static final TypeDescription VOID = TypeDescription.ForLoadedType.of(void.class);

            private static final int PARAM_INDEX0_THIS_OBJECT = 0;
            private static final int PARAM_INDEX1_MUTABLE_JOINPOINT = 1;

            private static final MethodDescription.InDefinedShape GET_ARGUMENTS_METHOD = JOINPOINT_TYPE.getDeclaredMethods().filter(named("getArguments")).getOnly();
            private static final MethodDescription.InDefinedShape GET_STATIC_PART_METHOD = JOINPOINT_TYPE.getDeclaredMethods().filter(named("getStaticPart")).getOnly();
            private static final MethodDescription.InDefinedShape GET_THIS_OBJECT_METHOD = JOINPOINT_TYPE.getDeclaredMethods().filter(named("getThisObject")).getOnly();

            private static final MethodDescription.InDefinedShape GET_RETURNING_METHOD = MUTABLE_JOINPOINT_TYPE.getDeclaredMethods().filter(named("getReturning")).getOnly();
            private static final MethodDescription.InDefinedShape GET_THROWING_METHOD = MUTABLE_JOINPOINT_TYPE.getDeclaredMethods().filter(named("getThrowing")).getOnly();


            private final MethodDescription methodDescription;
            private final AspectJMethodSpec aspectJMethodSpec;


            /**
             * @param aspectJMethodSpec
             */
            public AspectJAdviceMethodImplementation(AspectJMethodSpec aspectJMethodSpec) {
                this.aspectJMethodSpec = aspectJMethodSpec;
                this.methodDescription = aspectJMethodSpec.getAdviceMethodDescription();
            }

            @Override
            public InstrumentedType prepare(InstrumentedType instrumentedType) {
                return instrumentedType;
            }

            @Override
            public ByteCodeAppender appender(Target implementationTarget) {
                return this;
            }

            @Override
            public Size apply(MethodVisitor methodVisitor, Context implementationContext, MethodDescription instrumentedMethod) {
                TypeDescription instrumentedType = instrumentedMethod.getDeclaringType().asErasure();
                int localVariableSize = instrumentedMethod.getStackSize();
                int operandsStackSize = 0;

                // 1.decide AfterReturning or AfterThrowing Advice invocation
                final Label nullCheck = new Label();
                if(aspectJMethodSpec.isAfterReturning() == true || aspectJMethodSpec.isAfterThrowing() == true) {
                    methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MUTABLE_JOINPOINT_TYPE.getInternalName(), 
                            GET_THROWING_METHOD.getInternalName(), GET_THROWING_METHOD.getDescriptor(), true);

                    // if(throwing == null) invoke AfterReturning Advice
                    if(aspectJMethodSpec.isAfterReturning() == true)
                        methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, nullCheck);
                    // if(throwing != null) invoke AfterThrowing Advice
                    if(aspectJMethodSpec.isAfterThrowing() == true)
                        methodVisitor.visitJumpInsn(Opcodes.IFNULL, nullCheck);
                }


                // 2.declare and assign argument local variable
                // Object[] arguments = joinpoint.getArguments()
                int argumentsVariableOffset = -1;
                if(aspectJMethodSpec.getPointcutParameterNames().size() > 0) {
                    methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, JOINPOINT_TYPE.getInternalName(), 
                            GET_ARGUMENTS_METHOD.getInternalName(), GET_ARGUMENTS_METHOD.getDescriptor(), true);

                    argumentsVariableOffset = localVariableSize++;
                    methodVisitor.visitVarInsn(Opcodes.ASTORE, argumentsVariableOffset);
                }


                // 3.invoke AspectJAdvice method
                // invoke delegatee.method(joinpoint, ...)
                // 3.1.push target field for non-static method
                if(methodDescription.isStatic() == false) {
                    methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX0_THIS_OBJECT);

                    FieldDescription.InDefinedShape targetField = instrumentedType.getDeclaredFields().filter(named(TARGET_FILED)).getOnly();
                    methodVisitor.visitFieldInsn(Opcodes.GETFIELD, instrumentedType.asErasure().getInternalName(), 
                            targetField.getInternalName(), targetField.getDescriptor());

                    operandsStackSize++;
                }

                // 3.2.push arguments based on binder type
                Map<String, InDefinedShape> parameterDescriptionMap = aspectJMethodSpec.getParameterDescriptionMap();
                int paramIndex = 0;
                for(Entry<String, NamedPointcutParameter> entry : aspectJMethodSpec.getPointcutParameters().entrySet()) {
                    String parameterName = entry.getKey();
                    TypeDescription parameterType = parameterDescriptionMap.get(parameterName).getType().asErasure();
                    Generic parameterGeneric = parameterDescriptionMap.get(parameterName).getType();
                    NamedPointcutParameter pointcutParameter = entry.getValue();

                    paramIndex++;
                    operandsStackSize++;
                    switch(pointcutParameter.getParamType()) {
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
                            if(aspectJMethodSpec.isVoidReturningOfTargetMethod()) {
                                Assigner.DEFAULT.assign(VOID.asGenericType(), parameterGeneric, Typing.DYNAMIC).apply(methodVisitor, implementationContext);
                            } else {
                                methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                                methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MUTABLE_JOINPOINT_TYPE.getInternalName(), 
                                        GET_RETURNING_METHOD.getInternalName(), GET_RETURNING_METHOD.getDescriptor(), true);
                                Assigner.DEFAULT.assign(OBJECT.asGenericType(), parameterGeneric, Typing.DYNAMIC).apply(methodVisitor, implementationContext);

                                if(parameterGeneric.getStackSize().getSize() > 1)
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
                                    GET_THIS_OBJECT_METHOD.getInternalName(), GET_THIS_OBJECT_METHOD.getDescriptor(), true);
                            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, parameterType.getInternalName());

                            break;
                        }
                        case ARGS_VAR: {
                            methodVisitor.visitVarInsn(Opcodes.ALOAD, argumentsVariableOffset);
                            IntegerConstant.forValue(pointcutParameter.getArgsIndex()).apply(methodVisitor, implementationContext);
                            methodVisitor.visitInsn(Opcodes.AALOAD);
                            Assigner.DEFAULT.assign(OBJECT.asGenericType(), parameterGeneric, Typing.DYNAMIC).apply(methodVisitor, implementationContext);

                            operandsStackSize++;
                            if(parameterGeneric.getStackSize().getSize() == 1 && paramIndex < aspectJMethodSpec.getPointcutParameters().size())
                                operandsStackSize--;
                            break;
                        }
                        default:
                            break;
                    }
                }

                // 3.3.invoke delegatee.method(joinpoint, ...)
                MethodInvocation.invoke(methodDescription).apply(methodVisitor, implementationContext);

                // 3.4.pop returning if exists
                Generic returningType = methodDescription.getReturnType();
                int stackSize = returningType.getStackSize().getSize();
                if(stackSize > 0) {
                    methodVisitor.visitInsn(stackSize == 2 ? Opcodes.POP2 : Opcodes.POP);
                }


                // 4.mark method return label
                if(aspectJMethodSpec.isAfterReturning() == true || aspectJMethodSpec.isAfterThrowing() == true) {
                    methodVisitor.visitLabel(nullCheck);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);     // calculate stack frame map
                }


                // 5.return
                methodVisitor.visitInsn(Opcodes.RETURN);

                return new Size(operandsStackSize, localVariableSize);      // adjust local variable table size
            }
        }
    }
}
