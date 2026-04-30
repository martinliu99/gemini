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
package io.gemini.aop.test;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.LauncherDiscoveryListener;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.activation.AopActivator;
import io.gemini.activation.classloader.DefaultAopClassLoader;
import io.gemini.activation.support.AspectAppScanner;
import io.gemini.activation.support.UnpackedArchiveConfig;
import io.gemini.api.activation.LauncherConfig;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.SingleEnumeration;
import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * Activates the Gemini AOP framework before JUnit 5 test execution.
 * <p>
 * Registered as a JUnit 5 {@link org.junit.platform.launcher.LauncherDiscoveryListener}
 * and {@link org.junit.platform.launcher.TestExecutionListener} via the ServiceLoader mechanism.
 * On first test discovery, installs the ByteBuddy agent, builds the launcher configuration
 * from the test classpath, and calls {@link AopActivator#activateAop}.
 * </p>
 *
 * @author   martin.liu
 */
public class AopTestActivator implements LauncherDiscoveryListener, TestExecutionListener {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AopTestActivator.class);

    private static final String GEMINI_TEST_DEPENDENCY = "gemini-test";


    private static boolean LAUNCHED = false;


    /**
     * {@inheritDoc}
     */
    @Override
    public void launcherDiscoveryStarted(LauncherDiscoveryRequest request) {
        // launch AopLauncher and load all advisors
        if (LAUNCHED == false) {
            try {
                launch();

                LAUNCHED = true;
            } catch (Exception e) {
                LOGGER.warn("Could not activate AopTestActivator.", e);
            }
        }
    }

    protected void launch() throws Exception {
        // 1.prepare arguments
        Instrumentation instrumentation = ByteBuddyAgent.install();

        URL rootResource = AopTestActivator.class.getClassLoader().getResource(".");
        String launchLocation = new File(rootResource.toURI()).getParentFile().getPath() + File.separator + "aop-int-test" + File.separator;
        Path launchPath = Paths.get(launchLocation);
        if (Files.exists(launchPath) == false)
            Files.createDirectory( launchPath);

        List<String> classPaths = ClassLoaderUtils.getClassPaths();
        List<URL> classPathURLs = new ArrayList<>(classPaths.size());
        Map<String, URL> resourceFileURLs = new LinkedHashMap<>();
        for (String classPath : classPaths) {
            collectURLs(classPath, classPathURLs, resourceFileURLs);
        }


        // 2.activate AopLauncher
        try {
            LauncherConfig launcherConfig = new UnpackedArchiveConfig(launchPath, null, "",
                    () -> classPathURLs.toArray( new URL[0]),
                    true,
                    new AspectAppScanner.ClassesFolder() );

            AopClassLoader aopClassLoader = new DefaultAopClassLoader(classPathURLs.toArray(new URL[0]),  AopTestActivator.class.getClassLoader());

            configureClassLoader(aopClassLoader, resourceFileURLs);

            AopActivator.activateAop(launchLocation, instrumentation, launcherConfig, aopClassLoader);
        } catch (Throwable t) {
            throw new IllegalStateException("Could not activate AopLauncher.", t);
        }
    }

    private void collectURLs(String classPath, List<URL> classPathURLs, Map<String, URL> resourceFileURLs) {
        Path rootPath = Paths.get(classPath).normalize();
        if (Files.exists(rootPath) == false)
            return;

        URL rootUrl = null;
        try {
            rootUrl = rootPath.toUri().toURL();
        } catch (MalformedURLException e) {
            return;
        }


        // 1.collect class path file, and exclude gemini-test.jar
        if (Files.isRegularFile(rootPath)) {
            Path fileName = rootPath.getFileName();
            if (fileName.startsWith(GEMINI_TEST_DEPENDENCY) && fileName.endsWith("jar"))
                return;

            classPathURLs.add(rootUrl);
            return;
        }


        // 2.collect class path folder, and exclude gemini-test folder
        if (rootPath.endsWith("target/classes")) { 
            if (rootPath.getParent().getParent().getFileName().toString().equals(GEMINI_TEST_DEPENDENCY)) 
                return;

            classPathURLs.add(rootUrl);
            return;
        }


        // 3.iterate test-classes folder to load resource files
        try (Stream<Path> stream = Files.walk(rootPath)) {
            stream
            .filter( Files::isRegularFile )
            .filter( p -> p.toString().endsWith(ClassUtils.CLASS_FILE_EXTENSION) == false )
            .forEach( p -> {
                try {
                    String path = rootPath.relativize(p.normalize()).toString().replace('\\', ClassUtils.RESOURCE_SPERATOR);
                    resourceFileURLs.put(path, p.toUri().toURL());
                } catch (MalformedURLException e) {
                    LOGGER.warn("Could not convert path: {}", p, e);
                }
            } );
        } catch (IOException e) {
            LOGGER.warn("Could not iterate path: {}", rootPath, e);
        }
    }

    private void configureClassLoader(AopClassLoader aopClassLoader, Map<String, URL> resourceFileURLs) {
        // 1.load {@code ExecutionMemento} relevant classes via Launcher ClassLoader.
        aopClassLoader.addLauncherFirstFilter( new AopClassLoader.LauncherFirstFilter() {

            private final String launcherFirstClassPrefix;
            private final String launcherFirstResourcePrefix;


            {
                this.launcherFirstClassPrefix = ExecutionMemento.class.getName();
                this.launcherFirstResourcePrefix = ClassUtils.convertClassToResource(launcherFirstClassPrefix);
            }


            @Override
            public boolean isLauncherFirstClass(String name) {
                return name.startsWith(launcherFirstClassPrefix);
            }

            @Override
            public boolean isLauncherFirstResource(String name) {
                return name.startsWith(launcherFirstResourcePrefix);
            }
        } );


        // 2.load configuration files
        aopClassLoader.addTypeFinder( new AopClassLoader.TypeFinder() {

            @Override
            public byte[] findByteCode(String name) {
                return null;
            }

            @Override
            public URL findResource(String name) {
                return resourceFileURLs.get(name);
            }

            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                URL resources = resourceFileURLs.get(name);
                return resources != null ? new SingleEnumeration<>(resources) : null;
            }
        } );
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        ExecutionMemento.clearMemento();
    }
}
