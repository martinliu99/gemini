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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.Assert;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Aggregates all {@link FactoryContext} instances, one per discovered aspect application.
 * <p>
 * Loads global factory settings (shared ClassLoader flag, conflict ClassLoader groups,
 * enabled factory expressions) and creates a {@link FactoryContext} for each aspect app
 * found in the launcher's {@code aspectapps/} directory.
 * </p>
 *
 * @author   martin.liu
 */
class FactoriesContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FactoriesContext.class);

    private static final String FACTORIES_ENABLED_FACTORY_EXPRESSIONS_KEY = "aop.factories.enabledFactoryExpressions";


    private final AopContext aopContext;


    // global advisor factory settings
    private ElementMatcher<String> enabledFactoryMatcher;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictTargetClassLoaders;

    private boolean autoComputeAsm = false;


    private Map<String /* FactoryName */, FactoryContext> factoryContextMap;


    public FactoriesContext(AopContext aopContext) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating FactoriesContext, ");


        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;

        // 1.load global factory settings
        this.loadSettings(this.aopContext);


        // 2.initialize properties
        this.factoryContextMap = createFactoryContextMap(aopContext);


        if (LOGGER.isInfoEnabled()) {
            if (aopContext.getDiagnosticLevel().isDebugEnabled()) 
                LOGGER.info("$Took '{}' seconds to create FactoriesContext with FactoryContexts, {}", 
                        (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME,
                        StringUtils.join(factoryContextMap.keySet(), "\n  ", "\n  ", "\n")
                );
            else if (aopContext.getDiagnosticLevel().isSimpleEnabled()) 
                LOGGER.info("$Took '{}' seconds to create FactoriesContext.", 
                        (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME
                );
        }
    }

    private void loadSettings(AopContext aopContext) {
        ConfigView configView = aopContext.getConfigView();

        // load global advisor factory settings
        {
            Set<String> enabledFactoryExpressions = configView.getAsStringSet(
                    FACTORIES_ENABLED_FACTORY_EXPRESSIONS_KEY, Collections.emptySet());
            if (enabledFactoryExpressions.size() > 0) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n"
                            + "  {} \n", 
                            enabledFactoryExpressions.size(), FACTORIES_ENABLED_FACTORY_EXPRESSIONS_KEY,
                            StringUtils.join(enabledFactoryExpressions, "\n  ")
                    );

                this.enabledFactoryMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                        FACTORIES_ENABLED_FACTORY_EXPRESSIONS_KEY, enabledFactoryExpressions, ElementMatchers.none() );
            } else {
                this.enabledFactoryMatcher = ElementMatchers.any();
            }
        }

        {
            this.shareAspectClassLoader = configView.getAsBoolean("aop.factories.shareAspectClassLoader", false);
            this.conflictTargetClassLoaders = parseConflictTargetClassLoaders(
                    configView.getAsString("aop.factories.conflictTargetClassLoaders", "") );

            this.autoComputeAsm = configView.getAsBoolean("aop.factories.autoComputeAsm", false);
        }
    }

    List<Set<String>> parseConflictTargetClassLoaders(String conflictTargetClassLoadersStr) {
        if (StringUtils.hasText(conflictTargetClassLoadersStr) == false)
            return Collections.emptyList();

        StringTokenizer groupSt = new StringTokenizer(conflictTargetClassLoadersStr, ";");

        List<Set<String>> groupList = new ArrayList<>(groupSt.countTokens());
        while (groupSt.hasMoreTokens()) {
            String classLoadersStr = groupSt.nextToken().trim();
            StringTokenizer classLoaderNameSt = new StringTokenizer(classLoadersStr, ",");

            Set<String> classLoaderNames = new LinkedHashSet<>(classLoaderNameSt.countTokens());
            groupList.add(classLoaderNames);
            while (classLoaderNameSt.hasMoreTokens()) {
                String classLoaderName = classLoaderNameSt.nextToken().trim();

                if (StringUtils.hasText(classLoadersStr))
                    classLoaderNames.add(classLoaderName);
            }
        }

        return groupList;
    }

    private Map<String, FactoryContext> createFactoryContextMap(AopContext aopContext) {
        Map<String, URL[]> aspectAppResourceURLs = aopContext.getAspectAppResourceMap();

        Map<String, FactoryContext> factoryContexts = new LinkedHashMap<>(aspectAppResourceURLs.size());
        for (Entry<String, URL[]> entry : aspectAppResourceURLs.entrySet()) {
            String factoryName = entry.getKey();

            // filter aspect
            if (enabledFactoryMatcher.matches(factoryName) == false) 
                return null;

            factoryContexts.put(factoryName, 
                    new FactoryContext(aopContext, FactoriesContext.this, factoryName, entry.getValue() ) );
        }

        return factoryContexts;
    }


    /**
     * Returns {@code true} if a single {@link io.gemini.aop.factory.classloader.AspectClassLoader}
     * instance should be shared across multiple target class loaders when there is no conflict.
     *
     * @return {@code true} if aspect class loader sharing is enabled
     */
    public boolean isShareAspectClassLoader() {
        return shareAspectClassLoader;
    }

    /**
     * Returns the list of conflict groups — sets of class loader names that may load the same classes
     * and therefore must not share an {@link io.gemini.aop.factory.classloader.AspectClassLoader}.
     *
     * @return unmodifiable list of conflict class loader name sets
     */
    public List<Set<String>> getConflictTargetClassLoaders() {
        return Collections.unmodifiableList( conflictTargetClassLoaders );
    }

    /**
     * Returns {@code true} if ASM frame computation should be performed automatically
     * when generating advice bytecode.
     *
     * @return {@code true} if auto ASM computation is enabled
     */
    public boolean isAutoComputeAsm() {
        return autoComputeAsm;
    }

    /**
     * Returns an unmodifiable view of the factory context map, keyed by aspect application name.
     *
     * @return map of factory name to {@link FactoryContext}
     */
    public Map<String, FactoryContext> getFactoryContextMap() {
        return Collections.unmodifiableMap( this.factoryContextMap );
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        for (Closeable closeable : this.factoryContextMap.values()) {
            closeable.close();
        }
    }
}