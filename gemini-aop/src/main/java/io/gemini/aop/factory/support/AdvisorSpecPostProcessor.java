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
import io.gemini.aop.factory.support.AdvisorSpec.PointcutAdvisorSpec;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.annotation.Order;
import io.gemini.api.aop.MatchingContext;
import io.gemini.core.OrderComparator;
import io.gemini.core.Ordered;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Post-processes the map of scanned {@link AdvisorSpec} instances after initial scanning.
 * <p>
 * Implementations can add, modify, or remove advisor specs. The {@link Compound} implementation
 * delegates to all registered post-processors in order. 
 * 
 * Built-in implementations:
 * <ul>
 *   <li>{@link ParsingConfigViewAdvisorSpec} – adds or overrides advisor specs from configuration properties</li>
 *   <li>{@link FilteringEnabledAdvisorSpec} – removes advisor specs not matching the enabled-advisor filter</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public interface AdvisorSpecPostProcessor {

    Logger LOGGER = LoggerFactory.getLogger(AdvisorSpecPostProcessor.class);


    /**
     * Post-processes the given map of scanned {@link AdvisorSpec} instances.
     * Implementations may add, modify, or remove entries.
     *
     * @param factoryContext the factory context providing config and type information
     * @param advisorSpecMap the mutable map of advisor name to {@link AdvisorSpec}
     * @return the (possibly modified) advisor spec map
     */
    Map<String, AdvisorSpec> postProcess(FactoryContext factoryContext, Map<String, AdvisorSpec> advisorSpecMap);


    /**
     * Delegates to all registered {@link AdvisorSpecPostProcessor} instances in order.
     */
    @NoScanning
    class Compound implements AdvisorSpecPostProcessor {

        private final List<? extends AdvisorSpecPostProcessor> advisorSpecPostProcessors;


        public Compound(FactoryContext factoryContext) {
            List<? extends AdvisorSpecPostProcessor> advisorSpecPostProcessors = factoryContext.getObjectFactory()
                    .createObjectsImplementing(
                            AdvisorSpecPostProcessor.class, true, "factoryContext", factoryContext);
            this.advisorSpecPostProcessors = advisorSpecPostProcessors == null 
                    ? Collections.emptyList() : advisorSpecPostProcessors;

            OrderComparator.sort(this.advisorSpecPostProcessors);
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        public Map<String, AdvisorSpec> postProcess(FactoryContext factoryContext,
                Map<String, AdvisorSpec> advisorSpecMap) {
            long startedAt = System.nanoTime();
            String factoryName = factoryContext.getFactoryName();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("^Post-processing AdvisorSpecs under '{}' via AdvisorSpecPostProcessors, \n"
                        + "  {} \n", 
                        factoryName,
                        StringUtils.join(advisorSpecPostProcessors, AdvisorSpecPostProcessor::toString, "\n  ")
                );


            for (AdvisorSpecPostProcessor advisorSpecPostProcessor : advisorSpecPostProcessors) {
                try {
                    advisorSpecMap = advisorSpecPostProcessor.postProcess(factoryContext, advisorSpecMap);
                } catch (IllegalSpecException e) {
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not post-process loaded AdvisorSpecs via '{}', \n"
                                + "  Error reason: {} \n",
                                advisorSpecPostProcessor, 
                                t.getMessage(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }
            }


            if (LOGGER.isInfoEnabled() && factoryContext.getAopContext().getDiagnosticLevel().isSimpleEnabled())
                LOGGER.info("$Took '{}' seconds to post-process {} AdvisorSpecs under '{}'. ", 
                        (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecMap.size(), factoryName
                );

            return advisorSpecMap;
        }
        
    }


    /**
     * Adds or overrides advisor specs from configuration properties
     * (keys matching {@code aop.advisorSpecs.<prefix>.advisorName}).
     */
    class ParsingConfigViewAdvisorSpec implements AdvisorSpecPostProcessor, Ordered {

        private static final String ADVISOR_NAME_CONFIG_KEY_SUFFIX = "advisorName";


        /** 
         * {@inheritDoc} 
         */
        @Override
        public Map<String, AdvisorSpec> postProcess(FactoryContext factoryContext, 
                Map<String, AdvisorSpec> advisorSpecMap) {
            Set<String> configuiredAdvisorSpecPrefixs = findConfiguredAdvisorSpecPrefix(factoryContext);
            parseConfiguredAdvisorSpecs(factoryContext, advisorSpecMap, configuiredAdvisorSpecPrefixs);

            return advisorSpecMap;
        }

        /**
         * Scans the config view for all advisor spec key prefixes under {@code aop.advisorSpecs.}.
         *
         * @param factoryContext the factory context providing the config view
         * @return the set of discovered config key prefixes
         */
        private Set<String> findConfiguredAdvisorSpecPrefix(FactoryContext factoryContext) {
            Set<String> configuredAdvisorSpecPrefixes = new LinkedHashSet<>();
            for (String key : factoryContext.getConfigView().keys("aop.advisorSpecs.")) {
                int endPos = key.lastIndexOf(ADVISOR_NAME_CONFIG_KEY_SUFFIX);
                if (endPos > -1) {
                    configuredAdvisorSpecPrefixes.add( key.substring(0, endPos).trim() );
                }
            }

            return configuredAdvisorSpecPrefixes;
        }

        private void parseConfiguredAdvisorSpecs(FactoryContext factoryContext, Map<String, AdvisorSpec> advisorSpecMap, 
                Set<String> configuredAdvisorSpecPrefixs) {
            AdviceSpecParser adviceSpecParser = new AdviceSpecParser.Compound(factoryContext);
            PointcutSpecParser pointcutSpecParser = new PointcutSpecParser.Compound(factoryContext);

            for (String configKeyPrefix : configuredAdvisorSpecPrefixs) {
                AdvisorSpec configuredAdvisorSpec = doParseConfiguredAdvisorSpec(
                        factoryContext, advisorSpecMap, factoryContext.getConfigView(), configKeyPrefix,
                        adviceSpecParser, pointcutSpecParser);
                if (configuredAdvisorSpec == null)
                    continue;

                advisorSpecMap.put(configuredAdvisorSpec.getAdvisorName(), configuredAdvisorSpec);
            }
        }

        protected AdvisorSpec doParseConfiguredAdvisorSpec(
                FactoryContext factoryContext, Map<String, AdvisorSpec> advisorSpecMap, 
                ConfigView configView, String configKeyPrefix, 
                AdviceSpecParser adviceSpecParser, PointcutSpecParser pointcutSpecParser) {
            String advisorName = configView.getAsString(configKeyPrefix + ADVISOR_NAME_CONFIG_KEY_SUFFIX, "");

            try {
                PointcutAdvisorSpec existingAdvisorSpec = (PointcutAdvisorSpec) advisorSpecMap.get(advisorName);

                AdviceSpec adviceSpec = adviceSpecParser.parse(factoryContext, configKeyPrefix, 
                        existingAdvisorSpec == null ? null : existingAdvisorSpec.getAdviceSpec());

                PointcutSpec pointcutSpec = pointcutSpecParser.parse(factoryContext, configKeyPrefix, adviceSpec, existingAdvisorSpec);

                // overwrite configuration properties if exists
                advisorName = StringUtils.hasText(advisorName) 
                        ? advisorName
                        : existingAdvisorSpec == null ? adviceSpec.getAdviceClassName() : existingAdvisorSpec.getAdvisorName();

                boolean defaultPerInstance = existingAdvisorSpec == null 
                        ? false : existingAdvisorSpec.isPerInstance();
                boolean perInstance = configView.getAsBoolean(configKeyPrefix + "perInstance", defaultPerInstance );

                int defaultOrder = existingAdvisorSpec == null 
                        ? Order.LOWEST_PRECEDENCE : existingAdvisorSpec.getOrder();
                int order = configView.getAsInteger(configKeyPrefix + "order", defaultOrder );

                ElementMatcher<MatchingContext> condition = AdvisorConditionParser.parseAdvisorCondition(factoryContext, configKeyPrefix);
                if (condition == null) 
                    condition = existingAdvisorSpec != null ? existingAdvisorSpec.getCondition() : null;

                return new PointcutAdvisorSpec.Default(
                        advisorName, condition, 
                        adviceSpec, perInstance, order, 
                        pointcutSpec);
            } catch (IllegalSpecException e) {
                return null;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not load configured AdvisorSpec. \n"
                            + "  configKeyPrefix: {} \n"
                            + "  Error reason: {} \n", 
                            configKeyPrefix, 
                            e.getMessage(),
                            e
                    );

                Throwables.throwIfRequired(e);
            }

            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getOrder() {
            return Order.HIGHEST_PRECEDENCE;
        }
    }


    /**
     * Removes advisor specs whose names do not match the enabled-advisor filter
     * configured via {@code aop.factory.enabledAdvisorExpressions}.
     */
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
            return Order.LOWEST_PRECEDENCE;
        }
    }
}