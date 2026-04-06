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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.support.AdviceSpec.AspectJAdviceSpec;
import io.gemini.aop.matcher.ExprPointcut.PointcutParameterMatcher;
import io.gemini.api.aop.Pointcut;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.MethodUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

public interface PointcutSpec extends ElementMatcher<MethodDescription> {

    boolean DEFAULT_BREAK_CIRCULARITY = false;


    boolean isBreakCircularity();

    ElementMatcher<MethodDescription> getAdviceMethodMatcher();


    abstract class AbstractBase implements PointcutSpec {

        private static final Logger LOGGER = LoggerFactory.getLogger(PointcutSpec.class);

        private static final Generic RUNTIME_EXCEPTION = TypeDefinition.Sort.describe(RuntimeException.class);


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

        /**
         * {@inheritDoc}
         */
        public ElementMatcher<MethodDescription> getAdviceMethodMatcher() {
            return this;
        }


        /**
         * {@inheritDoc}
         */
        public boolean matches(MethodDescription targetMethod) {
            // 1.verify returning and throwing with matched method
            // verify returning type argument
            Generic targetReturningType = targetMethod.isConstructor() 
                    ? targetMethod.getDeclaringType().asGenericType() : targetMethod.getReturnType();

            Generic parameterizedReturningType = getAdviceSpec().getParameterizedReturningType();
            if (parameterizedReturningType != null 
                    && matchesReturningType(targetMethod, true, targetReturningType, parameterizedReturningType) == false) {
                return false;
            }

            // verify throwing type argument
            Generic parameterizedThrowingType = getAdviceSpec().getParameterizedThrowingType();
            if (parameterizedThrowingType != null
                    && matchesThrowingType(targetMethod, true, parameterizedThrowingType) == false) {
                return false;
            }

            return true;
        }

        protected boolean matchesReturningType(MethodDescription targetMethod, 
                boolean parameterizedReturningType, Generic targetReturningType, Generic adviceReturningType) {
            targetReturningType = TypeDefinition.Sort.NON_GENERIC == adviceReturningType.getSort() 
                    || TypeDefinition.Sort.GENERIC_ARRAY == adviceReturningType.getSort()
                    ? targetReturningType.asRawType() : targetReturningType;

            String matchingReturningTypeMsg = parameterizedReturningType ? "ParameterizedReturning" : "AdviceReturning";

            MethodDescription adviceMethod = getAdviceSpec().getAdviceMethod();

            if (ClassUtils.isVisibleTo(adviceReturningType.asErasure(), adviceMethod.getDeclaringType().asErasure()) == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored advice method referring to non public and non protected in the same package {} type under target ClassLoader. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    {}: {} {} \n",
                            matchingReturningTypeMsg,
                            getAdviceSpec().getDeclaringType(),
                            MethodUtils.getMethodSignature(adviceMethod),
                            matchingReturningTypeMsg, adviceReturningType.getVisibility(), adviceReturningType
                    );

                return false;
            }

            if (parameterizedReturningType == true) {
                if (ClassUtils.equals(adviceReturningType, targetReturningType) == false) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored advice method with {} is different to target method's returning type. \n" 
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    {}: {} \n"
                                + "  TargetMethod: {} \n"
                                + "    ActualReturning: {} \n",
                                matchingReturningTypeMsg,
                                getAdviceSpec().getDeclaringType(),
                                MethodUtils.getMethodSignature(adviceMethod),
                                matchingReturningTypeMsg, adviceReturningType, 
                                MethodUtils.getMethodSignature(targetMethod),
                                targetReturningType
                        );

                    return false;
                }
            } else {
                if (ClassUtils.isAssignableFrom(adviceReturningType, targetReturningType) == false) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored advice method with {} is unassignable from target method's returning type. \n" 
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    {}: {} \n"
                                + "  TargetMethod: {} \n"
                                + "    ActualReturning: {} \n",
                                matchingReturningTypeMsg, 
                                getAdviceSpec().getDeclaringType(),
                                MethodUtils.getMethodSignature(adviceMethod),
                                matchingReturningTypeMsg, adviceReturningType, 
                                MethodUtils.getMethodSignature(targetMethod),
                                targetReturningType
                        );

                    return false;
                }
            }
            return true;
        }

        protected boolean matchesThrowingType(MethodDescription targetMethod, 
                boolean parameterizedThrowingType, Generic adviceThrowingType) {
            String matchingThrowingTypeMsg = parameterizedThrowingType ? "ParameterizedThrowing" : "AdviceThrowing";

            MethodDescription adviceMethod = getAdviceSpec().getAdviceMethod();

            if (ClassUtils.isVisibleTo(adviceThrowingType.asErasure(), adviceMethod.getDeclaringType().asErasure()) == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored advice method referring to non public or non protected in the same package {} type under target ClassLoader. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    {}: {} {} \n",
                            matchingThrowingTypeMsg,
                            getAdviceSpec().getDeclaringType(),
                            MethodUtils.getMethodSignature(adviceMethod),
                            matchingThrowingTypeMsg, adviceThrowingType.getVisibility(), adviceThrowingType
                    );

                return false;
            }

            TypeList.Generic exceptionTypes = targetMethod.getExceptionTypes();
            if (exceptionTypes.size() == 0) {
                if (ClassUtils.equals(adviceThrowingType, RUNTIME_EXCEPTION) == false) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored advice method with non RuntimeException throwing type. \n" 
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    {}: {} \n"
                                + "  TargetMethod: {} \n"
                                + "    ActualThrowing: RuntimeException \n",
                                getAdviceSpec().getDeclaringType(),
                                MethodUtils.getMethodSignature(adviceMethod),
                                matchingThrowingTypeMsg, adviceThrowingType, 
                                MethodUtils.getMethodSignature(targetMethod)
                        );

                    return false;
                }
                return true;
            }

            boolean matched = true;
            for (Generic exceptionType : exceptionTypes) {
                if (ClassUtils.isAssignableFrom(adviceThrowingType, exceptionType) == false) {
                    matched = false;
                    break;
                }
            }

            if (matched == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored advice method with throwing type is unassignable from target method's all throwing types. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    {}: {} \n"
                            + "  TargetMethod: {} \n"
                            + "    ActualThrowing: {} \n",
                            getAdviceSpec().getDeclaringType(),
                            MethodUtils.getMethodSignature(adviceMethod),
                            matchingThrowingTypeMsg, adviceThrowingType, 
                            MethodUtils.getMethodSignature(targetMethod),
                            exceptionTypes
                    );

                return false;
            }

            return true;
        }
    }


    interface PojoPointcutSpec extends PointcutSpec {

        Class<? extends Pointcut> getPointcutClass();


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


    interface ExprPointcutSpec extends PointcutSpec {

        String getPointcutExpression();


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


    interface AspectJPointcutSpec extends ExprPointcutSpec {

        TypeDescription getAdviceType();

        Map<String, Generic> getPointcutParameterTypes();

        PointcutParameterMatcher getPointcutParameterMatcher();


        class Default extends ExprPointcutSpec.Default implements AspectJPointcutSpec {

            public Default(AspectJAdviceSpec adviceSpec, boolean breakCircularity, 
                    String pointcutExpression) {
                super(adviceSpec, breakCircularity, pointcutExpression);
            }

            protected AspectJAdviceSpec getAdviceSpec() {
                return (AspectJAdviceSpec) super.getAdviceSpec();
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


            /**
             * {@inheritDoc}
             */
            public boolean matches(MethodDescription targetMethod) {
                if (super.matches(targetMethod) == false)
                    return false;


                // 1.verify returning and throwing with matched method
                // verify returning type argument
                Generic targetReturningType = targetMethod.isConstructor() 
                        ? targetMethod.getDeclaringType().asGenericType() : targetMethod.getReturnType();

                Generic adviceReturningParameterType = getAdviceSpec().getAdviceReturningParameterType();
                if (adviceReturningParameterType != null
                        && matchesReturningType(targetMethod, false, targetReturningType, adviceReturningParameterType) == false) {
                    return false;
                }

                // verify throwing type argument
                Generic adviceThrowingParameterType = getAdviceSpec().getAdviceThrowingParameterType();
                if (adviceThrowingParameterType != null
                        && matchesThrowingType(targetMethod, false, adviceThrowingParameterType) == false) {
                    return false;
                }

                return true;
            }
        }
    }
}