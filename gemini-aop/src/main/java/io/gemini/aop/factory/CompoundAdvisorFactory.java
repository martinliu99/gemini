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
package io.gemini.aop.factory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.core.OrderComparator;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;


/**
 * Aggregates multiple {@link DefaultAdvisorFactory} instances, one per discovered aspect application.
 * <p>
 * When {@link #getAdvisors(TypeDescription, ClassLoader, JavaModule)} is called, it queries all
 * child factories and merges their advisor lists per method, preserving insertion order.
 * </p>
 *
 * @author   martin.liu
 */
class CompoundAdvisorFactory implements AdvisorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompoundAdvisorFactory.class);


    private final FactoriesContext factoriesContext;

    private final Map<FactoryContext, DefaultAdvisorFactory> advisorFactoryMap;


    public CompoundAdvisorFactory(AopContext aopContext) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating CompoundAdvisorFactory.");


        this.factoriesContext = new FactoriesContext(aopContext);

        Map<String, FactoryContext> factoryContextMap = factoriesContext.getFactoryContextMap();

        this.advisorFactoryMap = createAdvisorFactoryMap(aopContext, factoriesContext, factoryContextMap);


        if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create CompoundAdvisorFactory, {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME,
                    StringUtils.join(getAdvisorSpecNum().entrySet(), 
                            entry -> entry.getKey() + ": " + entry.getValue() + " AdvisorSpecs", "\n  ", "\n  ", "\n") 
            );
    }

    /**
     * Creates a {@link DefaultAdvisorFactory} (or its diagnostic variant) for each
     * aspect application found in the {@link FactoriesContext}.
     *
     * @param aopContext       the central AOP context
     * @param factoriesContext the aggregated factories context
     * @param factoryContextMap map of factory name to {@link FactoryContext}
     * @return ordered map of {@link FactoryContext} to its {@link DefaultAdvisorFactory}
     */
    private Map<FactoryContext, DefaultAdvisorFactory> createAdvisorFactoryMap(AopContext aopContext, 
            FactoriesContext factoriesContext,
            Map<String, FactoryContext> factoryContextMap) {
        Map<FactoryContext, DefaultAdvisorFactory> advisorFactoryMap = new LinkedHashMap<>(factoryContextMap.size());
        for (FactoryContext factoryContext : factoryContextMap.values()) {
            // create AdvisorFactory
            DefaultAdvisorFactory advisorFactory = aopContext.getDiagnosticLevel().isSimpleEnabled() == false
                    ? new DefaultAdvisorFactory(factoryContext)
                    : aopContext.isDetectTypeResolution() == false 
                            ? new DefaultAdvisorFactory.Diagnostic(factoryContext)
                            : new DefaultAdvisorFactory.TyepResolutionDetector(factoryContext);

            advisorFactoryMap.put(factoryContext, advisorFactory);
        }

        return advisorFactoryMap;
    }


    /**
     * {@inheritDoc}
     * <p>Returns a map of aspect-app name to the number of advisor specifications it contributed.</p>
     */
    @Override
    public Map<String, Integer> getAdvisorSpecNum() {
        return this.advisorFactoryMap.values().stream()
                .flatMap( e -> 
                    e.getAdvisorSpecNum().entrySet().stream() )
                .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );
    }


    /**
     * {@inheritDoc}
     * <p>Queries all child {@link DefaultAdvisorFactory} instances and merges their
     * per-method advisor lists, preserving insertion order.</p>
     */
    @Override
    public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(
            TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule) {
        Map<MethodDescription, List<Advisor>> targetMethodAdvisorMap = new LinkedHashMap<>();
        // collect advisors per method
        for (Entry<FactoryContext, DefaultAdvisorFactory> entry: advisorFactoryMap.entrySet()) {
            // get advisors per AdvisorFactory
            Map<? extends MethodDescription, List<? extends Advisor>> advisorMap = entry.getValue()
                    .getAdvisors(targetType, targetClassLoader, targetModule);

            // merge advisors
            for (Entry<? extends MethodDescription, List<? extends Advisor>> methodAdvisorEntry : advisorMap.entrySet()) {
                targetMethodAdvisorMap
                .computeIfAbsent(methodAdvisorEntry.getKey(), key -> new ArrayList<>() )
                .addAll(methodAdvisorEntry.getValue());
            }
        }

        if (targetMethodAdvisorMap.size() == 0) 
            return Collections.emptyMap();


        // sort advisors
        Map<MethodDescription, List<? extends Advisor>> resultMap = new LinkedHashMap<MethodDescription, List<? extends Advisor>>(
                targetMethodAdvisorMap.size());
        for (Entry<MethodDescription, List<Advisor>> entry : targetMethodAdvisorMap.entrySet()) {
            List<Advisor> advisors = entry.getValue();
            OrderComparator.sort(advisors);

            resultMap.put(entry.getKey(), advisors);
        }
        return resultMap;
    }

    /**
     * {@inheritDoc}
     * <p>Closes all child factories and the {@link FactoriesContext}.</p>
     */
    @Override
    public void close() throws IOException {
        for (Closeable closeable : advisorFactoryMap.values()) {
            closeable.close();
        }

        this.factoriesContext.close();
    }
}
