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

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopMetrics;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.matcher.AdvisorCondition;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.condition.ConditionContext;
import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.core.Ordered;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorSpecPostProcessor {

    static final Logger LOGGER = LoggerFactory.getLogger(AdvisorSpecPostProcessor.class);


    Map<String, AdvisorSpec> postProcess(FactoryContext factoryContext, Map<String, AdvisorSpec> advisorSpecMap);


    static Map<String, AdvisorSpec> postProcessSpecs(FactoryContext factoryContext, Map<String, AdvisorSpec> advisorSpecMap) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        List<AdvisorSpecPostProcessor> advisorSpecPostProcessors = factoryContext.getAdvisorSpecProcessors();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Post-processing AdvisorSpec instances under '{}' via AdvisorSpecPostProcessors, \n"
                    + "  {} \n", 
                    factoryName,
                    StringUtils.join(advisorSpecPostProcessors, AdvisorSpecPostProcessor::toString, "\n    ")
            );


        for (AdvisorSpecPostProcessor advisorSpecPostProcessor : advisorSpecPostProcessors) {
            try {
                advisorSpecMap = advisorSpecPostProcessor.postProcess(factoryContext, advisorSpecMap);
            } catch (Exception e) {
                LOGGER.warn("Failed to post-process loaded AdvisorSpec instances via '{}', \n  Error reason: {} \n",
                        advisorSpecPostProcessor, e.getMessage(), e);
            }
        }


        if (factoryContext.getAopContext().getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to post-process {} AdvisorSpec instances under '{}'. ", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecMap.size(), factoryName
            );

        return advisorSpecMap;
    }


    class ForConfiguredSpec implements AdvisorSpecPostProcessor, Ordered {

        private static final String CONFIGURED_ADVISOR_SPECS_PREFIX = "aop.advisorSpecs.";
        private static final String ADVISOR_NAME = "advisorName";


        /** {@inheritDoc} 
         */
        @Override
        public Map<String, AdvisorSpec> postProcess(FactoryContext factoryContext, 
                Map<String, AdvisorSpec> advisorSpecMap) {
            Set<String> configuiredAdvisorSpecPrefixs = findConfiguredAdvisorSpecPrefix(factoryContext);
            parseConfiguredAdvisorSpecs(factoryContext, advisorSpecMap, configuiredAdvisorSpecPrefixs);

            return advisorSpecMap;
        }

        /**
         * @param factoryContext
         * @return
         */
        private Set<String> findConfiguredAdvisorSpecPrefix(FactoryContext factoryContext) {
            Set<String> configuredAdvisorSpecPrefixs = new LinkedHashSet<>();
            for (String key : factoryContext.getConfigView().keys()) {
                int startPos = key.indexOf(CONFIGURED_ADVISOR_SPECS_PREFIX);
                int endPos = key.lastIndexOf(ADVISOR_NAME);
                if (startPos > -1 && endPos > -1) {
                    configuredAdvisorSpecPrefixs.add( key.substring(startPos, endPos).trim() );
                }
            }

            return configuredAdvisorSpecPrefixs;
        }

        private void parseConfiguredAdvisorSpecs(FactoryContext factoryContext, Map<String, AdvisorSpec> advisorSpecMap, 
                Set<String> configuredAdvisorSpecPrefixs) {
            for (String configSpecPrefix : configuredAdvisorSpecPrefixs) {
                AdvisorSpec configuredAdvisorSpec = doParseConfiguredAdvisorSpec(
                        factoryContext, advisorSpecMap, factoryContext.getConfigView(), configSpecPrefix);
                if (configuredAdvisorSpec == null)
                    continue;

                advisorSpecMap.put(configuredAdvisorSpec.getAdvisorName(), configuredAdvisorSpec);
            }
        }

        protected AdvisorSpec doParseConfiguredAdvisorSpec(
                FactoryContext factoryContext, Map<String, AdvisorSpec> advisorSpecMap, 
                ConfigView configView, String keyPrefix) {
            String advisorName = configView.getAsString(keyPrefix + ADVISOR_NAME, "");
            if (StringUtils.hasText(advisorName) == false)
                return null;

            String adviceMethodExpression = configView.getAsString(keyPrefix + "adviceMethodExpression", "").trim();
            String pointcutExpression = configView.getAsString(keyPrefix + "pointcutExpression", "").trim();


            // merge AdvisorSepc definition properties.
            AdvisorSpec existingAdvisorSpec = advisorSpecMap.get(advisorName);

            String adviceClassName = configView.getAsString(keyPrefix + "adviceClassName", 
                    existingAdvisorSpec != null ? existingAdvisorSpec.getAdviceClassName() : "").trim();

            ElementMatcher<ConditionContext> condition = AdvisorCondition.create(factoryContext, configView, keyPrefix);
            if (condition == null)
                condition = existingAdvisorSpec != null ? existingAdvisorSpec.getCondition() : AdvisorSpec.TRUE;

            Boolean perInstance = configView.getAsBoolean(keyPrefix + "perInstance", 
                    existingAdvisorSpec != null ? existingAdvisorSpec.isPerInstance() : false);
            Integer order = configView.getAsInteger(keyPrefix + "order", 
                    existingAdvisorSpec != null ? existingAdvisorSpec.getOrder(): Integer.MAX_VALUE);


            // create or modify AdvisorSpec instance
            if (existingAdvisorSpec == null) {
                // create new AdvisorSpec instance
                if (StringUtils.hasText(adviceMethodExpression)) {
                    // if adviceMethodExpression is defined, all properties should be defined.
                    MethodDescription aspectJMethod = findMethod(advisorName, factoryContext, adviceMethodExpression);
                    if (aspectJMethod == null)
                        return null;

                    TypeDescription aspectJType = aspectJMethod.getDeclaringType().asErasure();

                    return AspectJAdvisorSpec.Parser.parse(
                            advisorName, condition, perInstance, order,
                            aspectJType, aspectJMethod, 
                            configView, keyPrefix);
                } else {
                    return new AdvisorSpec.ExprPointcutSpec.Default(
                            advisorName, condition, perInstance,
                            adviceClassName, pointcutExpression, order);
                }
            } else {
                // modify existing AdvisorSpec instance
                if (StringUtils.hasLength(pointcutExpression)) {
                    if (existingAdvisorSpec instanceof AspectJAdvisorSpec == false) {
                        return new AdvisorSpec.ExprPointcutSpec.Default(
                                advisorName, condition, perInstance,
                                adviceClassName, pointcutExpression, order);
                    } else {
                        AspectJAdvisorSpec aspectJAdvisorSpec = (AspectJAdvisorSpec) existingAdvisorSpec;

                        return AspectJAdvisorSpec.Parser.parse(
                                advisorName, condition, perInstance, order,
                                aspectJAdvisorSpec.getAspectJType(), aspectJAdvisorSpec.getAspectJMethod(), 
                                configView, keyPrefix); 
                    }
                } else {
                    if (existingAdvisorSpec instanceof AdvisorSpec.PojoPointcutSpec) {
                        AdvisorSpec.PojoPointcutSpec pojoPointcutSpec = (AdvisorSpec.PojoPointcutSpec) existingAdvisorSpec;

                        return new AdvisorSpec.PojoPointcutSpec.Default(
                                advisorName, condition, perInstance,
                                adviceClassName, pojoPointcutSpec.getPointcut(), order);
                    } else if (existingAdvisorSpec instanceof AdvisorSpec.ExprPointcutSpec) {
                        AdvisorSpec.ExprPointcutSpec exprPointcutSpec = (AdvisorSpec.ExprPointcutSpec) existingAdvisorSpec;

                        return new AdvisorSpec.ExprPointcutSpec.Default(
                                advisorName, condition, perInstance,
                                adviceClassName, exprPointcutSpec.getPointcutExpression(), order);
                    } else if (existingAdvisorSpec instanceof AspectJAdvisorSpec) {
                        AspectJAdvisorSpec aspectJAdvisorSpec = (AspectJAdvisorSpec) existingAdvisorSpec;

                        return new AspectJAdvisorSpec(
                                advisorName, condition, perInstance, 
                                aspectJAdvisorSpec.getAdviceClassName(), order,
                                aspectJAdvisorSpec.getAspectJType(), aspectJAdvisorSpec.getAspectJMethod(), 
                                aspectJAdvisorSpec.getAdviceCategory(), aspectJAdvisorSpec.getPointcutExpression(),
                                aspectJAdvisorSpec.getParameterDescriptionMap(), 
                                aspectJAdvisorSpec.getPointcutParameterNames(), aspectJAdvisorSpec.getNamedPointcutParameters(),
                                aspectJAdvisorSpec.getAdviceReturningParameterType(), aspectJAdvisorSpec.getAdviceThrowingParameterType()); 
                    }
                }
            }

            return null;
        }

        private MethodDescription findMethod(String advisorName, FactoryContext factoryContext, String adviceMethodExpression) {
            try {
                return ExprParser.INSTANCE.findMethod(
                        factoryContext.getTypeWorld(), adviceMethodExpression);
            } catch (ExprParser.ExprParseException e) {
                LOGGER.warn("Failed to find method with unparsable MethodExpression. \n  AdvisorSpec: {} \n  MethodExpression: {} \n  FactoryName: {} \n  Syntax Error: {} \n", 
                        advisorName, adviceMethodExpression, factoryContext.getFactoryName(), e.getMessage());
            } catch (ExprParser.ExprLintException e) {
                LOGGER.warn("Failed to find method with lint MethodExpression. \n  AdvisorSpec: {} \n  MethodExpression: {} \n  FactoryName: {} \n  Lint message: {} \n", 
                        advisorName, adviceMethodExpression, factoryContext.getFactoryName(), e.getMessage());
            } catch (ExprParser.ExprUnknownException e) {
                Throwable cause = e.getCause();
                LOGGER.warn("Failed to find method with illegal MethodExpression. \n  AdvisorSpec: {} \n  MethodExpression: {} \n  FactoryName: {} \n  Error reason: {} \n", 
                        advisorName, adviceMethodExpression, factoryContext.getFactoryName(), cause.getMessage(), cause);
            } catch (Exception e) {
                LOGGER.warn("Failed to find method with illegal MethodExpression. \n  AdvisorSpec: {} \n  MethodExpression: {} \n  FactoryName: {} \n  Error reason: {} \n", 
                        advisorName, adviceMethodExpression, factoryContext.getFactoryName(), e.getMessage(), e);
            }

            return null;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }


    class ForMatchedSpec implements AdvisorSpecPostProcessor, Ordered {

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<String, AdvisorSpec> postProcess(FactoryContext factoryContext,
                Map<String, AdvisorSpec> advisorSpecMap) {
            for (Iterator<Entry<String, AdvisorSpec>> iterator = advisorSpecMap.entrySet().iterator(); 
                    iterator.hasNext(); ) {
                Entry<String, AdvisorSpec> entry = iterator.next();

                // filter advisorRepositry via advisorMatcher
                if (factoryContext.isEnabledAdvisor(entry.getKey()) == false)
                    iterator.remove();
            }

            return advisorSpecMap;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}