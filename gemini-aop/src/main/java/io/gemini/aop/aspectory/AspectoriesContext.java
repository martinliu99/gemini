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
import java.net.URL;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.aspectory.support.AspectRepositoryResolver;
import io.gemini.aop.aspectory.support.AspectSpecScanner;
import io.gemini.aop.matcher.Pattern.Parser;
import io.gemini.aop.matcher.StringMatcherFactory;
import io.gemini.api.aspect.AspectSpec;
import io.gemini.core.config.ConfigView;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.Assert;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.BooleanMatcher;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
class AspectoriesContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectoriesContext.class);

    private static final String ASPECTORIES_INCLUDED_ASPECTORIES_KEY = "aop.aspectories.includedAspectories";
    private static final String ASPECTORIES_EXCLUDED_ASPECTORIES_KEY = "aop.aspectories.excludedAspectories";

    static final String ASPECTORIES_DEFAULT_MATCHING_CLASS_LOADERS_KEY = "aop.aspectories.defaultMatchingClassLoaders";


    private final AopContext aopContext;


    // aspectorties settings
    private final StringMatcherFactory aspectoryMatcherFactory;

    private ElementMatcher<String> includedAspectoriesMatcher;
    private ElementMatcher<String> excludedAspectoriesMatcher;


    private Set<String> defaultMatchingClassLoaders;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictJoinpointClassLoaders;

    private boolean asmAutoCompute = false;


    private final List<AspectSpecScanner<AspectSpec>> aspectSpecScanners;
    private final List<AspectRepositoryResolver<AspectSpec, AspectSpec>> aspectRepositoryResolvers;

    private final Map<String /* AspectoryName */, AspectoryContext> aspectoryContextMap;


    @SuppressWarnings("unchecked")
    public AspectoriesContext(AopContext aopContext) {
        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;

        // 1.load aspectory settings
        this.aspectoryMatcherFactory = new StringMatcherFactory();

        this.loadSettings(this.aopContext);


        // 2.initialize properties
        // load aspectSpecScanners & aspectRepositoryResolver
        ObjectFactory objectFactory = aopContext.getObjectFactory();
        this.aspectSpecScanners = new ArrayList<>();
        for(AspectSpecScanner<AspectSpec> aspectSpecScanner : objectFactory.createObjectsImplementing(AspectSpecScanner.class)) {
            this.aspectSpecScanners.add(aspectSpecScanner);
        }

        this.aspectRepositoryResolvers = new ArrayList<>();
        for(AspectRepositoryResolver<AspectSpec, AspectSpec> aspectRepositoryResolver : 
                objectFactory.createObjectsImplementing(AspectRepositoryResolver.class)) {
            aspectRepositoryResolvers.add(aspectRepositoryResolver);
        }

        this.aspectoryContextMap = createAspectoryContextMap(aopContext);
    }

    private void loadSettings(AopContext aopContext) {
        ConfigView configView = aopContext.getConfigView();

        // load aspectory settings
        {
            Set<String> includedAspectories = configView.getAsStringSet(ASPECTORIES_INCLUDED_ASPECTORIES_KEY, Collections.emptySet());

            if(includedAspectories.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n  {} \n", 
                        includedAspectories.size(), ASPECTORIES_INCLUDED_ASPECTORIES_KEY,
                        StringUtils.join(includedAspectories, "\n  ")
                );

            this.includedAspectoriesMatcher = CollectionUtils.isEmpty(includedAspectories) 
                    ? BooleanMatcher.of(true)
                    : this.aspectoryMatcherFactory.createStringMatcher(
                            ASPECTORIES_INCLUDED_ASPECTORIES_KEY,
                            Parser.parsePatterns(includedAspectories), 
                            false, false );
        }

        {
            Set<String> excludedAspectories = configView.getAsStringSet(ASPECTORIES_EXCLUDED_ASPECTORIES_KEY, Collections.emptySet());

            if(excludedAspectories.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n  {} \n", 
                        excludedAspectories.size(), ASPECTORIES_EXCLUDED_ASPECTORIES_KEY,
                        StringUtils.join(excludedAspectories, "\n  ")
                );

            this.excludedAspectoriesMatcher = CollectionUtils.isEmpty(excludedAspectories) 
                    ? BooleanMatcher.of(false)
                    : this.aspectoryMatcherFactory.createStringMatcher(
                            ASPECTORIES_EXCLUDED_ASPECTORIES_KEY,
                            Parser.parsePatterns(excludedAspectories), 
                            true, false );
        }

        {
            Set<String> classLoaders = new HashSet<>();
            classLoaders.addAll( 
                    configView.getAsStringSet(ASPECTORIES_DEFAULT_MATCHING_CLASS_LOADERS_KEY, Collections.emptySet()) );
            this.defaultMatchingClassLoaders = classLoaders;

            this.shareAspectClassLoader = configView.getAsBoolean("aop.aspectories.shareAspectClassLoader", false);
            this.conflictJoinpointClassLoaders = parseConflictJoinpointClassLoaders(
                    configView.getAsString("aop.aspectories.conflictJoinpointClassLoaders", "") );

            this.asmAutoCompute = configView.getAsBoolean("aop.aspectories.asmAutoCompute", false);
        }
    }

    List<Set<String>> parseConflictJoinpointClassLoaders(String conflictJoinpointClassLoadersStr) {
        if(StringUtils.hasText(conflictJoinpointClassLoadersStr) == false)
            return Collections.emptyList();

        StringTokenizer groupSt = new StringTokenizer(conflictJoinpointClassLoadersStr, ";");

        List<Set<String>> groupList = new ArrayList<>(groupSt.countTokens());
        while(groupSt.hasMoreTokens()) {
            String classLoadersStr = groupSt.nextToken().trim();
            StringTokenizer classLoaderNameSt = new StringTokenizer(classLoadersStr, ",");

            Set<String> classLoaderNames = new LinkedHashSet<>(classLoaderNameSt.countTokens());
            groupList.add(classLoaderNames);
            while(classLoaderNameSt.hasMoreTokens()) {
                String classLoaderName = classLoaderNameSt.nextToken().trim();

                if(StringUtils.hasText(classLoadersStr))
                    classLoaderNames.add(classLoaderName);
            }
        }

        return groupList;
    }


    private Map<String, AspectoryContext> createAspectoryContextMap(AopContext aopContext) {
        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("^Creating AspectoryContexts.");
        }

        Map<String, URL[]> aspectsResourceURLs = aopContext.getAspectoryResourceMap();
        try {
            return aopContext.getGlobalTaskExecutor().executeTasks(
                    aspectsResourceURLs.entrySet().stream()
                        .collect( Collectors.toList() ),
                    entry -> new SimpleEntry<>(entry.getKey(), 
                            new AspectoryContext(aopContext, AspectoriesContext.this, entry.getKey(), entry.getValue() ) )
            )
           .collect( 
                   Collectors.toMap(Entry::getKey, Entry::getValue) );
        } finally {
            if(aopContext.getDiagnosticLevel().isSimpleEnabled())
                LOGGER.info("$Took '{}' seconds to create AspectoryContexts.", (System.nanoTime() - startedAt) / 1e9);
        }
    }


    public ElementMatcher<String> getIncludedAspectoriesMatcher() {
        return includedAspectoriesMatcher;
    }

    public ElementMatcher<String> getExcludedAspectoriesMatcher() {
        return excludedAspectoriesMatcher;
    }


    public Set<String> getDefaultMatchingClassLoaders() {
        return Collections.unmodifiableSet( defaultMatchingClassLoaders );
    }

    public boolean isShareAspectClassLoader() {
        return shareAspectClassLoader;
    }

    public List<Set<String>> getConflictJoinpointClassLoaders() {
        return Collections.unmodifiableList( conflictJoinpointClassLoaders );
    }

    public boolean isAsmAutoCompute() {
        return asmAutoCompute;
    }


    public List<AspectSpecScanner<AspectSpec>> getAspectSpecScanners() {
        return aspectSpecScanners;
    }

    public List<AspectRepositoryResolver<AspectSpec, AspectSpec>> getAspectRepositoryResolvers() {
        return aspectRepositoryResolvers;
    }

    public Map<String, AspectoryContext> getAspectoryContextMap() {
        return this.aspectoryContextMap;
    }


    @Override
    public void close() throws IOException {
        for(Closeable closeable : this.aspectoryContextMap.values()) {
            closeable.close();
        }
    }
}