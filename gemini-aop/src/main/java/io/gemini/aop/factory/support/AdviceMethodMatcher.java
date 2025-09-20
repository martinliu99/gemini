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
package io.gemini.aop.factory.support;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.support.AdviceMethodSpec.AspectJMethodSpec;
import io.gemini.aop.matcher.ExprPointcut;
import io.gemini.aop.matcher.ExprPointcut.PointcutParameterMatcher;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.MethodUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 */
abstract class AdviceMethodMatcher implements ElementMatcher<MethodDescription> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AdviceMethodMatcher.class);

    private static final Generic RUNTIME_EXCEPTION = TypeDefinition.Sort.describe(RuntimeException.class);

    protected final AdviceMethodSpec adviceMethodSpec;


    protected AdviceMethodMatcher(AdviceMethodSpec adviceMethodSpec) {
        this.adviceMethodSpec = adviceMethodSpec;
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches(MethodDescription methodDescription) {
        if(adviceMethodSpec.isValid() == false)
            return false;

        if(adviceMethodSpec.getAdviceMethod() == null)
            return false;

        // 1.match pointcut method matcher
        if(doMatch(methodDescription) == false)
            return false;


        // 2.verify returning and throwing with matched method
        // verify returning type argument
        Generic targetReturningType = methodDescription.isConstructor() 
                ? methodDescription.getDeclaringType().asGenericType() : methodDescription.getReturnType();

        if(adviceMethodSpec.getParameterizedReturningType() != null 
                && matchesReturningType(methodDescription, true, targetReturningType, adviceMethodSpec.getParameterizedReturningType()) == false) {
            return false;
        }

        if(adviceMethodSpec.getAdviceReturningParameterType() != null
                && matchesReturningType(methodDescription, false, targetReturningType, adviceMethodSpec.getAdviceReturningParameterType()) == false) {
            return false;
        }

        // verify throwing type argument
        if(adviceMethodSpec.getParameterizedThrowingType() != null
                && matchesThrowingType(methodDescription, true, adviceMethodSpec.getParameterizedThrowingType()) == false) {
            return false;
        }

        if(adviceMethodSpec.getAdviceThrowingParameterType() != null
                && matchesThrowingType(methodDescription, false, adviceMethodSpec.getAdviceThrowingParameterType()) == false) {
            return false;
        }

        return true;
    }

    /**
     * @param methodDescription
     */
    protected abstract boolean doMatch(MethodDescription methodDescription);


    private boolean matchesReturningType(MethodDescription methodDescription, 
            boolean parameterizedReturningType, Generic targetReturningType, Generic adviceReturningType) {
        targetReturningType = TypeDefinition.Sort.NON_GENERIC == adviceReturningType.getSort() 
                || TypeDefinition.Sort.GENERIC_ARRAY == adviceReturningType.getSort()
                ? targetReturningType.asRawType() : targetReturningType;

        String matchingReturningTypeMsg = parameterizedReturningType ? "ParameterizedReturning" : "AdviceReturning";

        if(ClassUtils.isVisibleTo(adviceReturningType.asErasure(), adviceMethodSpec.getAdviceMethod().getDeclaringType().asErasure()) == false) {
            LOGGER.warn("Ignored advice method referring to non public and non protected in the same package {} type under Joinpoint ClassLoader. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} {} \n",
                    matchingReturningTypeMsg,
                    adviceMethodSpec.getAdvisorName(),
                    adviceMethodSpec.getAdviceMethodSignature(),
                    matchingReturningTypeMsg, adviceReturningType.getVisibility(), adviceReturningType
            );

            return false;
        }

        if(parameterizedReturningType == true) {
            if(ClassUtils.equals(adviceReturningType, targetReturningType) == false) {
                LOGGER.warn("Ignored advice method with {} is different to target method's returning type. \n" 
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} \n  TargetMethod: {} \n    ActualReturning: {} \n",
                        matchingReturningTypeMsg,
                        adviceMethodSpec.getAdvisorName(),
                        adviceMethodSpec.getAdviceMethodSignature(),
                        matchingReturningTypeMsg, adviceReturningType, 
                        MethodUtils.getMethodSignature(methodDescription),
                        targetReturningType
                );

                return false;
            }
        } else {
            if(ClassUtils.isAssignableFrom(adviceReturningType, targetReturningType) == false) {
                LOGGER.warn("Ignored advice method with {} is unassignable from target method's returning type. \n" 
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} \n  TargetMethod: {} \n    ActualReturning: {} \n ",
                        matchingReturningTypeMsg, 
                        adviceMethodSpec.getAdvisorName(),
                        adviceMethodSpec.getAdviceMethodSignature(),
                        matchingReturningTypeMsg, adviceReturningType, 
                        MethodUtils.getMethodSignature(methodDescription),
                        targetReturningType
                );

                return false;
            }
        }
        return true;
    }

    private boolean matchesThrowingType(MethodDescription methodDescription, 
            boolean parameterizedThrowingType, Generic adviceThrowingType) {
        String matchingThrowingTypeMsg = parameterizedThrowingType ? "ParameterizedThrowing" : "AdviceThrowing";

        if(ClassUtils.isVisibleTo(adviceThrowingType.asErasure(), adviceMethodSpec.getAdviceMethod().getDeclaringType().asErasure()) == false) {
            LOGGER.warn("Ignored advice method referring to non public or non protected in the same package {} type under Joinpoint ClassLoader. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} {} \n",
                    matchingThrowingTypeMsg,
                    adviceMethodSpec.getAdvisorName(),
                    adviceMethodSpec.getAdviceMethodSignature(),
                    matchingThrowingTypeMsg, adviceThrowingType.getVisibility(), adviceThrowingType
            );

            return false;
        }

        TypeList.Generic exceptionTypes = methodDescription.getExceptionTypes();
        if(exceptionTypes.size() == 0) {
            if(ClassUtils.equals(adviceThrowingType, RUNTIME_EXCEPTION) == false) {
                LOGGER.warn("Ignored advice method with non RuntimeException throwing type. \n" 
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} \n  TargetMethod: {} \n    ActualThrowing: RuntimeException \n",
                        adviceMethodSpec.getAdvisorName(),
                        adviceMethodSpec.getAdviceMethodSignature(),
                        matchingThrowingTypeMsg, adviceThrowingType, 
                        MethodUtils.getMethodSignature(methodDescription)
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
            LOGGER.warn("Ignored advice method with throwing type is unassignable from target method's all throwing types. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} \n  TargetMethod: {} \n    ActualThrowing: {} \n",
                    adviceMethodSpec.getAdvisorName(),
                    adviceMethodSpec.getAdviceMethodSignature(),
                    matchingThrowingTypeMsg, adviceThrowingType, 
                    MethodUtils.getMethodSignature(methodDescription),
                    exceptionTypes
            );

            return false;
        }

        return true;
    }


    static class PojoMethodMatcher extends AdviceMethodMatcher {

        private ElementMatcher<MethodDescription> methodMatcher;

        public PojoMethodMatcher(AdviceMethodSpec adviceMethodSpec, ElementMatcher<MethodDescription> methodMatcher) {
            super(adviceMethodSpec);

            this.methodMatcher = methodMatcher;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doMatch(MethodDescription methodDescription) {
            return methodMatcher.matches(methodDescription);
        }
    }


    static class AspectJMethodMatcher extends AdviceMethodMatcher implements PointcutParameterMatcher {

        private final ExprPointcut exprPointcut;
        private final AspectJMethodSpec adviceMethodSpec; 

        public AspectJMethodMatcher(ExprPointcut exprPointcut, AspectJMethodSpec adviceMethodSpec) {
            super(adviceMethodSpec);

            this.exprPointcut = exprPointcut;
            this.adviceMethodSpec = adviceMethodSpec;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doMatch(MethodDescription methodDescription) {
            return exprPointcut.matches(methodDescription, this);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public boolean match(MethodDescription methodDescription, List<NamedPointcutParameter> pointcutParameters) {
            // 1.match parameter count and type
            if(pointcutParameters == null || pointcutParameters.size() != adviceMethodSpec.getPointcutParameterNames().size()) {
                LOGGER.warn("Ignored advice method with advice parameters is different to target method's resolved parameters. \n" 
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    AdviceParameters: {} \n  TargetMethod: {} \n    ResolvedParameters: {} \n",
                        adviceMethodSpec.getAdvisorName(),
                        adviceMethodSpec.getAdviceMethodSignature(),
                        adviceMethodSpec.getPointcutParameterNames(),
                        MethodUtils.getMethodSignature(methodDescription), 
                        pointcutParameters == null ? null : pointcutParameters.stream()
                                .map( p -> p.getParamName() )
                                .collect( Collectors.toList() )
                );

                return false;
            }

            for(NamedPointcutParameter pointcutParameterBinding : pointcutParameters) {
                String name = pointcutParameterBinding.getParamName();
                if(adviceMethodSpec.getPointcutParameterNames().contains(name) == false) {
                    LOGGER.warn("Ignored advice method with advice parameters do not contain target method's resolved parameter '{}'. \n" 
                            + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    AdviceParameters: {} \n  TargetMethod: {} \n    ResolvedParameters: {} \n",
                            name, 
                            adviceMethodSpec.getAdvisorName(),
                            adviceMethodSpec.getAdviceMethodSignature(),
                            adviceMethodSpec.getPointcutParameterNames(),
                            MethodUtils.getMethodSignature(methodDescription), 
                            pointcutParameters == null ? null : pointcutParameters.stream()
                                    .map( p -> p.getParamName() )
                                    .collect( Collectors.toList() )
                    );

                    return false;
                }

                TypeDescription paramType = adviceMethodSpec.getParameterMap().get(pointcutParameterBinding.getParamName()).getType().asErasure();
                if(ClassUtils.isVisibleTo(paramType, methodDescription.getDeclaringType().asErasure()) == false) {
                    LOGGER.warn("Ignored advice method referring to non public and non protected in the same package parameter type under Joinpoint ClassLoader. \n"
                            + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    parameter '{}': {} {} \n",
                            adviceMethodSpec.getAdvisorName(),
                            methodDescription.toGenericString(),
                            name, paramType.getVisibility(), paramType );

                    return false;
                }

                adviceMethodSpec.getPointcutParameters().put(name, pointcutParameterBinding);
            }

            Generic returnType = methodDescription.getReturnType();
            adviceMethodSpec.setVoidReturningOfTargetMethod( returnType.represents(void.class) );

            return true;
        }
    }

}
