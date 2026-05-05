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

import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.support.AdviceSpec.AspectJAdviceSpec;
import io.gemini.aop.factory.support.AdviceSpec.ByteBuddyAdviceSpec;
import io.gemini.aop.factory.support.AdviceSpec.PojoAdviceSpec;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.Pair;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

public interface AdviceMatcher extends ElementMatcher<MethodDescription> {

    /** 
     * {@inheritDoc}
     */
    @Override
    boolean matches(MethodDescription targetMethod);


    abstract class AbstractBase<A extends AdviceSpec> implements AdviceMatcher {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AdviceMatcher.class);

        private static final Generic RUNTIME_EXCEPTION = TypeDefinition.Sort.describe(RuntimeException.class);


        protected boolean matcheParameterizedArguments(MethodDescription adviceMethod, MethodDescription targetMethod) {
            // verify returning and throwing type arguments of joinpoint with target method
            Generic targetReturningType = targetMethod.isConstructor() 
                    ? targetMethod.getDeclaringType().asGenericType() : targetMethod.getReturnType();

            Pair<Generic, Generic> joinpointParamTypeArguments = resolveJoinpointParamTypeArguments(adviceMethod);
            if (joinpointParamTypeArguments == null)
                return true;

            // verify returning type argument
            Generic parameterizedReturningType = joinpointParamTypeArguments.getLeft();
            if (parameterizedReturningType != null 
                    && matchesReturningType(true, adviceMethod, parameterizedReturningType, targetMethod, targetReturningType) == false) {
                return false;
            }

            // verify throwing type argument
            Generic parameterizedThrowingType = joinpointParamTypeArguments.getRight();
            if (parameterizedThrowingType != null
                    && matchesThrowingType(true, adviceMethod, parameterizedThrowingType, targetMethod) == false) {
                return false;
            }

            return true;
        }

        protected Pair<Generic, Generic> resolveJoinpointParamTypeArguments(MethodDescription adviceMethod) {
            if (adviceMethod == null)
                return null;

            ParameterList<?> parameters = adviceMethod.getParameters();
            if (parameters.size() == 0)
                return null;

            Generic joinpointType = parameters.get(0).getType();
            if (TypeDefinition.Sort.PARAMETERIZED != joinpointType.getSort() ) {
                return null;
            }

            TypeList.Generic typeVariables = joinpointType.getTypeArguments();

            Generic returningType = typeVariables.asRawTypes().get(0);
            if (TypeDefinition.Sort.NON_GENERIC != returningType.getSort() && TypeDefinition.Sort.PARAMETERIZED != returningType.getSort()) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored advice method with Generic or WildcardType ParameterizedReturning of MutableJoinpoint. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    ParameterizedReturning: {} \n",
                            adviceMethod.getDeclaringType().getTypeName(),
                            MethodUtils.getMethodSignature(adviceMethod),
                            returningType.asErasure().getDescriptor()
                    );

                return null;
            }

            Generic throwingType = typeVariables.get(1);
            if (TypeDefinition.Sort.NON_GENERIC != throwingType.getSort() && TypeDefinition.Sort.PARAMETERIZED != throwingType.getSort()) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored advice method with Generic or WildcardType ParameterizedThrowing of MutableJoinpoint. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    ParameterizedThrowing: {} \n",
                            adviceMethod.getDeclaringType().getTypeName(),
                            MethodUtils.getMethodSignature(adviceMethod),
                            returningType.asErasure().getDescriptor()
                    );

                return null;
            }

            return new Pair<>(returningType, throwingType);
        }

        /**
         * Matches returning type with target class loader at class loading time.
         * 
         * @param parameterizedReturningType returning type defined via parameterized argument or advice returning argument
         * @param adviceMethod               advice method annotated with AspectJ annotation
         * @param adviceReturningType        returning type defined by advice method
         * @param targetMethod               target method in matching
         * @param targetReturningType        returning type of target method
         * @return matched or not
         */
        protected boolean matchesReturningType(boolean parameterizedReturningType, 
                MethodDescription adviceMethod, Generic adviceReturningType, 
                MethodDescription targetMethod, Generic targetReturningType) {
            targetReturningType = TypeDefinition.Sort.NON_GENERIC == adviceReturningType.getSort() 
                    || TypeDefinition.Sort.GENERIC_ARRAY == adviceReturningType.getSort()
                    ? targetReturningType.asRawType() : targetReturningType;
            String matchingReturningTypeMsg = parameterizedReturningType ? "ParameterizedReturning" : "AdviceReturning";

            // verify returning type visibility
            if (ClassUtils.isVisibleTo(adviceReturningType.asErasure(), adviceMethod.getDeclaringType().asErasure()) == false) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Ignored advice method referring to non public and non protected in the same package {} type under target ClassLoader. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    {}: {} {} \n",
                            matchingReturningTypeMsg,
                            adviceMethod.getDeclaringType().getTypeName(),
                            MethodUtils.getMethodSignature(adviceMethod),
                            matchingReturningTypeMsg, adviceReturningType.getVisibility(), adviceReturningType
                    );
                }

                return false;
            }

            // verify returning type assignment
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
                                adviceMethod.getDeclaringType().getTypeName(),
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
                                adviceMethod.getDeclaringType().getTypeName(),
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


        /**
         * Matches throwing type with target class loader at class loading time.
         * 
         * @param parameterizedThrowingType  throwing type defined via parameterized argument or advice throwing argument
         * @param adviceMethod               advice method annotated with AspectJ annotation
         * @param adviceThrowingType         throwing type defined by advice method
         * @param targetMethod               target method in matching
         * @return matched or not
         */
        protected boolean matchesThrowingType(boolean parameterizedThrowingType, 
                MethodDescription adviceMethod, Generic adviceThrowingType, MethodDescription targetMethod) {
            String matchingThrowingTypeMsg = parameterizedThrowingType ? "ParameterizedThrowing" : "AdviceThrowing";
            TypeDefinition declaringType = adviceMethod.getDeclaringType();

            // verify throwing type visibility
            if (ClassUtils.isVisibleTo(adviceThrowingType.asErasure(), declaringType.asErasure()) == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored advice method referring to non public or non protected in the same package {} type under target ClassLoader. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    {}: {} {} \n",
                            matchingThrowingTypeMsg,
                            declaringType.getTypeName(),
                            MethodUtils.getMethodSignature(adviceMethod),
                            matchingThrowingTypeMsg, adviceThrowingType.getVisibility(), adviceThrowingType
                    );

                return false;
            }

            // verify throwing type assignment
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
                                declaringType.getTypeName(),
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
                            declaringType.getTypeName(),
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


    class PojoAdviceMatcher extends AbstractBase<PojoAdviceSpec> {

        private final PojoAdviceSpec adviceSpec;


        protected PojoAdviceMatcher(PojoAdviceSpec adviceSpec) {
            this.adviceSpec = adviceSpec;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(MethodDescription targetMethod) {
            // verify returning and throwing type arguments of joinpoint with target method
            return matcheParameterizedArguments(adviceSpec.getAdviceMethod(), targetMethod);
        }
    }


    class AspectJAdviceMatcher extends AbstractBase<AspectJAdviceSpec> {

        private final AspectJAdviceSpec adviceSpec;


        protected AspectJAdviceMatcher(AspectJAdviceSpec adviceSpec) {
            this.adviceSpec = adviceSpec;
        }

        /**
         * {@inheritDoc}
         * 
         * It's necessary to verify target types' visibility since aspect advice class created by framework under aspect class loader
         * and could only access public target type or protected target type under same package.
         */
        @Override
        public boolean matches(MethodDescription targetMethod) {
            // 1.verify returning and throwing type arguments of joinpoint with target method
            MethodDescription adviceMethod = adviceSpec.getAdviceMethod();
            if (matcheParameterizedArguments(adviceMethod, targetMethod) == false)
                return false;


            // 2.verify returning and throwing type of advice method signature with target method
            Generic targetReturningType = targetMethod.isConstructor() 
                    ? targetMethod.getDeclaringType().asGenericType() : targetMethod.getReturnType();

            // verify returning type
            Generic adviceReturningParameterType = adviceSpec.getAdviceReturningParameterType();
            if (adviceReturningParameterType != null
                    && matchesReturningType(false, adviceMethod, adviceReturningParameterType, targetMethod, targetReturningType) == false) {
                return false;
            }

            // verify throwing type
            Generic adviceThrowingParameterType = adviceSpec.getAdviceThrowingParameterType();
            if (adviceThrowingParameterType != null
                    && matchesThrowingType(false, adviceMethod, adviceThrowingParameterType, targetMethod) == false) {
                return false;
            }

            // 3.verify parameter type of advice method signature with target method
            if (matchesParameterTypes(targetMethod, adviceReturningParameterType, adviceThrowingParameterType) == false)
                return false;

            return true;
        }

        /**
         * Matches parameter types of advice method with target class loader at class loading time,
         * except for adviceReturningParameterType, adviceThrowingParameterType.
         * 
         * @param targetMethod  target method in matching
         * @param adviceReturningParameterType returning type defined via returning argument
         * @param adviceThrowingParameterType  throwing type defined via throwing argument
         * @return matched or not
         */
        private boolean matchesParameterTypes(MethodDescription targetMethod, Generic adviceReturningParameterType, Generic adviceThrowingParameterType) {
            for (Entry<String, NamedPointcutParameter>  entry : adviceSpec.getNamedPointcutParameters().entrySet()) {
                String paramName = entry.getKey();
                NamedPointcutParameter pointcutParameterBinding = entry.getValue();

                Generic paramGeneric = pointcutParameterBinding.getParamType();
                if (adviceReturningParameterType != null && paramGeneric.equals(adviceReturningParameterType))
                    continue;
                if (adviceThrowingParameterType != null && paramGeneric.equals(adviceThrowingParameterType))
                    continue;

                // verify parameter type visibility
                TypeDescription paramType = paramGeneric.asErasure();
                if (ClassUtils.isVisibleTo(paramType, targetMethod.getDeclaringType().asErasure()) == false) {
                    if (LOGGER.isWarnEnabled()) 
                        LOGGER.warn("Ignored advice method referring to non public and non protected in the same package parameter type under target ClassLoader. \n"
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    parameter '{}': {} {} \n",
                                adviceSpec.getDeclaringType().getTypeName(),
                                MethodUtils.getMethodSignature(adviceSpec.getAdviceMethod()),
                                paramName, paramType.getVisibility(), paramType
                        );

                    return false;
                }

                // ignore parameter type assignment verified by {@link ExprPointcut}.
            }

            return true;
        }
    }


    class ByteBuddyAdviceMatcher extends AbstractBase<ByteBuddyAdviceSpec> {

        private final ByteBuddyAdviceSpec adviceSpec;


        protected ByteBuddyAdviceMatcher(ByteBuddyAdviceSpec adviceSpec) {
            this.adviceSpec = adviceSpec;
        }

        /**
         * {@inheritDoc}
         * 
         * It's unnecessary to verify target types' visibility since java compiler helps to validate it
         * when compiling ByteBuddy Advice class.
         */
        @Override
        public boolean matches(MethodDescription targetMethod) {
            // TODO: verify class assignment
            // 1.verify parameter of enter advice method
            return true;
        }
    }
}
