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
package io.gemini.aop.aspectapp.support;

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
import io.gemini.aop.aspect.Aspect;
import io.gemini.aop.aspect.AspectSpec;
import io.gemini.aop.aspectapp.AspectContext;
import io.gemini.aop.aspectapp.AspectFactory;
import io.gemini.aop.aspectapp.AspectSpecHolder;
import io.gemini.aop.matcher.Pattern.Parser;
import io.gemini.aop.matcher.StringMatcherFactory;
import io.gemini.core.OrderComparator;
import io.gemini.core.util.Assert;
import io.gemini.core.util.CollectionUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.BooleanMatcher;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

public class CompoundAspectFactory implements AspectFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompoundAspectFactory.class);

    private final AopContext aopContext;
    private final StringMatcherFactory aspectAppMatcherFactory;

    private final Map<AspectContext, DefaultAspectFactory> aspectFactoryMap;


    public CompoundAspectFactory(AopContext aopContext) {
        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;

        this.aspectAppMatcherFactory = new StringMatcherFactory();

        Map<String, AspectContext> aspectContextMap = aopContext.getAspectContextMap();

        this.aspectFactoryMap = createAspectFactoryMap(aopContext, aspectContextMap);
    }

    private Map<AspectContext, DefaultAspectFactory> createAspectFactoryMap(AopContext aopContext, 
            Map<String, AspectContext> aspectContextMap) {
        long startedAt = System.nanoTime();
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating CompoundAspectFactory.");
        }
        ConcurrentMap<String, Double> creationTime = new ConcurrentHashMap<>();

        try {
            ElementMatcher<String> includedAspectAppMatcher = CollectionUtils.isEmpty(aopContext.getIncludedAspectApps()) 
                    ? BooleanMatcher.of(true)
                    : this.aspectAppMatcherFactory.createStringMatcher(
                            AopContext.ASPECT_WEAVER_INCLUDED_ASPECT_APPS_KEY,
                            Parser.parsePatterns(
                                    aopContext.getIncludedAspectApps() ), false, false );
            ElementMatcher<String> excludedAspectAppMatcher = CollectionUtils.isEmpty(aopContext.getExcludedAspectApps()) 
                    ? BooleanMatcher.of(false)
                    : this.aspectAppMatcherFactory.createStringMatcher(
                            AopContext.ASPECT_WEAVER_EXCLUDED_ASPECT_APPS_KEY,
                            Parser.parsePatterns(
                                    aopContext.getExcludedAspectApps() ), true, false );

            return this.aopContext.getGlobalTaskExecutor().executeTasks(
                    aspectContextMap.entrySet().stream()
                        .collect( Collectors.toList() ), 
                    contextEntries -> {
                        List<Entry<AspectContext, DefaultAspectFactory>> aspectFactories = new ArrayList<>(1);
                        for(Entry<String, AspectContext> entry : contextEntries) {
                            long startedAt2 = System.nanoTime();

                            String appName = entry.getKey();
                            if(LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Creating DefaultAspectFactory '{}'", appName);
                            }

                            // filter aspectApp
                            if(includedAspectAppMatcher.matches(appName) == false) continue;
                            if(excludedAspectAppMatcher.matches(appName) == true) continue;

                            // create AspectFactory
                            try {
                                AspectContext value = entry.getValue();
                                Entry<AspectContext, DefaultAspectFactory> aspectFactory = new SimpleEntry<>(value, new DefaultAspectFactory(aopContext, value) );
                                aspectFactories.add(aspectFactory);
                            } finally {
                                double time = (System.nanoTime() - startedAt2) / 1e9;

                                if(LOGGER.isDebugEnabled())
                                    LOGGER.debug("Took '{}' seconds to create AspectFactory '{}'", time, appName);
                                creationTime.put(appName, time);
                            }
                        }
                        return aspectFactories;
                    },
                    1
            ).stream()
            .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );

//            return aspectContextMap.entrySet().stream()
//                    .filter(e -> {
//                        String appName = e.getKey();
//
//                        if(includedAspectAppMatcher.matches(appName) == true)
//                            return true;
//
//                        if(excludedAspectAppMatcher.matches(appName) == true)
//                            return false;
//
//                        return true;
//                    } )
//                    .map(e -> {
//                        long startedAt2 = System.nanoTime();
//
//                        String appName = e.getKey();
//                        if(LOGGER.isDebugEnabled()) {
//                            LOGGER.debug("Creating DefaultAspectFactory '{}'", appName);
//                        }
//
//                        try {
//                            AspectContext value = e.getValue();
//                            return new SimpleEntry<>(value, new DefaultAspectFactory(aopContext, value) );
//                        } finally {
//                            double time = (System.nanoTime() - startedAt2) / 1e9;
//
//                            if(LOGGER.isDebugEnabled())
//                                LOGGER.debug("Took '{}' seconds to create AspectFactory '{}'", time, appName);
//                            creationTime.put(appName, time);
//                        }
//                    } )
//                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        } finally {
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug("Took '{}' seconds to create CompoundAspectFactory.", (System.nanoTime() - startedAt) / 1e9);
                for(Entry<String, Double> entry : creationTime.entrySet()) {
                    LOGGER.debug("  '{}': '{}'", entry.getKey(), entry.getValue());
                }
            }
        }
    }



    @Override
    public Map<String, List<AspectSpecHolder<AspectSpec>>> getAspectSpecHolders() {
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

//        return aspectFactoryMap.values().stream()
//                .flatMap(
//                        aspectFactory -> aspectFactory.getAspects(typeDescription, joinpointClassLoader, javaModule).entrySet().stream() )
//                .collect(
//                        Collectors.groupingBy(Entry::getKey, Collectors.mapping(Entry::getValue, Collectors.toList())) )
//                .entrySet().stream()
//                .map(e -> { 
//                    List<Aspect> aspects = e.getValue().stream()
//                            .flatMap(a -> a.stream())
//                            .collect(Collectors.toList());
//                    OrderComparator.sort(aspects);
//
//                    return new SimpleEntry<MethodDescription, List<Aspect>>(e.getKey(), aspects);
//                } )
//                .collect(
//                        Collectors.toMap(Entry::getKey, Entry::getValue)
//                    )
//                ;
    }


    @Override
    public void close() {
        for(DefaultAspectFactory aspectFactory : aspectFactoryMap.values()) {
            aspectFactory.close();
        }
    }
}
