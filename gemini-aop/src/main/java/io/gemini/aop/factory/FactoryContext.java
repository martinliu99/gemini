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
import io.gemini.aop.factory.classloader.AspectTypeWorld;
import io.gemini.aop.matcher.AdvisorCondition;
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.api.aop.condition.ConditionContext;
import io.gemini.api.classloader.ClassLoaders;
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

    private static final String FACTORY_JOINPOINT_TYPE_EXPRESSIONS = "aop.factory.joinpointTypeExpressions";
    private static final String FACTORY_JOINPOINT_RESOURCE_EXPRESSIONS = "aop.factory.joinpointResourceExpressions";

    private static final String FACTORY_MATCHER_MATCH_JOINPOINT_KEY = "aop.factory.matchJoinpoint";

    private static final String FACTORY_CLASS_LOADER_EXPRESSIONS_KEY = "aop.factory.classLoaderExpressions";
    private static final String FACTORY_TYPE_EXPRESSIONS_KEY = "aop.factory.typeExpressions";
    private static final String FACTORY_ADVISOR_EXPRESSIONS_KEY = "aop.factory.advisorExpressions";

    private static final String FACTORY_DEFAULT_MATCHING_CLASS_LOADER_EXPRESSIONS_KEY = "aop.factory.defaultMatchingClassLoaderExpressions";


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


    private ElementMatcher<ConditionContext> defaultCondition;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictJoinpointClassLoaders;


    private final ClassScanner classScanner;

    private final ObjectFactory objectFactory;
    private final AspectTypePool typePool;
    private final TypeWorld typeWorld;

    private final ConcurrentMap<ClassLoader, AdvisorContext> advisorContextMap;


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

        this.classLoader = new AspectClassLoader(
                factoryName, 
                factoryResourceURLs, 
                aopContext.getAopClassLoader() );


        // 2.load settings
        // create configView
        this.configView = createConfigView(aopContext, classLoader);
        this.placeholderHelper = PlaceholderHelper.create(configView);

        this.typePoolFactory = aopContext.getTypePoolFactory();
        this.typeWorldFactory = aopContext.getTypeWorldFactory();

        // load factory settings
        this.loadSettings(factoriesContext, configView);


        // 3.create properties
        // create classScanner and objectFactory
        this.classScanner = this.createClassScanner(this.aopContext);
        this.objectFactory = this.createObjectFactory(classLoader, this.classScanner);

        this.typePool = new AspectTypePool(classLoader, typePoolFactory);
        this.typeWorld = new TypeWorld.LazyFacade(
                new AspectTypeWorld(typePool, placeholderHelper, classLoader, typeWorldFactory) );

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
            Set<String> joinpointTypeExpressions = configView.getAsStringSet(FACTORY_JOINPOINT_TYPE_EXPRESSIONS, Collections.emptySet());

            this.joinpointTypesMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                    FACTORY_JOINPOINT_TYPE_EXPRESSIONS, joinpointTypeExpressions );

            this.classLoader.setJoinpointTypeMatcher(joinpointTypesMatcher);


            Set<String> joinpointResourceExpressions = new LinkedHashSet<>();
            joinpointResourceExpressions.addAll(
                    configView.getAsStringSet(FACTORY_JOINPOINT_RESOURCE_EXPRESSIONS, Collections.emptySet()) );
            joinpointResourceExpressions.addAll(joinpointTypeExpressions);

            this.joinpointResourcesMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                    FACTORY_JOINPOINT_RESOURCE_EXPRESSIONS, joinpointResourceExpressions );

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
            Set<String> classLoaderExpressions = configView.getAsStringSet(FACTORY_CLASS_LOADER_EXPRESSIONS_KEY, Collections.emptySet());
            if(classLoaderExpressions.size() > 0) {
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        classLoaderExpressions.size(), FACTORY_CLASS_LOADER_EXPRESSIONS_KEY, factoryName,
                        StringUtils.join(classLoaderExpressions, "\n  ")
                );

                this.classLoaderMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(FACTORY_CLASS_LOADER_EXPRESSIONS_KEY, classLoaderExpressions );
            } else {
                this.classLoaderMatcher = ElementMatchers.any();
            }
        }

        {
            Set<String> typeExpressions = configView.getAsStringSet(FACTORY_TYPE_EXPRESSIONS_KEY, Collections.emptySet());
            if(typeExpressions.size() > 0) {
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        typeExpressions.size(), FACTORY_TYPE_EXPRESSIONS_KEY, factoryName,
                        StringUtils.join(typeExpressions, "\n  ")
                );

                this.typeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(FACTORY_TYPE_EXPRESSIONS_KEY, typeExpressions );
            } else {
                this.typeMatcher = ElementMatchers.any();
            }
        }

        {
            Set<String> advisorExpressions = configView.getAsStringSet(FACTORY_ADVISOR_EXPRESSIONS_KEY, Collections.emptySet());
            if(advisorExpressions.size() > 0) {
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n  {} \n", 
                        advisorExpressions.size(), FACTORY_ADVISOR_EXPRESSIONS_KEY, factoryName,
                        StringUtils.join(advisorExpressions, "\n  ")
                );

                this.advisorMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(FACTORY_ADVISOR_EXPRESSIONS_KEY, advisorExpressions );
            } else {
                this.advisorMatcher = ElementMatchers.any();
            }
        }

        {
            // load and merge global factory settings
            Set<String> mergedClassLoaderExpressions = new LinkedHashSet<>();
            mergedClassLoaderExpressions.addAll(factoriesContext.getDefaultMatchingClassLoaderExpressions());
            mergedClassLoaderExpressions.addAll(configView.getAsStringSet(FACTORY_DEFAULT_MATCHING_CLASS_LOADER_EXPRESSIONS_KEY, Collections.emptySet()) );

            this.defaultCondition = AdvisorCondition.create(this, mergedClassLoaderExpressions);
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


    public ConfigView getConfigView() {
        return configView;
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


    public ElementMatcher<ConditionContext> getDefaultCondition() {
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

    public TypeWorld getTypeWorld() {
        return typeWorld;
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
        if(ClassLoaders.getBuiltinClassLoaders().contains(joinpointClassLoader) == true)
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
            classLoader = new AspectClassLoader(
                    factoryName, 
                    factoryResourceURLs, 
                    aopContext.getAopClassLoader() );

            classLoader.setJoinpointTypeMatcher(joinpointTypesMatcher);
            classLoader.setJoinpointResourceMatcher(joinpointResourcesMatcher);

            objectFactory = createObjectFactory(classLoader, classScanner);
        }

        return new AdvisorContext(this,
                ClassLoaderUtils.getClassLoaderName(joinpointClassLoader), javaModule,
                classLoader, objectFactory, 
                typePoolFactory, typePool, 
                typeWorldFactory, typeWorld,
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
