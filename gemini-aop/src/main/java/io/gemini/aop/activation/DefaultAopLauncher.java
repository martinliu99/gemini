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

import io.gemini.aop.AdvisorFactory;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics.BootstraperMetrics;
import io.gemini.aop.AopWeaver;
import io.gemini.aop.activation.support.AopClassLoaderConfigurer;
import io.gemini.aop.activation.support.BootstrapClassLoaderConfigurer;
import io.gemini.aop.factory.AdvisorFactories;
import io.gemini.aop.weaver.AopWeavers;
import io.gemini.api.activation.AopLauncher;
import io.gemini.api.activation.LauncherConfig;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.concurrent.DaemonThreadFactory;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigViews;
import io.gemini.core.logging.DeferredLoggerFactory;
import io.gemini.core.logging.LoggingSystem;

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
        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

        AopWeaver aopWeaver = null;
        BootstraperMetrics bootstraperMetrics = null;
        try {
            // set AopClassLoader as T.C. ClassLoader
            // existing T.C. ClassLoader, generally is AppClassLoader, might contain libraries, such as log4j2, 
            // and conflict with AopClassLoader
            ThreadContext.setContextClassLoader(aopClassLoader);


            // 1.load AOP settings
            Map<String, Object> builtinSettings = new LinkedHashMap<>();
            builtinSettings.put("aop.launcher.launchPath", launcherConfig.getLaunchPath().toString());

            ConfigView configView = ConfigViews.createConfigView(
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
            long loggerCreationTime = System.nanoTime() - startedAt;
            long launcherSetupTime = System.nanoTime() - launcherConfig.getLaunchedAt();


            // 3.create helper classes
            this.aopContext = new AopContext(launcherConfig, aopClassLoader, 
                    builtinSettings, configView, diagnosticLevel);

            bootstraperMetrics = aopContext.getAopMetrics().getBootstraperMetrics();
            bootstraperMetrics.setLauncherStartedAt(launcherConfig.getLaunchedAt());
            bootstraperMetrics.setLauncherSetupTime(launcherSetupTime);
            bootstraperMetrics.setLoggerCreationTime(loggerCreationTime);


            // 4.configure ClassLoaders
            configureClassLoader(instrumentation, builtinSettings, aopContext);


            // 5.create AdvisorFactory
            this.advisorFactory = AdvisorFactories.createAdvisorFactory(aopContext);


            // 6.create AopWeaver
            aopWeaver = AopWeavers.createAopWeaver(instrumentation, aopContext, advisorFactory);
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
            if (bootstraperMetrics != null) {
                bootstraperMetrics.setLauncherStartupTime(System.nanoTime() - launcherConfig.getLaunchedAt());
            }

            ThreadContext.setContextClassLoader(existingClassLoader);
        }
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
            this.aopWeaver.close();
            this.advisorFactory.close();
            this.aopContext.close();;
        } catch (IOException e) {/* ignored */}
    }
}
