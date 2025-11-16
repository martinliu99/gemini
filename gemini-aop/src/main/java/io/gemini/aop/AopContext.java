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
package io.gemini.aop;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopMetrics.BootstraperMetrics;
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
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.agent.builder.AgentBuilder.LocationStrategy;


/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AopContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AopContext.class);

    public static final String BOOTSTRAP_CLASS_NAME_MAPPING_KEY = "bootstrapClassNameMapping";

    private static final String AOP_LAUNCHER_DUMP_BYTE_CODE_KEY = "aop.launcher.dumpByteCode";
    private final static String CLASS_SCANNER_ENABLE_VERBOSE_KEY = "aop.classScanner.enableVerbose";


    private final LauncherConfig launcherConfig;
    private final AopClassLoader aopClassLoader;
    private final Map<String, Object> builtinSettings;


    private final DiagnosticLevel diagnosticLevel;
    private Set<String> diagnosticClasses;


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
        BootstraperMetrics bootstraperMetrics = aopMetrics.getBootstraperMetrics();


        // 3.load aop settings
        this.loadSettings(configView);


        // 4.initialize properties
        this.classScanner = createClassScanner(bootstraperMetrics);
        this.objectFactory = createObjectFactory();


        this.typePoolFactory = createTypePoolFactory();
        this.typeWorldFactory = createTypeWorldFactory(typePoolFactory);

        boolean processInParallel = configView.getAsBoolean("aop.globalTaskExecutor.parallel", false);
        int taskTimeoutMs = configView.getAsInteger("aop.globalTaskExecutor.taskTimeoutMs", 0);
        this.globalTaskExecutor = TaskExecutor.create(diagnosticLevel, "globalTaskExecutor", processInParallel, taskTimeoutMs);


        long time = System.nanoTime() - startedAt;
        if (diagnosticLevel.isDebugEnabled()) 
            LOGGER.info("$Took '{}' seconds to create AopContext with settings, \n" 
                    + "  isDefaultProfile: {} \n"
                    + "  activeProfile: {} \n"
                    + "  internalConfigLocation: {} \n"
                    + "  userDefinedConfigLocation: {} \n"
                    + "  diagnosticStrategy: {} \n"
                    + "  classLoader: {} \n",
                    time / 1e9,
                    launcherConfig.isDefaultProfile(), launcherConfig.getActiveProfile(),
                    launcherConfig.getInternalConfigLocation(), launcherConfig.getUserDefinedConfigLocation(), diagnosticLevel,
                    aopClassLoader
            );
        else if (diagnosticLevel.isSimpleEnabled()) 
            LOGGER.info("$Took '{}' seconds to create AopContext. ", time / 1e9);

        aopMetrics.getBootstraperMetrics().setAopContextCreationTime(time);
    }

    private void loadSettings(ConfigView configView) {
        // load diagnostic settings
        {
            this.diagnosticClasses = configView.getAsStringSet("aop.launcher.diagnosticClasses", Collections.emptySet());
        }

        {
            if (diagnosticLevel.isDebugEnabled()) {
                builtinSettings.put(AOP_LAUNCHER_DUMP_BYTE_CODE_KEY, true);
            }

            this.dumpByteCode = configView.getAsBoolean(AOP_LAUNCHER_DUMP_BYTE_CODE_KEY, false);
            this.byteCodeDumpPath = configView.getAsString("aop.launcher.byteCodeDumpPath");
        }
    }

    private ClassScanner createClassScanner(BootstraperMetrics bootstraperMetrics) {
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

        bootstraperMetrics.setClassScannerCreationTime(System.nanoTime() - startedAt);
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
        return new TypePoolFactory.Default(LocationStrategy.ForClassLoader.WEAK);
    }

    private TypeWorldFactory createTypeWorldFactory(TypePoolFactory typePoolFactory) {
        return new TypeWorldFactory.Default(typePoolFactory);
    }


    protected LauncherConfig getLauncherConfig() {
        return launcherConfig;
    }

    public AopClassLoader getAopClassLoader() {
        return this.aopClassLoader;
    }

    public DiagnosticLevel getDiagnosticLevel() {
        return diagnosticLevel;
    }

    public boolean isDiagnosticClass(String typeName) {
        return DiagnosticLevel.DISABLED != diagnosticLevel && diagnosticClasses.contains(typeName);
    }

    public boolean isDiagnosticClass(Class<?>... types) {
        return isDiagnosticClass( Arrays.asList(types) );
    }

    public boolean isDiagnosticClass(List<Class<?>> types) {
        if (CollectionUtils.isEmpty(types) == true)
            return false;
        if (DiagnosticLevel.DISABLED == diagnosticLevel)
            return false;

        for (Class<?> clazz : types) {
            if (diagnosticClasses.contains(clazz.getName()))
                return true;
        }
        return false;
    }


    public String getActiveProfile() {
        return launcherConfig.getActiveProfile();
    }

    public boolean isDefaultProfile() {
        return launcherConfig.isDefaultProfile();
    }


    public ConfigView getConfigView() {
        return configView;
    }

    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }

    public AopMetrics getAopMetrics() {
        return aopMetrics;
    }

    public ClassScanner getClassScanner() {
        return classScanner;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public TypePoolFactory getTypePoolFactory() {
        return typePoolFactory;
    }

    public TypeWorldFactory getTypeWorldFactory() {
        return typeWorldFactory;
    }

    public TaskExecutor getGlobalTaskExecutor() {
        return globalTaskExecutor;
    }

    public boolean isScanClassesFolder() {
        return launcherConfig.isScanClassesFolder();
    }

    public boolean isDumpByteCode() {
        return dumpByteCode;
    }

    public String getByteCodeDumpPath() {
        return byteCodeDumpPath;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getBootstrapClassNameMapping() {
        return (Map<String, String>) this.builtinSettings.get(BOOTSTRAP_CLASS_NAME_MAPPING_KEY);
    }

    public Map<String, URL[]> getAspectAppResourceMap() {
        return this.launcherConfig.getAspectAppClassPathURLs();
    }


    @Override
    public void close() throws IOException {
        this.globalTaskExecutor.shutdown();
    }
}
