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
import io.gemini.aop.factory.classloader.AspectClassLoader;
import io.gemini.aop.factory.classloader.AspectTypePool;
import io.gemini.aop.matcher.Pattern;
import io.gemini.aop.matcher.Pattern.Parser;
import io.gemini.aop.matcher.StringMatcherFactory;
import io.gemini.aop.matcher.TypeMatcherFactory;
import io.gemini.api.aop.condition.Condition;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.aspectj.weaver.TypeWorldFactory;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigViews;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.ObjectFactory;
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

public class FactoryContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FactoryContext.class);

    private static final String FACTORY_INTERNAL_PROPERTIES = "META-INF/factory-internal.properties";

    private static final Set<ClassLoader> SYSTEM_CLASSLOADERS;

    private static final String FACTORY_JOINPOINT_TYPES = "aop.factory.joinpointTypes";
    private static final String FACTORY_JOINPOINT_RESOURCES = "aop.factory.joinpointResources";

    private static final String FACTORY_MATCHER_MATCH_JOINPOINT_KEY = "aop.factory.matchJoinpoint";

    private static final String FACTORY_INCLUDED_CLASS_LOADERS_KEY = "aop.factory.includedClassLoaders";
    private static final String FACTORY_EXCLUDED_CLASS_LOADERS_KEY = "aop.factory.excludedClassLoaders";

    private static final String FACTORY_INCLUDED_TYPE_PATTERNS_KEY = "aop.factory.includedTypePatterns";
    private static final String FACTORY_EXCLUDED_TYPE_PATTERNS_KEY = "aop.factory.excludedTypePatterns";

    private static final String FACTORY_INCLUDED_ADVISORS_KEY = "aop.factory.includedAdvisors";
    private static final String FACTORY_EXCLUDED_ADVISORS_KEY = "aop.factory.excludedAdvisors";

    private static final String FACTORY_DEFAULT_MATCHING_CLASS_LOADERS_KEY = "aop.factory.defaultMatchingClassLoaders";


    private static final String AOP_CONTEXT_OBJECT = "aopContext";
    private static final String OBJECT_FACTORY_OBJECT = "objectFactory";


    private final AopContext aopContext;
    private final FactoriesContext factoriesContext;

    private final String factoryName;
    private final URL[] factoryResourceURLs;

    private final AspectClassLoader classLoader;

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

    private ElementMatcher<String> includedAdvisorsMatcher;
    private ElementMatcher<String> excludedAdvisorsMatcher;

    private Condition defaultCondition;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictJoinpointClassLoaders;


    private final ClassScanner classScanner;

    private final ObjectFactory objectFactory;
    private final TypePool typePool;


    private final TypeWorldFactory typeWorldFactory;
    private final ConcurrentMap<ClassLoader, AdvisorContext> advisorContextMap;


    static {
        SYSTEM_CLASSLOADERS = new HashSet<>();
        SYSTEM_CLASSLOADERS.add(ClassLoaderUtils.BOOTSTRAP_CLASSLOADER);

        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        while (classLoader != null) {
            SYSTEM_CLASSLOADERS.add(classLoader);
            classLoader = classLoader.getParent();
        }
    }


    public FactoryContext(AopContext aopContext, 
            FactoriesContext factoriesContext,
            String factoryName, URL[] factoryResourceURLs) {
        long startedAt = System.nanoTime();
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating FactoryContext '{}'", factoryName);


        // 1.check input arguments and initialize properties
        this.aopContext = aopContext;
        this.factoriesContext = factoriesContext;

        this.factoryName = factoryName;

        this.factoryResourceURLs = factoryResourceURLs;

        this.classLoader = new AspectClassLoader.WithThreadContextCL(
                factoryName, 
                factoryResourceURLs, 
                aopContext.getAopClassLoader());


        // 2.load settings
        // create configView
        this.configView = createConfigView(aopContext, classLoader);
        this.placeholderHelper = new PlaceholderHelper.Builder().build(configView);

        this.stringMatcherFactory = new StringMatcherFactory();
        this.typeMatcherFactory = new TypeMatcherFactory(aopContext.getTypePoolFactory(), aopContext.getTypeWorldFactory());

        // load factory settings
        this.loadSettings(factoriesContext, configView);


        // 3.create properties
        // create classScanner and objectFactory
        this.classScanner = this.createClassScanner(this.aopContext);
        this.objectFactory = this.createObjectFactory(classLoader, this.classScanner);
        this.typePool = this.aopContext.getTypePoolFactory().createExplicitTypePool(this.classLoader, null);

        this.typeWorldFactory = new TypeWorldFactory.Prototype();
        this.advisorContextMap = new ConcurrentReferenceHashMap<>();


        if(LOGGER.isDebugEnabled())
            LOGGER.debug("$Took '{}' seconds to create FactoryContext '{}'", 
                    (System.nanoTime() - startedAt) / 1e9, factoryName);
    }

    protected ConfigView createConfigView(AopContext aopContext, AspectClassLoader classLoader) {
        Map<String, String> userDefinedConfigs = new LinkedHashMap<>();

        String userDefinedConfigLocation = getUserDefinedConfigLocation(aopContext);
        String userDefinedConfig = null;
        if(classLoader.getResource(userDefinedConfigLocation) != null) {
            userDefinedConfigs.put(userDefinedConfigLocation, factoryName);
            userDefinedConfig = userDefinedConfigLocation;
        }

        String internalConfig = classLoader.getResource(FACTORY_INTERNAL_PROPERTIES) == null ? null : FACTORY_INTERNAL_PROPERTIES;
        ConfigView configView = ConfigViews.createConfigView(aopContext.getConfigView(), classLoader, 
                internalConfig, 
                userDefinedConfigs);

        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("Created ConfigView for Factory '{}' with settings, \n"
                    + "  InternalConfigLoc: {} \n  UserDefinedConfigLoc: {} \n",
                    factoryName, internalConfig, userDefinedConfig);

        return configView;
    }

    private String getUserDefinedConfigLocation(AopContext aopContext) {
        return "factory" + (aopContext.isDefaultProfile() ? "" : "-" + aopContext.getActiveProfile()) + ".properties";
    }

    private void loadSettings(FactoriesContext factoriesContext, ConfigView configView) {
        {
            Set<String> joinpointTypes = configView.getAsStringSet(FACTORY_JOINPOINT_TYPES, Collections.emptySet());

            List<Pattern> joinpointTypePatterns = Parser.parsePatterns(joinpointTypes);
            this.joinpointTypesMatcher = stringMatcherFactory.createStringMatcher(
                    FACTORY_JOINPOINT_TYPES, joinpointTypePatterns, true, false);

            this.classLoader.setJoinpointTypeMatcher(joinpointTypesMatcher);


            Set<String> joinpointResources = configView.getAsStringSet(FACTORY_JOINPOINT_RESOURCES, Collections.emptySet());

            List<Pattern> mergedResourcePatterns = new ArrayList<>(joinpointTypePatterns.size() + joinpointResources.size());
            mergedResourcePatterns.addAll( Parser.parsePatterns(joinpointTypes, true) );
            mergedResourcePatterns.addAll( Parser.parsePatterns(joinpointResources) );
            this.joinpointResourcesMatcher = stringMatcherFactory.createStringMatcher(
                    FACTORY_JOINPOINT_RESOURCES, mergedResourcePatterns, true, false);

            this.classLoader.setJoinpointResourceMatcher(joinpointResourcesMatcher);
        }

        {
            this.matchJoinpoint = configView.getAsBoolean(FACTORY_MATCHER_MATCH_JOINPOINT_KEY, true);
            if(matchJoinpoint == false) {
                LOGGER.warn("WARNING! Setting '{}' is false for factory '{}', and switched off aop weaving. \n", FACTORY_MATCHER_MATCH_JOINPOINT_KEY, this.factoryName);
            }
        }

        {
            Set<String> includedClassLoaders = configView.getAsStringSet(FACTORY_INCLUDED_CLASS_LOADERS_KEY, Collections.emptySet());

            if(includedClassLoaders.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        includedClassLoaders.size(), FACTORY_INCLUDED_CLASS_LOADERS_KEY, factoryName,
                        StringUtils.join(includedClassLoaders, "\n  ")
                );

            this.includedClassLoadersMatcher = stringMatcherFactory.createStringMatcher(
                    FactoryContext.FACTORY_INCLUDED_CLASS_LOADERS_KEY,
                    Parser.parsePatterns(includedClassLoaders), 
                    false, false );
        }

        {
            Set<String> excludedClassLoaders = configView.getAsStringSet(FACTORY_EXCLUDED_CLASS_LOADERS_KEY, Collections.emptySet());

            if(excludedClassLoaders.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        excludedClassLoaders.size(), FACTORY_EXCLUDED_CLASS_LOADERS_KEY, factoryName,
                        StringUtils.join(excludedClassLoaders, "\n  ")
                );

            this.excludedClassLoadersMatcher = stringMatcherFactory.createStringMatcher(
                    FactoryContext.FACTORY_EXCLUDED_CLASS_LOADERS_KEY,
                    Parser.parsePatterns(excludedClassLoaders), 
                    true, false );
        }

        {
            Set<String> includedTypePatterns = configView.getAsStringSet(FACTORY_INCLUDED_TYPE_PATTERNS_KEY, Collections.emptySet());

            if(includedTypePatterns.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        includedTypePatterns.size(), FACTORY_INCLUDED_TYPE_PATTERNS_KEY, factoryName,
                        StringUtils.join(includedTypePatterns, "\n  ")
                );

            this.includedTypePatterns = typeMatcherFactory.validateTypePatterns(
                    FACTORY_INCLUDED_TYPE_PATTERNS_KEY,
                    Parser.parsePatterns( includedTypePatterns ), 
                    false, 
                    classLoader, 
                    null, 
                    placeholderHelper );
        }

        {
            Set<String> excludedTypePatterns = configView.getAsStringSet(FACTORY_EXCLUDED_TYPE_PATTERNS_KEY, Collections.emptySet());

            if(excludedTypePatterns.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        excludedTypePatterns.size(), FACTORY_EXCLUDED_TYPE_PATTERNS_KEY, factoryName,
                        StringUtils.join(excludedTypePatterns, "\n  ")
                );

            this.excludedTypePatterns = typeMatcherFactory.validateTypePatterns(
                    FACTORY_EXCLUDED_TYPE_PATTERNS_KEY,
                    Parser.parsePatterns( excludedTypePatterns ), 
                    true, 
                    classLoader, 
                    null, 
                    placeholderHelper );
        }

        {
            Set<String> includedAdvisors = configView.getAsStringSet(FACTORY_INCLUDED_ADVISORS_KEY, Collections.emptySet());

            if(includedAdvisors.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        includedAdvisors.size(), FACTORY_INCLUDED_ADVISORS_KEY, factoryName,
                        StringUtils.join(includedAdvisors, "\n  ")
                );

            this.includedAdvisorsMatcher = CollectionUtils.isEmpty(includedAdvisors) 
                    ? BooleanMatcher.of(true)
                    : this.stringMatcherFactory.createStringMatcher(
                            FACTORY_INCLUDED_ADVISORS_KEY,
                            Parser.parsePatterns(includedAdvisors), 
                            false, false );
        }

        {
            Set<String> excludedAdvisors = configView.getAsStringSet(FACTORY_EXCLUDED_ADVISORS_KEY, Collections.emptySet());

            if(excludedAdvisors.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        excludedAdvisors.size(), FACTORY_EXCLUDED_ADVISORS_KEY, factoryName,
                        StringUtils.join(excludedAdvisors, "\n  ")
                );


            this.excludedAdvisorsMatcher = CollectionUtils.isEmpty(excludedAdvisors) 
                    ? BooleanMatcher.of(false)
                    : stringMatcherFactory.createStringMatcher(
                            FACTORY_EXCLUDED_ADVISORS_KEY,
                            Parser.parsePatterns(excludedAdvisors), 
                            true, false );
        }

        {
            // load and merge global factory settings
            Set<String> mergedClassLoaders = new LinkedHashSet<>();
            mergedClassLoaders.addAll(factoriesContext.getDefaultMatchingClassLoaders());
            mergedClassLoaders.addAll(configView.getAsStringSet(FACTORY_DEFAULT_MATCHING_CLASS_LOADERS_KEY, Collections.emptySet()) );

            ElementMatcher<String> defaultClassLoaderMatcher = stringMatcherFactory.createStringMatcher(
                    FactoriesContext.FACTORIES_DEFAULT_MATCHING_CLASS_LOADERS_KEY + ", " + FACTORY_DEFAULT_MATCHING_CLASS_LOADERS_KEY,
                    Parser.parsePatterns(mergedClassLoaders), 
                    true, false);

            this.defaultCondition = new Condition() {

                @Override
                public boolean match(ConditionContext context) {
                    return defaultClassLoaderMatcher.matches(context.getClassLoaderName());
                }
            };
        }

        {
            // load and merge global factory settings
            boolean shareAspectClassLoader = configView.getAsBoolean("aop.factory.shareAspectClassLoader", false);
            this.shareAspectClassLoader = shareAspectClassLoader && factoriesContext.isShareAspectClassLoader();

            List<Set<String>> conflictJoinpointClassLoaders = new ArrayList<>();
            conflictJoinpointClassLoaders.addAll(
                    factoriesContext.parseConflictJoinpointClassLoaders(
                            configView.getAsString("aop.factory.conflictJoinpointClassLoaders", "") ) );
            conflictJoinpointClassLoaders.addAll( factoriesContext.getConflictJoinpointClassLoaders() );  // merge settings in weaverContext
            this.conflictJoinpointClassLoaders = conflictJoinpointClassLoaders;
        }
    }

    private ClassScanner createClassScanner(AopContext aopContext) {
        ClassScanner aopClassScanner = aopContext.getClassScanner();
        Assert.notNull(aopClassScanner, "'classScanner' must not be null.");

        // collect resourceUrls by parent ClassLoader and current appResource
        List<URL> resourceUrls = new ArrayList<>();
        resourceUrls.addAll( Arrays.asList(aopContext.getAopClassLoader().getURLs()) );
        resourceUrls.addAll( Arrays.asList(factoryResourceURLs) );

        // create ClassScanner
        return new ClassScanner.Builder()
                .classScanner( aopClassScanner )
                .filteredClasspathElementUrls(resourceUrls)
                .build();
    }

    private ObjectFactory createObjectFactory(AspectClassLoader classLoader, ClassScanner classScanner) {
        ObjectFactory objectFactory = new ObjectFactory.Builder()
                .classLoader(classLoader)
                .classScanner(classScanner)
                .build(false);

        objectFactory.registerSingleton(AOP_CONTEXT_OBJECT, aopContext);
        objectFactory.registerSingleton(OBJECT_FACTORY_OBJECT, objectFactory);

        return objectFactory;
    }


    public String getFactoryName() {
        return factoryName;
    }

    public FactoriesContext getFactoriesContext() {
        return factoriesContext;
    }

    public AspectClassLoader getClassLoader() {
        return this.classLoader;
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
                FACTORY_INCLUDED_TYPE_PATTERNS_KEY, 
                includedTypePatterns, 
                false, 
                joinpointClassLoader, 
                javaModule, 
                placeholderHelper ); 
    }

    public ElementMatcher<TypeDescription> createExcludedTypesMatcher(ClassLoader joinpointClassLoader, JavaModule javaModule) {
        return typeMatcherFactory.createTypeMatcher(
                FACTORY_EXCLUDED_TYPE_PATTERNS_KEY, 
                excludedTypePatterns, 
                true, 
                joinpointClassLoader, 
                javaModule, 
                placeholderHelper ); 
    }


    public ElementMatcher<String> getIncludedAdvisorsMatcher() {
        return includedAdvisorsMatcher;
    }

    public ElementMatcher<String> getExcludedAdvisorsMatcher() {
        return excludedAdvisorsMatcher;
    }


    public Condition getDefaultCondition() {
        return defaultCondition;
    }

    public ClassScanner getClassScanner() {
        return this.classScanner;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public TypePool getTypePool() {
        return typePool;
    }


    public AdvisorContext createAdvisorContext(ClassLoader joinpointClassLoader, JavaModule javaModule) {
        return createAdvisorContext(joinpointClassLoader, javaModule, false);
    }

    @SuppressWarnings("resource")
    public AdvisorContext createAdvisorContext(ClassLoader joinpointClassLoader, JavaModule javaModule, boolean validateContext) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(joinpointClassLoader);

        boolean sharedMode = useSharedAspectClassLoader(cacheKey);
        if(sharedMode == true) {
            if(this.advisorContextMap.containsKey(cacheKey) == false) {
                this.advisorContextMap.computeIfAbsent(
                        cacheKey, 
                        key -> doCreateAdvisorContext(joinpointClassLoader, javaModule, sharedMode, validateContext)
                );
            }
        } else {
            AdvisorContext advisorContext = doCreateAdvisorContext(joinpointClassLoader, javaModule, sharedMode, validateContext);
            // memory leak?
            this.advisorContextMap.put(cacheKey, advisorContext);  // overwrite existing AdvisorContext
        }

        return this.advisorContextMap.get(cacheKey);
    }

    private boolean useSharedAspectClassLoader(ClassLoader joinpointClassLoader) {
        // 1.use existing AspectClassLoader
        if(advisorContextMap.containsKey(joinpointClassLoader) == true)
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
        for(ClassLoader existingCL : advisorContextMap.keySet()) {
            if(existingCL.getClass() == classLoaderClass)
                return false;
        }

        // exist ClassLoader might conflict with the joinpintClassLoader 
        String joinpointCLClassName = ClassLoaderUtils.getClassLoaderName(joinpointClassLoader);
        List<Set<String>> conflictJoinpointClassLoaderList = conflictJoinpointClassLoaders.stream()
                .filter( classLoaders -> classLoaders.contains(joinpointCLClassName) )
                .collect( Collectors.toList() );

        for(ClassLoader existingCL : advisorContextMap.keySet()) {
            String existingCLClassName = ClassLoaderUtils.getClassLoaderName(existingCL);

            for(Set<String> classLoaders : conflictJoinpointClassLoaderList) {
                if(classLoaders.contains(existingCLClassName))
                    return false;
            }
        }


        // 5.no conflict, used shared AspectClassLoader
        return true;
    }

    protected AdvisorContext doCreateAdvisorContext(ClassLoader joinpointClassLoader, JavaModule javaModule, 
            boolean sharedMode, boolean validateContext) {
        AspectClassLoader aspectClassLoader = this.classLoader;
        ObjectFactory objectFactory = this.objectFactory;
        // create AspectClassLoader & objectFactory per ClassLoader
        if(sharedMode == false) {
            aspectClassLoader = new AspectClassLoader.WithJoinpointCL(
                    factoryName, 
                    factoryResourceURLs, 
                    aopContext.getAopClassLoader(),
                    joinpointClassLoader);

            aspectClassLoader.setJoinpointTypeMatcher(joinpointTypesMatcher);
            aspectClassLoader.setJoinpointResourceMatcher(joinpointResourcesMatcher);

            objectFactory = createObjectFactory(aspectClassLoader, classScanner);
        }

        // create typePool per ClassLoader
        AspectTypePool aspectTypePool = new AspectTypePool(
                new CacheProvider.Simple(),
                typePool,
                this.aopContext.getTypePoolFactory().createExplicitTypePool(joinpointClassLoader, javaModule)
        );
        aspectTypePool.setJoinpointTypeMatcher(joinpointTypesMatcher);

        TypeWorld aspectTypeWorld = typeWorldFactory.createTypeWorld(
                joinpointClassLoader, javaModule,
                aspectTypePool, placeholderHelper);

        return new AdvisorContext(this,
                ClassLoaderUtils.getClassLoaderName(joinpointClassLoader), javaModule,
                aspectClassLoader, objectFactory, 
                aspectTypePool, aspectTypeWorld,
                validateContext);
    }


    @Override
    public String toString() {
        return factoryName;
    }

    @Override
    public void close() throws IOException {
        for(Closeable closeable : this.advisorContextMap.values()) {
            closeable.close();
       };

        this.objectFactory.close();
        this.typePool.clear();
    }
}
