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
package io.gemini.api.aop;

import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.condition.Condition;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorSpec {

    default String getAdvisorName() {
        return this.getClass().getName();
    }

    default Condition getCondition() {
        return Condition.AlwaysTrue.INSTANCE;
    }

    boolean isPerInstance();

    String getAdviceClassName();

    int getOrder();



    abstract class AbstractBase implements AdvisorSpec {

        protected final String advisorName;

        private final Condition condition;

        protected final boolean perInstance;
        protected final String adviceClassName;

        protected final int order;


        public AbstractBase(Condition condition, boolean perInstance, String adviceClassName, int order) {
            this(null, condition, perInstance, adviceClassName, order);
        }

        public AbstractBase(String advisorName, Condition condition, 
                boolean perInstance, String adviceClassName, int order) {
            this.advisorName = advisorName == null ? AdvisorSpec.super.getAdvisorName() : advisorName;

            this.condition = condition == null ? Condition.AlwaysTrue.INSTANCE : condition;

            this.perInstance = perInstance;
            this.adviceClassName = adviceClassName;

            this.order = order;
        }

        @Override
        public String getAdvisorName() {
            return advisorName;
        }

        @Override
        public Condition getCondition() {
            return condition;
        }

        @Override
        public boolean isPerInstance() {
            return perInstance;
        }

        @Override
        public String getAdviceClassName() {
            return adviceClassName;
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

    abstract class BaseBuilder<T extends BaseBuilder<T>> {

        protected String advisorName;

        protected Condition condition;

        protected boolean perInstance = false;
        protected String adviceClassName;

        protected int order = 0;


        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        public T advisorName(String advisorName) {
            this.advisorName = advisorName;
            return self();
        }

        public T condition(Condition condition) {
            this.condition = condition;
            return self();
        }

        public T perInstance(boolean perInstance) {
            this.perInstance = perInstance;
            return self();
        }

        public T adviceClassName(String adviceClassName) {
            this.adviceClassName = adviceClassName;
            return self();
        }

        public T order(int order) {
            this.order = order;
            return self();
        }
    }


    interface PojoPointcutSpec extends AdvisorSpec {

        Pointcut getPointcut();


        @NoScanning
        class Default extends AdvisorSpec.AbstractBase implements PojoPointcutSpec {

            private final Pointcut pointcut;


            public Default(boolean perInstance, String adviceClassName, Pointcut pointcut, int order) {
                this(null, null, perInstance, adviceClassName, pointcut, order);
            }

            public Default(Condition condition, boolean perInstance, 
                    String adviceClassName, Pointcut pointcut, int order) {
                this(null, condition, perInstance, adviceClassName, pointcut, order);
            }

            public Default(String advisorName, Condition condition, boolean perInstance, 
                    String adviceClassName, Pointcut pointcut, int order) {
                super(advisorName, condition, perInstance, adviceClassName, order);

                this.pointcut = pointcut;
            }

            @Override
            public Pointcut getPointcut() {
                return pointcut;
            }
        }


        class Builder extends AdvisorSpec.BaseBuilder<Builder> {

            private ElementMatcher<TypeDescription> typeMatcher;
            private ElementMatcher<MethodDescription> methodMatcher;


            public Builder typeMatcher(ElementMatcher<TypeDescription> typeMatcher) {
                this.typeMatcher = typeMatcher;
                return this;
            }

            public Builder methodMatcher(ElementMatcher<MethodDescription> methodMatcher) {
                this.methodMatcher = methodMatcher;
                return this;
            }

            public PojoPointcutSpec builder() {
                return new Default(condition,
                        perInstance, 
                        adviceClassName, 
                        new Pointcut.Default(typeMatcher, methodMatcher), order);
            }
        }


        interface Factory {

            PojoPointcutSpec getAdvisorSpec();

        }
    }


    interface ExprPointcutSpec extends AdvisorSpec {

        String getPointcutExpression();


        @NoScanning
        class Default extends AdvisorSpec.AbstractBase implements ExprPointcutSpec {

            private final String pointcutExpression;


            public Default(boolean perInstance, String adviceClassName, String pointcutExpression, int order) {
                this(null, null, perInstance, adviceClassName, pointcutExpression, order);
            }

            public Default(Condition condition, boolean perInstance, 
                    String adviceClassName, String pointcutExpression, int order) {
                this(null, condition, perInstance, adviceClassName, pointcutExpression, order);
            }

            public Default(String advisorName, Condition condition, boolean perInstance, 
                    String adviceClassName, String pointcutExpression, int order) {
                super(advisorName, condition, perInstance, adviceClassName, order);

                this.pointcutExpression = pointcutExpression;
            }

            @Override
            public String getPointcutExpression() {
                return pointcutExpression;
            }
        }


        class Builder extends AdvisorSpec.BaseBuilder<Builder> {

            private String pointcutExpression;


            public Builder pointcutExpression(String pointcutExpression) {
                this.pointcutExpression = pointcutExpression;
                return this;
            }

            public ExprPointcutSpec builder() {
                return new Default(condition,
                        perInstance, 
                        adviceClassName, 
                        pointcutExpression, order);
            }
        }


        interface Factory {

            ExprPointcutSpec getAdvisorSpec();

        }
    }


    interface AspectJSpec extends AdvisorSpec {

        String getAspectJClassName();


        class Default extends AdvisorSpec.AbstractBase implements AspectJSpec {

            private final String aspectJClassName;


            public Default(boolean perInstance, String aspectJClassName, int order) {
                this(null, null, perInstance, aspectJClassName, order);
            }

            public Default(Condition condition, boolean perInstance, 
                    String aspectJClassName, int order) {
                this(null, condition, perInstance, aspectJClassName, order);
            }

            public Default(String advisorName, Condition condition, boolean perInstance, 
                    String aspectJClassName, int order) {
                super(advisorName == null ? aspectJClassName : advisorName, condition, 
                        perInstance, null, order);

                this.aspectJClassName = aspectJClassName;
            }

            @Override
            public String getAspectJClassName() {
                return aspectJClassName;
            }
        }
    }
}