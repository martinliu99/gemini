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
package io.gemini.aop.factory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.AdvisorFactory;
import io.gemini.aop.AopContext;
import io.gemini.api.aop.AdvisorSpec;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;


class CompoundAdvisorFactory implements AdvisorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompoundAdvisorFactory.class);


    private final FactoriesContext factoriesContext;

    private final Map<FactoryContext, DefaultAdvisorFactory> advisorFactoryMap;


    public CompoundAdvisorFactory(AopContext aopContext) {
        this.factoriesContext = new FactoriesContext(aopContext);

        Map<String, FactoryContext> factoryContextMap = factoriesContext.getFactoryContextMap();

        this.advisorFactoryMap = createAdvisorFactoryMap(aopContext, factoriesContext, factoryContextMap);
    }

    private Map<FactoryContext, DefaultAdvisorFactory> createAdvisorFactoryMap(AopContext aopContext, 
            FactoriesContext factoriesContext,
            Map<String, FactoryContext> factoryContextMap) {
        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("^Creating CompoundAdvisorFactory.");

        Map<FactoryContext, DefaultAdvisorFactory> advisorFactoryMap = new LinkedHashMap<>();
        for(FactoryContext factoryContext : factoryContextMap.values()) {
            DefaultAdvisorFactory advisorFactory = this.createAdvisorFactory(aopContext, factoriesContext, factoryContext);
            advisorFactoryMap.put(factoryContext, advisorFactory);
        }

        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create CompoundAdvisorFactory. {}", (System.nanoTime() - startedAt) / 1e9);
        return advisorFactoryMap;
    }

    private DefaultAdvisorFactory createAdvisorFactory(AopContext aopContext,
            FactoriesContext factoriesContext, FactoryContext factoryContext) {
        String factoryName = factoryContext.getFactoryName();

        // filter aspect
        if(factoriesContext.matchFactory(factoryName) == false) 
            return null;

        // create AdvisorFactory
        return new DefaultAdvisorFactory(aopContext, factoryContext);
    }


    @Override
    public Map<String, List<? extends AdvisorSpec>> getAdvisorSpecs() {
        return this.advisorFactoryMap.values().stream()
                .flatMap( e -> 
                    e.getAdvisorSpecs().entrySet().stream() )
                .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );
    }


    @Override
    public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule) {
        Map<MethodDescription, List<Advisor>> methodAdvisorMap = new HashMap<>();
        // collect advisors per method
        for(AdvisorFactory advisorFactory : advisorFactoryMap.values()) {
            for(Entry<? extends MethodDescription, List<? extends Advisor>> entry : advisorFactory
                    .getAdvisors(typeDescription, joinpointClassLoader, javaModule).entrySet()) {
                methodAdvisorMap
                .computeIfAbsent(entry.getKey(), key -> new ArrayList<>() )
                .addAll(entry.getValue());
            }
        }

        return new HashMap<MethodDescription, List<? extends Advisor>>(methodAdvisorMap);
    }

    @Override
    public void close() throws IOException {
        for(Closeable closeable : advisorFactoryMap.values()) {
            closeable.close();
        }

        this.factoriesContext.close();
    }
}
