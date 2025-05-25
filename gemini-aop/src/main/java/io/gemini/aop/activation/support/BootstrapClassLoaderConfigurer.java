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
package io.gemini.aop.activation.support;

import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopException;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AopMetrics.BootstraperMetrics;
import io.gemini.aop.java.lang.BootstrapClassProvider;
import io.gemini.core.object.ClassRenamer;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.IOUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.type.PackageDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class BootstrapClassLoaderConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapClassLoaderConfigurer.class);


    private final Instrumentation instrumentation;

    private final AopContext aopContext;


    public BootstrapClassLoaderConfigurer(Instrumentation instrumentation, AopContext aopContext) {
        Assert.notNull(instrumentation, "'instrumentation' must not be null.");
        this.instrumentation = instrumentation;

        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;
    }


    public Map<String, String> configure(ClassLoader sourceClassLoader) {
        // check input arguments
        Assert.notNull(sourceClassLoader, "'sourceClassLoader' must not be null.");

        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("^Configuring BoostrapClassLoader with BootstrapClasses,");
        }

        Map<String, String> nameMapping = null;
        BootstraperMetrics bootstraperMetrics = aopContext.getAopMetrics().getBootstraperMetrics();
        try {
            nameMapping = scanClassNameMapping();

            Map<String, byte[]> classByteCodeMap = loadClassByteCode(sourceClassLoader, nameMapping);

            injectByteCode(classByteCodeMap);

            long time = System.nanoTime() - startedAt;
            bootstraperMetrics.setBootstrapCLConfigTime(time);
            if(aopContext.getDiagnosticLevel().isSimpleEnabled()) 
                LOGGER.info("$Took '{}' seconds to configure BoostrapClassLoader with renamed BootstrapClass. \n  {} \n", 
                        time / AopMetrics.NANO_TIME, 
                        StringUtils.join(nameMapping.entrySet(), entry -> entry.getKey() + " => " + entry.getValue(), "\n  ") 
                );

            return nameMapping;
        } catch (Exception e) {
            LOGGER.warn("$Failed to configure BootstrapClassLoader with renamed BootstrapClass. \n  ", 
                    StringUtils.join(nameMapping.entrySet(), entry -> entry.getKey() + " => " + entry.getValue(), "\n  "), 
                    e);

            throw new AopException(e);
        }
    }


    private Map<String, String> scanClassNameMapping() {
        // 1.discover bootstrap classes
        // load configured classes.
        Set<String> bootstrapClassNames = aopContext.getConfigView().getAsStringSet("aop.bootstrapClassLoader.bootstrapClasses", new LinkedHashSet<>());
        // discover built-in classes
        bootstrapClassNames.addAll(
                aopContext.getClassScanner().getClassNamesWithAnnotation(BootstrapClassProvider.class.getName()) );
        Assert.notEmpty(bootstrapClassNames, "'BootstrapClass' must not be empty.");


        // 2.validate bootstrap class package name
        Map<String, String> nameMapping = new LinkedHashMap<>(bootstrapClassNames.size());
        for(String bootstrapClassName : bootstrapClassNames) {
            int pos = bootstrapClassName.indexOf(".java.");
            if(pos == -1) {
                LOGGER.warn("Ignored candidate bootstrap class '{}' since full class name should be 'xxx.yyy.java.*'.",  bootstrapClassName);
                continue;
            }

            // record (xxx.yyy.java.*, java.*) pair
            nameMapping.put(bootstrapClassName, bootstrapClassName.substring(pos + 1));
        }

        return nameMapping;
    }

    private Map<String, byte[]> loadClassByteCode(ClassLoader sourceClassLoader, Map<String, String> nameMapping) 
            throws IOException, IllegalClassFormatException {
        ClassRenamer classRenamer = new ClassRenamer.Default(
                nameMapping, 
                aopContext.isDumpByteCode(),
                aopContext.getByteCodeDumpPath()
        );

        // load byte code of bootstrap classes
        Map<String, byte[]> classByteCodeMap = new LinkedHashMap<>(nameMapping.size());
        for(Entry<String, String> entry : nameMapping.entrySet()) {
            String oldClassName = entry.getKey();
            String oldClassPath = ClassUtils.convertClassToResource(oldClassName, true);
            byte[] byteCode = IOUtils.toByteArray(
                    sourceClassLoader.getResourceAsStream(oldClassPath) );

            classByteCodeMap.put(entry.getValue(), 
                    classRenamer.map(oldClassName, byteCode));
        }

        return classByteCodeMap;
    }

    private void injectByteCode(Map<String, byte[]> classByteCodeMap) {
        // inject into bootstrap class loader with ClassInjector
        // Instrumentation.appendToBootstrapClassLoaderSearch(...) does NOT support java.lang.* class injection
        ClassInjector classInjector = null;
        if(ClassInjector.UsingLookup.isAvailable()) {
            // 1. use MethodHandles.privateLookup::defineClass on JDK9 or later
            Class<String> type = String.class;
            JavaModule typeModule = JavaModule.ofType(type);
            PackageDescription typePackageDescription = new PackageDescription.ForLoadedPackage(type.getPackage());

            JavaModule reflectingModule = JavaModule.ofType(getClass());

            if (typeModule.isOpened(typePackageDescription, reflectingModule) == false) {
                // redefine module module/package(java.base/java.lang) to allow access from current unnamed module.
                ClassInjector.UsingInstrumentation.redefineModule(
                        instrumentation, 
                        typeModule,
                        Collections.emptySet(), 
                        Collections.emptyMap(),
                        Collections.singletonMap(typePackageDescription.getName(), Collections.singleton(reflectingModule)),      // opens
                        Collections.emptySet(), 
                        Collections.emptyMap());
            }

            // Lookup::defineClass is alternative of Unsafe::defineClass on JDK 9+, 
            // and perform better than UsingUnsafeOverrite.
            classInjector = ClassInjector.UsingLookup.of(MethodHandles.lookup()).in(type);
        } else {
            // 2. use sun.misc.Unsafe::defineClass on JDK 8 and below.
            classInjector = ClassInjector.UsingUnsafe.ofBootLoader();
        }

        // 3. inject BootstrapClass
        classInjector.injectRaw(classByteCodeMap);
    }

}