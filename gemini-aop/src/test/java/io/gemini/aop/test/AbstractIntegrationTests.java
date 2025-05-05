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
package io.gemini.aop.test;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.activation.classloader.DefaultAopClassLoader;
import io.gemini.activation.support.AspectoryScanner;
import io.gemini.activation.support.LauncherScanner;
import io.gemini.activation.support.UnpackedArchiveConfig;
import io.gemini.aop.activation.DefaultAopLauncher;
import io.gemini.api.activation.AopLauncher;
import io.gemini.api.activation.LauncherConfig;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.SingleEnumeration;
import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public abstract class AbstractIntegrationTests {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractIntegrationTests.class);

    private static boolean LAUNCHED = false;


    @BeforeAll
    public static void beforeAllTests() throws Exception {
        // launch AopLauncher and load all aspects
        if(LAUNCHED == false) {
            start();
            LAUNCHED = true;
        }
    }


    @SuppressWarnings("unchecked")
    protected static void start() throws Exception {
        // 1.prepare arguments
        Instrumentation instrumentation = ByteBuddyAgent.install();

        CodeSource codeSource = AbstractIntegrationTests.class.getProtectionDomain().getCodeSource();
        String launchLocation = new File(codeSource.getLocation().toURI()).getParentFile().getPath() + File.separator + "int-test" + File.separator;
        Path launchPath = Paths.get(launchLocation);
        if(Files.exists(launchPath) == false)
            Files.createDirectory( launchPath);

        LauncherConfig launcherConfig = new UnpackedArchiveConfig(launchPath, "",
                new TestLauncherScanner(), 
                true,
                new AspectoryScanner.ClassesFolder() );

        List<String> classPaths = ClassLoaderUtils.getClassPaths();
        List<URL> classLoaderUrls = new ArrayList<>(classPaths.size());
        Map<String, URL> externalResourceMap = new LinkedHashMap<>();
        for(String classPath : classPaths) {
            collectURLResources(classPath, classLoaderUrls, externalResourceMap);
        }

        AopClassLoader aopClassLoader = new DefaultAopClassLoader(classLoaderUrls.toArray(new URL[0]),  AbstractIntegrationTests.class.getClassLoader());
        aopClassLoader.addTypeFinder( new AopClassLoader.TypeFinder() {

            @Override
            public byte[] findByteCode(String name) {
                return null;
            }

            @Override
            public URL findResource(String name) {
                return externalResourceMap.get(name);
            }

            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                URL resources = externalResourceMap.get(name);
                return resources != null ? new SingleEnumeration<>(resources) : null;
            }
        } );

        // 2.instantiate AopBootstraper instance, and invoke it
        // TODO: hold AspectWeaver
        try {
            Class<?> clazz = (Class<DefaultAopLauncher>) 
                    aopClassLoader.loadClass(io.gemini.aop.activation.DefaultAopLauncher.class.getName());

            AopLauncher aopBootstraper = (AopLauncher) clazz.newInstance();
            aopBootstraper.start(instrumentation, launcherConfig, aopClassLoader);
//            AopActivator.activateAop(launchLocation, instrumentation, launcherConfig);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to invoke DefaultAopBootstraper.", t);
        }
    }

    private static class TestLauncherScanner implements LauncherScanner {

        /* 
         * @see io.gemini.bootstrap.support.LauncherScanner#scanResources() 
         */
        @Override
        public URL[] scanResources() throws IOException {
            List<String> classPaths = ClassLoaderUtils.getClassPaths();
            List<URL> classLoaderUrls = new ArrayList<>(classPaths.size());
            Map<String, URL> externalResourceMap = new LinkedHashMap<>();
            for(String classPath : classPaths) {
                collectURLResources(classPath, classLoaderUrls, externalResourceMap);
            }
            return classLoaderUrls.toArray( new URL[0]);
        }
        
    }

    private static void collectURLResources(String classPath, List<URL> classLoaderUrls, Map<String, URL> externalResourceMap) {
        Path rootPath = Paths.get(classPath).normalize();
        if(Files.exists(rootPath) == false)
            return;

        URL rootUrl = null;
        try {
            rootUrl = rootPath.toUri().toURL();
        } catch (MalformedURLException e) {
            return;
        }

        if(Files.isRegularFile(rootPath) || rootPath.endsWith("test-classes") == false) {
            classLoaderUrls.add(rootUrl);
            return;
        }

        // iterate test-classes folder to load resource files
        try {
            Files.walk(rootPath)
            .filter( Files::isRegularFile )
            .filter( p -> p.toString().endsWith(ClassUtils.CLASS_FILE_EXTENSION) == false )
            .forEach( p -> {
                try {
                    String path = rootPath.relativize(p.normalize()).toString().replace('\\', ClassUtils.RESOURCE_SPERATOR);
                    externalResourceMap.put(path, p.toUri().toURL());
                } catch (MalformedURLException e) {
                    LOGGER.warn("Failed to convert path: {}", p, e);
                }
            } );
        } catch (IOException e) {
            LOGGER.warn("Failed to iterate path: {}", rootPath, e);
        }
    }


    @BeforeEach
    public void beforeEachTest() throws Exception {
        ExecutionMemento.clearMemento();
    }
}
