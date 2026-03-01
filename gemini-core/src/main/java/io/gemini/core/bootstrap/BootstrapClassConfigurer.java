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
package io.gemini.core.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.aop.AopException;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.object.ClassRenamer;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.IOUtils;
import io.gemini.core.util.SingleEnumeration;
import io.gemini.core.util.StringUtils;
import io.github.classgraph.AnnotationClassRef;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import net.bytebuddy.description.type.PackageDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;

/**
 * This utility class configures 'bootstrap' AOP framework classes annotated with 
 * {@code @BootstrapClassProvider} and injects them into bootstrap ClassLoader.
 * 
 * <p>
 * After JDK9, these classes could not be placed under 'java.lang' package 
 * since this package name belongs to 'java.base' module. 
 * 
 * <p>
 * To resolve this package name conflict, AOP framework,
 * <ol>
 * <li>place 'bootstrap' classes under 'io.gemini' package at compilation time.
 * <li>scan 'bootstrap' classes, rename package name defined by {@code @BootstrapClassProvider.scopeType},
 * and inject renamed classes into bootstrap ClassLoader when launching,
 * <li>transform classes annotated with {@code @BootstrapClassConsumer}, and rename referred
 * 'bootstrap' classes.
 * </ol>
 * 
 * 
 * @author   martin.liu
 * @since	 1.0
 */
public class BootstrapClassConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapClassConfigurer.class);


    private final Instrumentation instrumentation;

    private final DiagnosticLevel diagnosticLevel;

    private final boolean dumpByteCode;
    private final String byteCodeDumpPath;


    public BootstrapClassConfigurer(Instrumentation instrumentation, DiagnosticLevel diagnosticLevel, 
            boolean dumpByteCode, String byteCodeDumpPath) {
        Assert.notNull(instrumentation, "'instrumentation' must not be null.");
        this.instrumentation = instrumentation;

        this.diagnosticLevel = diagnosticLevel;

        this.dumpByteCode = dumpByteCode;
        this.byteCodeDumpPath = byteCodeDumpPath;
    }


    /**
     * scan 'bootstrap' classes, rename package name defined by {@code @BootstrapClassProvider.scopeType},
     * and inject renamed classes into bootstrap ClassLoader when launching.
     * 
     * @param sourceClassLoader
     * @param classScanner
     * @return
     */
    public Map<String, String> configureProviderClasses(ClassLoader sourceClassLoader, ClassScanner classScanner) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("^Configuring BoostrapClassLoader with BootstrapClasses, ");
        }

        // check input arguments
        Assert.notNull(sourceClassLoader, "'sourceClassLoader' must not be null.");
        Assert.notNull(classScanner, "'classScanner' must not be null.");


        Map<String, String> nameMapping = Collections.emptyMap();
        try {
            Map<Class<?>, Set<ProviderClass>> providerClasses = scanProviderClasses(sourceClassLoader, classScanner);

            nameMapping = providerClasses.entrySet().stream()
                    .flatMap( e -> e.getValue().stream() )
                    .map( provider -> new SimpleEntry<String, String>(provider.getSrcClassName(), provider.getDestClassName()) )
                    .collect( Collectors.toMap( Entry::getKey, Entry::getValue) );

            injectByteCode(sourceClassLoader, providerClasses, getClassRenamer(nameMapping));

            long time = System.nanoTime() - startedAt;
            if (LOGGER.isInfoEnabled()) {
                if (diagnosticLevel.isDebugEnabled())
                    LOGGER.info("$Took '{}' seconds to configure BoostrapClassLoader with renamed BootstrapClass, \n"
                            + "  {} \n", 
                            time / 1e9, 
                            StringUtils.join(nameMapping.entrySet(), entry -> entry.getKey() + " => " + entry.getValue(), "\n  ") 
                    );
                else if (diagnosticLevel.isSimpleEnabled())
                    LOGGER.info("$Took '{}' seconds to configure BoostrapClassLoader.", time / 1e9);
            }

            return nameMapping;
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("$Could not configure BootstrapClassLoader with renamed BootstrapClass. \n"
                        + "  {}", 
                        StringUtils.join(nameMapping.entrySet(), entry -> entry.getKey() + " => " + entry.getValue(), "\n  "), 
                        e);

            throw new AopException(e);
        }
    }


    private ClassRenamer getClassRenamer(Map<String, String> nameMapping) {
        return new ClassRenamer.Default(nameMapping, dumpByteCode, byteCodeDumpPath);
    }

    private Map<Class<?>, Set<ProviderClass>> scanProviderClasses(ClassLoader sourceClassLoader, 
            ClassScanner classScanner) throws ClassNotFoundException, IOException {
        // discover bootstrap classes
        Class<BootstrapClassProvider> annotationClass = BootstrapClassProvider.class;
        ClassInfoList classesWithAnnotation = classScanner.getClassesWithAnnotation(annotationClass.getName());

        Map<Class<?>, Set<ProviderClass>> providerClasses = new LinkedHashMap<>(classesWithAnnotation.size());
        for (ClassInfo classInfo : classesWithAnnotation) {
            AnnotationInfo annotationinfo = classInfo.getAnnotationInfo(annotationClass);
            AnnotationClassRef classRef = (AnnotationClassRef) annotationinfo.getParameterValues().get("scopeType").getValue();
            Class<?> directiveClss = sourceClassLoader.loadClass(classRef.getName());

            providerClasses.computeIfAbsent(
                    directiveClss, 
                    key -> new HashSet<>()
            )
            .add( 
                    new ProviderClass(sourceClassLoader, directiveClss, classInfo.getName()) 
            );
        }

        return providerClasses;
    }

    private void injectByteCode(ClassLoader sourceClassLoader, 
            Map<Class<?>, Set<ProviderClass>> providerClasses, ClassRenamer classRenamer) throws IllegalClassFormatException {
        // inject into bootstrap class loader with ClassInjector
        // Instrumentation.appendToBootstrapClassLoaderSearch(...) does NOT support java.lang.* class injection

        // 1. use sun.misc.Unsafe::defineClass on JDK 8 and below.
        if (ClassInjector.UsingLookup.isAvailable() == false) {
            Map<String, byte[]> classBytecodeMap = new HashMap<>();
            for (Entry<Class<?>, Set<ProviderClass>> entry : providerClasses.entrySet()) {
                for(ProviderClass provider : entry.getValue()) {
                    byte[] renamedByteCode = classRenamer.map(provider.getSrcClassName(), provider.getByteCode());
                    classBytecodeMap.put(provider.getDestClassName(), renamedByteCode);
                }
            }

            ClassInjector.UsingUnsafe.ofBootLoader().injectRaw( classBytecodeMap );
            return;
        }


        // 2. use MethodHandles.privateLookup::defineClass under JDK9 or later
        for (Entry<Class<?>, Set<ProviderClass>> entry : providerClasses.entrySet()) {
            Class<?> lookupType = entry.getKey();

            JavaModule typeModule = JavaModule.ofType(lookupType);
            PackageDescription typePackageDescription = new PackageDescription.ForLoadedPackage(lookupType.getPackage());

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
            ClassInjector classInjector = ClassInjector.UsingLookup.of(MethodHandles.lookup()).in(lookupType);

            // inject byte code
            Map<String, byte[]> classBytecodeMap = new HashMap<>();
            for(ProviderClass provider : entry.getValue()) {
                byte[] renamedByteCode = classRenamer.map(provider.getSrcClassName(), provider.getByteCode());
                classBytecodeMap.put(provider.getDestClassName(), renamedByteCode);
            }

            classInjector.injectRaw( classBytecodeMap );
        }
    }


    /**
     * transform classes annotated with {@code @BootstrapClassConsumer}, and rename referred 'bootstrap' classes.
     * 
     * @param aopClassLoader
     * @param classScanner
     * @param nameMapping
     */
    public void configureConsumerClasses(AopClassLoader aopClassLoader, ClassScanner classScanner, Map<String, String> nameMapping) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("^Configuring AopClassLoader with BootstrapClasses, ");
        }

        // discover consumer classes
        List<String> consumerClassNames = classScanner.getClassNamesWithAnnotation(BootstrapClassConsumer.class.getName());

        ClassRenamer classRenamer = getClassRenamer(nameMapping);

        Map<String, byte[]> typeMap = new LinkedHashMap<>();
        Map<String, URL> resourceMap = new LinkedHashMap<>(consumerClassNames.size());
        try {
            for (String className : consumerClassNames) {
                String path = ClassUtils.convertClassToResource(className, true);
                InputStream inputStream = aopClassLoader.getResourceAsStream(path);

                byte[] byteCode = classRenamer.map(className, IOUtils.toByteArray(inputStream) );

                typeMap.put(className, byteCode);
                resourceMap.put(path, IOUtils.toURL(path, byteCode));
            }
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Could not load BootstrapClass consumer class", e);

            throw new AopException(e);
        }

        aopClassLoader.addTypeFinder( new AopClassLoader.TypeFinder() {

            @Override
            public byte[] findByteCode(String name) {
                return typeMap.get(name);
            }


            @Override
            public URL findResource(String name) {
                return resourceMap.get(name);
            }

            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                 return new SingleEnumeration<URL>( resourceMap.get(name) );
            }
        } );

        long time = System.nanoTime() - startedAt;
        if (LOGGER.isInfoEnabled()) {
            if (diagnosticLevel.isDebugEnabled())
                LOGGER.info("$Took '{}' seconds to configure AopClassLoader with renamed BootstrapClass, \n"
                        + "  {} \n", 
                        time / 1e9, 
                        StringUtils.join(consumerClassNames, "\n  ") 
                );
            else if (diagnosticLevel.isSimpleEnabled())
                LOGGER.info("$Took '{}' seconds to configure aopClassLoader with renamed BootstrapClass.", time / 1e9);
        }
    }


    static class ProviderClass {

        private final Class<?> scopeType;

        private final String srcClassName;
        private final String destClassName;
        private final byte[] byteCode;


        public ProviderClass(ClassLoader sourceClassLoader, Class<?> scopeType, String srcClassName) throws IOException {
            this.scopeType = scopeType;

            this.srcClassName = srcClassName;

            // get destClassName
            String destPkgName = scopeType.getPackage().getName();

            int pos = srcClassName.lastIndexOf( ClassUtils.PACKAGE_SEPARATOR );
            this.destClassName = destPkgName + ClassUtils.PACKAGE_SEPARATOR 
                    + (pos == -1 ? srcClassName : srcClassName.substring(pos + 1));

            // load bytecode
            this.byteCode = IOUtils.toByteArray(
                    sourceClassLoader.getResourceAsStream(
                            ClassUtils.convertClassToResource(srcClassName, true) ) );
        }


        public Class<?> getScopeType() {
            return scopeType;
        }

        public String getSrcClassName() {
            return srcClassName;
        }

        public String getDestClassName() {
            return destClassName;
        }

        public byte[] getByteCode() {
            return byteCode;
        }
    }
}