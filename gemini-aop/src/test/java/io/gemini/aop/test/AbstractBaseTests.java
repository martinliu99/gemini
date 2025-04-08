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
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.agent.bootstraper.AopBootstraperConfig;
import io.gemini.aop.agent.classloader.AgentClassLoader;
import io.gemini.aop.bootstraper.DefaultAopBootstraper;
import io.gemini.core.util.ClassLoaderUtils;
import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public abstract class AbstractBaseTests {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractBaseTests.class);

    private static boolean LAUNCHED = false;
    private static final AgentClassLoader AGENT_CLASSLOADER;

    static {
        List<String> classPaths = ClassLoaderUtils.getClassPaths();
        List<URL> classUrls = new ArrayList<>(classPaths.size());
        List<URL> resourceUrls = new ArrayList<>();

        for(String classPath : classPaths) {
            collectURLResources(classPath, classUrls, resourceUrls);
        }

        AGENT_CLASSLOADER = new AgentClassLoader(classUrls.toArray(new URL[0]), resourceUrls.toArray(new URL[0]), AbstractBaseTests.class.getClassLoader());
    }

    private static void collectURLResources(String classPath, List<URL> classUrls, List<URL> resourceUrls) {
        File file = new File(classPath);
        if(file.exists() == false)
            return;

        URL url = null;
        try {
            url = file.toURI().toURL();
        } catch (MalformedURLException e) {
            return;
        }

        if(file.isFile()) {
            classUrls.add(url);
            return;
        }

        if(file.getName().equals("test-classes") == false) {
            classUrls.add(url);
            return;
        }

        // iterate test-classes folder
        for(File element : file.listFiles()) {
            if(element.isDirectory())
                continue;

            if(element.getName().endsWith(".class"))
                continue;

            try {
                resourceUrls.add(element.toURI().toURL());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }


    @BeforeAll
    public static void beforeAllTests() throws Exception {
        // launch Agent and load all advices
        if(LAUNCHED == false) {
            launchAgent();
            LAUNCHED = true;
        }
    }


    @SuppressWarnings("unchecked")
    protected static void launchAgent() throws Exception {
        // 1.prepare arguments
        Instrumentation instrumentation = ByteBuddyAgent.install();

        CodeSource codeSource = AbstractBaseTests.class.getProtectionDomain().getCodeSource();
        String agentCodeLocation;
        try {
            agentCodeLocation = new File(codeSource.getLocation().toURI()).getParentFile().getPath() + File.separator + "int-test" + File.separator;
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to find agent code location", t);
        }
        AopBootstraperConfig aopBootstraperConfig = new AopBootstraperConfig("", agentCodeLocation);


        // 2.instantiate AopBootstraper instance, and invoke it
        // TODO: hold AspectWeaver
        Class<?> clazz = (Class<DefaultAopBootstraper>) 
                AGENT_CLASSLOADER.loadClass(io.gemini.aop.bootstraper.DefaultAopBootstraper.class.getName());
        Method method = clazz.getMethod("start", Instrumentation.class, AopBootstraperConfig.class);

        Object aopBootstraper = clazz.newInstance();
        method.invoke(aopBootstraper, instrumentation, aopBootstraperConfig);
    }

    protected static void relaunchAgent() {
        
    }

    @BeforeEach
    public void beforeEachTest() throws Exception {
        ExecutionMemento.clearMemento();
    }
}
