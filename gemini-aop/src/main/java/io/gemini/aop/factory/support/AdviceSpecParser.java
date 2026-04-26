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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AdviceKind.ByteBuddyAdviceKind;
import io.gemini.aop.AdviceKind.PojoAdviceKind;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AdviceSpec.AspectJAdviceSpec;
import io.gemini.aop.factory.support.AdviceSpec.ByteBuddyAdviceSpec;
import io.gemini.aop.factory.support.AdviceSpec.PojoAdviceSpec;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.core.OrderComparator;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;

/**
 * Parses {@link AdviceSpec} instances from a {@link TypeDescription} or from configuration properties.
 * <p>
 * Three concrete parsers handle the three advice styles:
 * <ul>
 *   <li>{@link ForPojoAdvice} – detects POJO {@link io.gemini.api.aop.Advice.Before}/{@link io.gemini.api.aop.Advice.After}/{@link io.gemini.api.aop.Advice.Around} implementations</li>
 *   <li>{@link ForAspectJAdvice} – detects {@code @Aspect}-annotated classes with AspectJ advice annotations</li>
 *   <li>{@link ForByteBuddyAdvice} – detects classes with ByteBuddy {@code @Advice.OnMethodEnter}/{@code @Advice.OnMethodExit}</li>
 * </ul>
 * The {@link Compound} implementation delegates to all registered parsers in order.
 * </p>
 *
 * @author   martin.liu
 */
public interface AdviceSpecParser {

    Logger LOGGER = LoggerFactory.getLogger(AdviceSpecParser.class);


    /**
     * Parses all {@link AdviceSpec} instances from the given declaring type.
     * Returns an empty collection if this parser does not recognize the type.
     *
     * @param factoryContext the factory context providing type pool
     * @param declaringType  the type to inspect for advice methods or class-level markers
     * @return a collection of parsed {@link AdviceSpec} instances, never {@code null}
     */
    Collection<? extends AdviceSpec> parse(FactoryContext factoryContext, TypeDescription declaringType);

    /**
     * Parses or updates an {@link AdviceSpec} from configuration properties under the given key prefix.
     * Returns {@code null} if this parser does not handle the spec type or no config is found.
     *
     * @param factoryContext    the factory context providing config view and type pool
     * @param configKeyPrefix   the configuration key prefix (e.g. {@code aop.advisorSpecs.myAdvisor.})
     * @param existingAdviceSpec an existing spec to update, or {@code null} to create a new one
     * @return the parsed or updated {@link AdviceSpec}, or {@code null}
     */
    AdviceSpec parse(FactoryContext factoryContext, String configKeyPrefix, AdviceSpec existingAdviceSpec);


    /**
     * Organizes all registered {@link AdviceSpecParser} instances in order,
     * and returns the first non-null result.
     */
    @NoScanning
    class Compound implements AdviceSpecParser {

        private final List<? extends AdviceSpecParser> adviceSpecParsers;


        public Compound(FactoryContext factoryContext) {
            List<? extends AdviceSpecParser> adviceSpecParsers = factoryContext.getObjectFactory()
                    .createObjectsImplementing(
                            AdviceSpecParser.class, 
                            false, 
                            "factoryContext", factoryContext 
                    );
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
                                adviceSpecParser.getClass().getSimpleName(), 
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

            return existingAdviceSpec;
        }
    }


    /**
     * Abstract base providing common parsing logic: spec class filtering, error handling,
     * and config-key-based parsing delegation.
     */
    abstract class AbstractBase<A extends AdviceSpec> implements AdviceSpecParser {

        private static final String ADVICE_CLASS_NAME_CONFIG_KEY_SUFFIX = "adviceClassName";


        protected abstract boolean supports(AdviceSpec adviceSpec);


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
                    LOGGER.warn("Could not parse AdviceSpec via '{}'. \n"
                            + "  DeclaringType: {} \n"
                            + "  Error reason: {} \n", 
                            this.getClass().getSimpleName(), 
                            declaringType.getTypeName(), 
                            e.getMessage(), 
                            e
                    );

                return null;
            }
        }

        protected abstract Collection<? extends A> doParse(FactoryContext factoryContext, TypeDescription declaringType);


        /** 
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public AdviceSpec parse(FactoryContext factoryContext, String configKeyPrefix, AdviceSpec existingAdviceSpec) {
            try {
                if (existingAdviceSpec != null && supports(existingAdviceSpec) == false)
                    return null;

                return (A) doParse(factoryContext, configKeyPrefix, (A) existingAdviceSpec);
            } catch (IllegalSpecException e) {
                return null;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not parse AdviceSpec via '{}'. \n"
                            + "  ConfigKeyPrefix: {} \n"
                            + "  Error reason: {} \n", 
                            this.getClass().getSimpleName(), 
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


    /**
     * Parses POJO advice specs by detecting classes that implement
     * {@link io.gemini.api.aop.Advice.Before}, {@link io.gemini.api.aop.Advice.After},
     * or {@link io.gemini.api.aop.Advice.Around}.
     */
    class ForPojoAdvice extends AbstractBase<PojoAdviceSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean supports(AdviceSpec adviceSpec) {
            return PojoAdviceSpec.class.isAssignableFrom(adviceSpec.getClass());
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
            PojoAdviceSpec.Default pojoAdviceSpec = new PojoAdviceSpec.Default(
                    PojoAdviceKind.parse(beforeAdvice, afterAdvice, aroundAdvice),
                    declaringType, declaringType.getTypeName(), adviceMethod
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


    /**
     * Parses AspectJ advice specs from {@code @Aspect}-annotated classes,
     * discovering advice methods annotated with {@code @Before}, {@code @After},
     * {@code @AfterReturning}, {@code @AfterThrowing}, or {@code @Around}.
     */
    class ForAspectJAdvice extends AbstractBase<AspectJAdviceSpec> {

        private static final List<Class<? extends Annotation>> ADVICE_ANNOTATIONS = Arrays.asList(
                Before.class, After.class, 
                AfterReturning.class, AfterThrowing.class, 
                Around.class);


        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean supports(AdviceSpec adviceSpec) {
            return AspectJAdviceSpec.class.isAssignableFrom(adviceSpec.getClass());
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
                return new AspectJAdviceSpec.Default(declaringType, adviceMethod, adviceAnnotation);
            } catch (IllegalSpecException e) {
                return null;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not parse AdviceSpec via '{}'. \n"
                            + "  DeclaringType: {} \n"
                            + "  AdviceMethod: {} \n"
                            + "  Error reason: {} \n", 
                            this.getClass().getSimpleName(),
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

            return new AspectJAdviceSpec.Default(declaringType, adviceMethod, factoryContext.getConfigView(), configKeyPrefix);
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
    }


    /**
     * Parses ByteBuddy advice specs from classes annotated with
     * {@link net.bytebuddy.asm.Advice.OnMethodEnter} or {@link net.bytebuddy.asm.Advice.OnMethodExit}.
     */
    class ForByteBuddyAdvice extends AbstractBase<ByteBuddyAdviceSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean supports(AdviceSpec adviceSpec) {
            return ByteBuddyAdviceSpec.class.isAssignableFrom(adviceSpec.getClass());
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
        protected ByteBuddyAdviceSpec doParse(FactoryContext factoryContext, 
                String configKeyPrefix, ByteBuddyAdviceSpec existingAdviceSpec) {
            return null;
        }
    }
}
