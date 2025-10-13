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
package io.gemini.aop.matcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.MethodUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 */
public class AdviceMethodMatcher implements ElementMatcher<MethodDescription> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AdviceMethodMatcher.class);

    private static final Generic RUNTIME_EXCEPTION = TypeDefinition.Sort.describe(RuntimeException.class);


    private final AdvisorSpec advisorSpec;

    private final MethodDescription adviceMethod;

    private boolean isValid = true;

    private Generic parameterizedReturningType = null;
    private Generic parameterizedThrowingType = null;

    private Generic adviceReturningParameterType = null;
    private Generic adviceThrowingParameterType = null;


    public static AdviceMethodMatcher create(AdvisorSpec advisorSpec, TypeDescription adviceType) {
        // discover returning and throwing types
        MethodDescription adviceMethod = discoverAdviceMethod(adviceType);
        Assert.notNull(adviceMethod, "adviceMethod must not be null.");

        return new AdviceMethodMatcher(advisorSpec, adviceMethod, null, null);
    }

    private static MethodDescription discoverAdviceMethod(TypeDescription adviceType) {
        while(adviceType != null) {
            MethodDescription adviceMethod = discoverFirstAdviceMethod(adviceType);
            if(adviceMethod != null)
                return adviceMethod;

            adviceType = adviceType.getSuperClass().asErasure();
        }

        return null;
    }

    private static MethodDescription discoverFirstAdviceMethod(TypeDescription adviceType) {
        MethodList<MethodDescription.InDefinedShape> beforeMethodFilter = adviceType.getDeclaredMethods()
                .filter(named("before")
                        .and(takesArgument(0, named(MutableJoinpoint.class.getName())))
                );
        if(beforeMethodFilter.isEmpty() == false) {
            MethodDescription beforeMethod = beforeMethodFilter.getOnly();

            Generic parameterType = beforeMethod.getParameters().get(0).getType();
            if(TypeDefinition.Sort.PARAMETERIZED == parameterType.getSort() && parameterType.getTypeArguments().size() > 0)
                return beforeMethod;
        }

        MethodList<MethodDescription.InDefinedShape> afterMethodFilter = adviceType.getDeclaredMethods()
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

    public static AdviceMethodMatcher create(AdvisorSpec advisorSpec, MethodDescription adviceMethod, 
            Generic adviceReturningParameterType, Generic adviceThrowingParameterType) {
        return new AdviceMethodMatcher(advisorSpec, adviceMethod, adviceReturningParameterType, adviceThrowingParameterType);
    }


    private AdviceMethodMatcher(AdvisorSpec advisorSpec, MethodDescription adviceMethod, 
            Generic adviceReturningParameterType, Generic adviceThrowingParameterType) {
        this.advisorSpec = advisorSpec;

        this.adviceMethod = adviceMethod;
        this.doDiscoverJoinpointTypeArguments(adviceMethod.getParameters().get(0).getType());

        this.adviceReturningParameterType = adviceReturningParameterType;
        this.adviceThrowingParameterType = adviceThrowingParameterType;
    }

    private void doDiscoverJoinpointTypeArguments(Generic joinpointType) {
        if(TypeDefinition.Sort.PARAMETERIZED != joinpointType.getSort()) {
            return;
        }

        TypeList.Generic typeVariables = joinpointType.getTypeArguments();

        Generic returningType = typeVariables.get(0);
        if(TypeDefinition.Sort.NON_GENERIC != returningType.getSort() && TypeDefinition.Sort.PARAMETERIZED != returningType.getSort()) { 
            LOGGER.warn("Ignored advice method with Generic or WildcardType ParameterizedReturning of MutableJoinpoint. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    ParameterizedReturning: {} \n",
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(adviceMethod),
                    returningType.asErasure().getDescriptor()
            );

            this.isValid = false;
            return;
        }
        this.parameterizedReturningType = returningType;

        Generic throwingType = typeVariables.get(1);
        if(TypeDefinition.Sort.NON_GENERIC != throwingType.getSort() && TypeDefinition.Sort.PARAMETERIZED != throwingType.getSort()) {
            LOGGER.warn("Ignored advice method with Generic or WildcardType ParameterizedThrowing of MutableJoinpoint. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    ParameterizedThrowing: {} \n",
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(adviceMethod),
                    returningType.asErasure().getDescriptor()
            );

            this.isValid = false;
            return;
        }
        this.parameterizedThrowingType = throwingType;
    }

    protected String getAdvisorName() {
        return advisorSpec.getAdvisorName();
    }


    public Generic getParameterizedReturningType() {
        return parameterizedReturningType;
    }

    public Generic getParameterizedThrowingType() {
        return parameterizedThrowingType;
    }


    /**
     * {@inheritDoc}
     */
    public boolean matches(MethodDescription methodDescription) {
        // 1.validate matcher
        if(isValid == false)
            return false;

        if(adviceMethod == null)
            return false;


        // 2.verify returning and throwing with matched method
        // verify returning type argument
        Generic targetReturningType = methodDescription.isConstructor() 
                ? methodDescription.getDeclaringType().asGenericType() : methodDescription.getReturnType();

        if(parameterizedReturningType != null 
                && matchesReturningType(methodDescription, true, targetReturningType, parameterizedReturningType) == false) {
            return false;
        }

        if(adviceReturningParameterType != null
                && matchesReturningType(methodDescription, false, targetReturningType, adviceReturningParameterType) == false) {
            return false;
        }

        // verify throwing type argument
        if(parameterizedThrowingType != null
                && matchesThrowingType(methodDescription, true, parameterizedThrowingType) == false) {
            return false;
        }

        if(adviceThrowingParameterType != null
                && matchesThrowingType(methodDescription, false, adviceThrowingParameterType) == false) {
            return false;
        }

        return true;
    }

    private boolean matchesReturningType(MethodDescription methodDescription, 
            boolean parameterizedReturningType, Generic targetReturningType, Generic adviceReturningType) {
        targetReturningType = TypeDefinition.Sort.NON_GENERIC == adviceReturningType.getSort() 
                || TypeDefinition.Sort.GENERIC_ARRAY == adviceReturningType.getSort()
                ? targetReturningType.asRawType() : targetReturningType;

        String matchingReturningTypeMsg = parameterizedReturningType ? "ParameterizedReturning" : "AdviceReturning";

        if(ClassUtils.isVisibleTo(adviceReturningType.asErasure(), adviceMethod.getDeclaringType().asErasure()) == false) {
            LOGGER.warn("Ignored advice method referring to non public and non protected in the same package {} type under Joinpoint ClassLoader. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} {} \n",
                    matchingReturningTypeMsg,
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(adviceMethod),
                    matchingReturningTypeMsg, adviceReturningType.getVisibility(), adviceReturningType
            );

            return false;
        }

        if(parameterizedReturningType == true) {
            if(ClassUtils.equals(adviceReturningType, targetReturningType) == false) {
                LOGGER.warn("Ignored advice method with {} is different to target method's returning type. \n" 
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} \n  TargetMethod: {} \n    ActualReturning: {} \n",
                        matchingReturningTypeMsg,
                        getAdvisorName(),
                        MethodUtils.getMethodSignature(adviceMethod),
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
                        getAdvisorName(),
                        MethodUtils.getMethodSignature(adviceMethod),
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

        if(ClassUtils.isVisibleTo(adviceThrowingType.asErasure(), adviceMethod.getDeclaringType().asErasure()) == false) {
            LOGGER.warn("Ignored advice method referring to non public or non protected in the same package {} type under Joinpoint ClassLoader. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} {} \n",
                    matchingThrowingTypeMsg,
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(adviceMethod),
                    matchingThrowingTypeMsg, adviceThrowingType.getVisibility(), adviceThrowingType
            );

            return false;
        }

        TypeList.Generic exceptionTypes = methodDescription.getExceptionTypes();
        if(exceptionTypes.size() == 0) {
            if(ClassUtils.equals(adviceThrowingType, RUNTIME_EXCEPTION) == false) {
                LOGGER.warn("Ignored advice method with non RuntimeException throwing type. \n" 
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    {}: {} \n  TargetMethod: {} \n    ActualThrowing: RuntimeException \n",
                        getAdvisorName(),
                        MethodUtils.getMethodSignature(adviceMethod),
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
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(adviceMethod),
                    matchingThrowingTypeMsg, adviceThrowingType, 
                    MethodUtils.getMethodSignature(methodDescription),
                    exceptionTypes
            );

            return false;
        }

        return true;
    }

}
