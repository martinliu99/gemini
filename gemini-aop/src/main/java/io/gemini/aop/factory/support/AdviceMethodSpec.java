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

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.support.AspectJSpecs.AspectJAdvisorSpec;
import io.gemini.api.aop.Joinpoint;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.aspectj.weaver.PointcutParameter;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.core.util.Assert;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;

/**
 * 
 */
abstract class AdviceMethodSpec {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AdviceMethodSpec.class);

    protected final String advisorName;

    protected boolean isValid = true;

    private final TypeDescription adviceType;

    protected MethodDescription adviceMethod;
    private String adviceMethodSignature;

    private Generic parameterizedReturningType = null;
    private Generic parameterizedThrowingType = null;

    protected Generic adviceReturningParameterType = null;
    protected Generic adviceThrowingParameterType = null;


    protected AdviceMethodSpec(String advisorName, TypeDescription adviceType) {
        this.advisorName = advisorName;
        this.adviceType = adviceType;
    }

    protected MethodDescription doDiscoverAdviceMethod() {
        TypeDescription adviceType = this.adviceType;
        while(adviceType != null) {
            MethodDescription adviceMethod = discoverFirstAdviceMethod(adviceType);
            if(adviceMethod != null)
                return adviceMethod;

            adviceType = adviceType.getSuperClass().asErasure();
        }

        return null;
    }

    private MethodDescription discoverFirstAdviceMethod(TypeDescription adviceType) {
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

    protected void doDiscoverJoinpointTypeArguments(Generic joinpointType) {
        if(TypeDefinition.Sort.PARAMETERIZED != joinpointType.getSort()) {
            return;
        }

        TypeList.Generic typeVariables = joinpointType.getTypeArguments();

        Generic returningType = typeVariables.get(0);
        if(TypeDefinition.Sort.NON_GENERIC != returningType.getSort() && TypeDefinition.Sort.PARAMETERIZED != returningType.getSort()) { 
            LOGGER.warn("Ignored advice method with Generic or WildcardType ParameterizedReturning of MutableJoinpoint. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    ParameterizedReturning: {} \n",
                    advisorName,
                    getAdviceMethodSignature(),
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
                    advisorName,
                    getAdviceMethodSignature(),
                    returningType.asErasure().getDescriptor()
            );

            this.isValid = false;
            return;
        }
        this.parameterizedThrowingType = throwingType;
    }

    public String getAdvisorName() {
        return advisorName;
    }

    public boolean isValid() {
        return isValid;
    }

    public TypeDescription getAdviceType() {
        return adviceType;
    }

    public MethodDescription getAdviceMethod() {
        return adviceMethod;
    }

    public String getAdviceMethodSignature() {
        if(adviceMethodSignature == null)
            adviceMethodSignature = MethodUtils.getMethodSignature(adviceMethod);

        return adviceMethodSignature;
    }

    public Generic getParameterizedReturningType() {
        return parameterizedReturningType;
    }

    public Generic getParameterizedThrowingType() {
        return parameterizedThrowingType;
    }

    public Generic getAdviceReturningParameterType() {
        return adviceReturningParameterType;
    }

    public Generic getAdviceThrowingParameterType() {
        return adviceThrowingParameterType;
    }


    static class PojoMethodSpec extends AdviceMethodSpec {

        public PojoMethodSpec(String advisorName, TypeDescription adviceType) {
            super(advisorName, adviceType);

            // discover returning and throwing types
            MethodDescription adviceMethod = this.doDiscoverAdviceMethod();
            Assert.notNull(adviceMethod, "adviceMethod must not be null.");

            this.adviceMethod = adviceMethod;
            this.doDiscoverJoinpointTypeArguments(adviceMethod.getParameters().get(0).getType());
        }
    }


    static class AspectJMethodSpec extends AdviceMethodSpec {

        public static final TypeDescription JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.class);
        public static final TypeDescription MUTABLE_JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.MutableJoinpoint.class);

        private static final List<TypeDescription> ACCESSIBLE_OBJECTS = Arrays.asList( 
                TypeDescription.ForLoadedType.of(AccessibleObject.class),
                TypeDescription.ForLoadedType.of(Executable.class),
                TypeDescription.ForLoadedType.of(Constructor.class),
                TypeDescription.ForLoadedType.of(Method.class)
        );

        private final List<String> parameterNames;
        private final Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap;

        private List<String> pointcutParameterNames;
        private Map<String, NamedPointcutParameter> pointcutParameters;

        private final boolean isAround;
        private final boolean isBefore;
        private boolean isAfterReturning;
        private boolean isAfterThrowing;

        private boolean isVoidReturningOfTargetMethod = false;


        public AspectJMethodSpec(AspectJAdvisorSpec advisorSpec) {
            super(advisorSpec.getAdvisorName(), advisorSpec.getAspectJType());

            this.adviceMethod = advisorSpec.getAspectJMethod();
            AnnotationDescription aspectJAnnotation = advisorSpec.getAspectJAnnotation();

            ParameterList<ParameterDescription.InDefinedShape> parameters = adviceMethod.asDefined().getParameters();
            this.parameterNames = this.parseParameterNames(adviceMethod, aspectJAnnotation, parameters);

            this.parameterDescriptionMap = this.createParameterDescriptionMap(parameters, this.parameterNames);

            this.pointcutParameters = new LinkedHashMap<>();
            this.pointcutParameterNames = new ArrayList<>(parameterNames);
            this.resolvePointcutParameters(adviceMethod, 
                    parameterDescriptionMap, pointcutParameters, pointcutParameterNames);

            this.isAround = aspectJAnnotation.getAnnotationType().represents(Around.class);
            this.isBefore = aspectJAnnotation.getAnnotationType().represents(Before.class);

            this.adviceReturningParameterType = this.resolveAdviceReturningParamBindings(adviceMethod, 
                    aspectJAnnotation, parameterDescriptionMap, pointcutParameters, pointcutParameterNames);

            this.adviceThrowingParameterType = this.resolveAdviceThrowingParamBindings(adviceMethod, 
                    aspectJAnnotation, parameterDescriptionMap, pointcutParameters, pointcutParameterNames);
        }

        private List<String> parseParameterNames(MethodDescription methodDescription, 
                AnnotationDescription annotationDescription, ParameterList<ParameterDescription.InDefinedShape> parameters) {
            List<String> parameterNames = new ArrayList<>(parameters.size());
            if(parameters.size() == 0)
                return parameterNames;

            ParameterDescription.InDefinedShape index0Param = parameters.get(0);

            // 1.parse 'argNames' value in annotation
            AnnotationValue<?, ?> annotationValue = annotationDescription.getValue("argNames");
            String argNamesStr = annotationValue.resolve(String.class).toString();
            if(StringUtils.hasText(argNamesStr)) {
                StringTokenizer st = new StringTokenizer(argNamesStr, ",");
                if(st.countTokens() != parameters.size() && st.countTokens() != parameters.size() - 1) {
                    LOGGER.warn("Ignored AspectJ advice method with parameters is inconsistent with 'argNames' annotation attribute. \n"
                            + "  AdvisorSpec: {} \n  AdviceMethod: {} \n  ArgNames: {} \n", 
                            advisorName, 
                            getAdviceMethodSignature(),
                            argNamesStr
                    );

                    this.isValid = false;
                    return Collections.emptyList();
                }

                // first parameter should be joinpoint
                if(st.countTokens() == parameters.size() - 1) {
                    parameterNames.add(index0Param.getName());
                }
                while(st.hasMoreTokens()) {
                    parameterNames.add(st.nextToken().trim());
                }
                return parameterNames;
            }

            // 2.parse parameter names in MethodParameters section
            // validate parameter name for index0 parameter
            if(index0Param.getName().equals(index0Param.getActualName()) == false) {
                LOGGER.warn("Ignored AspectJ advice method without parameter reflection support and 'argNames' annotation attribute. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n {} \n {} \n",
                        advisorName, 
                        getAdviceMethodSignature(), 
                        index0Param.getName(), index0Param.getActualName()
                );

                this.isValid = false;
                return Collections.emptyList();
            }

            return parameters.stream()
                    .map( p -> p.getName() )
                    .collect( Collectors.toList() );
        }

        private Map<String, ParameterDescription.InDefinedShape> createParameterDescriptionMap(
                ParameterList<ParameterDescription.InDefinedShape> parameters, List<String> parameterNames) {
            if(parameterNames.size() == 0)
                return Collections.emptyMap();

            Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap = new LinkedHashMap<>(parameters.size());

            // access by parameter index other than parameter name which might NOT contain in bytecode file
            for(int index = 0; index < parameters.size(); index++) {
                ParameterDescription.InDefinedShape paramType = parameters.get(index);
                parameterDescriptionMap.put(parameterNames.get(index), paramType);
            }

            return parameterDescriptionMap;
        }

        private void resolvePointcutParameters(MethodDescription methodDescription, 
                Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap, 
                Map<String, NamedPointcutParameter> pointcutParameters, List<String> pointcutParameterNames) {
            if(parameterDescriptionMap.size() == 0) 
                return;

            // 1.bind first parameter
            ParameterDescription.InDefinedShape parameterDescription = methodDescription.asDefined().getParameters().get(0);
            String parameterName = parameterDescription.getName();
            TypeDescription parameterType = parameterDescription.getType().asErasure();

            if(JOINPOINT_TYPE.equals(parameterType)) {
                pointcutParameters.put(parameterName, 
                        new PointcutParameter.Default(parameterName, PointcutParameter.ParamType.JOINPOINT_PARAM));
                pointcutParameterNames.remove(parameterName);
            } else if(MUTABLE_JOINPOINT_TYPE.equals(parameterType)) {
                pointcutParameters.put(parameterName, 
                        new PointcutParameter.Default(parameterName, PointcutParameter.ParamType.MUTABLE_JOINPOINT_PARAM));
                pointcutParameterNames.remove(parameterName);

                this.doDiscoverJoinpointTypeArguments(parameterDescription.getType());
            } else if(ACCESSIBLE_OBJECTS.contains(parameterType)) {
                pointcutParameters.put(parameterName, 
                        new PointcutParameter.Default(parameterName, PointcutParameter.ParamType.STATIC_PART_PARAM));
                pointcutParameterNames.remove(parameterName);
            }
        }

        private Generic resolveAdviceReturningParamBindings(MethodDescription methodDescription, 
                AnnotationDescription aspectJAnnotation, Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap, 
                Map<String, NamedPointcutParameter> pointcutParameters, List<String> pointcutParameterNames) {
            this.isAfterReturning = aspectJAnnotation.getAnnotationType().represents(AfterReturning.class);
            if(this.isAfterReturning == false)
                return null;

            // 2.index returning parameters
            AnnotationValue<?, ?> annotationValue = aspectJAnnotation.getValue("returning");
            String returningParameter = annotationValue.resolve(String.class).trim();
            if(StringUtils.hasText(returningParameter) == false) {
                LOGGER.warn("Ignored AspectJ @AfterReturning advice method without 'returning' annotation attribute. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    returning: {} \n",
                        advisorName,
                        getAdviceMethodSignature(),
                        returningParameter
                );

                this.isValid = false;
                return null;
            }

            if(parameterDescriptionMap.containsKey(returningParameter) == false) { 
                LOGGER.warn("Ignored AspectJ @AfterReturning advice method with 'returning' annotation attribute referring to nonexistent parameter. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    returning: {} \n",
                        advisorName,
                        getAdviceMethodSignature(),
                        returningParameter
                );

                this.isValid = false;
                return null;
            }

            pointcutParameters.put(returningParameter, 
                    new PointcutParameter.Default(returningParameter, PointcutParameter.ParamType.RETURNING_ANNOTATION));
            pointcutParameterNames.remove(returningParameter);

            return parameterDescriptionMap.get(returningParameter).getType();
        }

        private Generic resolveAdviceThrowingParamBindings(MethodDescription methodDescription, 
                AnnotationDescription aspectJAnnotation, Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap, 
                Map<String, NamedPointcutParameter> pointcutParameters, List<String> pointcutParameterNames) {
            this.isAfterThrowing = aspectJAnnotation.getAnnotationType().represents(AfterThrowing.class);
            if(this.isAfterThrowing == false)
                return null;

            // 3.index throwing parameters
            AnnotationValue<?, ?> annotationValue = aspectJAnnotation.getValue("throwing");
            String throwingParameter = annotationValue.resolve(String.class).trim();

            if(StringUtils.hasText(throwingParameter) == false) {
                LOGGER.warn("Ignored AspectJ @AfterThrowing advice method without 'throwing' annotation attribute. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    throwing: {} \n",
                        advisorName,
                        getAdviceMethodSignature(),
                        throwingParameter
                );

                this.isValid = false;
                return null;
            }

            if(parameterDescriptionMap.containsKey(throwingParameter) == false) {
                LOGGER.warn("Ignored AspectJ @AfterThrowing advice method with 'throwing' annotation attribute referring to nonexistent parameter. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    throwing: {} \n",
                        advisorName,
                        getAdviceMethodSignature(),
                        throwingParameter
                );

                this.isValid = false;
                return null;
            }

            pointcutParameters.put(throwingParameter, 
                    new PointcutParameter.Default(throwingParameter, PointcutParameter.ParamType.THROWING_ANNOTATION));
            pointcutParameterNames.remove(throwingParameter);

            return parameterDescriptionMap.get(throwingParameter).getType();
        }


        public Map<String, ParameterDescription.InDefinedShape> getParameterMap() {
            return parameterDescriptionMap;
        }

        public List<String> getPointcutParameterNames() {
            return Collections.unmodifiableList(this.pointcutParameterNames);
        }

        public Map<String, TypeDescription> getPointcutParameterTypes() {
            Map<String, TypeDescription> parameterTypes = new LinkedHashMap<>(pointcutParameterNames.size());
            for(String parameterName : pointcutParameterNames)
                parameterTypes.put(parameterName, this.parameterDescriptionMap.get(parameterName).getType().asErasure());
            return parameterTypes;
        }

        public Map<String, NamedPointcutParameter> getPointcutParameters() {
            return pointcutParameters;
        }

        public boolean isAround() {
            return isAround;
        }

        public boolean isBefore() {
            return isBefore;
        }

        public boolean isAfterReturning() {
            return isAfterReturning;
        }

        public boolean isAfterThrowing() {
            return isAfterThrowing;
        }

        public void setVoidReturningOfTargetMethod(boolean isVoidReturningOfTargetMethod) {
            this.isVoidReturningOfTargetMethod = isVoidReturningOfTargetMethod;
        }

        public boolean isVoidReturningOfTargetMethod() {
            return isVoidReturningOfTargetMethod;
        }
    }
}
