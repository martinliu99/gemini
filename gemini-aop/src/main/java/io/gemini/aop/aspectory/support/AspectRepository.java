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
package io.gemini.aop.aspectory.support;

import java.lang.reflect.Constructor;
import java.util.function.Supplier;

import org.aspectj.weaver.patterns.ParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Aspect;
import io.gemini.aop.ExprPointcut;
import io.gemini.aop.ExprPointcut.AspectJExprPointcut;
import io.gemini.aop.aspectory.AspectContext;
import io.gemini.aop.aspectory.support.AspectJAdvice.ClassMaker;
import io.gemini.aop.aspectory.support.AspectJAdvice.MethodSpec;
import io.gemini.api.aspect.Advice;
import io.gemini.api.aspect.AspectSpec;
import io.gemini.api.aspect.Pointcut;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.ReflectionUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AspectRepository<T extends AspectSpec> {

    Aspect create(AspectContext aspectContext);


    abstract class AbstractBase<T extends AspectSpec> implements AspectRepository<T> {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AspectRepository.class);

        protected T aspectSpec;
        protected volatile boolean isValid = true;


        public AbstractBase(T aspectSpec) {
            this.aspectSpec = aspectSpec;
        }

        @Override
        public Aspect create(AspectContext aspectContext) {
            if(this.isValid == false)
                return null;

            try {
                // try to create Pointcut
                Pointcut pointcut = doCreatePointcut(aspectContext);
                if(pointcut == null) {
                    return null;
                }

                // create advice class supplier
                Supplier<Class<? extends Advice>> adviceClassSupplier = this.doCreateAdviceClassSupplier(aspectContext);

                // create advice class supplier
                Supplier<? extends Advice> adviceSupplier = this.doCreateAdviceSupplier(aspectContext, adviceClassSupplier);

                return new Aspect.PointcutAspect.Default(
                        aspectSpec.getAspectName(), 
                        aspectSpec.isPerInstance(),
                        adviceClassSupplier, adviceSupplier, 
                        pointcut,
                        aspectSpec.getOrder()
                );
            } catch(Throwable t) {
                LOGGER.warn("Failed to create aspect with AspectSpec '{}'.", aspectSpec.getAspectName(), t);

                this.isValid = false;
                return null;
            }
        }

        /**
         * @param aspectContext
         */
        protected abstract Pointcut doCreatePointcut(AspectContext aspectContext);


        protected AspectJExprPointcut doCreateExprPointcut(AspectContext aspectContext, String pointcutExpression, 
                TypeDescription pointcutDeclarationScope, String[] pointcutParameterNames, TypeDescription[] pointcutParameterTypes) {
            if(StringUtils.hasText(pointcutExpression) == false) {
                return null;
            }

            // try to replace placeholders in expression
            pointcutExpression = aspectContext.getPlaceholderHelper().replace(pointcutExpression);

            AspectJExprPointcut expressionPointcut = pointcutDeclarationScope != null 
                    ? new AspectJExprPointcut(pointcutDeclarationScope, pointcutParameterNames, pointcutParameterTypes)
                    : new AspectJExprPointcut();

            expressionPointcut.setPointcutExpr(pointcutExpression);
            expressionPointcut.setTypeWorld(aspectContext.getTypeWorld());

            // TODO: now just use defaultClassLoaderMatcher definition
            expressionPointcut.setClassLoaderMatcher(aspectContext.getDefaultClassLoaderMatcher());

            return expressionPointcut;
        }

        protected boolean doValidatePointcut(AspectContext aspectContext, Pointcut pointcut) {
            if(pointcut == null) {
                this.isValid = false;
                return false;
            }

            ElementMatcher<String> classLoaderMatcher = pointcut.getClassLoaderMatcher() == null 
                    ? aspectContext.getDefaultClassLoaderMatcher() 
                    : pointcut.getClassLoaderMatcher();
            if(classLoaderMatcher.matches(aspectContext.getJoinpointClassLoaderName()) == false) {
                return false;
            }

            if(pointcut instanceof AspectJExprPointcut) {
                AspectJExprPointcut aspectJExpressionPointcut = (AspectJExprPointcut) pointcut;
                try {
                    aspectJExpressionPointcut.checkReadyToMatch();
                    return true;
                } catch(Throwable t) {
                    String aspectName = aspectSpec.getAspectName();
                    if(t instanceof IllegalArgumentException && t.getCause() instanceof ParserException) {
                        LOGGER.warn("Ignored AspectSpec with unparsable AspectJ ExprPointcut. \n  AspectSpec: {} \n  PointcutExpression: {} \n    ClassLoader: '{}' \n", 
                                aspectName, aspectJExpressionPointcut.getPointcutExpr(), aspectContext.getClassLoader(), t);

                        this.isValid = false;
                        return false;
                    } else {
                        if(aspectContext.isValidateContext() == false) {
                            LOGGER.warn("Ignored AspectSpec with invalid AspectJ ExprPointcut. \n  AspectSpec: {} \n  PointcutExpression: {} \n  ClassLoader: '{}' \n", 
                                    aspectName, aspectJExpressionPointcut.getPointcutExpr(), aspectContext.getClassLoader(), t);
                        }

                        return false;
                    }
                }
            }

            return true;
        }


        /**
         * delay advice class loading to reduce aspect matching time
         * 
         * @param aspectContext
         * @return
         */
        @SuppressWarnings("unchecked")
        protected Supplier<Class<? extends Advice>> doCreateAdviceClassSupplier(AspectContext aspectContext) {
            return new Supplier<Class<? extends Advice>>() {

                private boolean isResolved;
                private Class<? extends Advice> adviceClass = null;

                @Override
                public Class<? extends Advice> get() {
                    if(isResolved)
                        return adviceClass;

                    // try to load advice class
                    ObjectFactory objectFactory = aspectContext.getObjectFactory();
                    String aspectName = aspectSpec.getAspectName();

                    String adviceClassName = aspectSpec.getAdviceClassName();
                    Class<?> clazz = null;
                    try {
                        clazz = objectFactory.loadClass(adviceClassName);

                        if(objectFactory.isInstantiatable(clazz) == false) {
                            LOGGER.warn("Ignored AspectSpec with non top-level or nested, concrete AdviceClass. \n  AspectSpec: {} \n  AdviceClass: {} \n  ClassLoader: '{}' \n",
                                    aspectName, adviceClassName, aspectContext.getClassLoader());
                            return null;
                        }

                        if(Advice.class.isAssignableFrom(clazz) == false) {
                            LOGGER.warn("Ignored AspectSpec with non {} AdviceClass. \n  AspectSpec: {} \n  AdviceClass: {} \n  ClassLoader: '{}' \n",
                                    Advice.class.getName(), aspectName, adviceClassName, aspectContext.getClassLoader());
                            return null;
                        }

                        this.adviceClass = (Class<? extends Advice>) clazz;
                        return this.adviceClass;
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to load AdviceClass. \n  AspectSpec: {} \n  AdviceClass: {} \n  ClassLoader: '{}' \n",
                                aspectName, adviceClassName, aspectContext.getClassLoader(), t);
                        return null;
                    } finally {
                        isResolved = true;
                    }
                }
            };
        }

        /**
         * @param aspectContext
         * @param adviceClassSupplier 
         * @return
         */
        protected Supplier<? extends Advice> doCreateAdviceSupplier(
                AspectContext aspectContext, Supplier<Class<? extends Advice>> adviceClassSupplier) {
            return () -> {
                Class<? extends Advice> adviceClass = adviceClassSupplier.get();
                if(adviceClass == null)
                    return null;

                // try to instantiate advice object
                Advice advice = null;
                try {
                    ObjectFactory objectFactory = aspectContext.getObjectFactory();

                    advice = (Advice) objectFactory.createObject(adviceClass);
                } catch (Exception e) {
                    LOGGER.warn("Ignored AspectSpec with uninstantiable AdviceClass. \n  AspectSpec: {} \n  AdviceClass: {} \n  ClassLoader: '{}' \n",
                            aspectSpec.getAspectName(), adviceClass, aspectContext.getClassLoader(), e);
                }

                return advice;
            };
        }

        @Override
        public String toString() {
            return this.aspectSpec.getAspectName();
        }
    }


    class ForPojoPointcut extends AbstractBase<AspectSpec.PojoPointcutSpec> {

        public ForPojoPointcut(AspectSpec.PojoPointcutSpec aspectSpec) {
            super(aspectSpec);
        }

        @Override
        protected Pointcut doCreatePointcut(AspectContext aspectContext) {
            Pointcut pointcut = aspectSpec.getPointcut();
            if(this.doValidatePointcut(aspectContext, pointcut) == false) {
                return null;
            }

            TypeDescription adviceType = aspectContext.getTypePool()
                    .describe(aspectSpec.getAdviceClassName())
                    .resolve();
            AdviceAwareMethodMatcher.PojoAdvice methodMatcher = new AdviceAwareMethodMatcher.PojoAdvice(
                    aspectSpec.getAspectName(), pointcut.getMethodMatcher(), adviceType);
            if(methodMatcher.isValid == false)
                return null;

            return new Pointcut.Default(
                    pointcut.getClassLoaderMatcher() != null ? pointcut.getClassLoaderMatcher() : aspectContext.getDefaultClassLoaderMatcher(), 
                    pointcut.getTypeMatcher(), 
                    methodMatcher
            );
        }
    }


    class ForExprPointcut extends AbstractBase<AspectSpec.ExprPointcutSpec> {

        public ForExprPointcut(AspectSpec.ExprPointcutSpec aspectSpec) {
            super(aspectSpec);
        }

        @Override
        protected Pointcut doCreatePointcut(AspectContext aspectContext) {
            ExprPointcut exprPointcut = this.doCreateExprPointcut(aspectContext, aspectSpec.getPointcutExpression(), null, null, null);
            if(this.doValidatePointcut(aspectContext, exprPointcut) == false) {
                return null;
            }

            TypeDescription adviceType = aspectContext.getTypePool()
                    .describe(aspectSpec.getAdviceClassName())
                    .resolve();
            AdviceAwareMethodMatcher.PojoAdvice methodMatcher = new AdviceAwareMethodMatcher.PojoAdvice(
                    aspectSpec.getAspectName(), exprPointcut.getMethodMatcher(), adviceType);
            if(methodMatcher.isValid() == false)
                return null;

            return new Pointcut.Default(
                    exprPointcut.getClassLoaderMatcher(),
                    exprPointcut.getTypeMatcher(),
                    methodMatcher
            );
        }
    }


    class ForAspectJAdvice extends AbstractBase<AspectSpec.ExprPointcutSpec> {

        private final ClassMaker classMaker;


        public ForAspectJAdvice(AspectSpec.ExprPointcutSpec aspectSpec, ClassMaker classMaker) {
            super(aspectSpec);
            this.classMaker = classMaker;
        }


        @Override
        protected Pointcut doCreatePointcut(AspectContext aspectContext) {
            MethodSpec methodSpec = classMaker.getMethodSpec();
            String[] pointcutParameterNames = methodSpec.getPointcutParameterNames().toArray(new String[] {});
            TypeDescription[] pointcutParameterTypes = methodSpec.getPointcutParameterTypes().toArray(new TypeDescription[] {});
            String pointcutExpression = aspectSpec.getPointcutExpression();

            ExprPointcut exprPointcut = this.doCreateExprPointcut(aspectContext, pointcutExpression, classMaker.getAspectJTypeDescription(), pointcutParameterNames, pointcutParameterTypes);
            if(this.doValidatePointcut(aspectContext, exprPointcut) == false) {
                return null;
            }

            return new Pointcut.Default(
                    exprPointcut.getClassLoaderMatcher(),
                    exprPointcut.getTypeMatcher(),
                    new AdviceAwareMethodMatcher.AspectJAdvice(exprPointcut, methodSpec) 
            );
        }

        @Override
        protected Supplier<Class<? extends Advice>> doCreateAdviceClassSupplier(AspectContext aspectContext) {
            return new Supplier<Class<? extends Advice>>() {

                private boolean isResolved;
                private Class<? extends Advice> adviceClass = null;

                @Override
                public Class<? extends Advice> get() {
                    if(isResolved) 
                        return this.adviceClass;

                    // try to make advice class
                    String aspectJClassName = classMaker.getAspectJTypeDescription().getTypeName();
                    String aspectName = aspectSpec.getAspectName();
                    try {
                        aspectContext.getObjectFactory().loadClass(aspectJClassName);
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to load AspectJClass. \n  AspectSpec: {} \n  AdviceJClass: {} \n  ClassLoader: '{}' \n",
                                aspectName, aspectJClassName, aspectContext.getClassLoader(), t);
                        return null;
                    } finally {
                        this.isResolved = true;
                    }

                    try {
                        this.adviceClass = classMaker.make(aspectContext);
                        return this.adviceClass;
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to generate adapter class for AspectJ advice method. \n  AspectSpec: {} \n  AdapterClass: {} \n  ClassLoader: '{}' \n",
                                aspectName, classMaker.getAdviceClassName(), aspectContext.getClassLoader(), t);
                        return null;
                    } finally {
                        this.isResolved = true;
                    }
                }
            };
        }

        @Override
        protected Supplier<? extends Advice> doCreateAdviceSupplier(
                AspectContext aspectContext, Supplier<Class<? extends Advice>> adviceClassSupplier) {
            return new Supplier<Advice>() {

                private final Class<?> aspectJClass = aspectContext.getObjectFactory().loadClass(
                        classMaker.getAspectJTypeDescription().getTypeName());

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
                                aspectContext.getObjectFactory().createObject(aspectJClass) );
                    } catch (Throwable t) {
                        LOGGER.warn("Failed to instantiate adapter class for AspectJ advice method. \n  AspectSpec: {} \n  AdapterClass: {} \n  ClassLoader: '{}' \n",
                                aspectSpec.getAspectName(), classMaker.getAdviceClassName(), aspectContext.getClassLoader(), t);
                        return null;
                    }
                }
            };
        }
    }
}
