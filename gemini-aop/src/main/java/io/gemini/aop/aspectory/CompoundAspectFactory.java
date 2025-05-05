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
package io.gemini.aop.aspectory;

import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.Aspect;
import io.gemini.aop.AspectFactory;
import io.gemini.api.aspect.AspectSpec;
import io.gemini.core.OrderComparator;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;


class CompoundAspectFactory implements AspectFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompoundAspectFactory.class);


    private final AspectoriesContext aspectoriesContext;

    private final Map<AspectoryContext, DefaultAspectFactory> aspectFactoryMap;


    public CompoundAspectFactory(AopContext aopContext) {
        this.aspectoriesContext = new AspectoriesContext(aopContext);

        Map<String, AspectoryContext> aspectoryContextMap = aspectoriesContext.getAspectoryContextMap();

        this.aspectFactoryMap = createAspectFactoryMap(aopContext, aspectoriesContext, aspectoryContextMap);
    }

    private Map<AspectoryContext, DefaultAspectFactory> createAspectFactoryMap(AopContext aopContext, 
            AspectoriesContext aspectoriesContext,
            Map<String, AspectoryContext> aspectoryContextMap) {
        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("^Creating CompoundAspectFactory.");
        }
        ConcurrentMap<String, Double> creationTime = new ConcurrentHashMap<>();

        try {
            return aopContext.getGlobalTaskExecutor().executeTasks(
                    aspectoryContextMap.entrySet().stream()
                        .collect( Collectors.toList() ), 
                    contextEntries -> {
                        List<Entry<AspectoryContext, DefaultAspectFactory>> aspectFactories = new ArrayList<>(1);
                        for(Entry<String, AspectoryContext> entry : contextEntries) {
                            long startedAt2 = System.nanoTime();

                            String aspectoryName = entry.getKey();
                            if(LOGGER.isDebugEnabled()) {
                                LOGGER.debug("^Creating DefaultAspectFactory '{}'", aspectoryName);
                            }

                            // filter aspectory
                            if(aspectoriesContext.getIncludedAspectoriesMatcher().matches(aspectoryName) == false) 
                                continue;

                            if(aspectoriesContext.getExcludedAspectoriesMatcher().matches(aspectoryName) == true) 
                                continue;

                            // create AspectFactory
                            try {
                                AspectoryContext value = entry.getValue();
                                Entry<AspectoryContext, DefaultAspectFactory> aspectFactory = new SimpleEntry<>(value, 
                                        new DefaultAspectFactory(aopContext, value) );
                                aspectFactories.add(aspectFactory);
                            } finally {
                                double time = (System.nanoTime() - startedAt2) / 1e9;

                                if(LOGGER.isDebugEnabled())
                                    LOGGER.debug("$Took '{}' seconds to create AspectFactory '{}'", time, aspectoryName);
                                creationTime.put(aspectoryName, time);
                            }
                        }
                        return aspectFactories;
                    },
                    1
            ).stream()
            .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );
        } finally {
            if(aopContext.getDiagnosticLevel().isSimpleEnabled())
                LOGGER.info("$Took '{}' seconds to create CompoundAspectFactory. {}", 
                        (System.nanoTime() - startedAt) / 1e9,
                        creationTime.size() == 0 ? "" : creationTime.entrySet().stream().map( entry -> entry.getKey() + ": " + entry.getValue() ).collect( Collectors.joining("\n  ", "\n  ", "\n") ) );
        }
    }


    Map<String, List<AspectSpecHolder<AspectSpec>>> getAspectSpecHolders() {
        return this.aspectFactoryMap.values().stream()
                .flatMap( e -> 
                    e.getAspectSpecHolders().entrySet().stream() )
                .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );
    }


    @Override
    public Map<? extends MethodDescription, List<? extends Aspect>> getAspects(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule) {
        Map<MethodDescription, List<Aspect>> methodAspectMap = new HashMap<>();
        // collect aspects per method
        for(AspectFactory aspectFactory : aspectFactoryMap.values()) {
            for(Entry<? extends MethodDescription, List<? extends Aspect>> entry : aspectFactory.getAspects(typeDescription, joinpointClassLoader, javaModule).entrySet()) {
                MethodDescription method = entry.getKey();
                List<Aspect> aspects = methodAspectMap.get(method);
                if(aspects == null) {
                    aspects = new ArrayList<>();
                    methodAspectMap.put(method, aspects);
                }

                aspects.addAll(entry.getValue());
            }
        }

        // sort aspects
        Map<MethodDescription, List<? extends Aspect>> results = new HashMap<>();
        for(Entry<MethodDescription, List<Aspect>> entry : methodAspectMap.entrySet()) {
            List<Aspect> aspects = entry.getValue();
            OrderComparator.sort(aspects);
            results.put(entry.getKey(), aspects);
        }

        return results;
    }

    @Override
    public void close() throws IOException {
        for(Closeable closeable : aspectFactoryMap.values()) {
            closeable.close();
        }

        this.aspectoriesContext.close();
    }
}
