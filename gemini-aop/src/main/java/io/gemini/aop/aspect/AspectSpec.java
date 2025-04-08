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
package io.gemini.aop.aspect;

import io.gemini.core.Ordered;
import io.gemini.core.object.ClassScanner.Ignored;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AspectSpec extends Ordered {

    boolean isPerInstance();

    String getAdviceClassName();

    int getOrder();


    abstract class AbstractBase implements AspectSpec {

        protected final boolean perInstance;
        protected final String adviceClassName;

        protected final int order;


        public AbstractBase(boolean perInstance, String adviceClassName, int order) {
            this.perInstance = perInstance;
            this.adviceClassName = adviceClassName;
            this.order = order;
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
    }

    abstract class BaseBuilder<T extends BaseBuilder<T>> {

        protected boolean perInstance = false;
        protected String adviceClassName;

        protected int order = 0;


        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
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


        @Ignored
        class Default extends AspectSpec.AbstractBase implements PojoPointcutSpec {

            private final Pointcut pointcut;


            public Default(boolean perInstance, String adviceClassName, 
                    Pointcut pointcut, int order) {
                super(perInstance, adviceClassName, order);

                this.pointcut = pointcut;
            }


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

//        String getClassLoaderExpression();

        String getPointcutExpression();


        @Ignored
        class Default extends AspectSpec.AbstractBase implements ExprPointcutSpec {

            private final String pointcutExpression;


            public Default(boolean perInstance, String adviceClassName, 
                    String pointcutExpression, int order) {
                super(perInstance, adviceClassName, order);

                this.pointcutExpression = pointcutExpression;
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


            public Default(boolean perInstance, String adviceClassName, 
                    String aspectJClassName, int order) {
                super(perInstance, adviceClassName, order);

                this.aspectJClassName = aspectJClassName;
            }

            @Override
            public String getAspectJClassName() {
                return aspectJClassName;
            }
        }
    }
}