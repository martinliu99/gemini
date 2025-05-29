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
import io.gemini.aop.factory.support.AdvisorRepositoryResolver;
import io.gemini.aop.factory.support.AdvisorSpecScanner;
import io.gemini.aop.matcher.Pattern.Parser;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.aop.matcher.StringMatcherFactory;
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
class FactoriesContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FactoriesContext.class);

    private static final String FACTORIES_INCLUDED_FACTORIES_KEY = "aop.factories.includedFactories";
    private static final String FACTORIES_EXCLUDED_FACTORIES_KEY = "aop.factories.excludedFactories";

    static final String FACTORIES_DEFAULT_MATCHING_CLASS_LOADERS_KEY = "aop.factories.defaultMatchingClassLoaders";


    private final AopContext aopContext;


    // global advisor factory settings
    private final StringMatcherFactory factoriesMatcherFactory;

    private ElementMatcher<String> includedFactoriesMatcher;
    private ElementMatcher<String> excludedFactoriesMatcher;


    private Set<String> defaultMatchingClassLoaders;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictJoinpointClassLoaders;

    private boolean asmAutoCompute = false;


    private final List<AdvisorSpecScanner<AdvisorSpec>> advisorSpecScanners;
    private final List<AdvisorRepositoryResolver<AdvisorSpec, AdvisorSpec>> advisorRepositoryResolvers;

    private final Map<String /* FactoryName */, FactoryContext> factoryContextMap;


    @SuppressWarnings("unchecked")
    public FactoriesContext(AopContext aopContext) {
        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;

        // 1.load global factory settings
        this.factoriesMatcherFactory = new StringMatcherFactory();

        this.loadSettings(this.aopContext);


        // 2.initialize properties
        // load advisorSpecScanners & advisorRepositoryResolver
        ObjectFactory objectFactory = aopContext.getObjectFactory();
        this.advisorSpecScanners = new ArrayList<>();
        for(AdvisorSpecScanner<AdvisorSpec> advisorSpecScanner : objectFactory.createObjectsImplementing(AdvisorSpecScanner.class)) {
            this.advisorSpecScanners.add(advisorSpecScanner);
        }

        this.advisorRepositoryResolvers = new ArrayList<>();
        for(AdvisorRepositoryResolver<AdvisorSpec, AdvisorSpec> advisorRepositoryResolver : 
                objectFactory.createObjectsImplementing(AdvisorRepositoryResolver.class)) {
            advisorRepositoryResolvers.add(advisorRepositoryResolver);
        }

        this.factoryContextMap = createFactoryContextMap(aopContext);
    }

    private void loadSettings(AopContext aopContext) {
        ConfigView configView = aopContext.getConfigView();

        // load global advisor factory settings
        {
            Set<String> includedFactories = configView.getAsStringSet(FACTORIES_INCLUDED_FACTORIES_KEY, Collections.emptySet());

            if(includedFactories.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n  {} \n", 
                        includedFactories.size(), FACTORIES_INCLUDED_FACTORIES_KEY,
                        StringUtils.join(includedFactories, "\n  ")
                );

            this.includedFactoriesMatcher = CollectionUtils.isEmpty(includedFactories) 
                    ? BooleanMatcher.of(true)
                    : this.factoriesMatcherFactory.createStringMatcher(
                            FACTORIES_INCLUDED_FACTORIES_KEY,
                            Parser.parsePatterns(includedFactories), 
                            false, false );
        }

        {
            Set<String> excludedFactories = configView.getAsStringSet(FACTORIES_EXCLUDED_FACTORIES_KEY, Collections.emptySet());

            if(excludedFactories.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n  {} \n", 
                        excludedFactories.size(), FACTORIES_EXCLUDED_FACTORIES_KEY,
                        StringUtils.join(excludedFactories, "\n  ")
                );

            this.excludedFactoriesMatcher = CollectionUtils.isEmpty(excludedFactories) 
                    ? BooleanMatcher.of(false)
                    : this.factoriesMatcherFactory.createStringMatcher(
                            FACTORIES_EXCLUDED_FACTORIES_KEY,
                            Parser.parsePatterns(excludedFactories), 
                            true, false );
        }

        {
            Set<String> classLoaders = new HashSet<>();
            classLoaders.addAll( 
                    configView.getAsStringSet(FACTORIES_DEFAULT_MATCHING_CLASS_LOADERS_KEY, Collections.emptySet()) );
            this.defaultMatchingClassLoaders = classLoaders;

            this.shareAspectClassLoader = configView.getAsBoolean("aop.factories.shareAspectClassLoader", false);
            this.conflictJoinpointClassLoaders = parseConflictJoinpointClassLoaders(
                    configView.getAsString("aop.factories.conflictJoinpointClassLoaders", "") );

            this.asmAutoCompute = configView.getAsBoolean("aop.factories.asmAutoCompute", false);
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


    private Map<String, FactoryContext> createFactoryContextMap(AopContext aopContext) {
        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("^Creating FactoryContexts.");
        }

        Map<String, URL[]> aspectAppResourceURLs = aopContext.getAspectAppResourceMap();
        try {
            return aopContext.getGlobalTaskExecutor().executeTasks(
                    aspectAppResourceURLs.entrySet().stream()
                        .collect( Collectors.toList() ),
                    entry -> new SimpleEntry<>(entry.getKey(), 
                            new FactoryContext(aopContext, FactoriesContext.this, entry.getKey(), entry.getValue() ) )
            )
           .collect( 
                   Collectors.toMap(Entry::getKey, Entry::getValue) );
        } finally {
            if(aopContext.getDiagnosticLevel().isSimpleEnabled())
                LOGGER.info("$Took '{}' seconds to create FactoryContexts.", (System.nanoTime() - startedAt) / 1e9);
        }
    }


    public ElementMatcher<String> getIncludedFactoriesMatcher() {
        return includedFactoriesMatcher;
    }

    public ElementMatcher<String> getExcludedFactoriesMatcher() {
        return excludedFactoriesMatcher;
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


    public List<AdvisorSpecScanner<AdvisorSpec>> getAdvisorSpecScanners() {
        return advisorSpecScanners;
    }

    public List<AdvisorRepositoryResolver<AdvisorSpec, AdvisorSpec>> getAdvisorRepositoryResolvers() {
        return advisorRepositoryResolvers;
    }

    public Map<String, FactoryContext> getFactoryContextMap() {
        return this.factoryContextMap;
    }


    @Override
    public void close() throws IOException {
        for(Closeable closeable : this.factoryContextMap.values()) {
            closeable.close();
        }
    }
}