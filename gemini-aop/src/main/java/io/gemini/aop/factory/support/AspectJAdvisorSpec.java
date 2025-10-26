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


    private final TypeDescription aspectJType;
    private final MethodDescription aspectJMethod;

    private final AdviceCategory adviceCategory;

    private final String pointcutExpression;

    private final Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap;

    private final List<String> pointcutParameterNames;
    private final Map<String, NamedPointcutParameter> namedPointcutParameters;

    private final Generic adviceReturningParameterType;
    private final Generic adviceThrowingParameterType;

    private boolean isVoidReturningOfTargetMethod = false;


    AspectJAdvisorSpec(String advisorName, ElementMatcher<ConditionContext> condition, 
            boolean perInstance, String adviceClassName, int order,
            TypeDescription aspectJType, MethodDescription aspectJMethod, 
            AdviceCategory adviceCategory, String pointcutExpression, 
            Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap, 
            List<String> pointcutParameterNames, Map<String, NamedPointcutParameter> namedPointcutParameters,
            Generic adviceReturningParameterType, Generic adviceThrowingParameterType) {
        super(advisorName == null ? adviceClassName : advisorName, condition, 
                perInstance, adviceClassName, order);

        this.aspectJType = aspectJType;
        this.aspectJMethod = aspectJMethod;

        this.adviceCategory = adviceCategory;
        this.pointcutExpression = pointcutExpression;

        this.parameterDescriptionMap = parameterDescriptionMap;

        this.pointcutParameterNames = pointcutParameterNames;
        this.namedPointcutParameters = namedPointcutParameters;

        this.adviceReturningParameterType = adviceReturningParameterType;
        this.adviceThrowingParameterType = adviceThrowingParameterType;
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


    protected Map<String, ParameterDescription.InDefinedShape> getParameterDescriptionMap() {
        return parameterDescriptionMap;
    }

    protected List<String> getPointcutParameterNames() {
        return pointcutParameterNames;
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


    static enum Parser {

        INSTANCE;

        private static final Logger LOGGER = LoggerFactory.getLogger(AspectJAdvisorSpec.class);

        private static final TypeDescription JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.class);
        private static final TypeDescription MUTABLE_JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.MutableJoinpoint.class);

        private static final List<TypeDescription> ACCESSIBLE_OBJECTS = Arrays.asList( 
                TypeDescription.ForLoadedType.of(AccessibleObject.class),
                TypeDescription.ForLoadedType.of(Executable.class),
                TypeDescription.ForLoadedType.of(Constructor.class),
                TypeDescription.ForLoadedType.of(Method.class)
        );


        public static AspectJAdvisorSpec parse(ElementMatcher<ConditionContext> condition, boolean perInstance, int order, 
                TypeDescription aspectJType, MethodDescription aspectJMethod, AnnotationDescription aspectJAnnotation) {
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

            return create(null, condition, 
                    perInstance, order, 
                    aspectJType, aspectJMethod, 
                    adviceCategory, pointcutExpression, 
                    argNamesStr, returningParameter, throwingParameter);
        }

        public static AspectJAdvisorSpec parse(String advisorName, ElementMatcher<ConditionContext> condition, 
                boolean perInstance,  int order, 
                TypeDescription aspectJType, MethodDescription aspectJMethod, 
                ConfigView configView, String keyPrefix) {
            String adviceCategoryStr = configView.getAsString(keyPrefix + "adviceCategory", "");
            AdviceCategory adviceCategory = AdviceCategory.parse(adviceCategoryStr);

            String pointcutExpression = configView.getAsString(keyPrefix + "pointcutExpression", "").trim();

            String argNamesStr = configView.getAsString(keyPrefix + "argNames", "").trim();

            String returningParameter = configView.getAsString(keyPrefix + "returning", "").trim();;
            String throwingParameter = configView.getAsString(keyPrefix + "throwing", "").trim();;

            return create(advisorName, condition, 
                    perInstance, order, 
                    aspectJType, aspectJMethod, 
                    adviceCategory, pointcutExpression, 
                    argNamesStr, returningParameter, throwingParameter);
        }

        private static AspectJAdvisorSpec create(String advisorName, ElementMatcher<ConditionContext> condition, 
                boolean perInstance, int order,
                TypeDescription aspectJType, MethodDescription aspectJMethod, 
                AdviceCategory adviceCategory, String pointcutExpression, 
                String argNamesStr, String returningParameter, String throwingParameter) {
            // 1.define advice class name
            String adviceClassName = aspectJType.getTypeName() + "_$" + aspectJMethod.getName() + "_" + adviceCategory + "$";

            // 2.resolve parameter name/type pair
            ParameterList<ParameterDescription.InDefinedShape> parameters = aspectJMethod.asDefined().getParameters();

            List<String> parameterNames = resolveParameterNames(advisorName, aspectJMethod, argNamesStr, parameters);
            if(parameterNames == null)
                return null;

            Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap = createParameterDescriptionMap(parameters, parameterNames);

            // 3.bind AspectJ method parameters
            List<String> pointcutParameterNames = new ArrayList<>(parameterNames);
            Map<String, NamedPointcutParameter> namedPointcutParameters = new LinkedHashMap<>(parameterNames.size());

            resolveJoinpointParamBinding(aspectJMethod, 
                    parameterDescriptionMap, namedPointcutParameters, pointcutParameterNames);

            Generic returningParameterType = null;
            if(adviceCategory.isAfterReturning()) {
                returningParameterType = resolveAdviceReturningParamBinding(
                        advisorName, aspectJMethod, parameterDescriptionMap, 
                        adviceCategory, namedPointcutParameters, 
                        returningParameter);

                if(returningParameterType == null)
                    return null;
                else
                    pointcutParameterNames.remove(returningParameter);
            }

            Generic throwingParameterType = null;
            if(adviceCategory.isAfterThrowing()) {
                throwingParameterType = resolveAdviceThrowingParamBinding(
                        advisorName, aspectJMethod, parameterDescriptionMap, 
                        adviceCategory, namedPointcutParameters,
                        throwingParameter);

                if(throwingParameterType == null)
                    return null;
                else
                    pointcutParameterNames.remove(throwingParameter);
            }

            return new AspectJAdvisorSpec(advisorName, condition,
                    perInstance, adviceClassName, order,
                    aspectJType, aspectJMethod, 
                    adviceCategory, pointcutExpression,
                    parameterDescriptionMap,
                    pointcutParameterNames, namedPointcutParameters,
                    returningParameterType, throwingParameterType);
        }

        private static List<String> resolveParameterNames(String advisorName, MethodDescription aspectJMethod, 
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
                            advisorName, 
                            MethodUtils.getMethodSignature(aspectJMethod),
                            argNamesStr
                    );

                    return null;
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
                        advisorName, 
                        MethodUtils.getMethodSignature(aspectJMethod), 
                        index0Param.getName(), index0Param.getActualName()
                );

                return null;
            }

            return parameters.stream()
                    .map( p -> p.getName() )
                    .collect( Collectors.toList() );
        }

        private static Map<String, ParameterDescription.InDefinedShape> createParameterDescriptionMap(
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

        private static void resolveJoinpointParamBinding(MethodDescription methodDescription, 
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

        private static Generic resolveAdviceReturningParamBinding(String advisorName, MethodDescription aspectJMethod, 
                Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap, 
                AdviceCategory adviceCategory, Map<String, NamedPointcutParameter> pointcutParameters, String returningParameter) {
            // resolve returning parameters
            if(StringUtils.hasText(returningParameter) == false) {
                LOGGER.warn("Ignored AspectJ @AfterReturning advice method without 'returning' annotation attribute. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    returning: {} \n",
                        advisorName,
                        MethodUtils.getMethodSignature(aspectJMethod),
                        returningParameter
                );

                return null;
            }

            if(parameterDescriptionMap.containsKey(returningParameter) == false) { 
                LOGGER.warn("Ignored AspectJ @AfterReturning advice method with 'returning' annotation attribute referring to nonexistent parameter. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    returning: {} \n",
                        advisorName,
                        MethodUtils.getMethodSignature(aspectJMethod),
                        returningParameter
                );

                return null;
            }

            Generic parameterType = parameterDescriptionMap.get(returningParameter).getType();
            pointcutParameters.put(returningParameter, 
                    new PointcutParameter.Default(returningParameter, parameterType, PointcutParameter.ParamCategory.RETURNING_ANNOTATION));

            return parameterType;
        }

        private static Generic resolveAdviceThrowingParamBinding(String advisorName, MethodDescription aspectJMethod, 
                Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap, 
                AdviceCategory adviceCategory, Map<String, NamedPointcutParameter> pointcutParameters, String throwingParameter) {
            // resolve throwing parameters
            if(StringUtils.hasText(throwingParameter) == false) {
                LOGGER.warn("Ignored AspectJ @AfterThrowing advice method without 'throwing' annotation attribute. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    throwing: {} \n",
                        advisorName,
                        MethodUtils.getMethodSignature(aspectJMethod),
                        throwingParameter
                );

                return null;
            }

            if(parameterDescriptionMap.containsKey(throwingParameter) == false) {
                LOGGER.warn("Ignored AspectJ @AfterThrowing advice method with 'throwing' annotation attribute referring to nonexistent parameter. \n"
                        + "  AdvisorSpec: {} \n  AdviceMethod: {} \n    throwing: {} \n",
                        advisorName,
                        MethodUtils.getMethodSignature(aspectJMethod),
                        throwingParameter
                );

                return null;
            }

            Generic parameterType = parameterDescriptionMap.get(throwingParameter).getType();
            pointcutParameters.put(throwingParameter, 
                    new PointcutParameter.Default(throwingParameter, parameterType, PointcutParameter.ParamCategory.THROWING_ANNOTATION));

            return parameterType;
        }
    }
}
