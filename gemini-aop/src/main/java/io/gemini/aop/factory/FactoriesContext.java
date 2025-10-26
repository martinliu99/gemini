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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.Assert;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
class FactoriesContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FactoriesContext.class);

    private static final String FACTORIES_ENABLED_FACTORY_EXPRESSIONS_KEY = "aop.factories.enabledFactoryExpressions";

    static final String FACTORIES_DEFAULT_ACCEPTABLE_CLASS_LOADER_EXPRESSIONS_KEY = "aop.factories.defaultAcceptableClassLoaderExpressions";


    private final AopContext aopContext;


    // global advisor factory settings
    private ElementMatcher<String> enabledFactoryMatcher;


    private Set<String> defaultAcceptableClassLoaderExpressions;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictJoinpointClassLoaders;

    private boolean asmAutoCompute = false;


    private Map<String /* FactoryName */, FactoryContext> factoryContextMap;


    public FactoriesContext(AopContext aopContext) {
        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;

        // 1.load global factory settings
        this.loadSettings(this.aopContext);


        // 2.initialize properties
        this.factoryContextMap = createFactoryContextMap(aopContext);
    }

    private void loadSettings(AopContext aopContext) {
        ConfigView configView = aopContext.getConfigView();

        // load global advisor factory settings
        {
            Set<String> enabledFactoryExpressions = configView.getAsStringSet(FACTORIES_ENABLED_FACTORY_EXPRESSIONS_KEY, Collections.emptySet());
            if(enabledFactoryExpressions.size() > 0) {
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n  {} \n", 
                        enabledFactoryExpressions.size(), FACTORIES_ENABLED_FACTORY_EXPRESSIONS_KEY,
                        StringUtils.join(enabledFactoryExpressions, "\n  ")
                );

                this.enabledFactoryMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(FACTORIES_ENABLED_FACTORY_EXPRESSIONS_KEY, enabledFactoryExpressions );
            } else {
                this.enabledFactoryMatcher = ElementMatchers.any();
            }
        }

        {
            this.defaultAcceptableClassLoaderExpressions = configView.getAsStringSet(
                    FACTORIES_DEFAULT_ACCEPTABLE_CLASS_LOADER_EXPRESSIONS_KEY, Collections.emptySet());

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

        try {
            Map<String, URL[]> aspectAppResourceURLs = aopContext.getAspectAppResourceMap();

            Map<String, FactoryContext> factoryContexts = new HashMap<>(aspectAppResourceURLs.size());
            for(Entry<String, URL[]> entry : aspectAppResourceURLs.entrySet()) {
                factoryContexts.put(entry.getKey(), 
                        new FactoryContext(aopContext, FactoriesContext.this, entry.getKey(), entry.getValue() ) );
            }

            return factoryContexts;
        } finally {
            if(aopContext.getDiagnosticLevel().isSimpleEnabled())
                LOGGER.info("$Took '{}' seconds to create FactoryContexts.", (System.nanoTime() - startedAt) / 1e9);
        }
    }


    public boolean isEnabledFactory(String factoryName) {
        return enabledFactoryMatcher.matches(factoryName);
    }


    public Set<String> getDefaultAcceptableClassLoaderExpressions() {
        return Collections.unmodifiableSet( defaultAcceptableClassLoaderExpressions );
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