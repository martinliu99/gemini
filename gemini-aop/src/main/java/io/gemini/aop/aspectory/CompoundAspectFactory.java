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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.Aspect;
import io.gemini.aop.AspectFactory;
import io.gemini.api.aspect.AspectSpec;
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
        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("^Creating CompoundAspectFactory.");

        try {
            return aopContext.getGlobalTaskExecutor().executeTasks( 
                    aspectoryContextMap.entrySet().stream()
                        .collect( Collectors.toList() ), 
                    entry -> 
                        new SimpleEntry<>( entry.getValue(), 
                                createAspectFactory(aopContext, aspectoriesContext, entry.getValue()) )
            )
            .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );
        } finally {
            if(aopContext.getDiagnosticLevel().isSimpleEnabled())
                LOGGER.info("$Took '{}' seconds to create CompoundAspectFactory. {}", (System.nanoTime() - startedAt) / 1e9);
        }
    }

    private DefaultAspectFactory createAspectFactory(AopContext aopContext,
            AspectoriesContext aspectoriesContext, AspectoryContext aspectoryContext) {
        String aspectoryName = aspectoryContext.getAspectoryName();

        // filter aspectory
        if(aspectoriesContext.getIncludedAspectoriesMatcher().matches(aspectoryName) == false) 
            return null;

        if(aspectoriesContext.getExcludedAspectoriesMatcher().matches(aspectoryName) == true) 
            return null;

        // create AspectFactory
        DefaultAspectFactory aspectFactory = new DefaultAspectFactory(aopContext, aspectoryContext);

        return aspectFactory;
    }


    @Override
    public Map<String, List<? extends AspectSpec>> getAspectSpecs() {
        return this.aspectFactoryMap.values().stream()
                .flatMap( e -> 
                    e.getAspectSpecs().entrySet().stream() )
                .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );
    }


    @Override
    public Map<? extends MethodDescription, List<? extends Aspect>> getAspects(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule) {
        Map<MethodDescription, List<Aspect>> methodAspectMap = new HashMap<>();
        // collect aspects per method
        for(AspectFactory aspectFactory : aspectFactoryMap.values()) {
            for(Entry<? extends MethodDescription, List<? extends Aspect>> entry : aspectFactory
                    .getAspects(typeDescription, joinpointClassLoader, javaModule).entrySet()) {
                methodAspectMap
                .computeIfAbsent(entry.getKey(), key -> new ArrayList<>() )
                .addAll(entry.getValue());
            }
        }

        return new HashMap<MethodDescription, List<? extends Aspect>>(methodAspectMap);
    }

    @Override
    public void close() throws IOException {
        for(Closeable closeable : aspectFactoryMap.values()) {
            closeable.close();
        }

        this.aspectoriesContext.close();
    }
}
