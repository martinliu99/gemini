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

    static final boolean DEFAULT_INHERIT_CLASSLOADER_MATCHER = true;
    static final boolean DEFAULT_INHERIT_TYPE_MATCHER = true;

    static final boolean DEFAULT_PER_INSTANCE = false;

    static final int DEFAULT_ORDER = Integer.MAX_VALUE;


    default String getAdvisorName() {
        return null;
    }


    default ElementMatcher<MatchingContext> getCondition() {
        return DEFAULT_CONDITION;
    }

    default boolean isInheritClassLoaderMatcher() {
        return DEFAULT_INHERIT_CLASSLOADER_MATCHER;
    }

    default boolean isInheritTypeMatcher() {
        return DEFAULT_INHERIT_TYPE_MATCHER;
    }


    String getAdviceClassName();

    boolean isPerInstance();

    int getOrder();


    abstract class AbstractBase implements AdvisorSpec {

        private String advisorName;


        private ElementMatcher<MatchingContext> condition = DEFAULT_CONDITION;

        private boolean inheritClassLoaderMatcher = DEFAULT_INHERIT_CLASSLOADER_MATCHER;
        private boolean inheritTypeMatcher = DEFAULT_INHERIT_TYPE_MATCHER;


        private String adviceClassName;

        private boolean perInstance = DEFAULT_PER_INSTANCE;
        private int order = DEFAULT_ORDER;


        public AbstractBase() {
        }

        public AbstractBase(String advisorName, ElementMatcher<MatchingContext> condition, 
                boolean inheritClassLoaderMatcher, boolean inheritTypeMatcher,
                String adviceClassName, boolean perInstance, int order) {
            this();

            if (hasText(advisorName))
                this.advisorName = advisorName;


            this.condition = condition;

            this.inheritClassLoaderMatcher = inheritClassLoaderMatcher;
            this.inheritTypeMatcher = inheritTypeMatcher;


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
        public boolean isInheritClassLoaderMatcher() {
            return inheritClassLoaderMatcher;
        }

        protected void setInheritClassLoaderMatcher(boolean inheritClassLoaderMatcher) {
            this.inheritClassLoaderMatcher = inheritClassLoaderMatcher;
        }

        @Override
        public boolean isInheritTypeMatcher() {
            return inheritTypeMatcher;
        }

        protected void setInheritTypeMatcher(boolean inheritTypeMatcher) {
            this.inheritTypeMatcher = inheritTypeMatcher;
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

        private boolean inheritClassLoaderMatcher = PointcutAdvisorSpec.DEFAULT_INHERIT_CLASSLOADER_MATCHER;
        private boolean inheritTypeMatcher = PointcutAdvisorSpec.DEFAULT_INHERIT_TYPE_MATCHER;


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

        public T inheritClassLoaderMatcher(boolean inheritClassLoaderMatcher) {
            this.inheritClassLoaderMatcher = inheritClassLoaderMatcher;
            return self();
        }

        protected boolean isInheritClassLoaderMatcher() {
            return inheritClassLoaderMatcher;
        }

        public T inheritTypeMatcher(boolean inheritTypeMatcher) {
            this.inheritTypeMatcher = inheritTypeMatcher;
            return self();
        }

        protected boolean isInheritTypeMatcher() {
            return inheritTypeMatcher;
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

        abstract class AbstractBase extends AdvisorSpec.AbstractBase implements PointcutAdvisorSpec {

            public AbstractBase() {
                super();
            }

            public AbstractBase(String advisorName, ElementMatcher<MatchingContext> condition, 
                    boolean inheritClassLoaderMatcher, boolean inheritTypeMatcher,
                    String adviceClassName, boolean perInstance, int order) {
                super(advisorName, 
                        condition, inheritClassLoaderMatcher, inheritTypeMatcher,
                        adviceClassName, perInstance, order);
            }
        }


        abstract class PointcutAdvisorSpecBuilder<T extends PointcutAdvisorSpecBuilder<T>> extends AdvisorSpecBuilder<T> {
        }
    }


    interface PojoPointcutSpec extends AdvisorSpec.PointcutAdvisorSpec {

        Pointcut getPointcut();


        @NoScanning
        class Default extends AdvisorSpec.PointcutAdvisorSpec.AbstractBase implements PojoPointcutSpec {

            private final Pointcut pointcut;


            public Default(Pointcut pointcut, String adviceClassName, boolean perInstance, int order) {
                this(null, DEFAULT_CONDITION, 
                        DEFAULT_INHERIT_CLASSLOADER_MATCHER, DEFAULT_INHERIT_TYPE_MATCHER, pointcut, 
                        adviceClassName, perInstance, order);
            }

            public Default(ElementMatcher<MatchingContext> condition, 
                    boolean inheritClassLoaderMatcher, boolean inheritTypeMatcher, Pointcut pointcut, 
                    String adviceClassName, boolean perInstance, int order) {
                this(null, condition, 
                        inheritClassLoaderMatcher, inheritTypeMatcher, pointcut, 
                        adviceClassName, perInstance, order);
            }

            public Default(String advisorName, ElementMatcher<MatchingContext> condition, 
                    boolean inheritClassLoaderMatcher, boolean inheritTypeMatcher, Pointcut pointcut, 
                    String adviceClassName, boolean perInstance, int order) {
                super(advisorName, condition, 
                        inheritClassLoaderMatcher, inheritTypeMatcher,
                        adviceClassName, perInstance, order);

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
                        isInheritClassLoaderMatcher(),
                        isInheritTypeMatcher(),
                        new Pointcut.Default(typeMatcher, methodMatcher), 
                        getAdviceClassName(), 
                        isPerInstance(), 
                        getOrder()
                );
            }
        }


        interface Factory {

            PojoPointcutSpec getAdvisorSpec();

        }
    }


    interface ExprPointcutSpec extends AdvisorSpec.PointcutAdvisorSpec {

        static final String DEFAULT_CLASSLOADER_EXPRESSION = null;

        default String getClassLoaderExpression() {
            return DEFAULT_CLASSLOADER_EXPRESSION;
        }

        String getPointcutExpression();


        @NoScanning
        abstract class AbstractBase extends AdvisorSpec.PointcutAdvisorSpec.AbstractBase implements ExprPointcutSpec {

            private String classLoaderExpression = DEFAULT_CLASSLOADER_EXPRESSION;
            private String pointcutExpression;


            public AbstractBase() {}

            public AbstractBase(ElementMatcher<MatchingContext> condition, 
                    boolean inheritClassLoaderMatcher, boolean inheritTypeMatcher,
                    String classLoaderExpression, String pointcutExpression, 
                    String adviceClassName, boolean perInstance, int order) {
                this(null, condition, 
                        DEFAULT_INHERIT_CLASSLOADER_MATCHER, DEFAULT_INHERIT_TYPE_MATCHER, 
                        classLoaderExpression, pointcutExpression, 
                        adviceClassName, perInstance, order);
            }

            public AbstractBase(String advisorName, ElementMatcher<MatchingContext> condition, 
                    boolean inheritClassLoaderMatcher, boolean inheritTypeMatcher,
                    String classLoaderExpression, String pointcutExpression,  
                    String adviceClassName, boolean perInstance, int order) {
                super(advisorName, condition, 
                        inheritClassLoaderMatcher, inheritTypeMatcher,
                        adviceClassName, perInstance, order);

                this.classLoaderExpression = classLoaderExpression;
                this.pointcutExpression = pointcutExpression;
            }


            @Override
            public String getClassLoaderExpression() {
                return classLoaderExpression;
            }

            protected void setClassLoaderExpression(String classLoaderExpression) {
                this.classLoaderExpression = classLoaderExpression;
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

            public Default(String pointcutExpression, String adviceClassName, boolean perInstance, int order) {
                this(null, DEFAULT_CONDITION, 
                        DEFAULT_INHERIT_CLASSLOADER_MATCHER, DEFAULT_INHERIT_TYPE_MATCHER, 
                        DEFAULT_CLASSLOADER_EXPRESSION, pointcutExpression, 
                        adviceClassName, perInstance, order);
            }

            public Default(ElementMatcher<MatchingContext> condition, 
                    boolean inheritClassLoaderMatcher, boolean inheritTypeMatcher,
                    String classLoaderExpression, String pointcutExpression, 
                    String adviceClassName, boolean perInstance, int order) {
                super(null, condition, 
                        DEFAULT_INHERIT_CLASSLOADER_MATCHER, DEFAULT_INHERIT_TYPE_MATCHER, 
                        classLoaderExpression, pointcutExpression, 
                        adviceClassName, perInstance, order);
            }

            public Default(String advisorName, ElementMatcher<MatchingContext> condition, 
                    boolean inheritClassLoaderMatcher, boolean inheritTypeMatcher,
                    String classLoaderExpression, String pointcutExpression,  
                    String adviceClassName, boolean perInstance, int order) {
                super(advisorName, condition, 
                        inheritClassLoaderMatcher, inheritTypeMatcher,
                        classLoaderExpression, pointcutExpression,
                        adviceClassName, perInstance, order);
            }
        }


        class Builder extends AdvisorSpec.PointcutAdvisorSpec.PointcutAdvisorSpecBuilder<Builder> {

            private String classLoaderExpression;
            private String pointcutExpression;


            public Builder classLoaderExpression(String classLoaderExpression) {
                this.classLoaderExpression = classLoaderExpression;
                return this;
            }

            public Builder pointcutExpression(String pointcutExpression) {
                this.pointcutExpression = pointcutExpression;
                return this;
            }

            public ExprPointcutSpec builder() {
                return new Default(
                        getAdviceClassName(),
                        getCondition(),
                        isInheritClassLoaderMatcher(),
                        isInheritTypeMatcher(),
                        classLoaderExpression,
                        pointcutExpression, 
                        getAdviceClassName(), 
                        isPerInstance(), 
                        getOrder()
                );
            }
        }


        interface Factory {

            ExprPointcutSpec getAdvisorSpec();

        }
    }
}