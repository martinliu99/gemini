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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.matcher.ExprPointcut.PointcutParameterMatcher;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Joinpoint;
import io.gemini.api.aop.condition.ConditionContext;
import io.gemini.aspectj.weaver.PointcutParameter;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.matcher.ElementMatcher;


class AspectJAdvisorSpec extends AdvisorSpec.AbstractBase implements PointcutParameterMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectJAdvisorSpec.class);

    public static final TypeDescription JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.class);
    public static final TypeDescription MUTABLE_JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.MutableJoinpoint.class);

    private static final List<TypeDescription> ACCESSIBLE_OBJECTS = Arrays.asList( 
            TypeDescription.ForLoadedType.of(AccessibleObject.class),
            TypeDescription.ForLoadedType.of(Executable.class),
            TypeDescription.ForLoadedType.of(Constructor.class),
            TypeDescription.ForLoadedType.of(Method.class)
    );


    private final TypeDescription aspectJType;
    private final MethodDescription aspectJMethod;

    private final AdviceCategory adviceCategory;

    private final String pointcutExpression;

    private final Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap;

    private List<String> pointcutParameterNames;
    private Map<String, NamedPointcutParameter> namedPointcutParameters;

    private Generic adviceReturningParameterType = null;
    private Generic adviceThrowingParameterType = null;

    private boolean isVoidReturningOfTargetMethod = false;
    private boolean isValid = true;


    public static AspectJAdvisorSpec create(String advisorName, ElementMatcher<ConditionContext> condition, boolean perInstance, 
            String adviceClassName, TypeDescription aspectJType, MethodDescription adviceMethod, 
            AnnotationDescription aspectJAnnotation, int order) {
        AdviceCategory adviceCategory = AdviceCategory.parse(aspectJAnnotation.getAnnotationType().getSimpleName());

        AnnotationValue<?, ?> annotationValue = aspectJAnnotation.getValue("value");
        String pointcutExpression = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

        if(StringUtils.hasText(pointcutExpression) == false) {
            annotationValue = aspectJAnnotation.getValue("pointcut");
            pointcutExpression = annotationValue == null ? null : annotationValue.resolve(String.class).trim();
        }

        annotationValue = aspectJAnnotation.getValue("argNames");
        String argNamesStr = annotationValue.resolve(String.class).toString();

        String returningParameter = null;
        if(adviceCategory.isAfterReturning()) {
            annotationValue = aspectJAnnotation.getValue("returning");
            returningParameter = annotationValue == null ? null : annotationValue.resolve(String.class).trim();
        }

        String throwingParameter = null;
        if(adviceCategory.isAfterThrowing()) {
            annotationValue = aspectJAnnotation.getValue("throwing");
            throwingParameter = annotationValue == null ? null : annotationValue.resolve(String.class).trim();
        }

        return new AspectJAdvisorSpec(advisorName, condition, perInstance, 
                adviceClassName, aspectJType, adviceMethod, order,
                adviceCategory, pointcutExpression, argNamesStr, 
                returningParameter, throwingParameter);
    }

    public static AspectJAdvisorSpec create(String advisorName, ElementMatcher<ConditionContext> condition, boolean perInstance, 
            String adviceClassName, TypeDescription aspectJType, MethodDescription aspectJMethod, 
            ConfigView configView, String keyPrefix,  int order) {
        String adviceCategoryStr = configView.getAsString(keyPrefix + "adviceCategory", "");
        AdviceCategory adviceCategory = AdviceCategory.parse(adviceCategoryStr);

        String pointcutExpression = configView.getAsString(keyPrefix + "pointcutExpression", "").trim();

        String argNamesStr = configView.getAsString(keyPrefix + "argNames", "").trim();

        String returningParameter = configView.getAsString(keyPrefix + "returning", "").trim();;
        String throwingParameter = configView.getAsString(keyPrefix + "throwing", "").trim();;

        if(StringUtils.hasText(adviceClassName) == false)
            adviceClassName = aspectJType.getTypeName() + "_" + aspectJMethod.getName() + "_" + adviceCategory.getClass().getSimpleName();

        return new AspectJAdvisorSpec(advisorName, condition, perInstance, 
                adviceClassName, aspectJType, aspectJMethod, order,
                adviceCategory, pointcutExpression, argNamesStr, 
                returningParameter, throwingParameter);
    }


    private AspectJAdvisorSpec(String advisorName, ElementMatcher<ConditionContext> condition, boolean perInstance, 
            String adviceClassName, TypeDescription aspectJType, MethodDescription aspectJMethod, int order,
            AdviceCategory adviceCategory, String pointcutExpression, String argNamesStr, String returningParameter, String throwingParameter) {
        super(advisorName == null ? aspectJType.getTypeName() : advisorName, condition, 
                perInstance, adviceClassName, order);

        this.aspectJType = aspectJType;
        this.aspectJMethod = aspectJMethod;

        this.adviceCategory = adviceCategory;
        this.pointcutExpression = pointcutExpression;

        ParameterList<ParameterDescription.InDefinedShape> parameters = aspectJMethod.asDefined().getParameters();

        List<String> parameterNames = this.resolveParameterNames(aspectJMethod, argNamesStr, parameters);

        this.parameterDescriptionMap = this.createParameterDescriptionMap(parameters, parameterNames);

        this.namedPointcutParameters = new LinkedHashMap<>();
        this.pointcutParameterNames = new ArrayList<>(parameterNames);
        this.resolveJoinpointParamBinding(aspectJMethod, 
                parameterDescriptionMap, namedPointcutParameters, pointcutParameterNames);

        this.adviceReturningParameterType = this.resolveAdviceReturningParamBinding(
                aspectJMethod, parameterDescriptionMap, 
                this.adviceCategory, namedPointcutParameters, 
                returningParameter);
        if(adviceReturningParameterType != null)
            pointcutParameterNames.remove(returningParameter);

        this.adviceThrowingParameterType = this.resolveAdviceThrowingParamBinding(
                aspectJMethod, parameterDescriptionMap, 
                this.adviceCategory, namedPointcutParameters,
                throwingParameter);
        if(adviceThrowingParameterType != null)
            pointcutParameterNames.remove(throwingParameter);
    }

    private List<String> resolveParameterNames(MethodDescription methodDescription, 
            String argNamesStr, ParameterList<ParameterDescription.InDefinedShape> parameters) {
        if(parameters.size() == 0)
            return Collections.emptyList();

        ParameterDescription.InDefinedShape index0Param = parameters.get(0);
        List<String> parameterNames = new ArrayList<>(parameters.size());

        // 1.fetch 'argNames' value in annotation
        StringTokenizer st = new StringTokenizer(argNamesStr, ",");
        List<String> argNames = new ArrayList<>(st.countTokens());
        while(st.hasMoreTokens())
            argNames.add(st.nextToken().trim());

        if(argNames.size() > 0) {
            if(argNames.size() != parameters.size() && argNames.size() != parameters.size() - 1) {
                LOGGER.warn("Ignored AspectJ advice method with parameters is inconsistent with 'argNames' annotation attribute. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n  ArgNames: {} \n", 
                        getAdvisorName(), 
                        MethodUtils.getMethodSignature(aspectJMethod),
                        argNamesStr
                );

                this.isValid = false;
                return Collections.emptyList();
            }

            // first parameter should be joinpoint
            if(argNames.size() == parameters.size() - 1) {
                parameterNames.add(index0Param.getName());
            }
            parameterNames.addAll(argNames);

            return parameterNames;
        }

        // 2.parse parameter names in MethodParameters section
        // validate parameter name for index0 parameter
        if(index0Param.getName().equals(index0Param.getActualName()) == false) {
            LOGGER.warn("Ignored AspectJ advice method without parameter reflection support and 'argNames' annotation attribute. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n {} \n {} \n",
                    getAdvisorName(), 
                    MethodUtils.getMethodSignature(aspectJMethod), 
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

    private void resolveJoinpointParamBinding(MethodDescription methodDescription, 
            Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap, 
            Map<String, NamedPointcutParameter> pointcutParameters, List<String> pointcutParameterNames) {
        if(parameterDescriptionMap.size() == 0) 
            return;

        // bind first parameter
        ParameterDescription.InDefinedShape parameterDescription = methodDescription.asDefined().getParameters().get(0);
        String parameterName = parameterDescription.getName();
        TypeDescription parameterType = parameterDescription.getType().asErasure();

        if(JOINPOINT_TYPE.equals(parameterType)) {
            pointcutParameters.put(parameterName, 
                    new PointcutParameter.Default(parameterName, parameterDescription.getType(), PointcutParameter.ParamCategory.JOINPOINT_PARAM));
            pointcutParameterNames.remove(parameterName);
        } else if(MUTABLE_JOINPOINT_TYPE.equals(parameterType)) {
            pointcutParameters.put(parameterName, 
                    new PointcutParameter.Default(parameterName, parameterDescription.getType(), PointcutParameter.ParamCategory.MUTABLE_JOINPOINT_PARAM));
            pointcutParameterNames.remove(parameterName);
        } else if(ACCESSIBLE_OBJECTS.contains(parameterType)) {
            pointcutParameters.put(parameterName, 
                    new PointcutParameter.Default(parameterName, parameterDescription.getType(), PointcutParameter.ParamCategory.STATIC_PART_PARAM));
            pointcutParameterNames.remove(parameterName);
        }
    }

    private Generic resolveAdviceReturningParamBinding(MethodDescription aspectJMethod, 
            Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap, 
            AdviceCategory adviceCategory, 
            Map<String, NamedPointcutParameter> pointcutParameters, 
            String returningParameter) {
        if(adviceCategory.isAfterReturning() == false)
            return null;

        // index returning parameters
        if(StringUtils.hasText(returningParameter) == false) {
            LOGGER.warn("Ignored AspectJ @AfterReturning advice method without 'returning' annotation attribute. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    returning: {} \n",
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(aspectJMethod),
                    returningParameter
            );

            this.isValid = false;
            return null;
        }

        if(parameterDescriptionMap.containsKey(returningParameter) == false) { 
            LOGGER.warn("Ignored AspectJ @AfterReturning advice method with 'returning' annotation attribute referring to nonexistent parameter. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    returning: {} \n",
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(aspectJMethod),
                    returningParameter
            );

            this.isValid = false;
            return null;
        }

        Generic parameterType = parameterDescriptionMap.get(returningParameter).getType();

        pointcutParameters.put(returningParameter, 
                new PointcutParameter.Default(returningParameter, parameterType, PointcutParameter.ParamCategory.RETURNING_ANNOTATION));

        return parameterType;
    }

    private Generic resolveAdviceThrowingParamBinding(MethodDescription aspectJMethod, 
            Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap, 
            AdviceCategory adviceCategory,
            Map<String, NamedPointcutParameter> pointcutParameters, 
            String throwingParameter) {
        if(adviceCategory.isAfterThrowing() == false)
            return null;

        // index throwing parameters
        if(StringUtils.hasText(throwingParameter) == false) {
            LOGGER.warn("Ignored AspectJ @AfterThrowing advice method without 'throwing' annotation attribute. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    throwing: {} \n",
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(aspectJMethod),
                    throwingParameter
            );

            this.isValid = false;
            return null;
        }

        if(parameterDescriptionMap.containsKey(throwingParameter) == false) {
            LOGGER.warn("Ignored AspectJ @AfterThrowing advice method with 'throwing' annotation attribute referring to nonexistent parameter. \n"
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    throwing: {} \n",
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(aspectJMethod),
                    throwingParameter
            );

            this.isValid = false;
            return null;
        }

        Generic parameterType = parameterDescriptionMap.get(throwingParameter).getType();

        pointcutParameters.put(throwingParameter, 
                new PointcutParameter.Default(throwingParameter, parameterType, PointcutParameter.ParamCategory.THROWING_ANNOTATION));

        return parameterType;
    }


    public TypeDescription getAspectJType() {
        return aspectJType;
    }

    public MethodDescription getAspectJMethod() {
        return aspectJMethod;
    }

    public AdviceCategory getAdviceCategory() {
        return adviceCategory;
    }

    public String getPointcutExpression() {
        return pointcutExpression;
    }

    public Map<String, Generic> getPointcutParameterTypes() {
        Map<String, Generic> parameterTypes = new LinkedHashMap<>(pointcutParameterNames.size());
        for(String parameterName : pointcutParameterNames)
            parameterTypes.put(parameterName, this.parameterDescriptionMap.get(parameterName).getType());
        return parameterTypes;
    }

    public Map<String, NamedPointcutParameter> getNamedPointcutParameters() {
        return namedPointcutParameters;
    }


    public Generic getAdviceReturningParameterType() {
        return adviceReturningParameterType;
    }

    public Generic getAdviceThrowingParameterType() {
        return adviceThrowingParameterType;
    }

    public boolean isVoidReturningOfTargetMethod() {
        return isVoidReturningOfTargetMethod;
    }

    public boolean isValid() {
        return isValid;
    }


    /** 
     * {@inheritDoc}
     */
    @Override
    public boolean match(MethodDescription methodDescription, List<NamedPointcutParameter> pointcutParameters) {
        // 1.match parameter count and type
        if(pointcutParameters == null || pointcutParameters.size() != pointcutParameterNames.size()) {
            LOGGER.warn("Ignored advice method with advice parameters is different to target method's resolved parameters. \n" 
                    + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    AdviceParameters: {} \n  TargetMethod: {} \n    ResolvedParameters: {} \n",
                    getAdvisorName(),
                    MethodUtils.getMethodSignature(aspectJMethod),
                    pointcutParameterNames,
                    MethodUtils.getMethodSignature(methodDescription), 
                    pointcutParameters == null ? null : pointcutParameters.stream()
                            .map( p -> p.getParamName() )
                            .collect( Collectors.toList() )
            );

            return false;
        }

        for(NamedPointcutParameter pointcutParameterBinding : pointcutParameters) {
            String name = pointcutParameterBinding.getParamName();
            if(pointcutParameterNames.contains(name) == false) {
                LOGGER.warn("Ignored advice method with advice parameters do not contain target method's resolved parameter '{}'. \n" 
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    AdviceParameters: {} \n  TargetMethod: {} \n    ResolvedParameters: {} \n",
                        name, 
                        getAdvisorName(),
                        MethodUtils.getMethodSignature(aspectJMethod),
                        pointcutParameterNames,
                        MethodUtils.getMethodSignature(methodDescription), 
                        pointcutParameters == null ? null : pointcutParameters.stream()
                                .map( p -> p.getParamName() )
                                .collect( Collectors.toList() )
                );

                return false;
            }

            TypeDescription paramType = parameterDescriptionMap.get(pointcutParameterBinding.getParamName()).getType().asErasure();
            if(ClassUtils.isVisibleTo(paramType, methodDescription.getDeclaringType().asErasure()) == false) {
                LOGGER.warn("Ignored advice method referring to non public and non protected in the same package parameter type under Joinpoint ClassLoader. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    parameter '{}': {} {} \n",
                        getAdvisorName(),
                        methodDescription.toGenericString(),
                        name, paramType.getVisibility(), paramType );

                return false;
            }

            this.namedPointcutParameters.put(name, pointcutParameterBinding);
        }

        Generic returnType = methodDescription.getReturnType();
        isVoidReturningOfTargetMethod = returnType.represents(void.class);

        return true;
    }


    static enum AdviceCategory {

        BEFORE(true, false, false, false, false),
        AFTER(false, true, false, false, false),
        AFTER_RETURNING(false, false, true, false, false),
        AFTER_THROWING(false, false, false, true, false),
        AROUND(false, false, false, false, true);

        private final boolean before;

        private final boolean after;
        private final boolean afterReturning;
        private final boolean afterThrowing;

        private final boolean around;

        private static Map<String, AdviceCategory> VALUE_MAP;
        static {
            VALUE_MAP = new HashMap<>(AdviceCategory.values().length);
            for(AdviceCategory adviceCategory : AdviceCategory.values())
                VALUE_MAP.put(adviceCategory.toString().replace("_", ""), adviceCategory);
        }

        public static AdviceCategory parse(String value) {
            AdviceCategory adviceCategory = VALUE_MAP.get(value.trim().toUpperCase());
            if(adviceCategory != null)
                return adviceCategory;

            throw new IllegalArgumentException("Unsupported AdviceCategory '" + value + "'.");
        }

        private AdviceCategory(boolean before, boolean after, 
                boolean afterReturning, boolean afterThrowing,
                boolean around) {
            this.before = before;
            this.after = after;
            this.afterReturning = afterReturning;
            this.afterThrowing = afterThrowing;
            this.around = around;
        }

        public boolean isBefore() {
            return before;
        }

        public boolean isAfter() {
            return after;
        }

        public boolean isAfterReturning() {
            return afterReturning;
        }

        public boolean isAfterThrowing() {
            return afterThrowing;
        }

        public boolean isAround() {
            return around;
        }
    }

}
