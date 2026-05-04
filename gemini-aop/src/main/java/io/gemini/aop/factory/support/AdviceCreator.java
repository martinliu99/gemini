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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AdviceKind.AspectJAdviceKind;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.factory.AdvisorContext;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AdviceSpec.AspectJAdviceSpec;
import io.gemini.aop.factory.support.AdviceSpec.ByteBuddyAdviceSpec;
import io.gemini.aop.factory.support.AdviceSpec.PojoAdviceSpec;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.core.OrderComparator;
import io.gemini.core.loading.ClassLoadingStrategySelector;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.ReflectionUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Factory interface for loading and instantiating {@link io.gemini.api.aop.Advice} classes
 * from {@link AdvisorSpec} descriptors.
 * <p>
 * Three concrete implementations handle the three advice styles:
 * <ul>
 *   <li>{@link PojoAdviceCreator} – loads POJO {@link io.gemini.api.aop.Advice} implementations</li>
 *   <li>{@link AspectJAdviceCreator} – generates and loads adapter classes for AspectJ advice methods</li>
 *   <li>{@link ByteBuddyAdviceCreator} – loads raw ByteBuddy {@code @Advice} classes</li>
 * </ul>
 * The {@link Compound} implementation delegates to all registered creators in order.
 * </p>
 *
 * @author   martin.liu
 */
public interface AdviceCreator {

    Logger LOGGER = LoggerFactory.getLogger(AdviceCreator.class);


    /**
     * Returns the {@link ElementMatcher} used to match target methods against advice method signature constraints 
     * (e.g. returning type and throwing type compatibility).
     *
     * @return the advice matcher for this advice spec
     */
    ElementMatcher<MethodDescription> createAdviceMatcher(AdvisorContext advisorContext, AdvisorSpec advisorSpec);

    /**
     * Loads and returns the {@link io.gemini.api.aop.Advice} class described by the given
     * {@link AdvisorSpec}, or {@code null} if this creator does not handle the spec type.
     *
     * @param advisorContext the advisor context for the target class loader
     * @param advisorSpec    the advisor spec describing the advice to load
     * @return the loaded advice class, or {@code null}
     */
    Class<? extends Advice> loadClass(AdvisorContext advisorContext, AdvisorSpec advisorSpec);

    /**
     * Creates and returns a new instance of the given advice class, or {@code null} if this
     * creator does not handle the spec type.
     *
     * @param advisorContext the advisor context for the target class loader
     * @param advisorSpec    the advisor spec describing the advice
     * @param adviceClass    the advice class to instantiate
     * @return a new advice instance, or {@code null}
     */
    Advice createInstance(AdvisorContext advisorContext, AdvisorSpec advisorSpec, Class<? extends Advice> adviceClass);


    /**
     * Delegates to all registered {@link AdviceCreator} instances in order,
     * and returns the first non-null result.
     */
    @NoScanning
    class Compound implements AdviceCreator {

        private final List<? extends AdviceCreator> adviceCreators;


        public Compound(FactoryContext factoryContext) {
            List<? extends AdviceCreator> adviceCreators = factoryContext.getObjectFactory()
                    .createObjectsImplementing(AdviceCreator.class, false);
            this.adviceCreators = adviceCreators == null 
                    ? Collections.emptyList() : adviceCreators;

            OrderComparator.sort(adviceCreators);
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<MethodDescription> createAdviceMatcher(AdvisorContext advisorContext, AdvisorSpec advisorSpec) {
            for (AdviceCreator adviceCreator : adviceCreators) {
                ElementMatcher<MethodDescription> adviceMatcher = adviceCreator.createAdviceMatcher(advisorContext, advisorSpec);
                if (adviceMatcher != null)
                    return adviceMatcher;
            }
            return null;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends Advice> loadClass(AdvisorContext advisorContext, AdvisorSpec advisorSpec) {
            for (AdviceCreator adviceCreator : adviceCreators) {
                try {
                    Class<? extends Advice> adviceClass = adviceCreator.loadClass(advisorContext, advisorSpec);
                    if (adviceClass != null) 
                        return adviceClass;
                } catch (IllegalSpecException e) {
                    throw e;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not load Advice class via '{}'. \n"
                                + "  AdvisorName: {} \n"
                                + "  AdviceClass: {} \n"
                                + "  ClassLoader: {} \n"
                                + "  Error reason: {} \n", 
                                adviceCreator.getClass().getSimpleName(), 
                                advisorSpec.getAdvisorName(),
                                advisorSpec.getAdviceSpec().getAdviceClassName(),
                                advisorContext.getTargetClassLoaderName(), 
                                t.getMessage(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Advice createInstance(AdvisorContext advisorContext, AdvisorSpec advisorSpec, Class<? extends Advice> adviceClass) {
            for (AdviceCreator adviceCreator : adviceCreators) {
                try {
                    Advice advice = adviceCreator.createInstance(advisorContext, advisorSpec, adviceClass);
                    if (advice != null) 
                        return advice;
                } catch (IllegalSpecException e) {
                    throw e;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not create Advice via '{}'. \n"
                                + "  AdvisorName: {} \n"
                                + "  AdviceClass: {} \n"
                                + "  ClassLoader: {} \n"
                                + "  Error reason: {} \n", 
                                adviceCreator, 
                                advisorSpec.getAdvisorName(),
                                adviceClass.getName(),
                                advisorContext.getTargetClassLoaderName(), 
                                t.getMessage(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }
            }
            return null;
        }
    }


    /**
     * Abstract base providing common logic for loading and instantiating advice classes:
     * spec class filtering, class loading, validity checking, and instance creation.
     */
    abstract class AbstractBase<A extends AdviceSpec> implements AdviceCreator {

        protected abstract boolean supports(AdviceSpec adviceSpec);


        /** 
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public ElementMatcher<MethodDescription> createAdviceMatcher(AdvisorContext advisorContext, AdvisorSpec advisorSpec) {
            if (advisorSpec == null || advisorSpec.getAdviceSpec() == null ||  supports(advisorSpec.getAdviceSpec()) == false)
                return null;

            return doCreateAdviceMatcher(advisorContext, advisorSpec, (A) advisorSpec.getAdviceSpec());
        }

        protected abstract ElementMatcher<MethodDescription> doCreateAdviceMatcher(AdvisorContext advisorContext,
                AdvisorSpec advisorSpec, A adviceSpec);

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public Class<? extends Advice> loadClass(AdvisorContext advisorContext, AdvisorSpec advisorSpec) {
            try {
                if (advisorSpec == null || advisorSpec.getAdviceSpec() == null ||  supports(advisorSpec.getAdviceSpec()) == false)
                    return null;

                return doLoadAdviceClass(advisorContext, advisorSpec, (A) advisorSpec.getAdviceSpec());
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not load Advice class. \n"
                            + "  AdvisorName: {} \n"
                            + "  AdviceClass: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            advisorSpec.getAdviceSpec().getAdviceClassName(), 
                            advisorContext.getTargetClassLoaderName(), 
                            e.getMessage(),
                            e
                    );

                return null;
            }
        }

        protected abstract Class<? extends Advice> doLoadAdviceClass(AdvisorContext advisorContext, AdvisorSpec advisorSpec, A adviceSpec);


        protected Class<? extends Advice> loadClass(AdvisorContext advisorContext, 
                String advisorName, A adviceSpec, String className) {
            ObjectFactory objectFactory = advisorContext.getObjectFactory();

            Class<? extends Advice> adviceClass = objectFactory.loadClass(className);
            if (objectFactory.isInstantiatable(adviceClass))
                return adviceClass;

            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Ignored AdvisorSpec with non top-level or nested, concrete AdviceClass. \n"
                        + "  AdvisorName: {} \n"
                        + "  AdviceClass: {} \n"
                        + "  ClassLoader: {} \n",
                        advisorName, 
                        className, 
                        advisorContext.getTargetClassLoaderName()
                );

            return null;
        }

        protected boolean isValid(AdvisorContext advisorContext, String advisorName, A adviceSpec, 
                Class<? extends Advice> adviceClass) {
            if (Advice.class.isAssignableFrom(adviceClass) == true)
                return true;

            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Ignored AdvisorSpec with non {} AdviceClass. \n"
                        + "  AdvisorName: {} \n"
                        + "  AdviceClass: {} \n"
                        + "  ClassLoader: {} \n",
                        Advice.class.getName(), 
                        advisorName, 
                        adviceClass.getName(), 
                        advisorContext.getTargetClassLoaderName()
                );
            return false;
        }


        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public Advice createInstance(AdvisorContext advisorContext, AdvisorSpec advisorSpec, Class<? extends Advice> adviceClass) {
            if (advisorSpec == null || advisorSpec.getAdviceSpec() == null ||  supports(advisorSpec.getAdviceSpec()) == false)
                return null;

            // try to instantiate advice object
            return doCreateInstance(advisorContext, advisorSpec, (A) advisorSpec.getAdviceSpec(), adviceClass);
        }

        protected Advice doCreateInstance(AdvisorContext advisorContext, AdvisorSpec advisorSpec, 
                A adviceSpec, Class<? extends Advice> adviceClass) {
            // try to instantiate advice object
            try {
                ObjectFactory objectFactory = advisorContext.getObjectFactory();

                return objectFactory.createObject(adviceClass);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdvisorSpec with uninstantiable AdviceClass. \n"
                            + "  AdvisorName: {} \n"
                            + "  AdviceClass: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            adviceClass.getName(), 
                            advisorContext.getTargetClassLoaderName(), 
                            e.getMessage(),
                            e
                    );

                return null;
            }
        }
    }


    /**
     * Loads and instantiates POJO {@link io.gemini.api.aop.Advice} implementations
     * (classes implementing {@link io.gemini.api.aop.Advice.Before}, {@link io.gemini.api.aop.Advice.After},
     * or {@link io.gemini.api.aop.Advice.Around}).
     */
    class PojoAdviceCreator extends AbstractBase<PojoAdviceSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean supports(AdviceSpec adviceSpec) {
            return PojoAdviceSpec.class.isAssignableFrom(adviceSpec.getClass());
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected ElementMatcher<MethodDescription> doCreateAdviceMatcher(AdvisorContext advisorContext,
                AdvisorSpec advisorSpec, PojoAdviceSpec adviceSpec) {
            return new AdviceMatcher.PojoAdviceMatcher(adviceSpec);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends Advice> doLoadAdviceClass(AdvisorContext advisorContext,
                AdvisorSpec advisorSpec, PojoAdviceSpec adviceSpec) {
            // try to load advice class
            String advisorName = advisorSpec.getAdvisorName();
            Class<? extends Advice> adviceClass = loadClass(advisorContext, advisorName,
                    adviceSpec, adviceSpec.getAdviceClassName());
            if (isValid(advisorContext, advisorName, adviceSpec, adviceClass) == false)
                return null;

            return adviceClass;
        }
    }


    /**
     * Generates and loads a concrete {@link io.gemini.api.aop.Advice} adapter class
     * for an AspectJ advice method using {@link AdviceClassGenerator}.
     */
//    @NoScanning
    class AspectJAdviceCreator extends AbstractBase<AspectJAdviceSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean supports(AdviceSpec adviceSpec) {
            return AspectJAdviceSpec.class.isAssignableFrom(adviceSpec.getClass());
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected ElementMatcher<MethodDescription> doCreateAdviceMatcher(AdvisorContext advisorContext,
                AdvisorSpec advisorSpec, AspectJAdviceSpec adviceSpec) {
            return new AdviceMatcher.AspectJAdviceMatcher(adviceSpec);
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        protected Class<? extends Advice> doLoadAdviceClass(AdvisorContext advisorContext, 
                AdvisorSpec advisorSpec, AspectJAdviceSpec adviceSpec) {
            long startedAt = System.nanoTime();

            ClassLoader aspectClassLoader = advisorContext.getClassLoader();

            // try to make advice class
            String advisorName = advisorSpec.getAdvisorName();
            try {
                try {
                    return (Class<? extends Advice>) aspectClassLoader.loadClass(adviceSpec.getAdviceClassName());
                } catch (Throwable t) {
                    Throwables.throwIfRequired(t);
                }

                Class<?> aspectJClass = this.loadClass(advisorContext, advisorName, 
                        adviceSpec, adviceSpec.getDeclaringType().getTypeName());

                Class<? extends Advice> adviceClass = adviceSpec.getUnloadedAdviceClass()
                        .load(aspectClassLoader, ClassLoadingStrategySelector.Default.SINGLETON.select(aspectJClass))
                        .getLoaded();

//                this.loadClass(advisorContext, adviceSpec, adviceSpec.getDeclaringType().getTypeName());

                if (LOGGER.isInfoEnabled() && advisorContext.getFactoryContext().getAopContext().getDiagnosticLevel().isSimpleEnabled())
                    LOGGER.info("Took '{}' seconds to inject AdvisorClass '{}' into classloader '{}'.", 
                            (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, adviceSpec.getAdviceClassName(), aspectClassLoader
                    );

                return adviceClass;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not generate adapter class for AspectJ advice method. \n"
                            + "  AdvisorName: {} \n"
                            + "  AdapterClass: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorName, 
                            adviceSpec.getDeclaringType(), 
                            advisorContext.getTargetClassLoaderName(), 
                            e.getMessage(),
                            e
                    );

                return null;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Advice doCreateInstance(AdvisorContext advisorContext, AdvisorSpec advisorSpec, 
                AspectJAdviceSpec adviceSpec,  Class<? extends Advice> adviceClass) {
            String advisorName = advisorSpec.getAdvisorName();
            try {
                // load AspectJ class
                Class<?> aspectJClass = this.loadClass(advisorContext, advisorName, 
                        adviceSpec, adviceSpec.getDeclaringType().getTypeName());

                Constructor<?> adviceConstructor = adviceClass.getConstructor(aspectJClass);
                ReflectionUtils.makeAccessible(adviceClass, adviceConstructor);

                // try to instantiate advice object
                return (Advice) adviceConstructor.newInstance(
                        advisorContext.getObjectFactory().createObject(aspectJClass) );
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not instantiate adapter class for AspectJ advice method. \n"
                            + "  AdvisorName: {} \n"
                            + "  AdapterClass: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorName, 
                            adviceSpec.getAdviceClassName(), 
                            advisorContext.getTargetClassLoaderName(), 
                            e.getMessage(),
                            e
                    );

                return null;
            }
        }
    }


    /**
     * Alternative AspectJ advice creator that uses {@link MethodHandle}-based adapters
     * instead of bytecode generation. Currently disabled ({@code @NoScanning}).
     */
    @NoScanning
    class AspectJAdviceCreator2 extends AbstractBase<AspectJAdviceSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean supports(AdviceSpec adviceSpec) {
            return AspectJAdviceSpec.class.isAssignableFrom(adviceSpec.getClass());
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected ElementMatcher<MethodDescription> doCreateAdviceMatcher(AdvisorContext advisorContext,
                AdvisorSpec advisorSpec, AspectJAdviceSpec adviceSpec) {
            return new AdviceMatcher.AspectJAdviceMatcher(adviceSpec);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends Advice> doLoadAdviceClass(AdvisorContext advisorContext,
                AdvisorSpec advisorSpec, AspectJAdviceSpec adviceSpec) {
            AspectJAdviceKind adviceKind = adviceSpec.getAdviceKind();
            if (adviceKind == AspectJAdviceKind.BEFORE)
                return BeforeAdviceAdapter.class;
            else
                return AfterAdviceAdapter.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Advice doCreateInstance(AdvisorContext advisorContext, AdvisorSpec advisorSpec, 
                AspectJAdviceSpec adviceSpec,  Class<? extends Advice> adviceClass) {
            String advisorName = advisorSpec.getAdvisorName();
            try {
                // load AspectJ class
                Class<?> aspectJClass = this.loadClass(advisorContext, advisorName, 
                        adviceSpec, adviceSpec.getDeclaringType().getTypeName());
                Object aspectJObject = advisorContext.getObjectFactory().createObject(aspectJClass);

                AspectJAdviceKind adviceKind = adviceSpec.getAdviceKind();
                if (adviceKind == AspectJAdviceKind.BEFORE)
                    return new BeforeAdviceAdapter(adviceSpec, aspectJObject);
                else
                    return new AfterAdviceAdapter(adviceSpec, aspectJObject);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not instantiate adapter class for AspectJ advice method. \n"
                            + "  AdvisorName: {} \n"
                            + "  AdapterClass: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorName, 
                            adviceSpec.getAdviceClassName(), 
                            advisorContext.getTargetClassLoaderName(), 
                            e.getMessage(),
                            e
                    );

                return null;
            }
        }


        /**
         * Abstract base for {@link MethodHandle}-based AspectJ advice adapters.
         * Holds the AspectJ aspect instance and provides argument extraction from the joinpoint.
         */
        static abstract class AbstractAdapter extends Advice.AbstractBase {

            private final AspectJAdviceSpec adviceSpec;
            private final Object delegate;

            public AbstractAdapter(AspectJAdviceSpec adviceSpec, Object delegate) 
                    throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
                this.adviceSpec = adviceSpec;
                this.delegate = delegate;
            }

            public AspectJAdviceSpec getAdviceSpec() {
                return adviceSpec;
            }

            public Object getDelegate() {
                return delegate;
            }

            protected MethodHandle getAdviceMethod(AspectJAdviceSpec adviceSpec, Class<?> clazz) 
                    throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
                ClassLoader classLoader = clazz.getClassLoader();

                MethodDescription adviceMethod = adviceSpec.getAdviceMethod();
                Class<?> returnType = ClassUtils.forName(adviceMethod.getReturnType().asErasure().getTypeName(), classLoader);
                List<Class<?>> paramTypes = new ArrayList<Class<?>>(adviceMethod.getParameters().size());
                for (ParameterDescription parameter : adviceMethod.getParameters()) {
                    paramTypes.add( ClassUtils.forName(parameter.getType().asErasure().getTypeName(), classLoader) );
                }

                MethodType methodType = MethodType.methodType(returnType, paramTypes);
                if (adviceMethod.isStatic())
                    return MethodHandles.lookup().findStatic(clazz, adviceMethod.getName(), methodType);
//                else if (adviceMethod.isPrivate())
//                    return MethodHandles.lookup().findSpecial(clazz, adviceMethod.getName(), methodType);
                else
                    return MethodHandles.lookup().findVirtual(clazz, adviceMethod.getName(), methodType);
            }


            protected Object[] getArguments(MutableJoinpoint<Object, Throwable> joinpoint) {
                int index = 0;
                Map<String, NamedPointcutParameter> namedPointcutParameters = getAdviceSpec().getNamedPointcutParameters();
                Object[] args = new Object[namedPointcutParameters.size()];
                for (Entry<String, NamedPointcutParameter> entry : namedPointcutParameters.entrySet()) {
                    switch (entry.getValue().getParamCategory()) {
                        case JOINPOINT_PARAM:
                        case MUTABLE_JOINPOINT_PARAM:
                        case PROCEDDING_JOINPOINT_PARAM: {
                            // first parameter is Joinpoint
                            args[index++] = joinpoint;

                            break;
                        }
                        case STATIC_PART_PARAM: {
                            // first parameter is StaticPart
                            args[index++] = joinpoint.getStaticPart();

                            break;
                        }
                        case RETURNING_ANNOTATION: {
                            args[index++] = joinpoint.getReturning();

                            break;
                        }
                        case THROWING_ANNOTATION: {
                            args[index++] = joinpoint.getThrowing();

                            break;
                        }
                        case THIS_VAR:
                        case TARGET_VAR: {
                            args[index++] = joinpoint.getTargetObject();
    
                            break;
                        }
                        case ARGS_VAR: {
                            Object[] arguments = joinpoint.getArguments();
                            args[index++] = arguments[entry.getValue().getArgsIndex()];

                            break;
                        }
                        default:
                            break;
                        }
                }
                return args;
            }
        }


        /**
         * {@link io.gemini.api.aop.Advice.Before} adapter that invokes the AspectJ before-advice
         * method via a cached {@link MethodHandle}.
         */
        static class BeforeAdviceAdapter extends AbstractAdapter implements Advice.Before<Object, Throwable> {

            private final MethodHandle beforeAdviceMethod;

            public BeforeAdviceAdapter(AspectJAdviceSpec adviceSpec, Object delegate)
                    throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
                super(adviceSpec, delegate);

                this.beforeAdviceMethod = getAdviceMethod(adviceSpec, delegate.getClass()).bindTo(delegate);
            }

            /**
             *  {@inheritDoc}
             */
            @Override
            public void before(MutableJoinpoint<Object, Throwable> joinpoint) throws Throwable {
                Object[] args = getArguments(joinpoint);
                beforeAdviceMethod.invokeWithArguments(args);
            }
        }


        /**
         * {@link io.gemini.api.aop.Advice.After} adapter that invokes the AspectJ after-advice
         * method via a cached {@link MethodHandle}.
         */
        static class AfterAdviceAdapter extends AbstractAdapter implements Advice.After<Object, Throwable> {

            private final MethodHandle afterAdviceMethod;

            public AfterAdviceAdapter(AspectJAdviceSpec adviceSpec, Object delegate)
                    throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
                super(adviceSpec, delegate);

                this.afterAdviceMethod = getAdviceMethod(adviceSpec, delegate.getClass()).bindTo(delegate);
            }

            /**
             *  {@inheritDoc}
             */
            @Override
            public void after(MutableJoinpoint<Object, Throwable> joinpoint) throws Throwable {
                Object[] args = getArguments(joinpoint);
                afterAdviceMethod.invokeWithArguments(args);
            }
        }
    }


    /**
     * Loads raw ByteBuddy {@code @Advice} classes directly without any wrapping.
     */
    class ByteBuddyAdviceCreator extends AbstractBase<ByteBuddyAdviceSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean supports(AdviceSpec adviceSpec) {
            return ByteBuddyAdviceSpec.class.isAssignableFrom(adviceSpec.getClass());
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected ElementMatcher<MethodDescription> doCreateAdviceMatcher(AdvisorContext advisorContext,
                AdvisorSpec advisorSpec, ByteBuddyAdviceSpec adviceSpec) {
            return ElementMatchers.any();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends Advice> doLoadAdviceClass(AdvisorContext advisorContext,
                AdvisorSpec advisorSpec, ByteBuddyAdviceSpec adviceSpec) {
            // try to load advice class
            return loadClass(advisorContext, advisorSpec.getAdvisorName(),
                    adviceSpec, adviceSpec.getAdviceClassName());
        }
    }
}
