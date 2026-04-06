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
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface PointcutSpecParser {

    Logger LOGGER = LoggerFactory.getLogger(PointcutSpecParser.class);


    PointcutSpec parse(FactoryContext factoryContext, AdviceSpec adviceSpec);

    PointcutSpec parse(FactoryContext factoryContext, String configKeyPrefix, AdviceSpec adviceSpec, 
            PointcutAdvisorSpec existingAdvisorSpec);


    @NoScanning
    class Compound implements PointcutSpecParser {

        private final List<? extends PointcutSpecParser> pointcutSpecParsers;


        public Compound(FactoryContext factoryContext) {
            List<? extends PointcutSpecParser> pointcutSpecParsers = factoryContext.getObjectFactory().createObjectsImplementing(
                    PointcutSpecParser.class, true, "factoryContext", factoryContext);
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
                                + "  Error reason: {} \n", 
                                pointcutSpecParser, 
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
                                + "  Error reason: {} \n", 
                                pointcutSpecParser, 
                                t.getMessage(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }
            }

            return null;
        }
    }


    abstract class AbstractBase<A extends AdviceSpec, P extends PointcutSpec> implements PointcutSpecParser {

        protected abstract Class<? extends A> doGetAdviceSpecClass();

        protected abstract Class<? extends P> doGetPointcutSpecClass();


        /** 
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        @Override
        public PointcutSpec parse(FactoryContext factoryContext, AdviceSpec adviceSpec) {
            try {
                Class<? extends A> adviceSpecClass = doGetAdviceSpecClass();
                if (adviceSpecClass == null 
                        || adviceSpecClass.isAssignableFrom(adviceSpec.getClass()) == false)
                    return null;

                return doParse(factoryContext, (A) adviceSpec);
            } catch(IllegalSpecException e) {
                return null;
            } catch (Throwable t) {
                LOGGER.warn("Could not parse PointcutSpec '{}'. \n"
                        + "  DeclaringType: {} \n"
                        + "  Error reason: {} \n", 
                        doGetPointcutSpecClass().getSimpleName(), 
                        adviceSpec.getDeclaringType().getTypeName(), 
                        t.getMessage(),
                        t
                );

                Throwables.throwIfRequired(t);
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
                Class<? extends A> adviceSpecClass = doGetAdviceSpecClass();
                if (adviceSpecClass == null 
                        || adviceSpecClass.isAssignableFrom(adviceSpec.getClass()) == false
                        || (existingAdvisorSpec != null && existingAdvisorSpec.getAdviceSpec() != null
                            && existingAdvisorSpec.getAdviceSpec().getClass() != adviceSpec.getClass()) )
                    return null;

                return doParse(factoryContext, configKeyPrefix, (A) adviceSpec, existingAdvisorSpec);
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not parse PointcutSpec '{}'. \n"
                            + "  DeclaringType: {} \n"
                            + "  Error reason: {} \n", 
                            doGetPointcutSpecClass().getSimpleName(), 
                            adviceSpec.getDeclaringType().getTypeName(), 
                            t.getMessage(), t
                    );

                Throwables.throwIfRequired(t);
                return null;
            }
        }

        protected abstract PointcutSpec doParse(FactoryContext factoryContext, String configKeyPrefix, 
                A adviceSpec, PointcutAdvisorSpec existingAdvisorSpec);
    }


    class ForPojoPointcut extends AbstractBase<AdviceSpec, PojoPointcutSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends AdviceSpec> doGetAdviceSpecClass() {
            return AdviceSpec.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends PojoPointcutSpec> doGetPointcutSpecClass() {
            return PojoPointcutSpec.class;
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
                pointcutClass = pointcutAnnotation.getValue("pointcutClass")
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
            return null;
        }
    }


    class ForExprPointcut extends AbstractBase<AdviceSpec, ExprPointcutSpec> {

        /** 
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends AdviceSpec> doGetAdviceSpecClass() {
            return AdviceSpec.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Class<? extends ExprPointcutSpec> doGetPointcutSpecClass() {
            return ExprPointcutSpec.class;
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
                pointcutExpression = pointcutAnnotation.getValue("pointcutExpression").resolve(String.class).trim();

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


    class ForAspectJPointcut extends AbstractBase<AspectJAdviceSpec, AspectJPointcutSpec> {

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
        protected Class<? extends AspectJPointcutSpec> doGetPointcutSpecClass() {
            return AspectJPointcutSpec.class;
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
                } catch (Exception ignored) {}
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
}
