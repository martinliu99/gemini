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
package io.gemini.aop;

import io.gemini.aop.factory.support.AdvisorSpec;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Pointcut;
import io.gemini.core.Ordered;
import io.gemini.core.util.ObjectUtils;
import io.gemini.core.util.StringUtils;

/**
 * Represents a configured AOP advisor that binds an {@link io.gemini.api.aop.Advice} implementation
 * to a set of joinpoints.
 * <p>
 * An advisor encapsulates:
 * <ul>
 *   <li>A unique name for identification and logging</li>
 *   <li>The {@link AdviceKind} (POJO, AspectJ, or ByteBuddy)</li>
 *   <li>The {@link io.gemini.api.aop.Advice} instance or class</li>
 *   <li>Whether advice instances are shared or per-target-instance</li>
 *   <li>An ordering value for chaining multiple advisors</li>
 * </ul>
 * The {@link PointcutAdvisor} sub-interface additionally carries a {@link io.gemini.api.aop.Pointcut}
 * that determines which types and methods are intercepted.
 * </p>
 *
 * @author   martin.liu
 */
public interface Advisor extends Ordered {

    /**
     * Returns the unique name of this advisor, used for identification and logging.
     *
     * @return the advisor name
     */
    String getAdvisorName();

    /**
     * Returns the advice kind (POJO, AspectJ, or ByteBuddy).
     *
     * @return the {@link AdviceKind}
     */
    AdviceKind getAdviceKind();

    /**
     * Returns the shared advice instance, or creates a new one if per-instance mode is active.
     *
     * @return the advice instance
     */
    Advice getAdvice();

    /**
     * Returns the advice class without instantiating it.
     *
     * @return the advice class
     */
    Class<? extends Advice> getAdviceClass();

    /**
     * Returns {@code true} if a new advice instance should be created per target object instance.
     *
     * @return {@code true} for per-instance mode
     */
    boolean isPerInstance();


    /**
     * {@inheritDoc}
     */
    @Override
    int getOrder();


    /**
     * Abstract base implementation providing common advisor properties:
     * name, advice kind, per-instance flag, and ordering.
     */
    abstract class AbstractBase implements Advisor {

        private final String advisorName;

        private final AdviceKind adviceKind;

        private final boolean perInstance;

        private final int order;


        public AbstractBase(String advisorName, AdviceKind adviceKind, boolean perInstance, int order) {
            this.advisorName = StringUtils.hasText(advisorName) ? advisorName : super.toString();

            this.adviceKind = adviceKind;
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
        public AdviceKind getAdviceKind() {
            return this.adviceKind;
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
            return this.order;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return this.getAdvisorName() + "@" + ObjectUtils.getIdentityHexString(this);
        }
    }


    /**
     * An advisor that associates a {@link io.gemini.api.aop.Pointcut} with an advice,
     * enabling type and method matching before advice application.
     */
    interface PointcutAdvisor extends Advisor {

        /**
         * Returns whether this advisor should break advice circularity.
         * <p>
         * When {@code true}, the framework will skip re-entering this advisor's advice
         * if the advised method is invoked recursively within the same advice execution,
         * preventing infinite loops caused by self-invocation.
         * </p>
         *
         * @return {@code true} if circularity breaking is enabled
         */
        boolean isBreakCircularity();

        /**
         * Returns the {@link Pointcut} that determines which types and methods
         * this advisor applies to.
         *
         * @return the pointcut, never {@code null}
         */
        Pointcut getPointcut();


        /**
         * Abstract base for {@link PointcutAdvisor} implementations, adding
         * circularity-breaking flag and pointcut reference.
         */
        abstract class AbstractBase extends Advisor.AbstractBase implements PointcutAdvisor {

            private final boolean breakCircularity;
            private final Pointcut pointcut;


            public AbstractBase(String advisorName, AdviceKind adviceKind, 
                    boolean perInstance, int order,
                    boolean breakCircularity, Pointcut pointcut) {
                super(advisorName, adviceKind, perInstance, order);

                this.breakCircularity = breakCircularity;
                if (pointcut == null)
                    throw new IllegalArgumentException("'pointcut' must not be null");
                this.pointcut = pointcut;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isBreakCircularity() {
                return breakCircularity;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Pointcut getPointcut() {
                return this.pointcut;
            }
        }
    }


    /**
     * Placeholder advisor created when an {@link io.gemini.aop.factory.support.AdvisorSpec}
     * fails validation. Holds the original spec so it can be removed from the advisor list.
     */
    class IllegalAdvisor extends Advisor.AbstractBase {

        private final AdvisorSpec advisorSpec;


        public IllegalAdvisor(AdvisorSpec advisorSpec) {
            super(advisorSpec.getAdvisorName(), null, 
                    advisorSpec.isPerInstance(), advisorSpec.getOrder());

            this.advisorSpec = advisorSpec;
        }


        public AdvisorSpec getAdvisorSpec() {
            return advisorSpec;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public Advice getAdvice() {
            throw new UnsupportedOperationException();
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public Class<? extends Advice> getAdviceClass() {
            throw new UnsupportedOperationException();
        }
    }
}
