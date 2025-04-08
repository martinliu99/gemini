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
package io.gemini.aop.bootstraper;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopException;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AopMetrics.BootstraperMetrics;
import io.gemini.aop.agent.bootstraper.AopBootstraper;
import io.gemini.aop.agent.bootstraper.AopBootstraperConfig;
import io.gemini.aop.agent.classloader.AgentClassLoader;
import io.gemini.aop.support.AopSettingHelper;
import io.gemini.aop.weaver.AspectWeaver;
import io.gemini.aop.weaver.AspectWeaverInstaller;
import io.gemini.core.annotation.BootstrapClass;
import io.gemini.core.logging.LoggingSystem;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.utility.JavaModule;

public class DefaultAopBootstraper implements AopBootstraper {

    private AspectWeaver aspectWeaver;


    public DefaultAopBootstraper() {
    }

    @Override
    public void start(Instrumentation instrumentation, AopBootstraperConfig aopBootstraperConfig) {
        ClassLoader existingClassLoader = Thread.currentThread().getContextClassLoader();

        AspectWeaver aspectWeaver = null;
        BootstraperMetrics bootstraperMetrics = null;
        try {
            ClassLoader classLoader = this.getClass().getClassLoader();
            ClassLoaderUtils.validate(classLoader);
            if(classLoader instanceof AgentClassLoader == false)
                throw new IllegalStateException("DefaultAopBootstraper's class loader must be AgentClassLoader.");
            AgentClassLoader agentClassLoader = (AgentClassLoader) classLoader;

            // set AgentClassLoader as T.C. ClassLoader
            // existingClassLoader generally is AppClassLoader and will impact agent and some libraries, such as log4j2, logic 
            Thread.currentThread().setContextClassLoader(agentClassLoader);

            // 1.load AOP settings
            Map<String, String> builtinSettings = new LinkedHashMap<>();
            AopSettingHelper aopSettingHelper = new AopSettingHelper(aopBootstraperConfig, builtinSettings);


            // 2.initialize LoggingSystem
            // !!! do not create logger instance (using default configuration) before configuring LoggingSystem !!!
            long startedAt = System.nanoTime();
            LoggingSystem loggingSystem = createLoggingSystem(agentClassLoader, aopSettingHelper);

            // create logger instance
            Logger logger = LoggerFactory.getLogger(getClass());
            long loggerCreationTime = System.nanoTime() - startedAt;


            // 3.initialize AopContext
            AopContext aopContext = createAopContext(instrumentation, agentClassLoader, aopSettingHelper, builtinSettings, loggingSystem);

            bootstraperMetrics = aopContext.getAopMetrics().getBootstraperMetrics();
            bootstraperMetrics.setAgentStartedAt(aopBootstraperConfig.getAgentStartedAt());
            bootstraperMetrics.setLoggerCreationTime(loggerCreationTime);


            // 4.initialize ClassLoader
            // 4.1.inject built-in bootstrap classes, such as JreBootstrapHook into bootstrap ClassLoader in advance
            Set<String> bootstrapClassNames = this.installBootstrapClasses(aopContext, agentClassLoader, 
                    bootstraperMetrics, logger);

            // 4.2.initialize AgentClassLoader with parent first classes and resources
            this.installAgentClassLoader(aopContext, agentClassLoader, bootstrapClassNames, logger);


            // 5.initialize aspect weaver
            aspectWeaver = new AspectWeaverInstaller()
                    .aopContext(aopContext)
                    .build();
            this.aspectWeaver = aspectWeaver;
        } finally {
            if(bootstraperMetrics != null) {
                bootstraperMetrics.setAgentStartupTime(System.nanoTime() - aopBootstraperConfig.getAgentStartedAt());
            }

            Thread.currentThread().setContextClassLoader(existingClassLoader);
        }
    }

    private LoggingSystem createLoggingSystem(AgentClassLoader agentClassLoader, AopSettingHelper aopSettingHelper) {
        LoggingSystem loggingSystem = new LoggingSystem.Builder()
                .configLocation(aopSettingHelper.getLogConfigFileName())
                .configSource(aopSettingHelper.getConfigSource())
                .diagnosticLevel(aopSettingHelper.getDiagnosticLevel())
                .build()
                ;
        loggingSystem.initialize(agentClassLoader);

        return loggingSystem;
    }

    private AopContext createAopContext(Instrumentation instrumentation, AgentClassLoader agentClassLoader,
            AopSettingHelper aopSettingHelper, Map<String, String> builtinSettings, LoggingSystem loggingSystem) {
        long startedAt = System.nanoTime();

        AopContext aopContext = new AopContext(
                instrumentation, 
                agentClassLoader, 
                aopSettingHelper,
                builtinSettings,
                loggingSystem);
        aopContext.getAopMetrics().getBootstraperMetrics().setAopContextCreationTime(System.nanoTime() - startedAt);

        return aopContext;
    }

    private Set<String> installBootstrapClasses(AopContext aopContext, AgentClassLoader agentClassLoader, 
            BootstraperMetrics bootstraperMetrics, Logger logger) {
        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            logger.info("^Injecting below BootstrapClasses into BoostrapClassLoader,");
        }

        Set<String> bootstrapClassNames = new HashSet<>();
        try {
            // 1.discover bootstrap classes
            // discover built-in classes
            List<String> classNames = aopContext.getClassScanner().getClassNamesWithAnnotation(BootstrapClass.class.getName());
            Assert.notEmpty(classNames, "Builtin 'BootstrapClass' must not be empty.");
            bootstrapClassNames.addAll(classNames);

            // include configured classes
            bootstrapClassNames.addAll(aopContext.getBootstrapClasses());

            // 2.load byte code of bootstrap classes
            ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.of(agentClassLoader);
            Map<String, byte[]> classesTypeMap = new LinkedHashMap<>();
            for(String className : bootstrapClassNames) {
                byte[] byteCode = classFileLocator.locate(className).resolve();

                classesTypeMap.put(className, byteCode);
            }

            // 3.inject into bootstrap class loader with ClassInjector
            // Instrumentation.appendToBootstrapClassLoaderSearch(...) does NOT support java.lang.* class injection

            ClassInjector classInjector = null;
            if(ClassInjector.UsingLookup.isAvailable()) {
                // 1) use MethodHandles.privateLookup::defineClass on JDK9 or later
                Class<String> targetType = String.class;

                // open module access to target package(java.lang) of target module to current unnamed module.
                JavaModule unnamedModule = JavaModule.ofType(getClass());
                ClassInjector.UsingInstrumentation.redefineModule(
                        aopContext.getInstrumentation(), 
                        JavaModule.ofType(targetType),      // targetModule
                        Collections.emptySet(), 
                        Collections.emptyMap(),
                        Collections.singletonMap(targetType.getPackage().getName(), Collections.singleton(unnamedModule)),      // opens
                        Collections.emptySet(), 
                        Collections.emptyMap());

                // Lookup::defineClass is alternative of Unsafe::defineClass on JDK 9+, 
                // and perform better than UsingUnsafeOverrite.
                classInjector = ClassInjector.UsingLookup.of(MethodHandles.lookup()).in(targetType);
            } else {
                // 2) use sun.misc.Unsafe::defineClass on JDK 8 and below.
//                classInjector = ClassInjector.UsingUnsafe.ofBootLoader();
                classInjector = ClassInjector.UsingUnsafe.Factory.resolve(aopContext.getInstrumentation())
                        .make(ClassLoadingStrategy.BOOTSTRAP_LOADER, ClassLoadingStrategy.NO_PROTECTION_DOMAIN);
            }

            // inject BootstrapClass
            classInjector.injectRaw(classesTypeMap);


            long time = System.nanoTime() - startedAt;
            bootstraperMetrics.setBootstrapClassInjectionTime(time);
            if(aopContext.getDiagnosticLevel().isSimpleEnabled()) 
                logger.info("$Took '{}' seconds to inject BootstrapClass into BoostrapClassLoader. \n  {} \n", 
                        time / AopMetrics.NANO_TIME,
                        bootstrapClassNames.stream().collect( Collectors.joining("\n  ") ) );

            return bootstrapClassNames;
        } catch (Throwable t) {
            logger.warn("$Failed to inject classes '{}' into Bootstrap ClassLoader.", bootstrapClassNames, t);
            throw new AopException(t);
        }
    }

    private void installAgentClassLoader(AopContext aopContext, AgentClassLoader agentClassLoader, Set<String> bootstrapClassNames, Logger logger) {
        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            logger.info("^Initializing AgentClassLoader with settings,");
        }

        // 1.initialize parentFirstClassPrefixes
        Set<String> parentFirstClassPrefixes = new LinkedHashSet<>();
        parentFirstClassPrefixes.addAll(bootstrapClassNames);
        parentFirstClassPrefixes.addAll(aopContext.getParentFirstClassPrefixes());

        agentClassLoader.setParentFirstClassPrefixes(parentFirstClassPrefixes);

        // 2.initialize parentFirstResources
        Set<String> parentFirstResourcePrefixes = aopContext.getParentFirstResourcePrefixes();
        agentClassLoader.setParentFirstResourcePrefixes(parentFirstResourcePrefixes);

        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) 
            logger.info("$Took '{}' seconds to initialize AgentClassLoader. \n"
                    + "  parentFirstClassPrefixes: {}  parentFirstResourcePrefixes: {}", 
                    (System.nanoTime() - startedAt) / 1e9,
                    parentFirstClassPrefixes.size() == 0 ? "" : parentFirstClassPrefixes.stream().collect( Collectors.joining("\n    ", "\n    ", "\n") ), 
                    parentFirstResourcePrefixes.size() == 0 ? "" : parentFirstResourcePrefixes.stream().collect( Collectors.joining("\n    ", "\n    ", "\n") )
            );
    }


    @Override
    public void stop() {
        // TODO: try to clean up resources
        this.aspectWeaver.close();
    }
}
