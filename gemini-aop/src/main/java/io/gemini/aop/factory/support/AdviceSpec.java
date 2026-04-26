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

import io.gemini.aop.AdviceKind;
import io.gemini.aop.AdviceKind.AspectJAdviceKind;
import io.gemini.aop.AdviceKind.ByteBuddyAdviceKind;
import io.gemini.aop.AdviceKind.PojoAdviceKind;
import io.gemini.aop.matcher.ExprPointcut.PointcutParameterMatcher;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint;
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
import net.bytebuddy.dynamic.DynamicType;

/**
 * Describes the metadata of an advice method or class parsed from an aspect application.
 * <p>
 * Three sub-interfaces cover the three advice styles:
 * <ul>
 *   <li>{@link PojoAdviceSpec} – POJO before/after/around advice</li>
 *   <li>{@link AspectJAdviceSpec} – AspectJ annotation-based advice with pointcut parameter binding</li>
 *   <li>{@link ByteBuddyAdviceSpec} – raw ByteBuddy {@code @Advice} class</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public interface AdviceSpec {

    /**
     * Returns the advice kind (POJO, AspectJ, or ByteBuddy).
     *
     * @return the {@link AdviceKind}
     */
    AdviceKind getAdviceKind();

    /**
     * Returns the type that declares the advice method or class.
     *
     * @return the declaring {@link TypeDescription}
     */
    TypeDescription getDeclaringType();

    /**
     * Returns the fully-qualified class name of the advice class.
     *
     * @return the advice class name
     */
    String getAdviceClassName();


    /**
     * Abstract base providing common advice spec properties: advice kind, declaring type,
     * advice class name, advice method, and parameterized return/throw types.
     */
    abstract class AbstractBase implements AdviceSpec {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AdviceSpecParser.class);


        private AdviceKind adviceKind;

        private TypeDescription declaringType;

        private String adviceClassName;


        public AbstractBase() {}

        public AbstractBase(AdviceKind adviceKind, TypeDescription declaringType, String adviceClassName) {
            this.adviceKind = adviceKind;

            this.declaringType = declaringType;
            this.adviceClassName = adviceClassName;
        }

        /**
         * {@inheritDoc}
         */
        public AdviceKind getAdviceKind() {
            return adviceKind;
        }

        /**
         * Sets the advice kind. Called by subclasses during initialization.
         *
         * @param adviceKind the advice kind to set
         */
        protected void setAdviceKind(AdviceKind adviceKind) {
            this.adviceKind = adviceKind;
        }

        /**
         * {@inheritDoc}
         */
        public TypeDescription getDeclaringType() {
            return declaringType;
        }

        /**
         * {@inheritDoc}
         */
        public String getAdviceClassName() {
            return adviceClassName;
        }

        /**
         * Sets the advice class name. Called by subclasses during initialization.
         *
         * @param adviceClassName the fully-qualified advice class name
         */
        protected void setAdviceClassName(String adviceClassName) {
            this.adviceClassName = adviceClassName;
        }
    }


    /** 
     * POJO-style advice spec for before/after/around advice implementations. 
     */
    interface PojoAdviceSpec extends AdviceSpec {

        /**
         * Returns the POJO advice kind (BEFORE, AFTER, BEFORE_AFTER, or AROUND).
         *
         * @return the {@link PojoAdviceKind}
         */
        PojoAdviceKind getAdviceKind();

        /**
         * Returns the advice method description, or {@code null} for ByteBuddy advice.
         *
         * @return the advice {@link MethodDescription}, or {@code null}
         */
        MethodDescription getAdviceMethod();


        /**
         * Default {@link PojoAdviceSpec} implementation for POJO before/after/around advice.
         */
        class Default extends AbstractBase implements PojoAdviceSpec {

            private final MethodDescription adviceMethod;


            /**
             * Creates a POJO advice spec.
             *
             * @param adviceKind                 the POJO advice kind
             * @param declaringType              the declaring type
             * @param adviceClassName            the fully-qualified advice class name
             * @param adviceMethod               the advice method (before/after/invoke)
             * @param parameterizedReturningType the parameterized return type of the joinpoint, or {@code null}
             * @param parameterizedThrowingType  the parameterized throw type of the joinpoint, or {@code null}
             */
            public Default(PojoAdviceKind adviceKind, TypeDescription declaringType, 
                    String adviceClassName, MethodDescription adviceMethod) {
                super(adviceKind, declaringType, adviceClassName);

                this.adviceMethod = adviceMethod;
            }


            /** 
             * {@inheritDoc}
             */
            public PojoAdviceKind getAdviceKind() {
                return (PojoAdviceKind) super.getAdviceKind();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public MethodDescription getAdviceMethod() {
                return adviceMethod;
            }
        }
    }


    /**
     * AspectJ annotation-based advice spec with pointcut parameter binding metadata.
     * Carries the advice annotation, returning/throwing parameter types, and the
     * unloaded generated advice class.
     */
    interface AspectJAdviceSpec extends AdviceSpec, PointcutParameterMatcher {

        /** 
         * Returns the AspectJ advice kind (@Before, @After, @AfterReturning, @AfterThrowing, @Around). 
         */
        AspectJAdviceKind getAdviceKind();

        /** 
         * Returns the AspectJ advice annotation (e.g., {@code @Before}, {@code @Around}). 
         */
        AnnotationDescription getAdviceAnnotation();

        /**
         * Returns the advice method description, or {@code null} for ByteBuddy advice.
         *
         * @return the advice {@link MethodDescription}, or {@code null}
         */
        MethodDescription getAdviceMethod();

        /**
         * Returns the generic type of the {@code returning} parameter in the advice method,
         * or {@code null} if no returning parameter is declared.
         *
         * @return the advice returning parameter type, or {@code null}
         */
        Generic getAdviceReturningParameterType();

        /**
         * Returns the generic type of the {@code throwing} parameter in the advice method,
         * or {@code null} if no throwing parameter is declared.
         *
         * @return the advice throwing parameter type, or {@code null}
         */
        Generic getAdviceThrowingParameterType();

        /**
         * Returns the map of pointcut parameter names to their generic types,
         * used for parameter binding in the generated advice class.
         *
         * @return map of parameter name to generic type
         */
        Map<String, Generic> getPointcutParameterTypes();

        /**
         * Returns the resolved named pointcut parameters with their binding categories
         * (e.g., args, this, target, returning, throwing).
         *
         * @return map of parameter name to {@link NamedPointcutParameter}
         */
        Map<String, NamedPointcutParameter> getNamedPointcutParameters();

        /**
         * Returns {@code true} if the target method returns {@code void},
         * which affects how the returning parameter is handled in the generated advice.
         *
         * @return {@code true} if the target method is void-returning
         */
        boolean isVoidReturning();

        /**
         * Returns the dynamically generated, unloaded {@link Advice} class that wraps
         * the AspectJ advice method for use with the Gemini AOP framework dispatch mechanism.
         *
         * @return the unloaded advice class
         */
        DynamicType.Unloaded<? extends Advice> getUnloadedAdviceClass();


        /**
         * Default {@link AspectJAdviceSpec} implementation that parses the AspectJ advice annotation,
         * resolves pointcut parameter bindings, and generates the unloaded advice class via
         * {@link AdviceClassGenerator}.
         */
        class Default extends AdviceSpec.AbstractBase implements AspectJAdviceSpec {

            private static final TypeDescription JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.class);
            private static final TypeDescription MUTABLE_JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.MutableJoinpoint.class);

            private static final List<TypeDescription> ACCESSIBLE_OBJECTS = Arrays.asList( 
                    TypeDescription.ForLoadedType.of(AccessibleObject.class),
                    TypeDescription.ForLoadedType.of(Executable.class),
                    TypeDescription.ForLoadedType.of(Constructor.class),
                    TypeDescription.ForLoadedType.of(Method.class)
            );


            private MethodDescription adviceMethod;
            private final AnnotationDescription adviceAnnotation;

            private Map<String, ParameterDescription.InDefinedShape> parameterDescriptionMap;

            private String argNamesStr;
            private List<String> pointcutParameterNames;
            private Map<String, NamedPointcutParameter> namedPointcutParameters;

            private String returningParameter;
            private Generic adviceReturningParameterType;

            private String throwingParameter;
            private Generic adviceThrowingParameterType;

            private boolean voidReturning = false;

            private DynamicType.Unloaded<? extends Advice> unloadedAdviceClass;


            public Default(TypeDescription declaringType, MethodDescription adviceMethod,
                    AnnotationDescription adviceAnnotation) throws IllegalSpecException {
                super(null, declaringType, null);

                this.adviceMethod = adviceMethod;
                this.adviceAnnotation = adviceAnnotation;

                String adviceKindValue = adviceAnnotation.getAnnotationType().getSimpleName().toUpperCase();
                setAdviceKind( AspectJAdviceKind.parse(adviceKindValue) );

                String adviceClassName = doGenerateAdviceClassName(adviceKindValue);
                setAdviceClassName(adviceClassName);

                doParseAspectJAdviceSpec(adviceAnnotation);

                doInitializeSpec();
            }

            protected String doGenerateAdviceClassName(String adviceKind) {
                // define advice class name
                return getDeclaringType().getTypeName() + "_$$" + getAdviceMethod().getName() + "_" + adviceKind + "$$";
            }

            protected void doParseAspectJAdviceSpec(AnnotationDescription adviceAnnotation) {
                this.argNamesStr = adviceAnnotation.getValue("argNames").resolve(String.class).trim();

                if (getAdviceKind().isAfterReturning()) 
                    returningParameter = adviceAnnotation.getValue("returning").resolve(String.class).trim();

                if (getAdviceKind().isAfterThrowing()) 
                    throwingParameter = adviceAnnotation.getValue("throwing").resolve(String.class).trim();
            }


            public Default(TypeDescription declaringType, MethodDescription adviceMethod,
                    ConfigView configView, String configKeyPrefix) throws IllegalSpecException {
                super(null, declaringType, null);

                this.adviceMethod = adviceMethod;
                this.adviceAnnotation = null;

                String adviceKindKey = configKeyPrefix + "adviceCategory";
                String adviceKindValue = configView.getAsString(adviceKindKey, "").toUpperCase();
                try {
                    setAdviceKind( AspectJAdviceKind.parse(adviceKindValue) );
                } catch (Exception e) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored AspectJ advice method with illegal 'adviceKind' configuration property. \n"
                                + "  ConfigKeyPrefix: {} \n"
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    {}: {} \n"
                                + "  Error reason: {} \n", 
                                configKeyPrefix,
                                declaringType.getTypeName(), 
                                MethodUtils.getMethodSignature(adviceMethod),
                                adviceKindKey, adviceKindValue,
                                e.getMessage()
                        );

                    throw new IllegalSpecException();
                }


                String adviceClassName = doGenerateAdviceClassName(adviceKindValue);
                setAdviceClassName(adviceClassName);

                doParseAspectJAdviceSpec(configView, configKeyPrefix);

                doInitializeSpec();
            }

            protected void doParseAspectJAdviceSpec(ConfigView configView, String configKeyPrefix) {
                // overwrite configuration properties if exists
                this.argNamesStr = configView.getAsString(configKeyPrefix + "argNames", "");

                String returningKey = configKeyPrefix + "returning";
                String returningParameter = configView.getAsString(returningKey, "");
                if (getAdviceKind().isAfterReturning()) 
                    this.returningParameter = returningParameter;
                else if (LOGGER.isInfoEnabled() && StringUtils.hasText(returningParameter))
                    LOGGER.info("Ignored meaningless 'returning' property of non AspectJ @AfterReturning advice method. \n"
                            + "  ConfigKeyPrefix: {} \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    {}: {} \n",
                            configKeyPrefix,
                            getDeclaringType().getTypeName(), 
                            MethodUtils.getMethodSignature(getAdviceMethod()),
                            returningKey, returningParameter
                    );

                String throwingKey = configKeyPrefix + "throwing";
                String throwingParameter = configView.getAsString(throwingKey, "");
                if (getAdviceKind().isAfterThrowing()) 
                    this.throwingParameter = throwingParameter;
                else if (LOGGER.isInfoEnabled() && StringUtils.hasText(throwingParameter))
                    LOGGER.info("Ignored meaningless 'throwing' property of non AspectJ @AfterThrowing advice method. \n"
                            + "  ConfigKeyPrefix: {} \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "    {}: {} \n",
                            configKeyPrefix,
                            getDeclaringType().getTypeName(), 
                            MethodUtils.getMethodSignature(getAdviceMethod()),
                            throwingKey, returningParameter
                    );
            }

            protected void doInitializeSpec() {
                // 1.resolve parameter name/type pair
                TypeDescription declaringType = getDeclaringType();
                MethodDescription adviceMethod = getAdviceMethod();

                ParameterList<ParameterDescription.InDefinedShape> parameters = adviceMethod.asDefined().getParameters();
                List<String> parameterNames = resolveParameterNames(declaringType, adviceMethod, argNamesStr, parameters);

                this.parameterDescriptionMap = createParameterDescriptionMap(parameters, parameterNames);


                // 2.bind AspectJ method parameters
                List<String> pointcutParameterNames = new ArrayList<>(parameterNames);
                Map<String, NamedPointcutParameter> namedPointcutParameters = new LinkedHashMap<>(parameterNames.size());

                resolveJoinpointParamBinding(namedPointcutParameters, pointcutParameterNames);

                Generic returningParameterType = null;
                if (getAdviceKind().isAfterReturning()) {
                    returningParameterType = resolveAdviceReturningParamBinding(declaringType, namedPointcutParameters);

                    if (returningParameterType == null)
                        throw new IllegalSpecException();
                    else
                        pointcutParameterNames.remove(returningParameter);
                }

                Generic throwingParameterType = null;
                if (getAdviceKind().isAfterThrowing()) {
                    throwingParameterType = resolveAdviceThrowingParamBinding(declaringType, namedPointcutParameters);

                    if (throwingParameterType == null)
                        throw new IllegalSpecException();
                    else
                        pointcutParameterNames.remove(throwingParameter);
                }

                this.pointcutParameterNames = pointcutParameterNames;
                this.namedPointcutParameters = namedPointcutParameters;

                this.adviceReturningParameterType = returningParameterType;
                this.adviceThrowingParameterType = throwingParameterType;
            }

            private List<String> resolveParameterNames(TypeDescription declaringType, MethodDescription adviceMethod, 
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
                                    + "  DeclaringType: {} \n"
                                    + "  AdviceMethod: {} \n"
                                    + "    ArgNames: {} \n", 
                                    declaringType.getTypeName(),
                                    MethodUtils.getMethodSignature(adviceMethod),
                                    argNamesStr
                            );

                        throw new IllegalSpecException();
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
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    BinaryMethodName: {} \n"
                                + "    ActualMethodName: {} \n",
                                declaringType.getTypeName(),
                                MethodUtils.getMethodSignature(adviceMethod), 
                                index0Param.getName(), 
                                index0Param.getActualName()
                        );

                    throw new IllegalSpecException();
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
                ParameterDescription.InDefinedShape parameterDescription = getAdviceMethod().asDefined().getParameters().get(0);
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

            private Generic resolveAdviceReturningParamBinding(TypeDescription declaringType, 
                    Map<String, NamedPointcutParameter> pointcutParameters) {
                // resolve returning parameters
                if (StringUtils.hasText(returningParameter) == false) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored AspectJ @AfterReturning advice method without 'returning' annotation attribute. \n"
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    'returning': {} \n",
                                declaringType.getTypeName(),
                                MethodUtils.getMethodSignature(getAdviceMethod()),
                                returningParameter
                        );

                    throw new IllegalSpecException();
                }

                if (parameterDescriptionMap.containsKey(returningParameter) == false) { 
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored AspectJ @AfterReturning advice method with 'returning' annotation attribute referring to nonexistent parameter. \n"
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    'returning': {} \n",
                                declaringType.getTypeName(),
                                MethodUtils.getMethodSignature(getAdviceMethod()),
                                returningParameter
                        );

                    throw new IllegalSpecException();
                }

                Generic parameterType = parameterDescriptionMap.get(returningParameter).getType();
                pointcutParameters.put(returningParameter, 
                        new PointcutParameter.Default(returningParameter, parameterType, PointcutParameter.ParamCategory.RETURNING_ANNOTATION));

                return parameterType;
            }

            private Generic resolveAdviceThrowingParamBinding(TypeDescription declaringType, Map<String, NamedPointcutParameter> pointcutParameters) {
                // resolve throwing parameters
                if (StringUtils.hasText(throwingParameter) == false) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored AspectJ @AfterThrowing advice method without 'throwing' annotation attribute. \n"
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    'throwing': {} \n",
                                declaringType.getTypeName(),
                                MethodUtils.getMethodSignature(getAdviceMethod()),
                                throwingParameter
                        );

                    throw new IllegalSpecException();
                }

                if (parameterDescriptionMap.containsKey(throwingParameter) == false) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored AspectJ @AfterThrowing advice method with 'throwing' annotation attribute referring to nonexistent parameter. \n"
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    'throwing': {} \n",
                                declaringType.getTypeName(),
                                MethodUtils.getMethodSignature(getAdviceMethod()),
                                throwingParameter
                        );

                    throw new IllegalSpecException();
                }

                Generic parameterType = parameterDescriptionMap.get(throwingParameter).getType();
                pointcutParameters.put(throwingParameter, 
                        new PointcutParameter.Default(throwingParameter, parameterType, PointcutParameter.ParamCategory.THROWING_ANNOTATION));

                return parameterType;
            }


            /**
             * {@inheritDoc}
             */
            public AspectJAdviceKind getAdviceKind() {
                return (AspectJAdviceKind) super.getAdviceKind();
            }

            /** 
             * {@inheritDoc}
             */
            @Override
            public MethodDescription getAdviceMethod() {
                return adviceMethod;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public AnnotationDescription getAdviceAnnotation() {
                return adviceAnnotation;
            }

            /**
             * {@inheritDoc}
             */
            public Generic getAdviceReturningParameterType() {
                return adviceReturningParameterType;
            }

            /**
             * {@inheritDoc}
             */
            public Generic getAdviceThrowingParameterType() {
                return adviceThrowingParameterType;
            }

            /**
             * {@inheritDoc}
             */
            public Map<String, Generic> getPointcutParameterTypes() {
                Map<String, Generic> parameterTypes = new LinkedHashMap<>(pointcutParameterNames.size());
                for (String parameterName : pointcutParameterNames)
                    parameterTypes.put(parameterName, this.parameterDescriptionMap.get(parameterName).getType());
                return parameterTypes;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public boolean match(MethodDescription targetMethod, List<NamedPointcutParameter> pointcutParameters) {
                // 1.match parameter count and type
                if (pointcutParameters == null || pointcutParameters.size() != pointcutParameterNames.size()) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored advice method with advice parameters is different to target method's resolved parameters. \n" 
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n"
                                + "    AdviceParameters: {} \n"
                                + "  TargetMethod: {} \n"
                                + "    ResolvedParameters: {} \n",
                                getDeclaringType().getTypeName(),
                                MethodUtils.getMethodSignature(getAdviceMethod()),
                                pointcutParameterNames,
                                MethodUtils.getMethodSignature(targetMethod), 
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
                                    + "  DeclaringType: {} \n"
                                    + "  AdviceMethod: {} \n"
                                    + "    AdviceParameters: {} \n"
                                    + "  TargetMethod: {} \n"
                                    + "    ResolvedParameters: {} \n",
                                    name, 
                                    getDeclaringType().getTypeName(),
                                    MethodUtils.getMethodSignature(getAdviceMethod()),
                                    pointcutParameterNames,
                                    MethodUtils.getMethodSignature(targetMethod), 
                                    pointcutParameters == null ? null : pointcutParameters.stream()
                                            .map( p -> p.getParamName() )
                                            .collect( Collectors.toList() )
                            );

                        return false;
                    }

                    TypeDescription paramType = parameterDescriptionMap.get(pointcutParameterBinding.getParamName()).getType().asErasure();
                    if (ClassUtils.isVisibleTo(paramType, targetMethod.getDeclaringType().asErasure()) == false) {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Ignored advice method referring to non public and non protected in the same package parameter type under target ClassLoader. \n"
                                    + "  DeclaringType: {} \n"
                                    + "  AdviceMethod: {} \n"
                                    + "    parameter '{}': {} {} \n",
                                    getDeclaringType().getTypeName(),
                                    MethodUtils.getMethodSignature(getAdviceMethod()),
                                    name, paramType.getVisibility(), paramType
                            );

                        return false;
                    }

                    this.namedPointcutParameters.put(name, pointcutParameterBinding);
                }

                Generic returnType = targetMethod.getReturnType();
                voidReturning = returnType.represents(void.class);

                return true;
            }

            /**
             * {@inheritDoc}
             */
            public Map<String, NamedPointcutParameter> getNamedPointcutParameters() {
                return namedPointcutParameters;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isVoidReturning() {
                return voidReturning;
            }

            /**
             * {@inheritDoc}
             */
            public DynamicType.Unloaded<? extends Advice> getUnloadedAdviceClass() {
                if (unloadedAdviceClass == null)
                this.unloadedAdviceClass = AdviceClassGenerator.INSTANCE.make(this, true);

                return unloadedAdviceClass;
            }
        }
    }


    /** 
     * Raw ByteBuddy {@code @Advice} class spec — no advice method, just the class itself. 
     */
    interface ByteBuddyAdviceSpec extends AdviceSpec {

        /**
         * Default {@link ByteBuddyAdviceSpec} implementation for raw ByteBuddy {@code @Advice} classes.
         */
        class Default extends AbstractBase implements ByteBuddyAdviceSpec {

            /**
             * Creates a ByteBuddy advice spec from the given advice class type.
             *
             * @param adviceKind    the ByteBuddy advice kind (OnMethodEnter, OnMethodExit, or both)
             * @param declaringType the ByteBuddy {@code @Advice} class type description
             */
            public Default(ByteBuddyAdviceKind adviceKind, TypeDescription declaringType) {
                super(adviceKind, declaringType, declaringType.getTypeName());
            }


            /** 
             * Returns the Buddy advice kind (OnMethodEnter, OnMethodExit, or both). 
             */
            public ByteBuddyAdviceKind getAdviceKind() {
                return (ByteBuddyAdviceKind) super.getAdviceKind();
            }
        }
    }
}