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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.aspectory.classloader.AspectClassLoader;
import io.gemini.aop.matcher.Pattern;
import io.gemini.aop.matcher.Pattern.Parser;
import io.gemini.aop.matcher.StringMatcherFactory;
import io.gemini.aop.matcher.TypeMatcherFactory;
import io.gemini.aspectj.weaver.world.TypeWorld;
import io.gemini.aspectj.weaver.world.TypeWorldFactory;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigViews;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.pool.AspectTypePool;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.PlaceholderHelper;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.BooleanMatcher;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.CacheProvider;
import net.bytebuddy.utility.JavaModule;

public class AspectoryContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectoryContext.class);

    private static final String ASPECTORY_INTERNAL_PROPERTIES = "META-INF/aspectory-internal.properties";

    private static final Set<ClassLoader> SYSTEM_CLASSLOADERS;

    private static final String ASPECTORY_JOINPOINT_TYPES = "aop.aspectory.joinpointTypes";
    private static final String ASPECTORY_JOINPOINT_RESOURCES = "aop.aspectory.joinpointResources";

    private static final String ASPECTORY_MATCHER_MATCH_JOINPOINT_KEY = "aop.aspectory.matchJoinpoint";

    private static final String ASPECTORY_INCLUDED_CLASS_LOADERS_KEY = "aop.aspectory.includedClassLoaders";
    private static final String ASPECTORY_EXCLUDED_CLASS_LOADERS_KEY = "aop.aspectory.excludedClassLoaders";

    private static final String ASPECTORY_INCLUDED_TYPE_PATTERNS_KEY = "aop.aspectory.includedTypePatterns";
    private static final String ASPECTORY_EXCLUDED_TYPE_PATTERNS_KEY = "aop.aspectory.excludedTypePatterns";

    private static final String ASPECTORY_INCLUDED_ASPECTS_KEY = "aop.aspectory.includedAspects";
    private static final String ASPECTORY_EXCLUDED_ASPECTS_KEY = "aop.aspectory.excludedAspects";

    private static final String ASPECTORY_DEFAULT_MATCHING_CLASS_LOADERS_KEY = "aop.aspectory.defaultMatchingClassLoaders";


    private static final String AOP_CONTEXT_OBJECT = "aopContext";
    private static final String OBJECT_FACTORY_OBJECT = "objectFactory";


    private final AopContext aopContext;
    private final AspectoriesContext aspectoriesContext;

    private final String aspectoryName;
    private final URL[] aspectoryResourceURLs;

    private final AspectClassLoader aspectClassLoader;

    private final ConfigView configView;
    private final PlaceholderHelper placeholderHelper;


    private final StringMatcherFactory stringMatcherFactory;
    private TypeMatcherFactory typeMatcherFactory;


    private ElementMatcher<String> joinpointTypesMatcher;
    private ElementMatcher<String> joinpointResourcesMatcher;


    private boolean matchJoinpoint;

    private ElementMatcher<String> includedClassLoadersMatcher;
    private ElementMatcher<String> excludedClassLoadersMatcher;

    private Collection<Pattern> excludedTypePatterns;
    private Collection<Pattern> includedTypePatterns;

    private ElementMatcher<String> includedAspectsMatcher;
    private ElementMatcher<String> excludedAspectsMatcher;


    private ElementMatcher<String> defaultClassLoaderMatcher;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictJoinpointClassLoaders;


    private final ClassScanner classScanner;

    private final ObjectFactory objectFactory;
    private final TypePool aspectTypePool;


    private final TypeWorldFactory typeWorldFactory;
    private final ConcurrentMap<ClassLoader, AspectContext> aspectContextMap;


    static {
        SYSTEM_CLASSLOADERS = new HashSet<>();
        SYSTEM_CLASSLOADERS.add(ClassLoaderUtils.BOOTSTRAP_CLASSLOADER);

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        while (classLoader != null) {
            SYSTEM_CLASSLOADERS.add(classLoader);
            classLoader = classLoader.getParent();
        }
    }


    public AspectoryContext(AopContext aopContext, 
            AspectoriesContext aspectoriesContext,
            String aspectoryName, URL[] aspectoryResourceURLs) {
        long startedAt = System.nanoTime();
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating AspectoryContext '{}'", aspectoryName);


        // 1.check input arguments and initialize properties
        this.aopContext = aopContext;
        this.aspectoriesContext = aspectoriesContext;

        this.aspectoryName = aspectoryName;

        this.aspectoryResourceURLs = aspectoryResourceURLs;

        this.aspectClassLoader = new AspectClassLoader.WithThreadContextCL(
                aspectoryName, 
                aspectoryResourceURLs, 
                aopContext.getAopClassLoader());


        // 2.load settings
        // create configView
        this.configView = createConfigView(aopContext, aspectClassLoader);
        this.placeholderHelper = new PlaceholderHelper.Builder().build(configView);

        this.stringMatcherFactory = new StringMatcherFactory();
        this.typeMatcherFactory = new TypeMatcherFactory(aopContext.getTypePoolFactory(), aopContext.getTypeWorldFactory());

        // load Aspectory settings
        this.loadSettings(aspectoriesContext, configView);


        // 3.create properties
        // create classScanner and objectFactory
        this.classScanner = this.createClassScanner(this.aopContext);
        this.objectFactory = this.createObjectFactory(aspectClassLoader, this.classScanner);
        this.aspectTypePool = this.aopContext.getTypePoolFactory().createExplicitTypePool(this.aspectClassLoader, null);

        this.typeWorldFactory = new TypeWorldFactory.Prototype();
        this.aspectContextMap = new ConcurrentReferenceHashMap<>();


        if(LOGGER.isDebugEnabled())
            LOGGER.debug("$Took '{}' seconds to create AspectoryContext '{}'", 
                    (System.nanoTime() - startedAt) / 1e9, aspectoryName);
    }

    protected ConfigView createConfigView(AopContext aopContext, AspectClassLoader aspectClassLoader) {
        Map<String, String> userDefinedConfigs = new LinkedHashMap<>();

        String userDefinedConfigLocation = getUserDefinedConfigLocation(aopContext);
        String userDefinedConfig = null;
        if(aspectClassLoader.getResource(userDefinedConfigLocation) != null) {
            userDefinedConfigs.put(userDefinedConfigLocation, aspectoryName);
            userDefinedConfig = userDefinedConfigLocation;
        }

        String internalConfig = aspectClassLoader.getResource(ASPECTORY_INTERNAL_PROPERTIES) == null ? null : ASPECTORY_INTERNAL_PROPERTIES;
        ConfigView configView = ConfigViews.createConfigView(aopContext.getConfigView(), aspectClassLoader, 
                internalConfig, 
                userDefinedConfigs);

        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("Created ConfigView for Aspectory '{}' with settings, \n"
                    + "  InternalConfigLoc: {} \n  UserDefinedConfigLoc: {} \n",
                    aspectoryName, internalConfig, userDefinedConfig);

        return configView;
    }

    private String getUserDefinedConfigLocation(AopContext aopContext) {
        return "aspectory" + (aopContext.isDefaultProfile() ? "" : "-" + aopContext.getActiveProfile()) + ".properties";
    }

    private void loadSettings(AspectoriesContext aspectoriesContext, ConfigView configView) {
        {
            Set<String> joinpointTypes = configView.getAsStringSet(ASPECTORY_JOINPOINT_TYPES, Collections.emptySet());

            List<Pattern> joinpointTypePatterns = Parser.parsePatterns(joinpointTypes);
            this.joinpointTypesMatcher = stringMatcherFactory.createStringMatcher(
                    ASPECTORY_JOINPOINT_TYPES, joinpointTypePatterns, true, false);

            this.aspectClassLoader.setJoinpointTypeMatcher(joinpointTypesMatcher);


            Set<String> joinpointResources = configView.getAsStringSet(ASPECTORY_JOINPOINT_RESOURCES, Collections.emptySet());

            List<Pattern> mergedResourcePatterns = new ArrayList<>(joinpointTypePatterns.size() + joinpointResources.size());
            mergedResourcePatterns.addAll( Parser.parsePatterns(joinpointTypes, true) );
            mergedResourcePatterns.addAll( Parser.parsePatterns(joinpointResources) );
            this.joinpointResourcesMatcher = stringMatcherFactory.createStringMatcher(
                    ASPECTORY_JOINPOINT_RESOURCES, mergedResourcePatterns, true, false);

            this.aspectClassLoader.setJoinpointResourceMatcher(joinpointResourcesMatcher);
        }

        {
            this.matchJoinpoint = configView.getAsBoolean(ASPECTORY_MATCHER_MATCH_JOINPOINT_KEY, true);
            if(matchJoinpoint == false) {
                LOGGER.warn("WARNING! Setting '{}' is false for aspect app '{}', and switched off aspect weaving. \n", ASPECTORY_MATCHER_MATCH_JOINPOINT_KEY, this.aspectoryName);
            }
        }

        {
            Set<String> includedClassLoaders = configView.getAsStringSet(ASPECTORY_INCLUDED_CLASS_LOADERS_KEY, Collections.emptySet());

            if(includedClassLoaders.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        includedClassLoaders.size(), ASPECTORY_INCLUDED_CLASS_LOADERS_KEY, aspectoryName,
                        StringUtils.join(includedClassLoaders, "\n  ")
                );

            this.includedClassLoadersMatcher = stringMatcherFactory.createStringMatcher(
                    AspectoryContext.ASPECTORY_INCLUDED_CLASS_LOADERS_KEY,
                    Parser.parsePatterns(includedClassLoaders), 
                    false, false );
        }

        {
            Set<String> excludedClassLoaders = configView.getAsStringSet(ASPECTORY_EXCLUDED_CLASS_LOADERS_KEY, Collections.emptySet());

            if(excludedClassLoaders.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        excludedClassLoaders.size(), ASPECTORY_EXCLUDED_CLASS_LOADERS_KEY, aspectoryName,
                        StringUtils.join(excludedClassLoaders, "\n  ")
                );

            this.excludedClassLoadersMatcher = stringMatcherFactory.createStringMatcher(
                    AspectoryContext.ASPECTORY_EXCLUDED_CLASS_LOADERS_KEY,
                    Parser.parsePatterns(excludedClassLoaders), 
                    true, false );
        }

        {
            Set<String> includedTypePatterns = configView.getAsStringSet(ASPECTORY_INCLUDED_TYPE_PATTERNS_KEY, Collections.emptySet());

            if(includedTypePatterns.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        includedTypePatterns.size(), ASPECTORY_INCLUDED_TYPE_PATTERNS_KEY, aspectoryName,
                        StringUtils.join(includedTypePatterns, "\n  ")
                );

            this.includedTypePatterns = typeMatcherFactory.validateTypePatterns(
                    ASPECTORY_INCLUDED_TYPE_PATTERNS_KEY,
                    Parser.parsePatterns( includedTypePatterns ), 
                    false, 
                    aspectClassLoader, 
                    null, 
                    placeholderHelper );
        }

        {
            Set<String> excludedTypePatterns = configView.getAsStringSet(ASPECTORY_EXCLUDED_TYPE_PATTERNS_KEY, Collections.emptySet());

            if(excludedTypePatterns.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        excludedTypePatterns.size(), ASPECTORY_EXCLUDED_TYPE_PATTERNS_KEY, aspectoryName,
                        StringUtils.join(excludedTypePatterns, "\n  ")
                );

            this.excludedTypePatterns = typeMatcherFactory.validateTypePatterns(
                    ASPECTORY_EXCLUDED_TYPE_PATTERNS_KEY,
                    Parser.parsePatterns( excludedTypePatterns ), 
                    true, 
                    aspectClassLoader, 
                    null, 
                    placeholderHelper );
        }

        {
            Set<String> includedAspects = configView.getAsStringSet(ASPECTORY_INCLUDED_ASPECTS_KEY, Collections.emptySet());

            if(includedAspects.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        includedAspects.size(), ASPECTORY_INCLUDED_ASPECTS_KEY, aspectoryName,
                        StringUtils.join(includedAspects, "\n  ")
                );

            this.includedAspectsMatcher = CollectionUtils.isEmpty(includedAspects) 
                    ? BooleanMatcher.of(true)
                    : this.stringMatcherFactory.createStringMatcher(
                            ASPECTORY_INCLUDED_ASPECTS_KEY,
                            Parser.parsePatterns(includedAspects), 
                            false, false );
        }

        {
            Set<String> excludedAspects = configView.getAsStringSet(ASPECTORY_EXCLUDED_ASPECTS_KEY, Collections.emptySet());

            if(excludedAspects.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        excludedAspects.size(), ASPECTORY_EXCLUDED_ASPECTS_KEY, aspectoryName,
                        StringUtils.join(excludedAspects, "\n  ")
                );


            this.excludedAspectsMatcher = CollectionUtils.isEmpty(excludedAspects) 
                    ? BooleanMatcher.of(false)
                    : stringMatcherFactory.createStringMatcher(
                            ASPECTORY_EXCLUDED_ASPECTS_KEY,
                            Parser.parsePatterns(excludedAspects), 
                            true, false );
        }

        {
            // load and merge aspectories settings
            Set<String> mergedClassLoaders = new LinkedHashSet<>();
            mergedClassLoaders.addAll(aspectoriesContext.getDefaultMatchingClassLoaders());
            mergedClassLoaders.addAll(configView.getAsStringSet(ASPECTORY_DEFAULT_MATCHING_CLASS_LOADERS_KEY, Collections.emptySet()) );

            this.defaultClassLoaderMatcher = stringMatcherFactory.createStringMatcher(
                    AspectoriesContext.ASPECTORIES_DEFAULT_MATCHING_CLASS_LOADERS_KEY + ", " + ASPECTORY_DEFAULT_MATCHING_CLASS_LOADERS_KEY,
                    Parser.parsePatterns(mergedClassLoaders), 
                    true, false);
        }

        {
            // load and merge aspectories settings
            boolean shareAspectClassLoader = configView.getAsBoolean("aop.aspectory.shareAspectClassLoader", false);
            this.shareAspectClassLoader = shareAspectClassLoader && aspectoriesContext.isShareAspectClassLoader();

            List<Set<String>> conflictJoinpointClassLoaders = new ArrayList<>();
            conflictJoinpointClassLoaders.addAll(
                    aspectoriesContext.parseConflictJoinpointClassLoaders(
                            configView.getAsString("aop.aspectory.conflictJoinpointClassLoaders", "") ) );
            conflictJoinpointClassLoaders.addAll( aspectoriesContext.getConflictJoinpointClassLoaders() );  // merge settings in weaverContext
            this.conflictJoinpointClassLoaders = conflictJoinpointClassLoaders;
        }
    }

    private ClassScanner createClassScanner(AopContext aopContext) {
        ClassScanner aopClassScanner = aopContext.getClassScanner();
        Assert.notNull(aopClassScanner, "'classScanner' must not be null.");

        // collect resourceUrls by parent ClassLoader and current appResource
        List<URL> resourceUrls = new ArrayList<>();
        resourceUrls.addAll( Arrays.asList(aopContext.getAopClassLoader().getURLs()) );
        resourceUrls.addAll( Arrays.asList(aspectoryResourceURLs) );

        // create ClassScanner
        return new ClassScanner.Builder()
                .classScanner( aopClassScanner )
                .filteredClasspathElementUrls(resourceUrls)
                .build();
    }

    private ObjectFactory createObjectFactory(AspectClassLoader aspectClassLoader, ClassScanner classScanner) {
        ObjectFactory objectFactory = new ObjectFactory.Builder()
                .classLoader(aspectClassLoader)
                .classScanner(classScanner)
                .build(false);

        objectFactory.registerSingleton(AOP_CONTEXT_OBJECT, aopContext);
        objectFactory.registerSingleton(OBJECT_FACTORY_OBJECT, objectFactory);

        return objectFactory;
    }


    public String getAspectoryName() {
        return aspectoryName;
    }

    public AspectoriesContext getAspectoriesContext() {
        return aspectoriesContext;
    }

    public AspectClassLoader getAspectClassLoader() {
        return this.aspectClassLoader;
    }

    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }


    public boolean isMatchJoinpoint() {
        return matchJoinpoint;
    }

    public ElementMatcher<String> getIncludedClassLoadersMatcher() {
        return includedClassLoadersMatcher;
    }

    public ElementMatcher<String> getExcludedClassLoadersMatcher() {
        return excludedClassLoadersMatcher;
    }


    public ElementMatcher<TypeDescription> createIncludedTypesMatcher(ClassLoader joinpointClassLoader, JavaModule javaModule) {
        return typeMatcherFactory.createTypeMatcher(
                ASPECTORY_INCLUDED_TYPE_PATTERNS_KEY, 
                includedTypePatterns, 
                false, 
                joinpointClassLoader, 
                javaModule, 
                placeholderHelper ); 
    }

    public ElementMatcher<TypeDescription> createExcludedTypesMatcher(ClassLoader joinpointClassLoader, JavaModule javaModule) {
        return typeMatcherFactory.createTypeMatcher(
                ASPECTORY_EXCLUDED_TYPE_PATTERNS_KEY, 
                excludedTypePatterns, 
                true, 
                joinpointClassLoader, 
                javaModule, 
                placeholderHelper ); 
    }


    public ElementMatcher<String> getIncludedAspectsMatcher() {
        return includedAspectsMatcher;
    }

    public ElementMatcher<String> getExcludedAspectsMatcher() {
        return excludedAspectsMatcher;
    }


    public ClassScanner getClassScanner() {
        return this.classScanner;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public TypePool getAspectTypePool() {
        return aspectTypePool;
    }


    public AspectContext createAspectContext(ClassLoader joinpointClassLoader, JavaModule javaModule) {
        return createAspectContext(joinpointClassLoader, javaModule, false);
    }

    @SuppressWarnings("resource")
    public AspectContext createAspectContext(ClassLoader joinpointClassLoader, JavaModule javaModule, boolean validateContext) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(joinpointClassLoader);

        boolean sharedMode = useSharedAspectClassLoader(cacheKey);
        if(sharedMode == true) {
            if(this.aspectContextMap.containsKey(cacheKey) == false) {
                this.aspectContextMap.computeIfAbsent(
                        cacheKey, 
                        key -> doCreateAspectContext(joinpointClassLoader, javaModule, sharedMode, validateContext)
                );
            }
        } else {
            AspectContext aspectContext = doCreateAspectContext(joinpointClassLoader, javaModule, sharedMode, validateContext);
            // memory leak?
            this.aspectContextMap.put(cacheKey, aspectContext);  // overwrite existing AspectContext
        }

        return this.aspectContextMap.get(cacheKey);
    }

    private boolean useSharedAspectClassLoader(ClassLoader joinpointClassLoader) {
        // 1.use existing AspectClassLoader
        if(aspectContextMap.containsKey(joinpointClassLoader) == true)
            return true;


        // 2.used shared AspectClassLoader for system ClassLoaders
        if(SYSTEM_CLASSLOADERS.contains(joinpointClassLoader) == true)
            return true;


        // 3.check shareAspectClassLoader flag
        if(shareAspectClassLoader == false) 
            return false;


        // 4.check potentially class loading conflict
        // exist ClassLoader is same instance of the joinpointClassLoader
        Class<? extends ClassLoader> classLoaderClass = joinpointClassLoader.getClass();
        for(ClassLoader existingCL : aspectContextMap.keySet()) {
            if(existingCL.getClass() == classLoaderClass)
                return false;
        }

        // exist ClassLoader might conflict with the joinpintClassLoader 
        String joinpointCLClassName = ClassLoaderUtils.getClassLoaderName(joinpointClassLoader);
        List<Set<String>> conflictJoinpointClassLoaderList = conflictJoinpointClassLoaders.stream()
                .filter( classLoaders -> classLoaders.contains(joinpointCLClassName) )
                .collect( Collectors.toList() );

        for(ClassLoader existingCL : aspectContextMap.keySet()) {
            String existingCLClassName = ClassLoaderUtils.getClassLoaderName(existingCL);

            for(Set<String> classLoaders : conflictJoinpointClassLoaderList) {
                if(classLoaders.contains(existingCLClassName))
                    return false;
            }
        }


        // 5.no conflict, used shared AspectClassLoader
        return true;
    }

    protected AspectContext doCreateAspectContext(ClassLoader joinpointClassLoader, JavaModule javaModule, 
            boolean sharedMode, boolean validateContext) {
        AspectClassLoader aspectClassLoader = this.aspectClassLoader;
        ObjectFactory objectFactory = this.objectFactory;
        // create AspectClassLoader & objectFactory per ClassLoader
        if(sharedMode == false) {
            aspectClassLoader = new AspectClassLoader.WithJoinpointCL(
                    aspectoryName, 
                    aspectoryResourceURLs, 
                    aopContext.getAopClassLoader(),
                    joinpointClassLoader);

            aspectClassLoader.setJoinpointTypeMatcher(joinpointTypesMatcher);
            aspectClassLoader.setJoinpointResourceMatcher(joinpointResourcesMatcher);

            objectFactory = createObjectFactory(aspectClassLoader, classScanner);
        }

        // create typePool per ClassLoader
        AspectTypePool typePool = new AspectTypePool(
                new CacheProvider.Simple(),
                aspectTypePool,
                this.aopContext.getTypePoolFactory().getExplicitTypePool(joinpointClassLoader)
        );
        typePool.setJoinpointTypeMatcher(joinpointTypesMatcher);

        TypeWorld typeWorld = typeWorldFactory.createTypeWorld(
                joinpointClassLoader, javaModule,
                typePool, placeholderHelper);

        return new AspectContext(this,
                ClassLoaderUtils.getClassLoaderName(joinpointClassLoader), javaModule,
                aspectClassLoader, objectFactory, 
                typePool, typeWorld,
                defaultClassLoaderMatcher,
                validateContext);
    }


    @Override
    public String toString() {
        return aspectoryName;
    }

    @Override
    public void close() throws IOException {
        for(Closeable closeable : this.aspectContextMap.values()) {
            closeable.close();
       };

        this.objectFactory.close();
        this.aspectTypePool.clear();
    }
}
