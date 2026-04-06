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

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.AdvisorContext;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AdvisorSpec.PointcutAdvisorSpec;
import io.gemini.aop.factory.support.PointcutSpec.AspectJPointcutSpec;
import io.gemini.aop.factory.support.PointcutSpec.ExprPointcutSpec;
import io.gemini.aop.factory.support.PointcutSpec.PojoPointcutSpec;
import io.gemini.aop.matcher.ExprPointcut;
import io.gemini.aop.matcher.ExprPointcut.AspectJExprPointcut;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.Pointcut;
import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.core.OrderComparator;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;


public interface PointcutCreator {

    Logger LOGGER = LoggerFactory.getLogger(PointcutCreator.class);


    Pointcut create(AdvisorContext advisorContext, PointcutAdvisorSpec advisorSpec);


    @NoScanning
    class Compound implements PointcutCreator {

        private final List<? extends PointcutCreator> pointcutCreators;


        public Compound(FactoryContext factoryContext) {
            List<? extends PointcutCreator> pointcutCreators = factoryContext.getObjectFactory()
                    .createObjectsImplementing(PointcutCreator.class, true);
            this.pointcutCreators = pointcutCreators == null 
                    ? Collections.emptyList() : pointcutCreators;

            OrderComparator.sort(pointcutCreators);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pointcut create(AdvisorContext advisorContext, PointcutAdvisorSpec advisorSpec) {
            for (PointcutCreator pointcutCreator : pointcutCreators) {
                try {
                    Pointcut pointcut = pointcutCreator.create(advisorContext, advisorSpec);
                    if (pointcut != null) 
                        return pointcut;
                } catch (IllegalSpecException e) {
                    throw e;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not create Pointcut via '{}'. \n"
                                + "  Error reason: {} \n", 
                                pointcutCreator, 
                                t.getMessage(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }
            }
            return null;
        }
    }


    abstract class AbstractBase<PS extends PointcutSpec, P extends Pointcut> implements PointcutCreator {

        @SuppressWarnings("unchecked")
        @Override
        public Pointcut create(AdvisorContext advisorContext, PointcutAdvisorSpec advisorSpec) {
            Class<? extends PointcutSpec> pointcutSpecClass = doGetSpecClass();
            PointcutSpec pointcutSpec = advisorSpec.getPointcutSpec();
            if (pointcutSpecClass == null || pointcutSpecClass.isAssignableFrom(pointcutSpec.getClass()) == false)
                return null;

            // 1.try to create Pointcut
            P originPointcut = null;
            try {
                originPointcut = doCreatePointcut(advisorContext, (PS) pointcutSpec);
            } catch (IllegalSpecException e) {
                throw e;
            } catch (ExprParser.ExprParseException e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdvisorSpec with unparsable AspectJExprPointcut. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  PointcutExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Syntax Error: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getTargetClassLoaderName(), 
                            e.getMessage()
                    );

                throw new IllegalSpecException();
            } catch (ExprParser.ExprLintException e) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Ignored AdvisorSpec with lint AspectJExprPointcut. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  PointcutExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Lint message: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getTargetClassLoaderName(), 
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
                            advisorContext.getTargetClassLoaderName(), 
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
                            advisorContext.getTargetClassLoaderName(), 
                            e.getMessage(), 
                            e
                    );
                }
            }

            if (originPointcut == null) 
                return null;


            // 2.decorate Pointcut
            Pointcut pointcut = doDecoratePointcut(advisorContext, (PS) pointcutSpec, originPointcut);


            // 3.validate Pointcut
            try {
                if (this.doValidatePointcut(advisorContext, pointcut) == false)
                    return null;
            } catch (IllegalSpecException e) {
                throw e;
            } catch (Throwable t) {
                if (advisorContext.isValidateContext() == false && LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdvisorSpec with illegal Pointcut. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            advisorContext.getTargetClassLoaderName(), 
                            t.getMessage(), 
                            t
                    );

                Throwables.throwIfRequired(t);
            }

            return pointcut;
        }

        protected abstract Class<? extends PointcutSpec> doGetSpecClass();

        protected abstract P doCreatePointcut(AdvisorContext advisorContext, PS pointcutSpec);

        protected Pointcut doDecoratePointcut(AdvisorContext advisorContext, PS pointcutSpec, P pointcut) {
            ElementMatcher<MethodDescription> methodMatcher = pointcut.getMethodMatcher();
            if (pointcutSpec.getAdviceMethodMatcher() != null)
                methodMatcher = new ElementMatcher.Junction.Conjunction<MethodDescription>(
                        methodMatcher, pointcutSpec.getAdviceMethodMatcher());

            return new Pointcut.Default( 
                    pointcut.getTypeMatcher(), methodMatcher);
        }

        protected boolean doValidatePointcut(AdvisorContext advisorContext, Pointcut pointcut) {
            return true;
        }
    }


    class PojoPointcutCreator extends AbstractBase<PojoPointcutSpec, Pointcut> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends PointcutSpec> doGetSpecClass() {
            return PojoPointcutSpec.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Pointcut doCreatePointcut(AdvisorContext advisorContext, PojoPointcutSpec pointcutSpec) {
            return advisorContext.getObjectFactory().createObject(
                    pointcutSpec.getPointcutClass() );
        }
    }


    class ExprPointcutCreator extends AbstractBase<ExprPointcutSpec, Pointcut> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends PointcutSpec> doGetSpecClass() {
            return ExprPointcutSpec.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Pointcut doCreatePointcut(AdvisorContext advisorContext, ExprPointcutSpec pointcutSpec) {
            String pointcutExpression = pointcutSpec.getPointcutExpression();
            if (StringUtils.hasText(pointcutExpression) == false) {
                return null;
            }

            // try to replace placeholders in expression
            if (pointcutExpression != null)
                pointcutExpression = advisorContext.getPlaceholderHelper().replace(pointcutExpression);

            return new AspectJExprPointcut(advisorContext.getTypeWorld(), pointcutExpression);
        }
    }


    class AspectJPointcutCreator extends AbstractBase<AspectJPointcutSpec, ExprPointcut> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends PointcutSpec> doGetSpecClass() {
            return AspectJPointcutSpec.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected ExprPointcut doCreatePointcut(AdvisorContext advisorContext, AspectJPointcutSpec pointcutSpec) {
            String pointcutExpression = pointcutSpec.getPointcutExpression();
            if (StringUtils.hasText(pointcutExpression) == false) {
                return null;
            }

            // try to replace placeholders in expression
            if (pointcutExpression != null)
                pointcutExpression = advisorContext.getPlaceholderHelper().replace(pointcutExpression);

            return new AspectJExprPointcut(advisorContext.getTypeWorld(), 
                    pointcutExpression,
                    pointcutSpec.getAdviceType(), 
                    pointcutSpec.getPointcutParameterTypes());
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected Pointcut doDecoratePointcut(AdvisorContext advisorContext, 
                AspectJPointcutSpec pointcutSpec, ExprPointcut pointcut) {
            ElementMatcher<MethodDescription> methodMatcher = pointcut.getMethodMatcher();
            ElementMatcher<MethodDescription> adviceMethodMatcher = pointcutSpec.getAdviceMethodMatcher();
            if (adviceMethodMatcher != null)
                methodMatcher = new ElementMatcher.Junction.Conjunction<MethodDescription>(
                        new ElementMatcher<MethodDescription>() {
                            @Override
                            public boolean matches(MethodDescription targetMethod) {
                                return pointcut.matches(targetMethod, pointcutSpec.getPointcutParameterMatcher());
                            }
                        },
                        adviceMethodMatcher
                );


            return new Pointcut.Default(
                    pointcut.getTypeMatcher(), methodMatcher);
        }
    }
}
