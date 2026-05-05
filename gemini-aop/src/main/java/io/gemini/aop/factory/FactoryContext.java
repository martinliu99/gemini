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
import java.lang.annotation.Annotation;
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
import io.gemini.aop.AopMetrics;
import io.gemini.aop.factory.classloader.AspectClassLoader;
import io.gemini.aop.factory.classloader.AspectTypePool;
import io.gemini.aop.factory.classloader.AspectTypeWorld;
import io.gemini.aop.factory.support.AdvisorConditionParser;
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.api.classloader.ClassLoaders;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.aspectj.weaver.TypeWorldFactory;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigViews;
import io.gemini.core.converter.ConversionService;
import io.gemini.core.converter.Converter;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.pool.TypePoolFactory;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.PlaceholderHelper;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

/**
 * Per-aspect-application context holding all resources needed to create advisors for a single
 * aspect application (AspectApp).
 * <p>
 * Each {@code FactoryContext} owns:
 * <ul>
 *   <li>An {@link AspectClassLoader} with the aspect app's classpath</li>
 *   <li>A {@link io.gemini.core.object.ClassScanner} scoped to the aspect app's classes</li>
 *   <li>An {@link io.gemini.core.object.ObjectFactory} for instantiating advice objects</li>
 *   <li>A {@link ConfigView} merging internal, user-defined, and parent settings</li>
 *   <li>A {@link io.gemini.aop.factory.classloader.AspectTypePool} and {@link io.gemini.aspectj.weaver.TypeWorld} for type resolution</li>
 *   <li>A cache of {@link AdvisorContext} instances keyed by target class loader</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public class FactoryContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FactoryContext.class);

    private static final String FACTORY_INTERNAL_PROPERTIES = "META-INF/factory-internal.properties";

    private static final String FACTORY_TARGET_FIRST_TYPE_EXPRESSIONS = "aop.factory.targetFirstTypeExpressions";
    private static final String FACTORY_TARGET_FIRST_RESOURCE_EXPRESSIONS = "aop.factory.targetFirstResourceExpressions";

    private static final String FACTORY_FACTORY_CLASSLOADER_EXPRESSIONS_KEY = "aop.factory.factoryClassLoaderExpressions";

    private static final String FACTORY_ENABLED_ADVISOR_EXPRESSIONS_KEY = "aop.factory.enabledAdvisorExpressions";


    private static final String AOP_CONTEXT_OBJECT = "aopContext";
    private static final String OBJECT_FACTORY_OBJECT = "objectFactory";


    private final AopContext aopContext;
    private final FactoriesContext factoriesContext;

    private final String factoryName;
    private final URL[] factoryResourceURLs;

    private final AspectClassLoader classLoader;

    private final ClassScanner classScanner;
    private final ObjectFactory objectFactory;

    private final ConfigView configView;
    private final PlaceholderHelper placeholderHelper;


    private ElementMatcher<String> targetFirstTypeMatcher;
    private ElementMatcher<String> targetFirstResourcesMatcher;

    private ElementMatcher<ClassLoader> factoryClassLoaderMatcher;

    private ElementMatcher<String> enabledAdvisorMatcher;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictTargetClassLoaders;

    private final TypePoolFactory typePoolFactory;
    private final TypeWorldFactory typeWorldFactory;

    private final AspectTypePool typePool;
    private final TypeWorld typeWorld;

    private final Map<Class<? extends Annotation>, Class<?>> conditionalAndConditionClasses;

    private ConcurrentMap<ClassLoader, AdvisorContext> advisorContextMap;


    /**
     * Creates a new {@link FactoryContext} for the given aspect application.
     * Initializes the class loader, class scanner, object factory, config view,
     * type pool, type world, and conditional class mappings.
     *
     * @param aopContext           the central AOP context
     * @param factoriesContext     the parent factories context
     * @param factoryName          the name of this aspect application
     * @param factoryResourceURLs  the classpath URLs for this aspect application
     */
    public FactoryContext(AopContext aopContext, 
            FactoriesContext factoriesContext,
            String factoryName, URL[] factoryResourceURLs) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating FactoryContext '{}', ", factoryName);


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
        // create classScanner and objectFactory
        this.classScanner = this.createClassScanner(this.aopContext);
        this.objectFactory = this.createObjectFactory(classLoader, this.classScanner);

        // create configView
        this.configView = createConfigView(aopContext, classLoader, objectFactory);
        this.placeholderHelper = PlaceholderHelper.create(configView);

        // load factory settings
        this.loadSettings(factoriesContext, configView);


        // 3.create properties
        // create typePool and typeWorld
        this.typePoolFactory = aopContext.getTypePoolFactory();
        this.typeWorldFactory = aopContext.getTypeWorldFactory();

        this.typePool = createAspectTypePool(classLoader, typePoolFactory);
        this.typeWorld = 
                new TypeWorld.CacheResolutionFacade(
                        new TypeWorld.LazyFacade(
                                new AspectTypeWorld(typePool, placeholderHelper, classLoader, typeWorldFactory) ) );

        this.conditionalAndConditionClasses = AdvisorConditionParser.loadConditionalAndConditionClasses(classScanner, classLoader);

        this.advisorContextMap = new ConcurrentReferenceHashMap<>();


        if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled()) 
            LOGGER.info("$Took '{}' seconds to create FactoryContext '{}'.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, factoryName);
    }

    /**
     * Creates a {@link ClassScanner} scoped to the combined classpath of the AOP class loader
     * and this aspect application's own resource URLs.
     *
     * @param aopContext the central AOP context providing the base class scanner and class loader
     * @return a new {@link ClassScanner} filtered to the relevant classpath entries
     */
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

    /**
     * Creates an {@link ObjectFactory} backed by the given class loader and class scanner,
     * and pre-registers the {@link AopContext} and the factory itself as named singletons.
     *
     * @param classLoader  the aspect class loader used to load advice classes
     * @param classScanner the class scanner used to discover advice implementations
     * @return a configured {@link ObjectFactory}
     */
    private ObjectFactory createObjectFactory(AspectClassLoader classLoader, ClassScanner classScanner) {
        ObjectFactory objectFactory = new ObjectFactory.Builder()
                .diagnosticLevel(aopContext.getDiagnosticLevel())
                .classLoader(classLoader)
                .classScanner(classScanner)
                .build(false);

        objectFactory.registerSingleton(AOP_CONTEXT_OBJECT, aopContext);
        objectFactory.registerSingleton(OBJECT_FACTORY_OBJECT, objectFactory);

        return objectFactory;
    }

    /**
     * Creates and returns the {@link ConfigView} for this factory context by loading
     * internal and user-defined properties files from the aspect class loader.
     *
     * @param aopContext    the central AOP context providing the parent config view
     * @param classLoader   the aspect class loader used to locate config files
     * @param objectFactory the object factory used to discover custom converters
     * @return merged {@link ConfigView} for this aspect application
     */
    @SuppressWarnings("rawtypes")
    private ConfigView createConfigView(AopContext aopContext, AspectClassLoader classLoader, 
            ObjectFactory objectFactory) {
        Map<String, String> userDefinedConfigs = new LinkedHashMap<>();

        String userDefinedConfigLocation = getUserDefinedConfigLocation(aopContext);
        String userDefinedConfig = null;
        if (classLoader.getResource(userDefinedConfigLocation) != null) {
            userDefinedConfigs.put(userDefinedConfigLocation, factoryName);
            userDefinedConfig = userDefinedConfigLocation;
        }

        String internalConfig = classLoader.getResource(FACTORY_INTERNAL_PROPERTIES) == null ? null : FACTORY_INTERNAL_PROPERTIES;

        List<Converter> loadedConverters = objectFactory.createObjectsImplementing(Converter.class, false);
        List<Converter<?, ?>> converters = new ArrayList<>(loadedConverters.size());
        for (Converter<?, ?> converter : loadedConverters) 
            converters.add(converter);

        ConfigView configView = ConfigViews.createConfigView(
                aopContext.getConfigView(), 
                ConversionService.createConversionService(converters),
                classLoader, 
                internalConfig, 
                userDefinedConfigs);

        if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isDebugEnabled())
            LOGGER.info("Created ConfigView for Factory '{}' with settings, \n"
                    + "  InternalConfigLoc: {} \n"
                    + "  UserDefinedConfigLoc: {} \n",
                    factoryName, internalConfig, userDefinedConfig);

        return configView;
    }

    /**
     * Returns the user-defined configuration file location for this aspect application,
     * taking the active profile into account (e.g. {@code factory.properties} or
     * {@code factory-prod.properties}).
     *
     * @param aopContext the central AOP context providing the active profile
     * @return the relative path to the user-defined config file
     */
    private String getUserDefinedConfigLocation(AopContext aopContext) {
        return "factory" + (aopContext.isDefaultProfile() ? "" : "-" + aopContext.getActiveProfile()) + ".properties";
    }

    /**
     * Creates an {@link AspectTypePool} backed by the given class loader, and registers into
     * the given type pool factory.
     * 
     * @param classLoader   the aspect class loader used to locate advice classes
     * @param typePoolFactory the type pool factory to register created {@link AspectTypePool}
     * @return the created {@link AspectTypePool}
     */
    private AspectTypePool createAspectTypePool(AspectClassLoader classLoader, TypePoolFactory typePoolFactory) {
        AspectTypePool typePool = new AspectTypePool(classLoader, typePoolFactory);
        typePoolFactory.registerTypePool(classLoader, null, typePool);
        return typePool;
    }


    /**
     * Loads and applies all factory-level settings from the given {@link ConfigView}:
     * target-first type/resource matchers, factory class loader filter, enabled-advisor filter,
     * shared class loader flag, and conflict class loader groups.
     *
     * @param factoriesContext the parent factories context providing global defaults
     * @param configView       the merged config view for this aspect application
     */
    private void loadSettings(FactoriesContext factoriesContext, ConfigView configView) {
        {
            Set<String> targetFirstTypeExpressions = configView.getAsStringSet(FACTORY_TARGET_FIRST_TYPE_EXPRESSIONS, Collections.emptySet());

            this.targetFirstTypeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                    FACTORY_TARGET_FIRST_TYPE_EXPRESSIONS, targetFirstTypeExpressions, ElementMatchers.none() );

            this.classLoader.setTargetFirstTypeMatcher(targetFirstTypeMatcher);


            Set<String> targetResourceExpressions = new LinkedHashSet<>();
            targetResourceExpressions.addAll(
                    configView.getAsStringSet(FACTORY_TARGET_FIRST_RESOURCE_EXPRESSIONS, Collections.emptySet()) );
            targetResourceExpressions.addAll(targetFirstTypeExpressions);

            this.targetFirstResourcesMatcher = ElementMatcherFactory.INSTANCE.createResourceNameMatcher(
                    FACTORY_TARGET_FIRST_RESOURCE_EXPRESSIONS, targetResourceExpressions, ElementMatchers.none() );

            this.classLoader.setTargetFirstResourceMatcher(targetFirstResourcesMatcher);
        }

        {
            // parse classLoader/type expression pair
            Set<String> classLoaderExpressions = 
                    configView.getAsStringSet(FACTORY_FACTORY_CLASSLOADER_EXPRESSIONS_KEY, Collections.emptySet());

            if (LOGGER.isWarnEnabled() && classLoaderExpressions.size() > 0)
                LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n"
                        + "  {} \n", 
                        classLoaderExpressions.size(), FACTORY_FACTORY_CLASSLOADER_EXPRESSIONS_KEY, factoryName,
                        StringUtils.join(classLoaderExpressions, "\n  ")
                );

            // parse classLoader matcher
            ElementMatcher<ClassLoader> classLoaderMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                    FACTORY_FACTORY_CLASSLOADER_EXPRESSIONS_KEY, 
                    classLoaderExpressions, 
                    ElementMatchers.none()
            );

            this.factoryClassLoaderMatcher = classLoaderMatcher;
        }

        {
            Set<String> enabledAdvisorExpressions = configView.getAsStringSet(FACTORY_ENABLED_ADVISOR_EXPRESSIONS_KEY, Collections.emptySet());
            if (enabledAdvisorExpressions.size() > 0) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting under '{}'. \n"
                            + "  {} \n", 
                            enabledAdvisorExpressions.size(), FACTORY_ENABLED_ADVISOR_EXPRESSIONS_KEY, factoryName,
                            StringUtils.join(enabledAdvisorExpressions, "\n  ")
                    );

                this.enabledAdvisorMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                        FACTORY_ENABLED_ADVISOR_EXPRESSIONS_KEY, enabledAdvisorExpressions, ElementMatchers.none() );
            } else {
                this.enabledAdvisorMatcher = ElementMatchers.any();
            }
        }

        {
            // load and merge global factory settings
            boolean shareAspectClassLoader = configView.getAsBoolean("aop.factory.shareAspectClassLoader", false);
            this.shareAspectClassLoader = shareAspectClassLoader && factoriesContext.isShareAspectClassLoader();

            List<Set<String>> conflictTargetClassLoaders = new ArrayList<>();
            conflictTargetClassLoaders.addAll(
                    factoriesContext.parseConflictTargetClassLoaders(
                            configView.getAsString("aop.factory.conflictTargetClassLoaders", "") ) );
            conflictTargetClassLoaders.addAll( factoriesContext.getConflictTargetClassLoaders() );  // merge settings in weaverContext
            this.conflictTargetClassLoaders = conflictTargetClassLoaders;
        }
    }


    /**
     * Returns the name of this aspect application factory.
     *
     * @return the factory name
     */
    public String getFactoryName() {
        return factoryName;
    }

    /**
     * Returns the central {@link AopContext}.
     *
     * @return the AOP context
     */
    public AopContext getAopContext() {
        return aopContext;
    }

    /**
     * Returns the parent {@link FactoriesContext} that owns this factory context.
     *
     * @return the factories context
     */
    public FactoriesContext getFactoriesContext() {
        return factoriesContext;
    }

    /**
     * Returns the {@link AspectClassLoader} for this aspect application.
     *
     * @return the aspect class loader
     */
    public AspectClassLoader getClassLoader() {
        return this.classLoader;
    }


    /**
     * Returns the merged {@link ConfigView} for this aspect application.
     *
     * @return the config view
     */
    public ConfigView getConfigView() {
        return configView;
    }

    /**
     * Returns the placeholder helper for resolving {@code ${key}} expressions.
     *
     * @return the placeholder helper
     */
    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }


    /**
     * Returns {@code true} if the given target class loader is accepted by the
     * factory's class loader filter expression.
     *
     * @param targetClassLoader the class loader to test
     * @return {@code true} if this factory should create advisors for the given class loader
     */
    public boolean acceptTargetClassLoader(ClassLoader targetClassLoader) {
        return factoryClassLoaderMatcher.matches(targetClassLoader);
    }

    /**
     * Returns {@code true} if the given advisor name matches the enabled-advisor filter.
     *
     * @param advisorName the advisor name to test
     * @return {@code true} if this advisor should be included
     */
    public boolean isEnabledAdvisor(String advisorName) {
        return this.enabledAdvisorMatcher.matches(advisorName);
    }

    /**
     * Returns the {@link ClassScanner} scoped to this aspect application's classpath.
     *
     * @return the class scanner
     */
    public ClassScanner getClassScanner() {
        return this.classScanner;
    }

    /**
     * Returns the {@link ObjectFactory} for instantiating advice objects.
     *
     * @return the object factory
     */
    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    /**
     * Returns the {@link AspectTypePool} for resolving aspect-side type descriptions.
     *
     * @return the aspect type pool
     */
    public AspectTypePool getTypePool() {
        return typePool;
    }

    /**
     * Returns the {@link TypeWorld} for AspectJ pointcut expression evaluation.
     *
     * @return the type world
     */
    public TypeWorld getTypeWorld() {
        return typeWorld;
    }


    /**
     * Returns an unmodifiable map of conditional annotation class to its condition implementation class.
     *
     * @return map of conditional annotation to condition class
     */
    public Map<Class<? extends Annotation>, Class<?>> getConditionalAndConditionClasses() {
        return Collections.unmodifiableMap( conditionalAndConditionClasses );
    }


    /**
     * Returns or creates a cached {@link AdvisorContext} for the given target class loader.
     * Uses a shared {@link AspectClassLoader} when safe to do so (no class loading conflicts).
     *
     * @param targetClassLoader the target class loader
     * @param targetJavaModule  the target Java module (may be {@code null})
     * @return the advisor context for the given class loader
     */
    public AdvisorContext createAdvisorContext(ClassLoader targetClassLoader, JavaModule targetJavaModule) {
        return createAdvisorContext(targetClassLoader, targetJavaModule, false);
    }

    /**
     * Returns or creates a cached {@link AdvisorContext} for the given target class loader,
     * optionally in validation mode.
     *
     * @param targetClassLoader the target class loader
     * @param targetJavaModule  the target Java module (may be {@code null})
     * @param validateContext   if {@code true}, eagerly validates advisor specs at startup
     * @return the advisor context for the given class loader
     */
    public AdvisorContext createAdvisorContext(ClassLoader targetClassLoader, JavaModule targetJavaModule, 
            boolean validateContext) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(targetClassLoader);
        return this.advisorContextMap.computeIfAbsent( 
                cacheKey, 
                key -> doCreateAdvisorContext(
                        targetClassLoader, targetJavaModule, 
                        validateContext,
                        isUseSharedAspectClassLoader(cacheKey)
                )
        );
    }

    /**
     * Determines whether a shared {@link AspectClassLoader} can be reused for the given
     * target class loader, based on conflict group configuration and existing cached contexts.
     *
     * @param targetClassLoader the target class loader to evaluate
     * @return {@code true} if the shared aspect class loader can be reused
     */
    private boolean isUseSharedAspectClassLoader(ClassLoader targetClassLoader) {
        // 1.use existing AspectClassLoader
        if (advisorContextMap.containsKey(targetClassLoader) == true)
            return true;


        // 2.used shared AspectClassLoader for system ClassLoaders
        if (ClassLoaders.getBuiltinClassLoaders().contains(targetClassLoader) == true)
            return true;


        // 3.check shareAspectClassLoader flag
        if (shareAspectClassLoader == false) 
            return false;


        // 4.check potentially class loading conflict
        // exist ClassLoader is same instance of the TargetClassLoader
        Class<? extends ClassLoader> classLoaderClass = targetClassLoader.getClass();
        for (ClassLoader existingCL : advisorContextMap.keySet()) {
            if (existingCL.getClass() == classLoaderClass)
                return false;
        }

        // exist ClassLoader might conflict with the TargetClassLoader 
        String targetCLClassName = ClassLoaderUtils.getClassLoaderName(targetClassLoader);
        List<Set<String>> conflictTargetClassLoaderList = conflictTargetClassLoaders.stream()
                .filter( classLoaders -> classLoaders.contains(targetCLClassName) )
                .collect( Collectors.toList() );

        for (ClassLoader existingCL : advisorContextMap.keySet()) {
            String existingCLClassName = ClassLoaderUtils.getClassLoaderName(existingCL);

            for (Set<String> classLoaders : conflictTargetClassLoaderList) {
                if (classLoaders.contains(existingCLClassName))
                    return false;
            }
        }

        // 5.no conflict, used shared AspectClassLoader
        return true;
    }

    /**
     * Creates a new {@link AdvisorContext} for the given target class loader.
     * In shared mode, reuses the factory's own {@link AspectClassLoader} and {@link ObjectFactory};
     * otherwise creates a fresh pair.
     *
     * @param targetClassLoader the target class loader
     * @param targetJavaModule  the target Java module
     * @param validateContext   if {@code true}, enables validation mode
     * @param sharedMode        if {@code true}, reuses the factory's class loader
     * @return a new {@link AdvisorContext}
     */
    protected AdvisorContext doCreateAdvisorContext(ClassLoader targetClassLoader, JavaModule targetJavaModule, 
            boolean validateContext, boolean sharedMode) {
        // create AspectClassLoader & objectFactory per ClassLoader
        AspectClassLoader classLoader = this.classLoader;
        ObjectFactory objectFactory = this.objectFactory;
        AspectTypePool typePool = this.typePool;

        if (sharedMode == false) {
            classLoader = new AspectClassLoader(
                    factoryName, 
                    factoryResourceURLs, 
                    aopContext.getAopClassLoader() );

            classLoader.setTargetFirstTypeMatcher(targetFirstTypeMatcher);
            classLoader.setTargetFirstResourceMatcher(targetFirstResourcesMatcher);

            objectFactory = createObjectFactory(classLoader, classScanner);
            typePool = createAspectTypePool(classLoader, typePoolFactory);
        }

        return new AdvisorContext(this,
                ClassLoaderUtils.getClassLoaderName(targetClassLoader), targetJavaModule,
                classLoader, objectFactory, 
                typePool, typePoolFactory.createTypePool(targetClassLoader, targetJavaModule),
                typeWorld, typeWorldFactory.createTypeWorld(targetClassLoader, targetJavaModule),
                validateContext);
    }

    /**
     * {@inheritDoc}
     * <p>Hash code is based on {@link #factoryName} only.</p>
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((factoryName == null) ? 0 : factoryName.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>Equality is based on {@link #factoryName} only.</p>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (obj instanceof FactoryContext == false)
            return false;
        FactoryContext other = (FactoryContext) obj;
        if (factoryName == null) {
            if (other.factoryName != null)
                return false;
        } else if (!factoryName.equals(other.factoryName))
            return false;
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>Returns the factory name.</p>
     */
    @Override
    public String toString() {
        return factoryName;
    }

    /**
     * {@inheritDoc}
     * <p>Closes all cached {@link AdvisorContext} instances, the object factory, and the type pool.</p>
     */
    @Override
    public void close() throws IOException {
        for (Closeable closeable : this.advisorContextMap.values()) {
            closeable.close();
       };

        this.objectFactory.close();
        this.typePool.clear();
    }
}
