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

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AdviceKind.AspectJAdviceKind;
import io.gemini.aop.AdviceKind.ByteBuddyAdviceKind;
import io.gemini.aop.AdviceKind.PojoAdviceKind;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AdviceSpec.AspectJAdviceSpec;
import io.gemini.aop.factory.support.AdviceSpec.ByteBuddyAdviceSpec;
import io.gemini.aop.factory.support.AdviceSpec.PojoAdviceSpec;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.aspectj.weaver.PointcutParameter;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.core.OrderComparator;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.Pair;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.dynamic.DynamicType;

public interface AdviceSpecParser {

    Logger LOGGER = LoggerFactory.getLogger(AdviceSpecParser.class);


    Collection<? extends AdviceSpec> parse(FactoryContext factoryContext, TypeDescription declaringType);

    AdviceSpec parse(FactoryContext factoryContext, String configKeyPrefix, AdviceSpec existingAdviceSpec);


    @NoScanning
    class Compound implements AdviceSpecParser {

        private final List<? extends AdviceSpecParser> adviceSpecParsers;


        public Compound(FactoryContext factoryContext) {
            List<? extends AdviceSpecParser> adviceSpecParsers = factoryContext.getObjectFactory()
                    .createObjectsImplementing(
                            AdviceSpecParser.class, true, "factoryContext", factoryContext );
            this.adviceSpecParsers = adviceSpecParsers == null 
                    ? Collections.emptyList() : adviceSpecParsers;

            OrderComparator.sort(this.adviceSpecParsers);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<? extends AdviceSpec> parse(FactoryContext factoryContext, TypeDescription declaringType) {
            for (AdviceSpecParser adviceSpecParser : adviceSpecParsers) {
                try {
                    Collection<? extends AdviceSpec> adviceSpecs = adviceSpecParser.parse(factoryContext, declaringType);
                    if (CollectionUtils.isEmpty(adviceSpecs) == false)
                        return adviceSpecs;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not parse AdviceSpec via '{}'. \n"
                                + "  DeclaringType: {} \n"
                                + "  Error reason: {} \n", 
                                adviceSpecParser, 
                                declaringType.getTypeName(),
                                t.getMessage(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }
            }

            return Collections.emptyList();
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public AdviceSpec parse(FactoryContext factoryContext, String configKeyPrefix, AdviceSpec existingAdviceSpec) {
            for (AdviceSpecParser adviceSpecParser : adviceSpecParsers) {
                try {
                    AdviceSpec adviceSpec = adviceSpecParser.parse(factoryContext, configKeyPrefix, existingAdviceSpec);
                    if (adviceSpec != null)
                        return adviceSpec;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not parse AdviceSpec via '{}'. \n"
                                + "  ConfigKeyPrefix: {} \n"
                                + "  Error reason: {} \n", 
                                adviceSpecParser, 
                                configKeyPrefix,
                                t.getMessage(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }
            }

            return null;
        }
    }


    abstract class AbstractBase<A extends AdviceSpec> implements AdviceSpecParser {

        private static final String ADVICE_CLASS_NAME_CONFIG_KEY_SUFFIX = "adviceClassName";


        protected abstract Class<? extends A> doGetAdviceSpecClass();


        /** 
         * {@inheritDoc}
         */
        @Override
        public Collection<? extends A> parse(FactoryContext factoryContext, TypeDescription declaringType) {
            try {
                if (declaringType == null)
                    return Collections.emptyList();

                return doParse(factoryContext, declaringType);
            } catch (IllegalSpecException e) {
                return null;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not parse '{}'. \n"
                            + "  DeclaringType: {} \n"
                            + "  Error reason: {} \n", 
                            doGetAdviceSpecClass().getSimpleName(), 
                            declaringType.getTypeName(), 
                            e.getMessage(), 
                            e
                    );

                return null;
            }
        }

        protected abstract Collection<? extends A> doParse(FactoryContext factoryContext, TypeDescription declaringType);


        protected Pair<Generic, Generic> resolveJoinpointParamTypeArguments(MethodDescription adviceMethod) {
            ParameterList<?> parameters = adviceMethod.getParameters();
            if (parameters.size() == 0)
                return null;

            Generic joinpointType = parameters.get(0).getType();
            if (TypeDefinition.Sort.PARAMETERIZED != joinpointType.getSort()) {
                return null;
            }

            TypeList.Generic typeVariables = joinpointType.getTypeArguments();

            Generic returningType = typeVariables.get(0);
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
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public AdviceSpec parse(FactoryContext factoryContext, String configKeyPrefix, AdviceSpec existingAdviceSpec) {
            try {
                Class<? extends A> adviceSpecClass = doGetAdviceSpecClass();
                if (adviceSpecClass == null 
                        || (existingAdviceSpec != null && adviceSpecClass.isAssignableFrom(existingAdviceSpec.getClass()) == false))
                    return null;

                return (A) doParse(factoryContext, configKeyPrefix, (A) existingAdviceSpec);
            } catch (IllegalSpecException e) {
                return null;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not parse '{}'. \n"
                            + "  ConfigKeyPrefix: {} \n"
                            + "  Error reason: {} \n", 
                            doGetAdviceSpecClass().getSimpleName(), 
                            configKeyPrefix, 
                            e.getMessage(), 
                            e
                    );

                return null;
            }
        }

        protected abstract A doParse(FactoryContext factoryContext, String configKeyPrefix, A existingAdviceSpec);


        protected TypeDescription getAdviceType(FactoryContext factoryContext, String configKeyPrefix) {
            String adviceClassName = factoryContext.getConfigView().getAsString(
                    configKeyPrefix + ADVICE_CLASS_NAME_CONFIG_KEY_SUFFIX, null);
            return adviceClassName == null
                    ? null : factoryContext.getTypePool().describe(adviceClassName).resolve();
        }
    }


    class PojoAdviceParser extends AbstractBase<PojoAdviceSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends PojoAdviceSpec> doGetAdviceSpecClass() {
            return PojoAdviceSpec.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<? extends PojoAdviceSpec> doParse(FactoryContext factoryContext, TypeDescription declaringType) {
            // validate declaringType
            boolean beforeAdvice = declaringType.isAssignableTo(io.gemini.api.aop.Advice.Before.class);
            boolean afterAdvice = declaringType.isAssignableTo(io.gemini.api.aop.Advice.After.class);
            boolean aroundAdvice = declaringType.isAssignableTo(io.gemini.api.aop.Advice.Around.class);
            if (beforeAdvice == false && afterAdvice == false && aroundAdvice == false) {
                return Collections.emptyList();
            }

            // resolve advice method and parameterized joinpoint parameter
            MethodDescription adviceMethod = resolveAdviceMethod(declaringType);
            Pair<Generic, Generic> joinpointParamTypeArguments = resolveJoinpointParamTypeArguments(adviceMethod);

            if (joinpointParamTypeArguments == null)
                return Collections.emptyList();

            Generic parameterizedReturningType = joinpointParamTypeArguments == null ? null : joinpointParamTypeArguments.getLeft();
            Generic parameterizedThrowingType = joinpointParamTypeArguments == null ? null : joinpointParamTypeArguments.getRight();

            PojoAdviceSpec.Default pojoAdviceSpec = new PojoAdviceSpec.Default(
                    PojoAdviceKind.parse(beforeAdvice, afterAdvice, aroundAdvice),
                    declaringType, declaringType.getTypeName(), adviceMethod, 
                    parameterizedReturningType, parameterizedThrowingType
            );
            return Collections.singletonList(pojoAdviceSpec);
        }

        private MethodDescription resolveAdviceMethod(TypeDescription declaringType) {
            while (declaringType != null) {
                MethodDescription adviceMethod = resolveFirstAdviceMethod(declaringType);
                if (adviceMethod != null)
                    return adviceMethod;

                Generic superType = declaringType.getSuperClass();
                if (superType == null)
                    return null;

                declaringType = superType.asErasure();
            }

            return null;
        }

        private MethodDescription resolveFirstAdviceMethod(TypeDescription declaringType) {
            MethodList<MethodDescription.InDefinedShape> beforeMethodFilter = declaringType.getDeclaredMethods()
                    .filter(named("before")
                            .and(takesArgument(0, named(MutableJoinpoint.class.getName())))
                    );
            if (beforeMethodFilter.isEmpty() == false) {
                MethodDescription beforeMethod = beforeMethodFilter.getOnly();

                Generic parameterType = beforeMethod.getParameters().get(0).getType();
                if (TypeDefinition.Sort.PARAMETERIZED == parameterType.getSort() && parameterType.getTypeArguments().size() > 0)
                    return beforeMethod;
            }

            MethodList<MethodDescription.InDefinedShape> afterMethodFilter = declaringType.getDeclaredMethods()
                    .filter(named("after")
                            .and(takesArgument(0, named(MutableJoinpoint.class.getName())))
                    );
            if (afterMethodFilter.isEmpty() == false) {
                MethodDescription afterMethod = afterMethodFilter.getOnly();

                Generic parameterType = afterMethod.getParameters().get(0).getType();
                if (TypeDefinition.Sort.PARAMETERIZED == parameterType.getSort() && parameterType.getTypeArguments().size() > 0)
                    return afterMethod;
            }

            return null;
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        protected PojoAdviceSpec doParse(FactoryContext factoryContext, String configKeyPrefix, PojoAdviceSpec existingAdviceSpec) {
            Collection<? extends PojoAdviceSpec> adviceSpec = doParse(
                    factoryContext, 
                    getAdviceType(factoryContext, configKeyPrefix));
            return CollectionUtils.isEmpty(adviceSpec) ? null : adviceSpec.iterator().next();
        }
    }


    class AspectJAdviceParser extends AbstractBase<AspectJAdviceSpec> {

        private static final List<Class<? extends Annotation>> ADVICE_ANNOTATIONS = Arrays.asList(
                Before.class, After.class, 
                AfterReturning.class, AfterThrowing.class, 
                Around.class);


        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends AspectJAdviceSpec> doGetAdviceSpecClass() {
            return AspectJAdviceSpec.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Collection<? extends AspectJAdviceSpec> doParse(FactoryContext factoryContext, TypeDescription declaringType) {
            // validate declaringType
            if (declaringType.getDeclaredAnnotations().isAnnotationPresent(Aspect.class) == false)
                return Collections.emptyList();

            // resolve advice methods
            MethodList<MethodDescription.InDefinedShape> declaredMethods = declaringType.getDeclaredMethods();
            Map<String, AspectJAdviceSpec> adviceSpecMap = new LinkedHashMap<>(declaredMethods.size());
            for (MethodDescription adviceMethod : declaredMethods) {
                AspectJAdviceSpec adviceSpec = parseAspectJAdviceSpec(factoryContext, declaringType, adviceMethod);
                if (adviceSpec == null)
                    continue;

                if (adviceSpecMap.containsKey(adviceSpec.getAdviceClassName())) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Overwrote existing same name AspectJ advice method. \n"
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n",
                                declaringType.getTypeName(), 
                                MethodUtils.getMethodSignature(adviceMethod) 
                        );
                }

                adviceSpecMap.put(adviceSpec.getAdviceClassName(), adviceSpec);
            }

            if (adviceSpecMap.size() == 0) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdviceSpec without advice methods. \n"
                            + "  DeclaringType: {} \n", 
                            declaringType.getTypeName()
                    );

                return Collections.emptyList();
            }

            return adviceSpecMap.values();
        }

        private AspectJAdviceSpec parseAspectJAdviceSpec(FactoryContext factoryContext, 
                TypeDescription declaringType, MethodDescription adviceMethod) {
            // validate method modifier
            if (adviceMethod.isAbstract()) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored abstract AspectJ advice method. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n",
                            declaringType.getTypeName(), 
                            MethodUtils.getMethodSignature(adviceMethod) 
                    );
                return null;
            }

            if (adviceMethod.isPrivate()) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored private AspectJ advice method. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n",
                            declaringType.getTypeName(), 
                            MethodUtils.getMethodSignature(adviceMethod) 
                    );
                return null;
            }


            // resolve advice annotation
            AnnotationList adviceMethodAnnotations = adviceMethod.getDeclaredAnnotations();
            AnnotationDescription adviceAnnotation = null;
            for (Class<? extends Annotation> annotationType : ADVICE_ANNOTATIONS) {
                AnnotationDescription annotation = adviceMethodAnnotations.ofType(annotationType);
                if (annotation == null) continue;

                if (adviceAnnotation == null)
                    adviceAnnotation = annotation;
                else {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Ignored AspectJ advice method with more than one advice annotations. \n"
                                + "  DeclaringType: {} \n"
                                + "  AdviceMethod: {} \n",
                                declaringType.getTypeName(), 
                                MethodUtils.getMethodSignature(adviceMethod)
                        );
                    return null;
                }
            }

            if (adviceAnnotation == null)
                return null;

            try {
                // resolve parameterized joinpoint parameter
                Pair<Generic, Generic> joinpointParamTypeArguments = resolveJoinpointParamTypeArguments(adviceMethod);

                Generic parameterizedReturningType = null;
                Generic parameterizedThrowingType = null;
                if (joinpointParamTypeArguments != null) {
                    parameterizedReturningType =  joinpointParamTypeArguments.getLeft();
                    parameterizedThrowingType = joinpointParamTypeArguments.getRight();
                }

                return new AspectJAdviceParser.Default(declaringType, 
                        adviceMethod, parameterizedReturningType, parameterizedThrowingType, 
                        adviceAnnotation
                );
            } catch (IllegalSpecException e) {
                return null;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not parse '{}'. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "  Error reason: {} \n", 
                            doGetAdviceSpecClass().getSimpleName(),
                            declaringType.getTypeName(), 
                            MethodUtils.getMethodSignature(adviceMethod),
                            e.getMessage(),
                            e
                    );

                return null;
            }
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        protected AspectJAdviceSpec doParse(FactoryContext factoryContext, String configKeyPrefix, AspectJAdviceSpec existingAdviceSpec) {
            String adviceMethodExpression = factoryContext.getConfigView().getAsString(configKeyPrefix + "adviceMethodExpression", "").trim();
            if ("".equals(adviceMethodExpression))
                return null;
            MethodDescription adviceMethod = findMethod(factoryContext, configKeyPrefix, adviceMethodExpression);
            if (adviceMethod == null)
                return null;

            TypeDescription declaringType = adviceMethod.getDeclaringType().asErasure();

            // resolve parameterized joinpoint
            Pair<Generic, Generic> joinpointParamTypeArguments = resolveJoinpointParamTypeArguments(adviceMethod);

            Generic parameterizedReturningType = null;
            Generic parameterizedThrowingType = null;
            if (joinpointParamTypeArguments != null) {
                parameterizedReturningType =  joinpointParamTypeArguments.getLeft();
                parameterizedThrowingType = joinpointParamTypeArguments.getRight();
            }

            return new AspectJAdviceParser.Default(declaringType, 
                    adviceMethod, parameterizedReturningType, parameterizedThrowingType, 
                    factoryContext.getConfigView(), configKeyPrefix
            );
        }

        private MethodDescription findMethod(FactoryContext factoryContext, String configKeyPrefix, String adviceMethodExpression) {
            try {
                return ExprParser.INSTANCE.findMethod(
                        factoryContext.getTypeWorld(), adviceMethodExpression);
            } catch (ExprParser.ExprParseException e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not find method with unparsable MethodExpression. \n"
                            + "  ConfigKeyPrefix: {} \n"
                            + "  MethodExpression: {} \n"
                            + "  Syntax Error: {} \n", 
                            configKeyPrefix, 
                            adviceMethodExpression, 
                            e.getMessage()
                    );
            } catch (ExprParser.ExprLintException e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not find method with lint MethodExpression. \n"
                            + "  ConfigKeyPrefix: {} \n"
                            + "  MethodExpression: {} \n"
                            + "  Lint message: {} \n", 
                            configKeyPrefix, 
                            adviceMethodExpression, 
                            e.getMessage()
                    );
            } catch (ExprParser.ExprUnknownException e) {
                if (LOGGER.isWarnEnabled()) {
                    Throwable cause = e.getCause();
                    LOGGER.warn("Could not find method with illegal MethodExpression. \n"
                            + "  ConfigKeyPrefix: {} \n"
                            + "  MethodExpression: {} \n"
                            + "  Error reason: {} \n", 
                            configKeyPrefix, 
                            adviceMethodExpression, 
                            cause.getMessage(), 
                            cause
                    );
                }
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not find method with illegal MethodExpression. \n"
                            + "  ConfigKeyPrefix: {} \n"
                            + "  MethodExpression: {} \n"
                            + "  Error reason: {} \n", 
                            configKeyPrefix, 
                            adviceMethodExpression, 
                            e.getMessage(), 
                            e
                    );
            }

            return null;
        }


        static class Default extends AdviceSpec.AbstractBase implements AspectJAdviceSpec {

            private static final TypeDescription JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.class);
            private static final TypeDescription MUTABLE_JOINPOINT_TYPE = TypeDescription.ForLoadedType.of(Joinpoint.MutableJoinpoint.class);

            private static final List<TypeDescription> ACCESSIBLE_OBJECTS = Arrays.asList( 
                    TypeDescription.ForLoadedType.of(AccessibleObject.class),
                    TypeDescription.ForLoadedType.of(Executable.class),
                    TypeDescription.ForLoadedType.of(Constructor.class),
                    TypeDescription.ForLoadedType.of(Method.class)
            );


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
                    Generic parameterizedReturningType, Generic parameterizedThrowingType, 
                    AnnotationDescription adviceAnnotation) throws IllegalSpecException {
                super(null,
                        declaringType, null, adviceMethod, 
                        parameterizedReturningType, parameterizedThrowingType);

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
                    Generic parameterizedReturningType, Generic parameterizedThrowingType,
                    ConfigView configView, String configKeyPrefix) throws IllegalSpecException {
                super(null,
                        declaringType, null, adviceMethod, 
                        parameterizedReturningType, parameterizedThrowingType);

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
//                getUnloadedAdviceClass();
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


    class ByteBuddyAdviceParser extends AbstractBase<ByteBuddyAdviceSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends ByteBuddyAdviceSpec> doGetAdviceSpecClass() {
            return ByteBuddyAdviceSpec.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<? extends ByteBuddyAdviceSpec> doParse(FactoryContext factoryContext, TypeDescription declaringType) {
            // validate declaringType
            MethodDescription enterMethod = null;
            MethodDescription exitMethod = null;
            for (MethodDescription method : declaringType.getDeclaredMethods()) {
                if (method.getDeclaredAnnotations().isAnnotationPresent(OnMethodEnter.class)) {
                    if (enterMethod == null)
                        enterMethod = method;
                    else {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Ignored AdviceSpec with more than one @OnMethodEnter annotated advice methods. \n"
                                    + "  DeclaringType: {} \n"
                                    + "  AdviceMethod: {} \n", 
                                    declaringType.getTypeName(),
                                    MethodUtils.getMethodSignature(method)
                            );

                        return Collections.emptyList();
                    }
                }

                if (method.getDeclaredAnnotations().isAnnotationPresent(OnMethodExit.class)) {
                    if (exitMethod == null)
                        exitMethod = method;
                    else {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Ignored AdviceSpec with more than one @OnMethodExit annotated advice methods. \n"
                                    + "  DeclaringType: {} \n"
                                    + "  AdviceMethod: {} \n", 
                                    declaringType.getTypeName(),
                                    MethodUtils.getMethodSignature(method)
                            );

                        return Collections.emptyList();
                    }
                }
            }

            if (enterMethod == null && exitMethod == null)
                return Collections.emptyList();

            // TODO: resolve returning and throwing
            // inline = false, not private...

            ByteBuddyAdviceSpec.Default byteBuddyAdviceSpec = new ByteBuddyAdviceSpec.Default(
                    ByteBuddyAdviceKind.parse(enterMethod != null, exitMethod != null),
                    declaringType
            );
            return Collections.singletonList(byteBuddyAdviceSpec);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected ByteBuddyAdviceSpec doParse(FactoryContext factoryContext, String configKeyPrefix, ByteBuddyAdviceSpec existingAdviceSpec) {
            return null;
        }
    }
}
