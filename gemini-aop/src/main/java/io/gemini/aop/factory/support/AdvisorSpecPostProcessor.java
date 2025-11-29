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
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.MatchingContext;
import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.core.Ordered;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
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
                    StringUtils.join(advisorSpecPostProcessors, AdvisorSpecPostProcessor::toString, "\n  ")
            );


        for (AdvisorSpecPostProcessor advisorSpecPostProcessor : advisorSpecPostProcessors) {
            try {
                advisorSpecMap = advisorSpecPostProcessor.postProcess(factoryContext, advisorSpecMap);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not post-process loaded AdvisorSpec instances via '{}', \n"
                            + "  Error reason: {} \n",
                            advisorSpecPostProcessor, 
                            e.getMessage(), 
                            e
                    );
            }
        }


        if (factoryContext.getAopContext().getDiagnosticLevel().isSimpleEnabled() && LOGGER.isInfoEnabled())
            LOGGER.info("$Took '{}' seconds to post-process {} AdvisorSpec instances under '{}'. ", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecMap.size(), factoryName
            );

        return advisorSpecMap;
    }


    class ParsingConfigViewAdvisorSpec implements AdvisorSpecPostProcessor, Ordered {

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
            Set<String> configuredAdvisorSpecPrefixes = new LinkedHashSet<>();
            for (String key : factoryContext.getConfigView().keys("aop.advisorSpecs.")) {
                int endPos = key.lastIndexOf(ADVISOR_NAME);
                if (endPos > -1) {
                    configuredAdvisorSpecPrefixes.add( key.substring(0, endPos).trim() );
                }
            }

            return configuredAdvisorSpecPrefixes;
        }

        private void parseConfiguredAdvisorSpecs(FactoryContext factoryContext, Map<String, AdvisorSpec> advisorSpecMap, 
                Set<String> configuredAdvisorSpecPrefixs) {
            for (String configKeyPrefix : configuredAdvisorSpecPrefixs) {
                AdvisorSpec configuredAdvisorSpec = doParseConfiguredAdvisorSpec(
                        factoryContext, advisorSpecMap, factoryContext.getConfigView(), configKeyPrefix);
                if (configuredAdvisorSpec == null)
                    continue;

                advisorSpecMap.put(configuredAdvisorSpec.getAdvisorName(), configuredAdvisorSpec);
            }
        }

        protected AdvisorSpec doParseConfiguredAdvisorSpec(
                FactoryContext factoryContext, Map<String, AdvisorSpec> advisorSpecMap, 
                ConfigView configView, String configKeyPrefix) {
            String advisorName = configView.getAsString(configKeyPrefix + ADVISOR_NAME, "");
            if (StringUtils.hasText(advisorName) == false)
                return null;

            try {
                String adviceMethodExpression = configView.getAsString(configKeyPrefix + "adviceMethodExpression", "").trim();
                String pointcutExpression = configView.getAsString(configKeyPrefix + "pointcutExpression", "").trim();

                AdvisorSpec existingAdvisorSpec = advisorSpecMap.get(advisorName);

                ElementMatcher<MatchingContext> condition = AdvisorConditionParser.parseAdvisorCondition(factoryContext, configKeyPrefix);
                if (condition == null) 
                    condition = existingAdvisorSpec != null ? existingAdvisorSpec.getCondition() : null;

                // create or modify AdvisorSpec instance
                if (existingAdvisorSpec == null) {
                    // create new AdvisorSpec instance
                    if (StringUtils.hasText(adviceMethodExpression) == false) {
                        return AdvisorSpecParser.parseExprPointcutAdvisorSpec(
                                factoryContext,
                                condition, 
                                configView, 
                                configKeyPrefix,
                                (AdvisorSpec.ExprPointcutSpec) null
                        );
                    } else {
                        MethodDescription aspectJMethod = findMethod(advisorName, factoryContext, adviceMethodExpression);
                        if (aspectJMethod == null)
                            return null;

                        TypeDescription aspectJType = aspectJMethod.getDeclaringType().asErasure();

                        return AdvisorSpecParser.parseAspectJPointcutAdvisorSpec(
                                factoryContext,
                                aspectJType, 
                                aspectJMethod, 
                                condition, 
                                configView, 
                                configKeyPrefix,
                                null
                        );
                    }
                } else {
                    if (existingAdvisorSpec != null && StringUtils.hasText(adviceMethodExpression)) {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Ignored configured AdvisorSpec with adviceMethodExpression for existing AdvisorSpec. \n"
                                    + "  AdvisorSpec: {} \n"
                                    + "  MethodExpression: {} \n"
                                    + "  AdvisorFactory: {} \n",
                                    existingAdvisorSpec.getAdvisorName(),
                                    adviceMethodExpression, 
                                    factoryContext.getFactoryName()
                            ); 

                        return null;
                    }

                    // modify existing AdvisorSpec instance
                    if (existingAdvisorSpec instanceof AdvisorSpec.PojoPointcutSpec) {
                        if (StringUtils.hasLength(pointcutExpression) == false) {
                            return AdvisorSpecParser.parsePojoPointcutAdvisorSpec(
                                    factoryContext,
                                    condition,
                                    configView, configKeyPrefix, 
                                    (AdvisorSpec.PojoPointcutSpec) existingAdvisorSpec
                            );
                        } else {
                            return AdvisorSpecParser.parseExprPointcutAdvisorSpec(
                                    factoryContext,
                                    condition,
                                    configView, configKeyPrefix, 
                                    (AdvisorSpec.PojoPointcutSpec) existingAdvisorSpec
                            );
                        }
                    } else if (existingAdvisorSpec instanceof AdvisorSpec.ExprPointcutSpec) {
                        return AdvisorSpecParser.parseExprPointcutAdvisorSpec(
                                factoryContext,
                                condition,
                                configView, configKeyPrefix, 
                                (AdvisorSpec.ExprPointcutSpec) existingAdvisorSpec
                        );
                    } else if (existingAdvisorSpec instanceof AspectJPointcutAdvisorSpec) {
                        AspectJPointcutAdvisorSpec advisorSpec = (AspectJPointcutAdvisorSpec) existingAdvisorSpec;
                        return AdvisorSpecParser.parseAspectJPointcutAdvisorSpec(
                                factoryContext,
                                advisorSpec.getAspectJType(), 
                                advisorSpec.getAspectJMethod(), 
                                condition,
                                configView, configKeyPrefix,
                                advisorSpec
                        );
                    }
                }
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not load ConfiguredAdvisorSpec. \n"
                            + "  ConfiguredAdvisorSpec: {} \n"
                            + "  configKeyPrefix: {} \n", 
                            advisorName,
                            configKeyPrefix, 
                            t
                    );

                Throwables.throwIfRequired(t);
            }

            return null;
        }

        private MethodDescription findMethod(String advisorName, FactoryContext factoryContext, String adviceMethodExpression) {
            try {
                return ExprParser.INSTANCE.findMethod(
                        factoryContext.getTypeWorld(), adviceMethodExpression);
            } catch (ExprParser.ExprParseException e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not find method with unparsable MethodExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  MethodExpression: {} \n"
                            + "  AdvisorFactory: {} \n"
                            + "  Syntax Error: {} \n", 
                            advisorName, 
                            adviceMethodExpression, 
                            factoryContext.getFactoryName(), 
                            e.getMessage()
                    );
            } catch (ExprParser.ExprLintException e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not find method with lint MethodExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  MethodExpression: {} \n"
                            + "  AdvisorFactory: {} \n"
                            + "  Lint message: {} \n", 
                            advisorName, 
                            adviceMethodExpression, 
                            factoryContext.getFactoryName(), 
                            e.getMessage()
                    );
            } catch (ExprParser.ExprUnknownException e) {
                if (LOGGER.isWarnEnabled()) {
                    Throwable cause = e.getCause();
                    LOGGER.warn("Could not find method with illegal MethodExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  MethodExpression: {} \n"
                            + "  AdvisorFactory: {} \n"
                            + "  Error reason: {} \n", 
                            advisorName, 
                            adviceMethodExpression, 
                            factoryContext.getFactoryName(), 
                            cause.getMessage(), 
                            cause
                    );
                }
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not find method with illegal MethodExpression. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  MethodExpression: {} \n"
                            + "  AdvisorFactory: {} \n"
                            + "  Error reason: {} \n", 
                            advisorName, 
                            adviceMethodExpression, 
                            factoryContext.getFactoryName(), 
                            e.getMessage(), 
                            e
                    );
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


    class FilteringEnabledAdvisorSpec implements AdvisorSpecPostProcessor, Ordered {

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