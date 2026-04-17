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

import io.gemini.aop.Advisor;
import io.gemini.aop.Advisor.PointcutAdvisor;
import io.gemini.aop.factory.AdvisorContext;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AdvisorSpec.PointcutAdvisorSpec;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.MatchingContext;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.condition.MissingElementException;
import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.core.OrderComparator;
import io.gemini.core.util.Assert;
import io.gemini.core.util.Throwables;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Factory interface for creating {@link io.gemini.aop.Advisor} instances from
 * {@link AdvisorSpec} descriptors.
 * <p>
 * The {@link Compound} implementation delegates to all registered creators in order.
 * {@link ForPointcutAdvisor} handles {@link AdvisorSpec.PointcutAdvisorSpec} by
 * creating a {@link DefaultPointcutAdvisor} that lazily loads the advice class and
 * creates advice instances on demand.
 * </p>
 *
 * @author   martin.liu
 */
public interface AdvisorCreator {

    Logger LOGGER = LoggerFactory.getLogger(AdvisorCreator.class);


    /**
     * Creates and returns an {@link io.gemini.aop.Advisor} from the given {@link AdvisorSpec},
     * or {@code null} if this creator does not handle the spec type.
     *
     * @param advisorContext the advisor context for the target class loader
     * @param advisorSpec    the advisor spec to create an advisor from
     * @return the created {@link io.gemini.aop.Advisor}, or {@code null}
     */
    Advisor create(AdvisorContext advisorContext, AdvisorSpec advisorSpec);


    /**
     * Delegates to all registered {@link AdvisorCreator} instances in order,
     * and returns the first non-null result.
     */
    @NoScanning
    class Compound implements AdvisorCreator {

        private final List<? extends AdvisorCreator> advisorCreators;

        public Compound(FactoryContext factoryContext) {
            List<? extends AdvisorCreator> advisorCreators = factoryContext.getObjectFactory().createObjectsImplementing(
                    AdvisorCreator.class, true,"factoryContext", factoryContext);
            this.advisorCreators = advisorCreators == null 
                    ? Collections.emptyList() : advisorCreators;

            OrderComparator.sort(this.advisorCreators);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Advisor create(AdvisorContext advisorContext, AdvisorSpec advisorSpec) {
            for (AdvisorCreator advisorCreator : advisorCreators) {
                try {
                    Advisor advisor = advisorCreator.create(advisorContext, advisorSpec);
                    if (advisor != null)
                        return advisor;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not create Advisor via '{}'. \n"
                                + "  Error reason: {} \n", 
                                advisorCreator, 
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
     * Abstract base providing common advisor creation logic: spec class filtering,
     * condition validation, and error handling.
     */
    abstract class AbstractBase<A extends AdvisorSpec> implements AdvisorCreator {

        private final AdviceCreator adviceCreator;
        private final PointcutCreator pointcutCreator;


        public AbstractBase(FactoryContext factoryContext) {
            this.adviceCreator = new AdviceCreator.Compound(factoryContext);
            this.pointcutCreator = new PointcutCreator.Compound(factoryContext);

        }

        protected AdviceCreator getAdviceCreator() {
            return adviceCreator;
        }

        protected PointcutCreator getPointcutCreator() {
            return pointcutCreator;
        }


        protected abstract Class<? extends AdvisorSpec> doGetAdvisorSpecClass();


        @Override
        @SuppressWarnings("unchecked")
        public Advisor create(AdvisorContext advisorContext, AdvisorSpec advisorSpec) {
            try {
                Class<? extends AdvisorSpec> advisorSpecClass = doGetAdvisorSpecClass();
                if (advisorSpecClass == null || advisorSpecClass.isAssignableFrom(advisorSpec.getClass()) == false)
                    return null;

                // 1.validate advisor condition
                if (validateCondition(advisorContext, advisorSpec) == false)
                    return null;

                // 2.create Advisor
                return doCreateAdvisor(advisorContext, (A) advisorSpec);
            } catch (IllegalSpecException e) {
                return new Advisor.IllegalAdvisor(advisorSpec);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not create Advisor. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Error reason: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            advisorContext.getTargetClassLoaderName(), 
                            e.getMessage(), 
                            e 
                    );

                return null;
            }
        }

        private boolean validateCondition(AdvisorContext advisorContext, AdvisorSpec advisorSpec) {
            ElementMatcher<MatchingContext> condition = advisorSpec.getCondition();
            if (condition == null)
                return advisorContext.acceptTargetClassloader();

            try {
                // validate advisorSpec condition
                return condition.matches(advisorContext.getMatchingContext());
            } catch (IllegalSpecException e) {
                throw e;
            } catch (ExprParser.ExprParseException e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdvisorSpec with unparsable ConditionExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ConditionExpression: {} \n"
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
                    LOGGER.warn("Ignored AdvisorSpec with lint ConditionExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  ConditionExpression: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  Lint message: {} \n", 
                            advisorSpec.getAdvisorName(), 
                            e.getExpression(), 
                            advisorContext.getTargetClassLoaderName(), 
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
                            advisorContext.getTargetClassLoaderName(), 
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
                            advisorContext.getTargetClassLoaderName(), 
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
                            advisorContext.getTargetClassLoaderName(), 
                            e.getMessage(), 
                            e
                    );
            }
            return false;
        }

        protected abstract Advisor doCreateAdvisor(AdvisorContext advisorContext, A advisorSpec);
    }


    /**
     * Creates a {@link DefaultPointcutAdvisor} from a {@link PointcutAdvisorSpec},
     * building the pointcut and wiring the advice creator.
     */
    class ForPointcutAdvisor extends AbstractBase<PointcutAdvisorSpec> {

        public ForPointcutAdvisor(FactoryContext factoryContext) {
            super(factoryContext);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends AdvisorSpec> doGetAdvisorSpecClass() {
            return PointcutAdvisorSpec.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Advisor doCreateAdvisor(AdvisorContext advisorContext, PointcutAdvisorSpec advisorSpec) {
            Pointcut pointcut = getPointcutCreator().create(advisorContext, advisorSpec);

            return new DefaultPointcutAdvisor(
                    advisorSpec, 
                    advisorContext,
                    getAdviceCreator(),
                    advisorSpec.getPointcutSpec().isBreakCircularity(),
                    pointcut
            );
        }
    }


    /**
     * Default {@link io.gemini.aop.Advisor.PointcutAdvisor} implementation that lazily loads
     * the advice class and creates advice instances on demand via the {@link AdviceCreator}.
     */
    class DefaultPointcutAdvisor extends PointcutAdvisor.AbstractBase {

        private final AdvisorContext advisorContext;
        private final AdvisorSpec advisorSpec;
        private final AdviceCreator adviceCreator;

        private Class<? extends Advice> adviceClass;


        public DefaultPointcutAdvisor(AdvisorSpec advisorSpec,
                AdvisorContext advisorContext, AdviceCreator adviceCreator, 
                boolean breakCircularity, Pointcut pointcut) {
            super(advisorSpec.getAdvisorName(), advisorSpec.getAdviceSpec().getAdviceKind(), 
                    advisorSpec.isPerInstance(), advisorSpec.getOrder(), 
                    breakCircularity, pointcut);

            Assert.notNull(advisorContext, "'advisorContext' must not be null");
            this.advisorContext = advisorContext;

            this.advisorSpec = advisorSpec;

            Assert.notNull(adviceCreator, "'adviceCreator' must not be null");
            this.adviceCreator = adviceCreator;
        }

        @Override
        public Class<? extends Advice> getAdviceClass() {
            if (adviceClass == null)
                this.adviceClass = this.adviceCreator.loadClass(advisorContext, advisorSpec);

            return adviceClass;
        }

        @Override
        public Advice getAdvice() {
            return adviceCreator.createInstance(advisorContext, advisorSpec, adviceClass);
        }
    }
}
