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
package io.gemini.api.aspect;

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
public interface AspectSpec {

    default String getAspectName() {
        return this.getClass().getName();
    }

    boolean isPerInstance();

    String getAdviceClassName();

    int getOrder();


    abstract class AbstractBase implements AspectSpec {

        protected final String aspectName;

        protected final boolean perInstance;
        protected final String adviceClassName;

        protected final int order;


        public AbstractBase(boolean perInstance, String adviceClassName, int order) {
            this(null, perInstance, adviceClassName, order);
        }

        public AbstractBase(String aspectName, boolean perInstance, String adviceClassName, int order) {
            this.aspectName = aspectName == null ? AspectSpec.super.getAspectName() : aspectName;

            this.perInstance = perInstance;
            this.adviceClassName = adviceClassName;

            this.order = order;
        }

        @Override
        public String getAspectName() {
            return aspectName;
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
            return getAspectName();
        }
    }

    abstract class BaseBuilder<T extends BaseBuilder<T>> {

        protected String aspectName;

        protected boolean perInstance = false;
        protected String adviceClassName;

        protected int order = 0;


        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        public T aspectName(String aspectName) {
            this.aspectName = aspectName;
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


    interface PojoPointcutSpec extends AspectSpec {

        Pointcut getPointcut();


        @NoScanning
        class Default extends AspectSpec.AbstractBase implements PojoPointcutSpec {

            private final Pointcut pointcut;


            public Default(boolean perInstance, String adviceClassName, 
                    Pointcut pointcut, int order) {
                this(null, perInstance, adviceClassName, pointcut, order);
            }

            public Default(String aspectName, boolean perInstance, String adviceClassName, 
                    Pointcut pointcut, int order) {
                super(aspectName, perInstance, adviceClassName, order);

                this.pointcut = pointcut;
            }

            @Override
            public String getAspectName() {
                return aspectName;
            }

            @Override
            public Pointcut getPointcut() {
                return pointcut;
            }
        }


        class Builder extends AspectSpec.BaseBuilder<Builder> {

            private ElementMatcher<String> classLoaderMatcher;
            private ElementMatcher<TypeDescription> typeMatcher;
            private ElementMatcher<MethodDescription> methodMatcher;


            public Builder classLoaderMatcher(ElementMatcher<String> classLoaderMatcher) {
                this.classLoaderMatcher = classLoaderMatcher;
                return this;
            }

            public Builder typeMatcher(ElementMatcher<TypeDescription> typeMatcher) {
                this.typeMatcher = typeMatcher;
                return this;
            }

            public Builder methodMatcher(ElementMatcher<MethodDescription> methodMatcher) {
                this.methodMatcher = methodMatcher;
                return this;
            }

            public PojoPointcutSpec builder() {
                return new Default(perInstance, adviceClassName, 
                        new Pointcut.Default(classLoaderMatcher, typeMatcher, methodMatcher), order);
            }
        }


        interface Factory {

            PojoPointcutSpec getAspectSpec();

        }
    }


    interface ExprPointcutSpec extends AspectSpec {

        String getPointcutExpression();


        @NoScanning
        class Default extends AspectSpec.AbstractBase implements ExprPointcutSpec {

            private final String pointcutExpression;


            public Default(boolean perInstance, String adviceClassName, 
                    String pointcutExpression, int order) {
                this(null, perInstance, adviceClassName, pointcutExpression, order);
            }

            public Default(String aspectName, boolean perInstance, String adviceClassName, 
                    String pointcutExpression, int order) {
                super(aspectName, perInstance, adviceClassName, order);

                this.pointcutExpression = pointcutExpression;
            }

            @Override
            public String getAspectName() {
                return aspectName;
            }

            @Override
            public String getPointcutExpression() {
                return pointcutExpression;
            }
        }


        class Builder extends AspectSpec.BaseBuilder<Builder> {

            private String pointcutExpression;


            public Builder pointcutExpression(String pointcutExpression) {
                this.pointcutExpression = pointcutExpression;
                return this;
            }

            public ExprPointcutSpec builder() {
                return new Default(perInstance, adviceClassName, 
                        pointcutExpression, order);
            }
        }


        interface Factory {

            ExprPointcutSpec getAspectSpec();

        }
    }


    interface AspectJSpec extends AspectSpec {

        String getAspectJClassName();


        class Default extends AspectSpec.AbstractBase implements AspectJSpec {

            private final String aspectJClassName;


            public Default(boolean perInstance, String aspectJClassName, int order) {
                this(null, perInstance, aspectJClassName, order);
            }

            public Default(String aspectName, boolean perInstance, String aspectJClassName, int order) {
                super(aspectName == null ? aspectJClassName : aspectName,
                        perInstance, null, order);

                this.aspectJClassName = aspectJClassName;
            }

            @Override
            public String getAspectName() {
                return aspectName;
            }

            @Override
            public String getAspectJClassName() {
                return aspectJClassName;
            }
        }
    }
}