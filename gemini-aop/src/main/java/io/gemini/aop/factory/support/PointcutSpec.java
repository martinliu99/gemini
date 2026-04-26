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

import java.util.Map;

import io.gemini.aop.factory.support.AdviceSpec.AspectJAdviceSpec;
import io.gemini.aop.matcher.ExprPointcut.PointcutParameterMatcher;
import io.gemini.api.aop.Pointcut;
import io.gemini.core.util.Assert;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;

/**
 * Describes the pointcut specification for a {@link AdvisorSpec.PointcutAdvisorSpec}.
 * <p>
 * Validates that the advice method's parameterized returning and throwing types are
 * compatible with the target method's actual return and exception types for the matched
 * target type.
 * 
 * Sub-interfaces cover the three pointcut styles:
 * <ul>
 *   <li>{@link PojoPointcutSpec} – references a {@link io.gemini.api.aop.Pointcut} class</li>
 *   <li>{@link ExprPointcutSpec} – holds an AspectJ expression string</li>
 *   <li>{@link AspectJPointcutSpec} – extends ExprPointcutSpec with parameter binding metadata</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public interface PointcutSpec {

    boolean DEFAULT_BREAK_CIRCULARITY = false;


    /**
     * Returns whether this pointcut should break advice circularity.
     * <p>
     * When {@code true}, the framework skips re-entering this advice if the advised method
     * is invoked recursively within the same advice execution, preventing infinite loops
     * caused by self-invocation.
     * </p>
     *
     * @return {@code true} if circularity breaking is enabled; defaults to {@link #DEFAULT_BREAK_CIRCULARITY}
     */
    boolean isBreakCircularity();


    /**
     * Abstract base providing returning/throwing type compatibility validation
     * against the target method's actual return and exception types.
     */
    abstract class AbstractBase implements PointcutSpec {

        private final AdviceSpec adviceSpec;
        private final boolean breakCircularity;


        public AbstractBase(AdviceSpec adviceSpec, boolean breakCircularity) {
            this.adviceSpec = adviceSpec;
            this.breakCircularity = breakCircularity;
        }

        protected AdviceSpec getAdviceSpec() {
            return this.adviceSpec;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isBreakCircularity() {
            return breakCircularity;
        }
    }


    /** 
     * Pointcut spec that references a {@link io.gemini.api.aop.Pointcut} class. 
     */
    interface PojoPointcutSpec extends PointcutSpec {

        /**
         * Returns the {@link io.gemini.api.aop.Pointcut} implementation class that provides
         * the type and method matchers for this pointcut.
         *
         * @return the pointcut class, never {@code null}
         */
        Class<? extends Pointcut> getPointcutClass();


        /**
         * Default {@link PojoPointcutSpec} implementation holding the pointcut class reference.
         */
        class Default extends AbstractBase implements PojoPointcutSpec {

            private final Class<? extends Pointcut> pointcutClass;


            public Default(AdviceSpec adviceSpec, boolean breakCircularity, 
                    Class<? extends Pointcut> pointcutClass) {
                super(adviceSpec, breakCircularity);

                Assert.notNull(pointcutClass, "'pointcutClass' must not be null.");
                this.pointcutClass = pointcutClass;
            }

            /**
             *  {@inheritDoc}
             */
            @Override
            public Class<? extends Pointcut> getPointcutClass() {
                return pointcutClass;
            }
        }
    }


    /** 
     * Pointcut spec that holds an AspectJ expression string. 
     */
    interface ExprPointcutSpec extends PointcutSpec {

        /**
         * Returns the AspectJ pointcut expression string used to match target types and methods.
         *
         * @return the pointcut expression, never empty
         */
        String getPointcutExpression();


        /**
         * Default {@link ExprPointcutSpec} implementation holding the pointcut expression string.
         */
        class Default extends AbstractBase implements ExprPointcutSpec {

            private final String pointcutExpression;


            public Default(AdviceSpec adviceSpec, boolean breakCircularity, 
                    String pointcutExpression) {
                super(adviceSpec, breakCircularity);

                Assert.hasText(pointcutExpression, "'pointcutExpression' must not be empty.");
                this.pointcutExpression = pointcutExpression;
            }

            /** 
             * {@inheritDoc}
             */
            @Override
            public String getPointcutExpression() {
                return pointcutExpression;
            }
        }
    }


    /** 
     * AspectJ pointcut spec that extends {@link PointcutSpec} with parameter binding metadata. 
     */
    interface AspectJPointcutSpec extends PointcutSpec {

        /**
         * Returns the AspectJ pointcut expression string used to match target types and methods.
         *
         * @return the pointcut expression, never empty
         */
        String getPointcutExpression();

        /**
         * Returns the {@link TypeDescription} of the AspectJ advice class that declares this pointcut.
         *
         * @return the advice type description, never {@code null}
         */
        TypeDescription getAdviceType();

        /**
         * Returns a map of pointcut parameter names to their generic types, used for
         * binding matched join-point values (e.g.{@code argNames}, {@code returning}, {@code throwing}) to
         * advice method parameters.
         *
         * @return map of parameter name to generic type
         */
        Map<String, Generic> getPointcutParameterTypes();

        /**
         * Returns the {@link PointcutParameterMatcher} responsible for validating and binding
         * pointcut parameter values to the advice method's parameter list.
         *
         * @return the pointcut parameter matcher, never {@code null}
         */
        PointcutParameterMatcher getPointcutParameterMatcher();


        /**
         * Default {@link AspectJPointcutSpec} implementation that additionally validates
         * advice returning/throwing parameter types against the target method.
         */
        class Default extends PointcutSpec.AbstractBase implements AspectJPointcutSpec {

            private final String pointcutExpression;


            public Default(AspectJAdviceSpec adviceSpec, boolean breakCircularity, 
                    String pointcutExpression) {
                super(adviceSpec, breakCircularity);

                this.pointcutExpression = pointcutExpression;
            }

            protected AspectJAdviceSpec getAdviceSpec() {
                return (AspectJAdviceSpec) super.getAdviceSpec();
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public String getPointcutExpression() {
                return this.pointcutExpression;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public TypeDescription getAdviceType() {
                return super.getAdviceSpec().getDeclaringType();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Map<String, Generic> getPointcutParameterTypes() {
                return getAdviceSpec().getPointcutParameterTypes();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public PointcutParameterMatcher getPointcutParameterMatcher() {
                return getAdviceSpec();
            }
        }
    }
}