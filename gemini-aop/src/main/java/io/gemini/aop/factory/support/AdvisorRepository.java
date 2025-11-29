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

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.factory.AdvisorContext;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AdviceClassMaker.ByteBuddyMaker;
import io.gemini.aop.matcher.AdviceMethodMatcher;
import io.gemini.aop.matcher.ExprPointcut;
import io.gemini.aop.matcher.ExprPointcut.AspectJExprPointcut;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.MatchingContext;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.condition.MissingElementException;
import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.ReflectionUtils;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorRepository {

    static final Logger LOGGER = LoggerFactory.getLogger(AdvisorRepository.class);


    String getAdvisorName();

    AdvisorSpec getAdvisorSpec();

    Advisor create(AdvisorContext advisorContext);


    static List<? extends Advisor> createAdvisors(FactoryContext factoryContext,
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            Collection<? extends AdvisorRepository> advisorRepositories) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating advisors via {} AdvisorRepository instances under '{}' for '{}',", 
                    advisorRepositories.size(), factoryName, joinpointClassLoader);


        AdvisorContext advisorContext = factoryContext.createAdvisorContext(joinpointClassLoader, javaModule);
        AopContext aopContext = factoryContext.getAopContext();
        List<Advisor> advisors = aopContext.getGlobalTaskExecutor().executeTasks(
                advisorRepositories, 
                advisorRepository -> {
                    try {
                        return advisorRepository.create(advisorContext);
                    } catch (Throwable t) {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Could not instantiate advisor with '{}'.", 
                                    advisorRepository.getAdvisorName(), t);

                        Throwables.throwIfRequired(t);
                        return null;
                    }
                },
                result -> {
                    ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
                    try {
                        ThreadContext.setContextClassLoader(joinpointClassLoader);   // set joinpointClassLoader
                        return result.get();
                    } finally {
                        ThreadContext.setContextClassLoader(existingClassLoader);
                    }
                }
        )
        .stream()
        .filter( e -> e != null)
        .collect( Collectors.toList() );


        if (aopContext.getDiagnosticLevel().isDebugEnabled() && advisors.size() > 0
                && LOGGER.isInfoEnabled()) 
            LOGGER.info("$Took '{}' seconds to create {} Advisor instances under '{}' for '{}', \n"
                    + "  {} \n", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisors.size(), factoryName, joinpointClassLoader,
                    StringUtils.join(advisors, Advisor::getAdvisorName, "\n  ")
            );
        else if (aopContext.getDiagnosticLevel().isSimpleEnabled() && LOGGER.isInfoEnabled()) 
            LOGGER.info("$Took '{}' seconds to create {} Advisor instances under '{}' for '{}'. ", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, 
                    advisors.size(), factoryName, joinpointClassLoader
            );

        return advisors;
    }


    abstract class AbstractBase<S extends AdvisorSpec, P extends Pointcut> implements AdvisorRepository {

        protected final S advisorSpec;
        protected final AdviceMethodMatcher adviceMethodMatcher;

        protected volatile boolean isValid = true;


        public AbstractBase(S advisorSpec, AdviceMethodMatcher adviceMethodMatcher) {
            this.advisorSpec = advisorSpec;
            this.adviceMethodMatcher = adviceMethodMatcher;
        }

        @Override
        public String getAdvisorName() {
            return advisorSpec.getAdvisorName();
        }

        @Override
        public AdvisorSpec getAdvisorSpec() {
            return advisorSpec;
        }

        @Override
        public Advisor create(AdvisorContext advisorContext) {
            if (this.isValid == false)
                return null;

            try {
                // 1.validate advisor condition
                if (validateCondition(advisorContext) == false)
                    return null;


                // 2.try to create Pointcut
                P originPointcut = createPointcut(advisorContext);
                if (originPointcut == null) 
                    return null;

                Pointcut pointcut = doDecoratePointcut(advisorContext, originPointcut);

                // originPointcut
                if (validatePointcut(advisorContext, originPointcut) == false)
                    return null;


                // 3.create advice class supplier
                Supplier<Class<? extends Advice>> adviceClassSupplier = this.doCreateAdviceClassSupplier(advisorContext);

                // 4.create advice class supplier
                Supplier<? extends Advice> adviceSupplier = this.doCreateAdviceSupplier(advisorContext, adviceClassSupplier);

                return new Advisor.PointcutAdvisor.Default(
                        advisorSpec.getAdvisorName(), 
                        advisorSpec.isPerInstance(),
                        adviceClassSupplier, adviceSupplier, 
                        pointcut,
                        advisorSpec.getOrder()
                );
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not create advisor. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            t.getMessage(), 
                            t );

                Throwables.throwIfRequired(t);
                return null;
            }
        }

        private boolean validateCondition(AdvisorContext advisorContext) {
            try {
                // validate factory classLoaderMatcher
                MatchingContext matchingContext = advisorContext.getMatchingContext();
                if (this.advisorSpec.isInheritClassLoaderMatcher() 
                        && advisorContext.getFactoryContext().getFactoryClassLoaderMatcher().matches(matchingContext) == false)
                    return false;

                // validate advisorSpec condition
                ElementMatcher<MatchingContext> condition = this.advisorSpec.getCondition();
                if (condition == null)
                    return true;

                return condition.matches(matchingContext);
            } catch (ExprParser.ExprParseException e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdvisorSpec with unparsable ConditionExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ConditionExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Syntax Error: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            e.getMessage()
                    );

                this.isValid = false;
            } catch (ExprParser.ExprLintException e) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Ignored AdvisorSpec with lint ConditionExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ConditionExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Lint message: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            e.getMessage()
                    );
                }
            } catch (MissingElementException e) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Ignored AdvisorSpec missing element under given ClassLoader. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ConditionExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            e.getMessage()
                    );
                }
            } catch (ExprParser.ExprUnknownException e) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled()) {
                    Throwable cause = e.getCause();
                    LOGGER.warn("Ignored AdvisorSpec with illegal ConditionExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ConditionExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            cause.getMessage(), 
                            cause
                    );
                }
            } catch (Exception e) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdvisorSpec with illegal ConditionExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            e.getMessage(), 
                            e
                    );
            }
            return false;
        }

        private P createPointcut(AdvisorContext advisorContext) {
            try {
                return doCreatePointcut(advisorContext);
            } catch (ExprParser.ExprParseException e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdvisorSpec with unparsable AspectJExprPointcut. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  PointcutExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Syntax Error: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            e.getMessage()
                    );

                this.isValid = false;
            } catch (ExprParser.ExprLintException e) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Ignored AdvisorSpec with lint AspectJExprPointcut. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  PointcutExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Lint message: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            e.getMessage()
                    );
                }
            } catch (ExprParser.ExprUnknownException e) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled()) {
                    Throwable cause = e.getCause();
                    LOGGER.warn("Ignored AdvisorSpec with illegal AspectJExprPointcut. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  PointcutExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            cause.getMessage(), 
                            cause
                    );
                }
            } catch (Exception e) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Ignored AdvisorSpec with illegal AspectJExprPointcut. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            e.getMessage(), 
                            e
                    );
                }
            }

            return null;
        }

        protected abstract P doCreatePointcut(AdvisorContext advisorContext);

        protected AspectJExprPointcut doCreateExprPointcut(AdvisorContext advisorContext, 
                String classLoaderExpression, String pointcutExpression, 
                TypeDescription pointcutDeclarationScope, 
                Map<String, Generic> pointcutParameters) {
            if (StringUtils.hasText(pointcutExpression) == false) {
                return null;
            }

            // try to replace placeholders in expression\
            if (classLoaderExpression != null)
                classLoaderExpression = advisorContext.getPlaceholderHelper().replace(classLoaderExpression);

            if (pointcutExpression != null)
                pointcutExpression = advisorContext.getPlaceholderHelper().replace(pointcutExpression);

            return pointcutDeclarationScope == null 
                    ? new AspectJExprPointcut(
                            advisorContext.getTypeWorld(), 
                            pointcutExpression)
                    : new AspectJExprPointcut(
                            advisorContext.getTypeWorld(), 
                            pointcutExpression, 
                            pointcutDeclarationScope, 
                            pointcutParameters)
            ;
        }

        protected abstract Pointcut doDecoratePointcut(AdvisorContext advisorContext, P pointcut);

        protected ElementMatcher<TypeDescription> decorateTypeMatcher(
                FactoryContext factoryContext, AdvisorSpec.PointcutAdvisorSpec pointcutAdvisorSpec, Pointcut pointcut) {
            ElementMatcher<TypeDescription> typeMatcher = pointcut.getTypeMatcher();
            if (pointcutAdvisorSpec.isInheritTypeMatcher() == false)
                return typeMatcher;

            if (typeMatcher != null)
                return new ElementMatcher.Junction.Conjunction<>(
                        factoryContext.getFactoryTypeMatcher(), typeMatcher);
            else
                return factoryContext.getFactoryTypeMatcher();
        }


        private boolean validatePointcut(AdvisorContext advisorContext, Pointcut pointcut) {
            try {
                return this.doValidatePointcut(advisorContext, pointcut);
            } catch (Exception e) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdvisorSpec with illegal Pointcut. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            advisorContext.getJoinpointClassLoaderName(), 
                            e.getMessage(), 
                            e
                    );
            }

            return false;
        }

        protected boolean doValidatePointcut(AdvisorContext advisorContext, Pointcut pointcut) {
            return true;
        }


        /**
         * delay advice class loading to reduce advisor matching time
         * 
         * @param advisorContext
         * @return
         */
        protected Supplier<Class<? extends Advice>> doCreateAdviceClassSupplier(AdvisorContext advisorContext) {
            return new Supplier<Class<? extends Advice>>() {

                private boolean isResolved;
                private Class<? extends Advice> adviceClass = null;

                @Override
                public Class<? extends Advice> get() {
                    if (isResolved)
                        return adviceClass;

                    // try to load advice class
                    ObjectFactory objectFactory = advisorContext.getObjectFactory();
                    String advisorName = advisorSpec.getAdvisorName();

                    String adviceClassName = advisorSpec.getAdviceClassName();
                    Class<? extends Advice> clazz = null;
                    try {
                        clazz = objectFactory.loadClass(adviceClassName);

                        if (objectFactory.isInstantiatable(clazz) == false) {
                            if (LOGGER.isWarnEnabled())
                                LOGGER.warn("Ignored AdvisorSpec with non top-level or nested, concrete AdviceClass. \n"
                                        + "  AdvisorSpec: {} \n"
                                        + "  AdviceClass: {} \n"
                                        + "  ClassLoader: {} \n",
                                        advisorName, 
                                        adviceClassName, 
                                        advisorContext.getJoinpointClassLoaderName()
                                );

                            return null;
                        }

                        if (Advice.class.isAssignableFrom(clazz) == false) {
                            if (LOGGER.isWarnEnabled())
                                LOGGER.warn("Ignored AdvisorSpec with non {} AdviceClass. \n"
                                        + "  AdvisorSpec: {} \n"
                                        + "  AdviceClass: {} \n"
                                        + "  ClassLoader: {} \n",
                                        Advice.class.getName(), 
                                        advisorName, 
                                        adviceClassName, 
                                        advisorContext.getJoinpointClassLoaderName()
                                );

                            return null;
                        }

                        this.adviceClass = clazz;
                        return this.adviceClass;
                    } catch (Throwable t) {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Could not load AdviceClass. \n"
                                    + "  AdvisorSpec: {} \n"
                                    + "  AdviceClass: {} \n"
                                    + "  ClassLoader: {} \n",
                                    advisorName, 
                                    adviceClassName, 
                                    advisorContext.getJoinpointClassLoaderName(), 
                                    t
                            );

                        Throwables.throwIfRequired(t);
                        return null;
                    } finally {
                        isResolved = true;
                    }
                }
            };
        }

        /**
         * @param advisorContext
         * @param adviceClassSupplier 
         * @return
         */
        protected Supplier<? extends Advice> doCreateAdviceSupplier(
                AdvisorContext advisorContext, Supplier<Class<? extends Advice>> adviceClassSupplier) {
            return () -> {
                Class<? extends Advice> adviceClass = adviceClassSupplier.get();
                if (adviceClass == null)
                    return null;

                // try to instantiate advice object
                Advice advice = null;
                try {
                    ObjectFactory objectFactory = advisorContext.getObjectFactory();

                    advice = (Advice) objectFactory.createObject(adviceClass);
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored AdvisorSpec with uninstantiable AdviceClass. \n"
                                + "  AdvisorSpec: {} \n"
                                + "  AdviceClass: {} \n"
                                + "  ClassLoader: {} \n",
                                advisorSpec.getAdvisorName(), 
                                adviceClass, 
                                advisorContext.getJoinpointClassLoaderName(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }

                return advice;
            };
        }

        @Override
        public String toString() {
            return this.advisorSpec.getAdvisorName();
        }
    }


    class ForPojoPointcut extends AbstractBase<AdvisorSpec.PojoPointcutSpec, Pointcut> {

        public ForPojoPointcut(AdvisorSpec.PojoPointcutSpec advisorSpec, AdviceMethodMatcher adviceMethodMatcher) {
            super(advisorSpec, adviceMethodMatcher);
        }

        @Override
        protected Pointcut doCreatePointcut(AdvisorContext advisorContext) {
            return advisorSpec.getPointcut();
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected Pointcut doDecoratePointcut(AdvisorContext advisorContext, Pointcut pointcut) {
            ElementMatcher<MethodDescription> methodMatcher = pointcut.getMethodMatcher();
            if (adviceMethodMatcher != null)
                methodMatcher = new ElementMatcher.Junction.Conjunction<MethodDescription>(
                        methodMatcher, adviceMethodMatcher);

            return new Pointcut.Default(
                    decorateTypeMatcher(advisorContext.getFactoryContext(), advisorSpec, pointcut),
                    methodMatcher
            );
        }
    }


    class ForExprPointcut extends AbstractBase<AdvisorSpec.ExprPointcutSpec, Pointcut> {

        public ForExprPointcut(AdvisorSpec.ExprPointcutSpec advisorSpec, AdviceMethodMatcher adviceMethodMatcher) {
            super(advisorSpec, adviceMethodMatcher);
        }

        @Override
        protected Pointcut doCreatePointcut(AdvisorContext advisorContext) {
            return doCreateExprPointcut(
                    advisorContext, 
                    advisorSpec.getClassLoaderExpression(),
                    advisorSpec.getPointcutExpression(), 
                    null, 
                    null);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected Pointcut doDecoratePointcut(AdvisorContext advisorContext, Pointcut pointcut) {
            ElementMatcher<MethodDescription> methodMatcher = pointcut.getMethodMatcher();
            if (adviceMethodMatcher == null)
                methodMatcher = new ElementMatcher.Junction.Conjunction<MethodDescription>(
                        methodMatcher, adviceMethodMatcher);

            return new Pointcut.Default(
                    decorateTypeMatcher(advisorContext.getFactoryContext(), advisorSpec, pointcut),
                    methodMatcher
            );
        }
    }


    class ForAspectJPointcut extends AbstractBase<AspectJPointcutAdvisorSpec, ExprPointcut> {

        private final ByteBuddyMaker classMaker;


        public ForAspectJPointcut(AopContext aopContext, 
                AspectJPointcutAdvisorSpec advisorSpec, AdviceMethodMatcher adviceMethodMatcher) {
            super(advisorSpec, adviceMethodMatcher);

            this.classMaker = new AdviceClassMaker.ByteBuddyMaker(aopContext, advisorSpec, adviceMethodMatcher);
        }


        @Override
        protected ExprPointcut doCreatePointcut(AdvisorContext advisorContext) {
            return this.doCreateExprPointcut(
                    advisorContext, 
                    advisorSpec.getClassLoaderExpression(),
                    advisorSpec.getPointcutExpression(), 
                    advisorSpec.getAspectJType(), 
                    advisorSpec.getPointcutParameterTypes());
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected Pointcut doDecoratePointcut(AdvisorContext advisorContext, ExprPointcut pointcut) {
            ElementMatcher<MethodDescription> methodMatcher = pointcut.getMethodMatcher();
            if (adviceMethodMatcher != null)
                methodMatcher = new ElementMatcher.Junction.Conjunction<MethodDescription>(
                        new ElementMatcher<MethodDescription>() {
                            @Override
                            public boolean matches(MethodDescription methodDescription) {
                                return pointcut.matches(methodDescription, advisorSpec);
                            }
                        },
                        adviceMethodMatcher
                );


            return new Pointcut.Default(
                    decorateTypeMatcher(advisorContext.getFactoryContext(), advisorSpec, pointcut),
                    methodMatcher
            );
        }

        @Override
        protected Supplier<Class<? extends Advice>> doCreateAdviceClassSupplier(AdvisorContext advisorContext) {
            return new Supplier<Class<? extends Advice>>() {

                private boolean isResolved;
                private Class<? extends Advice> adviceClass = null;

                @Override
                public Class<? extends Advice> get() {
                    if (isResolved) 
                        return this.adviceClass;

                    // try to make advice class
                    String aspectJClassName = advisorSpec.getAspectJType().getTypeName();
                    String advisorName = advisorSpec.getAdvisorName();
                    try {
                        advisorContext.getObjectFactory().loadClass(aspectJClassName);
                    } catch (Throwable t) {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Could not load AspectJClass. \n"
                                    + "  AdvisorSpec: {} \n"
                                    + "  AdviceJClass: {} \n"
                                    + "  ClassLoader: {} \n",
                                    advisorName, 
                                    aspectJClassName, 
                                    advisorContext.getJoinpointClassLoaderName(), 
                                    t
                            );

                        Throwables.throwIfRequired(t);
                        return null;
                    } finally {
                        this.isResolved = true;
                    }

                    try {
                        return (this.adviceClass = classMaker.make(advisorContext));
                    } catch (Throwable t) {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Could not generate adapter class for AspectJ advice method. \n"
                                    + "  AdvisorSpec: {} \n"
                                    + "  AdapterClass: {} \n"
                                    + "  ClassLoader: {} \n",
                                    advisorName, 
                                    advisorSpec.getAdviceClassName(), 
                                    advisorContext.getJoinpointClassLoaderName(), 
                                    t
                            );

                        Throwables.throwIfRequired(t);
                        return null;
                    } finally {
                        this.isResolved = true;
                    }
                }
            };
        }

        @Override
        protected Supplier<? extends Advice> doCreateAdviceSupplier(
                AdvisorContext advisorContext, Supplier<Class<? extends Advice>> adviceClassSupplier) {
            return new Supplier<Advice>() {

                private Class<?> aspectJClass;

                private Class<? extends Advice> adviceClass;
                private Constructor<?> adviceConstructor;

                @Override
                public Advice get() {
                    try {
                        if (aspectJClass == null)
                            aspectJClass =  advisorContext.getObjectFactory().loadClass(
                                    advisorSpec.getAspectJType().getTypeName());

                        if (adviceClass == null)
                            this.adviceClass = adviceClassSupplier.get();
                        if (adviceClass == null)
                            return null;

                        this.adviceConstructor = adviceClass.getConstructor(aspectJClass);
                        ReflectionUtils.makeAccessible(adviceClass, adviceConstructor);

                        // try to instantiate advice object
                        return (Advice) this.adviceConstructor.newInstance(
                                advisorContext.getObjectFactory().createObject(aspectJClass) );
                    } catch (Throwable t) {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Could not instantiate adapter class for AspectJ advice method. \n"
                                    + "  AdvisorSpec: {} \n"
                                    + "  AdapterClass: {} \n"
                                    + "  ClassLoader: {} \n",
                                    advisorSpec.getAdvisorName(), 
                                    advisorSpec.getAdviceClassName(), 
                                    advisorContext.getJoinpointClassLoaderName(), 
                                    t
                            );

                        Throwables.throwIfRequired(t);
                        return null;
                    }
                }
            };
        }
    }
}
