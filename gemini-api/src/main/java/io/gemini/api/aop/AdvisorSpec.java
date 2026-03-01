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
package io.gemini.api.aop;

import io.gemini.api.annotation.NoScanning;
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

    static final ElementMatcher<MatchingContext> DEFAULT_CONDITION = null;

    static final boolean DEFAULT_PER_INSTANCE = false;

    static final int DEFAULT_ORDER = Integer.MAX_VALUE;


    default String getAdvisorName() {
        return null;
    }


    default ElementMatcher<MatchingContext> getCondition() {
        return DEFAULT_CONDITION;
    }


    String getAdviceClassName();

    boolean isPerInstance();

    int getOrder();


    abstract class AbstractBase implements AdvisorSpec {

        private String advisorName;


        private ElementMatcher<MatchingContext> condition = DEFAULT_CONDITION;

        private String adviceClassName;

        private boolean perInstance = DEFAULT_PER_INSTANCE;
        private int order = DEFAULT_ORDER;


        public AbstractBase() {
        }

        public AbstractBase(String advisorName, ElementMatcher<MatchingContext> condition, 
                String adviceClassName, boolean perInstance, int order) {
            this();

            if (hasText(advisorName))
                this.advisorName = advisorName;


            this.condition = condition;

            this.adviceClassName = adviceClassName;

            this.perInstance = perInstance;
            this.order = order;
        }


        @Override
        public String getAdvisorName() {
            return advisorName;
        }

        protected void setAdvisorName(String advisorName) {
            this.advisorName = advisorName;
        }


        @Override
        public ElementMatcher<MatchingContext> getCondition() {
            return condition;
        }

        protected void setCondition(ElementMatcher<MatchingContext> condition) {
            this.condition = condition;
        }

        @Override
        public String getAdviceClassName() {
            return adviceClassName;
        }

        protected void setAdviceClassName(String adviceClassName) {
            this.adviceClassName = adviceClassName;
        }

        @Override
        public boolean isPerInstance() {
            return perInstance;
        }

        protected void setPerInstance(boolean perInstance) {
            this.perInstance = perInstance;
        }

        @Override
        public int getOrder() {
            return order;
        }

        protected void setOrder(int order) {
            this.order = order;
        }


        private boolean hasText(String string) {
            return string != null && "".equals(string.trim()) == false;
        }

        @Override
        public String toString() {
            return getAdvisorName();
        }
    }


    abstract class AdvisorSpecBuilder<T extends AdvisorSpecBuilder<T>> {

        private String advisorName;

        private ElementMatcher<MatchingContext> condition;

        private String adviceClassName;

        private boolean perInstance = DEFAULT_PER_INSTANCE;
        private int order = DEFAULT_ORDER;


        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        public T advisorName(String advisorName) {
            this.advisorName = advisorName;
            return self();
        }

        protected String getAdvisorName() {
            return advisorName;
        }


        public T condition(ElementMatcher<MatchingContext> condition) {
            this.condition = condition;
            return self();
        }

        protected ElementMatcher<MatchingContext> getCondition() {
            return condition;
        }

        public T adviceClassName(String adviceClassName) {
            this.adviceClassName = adviceClassName;
            return self();
        }

        protected String getAdviceClassName() {
            return adviceClassName;
        }

        public T perInstance(boolean perInstance) {
            this.perInstance = perInstance;
            return self();
        }

        protected boolean isPerInstance() {
            return perInstance;
        }

        public T order(int order) {
            this.order = order;
            return self();
        }

        protected int getOrder() {
            return order;
        }
    }


    interface PointcutAdvisorSpec extends AdvisorSpec {

        static final boolean DEFAULT_BREAK_CIRCULARITY = false;


        boolean isBreakCircularity();


        abstract class AbstractBase extends AdvisorSpec.AbstractBase implements PointcutAdvisorSpec {

            private final boolean breakCircularity;


            public AbstractBase() {
                super();

                this.breakCircularity = DEFAULT_BREAK_CIRCULARITY;
            }

            public AbstractBase(String advisorName, ElementMatcher<MatchingContext> condition, 
                    String adviceClassName, boolean perInstance, int order, boolean breakCircularity) {
                super(advisorName, condition, 
                        adviceClassName, perInstance, order);

                this.breakCircularity = DEFAULT_BREAK_CIRCULARITY;
            }


            public boolean isBreakCircularity() {
                return breakCircularity;
            }
        }


        abstract class PointcutAdvisorSpecBuilder<T extends PointcutAdvisorSpecBuilder<T>> extends AdvisorSpecBuilder<T> {

            private boolean breakCircularity;


            public PointcutAdvisorSpecBuilder<T> breakCircularity(boolean breakCircularity) {
                this.breakCircularity = breakCircularity;
                return this;
            }

            public boolean isBreakCircularity() {
                return breakCircularity;
            }
        }
    }


    interface PojoPointcutSpec extends AdvisorSpec.PointcutAdvisorSpec {

        Pointcut getPointcut();


        @NoScanning
        class Default extends AdvisorSpec.PointcutAdvisorSpec.AbstractBase implements PojoPointcutSpec {

            private final Pointcut pointcut;


            public Default(String adviceClassName, boolean perInstance, int order, Pointcut pointcut) {
                this(null, DEFAULT_CONDITION, 
                        adviceClassName, perInstance, order, 
                        DEFAULT_BREAK_CIRCULARITY, pointcut);
            }

            public Default(ElementMatcher<MatchingContext> condition, 
                    String adviceClassName, boolean perInstance, int order, 
                    Pointcut pointcut) {
                this(null, condition, 
                        adviceClassName, perInstance, order, 
                        DEFAULT_BREAK_CIRCULARITY, pointcut);
            }

            public Default(String advisorName, ElementMatcher<MatchingContext> condition, 
                    String adviceClassName, boolean perInstance, int order, 
                    boolean breakCircularity, Pointcut pointcut) {
                super(advisorName, condition, 
                        adviceClassName, perInstance, order, 
                        breakCircularity);

                this.pointcut = pointcut;
            }

            @Override
            public Pointcut getPointcut() {
                return pointcut;
            }
        }


        class Builder extends AdvisorSpec.PointcutAdvisorSpec.PointcutAdvisorSpecBuilder<Builder> {

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
                return new Default(
                        getAdviceClassName(),
                        getCondition(),
                        getAdviceClassName(), 
                        isPerInstance(), 
                        getOrder(), 
                        isBreakCircularity(),
                        new Pointcut.Default(typeMatcher, methodMatcher)
                );
            }
        }


        interface Factory {

            PojoPointcutSpec getAdvisorSpec();

        }
    }


    interface ExprPointcutSpec extends AdvisorSpec.PointcutAdvisorSpec {

        String getPointcutExpression();


        @NoScanning
        abstract class AbstractBase extends AdvisorSpec.PointcutAdvisorSpec.AbstractBase implements ExprPointcutSpec {

            private String pointcutExpression;


            public AbstractBase() {}

            public AbstractBase(ElementMatcher<MatchingContext> condition, 
                    String adviceClassName, boolean perInstance, int order, 
                    boolean breakCircularity, String pointcutExpression) {
                this(null, condition, 
                        adviceClassName, perInstance, order, 
                        breakCircularity, pointcutExpression);
            }

            public AbstractBase(String advisorName, ElementMatcher<MatchingContext> condition, 
                    String adviceClassName, boolean perInstance, int order, 
                    boolean breakCircularity, String pointcutExpression) {
                super(advisorName, condition, 
                        adviceClassName, perInstance, order, breakCircularity);

                this.pointcutExpression = pointcutExpression;
            }


            @Override
            public String getPointcutExpression() {
                return pointcutExpression;
            }

            protected void setPointcutExpression(String pointcutExpression) {
                this.pointcutExpression = pointcutExpression;
            }
        }


        @NoScanning
        class Default extends AbstractBase {

            public Default(String adviceClassName, boolean perInstance, int order, String pointcutExpression) {
                this(null, DEFAULT_CONDITION, 
                        adviceClassName, perInstance, order, 
                        DEFAULT_BREAK_CIRCULARITY, pointcutExpression);
            }

            public Default(ElementMatcher<MatchingContext> condition, 
                    String adviceClassName, boolean perInstance, int order, boolean breakCircularity, 
                    String pointcutExpression) {
                super(null, condition, 
                        adviceClassName, perInstance, order, 
                        breakCircularity, pointcutExpression);
            }

            public Default(String advisorName, ElementMatcher<MatchingContext> condition, 
                    String adviceClassName, boolean perInstance, int order, 
                    boolean breakCircularity, String pointcutExpression) {
                super(advisorName, condition, 
                        adviceClassName, perInstance, order, 
                        breakCircularity, pointcutExpression);
            }
        }


        class Builder extends AdvisorSpec.PointcutAdvisorSpec.PointcutAdvisorSpecBuilder<Builder> {

            private String pointcutExpression;


            public Builder pointcutExpression(String pointcutExpression) {
                this.pointcutExpression = pointcutExpression;
                return this;
            }

            public ExprPointcutSpec builder() {
                return new Default(
                        getAdviceClassName(),
                        getCondition(),
                        getAdviceClassName(),
                        isPerInstance(), 
                        getOrder(), 
                        isBreakCircularity(), 
                        pointcutExpression
                );
            }
        }


        interface Factory {

            ExprPointcutSpec getAdvisorSpec();

        }
    }
}