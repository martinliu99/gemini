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
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.api.aop.AopException;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.object.ClassRenamer;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.IOUtils;
import io.gemini.core.util.SingleEnumeration;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AopClassLoaderConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AopClassLoaderConfigurer.class);

    private final static Set<String /* Class prefix */ > CONDITIONAL_BUILTIN_LAUNCHER_FIRST_CLASS_PREFIXES;


    private final AopContext aopContext;


    static {
        CONDITIONAL_BUILTIN_LAUNCHER_FIRST_CLASS_PREFIXES = new LinkedHashSet<>();
        CONDITIONAL_BUILTIN_LAUNCHER_FIRST_CLASS_PREFIXES.add("org.aspectj.lang.annotation");
    }


    public AopClassLoaderConfigurer(AopContext aopContext) {
        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;
    }


    public void configure(AopClassLoader aopClassLoader, ClassScanner classScanner, Map<String, String> nameMapping) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("^Configuring AopClassLoader, ");
        }

        // 1.create LauncherFirstFilter with launcherFirstTypeExpressions and launcherFirstResourceExpressions
        Set<String> launcherFirstTypeExpressions = new LinkedHashSet<>();
        Set<String> launcherFirstResourceExpressions = new LinkedHashSet<>();

        this.configureLauncherFirstFilter(aopClassLoader,
                launcherFirstTypeExpressions, launcherFirstResourceExpressions);


        // 2.create BootstrapClassFilter with bootstrap classes.
        this.configureBoostrapClassFilter(aopClassLoader, nameMapping);


        // 3.create BootstrapClassConsumerTypeFilter
        configureBootstrapClassConsumerClassFinder(aopClassLoader, classScanner, nameMapping);


        long time =  System.nanoTime() - startedAt;
        if (LOGGER.isInfoEnabled()) {
            if (aopContext.getDiagnosticLevel().isDebugEnabled()) 
                LOGGER.info("$Took '{}' seconds to configure AopClassLoader with settings, \n"
                        + "  launcherFirstTypeExpressions: {}"
                        + "  launcherFirstResourceExpressions: {}",
                        time / AopMetrics.NANO_TIME,
                        StringUtils.join(launcherFirstTypeExpressions, "\n    ", "\n    ", "\n"), 
                        StringUtils.join(launcherFirstResourceExpressions, "\n    ", "\n    ", "\n")
                );
            else if (aopContext.getDiagnosticLevel().isSimpleEnabled()) 
                LOGGER.info("$Took '{}' seconds to configure AopClassLoader.", time / AopMetrics.NANO_TIME);
        }

        aopContext.getAopMetrics().getLauncherMetrics().setAopCLConfigTime(time);
    }

    private void configureLauncherFirstFilter(AopClassLoader aopClassLoader, 
            Set<String> launcherFirstTypeExpressions, Set<String> launcherFirstResourceExpressions) {
        // 1.collect launcher-first type expressions
        launcherFirstTypeExpressions.addAll(
                aopContext.getConfigView().getAsStringSet("aop.aopClassLoader.builtinLauncherFirstTypeExpressions", Collections.emptySet()) );
        launcherFirstTypeExpressions.addAll(
                aopContext.getConfigView().getAsStringSet("aop.aopClassLoader.launcherFirstTypeExpressions", Collections.emptySet()) );

        if (aopContext.isClassesFolderScanned())
            launcherFirstTypeExpressions.addAll( CONDITIONAL_BUILTIN_LAUNCHER_FIRST_CLASS_PREFIXES );

        ElementMatcher<String> launcherFirstClassMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                "LauncherFirstClassMatcher", launcherFirstTypeExpressions, ElementMatchers.none());


        // 2.collect launcher-first resource expressions
        launcherFirstResourceExpressions.addAll( 
                aopContext.getConfigView().getAsStringSet("aop.aopClassLoader.launcherFirstResourceExpressions", Collections.emptySet()) );

        // convert launcherFirstTypeExpressions and merge into launcherFirstResourceExpressions
        launcherFirstResourceExpressions.addAll( launcherFirstTypeExpressions );

        ElementMatcher<String> launcherFirstResourceMatcher = ElementMatcherFactory.INSTANCE.createResourceNameMatcher(
                "LauncherFirstResourceMatcher", launcherFirstTypeExpressions, ElementMatchers.none());


        // 3.add LauncherFirstFilter
        aopClassLoader.addLauncherFirstFilter( new AopClassLoader.LauncherFirstFilter() {

            @Override
            public boolean isLauncherFirstClass(String name) {
                return launcherFirstClassMatcher.matches(name);
            }

            @Override
            public boolean isLauncherFirstResource(String name) {
                return launcherFirstResourceMatcher.matches(name);
            }
        });
    }


    private void configureBoostrapClassFilter(AopClassLoader aopClassLoader, Map<String, String> nameMapping) {
        ElementMatcher<String> bootstrapClassMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                "BootstrapClassMatcher", nameMapping.keySet(), ElementMatchers.none());

        ElementMatcher<String> bootstrapResourceMatcher = ElementMatcherFactory.INSTANCE.createResourceNameMatcher(
                "BootstrapResourceMatcher", nameMapping.keySet(), ElementMatchers.none());

        aopClassLoader.addTypeFilter( new AopClassLoader.TypeFilter() {

            private final String classLoaderName = ClassLoader.class.getName();

            @Override
            public String filterTypeName(String name) {
                if (bootstrapClassMatcher.matches(name)) {
                    handleBootstrapClassRenamingException(name);
                }

                return name;
            }

            @Override
            public String filterResourceName(String name) {
                if (bootstrapResourceMatcher.matches(name)) {
                    handleBootstrapClassRenamingException(name);
                }

                return name;
            }

            private void handleBootstrapClassRenamingException(String name) {
                StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();

                // find first call site for DefaultAopClassLoader#loadClass
                StackTraceElement invokingCode = null;
                if (stackTraceElements != null && stackTraceElements.length != 0) {
                    for (int i = stackTraceElements.length - 1; i >= 0 ; i--) {
                        StackTraceElement stackTraceElement = stackTraceElements[i];
                        if (classLoaderName.equals(stackTraceElement.getClassName()) == false
                                || "loadClass".equals(stackTraceElement.getMethodName()) == false)
                            continue;

                        invokingCode = stackTraceElements[i+1];
                        break;
                    }
                }

                String errorMessage = "Detected code " 
                        + (invokingCode == null ? "" : "(" + invokingCode + ") ") + "referring to " + name 
                        + " which should be renamed at runtime via @" + BootstrapClassConsumer.class.getName();
                throw new AopException(errorMessage );
            }
        } );
    }

    private void configureBootstrapClassConsumerClassFinder(AopClassLoader aopClassLoader, ClassScanner classScanner, Map<String, String> nameMapping) {
        // discover consumer classes
        List<String> consumerClassNames = classScanner.getClassNamesWithAnnotation(BootstrapClassConsumer.class.getName());

        ClassRenamer classRenamer = new ClassRenamer.Default(
                nameMapping, 
                aopContext.isByteCodeDumped(),
                aopContext.getByteCodeDumpPath()
        );

        Map<String, byte[]> classesTypeMap = new LinkedHashMap<>();
        Map<String, URL> classResourceMap = new LinkedHashMap<>(consumerClassNames.size());
        try {
            for (String className : consumerClassNames) {
                String path = ClassUtils.convertClassToResource(className, true);
                InputStream inputStream = aopClassLoader.getResourceAsStream(path);

                byte[] byteCode = classRenamer.map(className, IOUtils.toByteArray(inputStream) );

                classesTypeMap.put(className, byteCode);
                classResourceMap.put(path, IOUtils.toURL(path, byteCode));
            }
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Could not load BootstrapClass consumer class", e);

            throw new AopException(e);
        }

        aopClassLoader.addTypeFinder( new AopClassLoader.TypeFinder() {

            @Override
            public byte[] findByteCode(String name) {
                return classesTypeMap.get(name);
            }


            @Override
            public URL findResource(String name) {
                return classResourceMap.get(name);
            }

            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                 return new SingleEnumeration<URL>( classResourceMap.get(name) );
            }
        } );
    }
}