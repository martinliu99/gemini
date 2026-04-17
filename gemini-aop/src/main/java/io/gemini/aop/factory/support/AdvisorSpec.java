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

import io.gemini.api.aop.MatchingContext;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Describes the full specification of an advisor parsed from an aspect application.
 * <p>
 * Combines an advisor name, an optional {@link io.gemini.api.aop.MatchingContext} condition,
 * an {@link AdviceSpec}, per-instance flag, and ordering value.
 * The {@link PointcutAdvisorSpec} sub-interface additionally carries a {@link PointcutSpec}.
 * </p>
 *
 * @author   martin.liu
 */
public interface AdvisorSpec {

    ElementMatcher<MatchingContext> DEFAULT_CONDITION = null;


    /**
     * Returns the unique name of this advisor, used for identification and logging.
     * Defaults to {@code null}; concrete implementations typically derive it from the advice class name.
     *
     * @return the advisor name, or {@code null} if not explicitly set
     */
    default String getAdvisorName() {
        return null;
    }

    /**
     * Returns the {@link MatchingContext} condition that must be satisfied before this advisor
     * is applied to a target class loader. Returns {@link #DEFAULT_CONDITION} ({@code null})
     * if no condition is configured, meaning the advisor is unconditionally applied.
     *
     * @return the condition matcher, or {@code null} if unconditional
     */
    default ElementMatcher<MatchingContext> getCondition() {
        return DEFAULT_CONDITION;
    }

    /**
     * Returns the {@link AdviceSpec} describing the advice class and its method metadata.
     *
     * @return the advice spec, never {@code null} after initialization
     */
    AdviceSpec getAdviceSpec();

    /**
     * Returns whether a new advice instance should be created per target object instance.
     * When {@code false}, a single shared advice instance is used across all targets.
     *
     * @return {@code true} for per-instance mode
     */
    boolean isPerInstance();

    /**
     * Returns the ordering value used to sort multiple advisors applied to the same joinpoint.
     * Lower values have higher priority.
     *
     * @return the advisor order
     */
    int getOrder();


    /**
     * Abstract base providing common advisor spec properties: name, condition, advice spec,
     * per-instance flag, and ordering.
     */
    abstract class AbstractBase implements AdvisorSpec {

        private final String advisorName;

        private final ElementMatcher<MatchingContext> condition;

        private AdviceSpec adviceSpec;

        private final boolean perInstance;

        private final int order;


        public AbstractBase(String advisorName, ElementMatcher<MatchingContext> condition, 
                boolean perInstance, int order) {
            this(advisorName, condition, null, perInstance, order);
        }

        public AbstractBase(String advisorName, ElementMatcher<MatchingContext> condition, 
                AdviceSpec adviceSpec, boolean perInstance, int order) {
            if (StringUtils.hasText(advisorName))
                this.advisorName = advisorName;
            else
                this.advisorName = adviceSpec.getAdviceClassName();

            this.condition = condition;
            this.adviceSpec = adviceSpec;
            this.perInstance = perInstance;
            this.order = order;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public String getAdvisorName() {
            return advisorName;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<MatchingContext> getCondition() {
            return condition;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public AdviceSpec getAdviceSpec() {
            return adviceSpec;
        }

        protected void setAdviceSpec(AdviceSpec adviceSpec) {
            this.adviceSpec = adviceSpec;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isPerInstance() {
            return perInstance;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getOrder() {
            return order;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return getAdvisorName();
        }
    }


    /**
     * An advisor spec that additionally carries a {@link PointcutSpec} for type and method matching.
     */
    interface PointcutAdvisorSpec extends AdvisorSpec {

        /**
         * Returns the {@link PointcutSpec} that determines which types and methods
         * this advisor applies to.
         *
         * @return the pointcut spec, never {@code null} after initialization
         */
        PointcutSpec getPointcutSpec();


        /**
         * Default {@link PointcutAdvisorSpec} implementation holding all advisor metadata
         * including the associated {@link PointcutSpec}.
         */
        class Default extends AdvisorSpec.AbstractBase implements PointcutAdvisorSpec {

            private PointcutSpec pointcutSpec;


            public Default(String advisorName, ElementMatcher<MatchingContext> condition, 
                    boolean perInstance, int order) {
                this(advisorName, condition, null, perInstance, order, null);
            }

            public Default(String advisorName, ElementMatcher<MatchingContext> condition, 
                    AdviceSpec adviceSpec, boolean perInstance, int order, 
                    PointcutSpec pointcutSpec) {
                super(advisorName, condition, adviceSpec, perInstance, order);

                this.pointcutSpec = pointcutSpec;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public PointcutSpec getPointcutSpec() {
                return pointcutSpec;
            }

            protected void setPointcutSpec(PointcutSpec pointcutSpec) {
                this.pointcutSpec = pointcutSpec;
            }
        }
    }
}