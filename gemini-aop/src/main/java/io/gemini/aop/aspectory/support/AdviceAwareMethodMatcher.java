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
package io.gemini.aop.aspectory.support;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.ExprPointcut;
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;
import io.gemini.aspectj.weaver.tools.ShadowMatch;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.MethodUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdviceAwareMethodMatcher {


    public interface AdviceMethodMatcher {

        boolean match(MethodDescription methodDescription, ShadowMatch shadowMatch);


        abstract class AbstractBase implements AdviceMethodMatcher {

            protected static final Logger LOGGER = LoggerFactory.getLogger(AdviceMethodMatcher.class);

            private static final Generic RUNTIME_EXCEPTION = TypeDefinition.Sort.describe(RuntimeException.class);


            protected boolean isValid = true;

            protected ElementMatcher<MethodDescription> methodMatcher;

            protected MethodDescription adviceMethodDescription;
            protected String adviceMethodSignature;
            protected String adviceMethodGenericSignature;

            protected Generic parameterizedReturningType = null;
            protected Generic parameterizedThrowingType = null;

            protected Generic adviceReturningParameterType;
            protected Generic adviceThrowingParameterType;


            protected MethodDescription doDiscoverAdviceMethod(TypeDescription adviceType) {
                while(adviceType != null) {
                    MethodDescription adviceMethod = discoverFirstAdviceMethod(adviceType);
                    if(adviceMethod != null)
                        return adviceMethod;

                    adviceType = adviceType.getSuperClass().asErasure();
                }

                return null;
            }

            private MethodDescription discoverFirstAdviceMethod(TypeDescription adviceType) {
                MethodList<InDefinedShape> beforeMethodFilter = adviceType.getDeclaredMethods()
                        .filter(named("before")
                                .and(takesArgument(0, named(MutableJoinpoint.class.getName())))
                        );
                if(beforeMethodFilter.isEmpty() == false) {
                    MethodDescription beforeMethod = beforeMethodFilter.getOnly();

                    Generic parameterType = beforeMethod.getParameters().get(0).getType();
                    if(TypeDefinition.Sort.PARAMETERIZED == parameterType.getSort() && parameterType.getTypeArguments().size() > 0)
                        return beforeMethod;
                }

                MethodList<InDefinedShape> afterMethodFilter = adviceType.getDeclaredMethods()
                        .filter(named("after")
                                .and(takesArgument(0, named(MutableJoinpoint.class.getName())))
                        );
                if(afterMethodFilter.isEmpty() == false) {
                    MethodDescription afterMethod = afterMethodFilter.getOnly();

                    Generic parameterType = afterMethod.getParameters().get(0).getType();
                    if(TypeDefinition.Sort.PARAMETERIZED == parameterType.getSort() && parameterType.getTypeArguments().size() > 0)
                        return afterMethod;
                }

                return null;
            }

            protected void doDiscoverJoinpointTypeArguments(Generic joinpointType) {
                if(TypeDefinition.Sort.PARAMETERIZED != joinpointType.getSort()) {
                    return;
                }

                TypeList.Generic typeVariables = joinpointType.getTypeArguments();

                Generic returningType = typeVariables.get(0);
                if(TypeDefinition.Sort.NON_GENERIC != returningType.getSort() && TypeDefinition.Sort.PARAMETERIZED != returningType.getSort()) { 
                    LOGGER.warn("Ignored advice method containing illegal parameterized returning type. \n"
                            + "  parameterized returning type: {} \n    advice method: {} \n",
                            returningType.asErasure().getDescriptor(), 
                            MethodUtils.getMethodSignature(adviceMethodDescription)
                    );

                    this.isValid = false;
                    return;
                }
                this.parameterizedReturningType = returningType;

                Generic throwingType = typeVariables.get(1);
                if(TypeDefinition.Sort.NON_GENERIC != throwingType.getSort() && TypeDefinition.Sort.PARAMETERIZED != throwingType.getSort()) {
                    LOGGER.warn("Ignored advice method containing illegal parameterized throwing type. \n"
                            + "  parameterized throwing type: {} \n    advice method: {} \n",
                            returningType.asErasure().getDescriptor(), 
                            MethodUtils.getMethodSignature(adviceMethodDescription)
                    );

                    this.isValid = false;
                    return;
                }
                this.parameterizedThrowingType = throwingType;
            }


            protected boolean doMatchAdviceMethod(MethodDescription targetMethodDescription) {
                // match returning
                Generic targetReturningType = targetMethodDescription.isConstructor() 
                        ? targetMethodDescription.getDeclaringType().asGenericType() : targetMethodDescription.getReturnType();

                if(parameterizedReturningType != null 
                        && matchReturningType(targetMethodDescription, true, targetReturningType, parameterizedReturningType) == false) {
                    return false;
                }

                if(adviceReturningParameterType != null
                        && matchReturningType(targetMethodDescription, false, targetReturningType, adviceReturningParameterType) == false) {
                    return false;
                }

                // match throwing
                if(parameterizedThrowingType != null
                        && matchThrowingType(targetMethodDescription, true, parameterizedThrowingType) == false) {
                    return false;
                }
                if(adviceThrowingParameterType != null
                        && matchThrowingType(targetMethodDescription, false, adviceThrowingParameterType) == false) {
                    return false;
                }

                return true;
            }

            private boolean matchReturningType(MethodDescription targetMethodDescription, 
                    boolean parameterizedReturningType, Generic targetReturningType, Generic adviceReturningType) {
                targetReturningType = TypeDefinition.Sort.NON_GENERIC == adviceReturningType.getSort() 
                        || TypeDefinition.Sort.GENERIC_ARRAY == adviceReturningType.getSort()
                        ? targetReturningType.asRawType() : targetReturningType;

                String matchingReturningTypeMsg = parameterizedReturningType ? "parameterized returning type" : "advice returning parameter";

                if(ClassUtils.isAccessibleTo(adviceReturningType.asErasure(), adviceMethodDescription.getDeclaringType().asErasure()) == false) {
                    LOGGER.warn("Ignored matching since advice class can not access {} which must be public or protected in same package. \n"
                            + "  {}: {} \n    advice method: {} \n",
                            matchingReturningTypeMsg,
                            matchingReturningTypeMsg, adviceReturningType, 
                            MethodUtils.getMethodSignature(adviceMethodDescription) 
                    );

                    return false;
                }

                if(parameterizedReturningType == true) {
                    if(ClassUtils.equals(adviceReturningType, targetReturningType) == false) {
                        LOGGER.warn("Ignored matching since advice method's {} must be same as target method's returning type. \n" 
                                + "  {}: {} \n    advice method: {} \n  actual returning type: {} \n    target method: {} \n",
                                matchingReturningTypeMsg,
                                matchingReturningTypeMsg, adviceReturningType, 
                                MethodUtils.getMethodSignature(adviceMethodDescription), 
                                targetReturningType,
                                MethodUtils.getMethodSignature(targetMethodDescription)
                        );

                        return false;
                    }
                } else {
                    if(ClassUtils.isAssignableFrom(adviceReturningType, targetReturningType) == false) {
                        LOGGER.warn("Ignored matching since advice method's {} must be assignable from target method's returning type. \n" 
                                + "  {}: {} \n    advice method: {} \n  actual returning type: {} \n    target method: {} \n",
                                matchingReturningTypeMsg, 
                                matchingReturningTypeMsg, adviceReturningType, 
                                MethodUtils.getMethodSignature(adviceMethodDescription), 
                                targetReturningType,
                                MethodUtils.getMethodSignature(targetMethodDescription)
                        );

                        return false;
                    }
                }
                return true;
            }

            private boolean matchThrowingType(MethodDescription targetMethodDescription, 
                    boolean parameterizedThrowingType, Generic adviceThrowingType) {
                String matchingThrowingTypeMsg = parameterizedThrowingType ? "parameterized throwing type" : "advice throwing parameter";

                if(ClassUtils.isAccessibleTo(adviceThrowingType.asErasure(), adviceMethodDescription.getDeclaringType().asErasure()) == false) {
                    LOGGER.warn("Ignored matching since advice class can not access {} which must be public or protected in same package. \n"
                            + "  {}: {} \n    advice method: {} \n",
                            matchingThrowingTypeMsg,
                            matchingThrowingTypeMsg, adviceThrowingType, 
                            MethodUtils.getMethodSignature(adviceMethodDescription)
                    );

                    return false;
                }

                TypeList.Generic exceptionTypes = targetMethodDescription.getExceptionTypes();
                if(exceptionTypes.size() == 0) {
                    if(ClassUtils.equals(adviceThrowingType, RUNTIME_EXCEPTION) == false) {
                        LOGGER.warn("Ignored matching since advice method's throwing type must be RuntimeException. \n" 
                                + "  {}: {} \n    advice method: {} \n  actual throwing types: RuntimeException \n    target method: {} \n",
                                matchingThrowingTypeMsg, adviceThrowingType, 
                                MethodUtils.getMethodSignature(adviceMethodDescription), 
                                MethodUtils.getMethodSignature(targetMethodDescription)
                        );

                        return false;
                    }
                    return true;
                }

                boolean matched = true;
                for(Generic exceptionType : exceptionTypes) {
                    if(ClassUtils.isAssignableFrom(adviceThrowingType, exceptionType) == false) {
                        matched = false;
                        break;
                    }
                }

                if(matched == false) {
                    LOGGER.warn("Ignored matching since advice method's throwing type must be assignable from target method's all throwing types. \n"
                            + "  {}: {} \n    advice method: {} \n  actual throwing types: {} \n    target method: {} \n",
                            matchingThrowingTypeMsg, adviceThrowingType, 
                            MethodUtils.getMethodSignature(adviceMethodDescription), 
                            exceptionTypes,
                            MethodUtils.getMethodSignature(targetMethodDescription)
                    );

                    return false;
                }

                return true;
            }


            public boolean isValid() {
                return isValid;
            }
        }
    }


    public static class PojoAdvice extends AdviceMethodMatcher.AbstractBase implements ElementMatcher<MethodDescription> {

        public PojoAdvice(ElementMatcher<MethodDescription> methodMatcher, TypeDescription adviceType) {
            this.methodMatcher = methodMatcher;

            // discover returning and throwing types
            this.adviceMethodDescription = this.doDiscoverAdviceMethod(adviceType);
            Assert.notNull(adviceMethodDescription, "adviceMethodDescription must not be null.");

            this.doDiscoverJoinpointTypeArguments(adviceMethodDescription.getParameters().get(0).getType());
        }

        @Override
        public boolean matches(MethodDescription targetMethodDescription) {
            return match(targetMethodDescription, null);
        }

        @Override
        public boolean match(MethodDescription targetMethodDescription, ShadowMatch shadowMatch) {
            if(methodMatcher.matches(targetMethodDescription) == false)
                return false;

            if(this.adviceMethodDescription == null)
                return false;

            return this.doMatchAdviceMethod(targetMethodDescription);
        }
    }


    public static class AspectJAdvice implements ElementMatcher<MethodDescription> {

        private final ExprPointcut exprPointcut;
        private final AdviceMethodMatcher adviceMethodMatcher; 

        public AspectJAdvice(ExprPointcut exprPointcut, AdviceMethodMatcher adviceMethodMatcher) {
            this.exprPointcut = exprPointcut;
            this.adviceMethodMatcher = adviceMethodMatcher;
        }

        @Override
        public boolean matches(MethodDescription targetMethodDescription) {
            ShadowMatch shadowMatch = exprPointcut.getShadowMatch(targetMethodDescription);

            if (shadowMatch.alwaysMatches()) {
                // match MethodDescription
                return adviceMethodMatcher.match(targetMethodDescription, shadowMatch);
            }
            else if (shadowMatch.neverMatches()) {
                return false;
            }
            else {
                return false;
            }
        }
    }
}
