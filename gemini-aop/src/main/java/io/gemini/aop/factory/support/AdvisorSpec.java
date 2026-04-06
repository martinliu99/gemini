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
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorSpec {

    ElementMatcher<MatchingContext> DEFAULT_CONDITION = null;


    default String getAdvisorName() {
        return null;
    }

    default ElementMatcher<MatchingContext> getCondition() {
        return DEFAULT_CONDITION;
    }

    AdviceSpec getAdviceSpec();

    boolean isPerInstance();

    int getOrder();


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


        @Override
        public String getAdvisorName() {
            return advisorName;
        }

        @Override
        public ElementMatcher<MatchingContext> getCondition() {
            return condition;
        }

        @Override
        public AdviceSpec getAdviceSpec() {
            return adviceSpec;
        }

        protected void setAdviceSpec(AdviceSpec adviceSpec) {
            this.adviceSpec = adviceSpec;
        }

        @Override
        public boolean isPerInstance() {
            return perInstance;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public String toString() {
            return getAdvisorName();
        }
    }


    interface PointcutAdvisorSpec extends AdvisorSpec {

        PointcutSpec getPointcutSpec();


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