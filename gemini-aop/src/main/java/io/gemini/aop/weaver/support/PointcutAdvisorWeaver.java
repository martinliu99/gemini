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
package io.gemini.aop.weaver.support;

import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AdviceKind.ByteBuddyAdviceKind;
import io.gemini.aop.AdviceKind.Managed;
import io.gemini.aop.Advisor;
import io.gemini.aop.Advisor.PointcutAdvisor;
import io.gemini.aop.weaver.AopWeaver;
import io.gemini.aop.weaver.Joinpoints;
import io.gemini.aop.weaver.WeaverContext;
import io.gemini.aop.weaver.advice.CircularityBreakerCodeGenerator;
import io.gemini.aop.weaver.advice.DescriptorOffset;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.AopException;
import io.gemini.core.OrderComparator;
import io.gemini.core.Ordered;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.WithCustomMapping;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.asm.AsmVisitorWrapper.ForDeclaredMethods.MethodVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * 
 */
public interface PointcutAdvisorWeaver {

    Logger LOGGER = LoggerFactory.getLogger(PointcutAdvisorWeaver.class);


    DynamicType.Builder<?> weave(DynamicType.Builder<?> builder, Object... arguments);


    @NoScanning
    class Compound implements PointcutAdvisorWeaver {

        protected static final Logger LOGGER = LoggerFactory.getLogger(PointcutAdvisorWeaver.class);

        private final MethodDescription targetMethod;
        private final List<? extends PointcutAdvisorWeaver> advisorWeavers;


        public Compound(AopWeaver aopWeaver, boolean loadedType, MethodDescription targetMethod, 
                List<? extends Advisor> advisors) {
            this.targetMethod = targetMethod;

            List<? extends PointcutAdvisorWeaver> advisorWeavers = aopWeaver.getWeaverContext().getAopContext().getObjectFactory()
                    .createObjectsImplementing(PointcutAdvisorWeaver.class, 
                            true, 
                            "aopWeaver", aopWeaver, 
                            "loadedType", loadedType,
                            "targetMethod", targetMethod, 
                            "advisors", advisors);
            this.advisorWeavers = advisorWeavers == null 
                    ? Collections.emptyList() : advisorWeavers;

            OrderComparator.sort(advisorWeavers);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public Builder<?> weave(Builder<?> builder, Object... arguments) {
            for (PointcutAdvisorWeaver advisorWeaver : advisorWeavers) {
                try {
                    Builder<?> returning = advisorWeaver.weave(builder, arguments);
                    if (returning != null)
                        builder = returning;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled()) 
                        LOGGER.warn("Could not weave method via '{}'. \n"
                                + "  TargetMethod: {}"
                                + "  Error reason: {} \n", 
                                advisorWeaver,
                                MethodUtils.getMethodSignature(targetMethod),
                                t.getMessage(),
                                t
                        );
                }
            }
            return builder;
        }
    }


    abstract class AbstractBase implements PointcutAdvisorWeaver, Ordered {

        private final AopWeaver aopWeaver;

        private final boolean loadedType;
        private final MethodDescription targetMethod;
        private final String targetMethodSignature;

        private final List<? extends PointcutAdvisor> pointcutAdvisors;


        public AbstractBase(AopWeaver aopWeaver, boolean loadedType, MethodDescription targetMethod, 
                List<? extends Advisor> advisors) {
            this.aopWeaver = aopWeaver;

            this.loadedType = loadedType;
            this.targetMethod = targetMethod;
            this.targetMethodSignature = targetMethod.toGenericString();

            this.pointcutAdvisors = resolveAdvisors(advisors);
            if (CollectionUtils.isEmpty(this.pointcutAdvisors))
                throw new IgnoredWeaverException();
        }

        private List<? extends PointcutAdvisor> resolveAdvisors(List<? extends Advisor> advisors) {
            List<PointcutAdvisor> candidateAdvisors = new ArrayList<>();
            for (Advisor advisor : advisors) {
                if (advisor == null || advisor instanceof PointcutAdvisor == false)
                    continue;

                PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
                if (isCandidateAdvisor(pointcutAdvisor) == false)
                    continue;

                candidateAdvisors.add(pointcutAdvisor);
            }

            // sort advisor
            OrderComparator.sort(candidateAdvisors);

            return candidateAdvisors;
        }

        protected abstract boolean isCandidateAdvisor(PointcutAdvisor advisor);


        protected WeaverContext getWeaverContext() {
            return aopWeaver.getWeaverContext();
        }

        protected boolean isLoadedType() {
            return loadedType;
        }

        protected MethodDescription getTargetMethod() {
            return targetMethod;
        }

        protected String getTargetMethodSignature() {
            return targetMethodSignature;
        }

        protected List<? extends PointcutAdvisor> getPointcutAdvisors() {
            return pointcutAdvisors;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public Builder<?> weave(Builder<?> builder, Object... arguments) {
            try {
                return doWeave(builder, arguments);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled()) 
                    LOGGER.warn("Could not weave method via '{}'. \n"
                            + "  TargetMethod: {}"
                            + "  Error reason: {} \n", 
                            this,
                            MethodUtils.getMethodSignature(targetMethod),
                            e.getMessage(),
                            e
                    );

                return builder;
            }
        }

        protected abstract Builder<?> doWeave(Builder<?> builder, Object... arguments);
    }


    class IgnoredWeaverException extends AopException {

        public IgnoredWeaverException() {
            super("");
        }

        private static final long serialVersionUID = -5947928415690884829L;
    }


    class NativeMethodWeaver extends AbstractBase {

        public NativeMethodWeaver(AopWeaver aopWeaver, boolean loadedType, MethodDescription targetMethod, 
                List<? extends Advisor> advisors) {
            super(aopWeaver, loadedType, targetMethod, advisors);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean isCandidateAdvisor(PointcutAdvisor advisor) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Builder<?> doWeave(Builder<?> builder, Object... arguments) {
            if (getTargetMethod().isNative() == false)
                return builder;

            // define renamed native method
            MethodDescription targetMethod = getTargetMethod();

            String renamedMethodName = getWeaverContext().getNativeMethodPrefix() + targetMethod.getName();
            Implementation implementation = MethodCall.invoke( named(renamedMethodName) ).withAllArguments();

            return builder
            // define rename native method
            .defineMethod( 
                    renamedMethodName, targetMethod.getReturnType(), targetMethod.getActualModifiers() )
            .withParameters(
                    targetMethod.getParameters().asTypeList())
            .withoutCode()
            // intercept original method and replace method body
            .method( is(targetMethod) )
            .intercept( implementation );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getOrder() {
            return -100;
        }
    }


    class ManagedAdviceWeaver extends AbstractBase implements AopWeaver.WeavedCodeCallback {

        private static final Map<Class<?>, DynamicType.Loaded<?>> BEARKING_CIRCULARITY_ADVICES = new HashMap<>();


        private int callbackSlot;
        private Joinpoints.Descriptor descriptor;


        public ManagedAdviceWeaver(AopWeaver aopWeaver, boolean loadedType, MethodDescription targetMethod, 
                List<? extends Advisor> advisors) {
            super(aopWeaver, loadedType, targetMethod, advisors);

            callbackSlot = aopWeaver.registerCallback(this);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean isCandidateAdvisor(PointcutAdvisor advisor) {
            return Managed.class.isAssignableFrom(advisor.getAdviceKind().getClass());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Builder<?> doWeave(Builder<?> builder, Object... arguments) {
            MethodDescription targetMethod = getTargetMethod();

            WithCustomMapping withCustomMapping = Advice.withCustomMapping().bind( 
                    DescriptorOffset.create(
                            targetMethod, Integer.valueOf(callbackSlot) ) );
            Advice frameworkAdvice = toAdvice(
                    withCustomMapping, 
                    getWeaverContext().getFrameworkAdviceClass(targetMethod));

            // 1.weave regular method
            if (targetMethod.isNative() == false)
                return builder.visit( 
                        frameworkAdvice.on( is(targetMethod) ) 
                );

            // 2.weave native method
            if (isLoadedType() == true) {
                LOGGER.warn("Could not retransform type '{}' loaded by ClassLoader '{}' in AopWeaver, \n"
                        + "  NativeTargetMethod: {}",
                        getTargetMethod().getDeclaringType().getTypeName(),
                        getTargetMethodSignature());
                return builder;
            }

            return builder
            .method( is(targetMethod) )
            .intercept( frameworkAdvice )
            ;
        }

        private Advice toAdvice(WithCustomMapping withCustomMapping, Class<?> frameworkAdviceClass) {
            if (isBreakCircularity() == false)
                return withCustomMapping.to(frameworkAdviceClass);

            DynamicType.Loaded<?> loadedAdviceClass = BEARKING_CIRCULARITY_ADVICES.computeIfAbsent(
                    frameworkAdviceClass, 
                    clazz -> CircularityBreakerCodeGenerator.INSTANCE.wrapMethodImplementation(clazz)
            );
            return withCustomMapping.to(loadedAdviceClass.getLoaded(), loadedAdviceClass);
        }

        private boolean isBreakCircularity() {
            for (PointcutAdvisor pointcutAdvisor : getPointcutAdvisors()) {
                if (pointcutAdvisor.isBreakCircularity() == true)
                    return true;
            }
            return false;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public Object callback(Lookup targetLookup, String methodName, MethodType mothodType, Object... arguments) {
            int callbackSlot = (int) arguments[0];
            if (callbackSlot != this.callbackSlot)
                return null;

            Joinpoints.Descriptor descriptor = doCreateJoinpointDescriptor(targetLookup);
            boolean isCallSite = arguments[1].equals(DescriptorOffset.CALLSITE_DESCRIPTOR_FLAG);
            if (isCallSite == false)
                return descriptor;

            MethodHandle constant = MethodHandles.constant(Object.class, descriptor);
            return new ConstantCallSite( constant );
        }

        protected Joinpoints.Descriptor doCreateJoinpointDescriptor(Lookup targetLookup) {
            if (this.descriptor != null)
                return this.descriptor;

            Class<?> targetClass = targetLookup.lookupClass();
            ClassLoader targetClassLoader = targetClass.getClassLoader();

            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
            MethodDescription targetMethod = getTargetMethod();
            String accessibleName = getTargetMethodSignature();
            try {
                ThreadContext.setContextClassLoader(targetClassLoader);  // set targetClassLoader

                List<? extends Advisor> advisors = this.getPointcutAdvisors();

                if (CollectionUtils.isEmpty(advisors))
                    return null;

                //
                AccessibleObject accessibleObject = null;
                if (targetMethod.isTypeInitializer() == false) {
                    accessibleObject = ClassUtils.getAccessibleObject(targetClass, targetMethod);
                }

                return (this.descriptor = new Joinpoints.Descriptor(targetLookup, accessibleName, accessibleObject, advisors));
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not create joinpoint descriptor for type '{}' loaded by ClassLoader '{}'. \n"
                            + "  TargetMethod: {}"
                            + "  Error reason: {} \n", 
                            targetMethod.getDeclaringType().getTypeName(), targetClassLoader, 
                            accessibleName, 
                            t.getMessage(),
                            t
                    );

                Throwables.throwIfRequired(t);
                return null;
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getOrder() {
            return 0;
        }
    }


    class ByteBuddyAdviceWeaver extends AbstractBase implements AopWeaver.WeavedCodeCallback {

        private static final Map<Class<?>, DynamicType.Loaded<?>> BEARKING_CIRCULARITY_ADVICES = new HashMap<>();

        private int callbackSlot;


        /**
         * @param targetMethod
         * @param advisors
         */
        public ByteBuddyAdviceWeaver(AopWeaver aopWeaver, boolean loadedType, MethodDescription targetMethod, 
                List<? extends Advisor> advisors) {
            super(aopWeaver, loadedType, targetMethod, advisors);

            this.callbackSlot = aopWeaver.registerCallback(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean isCandidateAdvisor(PointcutAdvisor advisor) {
            return ByteBuddyAdviceKind.class.isAssignableFrom(advisor.getAdviceKind().getClass());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Builder<?> doWeave(Builder<?> builder, Object... arguments) {
            WithCustomMapping withCustomMapping = Advice.withCustomMapping().bootstrap(
                    AopWeaver.BOOTSTRAP_DISPATCHER_CALLBACK_METHOD,
                    new DefaultBootstrapArgumentResolverFactory(this.callbackSlot)
            );

            MethodDescription targetMethod = getTargetMethod();
            List<? extends PointcutAdvisor> advisors = this.getPointcutAdvisors();
            List<MethodVisitorWrapper> methodVisitorWrappers = new ArrayList<>(advisors.size() + 1);

            builder = builder.visit(
                    new AsmVisitorWrapper.ForDeclaredMethods().invokable(
                            is(getTargetMethod()), CircularityBreakerCodeGenerator.INSTANCE.postProcessTargetMethod(callbackSlot)
                    )
            );


            // 1.weave regular method
            if (targetMethod.isNative() == false) {
                for (PointcutAdvisor advisor : advisors) {
                    Advice advice = toAdvice(withCustomMapping, advisor);
                    methodVisitorWrappers.add(advice);
                }

                return builder.visit( 
                        new AsmVisitorWrapper.ForDeclaredMethods().invokable(
                                is(targetMethod), methodVisitorWrappers
                        ) 
                );
            }


            // 2.weave native method
            if (isLoadedType() == true) {
                LOGGER.warn("Could not retransform type '{}' loaded by ClassLoader '{}' in AopWeaver, \n"
                        + "  NativeTargetMethod: {}",
                        getTargetMethod().getDeclaringType().getTypeName(),
                        getTargetMethodSignature());
                return builder;
            }

            // TODO: reverse advice?
            Implementation implementation = null;
            for (PointcutAdvisor advisor : advisors) {
                Advice advice = toAdvice(withCustomMapping, advisor)
                        .withAssigner(Assigner.DEFAULT)
                        .withExceptionHandler(Advice.ExceptionHandler.Default.SUPPRESSING);

                implementation = implementation == null
                        ? implementation
                        : advice.wrap(implementation);
            }

            return builder
            .method( is(targetMethod) )
            .intercept( implementation );
        }

        private Advice toAdvice(WithCustomMapping withCustomMapping, PointcutAdvisor advisor) {
            Class<?> adviceClass = advisor.getAdviceClass();
            if (advisor.isBreakCircularity() == false)
                return withCustomMapping.to(adviceClass);
            else {
                DynamicType.Loaded<?> loadedAdviceClass = BEARKING_CIRCULARITY_ADVICES.computeIfAbsent(
                        adviceClass, 
                        clazz -> CircularityBreakerCodeGenerator.INSTANCE.wrapMethodCall(adviceClass)
                );
                return withCustomMapping.to(loadedAdviceClass.getLoaded(), loadedAdviceClass);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object callback(Lookup targetLookup, String methodName, MethodType mothodType, Object... arguments) {
            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
            try {
                ClassLoader targetClassLoader = targetLookup.lookupClass().getClassLoader();
                ThreadContext.setContextClassLoader(targetClassLoader);   // set targetClassLoader

                MethodHandle methodHandle = getMethodHandle((String) arguments[1], methodName, mothodType);
                if (methodHandle == null) {
                    return null;
                }

                return new ConstantCallSite(methodHandle);
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }

        private MethodHandle getMethodHandle(String adviceTypeName, String adviceMethodName, MethodType adviceMethodType) {
            Class<?> byteBuddyAdvice = null;
            for (PointcutAdvisor advisor : getPointcutAdvisors()) {
                if ( (byteBuddyAdvice = advisor.getAdviceClass()).getName().equals(adviceTypeName))
                    break;
            }

            try {
                if (byteBuddyAdvice != null)
                    return MethodHandles.lookup().findStatic(byteBuddyAdvice, adviceMethodName, adviceMethodType);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not find static advice method. \n"
                            + "  TargetMethod: {}"
                            + "  AdviceMethod: {} {}"
                            + "  Error reason: {} \n", 
                            getTargetMethodSignature(),
                            adviceMethodName, adviceMethodType,
                            e.getMessage(),
                            e
                    );
            }

            return null;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public int getOrder() {
            return 100;
        }
    }
}