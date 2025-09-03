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
import java.util.Map;
import java.util.function.Supplier;

import org.aspectj.weaver.patterns.ParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.factory.AdvisorContext;
import io.gemini.aop.factory.support.AdviceClassMaker.ByteBuddyMaker;
import io.gemini.aop.factory.support.AdviceMethodMatcher.AspectJMethodMatcher;
import io.gemini.aop.factory.support.AdviceMethodMatcher.PojoMethodMatcher;
import io.gemini.aop.factory.support.AdviceMethodSpec.AspectJMethodSpec;
import io.gemini.aop.factory.support.AdviceMethodSpec.PojoMethodSpec;
import io.gemini.aop.matcher.ExprPointcut;
import io.gemini.aop.matcher.ExprPointcut.AspectJExprPointcut;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Pointcut;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.ReflectionUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.type.TypeDescription;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorRepository<T extends AdvisorSpec> {

    String getAdvisorName();

    Advisor create(AdvisorContext advisorContext);


    abstract class AbstractBase<T extends AdvisorSpec, P extends Pointcut> implements AdvisorRepository<T> {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AdvisorRepository.class);

        protected T advisorSpec;
        protected volatile boolean isValid = true;


        public AbstractBase(T advisorSpec) {
            this.advisorSpec = advisorSpec;
        }

        @Override
        public String getAdvisorName() {
            return advisorSpec.getAdvisorName();
        }

        @Override
        public Advisor create(AdvisorContext advisorContext) {
            if(this.isValid == false)
                return null;

            try {
                // 1.validate advisor condition
                if(this.advisorSpec.getCondition().matches(advisorContext.getConditionContext()) == false) {
                    return null;
                }

                // 2.try to create Pointcut
                P originPointcut = doCreatePointcut(advisorContext);
                if(originPointcut == null) 
                    return null;

                if(doValidatePointcut(advisorContext, originPointcut) == false) {
                    return null;
                }

                Pointcut pointcut = doDecoratePointcut(advisorContext, originPointcut);


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
            } catch(Throwable t) {
                LOGGER.warn("Failed to create advisor. \n  AdvisorSpec: {} \n  ClassLoader: {} \n  Error reason: {} \n", 
                        advisorSpec.getAdvisorName(), advisorContext.getJoinpointClassLoaderName(), t.getMessage(), t);

//                this.isValid = false;
                return null;
            }
        }

        /**
         * @param advisorContext
         */
        protected abstract P doCreatePointcut(AdvisorContext advisorContext);


        protected AspectJExprPointcut doCreateExprPointcut(AdvisorContext advisorContext, 
                String pointcutExpression, 
                TypeDescription pointcutDeclarationScope, 
                Map<String, TypeDescription> pointcutParameters) {
            if(StringUtils.hasText(pointcutExpression) == false) {
                return null;
            }

            // try to replace placeholders in expression
            pointcutExpression = advisorContext.getPlaceholderHelper().replace(pointcutExpression);

            return pointcutDeclarationScope != null 
                    ? new AspectJExprPointcut(advisorContext.getTypeWorld(), 
                            pointcutExpression, 
                            pointcutDeclarationScope, 
                            pointcutParameters)
                    : new AspectJExprPointcut(advisorContext.getTypeWorld(), 
                            pointcutExpression);
        }

        protected boolean doValidatePointcut(AdvisorContext advisorContext, P pointcut) {
            if(pointcut == null) {
                this.isValid = false;
                return false;
            }

            if(pointcut instanceof AspectJExprPointcut) {
                AspectJExprPointcut aspectJExpressionPointcut = (AspectJExprPointcut) pointcut;
                try {
                    aspectJExpressionPointcut.getPointcut();
                    return true;
                } catch(Throwable t) {
                    String advisorName = advisorSpec.getAdvisorName();
                    if(t instanceof IllegalArgumentException && t.getCause() instanceof ParserException) {
                        LOGGER.warn("Ignored AdvisorSpec with unparsable AspectJ ExprPointcut. \n  AdvisorSpec: {} \n  PointcutExpression: {} \n  ClassLoader: '{}' \n", 
                                advisorName, aspectJExpressionPointcut.getPointcutExpression(), advisorContext.getClassLoader(), t);

                        this.isValid = false;
                        return false;
                    } else {
                        if(advisorContext.isValidateContext() == false) {
                            LOGGER.warn("Ignored AdvisorSpec with invalid AspectJ ExprPointcut. \n  AdvisorSpec: {} \n  PointcutExpression: {} \n  ClassLoader: {} \n  Error reason: {} \n", 
                                    advisorName, aspectJExpressionPointcut.getPointcutExpression(), advisorContext.getJoinpointClassLoaderName(), t.getMessage(), t);
                        }

                        return false;
                    }
                }
            }

            return true;
        }

        protected abstract Pointcut doDecoratePointcut(AdvisorContext advisorContext, P pointcut);


        /**
         * delay advice class loading to reduce advisor matching time
         * 
         * @param advisorContext
         * @return
         */
        @SuppressWarnings("unchecked")
        protected Supplier<Class<? extends Advice>> doCreateAdviceClassSupplier(AdvisorContext advisorContext) {
            return new Supplier<Class<? extends Advice>>() {

                private boolean isResolved;
                private Class<? extends Advice> adviceClass = null;

                @Override
                public Class<? extends Advice> get() {
                    if(isResolved)
                        return adviceClass;

                    // try to load advice class
                    ObjectFactory objectFactory = advisorContext.getObjectFactory();
                    String advisorName = advisorSpec.getAdvisorName();

                    String adviceClassName = advisorSpec.getAdviceClassName();
                    Class<?> clazz = null;
                    try {
                        clazz = objectFactory.loadClass(adviceClassName);

                        if(objectFactory.isInstantiatable(clazz) == false) {
                            LOGGER.warn("Ignored AdvisorSpec with non top-level or nested, concrete AdviceClass. \n  AdvisorSpec: {} \n  AdviceClass: {} \n  ClassLoader: '{}' \n",
                                    advisorName, adviceClassName, advisorContext.getClassLoader());
                            return null;
                        }

                        if(Advice.class.isAssignableFrom(clazz) == false) {
                            LOGGER.warn("Ignored AdvisorSpec with non {} AdviceClass. \n  AdvisorSpec: {} \n  AdviceClass: {} \n  ClassLoader: '{}' \n",
                                    Advice.class.getName(), advisorName, adviceClassName, advisorContext.getClassLoader());
                            return null;
                        }

                        this.adviceClass = (Class<? extends Advice>) clazz;
                        return this.adviceClass;
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to load AdviceClass. \n  AdvisorSpec: {} \n  AdviceClass: {} \n  ClassLoader: '{}' \n",
                                advisorName, adviceClassName, advisorContext.getClassLoader(), t);
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
                if(adviceClass == null)
                    return null;

                // try to instantiate advice object
                Advice advice = null;
                try {
                    ObjectFactory objectFactory = advisorContext.getObjectFactory();

                    advice = (Advice) objectFactory.createObject(adviceClass);
                } catch (Exception e) {
                    LOGGER.warn("Ignored AdvisorSpec with uninstantiable AdviceClass. \n  AdvisorSpec: {} \n  AdviceClass: {} \n  ClassLoader: '{}' \n",
                            advisorSpec.getAdvisorName(), adviceClass, advisorContext.getClassLoader(), e);
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

        public ForPojoPointcut(AdvisorSpec.PojoPointcutSpec advisorSpec) {
            super(advisorSpec);
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
            TypeDescription adviceType = advisorContext.getTypePool()
                    .describe(advisorSpec.getAdviceClassName())
                    .resolve();
            PojoMethodSpec pojoMethodSpec = new PojoMethodSpec( advisorSpec.getAdvisorName(), adviceType );
            if(pojoMethodSpec.isValid() == false)
                return null;

            return new Pointcut.Default(
                    pointcut.getTypeMatcher(), 
                    new PojoMethodMatcher( pojoMethodSpec, pointcut.getMethodMatcher() )
            );
        }
    }


    class ForExprPointcut extends AbstractBase<AdvisorSpec.ExprPointcutSpec, Pointcut> {

        public ForExprPointcut(AdvisorSpec.ExprPointcutSpec advisorSpec) {
            super(advisorSpec);
        }

        @Override
        protected Pointcut doCreatePointcut(AdvisorContext advisorContext) {
            return doCreateExprPointcut(advisorContext, 
                    advisorSpec.getPointcutExpression(), 
                    null, 
                    null);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected Pointcut doDecoratePointcut(AdvisorContext advisorContext, Pointcut pointcut) {
            TypeDescription adviceType = advisorContext.getTypePool()
                    .describe(advisorSpec.getAdviceClassName())
                    .resolve();
            PojoMethodSpec pojoMethodSpec = new PojoMethodSpec( advisorSpec.getAdvisorName(), adviceType );
            if(pojoMethodSpec.isValid() == false)
                return null;

            return new Pointcut.Default(
                    pointcut.getTypeMatcher(),
                    new PojoMethodMatcher( pojoMethodSpec, pointcut.getMethodMatcher() )
            );
        }
    }


    class ForAspectJAdvice extends AbstractBase<AdvisorSpec.ExprPointcutSpec, ExprPointcut> {

        private final AspectJMethodSpec aspectJMethodSpec;
        private final ByteBuddyMaker classMaker;


        public ForAspectJAdvice(AdvisorSpec.ExprPointcutSpec advisorSpec, AspectJMethodSpec aspectJMethodSpec) {
            super(advisorSpec);

            this.aspectJMethodSpec = aspectJMethodSpec;
            this.classMaker = new AdviceClassMaker.ByteBuddyMaker(advisorSpec.getAdviceClassName(), aspectJMethodSpec);
        }


        @Override
        protected ExprPointcut doCreatePointcut(AdvisorContext advisorContext) {
            return this.doCreateExprPointcut(advisorContext, 
                    advisorSpec.getPointcutExpression(), 
                    aspectJMethodSpec.getAdviceTypeDescription(), 
                    aspectJMethodSpec.getPointcutParameterTypes());
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected Pointcut doDecoratePointcut(AdvisorContext advisorContext, ExprPointcut pointcut) {
            return new Pointcut.Default(
                    pointcut.getTypeMatcher(),
                    new AspectJMethodMatcher(pointcut, aspectJMethodSpec) 
            );
        }

        @Override
        protected Supplier<Class<? extends Advice>> doCreateAdviceClassSupplier(AdvisorContext advisorContext) {
            return new Supplier<Class<? extends Advice>>() {

                private boolean isResolved;
                private Class<? extends Advice> adviceClass = null;

                @Override
                public Class<? extends Advice> get() {
                    if(isResolved) 
                        return this.adviceClass;

                    // try to make advice class
                    String aspectJClassName = aspectJMethodSpec.getAdviceTypeDescription().getTypeName();
                    String advisorName = advisorSpec.getAdvisorName();
                    try {
                        advisorContext.getObjectFactory().loadClass(aspectJClassName);
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to load AspectJClass. \n  AdvisorSpec: {} \n  AdviceJClass: {} \n  ClassLoader: '{}' \n",
                                advisorName, aspectJClassName, advisorContext.getClassLoader(), t);
                        return null;
                    } finally {
                        this.isResolved = true;
                    }

                    try {
                        this.adviceClass = classMaker.make(advisorContext);
                        return this.adviceClass;
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to generate adapter class for AspectJ advice method. \n  AdvisorSpec: {} \n  AdapterClass: {} \n  ClassLoader: '{}' \n",
                                advisorName, classMaker.getAdviceClassName(), advisorContext.getClassLoader(), t);
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

                private final Class<?> aspectJClass = advisorContext.getObjectFactory().loadClass(
                        aspectJMethodSpec.getAdviceTypeDescription().getTypeName());

                private Class<? extends Advice> adviceClass;
                private Constructor<?> adviceConstructor;

                @Override
                public Advice get() {
                    try {
                        if(adviceClass == null)
                            this.adviceClass = adviceClassSupplier.get();
                        if(adviceClass == null)
                            return null;

                        this.adviceConstructor = adviceClass.getConstructor(aspectJClass);
                        ReflectionUtils.makeAccessible(adviceClass, adviceConstructor);

                        // try to instantiate advice object
                        return (Advice) this.adviceConstructor.newInstance(
                                advisorContext.getObjectFactory().createObject(aspectJClass) );
                    } catch (Throwable t) {
                        LOGGER.warn("Failed to instantiate adapter class for AspectJ advice method. \n  AdvisorSpec: {} \n  AdapterClass: {} \n  ClassLoader: '{}' \n",
                                advisorSpec.getAdvisorName(), classMaker.getAdviceClassName(), advisorContext.getClassLoader(), t);
                        return null;
                    }
                }
            };
        }
    }
}
