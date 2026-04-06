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

public interface Advisor extends Ordered {

    String getAdvisorName();

    AdviceKind getAdviceKind();

    Advice getAdvice();

    Class<? extends Advice> getAdviceClass();

    boolean isPerInstance();

    @Override
    int getOrder();


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

        @Override
        public String getAdvisorName() {
            return advisorName;
        }

        @Override
        public AdviceKind getAdviceKind() {
            return this.adviceKind;
        }

        @Override
        public boolean isPerInstance() {
            return perInstance;
        }

        @Override
        public int getOrder() {
            return this.order;
        }


        @Override
        public String toString() {
            return this.getAdvisorName() + "@" + ObjectUtils.getIdentityHexString(this);
        }
    }


    interface PointcutAdvisor extends Advisor {

        boolean isBreakCircularity();

        Pointcut getPointcut();


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


            @Override
            public boolean isBreakCircularity() {
                return breakCircularity;
            }

            @Override
            public Pointcut getPointcut() {
                return this.pointcut;
            }
        }
    }


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
