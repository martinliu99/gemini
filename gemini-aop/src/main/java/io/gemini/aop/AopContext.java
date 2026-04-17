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
package io.gemini.aop;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopMetrics.LauncherMetrics;
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.api.activation.LauncherConfig;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.aspectj.weaver.TypeWorldFactory;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.concurrent.TaskExecutor;
import io.gemini.core.config.ConfigView;
import io.gemini.core.converter.ConversionService;
import io.gemini.core.converter.Converter.ToClass;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.pool.TypePoolFactory;
import io.gemini.core.util.Assert;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.agent.builder.AgentBuilder.LocationStrategy;
import net.bytebuddy.matcher.CachingMatcher;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;


/**
 * Central context object for the Gemini AOP framework, created once per launcher lifecycle.
 * <p>
 * Holds all shared infrastructure components required during class scanning, advisor creation,
 * and bytecode transformation:
 * <ul>
 *   <li>{@link LauncherConfig} – launch paths, profiles, and classpath URLs</li>
 *   <li>{@link AopClassLoader} – isolated class loader for AOP framework classes</li>
 *   <li>{@link ConfigView} – merged view of internal and user-defined properties</li>
 *   <li>{@link ClassScanner} – scans the classpath for aspect and advice classes</li>
 *   <li>{@link ObjectFactory} – creates and injects advice instances</li>
 *   <li>{@link TypePoolFactory} / {@link TypeWorldFactory} – ByteBuddy and AspectJ type resolution</li>
 *   <li>{@link TaskExecutor} – optional parallel task execution</li>
 *   <li>{@link AopMetrics} – collects startup and weaving performance metrics</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public class AopContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AopContext.class);

    public static final String BOOTSTRAP_CLASS_NAME_MAPPING_KEY = "bootstrapClassNameMapping";

    private static final String DIAGNOSTIC_TYPE_EXPRESSIONS_KEY = "aop.launcher.diagnosticTypeExpressions";

    private static final String AOP_LAUNCHER_DUMP_BYTECODE_KEY = "aop.launcher.dumpByteCode";
    private final static String CLASS_SCANNER_ENABLE_VERBOSE_KEY = "aop.classScanner.enableVerbose";


    private final LauncherConfig launcherConfig;
    private final AopClassLoader aopClassLoader;
    private final Map<String, Object> builtinSettings;


    private final DiagnosticLevel diagnosticLevel;
    private final ConcurrentMap<String, Boolean> diagnosticTypeCache = new ConcurrentHashMap<>();
    private ElementMatcher<String> diagnosticTypeMatcher;

    private boolean detectTypeResolution;


    private final ConfigView configView;
    private final PlaceholderHelper placeholderHelper;

    private final AopMetrics aopMetrics;

    private final ClassScanner classScanner;
    private final ObjectFactory objectFactory;


    private final TypePoolFactory typePoolFactory;
    private final TypeWorldFactory typeWorldFactory;

    private final TaskExecutor globalTaskExecutor;


    private boolean dumpByteCode;
    private String byteCodeDumpPath;


    public AopContext(
            LauncherConfig launcherConfig, 
            AopClassLoader aopClassLoader,
            Map<String, Object> builtinSettings,
            ConfigView configView,
            DiagnosticLevel diagnosticLevel) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating AopContext, ");


        // 1.check input arguments and initialize properties
        Assert.notNull(launcherConfig, "'launcherConfig' must not be null.");
        this.launcherConfig = launcherConfig;

        Assert.notNull(aopClassLoader, "'aopClassLoader' must not be null.");
        this.aopClassLoader = aopClassLoader;

        Assert.notNull(builtinSettings, "'builtinSettings' must not be null.");
        this.builtinSettings = builtinSettings;

        Assert.notNull(diagnosticLevel, "'diagnosticLevel' must not be null.");
        this.diagnosticLevel = diagnosticLevel;


        // 2.create helper classes
        ConversionService conversionService = ConversionService.createConversionService();
        conversionService.addConverter( new ToClass(aopClassLoader) );

        this.configView = new ConfigView.Builder()
                .parent(configView)
                .conversionService(conversionService)
                .build();

        this.placeholderHelper = PlaceholderHelper.create(this.getConfigView());

        this.aopMetrics = new AopMetrics(configView, diagnosticLevel);
        LauncherMetrics launcherMetrics = aopMetrics.getLauncherMetrics();


        // 3.load aop settings
        this.loadSettings(configView);


        // 4.initialize properties
        this.classScanner = createClassScanner(launcherMetrics);
        this.objectFactory = createObjectFactory();


        this.typePoolFactory = createTypePoolFactory();
        this.typeWorldFactory = createTypeWorldFactory(typePoolFactory);

        boolean processInParallel = configView.getAsBoolean("aop.globalTaskExecutor.parallel", false);
        int taskTimeoutMs = configView.getAsInteger("aop.globalTaskExecutor.taskTimeoutMs", 0);
        this.globalTaskExecutor = TaskExecutor.create(diagnosticLevel, "globalTaskExecutor", processInParallel, taskTimeoutMs);


        long time = System.nanoTime() - startedAt;
        if (LOGGER.isInfoEnabled()) {
            if (diagnosticLevel.isDebugEnabled()) 
                LOGGER.info("$Took '{}' seconds to create AopContext with settings, \n" 
                        + "  isDefaultProfile: {} \n"
                        + "  activeProfile: {} \n"
                        + "  internalConfigLocation: {} \n"
                        + "  userDefinedConfigLocation: {} \n"
                        + "  diagnosticStrategy: {} \n"
                        + "  classLoader: {} \n",
                        time / AopMetrics.NANO_TIME,
                        launcherConfig.isDefaultProfile(), launcherConfig.getActiveProfile(),
                        launcherConfig.getInternalConfigLocation(), launcherConfig.getUserDefinedConfigLocation(), diagnosticLevel,
                        aopClassLoader
                );
            else if (diagnosticLevel.isSimpleEnabled()) 
                LOGGER.info("$Took '{}' seconds to create AopContext. ", time / AopMetrics.NANO_TIME);
        }

        aopMetrics.getLauncherMetrics().setAopContextCreationTime(time);
    }

    private void loadSettings(ConfigView configView) {
        // load diagnostic settings
        {
            Set<String> diagnosticTypeExpressions = configView.getAsStringSet(DIAGNOSTIC_TYPE_EXPRESSIONS_KEY, Collections.emptySet());
            ElementMatcher<String> diagnosticTypeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                    DIAGNOSTIC_TYPE_EXPRESSIONS_KEY, diagnosticTypeExpressions, ElementMatchers.none());
            this.diagnosticTypeMatcher = new CachingMatcher<>(diagnosticTypeMatcher, diagnosticTypeCache);

            this.detectTypeResolution = configView.getAsBoolean("aop.launcher.detectTypeResolution", false);
        }

        {
            if (diagnosticLevel.isDebugEnabled()) {
                builtinSettings.put(AOP_LAUNCHER_DUMP_BYTECODE_KEY, true);
            }

            this.dumpByteCode = configView.getAsBoolean(AOP_LAUNCHER_DUMP_BYTECODE_KEY, false);
            this.byteCodeDumpPath = configView.getAsString("aop.launcher.byteCodeDumpPath");
        }
    }

    private ClassScanner createClassScanner(LauncherMetrics launcherMetrics) {
        long startedAt = System.nanoTime();

        ClassScanner.Builder builder = new ClassScanner.Builder()
                .enableVerbose( configView.getAsBoolean(CLASS_SCANNER_ENABLE_VERBOSE_KEY, false) )
                .diagnosticLevel( this.diagnosticLevel )
                ;

        builder = builder.overrideClasspaths( aopClassLoader.getUrls() );
        for (URL[] URLs : this.launcherConfig.getAspectAppClassPathURLs().values()) {
            builder = builder.overrideClasspaths( URLs );
        }

        ClassScanner classScanner = builder
                .acceptJarPatterns( configView.getAsStringList("aop.classScanner.builtinAcceptJarPatterns", Collections.emptyList()) )
                .acceptJarPatterns( configView.getAsStringList("aop.classScanner.acceptJarPatterns", Collections.emptyList()) )
                .acceptPackages( configView.getAsStringList("aop.classScanner.builtinAcceptPackages", Collections.emptyList()) )
                .acceptPackages( configView.getAsStringList("aop.classScanner.acceptPackages", Collections.emptyList()) )
                .workThreads( configView.getAsInteger("aop.classScanner.workThreads", ClassScanner.NO_WORK_THREAD) )
                .filteredClasspathElementUrls( aopClassLoader.getURLs() )
                .build();

        launcherMetrics.setClassScannerCreationTime(System.nanoTime() - startedAt);
        return classScanner;
    }

    private ObjectFactory createObjectFactory() {
        return new ObjectFactory.Builder()
                .diagnosticLevel(diagnosticLevel)
                .classLoader(aopClassLoader)
                .classScanner(classScanner)
                .build(true);
    }

    private TypePoolFactory createTypePoolFactory() {
        return isDetectTypeResolution() == false
                ? new TypePoolFactory.Default(LocationStrategy.ForClassLoader.WEAK)
                : new TypePoolFactory.Default.TyepResolutionDetector(LocationStrategy.ForClassLoader.WEAK)
        ;
    }

    private TypeWorldFactory createTypeWorldFactory(TypePoolFactory typePoolFactory) {
        return isDetectTypeResolution() == false
                ? new TypeWorldFactory.Default(typePoolFactory)
                : new TypeWorldFactory.Default.TyepResolutionDetector(typePoolFactory)
        ;
    }


    protected LauncherConfig getLauncherConfig() {
        return launcherConfig;
    }

    /** 
     * Returns the AOP-isolated class loader that loads framework and aspect classes. 
     */
    public AopClassLoader getAopClassLoader() {
        return this.aopClassLoader;
    }

    /** 
     * Returns the configured diagnostic level (DISABLED, SIMPLE, or DEBUG). 
     */
    public DiagnosticLevel getDiagnosticLevel() {
        return diagnosticLevel;
    }

    /**
     * Returns {@code true} if the given type name matches the configured diagnostic type expressions.
     * Used to enable verbose per-type logging during weaving.
     *
     * @param typeName the fully-qualified type name to check
     * @return {@code true} if diagnostic logging is enabled for this type
     */
    public boolean isDiagnosticType(String typeName) {
        return DiagnosticLevel.DISABLED != diagnosticLevel && diagnosticTypeMatcher.matches(typeName);
    }

    /**
     * Removes the cached diagnostic match result for the given type name.
     * Called after a type has been fully processed to free memory.
     *
     * @param typeName the type name to evict from the cache
     * @return the previously cached value, or {@code null} if absent
     */
    public Boolean removeCachedDiagnosticType(String typeName) {
        return diagnosticTypeCache.remove(typeName);
    }

    /**
     * Returns {@code true} if type resolution detection is enabled.
     * When enabled, the framework tracks which advisors successfully matched each type.
     *
     * @return {@code true} if type resolution detection is active
     */
    public boolean isDetectTypeResolution() {
        return detectTypeResolution || DiagnosticLevel.DISABLED == diagnosticLevel;
    }

    /** 
     * Returns the active configuration profile name (empty string for the default profile). 
     */
    public String getActiveProfile() {
        return launcherConfig.getActiveProfile();
    }

    /** 
     * Returns {@code true} if the default (empty) profile is active. 
     */
    public boolean isDefaultProfile() {
        return launcherConfig.isDefaultProfile();
    }

    /** 
     * Returns the merged configuration view combining internal and user-defined properties. 
     */
    public ConfigView getConfigView() {
        return configView;
    }

    /** 
     * Returns the placeholder helper for resolving {@code ${key}} expressions in configuration values. 
     */
    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }

    /** 
     * Returns the metrics collector for startup and weaving performance data. 
     */
    public AopMetrics getAopMetrics() {
        return aopMetrics;
    }

    /** 
     * Returns the class scanner used to discover aspect and advice classes on the classpath. 
     */
    public ClassScanner getClassScanner() {
        return classScanner;
    }

    /** 
     * Returns the object factory used to instantiate and inject advice objects. 
     */
    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    /** 
     * Returns the ByteBuddy type pool factory for resolving type descriptions during weaving. 
     */
    public TypePoolFactory getTypePoolFactory() {
        return typePoolFactory;
    }

    /** 
     * Returns the AspectJ type world factory for pointcut expression evaluation. 
     */
    public TypeWorldFactory getTypeWorldFactory() {
        return typeWorldFactory;
    }

    /** 
     * Returns the optional parallel task executor for concurrent class scanning and advisor creation. 
     */
    public TaskExecutor getGlobalTaskExecutor() {
        return globalTaskExecutor;
    }

    /** 
     * Returns {@code true} if the {@code /classes} and {@code /test-classes} folders are scanned for aspects. 
     */
    public boolean isClassesFolderScanned() {
        return launcherConfig.isClassesFolderScanned();
    }

    /** 
     * Returns {@code true} if instrumented bytecode should be dumped to disk for inspection. 
     */
    public boolean isDumpByteCode() {
        return dumpByteCode;
    }

    /** 
     * Returns the directory path where instrumented bytecode dumps are written. 
     */
    public String getByteCodeDumpPath() {
        return byteCodeDumpPath;
    }

    /**
     * Returns the bootstrap class name mapping used to relocate classes injected into the bootstrap class loader.
     *
     * @return map of original class name to relocated class name, or {@code null} if not configured
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getBootstrapClassNameMapping() {
        return (Map<String, String>) this.builtinSettings.get(BOOTSTRAP_CLASS_NAME_MAPPING_KEY);
    }

    /** 
     * Returns the map of aspect application names to their classpath URL arrays. 
     */
    public Map<String, URL[]> getAspectAppResourceMap() {
        return this.launcherConfig.getAspectAppClassPathURLs();
    }

    /**
     * Shuts down the global task executor and releases all held resources.
     *
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        this.globalTaskExecutor.shutdown();
    }
}
