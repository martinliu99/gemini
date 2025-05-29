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
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.AdvisorContext;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint;
import io.gemini.aspectj.weaver.BindingType;
import io.gemini.aspectj.weaver.ParameterBinding;
import io.gemini.aspectj.weaver.tools.ShadowMatch;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterDescription.InDefinedShape;
import net.bytebuddy.description.method.ParameterList;
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
interface AspectJAdvice {

    List<Class<? extends Annotation>> ADVICE_ANNOTATIONS = Arrays.asList(
            Before.class, After.class, AfterReturning.class, AfterThrowing.class, 
            Around.class);


    class MethodSpec extends AdviceAwareMethodMatcher.AdviceMethodMatcher.AbstractBase {

        private static final Logger LOGGER = LoggerFactory.getLogger(MethodSpec.class);

        private static final TypeDescription OBJECT = TypeDescription.ForLoadedType.of(Object.class);
        private static final TypeDescription VOID = TypeDescription.ForLoadedType.of(void.class);

        private static final TypeDescription JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.class);
        private static final TypeDescription MUTABLE_JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.MutableJoinpoint.class);

        private static final List<TypeDescription> ACCESSIBLE_OBJECTS = Arrays.asList( 
                TypeDescription.ForLoadedType.of(AccessibleObject.class),
                TypeDescription.ForLoadedType.of(Executable.class),
                TypeDescription.ForLoadedType.of(Constructor.class),
                TypeDescription.ForLoadedType.of(Method.class)
        );


        private final String pointcutExpression;

        private final AnnotationDescription annotationDescription;

        private final List<String> parameterNames;
        private final Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap;

        private List<String> pointcutParameterNames;
        private Map<String, ParameterBinding> parameterBindings;

        private final boolean isAround;
        private final boolean isBefore;
        private boolean isAfterReturning;
        private boolean isAfterThrowing;;

        private boolean isVoidReturningOfTargetMethod = false;


        public MethodSpec(String advisorName, 
                String pointcutExpression, MethodDescription adviceMethodDescription, 
                Class<? extends Annotation> annotationType, AnnotationDescription annotationDescription) {
            super(advisorName);

            this.pointcutExpression = pointcutExpression;
            this.adviceMethodDescription = adviceMethodDescription;
            this.annotationDescription = annotationDescription;

            ParameterList<ParameterDescription.InDefinedShape> parameters = adviceMethodDescription.asDefined().getParameters();
            this.parameterNames = this.parseParameterNames(adviceMethodDescription, this.annotationDescription, parameters);

            this.parameterDescriptionMap = this.createParameterDescriptionMap(parameters, this.parameterNames);

            this.parameterBindings = new LinkedHashMap<>();
            this.pointcutParameterNames = new ArrayList<>(parameterNames);
            this.resolveJoinpointParamBinding(adviceMethodDescription, 
                    parameterDescriptionMap, parameterBindings, pointcutParameterNames);

            this.isAround = Around.class == annotationType;
            this.isBefore = Before.class == annotationType;

            this.adviceReturningParameterType = this.resolveAdviceReturningParamBindings(adviceMethodDescription, 
                    annotationType, annotationDescription, 
                    parameterDescriptionMap, parameterBindings, pointcutParameterNames);

            this.adviceThrowingParameterType = this.resolveAdviceThrowingParamBindings(adviceMethodDescription, 
                    annotationType, annotationDescription, 
                    parameterDescriptionMap, parameterBindings, pointcutParameterNames);
        }

        private List<String> parseParameterNames(MethodDescription methodDescription, 
                AnnotationDescription annotationDescription, ParameterList<InDefinedShape> parameters) {
            List<String> parameterNames = new ArrayList<>(parameters.size());
            if(parameters.size() == 0)
                return parameterNames;

            ParameterDescription.InDefinedShape index0Param = parameters.get(0);

            // 1.parse 'argNames' value in annotation
            AnnotationValue<?, ?> annotationValue = annotationDescription.getValue("argNames");
            String argNamesStr = annotationValue.resolve(String.class).toString();
            if(StringUtils.hasText(argNamesStr)) {
                StringTokenizer st = new StringTokenizer(argNamesStr, ",");
                if(st.countTokens() != parameters.size() && st.countTokens() != parameters.size() - 1) {
                    LOGGER.warn("Ignored AspectJ advice method with parameters is inconsistent with 'argNames' attribute. \n"
                            + "  AdvisorSpec: {} \n  AdviceMethod: {} \n  ArgNames: {} \n", 
                            advisorName, 
                            MethodUtils.getMethodSignature(adviceMethodDescription),
                            argNamesStr
                    );

                    this.isValid = false;
                    return Collections.emptyList();
                }

                // first parameter should be joinpoint
                if(st.countTokens() == parameters.size() - 1) {
                    parameterNames.add(index0Param.getName());
                }
                while(st.hasMoreTokens()) {
                    parameterNames.add(st.nextToken().trim());
                }
                return parameterNames;
            }

            // 2.parse parameter names in MethodParameters section
            // validate parameter name for index0 parameter
            if(index0Param.getName().equals(index0Param.getActualName()) == false) {
                LOGGER.warn("Ignored AspectJ advice method without parameter reflection support and 'argNames' attribute. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n {} \n {} \n",
                        advisorName, 
                        MethodUtils.getMethodSignature(adviceMethodDescription), 
                        index0Param.getName(), index0Param.getActualName()
                );

                this.isValid = false;
                return Collections.emptyList();
            }

            return parameters.stream()
                    .map( p -> p.getName() )
                    .collect( Collectors.toList() );
        }

        private Map<String, ParameterDescription.InDefinedShape> createParameterDescriptionMap(
                ParameterList<InDefinedShape> parameters, List<String> parameterNames) {
            if(parameterNames.size() == 0)
                return Collections.emptyMap();

            Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap = new LinkedHashMap<>(parameters.size());

            // access by parameter index other than parameter name which might NOT contain in bytecode file
            for(int index = 0; index < parameters.size(); index++) {
                ParameterDescription.InDefinedShape paramType = parameters.get(index);
                parameterDescriptionMap.put(parameterNames.get(index), paramType);
            }

            return parameterDescriptionMap;
        }

        private void resolveJoinpointParamBinding(MethodDescription methodDescription, 
                Map<String, InDefinedShape> parameterDescriptionMap, 
                Map<String, ParameterBinding> parameterBindings, List<String> pointcutParameterNames) {
            if(parameterDescriptionMap.size() == 0) 
                return;

            // 1.bind first parameter
            ParameterDescription.InDefinedShape parameterDescription = methodDescription.asDefined().getParameters().get(0);
            String parameterName = parameterDescription.getName();
            TypeDescription parameterType = parameterDescription.getType().asErasure();

            if(JOINPOINT_TYPE.equals(parameterType)) {
                parameterBindings.put(parameterName, 
                        new ParameterBinding(parameterName, parameterType, BindingType.JOINPOINT_PARAM));
                pointcutParameterNames.remove(parameterName);
            } else if(MUTABLE_JOINPOINT_TYPE.equals(parameterType)) {
                parameterBindings.put(parameterName, 
                        new ParameterBinding(parameterName, parameterType, BindingType.MUTABLE_JOINPOINT_PARAM));
                pointcutParameterNames.remove(parameterName);

                this.doDiscoverJoinpointTypeArguments(parameterDescription.getType());
            } else if(ACCESSIBLE_OBJECTS.contains(parameterType)) {
                parameterBindings.put(parameterName, 
                        new ParameterBinding(parameterName, parameterType, BindingType.STATIC_PART_PARAM));
                pointcutParameterNames.remove(parameterName);
            }
        }

        private Generic resolveAdviceReturningParamBindings(MethodDescription methodDescription, 
                Class<? extends Annotation> annotationType, AnnotationDescription annotationDescription, 
                Map<String, InDefinedShape> parameterDescriptionMap, 
                Map<String, ParameterBinding> parameterBindings, List<String> pointcutParameterNames) {
            this.isAfterReturning = AfterReturning.class == annotationType;
            if(this.isAfterReturning == false)
                return null;

            // 2.index returning parameters
            AnnotationValue<?, ?> annotationValue = annotationDescription.getValue("returning");
            String returningParameter = annotationValue.resolve(String.class).trim();
            if(StringUtils.hasText(returningParameter) == false) 
                return null;

            if(parameterDescriptionMap.containsKey(returningParameter) == false) { 
                LOGGER.warn("Ignored AspectJ @AfterReturning advice method without 'returning' attribute. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    returning: {} \n",
                        advisorName,
                        MethodUtils.getMethodSignature(adviceMethodDescription),
                        returningParameter
                );

                this.isValid = false;
                return null;
            }

            parameterBindings.put(returningParameter, 
                    new ParameterBinding(returningParameter, parameterDescriptionMap.get(returningParameter).getType().asErasure(), BindingType.RETURNING_ANNOTATION));
            pointcutParameterNames.remove(returningParameter);

            return parameterDescriptionMap.get(returningParameter).getType();
        }

        private Generic resolveAdviceThrowingParamBindings(MethodDescription methodDescription, 
                Class<? extends Annotation> annotationType, AnnotationDescription annotationDescription, 
                Map<String, InDefinedShape> parameterDescriptionMap, 
                Map<String, ParameterBinding> parameterBindings, List<String> pointcutParameterNames) {
            this.isAfterThrowing = AfterThrowing.class == annotationType;
            if(this.isAfterThrowing == false)
                return null;

            // 3.index throwing parameters
            AnnotationValue<?, ?> annotationValue = annotationDescription.getValue("throwing");
            String throwingParameter = annotationValue.resolve(String.class).trim();

            if(StringUtils.hasText(throwingParameter) == false) 
                return null;

            if(parameterDescriptionMap.containsKey(throwingParameter) == false) {
                LOGGER.warn("Ignored AspectJ @AfterThrowing advice method without 'throwing' attribute. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    throwing: {} \n",
                        advisorName,
                        MethodUtils.getMethodSignature(adviceMethodDescription),
                        throwingParameter
                );

                this.isValid = false;
                return null;
            }

            parameterBindings.put(throwingParameter, 
                    new ParameterBinding(throwingParameter, parameterDescriptionMap.get(throwingParameter).getType().asErasure(), BindingType.THROWING_ANNOTATION));
            pointcutParameterNames.remove(throwingParameter);

            return parameterDescriptionMap.get(throwingParameter).getType();
        }


        public String getPointcutExpression() {
            return pointcutExpression;
        }

        public MethodDescription getAdviceMethodDescription() {
            return adviceMethodDescription;
        }

        public List<String> getPointcutParameterNames() {
            return Collections.unmodifiableList(this.pointcutParameterNames);
        }

        public List<TypeDescription> getPointcutParameterTypes() {
            return pointcutParameterNames.stream()
                    .map( p -> 
                        this.parameterDescriptionMap.get(p).getType().asErasure() )
                    .collect( Collectors.toList() );
        }

        public Map<String, ParameterBinding> getParameterBindings() {
            return parameterBindings;
        }


        @Override
        public boolean match(MethodDescription targetMethodDescription, ShadowMatch shadowMatch) {
            if(this.isValid == false)
                return false;

            // 1.match returning and throwing
            if(this.doMatchAdviceMethod(targetMethodDescription) == false) {
                return false;
            }


            // 2.match parameter count and type
            List<ParameterBinding> pointcutParameterBindings = shadowMatch.getParameterBindings();
            if(pointcutParameterBindings == null || pointcutParameterBindings.size() != this.pointcutParameterNames.size()) {
                LOGGER.warn("Ignored advice method with advice parameters is different to target method's resolved parameters. \n" 
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    AdviceParameters: {} \n  TargetMethod: {} \n    ResolvedParameters: {} \n",
                        advisorName,
                        MethodUtils.getMethodSignature(adviceMethodDescription), 
                        pointcutParameterNames,
                        MethodUtils.getMethodSignature(targetMethodDescription), 
                        pointcutParameterBindings == null ? null : pointcutParameterBindings.stream()
                                .map( p -> p.getName() )
                                .collect( Collectors.toList() )
                );

                return false;
            }

            for(ParameterBinding pointcutParameterBinding : pointcutParameterBindings) {
                String name = pointcutParameterBinding.getName();
                if(this.pointcutParameterNames.contains(name) == false) {
                    LOGGER.warn("Ignored advice method with advice parameters do not contain target method's resolved parameter '{}'. \n" 
                            + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    AdviceParameters: {} \n  TargetMethod: {} \n    ResolvedParameters: {} \n",
                            name, 
                            advisorName,
                            MethodUtils.getMethodSignature(adviceMethodDescription), 
                            pointcutParameterNames,
                            MethodUtils.getMethodSignature(targetMethodDescription), 
                            pointcutParameterBindings == null ? null : pointcutParameterBindings.stream()
                                    .map( p -> p.getName() )
                                    .collect( Collectors.toList() )
                    );

                    return false;
                }

                TypeDescription paramType = pointcutParameterBinding.getType();
                if(ClassUtils.isVisibleTo(paramType, targetMethodDescription.getDeclaringType().asErasure()) == false) {
                    LOGGER.warn("Ignored advice method referring to non public and non protected in the same package parameter type under Joinpoint ClassLoader. \n"
                            + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    parameter '{}': {} {} \n",
                            advisorName,
                            targetMethodDescription.toGenericString(),
                            name, paramType.getVisibility(), paramType );

                    return false;
                }

                this.parameterBindings.put(name, pointcutParameterBinding);
            }

            Generic returnType = targetMethodDescription.getReturnType();
            this.isVoidReturningOfTargetMethod = returnType.represents(void.class);

            return true;
        }
    }


    class ClassMaker {

        private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorRepository.class);

        private final String adviceClassName;
        private final TypeDescription aspectJTypeDescription;
        private final MethodSpec methodSpec;

        private ConcurrentMap<ClassLoader, WeakReference<Class<? extends Advice>>> adviceClassRefMap;
        private DynamicType.Unloaded<? extends Advice> adviceClassUnloaded;


        public ClassMaker(String adviceClassName, TypeDescription aspectJTypeDescription, MethodSpec methodSpec) {
            this.adviceClassName = adviceClassName;
            this.aspectJTypeDescription = aspectJTypeDescription;
            this.methodSpec = methodSpec;

            this.adviceClassRefMap = new ConcurrentReferenceHashMap<>();
        }


        public String getAdviceClassName() {
            return adviceClassName;
        }

        public TypeDescription getAspectJTypeDescription() {
            return aspectJTypeDescription;
        }

        public MethodSpec getMethodSpec() {
            return methodSpec;
        }

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

        private static final TypeDescription JOINPOINT_TYPE = MethodSpec.JOINPOINT_TYPE.asErasure();
        private static final TypeDescription MUTABLE_JOINPOINT_TYPE = MethodSpec.MUTABLE_JOINPOINT_TYPE.asErasure();

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
        public DynamicType.Unloaded<? extends Advice> make(AdvisorContext advisorContext, ClassMaker classMaker) {
            MethodSpec methodSpec = classMaker.methodSpec;

            // 1.define class
            // prepare parent interfaces
            List<TypeDefinition> implementTypeDefinitions = new ArrayList<>(2);
            if(methodSpec.parameterizedReturningType != null) {
                if(methodSpec.isAround == true) {
                    implementTypeDefinitions.add(
                            TypeDescription.Generic.Builder.parameterizedType(AROUND_ADVICE_TYPE, methodSpec.parameterizedReturningType, methodSpec.parameterizedThrowingType).build() );
                } else if(methodSpec.isBefore == true) {
                    implementTypeDefinitions.add(
                            TypeDescription.Generic.Builder.parameterizedType(BEFORE_ADVICE_TYPE, methodSpec.parameterizedReturningType, methodSpec.parameterizedThrowingType).build() );
                } else {
                    implementTypeDefinitions.add(
                            TypeDescription.Generic.Builder.parameterizedType(AFTER_ADVICE_TYPE, methodSpec.parameterizedReturningType, methodSpec.parameterizedThrowingType).build() );
                }
            } else {
                if(methodSpec.isAround == true) {
                    implementTypeDefinitions.add(AROUND_ADVICE_TYPE);
                } else if(methodSpec.isBefore == true) {
                    implementTypeDefinitions.add(BEFORE_ADVICE_TYPE);
                } else {
                    implementTypeDefinitions.add(AFTER_ADVICE_TYPE);
                }
            }

            String adviceClassName = classMaker.adviceClassName;
            TypeDescription aspectJTypeDescription = classMaker.aspectJTypeDescription;

            DynamicType.Builder<?> builder = new ByteBuddy()
                    .subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                    .name(adviceClassName)
                    .modifiers(aspectJTypeDescription.getModifiers() | Opcodes.ACC_SYNTHETIC)
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
            builder = builder.defineField(TARGET_FILED, aspectJTypeDescription, Modifier.PRIVATE | Modifier.FINAL);


            // 3.define constructor
            builder = builder
                    .defineConstructor(Modifier.PUBLIC)
                    .withParameter(aspectJTypeDescription, TARGET_FILED)
                    .intercept(
                            MethodCall.invoke(OBJECT_DEFAULT_CONSTRUCTOR)
                            .andThen(
                                    FieldAccessor.ofField(TARGET_FILED).setsArgumentAt(0)
                            )
                    );


            // 4.define advice method
            String methodName = methodSpec.isAround 
                    ? AROUND_ADVICE_METHOD_NAME 
                    : (methodSpec.isBefore ? BEFORE_ADVICE_METHOD_NAME : AFTER_ADVICE_METHOD_NAME);
            builder = builder
                    .defineMethod(methodName, void.class, Modifier.PUBLIC)
                    .withParameter(
                            methodSpec.parameterizedReturningType != null
                                ? TypeDescription.Generic.Builder.parameterizedType(MUTABLE_JOINPOINT_TYPE, methodSpec.parameterizedReturningType, methodSpec.parameterizedThrowingType).build()
                                : MUTABLE_JOINPOINT_TYPE, 
                            "joinpoint"
                    )
            .throwing(Throwable.class)
            .intercept( new AspectJAdviceMethodImplementation(methodSpec) )
            ;

            return (DynamicType.Unloaded<? extends Advice>) builder.make();
        }


        static class AspectJAdviceMethodImplementation implements Implementation, ByteCodeAppender {

            private static final int PARAM_INDEX0_THIS_OBJECT = 0;
            private static final int PARAM_INDEX1_MUTABLE_JOINPOINT = 1;

            private static final MethodDescription.InDefinedShape GET_ARGUMENTS_METHOD = JOINPOINT_TYPE.getDeclaredMethods().filter(named("getArguments")).getOnly();
            private static final MethodDescription.InDefinedShape GET_STATIC_PART_METHOD = JOINPOINT_TYPE.getDeclaredMethods().filter(named("getStaticPart")).getOnly();
            private static final MethodDescription.InDefinedShape GET_THIS_OBJECT_METHOD = JOINPOINT_TYPE.getDeclaredMethods().filter(named("getThisObject")).getOnly();

            private static final MethodDescription.InDefinedShape GET_RETURNING_METHOD = MUTABLE_JOINPOINT_TYPE.getDeclaredMethods().filter(named("getReturning")).getOnly();
            private static final MethodDescription.InDefinedShape GET_THROWING_METHOD = MUTABLE_JOINPOINT_TYPE.getDeclaredMethods().filter(named("getThrowing")).getOnly();


            private final MethodDescription methodDescription;
            private final MethodSpec methodSpec;


            /**
             * @param methodSpec
             */
            public AspectJAdviceMethodImplementation(MethodSpec methodSpec) {
                this.methodSpec = methodSpec;
                this.methodDescription = methodSpec.getAdviceMethodDescription();
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
                if(methodSpec.isAfterReturning == true || methodSpec.isAfterThrowing == true) {
                    methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MUTABLE_JOINPOINT_TYPE.getInternalName(), 
                            GET_THROWING_METHOD.getInternalName(), GET_THROWING_METHOD.getDescriptor(), true);

                    // if(throwing == null) invoke AfterReturning Advice
                    if(methodSpec.isAfterReturning == true)
                        methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, nullCheck);
                    // if(throwing != null) invoke AfterThrowing Advice
                    if(methodSpec.isAfterThrowing == true)
                        methodVisitor.visitJumpInsn(Opcodes.IFNULL, nullCheck);
                }


                // 2.declare and assign argument local variable
                // Object[] arguments = joinpoint.getArguments()
                int argumentsVariableOffset = -1;
                if(methodSpec.getPointcutParameterNames().size() > 0) {
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
                int paramIndex = 0;
                for(Entry<String, ParameterBinding> entry : methodSpec.getParameterBindings().entrySet()) {
                    String parameterName = entry.getKey();
                    TypeDescription parameterType = methodSpec.parameterDescriptionMap.get(parameterName).getType().asErasure();
                    Generic parameterGeneric = methodSpec.parameterDescriptionMap.get(parameterName).getType();
                    ParameterBinding parameterBinding = entry.getValue();

                    paramIndex++;
                    operandsStackSize++;
                    switch(parameterBinding.getBindingType()) {
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
                            if(methodSpec.isVoidReturningOfTargetMethod) {
                                Assigner.DEFAULT.assign(MethodSpec.VOID.asGenericType(), parameterGeneric, Typing.DYNAMIC).apply(methodVisitor, implementationContext);
                            } else {
                                methodVisitor.visitVarInsn(Opcodes.ALOAD, PARAM_INDEX1_MUTABLE_JOINPOINT);
                                methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MUTABLE_JOINPOINT_TYPE.getInternalName(), 
                                        GET_RETURNING_METHOD.getInternalName(), GET_RETURNING_METHOD.getDescriptor(), true);
                                Assigner.DEFAULT.assign(MethodSpec.OBJECT.asGenericType(), parameterGeneric, Typing.DYNAMIC).apply(methodVisitor, implementationContext);

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
                            IntegerConstant.forValue(parameterBinding.getArgIndex()).apply(methodVisitor, implementationContext);
                            methodVisitor.visitInsn(Opcodes.AALOAD);
                            Assigner.DEFAULT.assign(MethodSpec.OBJECT.asGenericType(), parameterGeneric, Typing.DYNAMIC).apply(methodVisitor, implementationContext);

                            operandsStackSize++;
                            if(parameterGeneric.getStackSize().getSize() == 1 && paramIndex < methodSpec.parameterBindings.size())
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
                if(methodSpec.isAfterReturning == true || methodSpec.isAfterThrowing == true) {
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
