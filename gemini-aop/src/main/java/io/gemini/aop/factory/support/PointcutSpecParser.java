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

import java.util.Collections;
import java.util.List;

import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AdviceSpec.AspectJAdviceSpec;
import io.gemini.aop.factory.support.AdvisorSpec.PointcutAdvisorSpec;
import io.gemini.aop.factory.support.PointcutSpec.AspectJPointcutSpec;
import io.gemini.aop.factory.support.PointcutSpec.ExprPointcutSpec;
import io.gemini.aop.factory.support.PointcutSpec.PojoPointcutSpec;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.annotation.EnableCircularityBreaker;
import io.gemini.api.aop.annotation.ExprPointcut;
import io.gemini.api.aop.annotation.PojoPointcut;
import io.gemini.core.OrderComparator;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.type.TypeDescription;

/**
 * Parses {@link PointcutSpec} instances from a {@link AdviceSpec} or from configuration properties.
 * <p>
 * Three concrete parsers handle the three pointcut styles:
 * <ul>
 *   <li>{@link ForPojoPointcut} – reads the {@code @PojoPointcut} annotation to find the pointcut class</li>
 *   <li>{@link ForExprPointcut} – reads the {@code @ExprPointcut} annotation for an AspectJ expression</li>
 *   <li>{@link ForAspectJPointcut} – reads the AspectJ advice annotation value for the pointcut expression</li>
 * </ul>
 * The {@link Compound} implementation delegates to all registered parsers in order.
 * </p>
 *
 * @author   martin.liu
 */
public interface PointcutSpecParser {

    Logger LOGGER = LoggerFactory.getLogger(PointcutSpecParser.class);


    /**
     * Parses a {@link PointcutSpec} from the given {@link AdviceSpec}.
     * Returns {@code null} if this parser does not handle the advice spec type.
     *
     * @param factoryContext the factory context providing type pool and config
     * @param adviceSpec     the advice spec to derive the pointcut spec from
     * @return the parsed {@link PointcutSpec}, or {@code null}
     */
    PointcutSpec parse(FactoryContext factoryContext, AdviceSpec adviceSpec);

    /**
     * Parses or updates a {@link PointcutSpec} from configuration properties under the given key prefix.
     * Returns {@code null} if this parser does not handle the spec type or no config is found.
     *
     * @param factoryContext       the factory context providing config view and type pool
     * @param configKeyPrefix      the configuration key prefix (e.g. {@code aop.advisorSpecs.myAdvisor.})
     * @param adviceSpec           the advice spec associated with this pointcut
     * @param existingAdvisorSpec  an existing advisor spec to update, or {@code null}
     * @return the parsed or updated {@link PointcutSpec}, or {@code null}
     */
    PointcutSpec parse(FactoryContext factoryContext, String configKeyPrefix, AdviceSpec adviceSpec, 
            PointcutAdvisorSpec existingAdvisorSpec);


    /**
     * Delegates to all registered {@link PointcutSpecParser} instances in order,
     * returning the first non-null result.
     */
    @NoScanning
    class Compound implements PointcutSpecParser {

        private final List<? extends PointcutSpecParser> pointcutSpecParsers;


        public Compound(FactoryContext factoryContext) {
            List<? extends PointcutSpecParser> pointcutSpecParsers = factoryContext.getObjectFactory()
                    .createObjectsImplementing(
                            PointcutSpecParser.class, 
                            false, 
                            "factoryContext", factoryContext
                    );
            this.pointcutSpecParsers = pointcutSpecParsers == null 
                    ? Collections.emptyList() : pointcutSpecParsers;

            OrderComparator.sort(this.pointcutSpecParsers);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public PointcutSpec parse(FactoryContext factoryContext, AdviceSpec adviceSpec) {
            for (PointcutSpecParser pointcutSpecParser : pointcutSpecParsers) {
                try {
                    PointcutSpec pointcutSpec = pointcutSpecParser.parse(factoryContext, adviceSpec);
                    if (pointcutSpec != null)
                        return pointcutSpec;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not parse PointcutSpec via '{}'. \n"
                                + "  DeclaringType: {} \n"
                                + "  Error reason: {} \n", 
                                pointcutSpecParser.getClass().getSimpleName(), 
                                adviceSpec.getDeclaringType().getTypeName(),
                                t.getMessage(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }
            }

            return null;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public PointcutSpec parse(FactoryContext factoryContext, String configKeyPrefix, 
                AdviceSpec adviceSpec, PointcutAdvisorSpec existingAdvisorSpec) {
            for (PointcutSpecParser pointcutSpecParser : pointcutSpecParsers) {
                try {
                    PointcutSpec pointcutSpec = pointcutSpecParser.parse(factoryContext, configKeyPrefix,
                            adviceSpec, existingAdvisorSpec);
                    if (pointcutSpec != null)
                        return pointcutSpec;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not parse PointcutSpec via '{}'. \n"
                                + "  ConfigKeyPrefix: {} \n"
                                + "  Error reason: {} \n", 
                                pointcutSpecParser, 
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


    /**
     * Abstract base providing common parsing logic: spec class filtering, error handling,
     * and config-key-based parsing delegation.
     */
    abstract class AbstractBase<A extends AdviceSpec, P extends PointcutSpec> implements PointcutSpecParser {

        protected abstract boolean supports(AdviceSpec adviceSpec);

        /** 
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public PointcutSpec parse(FactoryContext factoryContext, AdviceSpec adviceSpec) {
            try {
                if (adviceSpec == null || supports(adviceSpec) == false)
                    return null;

                return doParse(factoryContext, (A) adviceSpec);
            } catch(IllegalSpecException e) {
                return null;
            } catch (Exception e) {
                LOGGER.warn("Could not parse PointcutSpec via '{}'. \n"
                        + "  DeclaringType: {} \n"
                        + "  Error reason: {} \n", 
                        this.getClass().getSimpleName(), 
                        adviceSpec.getDeclaringType().getTypeName(), 
                        e.getMessage(),
                        e
                );

                return null;
            }
        }

        protected abstract P doParse(FactoryContext factoryContext, A adviceSpec);


        protected boolean isBreakCircularity(AnnotationList annotations) {
            return annotations.isAnnotationPresent(EnableCircularityBreaker.class)
                    ? true : PointcutSpec.DEFAULT_BREAK_CIRCULARITY;
        }


        /** 
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public PointcutSpec parse(FactoryContext factoryContext, String configKeyPrefix, AdviceSpec adviceSpec,
                PointcutAdvisorSpec existingAdvisorSpec) {
            try {
                if (adviceSpec == null || supports(adviceSpec) == false)
                    return null;

                return doParse(factoryContext, configKeyPrefix, (A) adviceSpec, existingAdvisorSpec);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not parse PointcutSpec via '{}'. \n"
                            + "  ConfigKeyPrefix: {} \n"
                            + "  DeclaringType: {} \n"
                            + "  Error reason: {} \n", 
                            this.getClass().getSimpleName(), 
                            configKeyPrefix,
                            adviceSpec.getDeclaringType().getTypeName(), 
                            e.getMessage(), 
                            e
                    );

                return null;
            }
        }

        protected abstract PointcutSpec doParse(FactoryContext factoryContext, String configKeyPrefix, 
                A adviceSpec, PointcutAdvisorSpec existingAdvisorSpec);
    }


    /**
     * Reads the {@link io.gemini.api.aop.annotation.PojoPointcut} annotation to extract
     * the pointcut class reference.
     */
    class ForPojoPointcut extends AbstractBase<AdviceSpec, PojoPointcutSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean supports(AdviceSpec adviceSpec) {
            return true;
        }

        /** 
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        protected PojoPointcutSpec doParse(FactoryContext factoryContext, AdviceSpec adviceSpec) {
            TypeDescription adviceType = adviceSpec.getDeclaringType();

            Class<? extends Pointcut> pointcutClass = null;
            AnnotationDescription pointcutAnnotation = adviceType.getDeclaredAnnotations().ofType(PojoPointcut.class);
            if (pointcutAnnotation != null)
                pointcutClass = pointcutAnnotation.getValue("value")
                .load(factoryContext.getClassLoader())
                .resolve(Class.class);

            if (pointcutClass == null)
                return null;

            return new PojoPointcutSpec.Default(
                    adviceSpec, 
                    isBreakCircularity(adviceType.getDeclaredAnnotations()), 
                    pointcutClass
            );
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        protected PointcutSpec doParse(FactoryContext factoryContext, String configKeyPrefix, 
                AdviceSpec adviceSpec, PointcutAdvisorSpec existingAdvisorSpec) {
            // do NOT parse PojoPointcut from configuration properties.
            return null;
        }
    }


    /**
     * Reads the {@link io.gemini.api.aop.annotation.ExprPointcut} annotation to extract
     * the pointcut expression string.
     */
    class ForExprPointcut extends AbstractBase<AdviceSpec, ExprPointcutSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean supports(AdviceSpec adviceSpec) {
            return true;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected ExprPointcutSpec doParse(FactoryContext factoryContext, AdviceSpec adviceSpec) {
            TypeDescription adviceType = adviceSpec.getDeclaringType();

            String pointcutExpression = null;
            AnnotationDescription pointcutAnnotation = adviceType.getDeclaredAnnotations().ofType(ExprPointcut.class);
            if (pointcutAnnotation != null)
                pointcutExpression = pointcutAnnotation.getValue("value").resolve(String.class).trim();

            if (StringUtils.hasText(pointcutExpression) == false)
                return null;

            return new ExprPointcutSpec.Default(
                    adviceSpec, 
                    isBreakCircularity(adviceType.getDeclaredAnnotations()), 
                    pointcutExpression 
            );
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        protected PointcutSpec doParse(FactoryContext factoryContext, String configKeyPrefix, 
                AdviceSpec adviceSpec, PointcutAdvisorSpec existingAdvisorSpec) {
            ConfigView configView = factoryContext.getConfigView();

            // overwrite configuration properties if exists
            String pointcutExpression = configView.getAsString(
                    configKeyPrefix + "pointcutExpression", "");
            if (StringUtils.hasText(pointcutExpression) == false)
                return existingAdvisorSpec.getPointcutSpec();

            boolean breakCircularity = configView.getAsBoolean(
                    configKeyPrefix + "breakCircularity", false);

            return new ExprPointcutSpec.Default(
                    adviceSpec, 
                    breakCircularity, 
                    pointcutExpression 
            );
        }
    }


    /**
     * Reads the AspectJ advice annotation value to extract the pointcut expression,
     * and builds an {@link io.gemini.aop.factory.support.PointcutSpec.AspectJPointcutSpec}.
     */
    class ForAspectJPointcut extends AbstractBase<AspectJAdviceSpec, AspectJPointcutSpec> {

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
        protected AspectJPointcutSpec doParse(FactoryContext factoryContext, AspectJAdviceSpec adviceSpec) {
            AnnotationList decalaringTypeAnnotations = adviceSpec.getDeclaringType().getDeclaredAnnotations();
            if (decalaringTypeAnnotations.isAnnotationPresent(Aspect.class) == false)
                return null;

            AnnotationDescription aspectJAdviceAnnotation = adviceSpec.getAdviceAnnotation();;
            String pointcutExpression = aspectJAdviceAnnotation.getValue("value").resolve(String.class).trim();
            if (StringUtils.hasText(pointcutExpression) == false) {
                try {
                    // override 'value' attribute
                    pointcutExpression = aspectJAdviceAnnotation.getValue("pointcut").resolve(String.class).trim();
                } catch (Exception ignored) { /* do nothing */ }
            }

            AnnotationList adviceMethodAnnotations = adviceSpec.getAdviceMethod().getDeclaredAnnotations();
            return new AspectJPointcutSpec.Default(
                    adviceSpec, 
                    isBreakCircularity(decalaringTypeAnnotations) || isBreakCircularity(adviceMethodAnnotations), 
                    pointcutExpression
            );
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        protected PointcutSpec doParse(FactoryContext factoryContext, String configKeyPrefix, 
                AspectJAdviceSpec adviceSpec, PointcutAdvisorSpec existingAdvisorSpec) {
            ConfigView configView = factoryContext.getConfigView();

            // overwrite configuration properties if exists
            String pointcutExpression = configView.getAsString(configKeyPrefix + "pointcutExpression", "");
            if (StringUtils.hasText(pointcutExpression) == false)
                return existingAdvisorSpec.getPointcutSpec();

            boolean breakCircularity = configView.getAsBoolean(configKeyPrefix + "breakCircularity", false);

            return new ExprPointcutSpec.Default(
                    adviceSpec, 
                    breakCircularity, 
                    pointcutExpression 
            );
        }
    }
}
