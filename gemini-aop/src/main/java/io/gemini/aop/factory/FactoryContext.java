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
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.api.aop.condition.Condition;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.aspectj.weaver.TypeWorldFactory;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigViews;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.pool.TypePoolFactory;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.PlaceholderHelper;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

public class FactoryContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FactoryContext.class);

    private static final String FACTORY_INTERNAL_PROPERTIES = "META-INF/factory-internal.properties";

    private static final Set<ClassLoader> SYSTEM_CLASSLOADERS;

    private static final String FACTORY_JOINPOINT_TYPE_EXPRS = "aop.factory.joinpointTypeExprs";
    private static final String FACTORY_JOINPOINT_RESOURCE_EXPRS = "aop.factory.joinpointResourceExprs";

    private static final String FACTORY_MATCHER_MATCH_JOINPOINT_KEY = "aop.factory.matchJoinpoint";

    private static final String FACTORY_INCLUDED_CLASS_LOADER_EXPRS_KEY = "aop.factory.includedClassLoaderExprs";
    private static final String FACTORY_EXCLUDED_CLASS_LOADER_EXPRS_KEY = "aop.factory.excludedClassLoaderExprs";

    private static final String FACTORY_INCLUDED_TYPE_EXPRS_KEY = "aop.factory.includedTypeExprs";
    private static final String FACTORY_EXCLUDED_TYPE_EXPRS_KEY = "aop.factory.excludedTypeExprs";

    private static final String FACTORY_INCLUDED_ADVISOR_EXPRS_KEY = "aop.factory.includedAdvisorExprs";
    private static final String FACTORY_EXCLUDED_ADVISOR_EXPRS_KEY = "aop.factory.excludedAdvisorExprs";

    private static final String FACTORY_DEFAULT_MATCHING_CLASS_LOADER_EXPRS_KEY = "aop.factory.defaultMatchingClassLoaderExprs";


    private static final String AOP_CONTEXT_OBJECT = "aopContext";
    private static final String OBJECT_FACTORY_OBJECT = "objectFactory";


    private final AopContext aopContext;
    private final FactoriesContext factoriesContext;

    private final String factoryName;
    private final URL[] factoryResourceURLs;

    private final AspectClassLoader classLoader;

    private final ConfigView configView;
    private final PlaceholderHelper placeholderHelper;


    private final TypePoolFactory typePoolFactory;
    private final TypeWorldFactory typeWorldFactory;


    private ElementMatcher<String> joinpointTypesMatcher;
    private ElementMatcher<String> joinpointResourcesMatcher;


    private boolean matchJoinpoint;

    private ElementMatcher<ClassLoader> classLoaderMatcher;
    private ElementMatcher<String> typeMatcher;

    private ElementMatcher<String> advisorMatcher;


    private Condition defaultCondition;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictJoinpointClassLoaders;


    private final ClassScanner classScanner;

    private final ObjectFactory objectFactory;
    private final TypePool typePool;
    private final TypeWorld typeWorld;

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

        this.typePoolFactory = aopContext.getTypePoolFactory();
        this.typeWorldFactory = aopContext.getTypeWorldFactory();

        // load factory settings
        this.loadSettings(factoriesContext, configView);


        // 3.create properties
        // create classScanner and objectFactory
        this.classScanner = this.createClassScanner(this.aopContext);
        this.objectFactory = this.createObjectFactory(classLoader, this.classScanner);

        this.typePool = new AspectTypePool(classLoader, typePoolFactory);
        this.typeWorld = typeWorldFactory.createTypeWorld(typePool, placeholderHelper);

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
            Set<String> joinpointTypeExprs = configView.getAsStringSet(FACTORY_JOINPOINT_TYPE_EXPRS, Collections.emptySet());

            this.joinpointTypesMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                    FACTORY_JOINPOINT_TYPE_EXPRS, joinpointTypeExprs );

            this.classLoader.setJoinpointTypeMatcher(joinpointTypesMatcher);


            Set<String> joinpointResourceExprs = new LinkedHashSet<>();
            joinpointResourceExprs.addAll(
                    configView.getAsStringSet(FACTORY_JOINPOINT_RESOURCE_EXPRS, Collections.emptySet()) );
            joinpointResourceExprs.addAll(joinpointTypeExprs);

            this.joinpointResourcesMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                    FACTORY_JOINPOINT_RESOURCE_EXPRS, joinpointResourceExprs );

            this.classLoader.setJoinpointResourceMatcher(joinpointResourcesMatcher);
        }

        {
            this.matchJoinpoint = configView.getAsBoolean(FACTORY_MATCHER_MATCH_JOINPOINT_KEY, true);
            if(matchJoinpoint == false) {
                LOGGER.warn("WARNING! Setting '{}' is false for factory '{}', and switched off aop weaving. \n", 
                        FACTORY_MATCHER_MATCH_JOINPOINT_KEY, this.factoryName);
            }
        }

        {
            Set<String> includedClassLoaderExprs = configView.getAsStringSet(FACTORY_INCLUDED_CLASS_LOADER_EXPRS_KEY, Collections.emptySet());

            if(includedClassLoaderExprs.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        includedClassLoaderExprs.size(), FACTORY_INCLUDED_CLASS_LOADER_EXPRS_KEY, factoryName,
                        StringUtils.join(includedClassLoaderExprs, "\n  ")
                );

            ElementMatcher.Junction<ClassLoader> includedClassLoadersMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                    FactoryContext.FACTORY_INCLUDED_CLASS_LOADER_EXPRS_KEY, includedClassLoaderExprs );


            Set<String> excludedClassLoaderExprs = configView.getAsStringSet(FACTORY_EXCLUDED_CLASS_LOADER_EXPRS_KEY, Collections.emptySet());

            if(excludedClassLoaderExprs.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        excludedClassLoaderExprs.size(), FACTORY_EXCLUDED_CLASS_LOADER_EXPRS_KEY, factoryName,
                        StringUtils.join(excludedClassLoaderExprs, "\n  ")
                );

            ElementMatcher.Junction<ClassLoader> excludedClassLoadersMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                    FactoryContext.FACTORY_EXCLUDED_CLASS_LOADER_EXPRS_KEY, excludedClassLoaderExprs );


            this.classLoaderMatcher = includedClassLoadersMatcher.or( ElementMatchers.not(excludedClassLoadersMatcher) );
        }

        {
            Set<String> includedTypeExprs = configView.getAsStringSet(FACTORY_INCLUDED_TYPE_EXPRS_KEY, Collections.emptySet());

            if(includedTypeExprs.size() > 0) 
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        includedTypeExprs.size(), FACTORY_INCLUDED_TYPE_EXPRS_KEY, factoryName,
                        StringUtils.join(includedTypeExprs, "\n  ")
                );

            ElementMatcher.Junction<String> includedTypeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                    FACTORY_INCLUDED_TYPE_EXPRS_KEY, includedTypeExprs );


            Set<String> excludedTypeExprs = configView.getAsStringSet(FACTORY_EXCLUDED_TYPE_EXPRS_KEY, Collections.emptySet());

            if(excludedTypeExprs.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        excludedTypeExprs.size(), FACTORY_EXCLUDED_TYPE_EXPRS_KEY, factoryName,
                        StringUtils.join(excludedTypeExprs, "\n  ")
                );

            ElementMatcher.Junction<String> excludedTypeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                    FACTORY_EXCLUDED_TYPE_EXPRS_KEY, excludedTypeExprs );


            this.typeMatcher = includedTypeMatcher.or( ElementMatchers.not(excludedTypeMatcher) );
        }

        {
            Set<String> includedAdvisorExprs = configView.getAsStringSet(FACTORY_INCLUDED_ADVISOR_EXPRS_KEY, Collections.emptySet());

            if(includedAdvisorExprs.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        includedAdvisorExprs.size(), FACTORY_INCLUDED_ADVISOR_EXPRS_KEY, factoryName,
                        StringUtils.join(includedAdvisorExprs, "\n  ")
                );

            ElementMatcher.Junction<String> includedAdvisorMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                            FACTORY_INCLUDED_ADVISOR_EXPRS_KEY, includedAdvisorExprs );


            Set<String> excludedAdvisorExprs = configView.getAsStringSet(FACTORY_EXCLUDED_ADVISOR_EXPRS_KEY, Collections.emptySet());

            if(excludedAdvisorExprs.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        excludedAdvisorExprs.size(), FACTORY_EXCLUDED_ADVISOR_EXPRS_KEY, factoryName,
                        StringUtils.join(excludedAdvisorExprs, "\n  ")
                );

            ElementMatcher.Junction<String> excludedAdvisorMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                            FACTORY_EXCLUDED_ADVISOR_EXPRS_KEY, excludedAdvisorExprs );


            this.advisorMatcher = includedAdvisorMatcher.or( ElementMatchers.not(excludedAdvisorMatcher) );
        }

        {
            // load and merge global factory settings
            Set<String> mergedClassLoaderExprs = new LinkedHashSet<>();
            mergedClassLoaderExprs.addAll(factoriesContext.getDefaultMatchingClassLoaderExprs());
            mergedClassLoaderExprs.addAll(configView.getAsStringSet(FACTORY_DEFAULT_MATCHING_CLASS_LOADER_EXPRS_KEY, Collections.emptySet()) );

            ElementMatcher<String> defaultClassLoaderMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                    FactoriesContext.FACTORIES_DEFAULT_MATCHING_CLASS_LOADER_EXPRS_KEY + ", " + FACTORY_DEFAULT_MATCHING_CLASS_LOADER_EXPRS_KEY,
                    mergedClassLoaderExprs );

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

    public boolean matchClassLoaders(ClassLoader classLoader) {
        return classLoaderMatcher.matches(classLoader);
    }

    public boolean matchType(String typeName) {
        return this.typeMatcher.matches(typeName);
    }


    public boolean matchAdvisor(String advisorName) {
        return this.advisorMatcher.matches(advisorName);
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
            this.advisorContextMap.computeIfAbsent(
                    cacheKey, 
                    key -> doCreateAdvisorContext(joinpointClassLoader, javaModule, sharedMode, validateContext)
            );
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
        // create AspectClassLoader & objectFactory per ClassLoader
        AspectClassLoader classLoader = this.classLoader;
        ObjectFactory objectFactory = this.objectFactory;
        if(sharedMode == false) {
            classLoader = new AspectClassLoader.WithJoinpointCL(
                    factoryName, 
                    factoryResourceURLs, 
                    aopContext.getAopClassLoader(),
                    joinpointClassLoader);

            classLoader.setJoinpointTypeMatcher(joinpointTypesMatcher);
            classLoader.setJoinpointResourceMatcher(joinpointResourcesMatcher);

            objectFactory = createObjectFactory(classLoader, classScanner);
        }

        return new AdvisorContext(this,
                ClassLoaderUtils.getClassLoaderName(joinpointClassLoader), javaModule,
                classLoader, objectFactory, 
                typePool, typeWorld,
                validateContext);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((factoryName == null) ? 0 : factoryName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FactoryContext other = (FactoryContext) obj;
        if (factoryName == null) {
            if (other.factoryName != null)
                return false;
        } else if (!factoryName.equals(other.factoryName))
            return false;
        return true;
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
