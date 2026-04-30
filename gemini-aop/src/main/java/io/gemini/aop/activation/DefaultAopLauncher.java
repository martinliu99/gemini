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
package io.gemini.aop.activation;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics.LauncherMetrics;
import io.gemini.aop.activation.support.AopClassLoaderConfigurer;
import io.gemini.aop.factory.AdvisorFactory;
import io.gemini.aop.weaver.AopWeaver;
import io.gemini.api.activation.AopLauncher;
import io.gemini.api.activation.LauncherConfig;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.bootstrap.BootstrapClassConfigurer;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.concurrent.DaemonThreadFactory;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigViews;
import io.gemini.core.logging.DeferredLoggerFactory;
import io.gemini.core.logging.LoggingSystem;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.dynamic.Nexus;
import net.bytebuddy.dynamic.scaffold.TypeWriter;
import net.bytebuddy.utility.AsmClassReader;
import net.bytebuddy.utility.AsmClassWriter;
import net.bytebuddy.utility.OpenedClassReader;

/**
 * Default implementation of {@link AopLauncher} that orchestrates the full AOP startup sequence.
 * <p>
 * Startup steps:
 * <ol>
 *   <li>Load AOP settings from internal and user-defined properties files</li>
 *   <li>Initialize the logging system</li>
 *   <li>Create {@link AopContext} (central configuration holder)</li>
 *   <li>Configure bootstrap classes and {@link AopClassLoader}</li>
 *   <li>Create {@link AdvisorFactory} (scans and parses all aspect applications)</li>
 *   <li>Create {@link AopWeaver} and install ByteBuddy agent builder</li>
 *   <li>Register a JVM shutdown hook to stop the framework cleanly</li>
 * </ol>
 * </p>
 *
 * @author   martin.liu
 */
public class DefaultAopLauncher implements AopLauncher {

    private static final Logger LOGGER = DeferredLoggerFactory.getLogger(DefaultAopLauncher.class);


    private AopContext aopContext;
    private AdvisorFactory advisorFactory;
    private AopWeaver aopWeaver;


    public DefaultAopLauncher() {
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void start(Instrumentation instrumentation, 
            LauncherConfig launcherConfig,
            AopClassLoader aopClassLoader) {
        DeferredLoggerFactory.enableDeferMode();

        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

        AopWeaver aopWeaver = null;
        ConfigView configView = null;
        LauncherMetrics launcherMetrics = null;
        try {
            // set AopClassLoader as T.C. ClassLoader
            // existing T.C. ClassLoader, generally is AppClassLoader, might contain libraries, such as log4j2, 
            // and conflict with AopClassLoader
            ThreadContext.setContextClassLoader(aopClassLoader);


            // 1.load AOP settings
            Map<String, Object> builtinSettings = new LinkedHashMap<>();
            builtinSettings.put("aop.launcher.launchPath", launcherConfig.getLaunchPath().toString());

            configView = ConfigViews.createConfigView(
                    launcherConfig.getLaunchArgs(), builtinSettings,
                    aopClassLoader,
                    launcherConfig.getInternalConfigLocation(), 
                    Collections.singletonMap(launcherConfig.getUserDefinedConfigLocation(), "aop-context")
            );

            DiagnosticLevel diagnosticLevel = ConfigViews.getDiagnosticLevel(configView);


            // 2.initialize LoggingSystem
            long startedAt = System.nanoTime();

            new LoggingSystem.Builder().configView(configView).diagnosticLevel(diagnosticLevel)
                    .build()
                    .initialize(aopClassLoader);

            replayDeferredMessages(configView);

            long loggerCreationTime = System.nanoTime() - startedAt;
            long launcherSetupTime = System.nanoTime() - launcherConfig.getLaunchedAt();


            // 3.create helper classes
            this.aopContext = new AopContext(launcherConfig, aopClassLoader, 
                    builtinSettings, configView, diagnosticLevel);

            launcherMetrics = aopContext.getAopMetrics().getLauncherMetrics();
            launcherMetrics.setLauncherStartedAt(launcherConfig.getLaunchedAt());
            launcherMetrics.setLauncherSetupTime(launcherSetupTime);
            launcherMetrics.setLoggerCreationTime(loggerCreationTime);


            // 4.configure ByteBuddy & ClassLoaders
            configureByteBuddy(aopContext);

            configureClassLoader(instrumentation, 
                    builtinSettings, aopContext, launcherMetrics);


            // 5.create AdvisorFactory
            System.getProperties().setProperty(OpenedClassReader.PROCESSOR_PROPERTY, "CLASS_FILE_API_FIRST");

            this.advisorFactory = AdvisorFactory.Creator.INSTANCE.create(aopContext);


            // 6.create AopWeaver
            aopWeaver = AopWeaver.Creator.INSTANCE.create(instrumentation, aopContext, advisorFactory);
            this.aopWeaver = aopWeaver;


            // 7.register shutdown hook
            Thread shutdownHook = new DaemonThreadFactory("ShutdownTask")
                    .newThread( () -> {
                        try {
                            DefaultAopLauncher.this.stop();

                            LOGGER.info("Stopped AopLauncher.");
                        } catch (Exception e) {/* ignored */}
                    } );
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } finally {
            replayDeferredMessages(configView);

            launcherMetrics.startupAopLauncher();

            ThreadContext.setContextClassLoader(existingClassLoader);
        }
    }

    private void replayDeferredMessages(ConfigView configView) {
        Level loggingLevel = Level.INFO;
        if (configView != null) {
            String allLoggingLevel = configView.getAsString(LoggingSystem.LOGGER_ALL_LOG_LEVEL_KEY, null);

            if (StringUtils.hasLength(allLoggingLevel))
                try {
                    loggingLevel = Level.valueOf(allLoggingLevel.toUpperCase(Locale.ENGLISH));
                } catch (Exception ignored) { /* do nothing */ }
        }

        DeferredLoggerFactory.replayDeferredMessages(loggingLevel);
    }

    /**
     * Configures ByteBuddy settings as eagerly as possible before any ByteBuddy API invocation.
     * 
     * @param aopContext aop context
     */
    private void configureByteBuddy(AopContext aopContext) {
        // enable Class-File API under JDK 24+.
        // put this setting to system properties before {@code AsmClassReader}
        // and {@code AsmClassWriter} class initialization to keep FACTORY field keeps in sysc
        // to avoid byte code generation conflict.
        System.getProperties().setProperty(OpenedClassReader.PROCESSOR_PROPERTY, "CLASS_FILE_API_FIRST");
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Configured ByteBuddy AsmClassReader as '{}', AsmClassWriter as '{}'.",
                    AsmClassReader.Factory.Default.CLASS_FILE_API_FIRST,
                    AsmClassWriter.Factory.Default.CLASS_FILE_API_FIRST
            );

        // disable Nexus
        System.getProperties().setProperty(Nexus.PROPERTY, "true");

        // set byte code dump path
        if (aopContext.isDumpByteCode()) {
            String byteCodeDumpPath = aopContext.getByteCodeDumpPath();
            File path = new File(byteCodeDumpPath + File.separator + "byte-buddy");
            path.mkdirs();

            System.getProperties().setProperty(TypeWriter.DUMP_PROPERTY, path.getAbsolutePath());
        }
    }

    private void configureClassLoader(Instrumentation instrumentation, 
            Map<String, Object> builtinSettings, AopContext aopContext, LauncherMetrics launcherMetrics) {
        // 1.configure BootstrapClassLoader and AopClassLoader with bootstrap classes
        long startedAt = System.nanoTime();

        BootstrapClassConfigurer bootstrapClassConfigurer = new BootstrapClassConfigurer(instrumentation, 
                aopContext.getDiagnosticLevel(), aopContext.isDumpByteCode(), aopContext.getByteCodeDumpPath());
        Map<String, String> nameMapping = bootstrapClassConfigurer
                .configureProviderClasses(aopContext.getAopClassLoader(), aopContext.getClassScanner());
        bootstrapClassConfigurer.configureConsumerClasses(aopContext.getAopClassLoader(), aopContext.getClassScanner(), nameMapping);

        builtinSettings.put(AopContext.BOOTSTRAP_CLASS_NAME_MAPPING_KEY, nameMapping);

        launcherMetrics.setBootstrapClassConfigTime(System.nanoTime() - startedAt);


        // 2.configure AopClassLoader
        startedAt = System.nanoTime();

        new AopClassLoaderConfigurer(aopContext)
                .configure(aopContext.getAopClassLoader(), aopContext.getClassScanner(), nameMapping);

        launcherMetrics.setAopClassLoaderConfigTime(System.nanoTime() - startedAt);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        try {
            this.aopWeaver.close();
            this.advisorFactory.close();
            this.aopContext.close();;
        } catch (IOException e) {/* ignored */}
    }
}
