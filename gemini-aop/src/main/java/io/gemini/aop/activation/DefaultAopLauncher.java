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
package io.gemini.aop.activation;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics.BootstraperMetrics;
import io.gemini.aop.AspectFactory;
import io.gemini.aop.AspectWeaver;
import io.gemini.aop.activation.support.AopClassLoaderConfigurer;
import io.gemini.aop.activation.support.BootstrapClassLoaderConfigurer;
import io.gemini.aop.aspectory.Aspectories;
import io.gemini.aop.weaver.AspectWeavers;
import io.gemini.api.activation.AopLauncher;
import io.gemini.api.activation.LauncherConfig;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.concurrent.DaemonThreadFactory;
import io.gemini.core.config.ConfigSource;
import io.gemini.core.config.ConfigViews;
import io.gemini.core.logging.LoggingSystem;

public class DefaultAopLauncher implements AopLauncher {

    private static final String DIAGNOSTIC_LEVEL_KEY = "aop.launcher.diagnosticStrategy";


    private AopContext aopContext;
    private AspectFactory aspectFactory;
    private AspectWeaver aspectWeaver;


    public DefaultAopLauncher() {
    }

    @Override
    public void start(Instrumentation instrumentation, 
            LauncherConfig launcherConfig,
            AopClassLoader aopClassLoader) {
        ClassLoader existingClassLoader = Thread.currentThread().getContextClassLoader();

        AspectWeaver aspectWeaver = null;
        BootstraperMetrics bootstraperMetrics = null;
        try {
            // set AopClassLoader as T.C. ClassLoader
            // existing T.C. ClassLoader, generally is AppClassLoader, might contain libraries, such as log4j2, 
            // and conflict with AopClassLoader
            Thread.currentThread().setContextClassLoader(aopClassLoader);


            // 1.load AOP settings
            Map<String, Object> builtinSettings = new LinkedHashMap<>();
            builtinSettings.put("aop.launcher.launchPath", launcherConfig.getLaunchPath());

            ConfigSource configSource = ConfigViews.createConfigSource(
                    launcherConfig.getLaunchArgs(), builtinSettings,
                    aopClassLoader,
                    launcherConfig.getInternalConfigLocation(), 
                    Collections.singletonMap(launcherConfig.getUserDefinedConfigLocation(), "aop-context")
            );
            DiagnosticLevel diagnosticLevel = this.parseDiagnosticLevel(configSource);


            // 2.initialize LoggingSystem and create logger instance
            // !!! do not create logger instance (using default configuration) before configuring LoggingSystem !!!
            long startedAt = System.nanoTime();
            Logger logger = createLogger(launcherConfig, aopClassLoader, configSource, diagnosticLevel);
            long loggerCreationTime = System.nanoTime() - startedAt;


            // 3.create helper classes
            this.aopContext = new AopContext(launcherConfig, aopClassLoader, 
                    builtinSettings, configSource, diagnosticLevel);
            bootstraperMetrics = aopContext.getAopMetrics().getBootstraperMetrics();
            bootstraperMetrics.setLauncherStartedAt(launcherConfig.getLaunchedAt());
            bootstraperMetrics.setLoggerCreationTime(loggerCreationTime);


            // 4.configure ClassLoaders
            configureClassLoader(instrumentation, builtinSettings, aopContext);


            // 5.create AspectFactory
            this.aspectFactory = Aspectories.createAspectFactory(aopContext);


            // 6.create AspectWeaver
            aspectWeaver = AspectWeavers.createAspectWeaver(instrumentation, aopContext, aspectFactory);
            this.aspectWeaver = aspectWeaver;


            // 7.register shutdown hook
            Thread shutdownHook = new DaemonThreadFactory("ShutdownTask")
                    .newThread( () -> {
                        try {
                            DefaultAopLauncher.this.stop();

                            logger.info("Stopped AopLauncher.");
                        } catch (Exception e) {/* ignored */}
                    } );
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } finally {
            if(bootstraperMetrics != null) {
                bootstraperMetrics.setLauncherStartupTime(System.nanoTime() - launcherConfig.getLaunchedAt());
            }

            Thread.currentThread().setContextClassLoader(existingClassLoader);
        }
    }

    private DiagnosticLevel parseDiagnosticLevel(ConfigSource configSource) {
        if(configSource.containsKey(DIAGNOSTIC_LEVEL_KEY)) {
            String level = configSource.getValue(DIAGNOSTIC_LEVEL_KEY)
                    .toString()
                    .trim().toUpperCase();
            try {
                return DiagnosticLevel.valueOf(level);
            } catch(Exception e) {
                System.err.println("Ignored illegal setting '" + level + "' and disabled diagnostic mode.\n");
            }
        }

        return DiagnosticLevel.DISABLED;
    }

    private Logger createLogger(LauncherConfig launcherConfig, AopClassLoader aopClassLoader, ConfigSource configSource,
            DiagnosticLevel diagnosticLevel) {
        LoggingSystem loggingSystem = new LoggingSystem.Builder()
                .configSource(configSource)
                .diagnosticLevel(diagnosticLevel)
                .build()
                ;
        loggingSystem.initialize(aopClassLoader);

        return LoggerFactory.getLogger(getClass());
    }

    private void configureClassLoader(Instrumentation instrumentation, 
            Map<String, Object> builtinSettings, AopContext aopContext) {
        // 1.configure BootstrapClassLoader with bootstrap classes
        Map<String, String> nameMapping = new BootstrapClassLoaderConfigurer(
                instrumentation, aopContext)
                .configure(aopContext.getAopClassLoader());

        builtinSettings.put(AopContext.BOOTSTRAP_CLASS_NAME_MAPPING_KEY, nameMapping);


        // 2.configure AopClassLoader 
        new AopClassLoaderConfigurer(aopContext)
                .configure(aopContext.getAopClassLoader(), nameMapping);
    }


    @Override
    public void stop() {
        try {
            this.aspectWeaver.close();
            this.aspectFactory.close();
            this.aopContext.close();;
        } catch (IOException e) {/* ignored */}
    }
}
