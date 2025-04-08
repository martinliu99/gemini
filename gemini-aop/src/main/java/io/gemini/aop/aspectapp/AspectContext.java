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
package io.gemini.aop.aspectapp;


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.aspect.AspectSpec;
import io.gemini.aop.aspectapp.support.AspectRepositoryResolver;
import io.gemini.aop.aspectapp.support.AspectSpecScanner;
import io.gemini.aop.classloader.AspectClassLoader;
import io.gemini.aop.matcher.Pattern;
import io.gemini.aop.matcher.Pattern.Parser;
import io.gemini.aop.matcher.StringMatcherFactory;
import io.gemini.aop.support.AopSettingHelper;
import io.gemini.core.config.ConfigView;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.Closeable;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.object.Resources;
import io.gemini.core.util.Assert;
import io.gemini.core.util.IOUtils;
import io.gemini.core.util.OrderedProperties;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

public class AspectContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectContext.class);

    private static final String ASPECT_FACTORY_JOINPOINT_TYPES = "aop.aspectapp.joinpointTypes";
    private static final String ASPECT_FACTORY_JOINPOINT_RESOURCES = "aop.aspectapp.joinpointResources";

    public static final String ASPECT_FACTORY_INCLUDED_CLASS_LOADERS_KEY = "aop.aspectapp.includedClassLoaders";
    public static final String ASPECT_FACTORY_EXCLUDED_CLASS_LOADERS_KEY = "aop.aspectapp.excludedClassLoaders";

    public static final String ASPECT_FACTORY_INCLUDED_TYPE_PATTERNS_KEY = "aop.aspectapp.includedTypePatterns";
    public static final String ASPECT_FACTORY_EXCLUDED_TYPE_PATTERNS_KEY = "aop.aspectapp.excludedTypePatterns";

    public static final String ASPECT_FACTORY_INCLUDED_ASPECTS_KEY = "aop.aspectapp.includedAspects";
    public static final String ASPECT_FACTORY_EXCLUDED_ASPECTS_KEY = "aop.aspectapp.excludedAspects";

    public static final String ASPECT_FACTORY_DEFAULT_MATCHING_CLASS_LOADERS_KEY = "aop.aspectapp.defaultMatchingClassLoaders";


    private static final String ASPECTAPP_INTERNAL_PROPERTIES = "META-INF/aspectapp-internal.properties";
    private static final String ASPECTAPP_ASPECTAPP_PROPERTIES = "META-INF/aspectapp.properties";

    private static final String ASPECT_CONTEXT_OBJECT = "aspectContext";
    private static final String OBJECT_FACTORY_OBJECT = "objectFactory";

    private static final String JOINPOINT_MATCHER_MATCH_JOINPOINT_KEY = "aop.aspectapp.matchJoinpoint";


    private final AopContext aopContext;

    private final String appName;
    private final Resources appResources;

    private final AspectClassLoader aspectClassLoader;

    private final AopMetrics aopMetrics;


    private final ConfigView configView;
    private final PlaceholderHelper placeholderHelper;

    private boolean matchJoinpoint;

    private Set<String> joinpointTypes;
    private Set<String> joinpointResources;

    private Set<String> includedClassLoaders;
    private Set<String> excludedClassLoaders;

    private Set<String> includedTypePatterns;
    private Set<String> excludedTypePatterns;

    private Set<String> includedAspects;
    private Set<String> excludedAspects;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictJoinpointClassLoaders;


    private final ClassScanner classScanner;

    private final ObjectFactory aspectObjectFactory;
    private final TypePool aspectTypePool;


    private final List<AspectSpecScanner<AspectSpec>> aspectSpecScanners;
    private final List<AspectRepositoryResolver<AspectSpec, AspectSpec>> aspectRepositoryResolvers;

    private final StringMatcherFactory stringMatcherFactory;

    private final ElementMatcher<String> joinpointTypeMatcher;
    private final ElementMatcher<String> joinpointResourceMatcher;

    private final ElementMatcher<String> defaultClassLoaderMatcher;


    @SuppressWarnings("unchecked")
    public AspectContext(AopContext aopContext, 
            String appName, Resources appResources, 
            AspectClassLoader aspectClassLoader) {
        // 1.check input arguments and initialize properties
        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;

        Assert.hasText(appName, "'appName' must not be null");
        this.appName = appName;

        Assert.notNull(appResources, "'appResources' must not be null.");
        this.appResources = appResources;

        Assert.notNull(aspectClassLoader, "'aspectClassLoader' must not be null.");
        this.aspectClassLoader = aspectClassLoader;

        Assert.notNull(aopContext.getAopMetrics(), "'aopMetrics' must not be null.");
        this.aopMetrics = aopContext.getAopMetrics();


        // 2.load settings
        Map<String, String> builtinSettings = new LinkedHashMap<>();

        // load built-in settings from AspectApp config file
        Map<String, String> aspectAppDefaultSettings = new LinkedHashMap<>();
        this.loadBuiltinSettings(ASPECTAPP_ASPECTAPP_PROPERTIES, aspectClassLoader, true, builtinSettings, aspectAppDefaultSettings);

        // load built-in settings from internal config file
        Map<String, String> internalDefaultSettings = new LinkedHashMap<>();
        this.loadBuiltinSettings(ASPECTAPP_INTERNAL_PROPERTIES, aspectClassLoader, false, builtinSettings, internalDefaultSettings);

        String userDefinedConfigName = getUserDefinedConfigName(this.aopContext);

        // create configView
        this.configView = new ConfigView.Builder()
                .parent(aopContext.getConfigView())
                .configSource("BuiltinSettings", builtinSettings)
                .configSource(userDefinedConfigName, this.loadProperties(userDefinedConfigName, aspectClassLoader, false) )
                .configSource("AspectAppDefaultSettings", aspectAppDefaultSettings)
                .configSource("InternalDefaultSettings", internalDefaultSettings)
                .build();

        this.placeholderHelper = new PlaceholderHelper.Builder().build(configView);

        // load aspectapp settings
        this.loadSettings(aopContext, configView);


        // 3.create properties
        // create classScanner and objectFactory
        this.classScanner = this.createClassScanner(this.aopContext);
        this.aspectObjectFactory = this.createObjectFactory(appResources, aspectClassLoader, this.classScanner);
        this.aspectTypePool = this.aopContext.getTypePoolFactory().createExplicitTypePool(this.aspectClassLoader, null);

        // load aspectSpecScanners & aspectRepositoryResolver
        this.aspectSpecScanners = new ArrayList<>();
        for(AspectSpecScanner<AspectSpec> aspectSpecScanner : aspectObjectFactory.createObjectsImplementing(AspectSpecScanner.class)) {
            this.aspectSpecScanners.add(aspectSpecScanner);
        }

        this.aspectRepositoryResolvers = new ArrayList<>();
        for(AspectRepositoryResolver<AspectSpec, AspectSpec> aspectRepositoryResolver : 
                aspectObjectFactory.createObjectsImplementing(AspectRepositoryResolver.class)) {
            aspectRepositoryResolvers.add(aspectRepositoryResolver);
        }

        // create matchers
        this.stringMatcherFactory = new StringMatcherFactory();

        List<Pattern> joinpointTypePatterns = Parser.parsePatterns(joinpointTypes);
        this.joinpointTypeMatcher = stringMatcherFactory.createStringMatcher(
                ASPECT_FACTORY_JOINPOINT_TYPES, joinpointTypePatterns, true, false);
        this.aspectClassLoader.setJoinpointTypeMatcher(joinpointTypeMatcher);

        List<Pattern> mergedResourcePatterns = new ArrayList<>(joinpointTypePatterns.size() + joinpointResources.size());
        mergedResourcePatterns.addAll( Parser.parsePatterns(joinpointTypes, true) );
        mergedResourcePatterns.addAll( Parser.parsePatterns(joinpointResources) );
        this.joinpointResourceMatcher = stringMatcherFactory.createStringMatcher(
                ASPECT_FACTORY_JOINPOINT_RESOURCES, mergedResourcePatterns, true, false);

        this.aspectClassLoader.setJoinpointResourceMatcher(joinpointResourceMatcher);

        this.defaultClassLoaderMatcher = createDefaultClassLoaderMatcher(stringMatcherFactory, aopContext, configView);
    }

    private void loadBuiltinSettings(String propertiesFilename, AspectClassLoader classLoader, boolean ignoredException,
            Map<String, String> builtinSettings, Map<String, String> defaultSettings) {
        OrderedProperties settings = this.loadProperties(propertiesFilename, classLoader, ignoredException);

        for(String key: settings.stringPropertyNames()) {
            String value = settings.getProperty(key);

            // built-in or default setting
            if(key.startsWith(AopSettingHelper.BUILTIN_SETTING_PREFIX)) {
                builtinSettings.put(key.substring(1), value);
            } else {
                defaultSettings.put(key, value);
            }
        }
    }

    private OrderedProperties loadProperties(String propertiesFilename, ClassLoader classLoader, boolean ignoredException) {
        OrderedProperties properties = new OrderedProperties();
        URL url = classLoader.getResource(propertiesFilename);
        if(url == null) {
            if(ignoredException)
                LOGGER.info("Did not find resource file '{}' under '{}'.", propertiesFilename, appName);
            else
                LOGGER.warn("Did not find resource file '{}' under '{}'.", propertiesFilename, appName);

            return properties;
        }

        InputStream inStream = null;
        try {
            inStream = url.openStream();
            properties.load(inStream);
        } catch (IOException e) {
            if(ignoredException)
                LOGGER.info("Failed to load resource file '{}' into OrderedProperties under '{}'.", properties, appName);
            else
                LOGGER.warn("Failed to load resource file '{}' into OrderedProperties under '{}'.", properties, appName, e);
        } finally {
            IOUtils.closeQuietly(inStream);
        }

        return properties;
    }

    private String getUserDefinedConfigName(AopContext aopContext) {
        return "aspectapp" + (aopContext.isDefaultProfile() ? "" : "-" + aopContext.getActiveProfile()) + ".properties";
    }

    private void loadSettings(AopContext aopContext, ConfigView configView) {
        {
            this.matchJoinpoint = configView.getAsBoolean(JOINPOINT_MATCHER_MATCH_JOINPOINT_KEY, true);
            if(matchJoinpoint == false) {
                LOGGER.warn("WARNING! Setting '{}' is false for aspect app '{}', and switched off aspect weaving. \n", JOINPOINT_MATCHER_MATCH_JOINPOINT_KEY, this.appName);
            }

            this.joinpointTypes = configView.getAsStringSet(ASPECT_FACTORY_JOINPOINT_TYPES, Collections.emptySet());
            this.joinpointResources = configView.getAsStringSet(ASPECT_FACTORY_JOINPOINT_RESOURCES, new LinkedHashSet<>());

            {
                this.includedClassLoaders = configView.getAsStringSet(ASPECT_FACTORY_INCLUDED_CLASS_LOADERS_KEY, Collections.emptySet());

                if(includedClassLoaders.size() > 0) 
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                            includedClassLoaders.size(), ASPECT_FACTORY_INCLUDED_CLASS_LOADERS_KEY, appName,
                            includedClassLoaders.stream().collect( Collectors.joining("\n  ") ) );

                this.excludedClassLoaders = configView.getAsStringSet(ASPECT_FACTORY_EXCLUDED_CLASS_LOADERS_KEY, Collections.emptySet());

                if(excludedClassLoaders.size() > 0) 
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                            excludedClassLoaders.size(), ASPECT_FACTORY_EXCLUDED_CLASS_LOADERS_KEY, appName,
                            excludedClassLoaders.stream().collect( Collectors.joining("\n  ") ) );
            }

            {
                this.includedTypePatterns = configView.getAsStringSet(ASPECT_FACTORY_INCLUDED_TYPE_PATTERNS_KEY, Collections.emptySet());

                if(includedTypePatterns.size() > 0) 
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                            includedTypePatterns.size(), ASPECT_FACTORY_INCLUDED_TYPE_PATTERNS_KEY, appName,
                            includedTypePatterns.stream().collect( Collectors.joining("\n  ") ) );

                this.excludedTypePatterns = configView.getAsStringSet(ASPECT_FACTORY_EXCLUDED_TYPE_PATTERNS_KEY, Collections.emptySet());

                if(excludedTypePatterns.size() > 0)
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                            excludedTypePatterns.size(), ASPECT_FACTORY_EXCLUDED_TYPE_PATTERNS_KEY, appName,
                            excludedTypePatterns.stream().collect( Collectors.joining("\n  ") ) );
            }

            {
                this.includedAspects = configView.getAsStringSet(ASPECT_FACTORY_INCLUDED_ASPECTS_KEY, Collections.emptySet());

                if(includedAspects.size() > 0)
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                            includedAspects.size(), ASPECT_FACTORY_INCLUDED_ASPECTS_KEY, appName,
                            includedAspects.stream().collect( Collectors.joining("\n  ") ) );

                this.excludedAspects = configView.getAsStringSet(ASPECT_FACTORY_EXCLUDED_ASPECTS_KEY, Collections.emptySet());

                if(excludedAspects.size() > 0)
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                            excludedAspects.size(), ASPECT_FACTORY_EXCLUDED_ASPECTS_KEY, appName,
                            excludedTypePatterns.stream().collect( Collectors.joining("\n  ") ) );
            }
        }

        {
            boolean shareAspectClassLoader = configView.getAsBoolean("aop.aspectapp.shareAspectClassLoader", false);
            this.shareAspectClassLoader = shareAspectClassLoader && aopContext.isShareAspectClassLoader();

            List<Set<String>> conflictJoinpointClassLoaders = new ArrayList<>();
            conflictJoinpointClassLoaders.addAll(
                    aopContext.parseConflictJoinpointClassLoaders(
                            configView.getAsString("aop.aspectapp.conflictJoinpointClassLoaders", "") ) );
            conflictJoinpointClassLoaders.addAll( aopContext.getConflictJoinpointClassLoaders() );  // merge settings in AopContext
            this.conflictJoinpointClassLoaders = conflictJoinpointClassLoaders;
        }
    }

    private ClassScanner createClassScanner(AopContext aopContext) {
        ClassScanner aopClassScanner = aopContext.getClassScanner();
        Assert.notNull(aopClassScanner, "'classScanner' must not be null.");

        // collect resourceUrls by parent ClassLoader and current appResource
        List<URL> resourceUrls = new ArrayList<>();
        resourceUrls.addAll( Arrays.asList(aopContext.getAgentClassLoader().getURLs()) );
        resourceUrls.addAll(appResources.getResourceUrlList());

        // create ClassScanner
        return new ClassScanner.Builder()
                .classScanner( aopClassScanner )
                .classpathElementUrls(resourceUrls)
                .build();
    }

    public ObjectFactory createObjectFactory(Resources appResources, 
            AspectClassLoader aspectClassLoader, ClassScanner classScanner) {
        ObjectFactory objectFactory = new ObjectFactory.Builder()
                .classLoader(aspectClassLoader)
                .classScanner(classScanner)
                .build(false);

        objectFactory.registerSingleton(ASPECT_CONTEXT_OBJECT, this);
        objectFactory.registerSingleton(OBJECT_FACTORY_OBJECT, objectFactory);

        return objectFactory;
    }

    private ElementMatcher<String> createDefaultClassLoaderMatcher(StringMatcherFactory stringMatcherFactory, 
            AopContext aopContext, ConfigView configView) {
        Set<String> mergedClassLoaders = new LinkedHashSet<>();
        mergedClassLoaders.addAll(aopContext.getGlobalMacthingClassLoaders());
        mergedClassLoaders.addAll(configView.getAsStringSet(ASPECT_FACTORY_DEFAULT_MATCHING_CLASS_LOADERS_KEY, Collections.emptySet()) );

        return stringMatcherFactory.createStringMatcher(
                AopContext.ASPECT_WEAVER_GLOBAL_MATCHING_CLASS_LOADERS_KEY + ", " + ASPECT_FACTORY_DEFAULT_MATCHING_CLASS_LOADERS_KEY,
                Parser.parsePatterns(mergedClassLoaders), true, false);
    }


    public AopContext getAopContext() {
        return aopContext;
    }

    public String getAppName() {
        return appName;
    }

    public Resources getAppResources() {
        return appResources;
    }

    public AspectClassLoader getAspectClassLoader() {
        return this.aspectClassLoader;
    }

    public AopMetrics getAopMetrics() {
        return this.aopMetrics;
    }

    public ConfigView getConfigView() {
        return configView;
    }

    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }


    public boolean isMatchJoinpoint() {
        return matchJoinpoint;
    }


    public Set<String> getIncludedClassLoaders() {
        return Collections.unmodifiableSet( includedClassLoaders );
    }

    public Set<String> getExcludedClassLoaders() {
        return Collections.unmodifiableSet( excludedClassLoaders );
    }

    public Set<String> getIncludedTypePatterns() {
        return Collections.unmodifiableSet( includedTypePatterns );
    }

    public Set<String> getExcludedTypePatterns() {
        return Collections.unmodifiableSet( excludedTypePatterns );
    }

    public Set<String> getIncludedAspects() {
        return Collections.unmodifiableSet( includedAspects );
    }

    public Set<String> getExcludedAspects() {
        return Collections.unmodifiableSet( excludedAspects );
    }


    public boolean isShareAspectClassLoader() {
        return shareAspectClassLoader;
    }

    public List<Set<String>> getConflictJoinpointClassLoaders() {
        return Collections.unmodifiableList( conflictJoinpointClassLoaders );
    }


    public ClassScanner getClassScanner() {
        return this.classScanner;
    }

    public ObjectFactory getObjectFactory() {
        return aspectObjectFactory;
    }

    public TypePool getAspectTypePool() {
        return aspectTypePool;
    }

    public List<AspectSpecScanner<AspectSpec>> getAspectSpecScanners() {
        return aspectSpecScanners;
    }

    public List<AspectRepositoryResolver<AspectSpec, AspectSpec>> getAspectRepositoryResolvers() {
        return aspectRepositoryResolvers;
    }

    public ElementMatcher<String> getJoinpointTypeMatcher() {
        return joinpointTypeMatcher;
    }

    public ElementMatcher<String> getJoinpointResourceMatcher() {
        return joinpointResourceMatcher;
    }

    public ElementMatcher<String> getDefaultClassLoaderMatcher() {
        return defaultClassLoaderMatcher;
    }

    @Override
    public String toString() {
        return appName;
    }

    @Override
    public void close() {
        this.aspectObjectFactory.close();
        this.aspectTypePool.clear();
    }
}
