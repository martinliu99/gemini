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
/**
 * 
 */
package io.gemini.aop.factory.support;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.FactoryContext;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.AdvisorSpec.ExprPointcutSpec;
import io.gemini.api.aop.AdvisorSpec.PojoPointcutSpec;
import io.gemini.api.aop.AopException;
import io.gemini.api.aop.Joinpoint;
import io.gemini.api.aop.MatchingContext;
import io.gemini.api.aop.Pointcut;
import io.gemini.aspectj.weaver.PointcutParameter;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 */
public interface AdvisorSpecParser {


    static AdvisorSpec parseAdvisorSpec(
            FactoryContext factoryContext, 
            String adviceClassName, 
            ElementMatcher<MatchingContext> condition,
            AnnotationDescription advisorAnnotation) throws IgnoredSpecException {
        return new AbstractAdvisorSpec(condition) {
            {
                this.doParseAdvisorSpec(
                        factoryContext,
                        adviceClassName, 
                        advisorAnnotation
                );
            }
        };
    }


    static AdvisorSpec.PojoPointcutSpec parsePojoPointcutAdvisorSpec(
            FactoryContext factoryContext, 
            ElementMatcher<MatchingContext> condition,
            ConfigView configView, 
            String configKeyPrefix, 
            PojoPointcutSpec existingAdvisorSpec) throws IgnoredSpecException {
        return new AbstractPojoPointcutSpec(condition) {
            {
                this.doParseAdvisorSpec(
                        factoryContext,
                        existingAdvisorSpec != null ? existingAdvisorSpec.getAdviceClassName() : null, 
                        configView, configKeyPrefix, existingAdvisorSpec
                );

                this.setPointcut(
                        existingAdvisorSpec.getPointcut() );
            }
        };
    }


    static AdvisorSpec.ExprPointcutSpec parseExprPointcutAdvisorSpec(
            FactoryContext factoryContext, 
            String adviceClassName, 
            ElementMatcher<MatchingContext> condition,
            AnnotationDescription advisorAnnotation, 
            AnnotationDescription exprPointcutAnnotation) throws IgnoredSpecException {
        return new AbstractExprPointcutSpec(condition) {
            {
                this.doParseAdvisorSpec(factoryContext, adviceClassName, advisorAnnotation);
                this.doParseExprPointcutSpec(exprPointcutAnnotation);
            }
        };
    }

    static AdvisorSpec.ExprPointcutSpec parseExprPointcutAdvisorSpec(
            FactoryContext factoryContext, 
            ElementMatcher<MatchingContext> condition,
            ConfigView configView, 
            String configKeyPrefix, 
            ExprPointcutSpec existingAdvisorSpec) throws IgnoredSpecException {
        return new AbstractExprPointcutSpec(condition) {
            {
                this.doParseAdvisorSpec(
                        factoryContext,
                        existingAdvisorSpec != null ? existingAdvisorSpec.getAdviceClassName() : null,
                        configView, configKeyPrefix, existingAdvisorSpec
                );

                this.doParseExprPointcutSpec(configView, configKeyPrefix, existingAdvisorSpec);
            }
        };
    }

    static AdvisorSpec.ExprPointcutSpec parseExprPointcutAdvisorSpec(
            FactoryContext factoryContext, 
            ElementMatcher<MatchingContext> condition,
            ConfigView configView, 
            String configKeyPrefix, 
            PojoPointcutSpec existingAdvisorSpec) throws IgnoredSpecException {
        return new AbstractExprPointcutSpec(condition) {
            {
                this.doParseAdvisorSpec(
                        factoryContext,
                        existingAdvisorSpec != null ? existingAdvisorSpec.getAdviceClassName() : null,
                        configView, configKeyPrefix, existingAdvisorSpec
                );

                this.setPointcut(existingAdvisorSpec.getPointcut());
            }
        };
    }


    static AspectJPointcutAdvisorSpec parseAspectJPointcutAdvisorSpec(
            FactoryContext factoryContext, 
            TypeDescription aspectJType, 
            MethodDescription aspectJMethod, 
            ElementMatcher<MatchingContext> condition,
            AnnotationDescription advisorAnnotation, 
            AnnotationDescription exprPointcutAnnotation,
            AnnotationDescription aspectJAnnotation) throws IgnoredSpecException {
        return new AbstractAspectJPointcutSpec(aspectJType, aspectJMethod, condition) {
            {
                String adviceCategoryValue = aspectJAnnotation.getAnnotationType().getSimpleName().toUpperCase();
                this.setAdviceCategory(
                        AdviceCategory.parse(adviceCategoryValue) );

                doParseAdvisorSpec(factoryContext, doGenerateAdviceClassName(adviceCategoryValue), advisorAnnotation);
                doParseExprPointcutSpec(exprPointcutAnnotation);
                doParseAspectJAdviceSpec(aspectJAnnotation);

                doInitializeSpec();
            }
        };
    }

    static AspectJPointcutAdvisorSpec parseAspectJPointcutAdvisorSpec(
            FactoryContext factoryContext, 
            TypeDescription aspectJType, 
            MethodDescription aspectJMethod, 
            ElementMatcher<MatchingContext> condition,
            ConfigView configView, 
            String configKeyPrefix,
            AdvisorSpec.ExprPointcutSpec existingAdvisorSpec) throws IgnoredSpecException {
        return new AbstractAspectJPointcutSpec(aspectJType, aspectJMethod, condition) {
            {
                String adviceCategoryKey = configKeyPrefix + "adviceCategory";
                String adviceCategoryValue = configView.getAsString(adviceCategoryKey, "").toUpperCase();

                try {
                    this.setAdviceCategory(
                            AdviceCategory.parse(adviceCategoryValue) );
                } catch (Exception e) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored AspectJ advice method with illegal 'adviceCategory' configuration property. \n"
                                + "  {}: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    {}: {} \n",
                                getSpecType(), aspectJType.getTypeName(), 
                                MethodUtils.getMethodSignature(aspectJMethod),
                                adviceCategoryKey, adviceCategoryValue
                        );

                    throw new IgnoredSpecException();
                }

                doParseAdvisorSpec(
                        factoryContext,
                        doGenerateAdviceClassName(adviceCategoryValue),
                        configView, configKeyPrefix, existingAdvisorSpec
                );
                doParseExprPointcutSpec(configView, configKeyPrefix, existingAdvisorSpec);
                doParseAspectJAdviceSpec(configView, configKeyPrefix);

                doInitializeSpec();
            }
        };
    }


    class IgnoredSpecException extends AopException {

        private static final long serialVersionUID = -6688678161574533296L;


        public IgnoredSpecException() {
            super("");
        }
    }


    @NoScanning
    abstract class AbstractAdvisorSpec extends AdvisorSpec.AbstractBase {

        AbstractAdvisorSpec(ElementMatcher<MatchingContext> condition) throws IgnoredSpecException {
            super();

            setCondition(condition);
        }

        protected void doParseAdvisorSpec(FactoryContext factoryContext, 
                String adviceClassName, AnnotationDescription advisorAnnotation) {
            if (advisorAnnotation == null) {
                this.setAdvisorName(adviceClassName);
                this.setAdviceClassName(adviceClassName);
            } else {
                String advisorName = advisorAnnotation.getValue("advisorName").resolve(String.class).trim();
                this.setAdvisorName(
                        StringUtils.hasText(advisorName) ? advisorName : adviceClassName );


                this.setInheritClassLoaderMatcher( 
                        advisorAnnotation.getValue("inheritClassLoaderMatcher").resolve(Boolean.class) );

                this.setInheritTypeMatcher( 
                        advisorAnnotation.getValue("inheritTypeMatcher").resolve(Boolean.class) );


                this.setAdviceClassName(adviceClassName);

                this.setPerInstance( 
                        advisorAnnotation.getValue("perInstance").resolve(Boolean.class) );

                this.setOrder( 
                        advisorAnnotation.getValue("order").resolve(Integer.class) );
            }
        }

        protected void doParseAdvisorSpec(FactoryContext factoryContext, 
                String adviceClassName, ConfigView configView, String configKeyPrefix,
                AdvisorSpec existingAdvisorSpec) {
            // overwrite configuration properties if exists
            String advisorName = configView.getAsString(configKeyPrefix + "advisorName", null);
            this.setAdvisorName( 
                    StringUtils.hasText(advisorName) 
                            ? advisorName
                            : existingAdvisorSpec == null ? adviceClassName : existingAdvisorSpec.getAdvisorName() 
            );


            boolean defaultInheritClassLoaderMatcher = existingAdvisorSpec == null
                    ? AdvisorSpec.DEFAULT_INHERIT_CLASSLOADER_MATCHER : existingAdvisorSpec.isInheritClassLoaderMatcher();
            this.setInheritClassLoaderMatcher(
                    configView.getAsBoolean(configKeyPrefix + "inheritClassLoaderMatcher", defaultInheritClassLoaderMatcher ) );

            boolean defaultInheritTypeMatcher = existingAdvisorSpec == null 
                    ? AdvisorSpec.DEFAULT_INHERIT_TYPE_MATCHER : existingAdvisorSpec.isInheritTypeMatcher();
            this.setInheritTypeMatcher(
                    configView.getAsBoolean(configKeyPrefix + "inheritTypeMatcher", defaultInheritTypeMatcher ) );


            this.setAdviceClassName(
                    configView.getAsString(configKeyPrefix + "adviceClassName", adviceClassName ) );


            boolean defaultPerInstance = existingAdvisorSpec == null 
                    ? AdvisorSpec.DEFAULT_PER_INSTANCE : existingAdvisorSpec.isPerInstance();
            this.setPerInstance(
                    configView.getAsBoolean(configKeyPrefix + "perInstance", defaultPerInstance ) );

            int defaultOrder = existingAdvisorSpec == null 
                    ? AdvisorSpec.DEFAULT_ORDER : existingAdvisorSpec.getOrder();
            this.setOrder(
                    configView.getAsInteger(configKeyPrefix + "order", defaultOrder ) );
        }


        protected String getSpecType() {
            return AdvisorSpec.class.getSimpleName();
        }
    }


    @NoScanning
    abstract class AbstractPointcutSpec extends AbstractAdvisorSpec 
            implements AdvisorSpec.PointcutAdvisorSpec {

        private Pointcut pointcut;


        AbstractPointcutSpec(ElementMatcher<MatchingContext> condition) throws IgnoredSpecException {
            super(condition);
        }


        public Pointcut getPointcut() {
            return pointcut;
        }

        protected void setPointcut(Pointcut pointcut) {
            this.pointcut = pointcut;
        }

        protected String getSpecType() {
            return AdvisorSpec.PojoPointcutSpec.class.getSimpleName();
        }
    }


    @NoScanning
    abstract class AbstractPojoPointcutSpec extends AbstractPointcutSpec 
            implements AdvisorSpec.PojoPointcutSpec {

        AbstractPojoPointcutSpec(ElementMatcher<MatchingContext> condition) throws IgnoredSpecException {
            super(condition);
        }


        protected String getSpecType() {
            return AdvisorSpec.PojoPointcutSpec.class.getSimpleName();
        }
    }


    @NoScanning
    abstract class AbstractExprPointcutSpec extends AbstractPointcutSpec implements AdvisorSpec.ExprPointcutSpec {

        private String pointcutExpression;


        AbstractExprPointcutSpec(ElementMatcher<MatchingContext> condition) throws IgnoredSpecException {
            super(condition);
        }

        @Override
        public String getPointcutExpression() {
            return pointcutExpression;
        }

        protected void setPointcutExpression(String pointcutExpression) {
            this.pointcutExpression = pointcutExpression;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AdvisorSpec.ExprPointcutSpec.class.getSimpleName();
        }

        protected void doParseExprPointcutSpec(AnnotationDescription exprPointcutAnnotation) {
            if (exprPointcutAnnotation == null)
                return;

            this.pointcutExpression = 
                    exprPointcutAnnotation.getValue("pointcutExpression").resolve(String.class).trim();
        }

        protected void doParseExprPointcutSpec(ConfigView configView, String configKeyPrefix,
                ExprPointcutSpec existingAdvisorSpec) {
            // overwrite configuration properties if exists
            String defaultPointcutExpression = existingAdvisorSpec == null 
                    ? "" : existingAdvisorSpec.getPointcutExpression();
            this.pointcutExpression = configView.getAsString(
                    configKeyPrefix + "pointcutExpression", defaultPointcutExpression );
        }
    }


    @NoScanning
    abstract class AbstractAspectJPointcutSpec extends AbstractExprPointcutSpec implements AspectJPointcutAdvisorSpec {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AspectJPointcutAdvisorSpec.class);

        private static final TypeDescription JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.class);
        private static final TypeDescription MUTABLE_JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.MutableJoinpoint.class);

        private static final List<TypeDescription> ACCESSIBLE_OBJECTS = Arrays.asList( 
                TypeDescription.ForLoadedType.of(AccessibleObject.class),
                TypeDescription.ForLoadedType.of(Executable.class),
                TypeDescription.ForLoadedType.of(Constructor.class),
                TypeDescription.ForLoadedType.of(Method.class)
        );


        private final TypeDescription aspectJType;
        private final MethodDescription aspectJMethod;

        private AdviceCategory adviceCategory;

        private Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap;

        private String argNamesStr;
        private List<String> pointcutParameterNames;
        private Map<String, NamedPointcutParameter> namedPointcutParameters;

        private String returningParameter;
        private Generic adviceReturningParameterType;

        private String throwingParameter;
        private Generic adviceThrowingParameterType;

        private boolean isVoidReturningOfTargetMethod = false;


        AbstractAspectJPointcutSpec(TypeDescription aspectJType, MethodDescription aspectJMethod,
                ElementMatcher<MatchingContext> condition) throws IgnoredSpecException {
            super(condition);

            this.aspectJType = aspectJType;
            this.aspectJMethod = aspectJMethod;
        }

        protected void doParseAspectJAdviceSpec(AnnotationDescription aspectJAnnotation) {
            String pointcutExpression = aspectJAnnotation.getValue("value").resolve(String.class).trim();
            if (StringUtils.hasText(pointcutExpression))
                this.setPointcutExpression(pointcutExpression);

            try {
                // override 'value' attribute
                pointcutExpression = aspectJAnnotation.getValue("pointcut").resolve(String.class).trim();

                if (StringUtils.hasText(pointcutExpression))
                    this.setPointcutExpression(pointcutExpression);
            } catch (Exception ignored) {}


            this.argNamesStr = aspectJAnnotation.getValue("argNames").resolve(String.class).trim();


            if (adviceCategory.isAfterReturning()) 
                returningParameter = aspectJAnnotation.getValue("returning").resolve(String.class).trim();

            if (adviceCategory.isAfterThrowing()) 
                throwingParameter = aspectJAnnotation.getValue("throwing").resolve(String.class).trim();
        }

        protected void doParseAspectJAdviceSpec(ConfigView configView, String configKeyPrefix) {
            // overwrite configuration properties if exists
            this.argNamesStr = configView.getAsString(configKeyPrefix + "argNames", "");

            String returningKey = configKeyPrefix + "returning";
            String returningParameter = configView.getAsString(returningKey, "");
            if (adviceCategory.isAfterReturning()) 
                this.returningParameter = returningParameter;
            else if (StringUtils.hasText(returningParameter) && LOGGER.isInfoEnabled())
                LOGGER.info("Ignored meaningless 'returning' property of non AspectJ @AfterReturning advice method. \n"
                        + "  {}: {} \n"
                        + "  AdviceMethod: {} \n"
                        + "    {}: {} \n",
                        getSpecType(), getAdvisorName(), 
                        MethodUtils.getMethodSignature(aspectJMethod),
                        returningKey, returningParameter
                );

            String throwingKey = configKeyPrefix + "throwing";
            String throwingParameter = configView.getAsString(throwingKey, "");
            if (adviceCategory.isAfterThrowing()) 
                this.throwingParameter = throwingParameter;
            else if (StringUtils.hasText(throwingParameter) && LOGGER.isInfoEnabled())
                LOGGER.info("Ignored meaningless 'throwing' property of non AspectJ @AfterThrowing advice method. \n"
                        + "  {}: {} \n"
                        + "  AdviceMethod: {} \n"
                        + "    {}: {} \n",
                        getSpecType(), getAdvisorName(), 
                        MethodUtils.getMethodSignature(aspectJMethod),
                        throwingKey, returningParameter
                );
        }

        protected String doGenerateAdviceClassName(String adviceCategory) {
            // define advice class name
            return aspectJType.getTypeName() + "_" + aspectJMethod.getName() + "_$" + adviceCategory + "$";
        }

        protected void doInitializeSpec() {
            // 1.resolve parameter name/type pair
            ParameterList<ParameterDescription.InDefinedShape> parameters = aspectJMethod.asDefined().getParameters();
            List<String> parameterNames = resolveParameterNames(getAdvisorName(), aspectJMethod, argNamesStr, parameters);

            this.parameterDescriptionMap = createParameterDescriptionMap(parameters, parameterNames);


            // 2.bind AspectJ method parameters
            List<String> pointcutParameterNames = new ArrayList<>(parameterNames);
            Map<String, NamedPointcutParameter> namedPointcutParameters = new LinkedHashMap<>(parameterNames.size());

            resolveJoinpointParamBinding(namedPointcutParameters, pointcutParameterNames);

            Generic returningParameterType = null;
            if (adviceCategory.isAfterReturning()) {
                returningParameterType = resolveAdviceReturningParamBinding(namedPointcutParameters);

                if (returningParameterType == null)
                    throw new IgnoredSpecException();
                else
                    pointcutParameterNames.remove(returningParameter);
            }

            Generic throwingParameterType = null;
            if (adviceCategory.isAfterThrowing()) {
                throwingParameterType = resolveAdviceThrowingParamBinding(namedPointcutParameters);

                if (throwingParameterType == null)
                    throw new IgnoredSpecException();
                else
                    pointcutParameterNames.remove(throwingParameter);
            }

            this.pointcutParameterNames = pointcutParameterNames;
            this.namedPointcutParameters = namedPointcutParameters;

            this.adviceReturningParameterType = returningParameterType;
            this.adviceThrowingParameterType = throwingParameterType;
        }

        private List<String> resolveParameterNames(String advisorName, MethodDescription aspectJMethod, 
                String argNamesStr, ParameterList<ParameterDescription.InDefinedShape> parameters) {
            if (parameters.size() == 0)
                return Collections.emptyList();

            ParameterDescription.InDefinedShape index0Param = parameters.get(0);
            List<String> parameterNames = new ArrayList<>(parameters.size());

            // 1.fetch 'argNames' value in annotation
            StringTokenizer st = new StringTokenizer(argNamesStr, ",");
            List<String> argNames = new ArrayList<>(st.countTokens());
            while (st.hasMoreTokens())
                argNames.add(st.nextToken().trim());

            if (argNames.size() > 0) {
                if (argNames.size() != parameters.size() && argNames.size() != parameters.size() - 1) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored AspectJ advice method with parameters is inconsistent with 'argNames' annotation attribute. \n"
                                + "  {}: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    ArgNames: {} \n", 
                                getSpecType(), getAdvisorName(), 
                                MethodUtils.getMethodSignature(aspectJMethod),
                                argNamesStr
                        );

                    throw new IgnoredSpecException();
                }

                // first parameter should be joinpoint
                if (argNames.size() == parameters.size() - 1) {
                    parameterNames.add(index0Param.getName());
                }
                parameterNames.addAll(argNames);

                return parameterNames;
            }

            // 2.parse parameter names in MethodParameters section
            // validate parameter name for index0 parameter
            if (index0Param.getName().equals(index0Param.getActualName()) == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AspectJ advice method without parameter reflection support and 'argNames' annotation attribute. \n"
                            + "  {}: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    BinaryMethodName: {} \n"
                            + "    ActualMethodName: {} \n",
                            getSpecType(), getAdvisorName(), 
                            MethodUtils.getMethodSignature(aspectJMethod), 
                            index0Param.getName(), 
                            index0Param.getActualName()
                    );

                throw new IgnoredSpecException();
            }

            return parameters.stream()
                    .map( p -> p.getName() )
                    .collect( Collectors.toList() );
        }

        private Map<String, ParameterDescription.InDefinedShape> createParameterDescriptionMap(
                ParameterList<ParameterDescription.InDefinedShape> parameters, List<String> parameterNames) {
            if (parameterNames.size() == 0)
                return Collections.emptyMap();

            Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap = new LinkedHashMap<>(parameters.size());

            // access by parameter index other than parameter name which might NOT contain in bytecode file
            for (int index = 0; index < parameters.size(); index++) {
                ParameterDescription.InDefinedShape paramType = parameters.get(index);
                parameterDescriptionMap.put(parameterNames.get(index), paramType);
            }

            return parameterDescriptionMap;
        }

        private void resolveJoinpointParamBinding(Map<String, NamedPointcutParameter> pointcutParameters, 
                List<String> pointcutParameterNames) {
            if (parameterDescriptionMap.size() == 0) 
                return;

            // bind first parameter
            ParameterDescription.InDefinedShape parameterDescription = aspectJMethod.asDefined().getParameters().get(0);
            String parameterName = parameterDescription.getName();
            TypeDescription parameterType = parameterDescription.getType().asErasure();

            if (JOINPOINT_TYPE.equals(parameterType)) {
                pointcutParameters.put(parameterName, 
                        new PointcutParameter.Default(parameterName, parameterDescription.getType(), PointcutParameter.ParamCategory.JOINPOINT_PARAM));
                pointcutParameterNames.remove(parameterName);
            } else if (MUTABLE_JOINPOINT_TYPE.equals(parameterType)) {
                pointcutParameters.put(parameterName, 
                        new PointcutParameter.Default(parameterName, parameterDescription.getType(), PointcutParameter.ParamCategory.MUTABLE_JOINPOINT_PARAM));
                pointcutParameterNames.remove(parameterName);
            } else if (ACCESSIBLE_OBJECTS.contains(parameterType)) {
                pointcutParameters.put(parameterName, 
                        new PointcutParameter.Default(parameterName, parameterDescription.getType(), PointcutParameter.ParamCategory.STATIC_PART_PARAM));
                pointcutParameterNames.remove(parameterName);
            }
        }

        private Generic resolveAdviceReturningParamBinding(Map<String, NamedPointcutParameter> pointcutParameters) {
            // resolve returning parameters
            if (StringUtils.hasText(returningParameter) == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AspectJ @AfterReturning advice method without 'returning' annotation attribute. \n"
                            + "  {}: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    'returning': {} \n",
                            getSpecType(), getAdvisorName(), 
                            MethodUtils.getMethodSignature(aspectJMethod),
                            returningParameter
                    );

                throw new IgnoredSpecException();
            }

            if (parameterDescriptionMap.containsKey(returningParameter) == false) { 
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AspectJ @AfterReturning advice method with 'returning' annotation attribute referring to nonexistent parameter. \n"
                            + "  {}: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    'returning': {} \n",
                            getSpecType(), getAdvisorName(), 
                            MethodUtils.getMethodSignature(aspectJMethod),
                            returningParameter
                    );

                throw new IgnoredSpecException();
            }

            Generic parameterType = parameterDescriptionMap.get(returningParameter).getType();
            pointcutParameters.put(returningParameter, 
                    new PointcutParameter.Default(returningParameter, parameterType, PointcutParameter.ParamCategory.RETURNING_ANNOTATION));

            return parameterType;
        }

        private Generic resolveAdviceThrowingParamBinding(Map<String, NamedPointcutParameter> pointcutParameters) {
            // resolve throwing parameters
            if (StringUtils.hasText(throwingParameter) == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AspectJ @AfterThrowing advice method without 'throwing' annotation attribute. \n"
                            + "  {}: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    'throwing': {} \n",
                            getSpecType(), getAdvisorName(), 
                            MethodUtils.getMethodSignature(aspectJMethod),
                            throwingParameter
                    );

                throw new IgnoredSpecException();
            }

            if (parameterDescriptionMap.containsKey(throwingParameter) == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AspectJ @AfterThrowing advice method with 'throwing' annotation attribute referring to nonexistent parameter. \n"
                            + "  {}: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    'throwing': {} \n",
                            getSpecType(), getAdvisorName(), 
                            MethodUtils.getMethodSignature(aspectJMethod),
                            throwingParameter
                    );

                throw new IgnoredSpecException();
            }

            Generic parameterType = parameterDescriptionMap.get(throwingParameter).getType();
            pointcutParameters.put(throwingParameter, 
                    new PointcutParameter.Default(throwingParameter, parameterType, PointcutParameter.ParamCategory.THROWING_ANNOTATION));

            return parameterType;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AspectJPointcutAdvisorSpec.class.getSimpleName();
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

        protected void setAdviceCategory(AdviceCategory adviceCategory) {
            this.adviceCategory = adviceCategory;
        }

        protected Map<String, ParameterDescription.InDefinedShape> getParameterDescriptionMap() {
            return parameterDescriptionMap;
        }

        protected List<String> getPointcutParameterNames() {
            return pointcutParameterNames;
        }

        public Map<String, Generic> getPointcutParameterTypes() {
            Map<String, Generic> parameterTypes = new LinkedHashMap<>(pointcutParameterNames.size());
            for (String parameterName : pointcutParameterNames)
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
            if (pointcutParameters == null || pointcutParameters.size() != pointcutParameterNames.size()) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored advice method with advice parameters is different to target method's resolved parameters. \n" 
                            + "  {}: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    AdviceParameters: {} \n"
                            + "  TargetMethod: {} \n"
                            + "    ResolvedParameters: {} \n",
                            getSpecType(), getAdvisorName(), 
                            MethodUtils.getMethodSignature(aspectJMethod),
                            pointcutParameterNames,
                            MethodUtils.getMethodSignature(methodDescription), 
                            pointcutParameters == null ? null : pointcutParameters.stream()
                                    .map( p -> p.getParamName() )
                                    .collect( Collectors.toList() )
                    );

                return false;
            }

            for (NamedPointcutParameter pointcutParameterBinding : pointcutParameters) {
                String name = pointcutParameterBinding.getParamName();
                if (pointcutParameterNames.contains(name) == false) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored advice method with advice parameters do not contain target method's resolved parameter '{}'. \n" 
                                + "  {}: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    AdviceParameters: {} \n"
                                + "  TargetMethod: {} \n"
                                + "    ResolvedParameters: {} \n",
                                name, 
                                getSpecType(), getAdvisorName(), 
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
                if (ClassUtils.isVisibleTo(paramType, methodDescription.getDeclaringType().asErasure()) == false) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored advice method referring to non public and non protected in the same package parameter type under Joinpoint ClassLoader. \n"
                                + "  AdvisorSpec: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    parameter '{}': {} {} \n",
                                getSpecType(), getAdvisorName(), 
                                methodDescription.toGenericString(),
                                name, paramType.getVisibility(), paramType
                        );

                    return false;
                }

                this.namedPointcutParameters.put(name, pointcutParameterBinding);
            }

            Generic returnType = methodDescription.getReturnType();
            isVoidReturningOfTargetMethod = returnType.represents(void.class);

            return true;
        }
    }
}
