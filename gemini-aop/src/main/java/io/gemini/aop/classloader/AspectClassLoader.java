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
package io.gemini.aop.classloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import io.gemini.aop.agent.classloader.AgentClassLoader;
import io.gemini.aop.agent.classloader.CompoundEnumeration;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * <p>
 * This specialized ClassLoader contains two parent CLassLoaders. One ClassLoader includes AspectApp resources 
 * and delegates to AgentClassLoader to load user-defined Advice, Pointcut, Aspect classes and AOP framework 
 * classes, the other delegates to ThreadContextClassLoader (runtime application ClassLoader) or explicitly defined
 * application ClassLoader to load joinpoint relevant classes.
 * 
 * <p>
 * AspectClassLoader loads class from AspectApp resources firstly. If not found, then delegates to application 
 * ClassLoader, and generally this class should be joinpoint class. If one class contains in two ClassLoaders, 
 * and there might be class conflicting, joinpointTypeMatcher could be used to load class by Joinpoint ClassLoader
 * firstly.
 * 
 * <p>
 * Below figure demonstrates runtime relationship between ClassLoaders. 
 * 
 *                           Logical Parent CL          Actual Parent CL        Jointpoint CL
 * ----------------         -------------------          -------------          -----------
 * | Bootstrap CL |  <----  | Ext/Platform CL |  <----   | System CL |  <----   |  XXX CL |
 * ----------------         -------------------          -------------          -----------
 *                                   ^                         ^                     ^
 *                                   | JavaSE class            | parent-first        | 
 *                              ----P1------P2-----------------|     class     -----P2------
 *                              | Agent CL | <---------------------------------| Aspect CL |
 *                              ------------                                   P1-----------
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public abstract class AspectClassLoader extends URLClassLoader {

    private final String appName;

    private ElementMatcher<String> joinpointTypeMatcher = ElementMatchers.none();
    private ElementMatcher<String> joinpointResourceMatcher = ElementMatchers.none();


    static {
        // invoke registerAsParallelCapable directly since JDK 7+
        ClassLoader.registerAsParallelCapable();
    }


    /**
     * Create AspectClassLoader instance working in dedicated mode with predefined JoinpointClassLoader 
     * when instantiation AspectClassLoader.
     * 
     * @param appName
     * @param urls
     * @param agentClassLoader
     */
    public AspectClassLoader(String appName, URL[] urls, AgentClassLoader agentClassLoader) {
        super(urls, agentClassLoader);

        Assert.hasText(appName, "'appName' must not be empty.");
        this.appName = appName;
    }


    public AgentClassLoader getAgentClassLoader() {
        return (AgentClassLoader) this.getParent();
    }


    public void setJoinpointTypeMatcher(ElementMatcher<String> joinpointTypeMatcher) {
        this.joinpointTypeMatcher = joinpointTypeMatcher;
    }

    public void setJoinpointResourceMatcher(ElementMatcher<String> joinpointResourceMatcher) {
        this.joinpointResourceMatcher = joinpointResourceMatcher;
    }


    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (super.getClassLoadingLock(name)) {
            // 1.check local cache
            Class<?> type = this.findLoadedClass(name);
            if(type != null)
                return type;


            // 2.delegate to parent CL(AgentClassLoader) to load AOP framework classes
            try {
                type = this.getParent().loadClass(name);
                if (resolve) {
                    resolveClass(type);
                }

                return type;
            } catch(ClassNotFoundException ignored) { /* ignored */ }


            // 3.delegate to joinpoint CL to load joinpoint only classes
            if(this.joinpointTypeMatcher.matches(name) == true) {
                // load classes by subclass-defined JoinpointClassLoader
                return this.loadClassFromJoinpointCL(name, resolve);
            }


            // 4.delegate to current CL and joinpoint CL
            // load Aspect relevant classes by current ClassLoader
            type = this.loadClassFromCurrentCL(name, resolve);
            if(type != null) {
                return type;
            }

            // load classes by subclass-defined JoinpointClassLoader
            return this.loadClassFromJoinpointCL(name, resolve);
        }
    }

    private Class<?> loadClassFromCurrentCL(String name, boolean resolve) throws ClassNotFoundException {
        try {
            Class<?> type = this.findClass(name);
            if (resolve) {
                resolveClass(type);
            }

            return type;
        } catch(ClassNotFoundException ignored) { /* ignored */ }

        return null;
    }

    private Class<?> loadClassFromJoinpointCL(String name, boolean resolve) throws ClassNotFoundException {
        ClassLoader joinpointCL = doFindJoinpointCL();
        if(null == joinpointCL || this == joinpointCL) {
            return null;
        }

        Class<?> type = joinpointCL.loadClass(name);

        if (resolve) {
            resolveClass(type);
        }
        return type;
    }


    protected abstract ClassLoader doFindJoinpointCL();


    /**
     * {@inheritDoc}
     */
    @Override
    public URL getResource(String name) {
        // 1.delegate to parent CL(AgentClassLoader) to load AOP framework resources
        URL url = this.getParent().getResource(name);
        if(url != null) {
            return url;
        }


        // 2.delegate to joinpoint CL to load joinpoint only resources
        if(this.joinpointResourceMatcher.matches(name) == true) {
            // load resource by subclass-defined JoinpointClassLoader
            return this.findResourceWithJoinpointCL(name);
        }


        // 3.delegate to joinpoint CL and current CL
        // load resource by subclass-defined JoinpointClassLoader
        url = this.findResource(name);
        if(url != null) {
            return url;
        }

        // load resource by current ClassLoader
        return this.findResourceWithJoinpointCL(name);
    }

    private URL findResourceWithJoinpointCL(String name) {
        ClassLoader joinpointCL = this.doFindJoinpointCL();
        if(null == joinpointCL || this == joinpointCL) {
            return null;
        }

        return joinpointCL.getResource(name);
    }


    @SuppressWarnings("unchecked")
    public Enumeration<URL> getResources(String name) throws IOException {
        List<Enumeration<URL>> urlsList = new ArrayList<>();
        Enumeration<URL> urls = null;

        // 1.delegate to parent CL(AgentClassLoader) to load AOP framework resources
        urls = super.getResources(name);
        if(urls != null) {
            urlsList.add(urls);
        }


        // 2.delegate to joinpoint CL to load joinpoint only resources
        if(this.joinpointResourceMatcher.matches(name) == true) {
            // load resources by subclass-defined JoinpointClassLoader
            urls = this.findResourcesWithJoinpointCL(name);
            if(urls != null) {
                urlsList.add(urls);
            }

            return new CompoundEnumeration<>( urlsList.toArray(new Enumeration[0]) );
        }


        // 3.delegate to joinpoint CL and current CL
        // load resources by subclass-defined JoinpointClassLoader
        urls = this.findResources(name);
        if(urls != null) {
            urlsList.add(urls);
        }

        // load resources by current ClassLoader
        urls = this.findResourcesWithJoinpointCL(name);
        if(urls != null) {
            urlsList.add(urls);
        }

        return new CompoundEnumeration<>( urlsList.toArray(new Enumeration[0]) );
    }

    private Enumeration<URL> findResourcesWithJoinpointCL(String name) throws IOException {
        ClassLoader joinpointCL = this.doFindJoinpointCL();
        if(null == joinpointCL || this == joinpointCL) {
            return null;
        }

        return joinpointCL.getResources(name);
    }

    private String getName() {
        return super.toString()
                + "-" + ClassLoaderUtils.getClassLoaderName(this.doFindJoinpointCL())
                + "-" + appName ;
    }

    
    @Override
    public String toString() {
        return getName();
    }


    public static class ThreadContext {

        // store contextClassLoader per thread
        private final static ThreadLocal<ClassLoader> CONTEXT_CLASS_LOADER = new ThreadLocal<>();

        public static ClassLoader getContextClassLoader() {
            return CONTEXT_CLASS_LOADER.get();
        }

        public static void setContextClassLoader(ClassLoader contextClassLoader) {
            CONTEXT_CLASS_LOADER.set(contextClassLoader);
        }

        public static void removeContextClassLoader() {
            CONTEXT_CLASS_LOADER.remove();
        }
    }


    public static class WithThreadContextCL extends AspectClassLoader {

        public WithThreadContextCL(String appName, URL[] urls, AgentClassLoader agentClassLoader) {
            super(appName, urls, agentClassLoader);
        }

        @Override
        protected ClassLoader doFindJoinpointCL() {
            return ThreadContext.getContextClassLoader();
        }
    }


    public static class WithJoinpointCL extends AspectClassLoader {

        private final ClassLoader joinpointClassLoader;

        public WithJoinpointCL(String appName, URL[] urls, AgentClassLoader agentClassLoader, 
                ClassLoader joinpointClassLoader) {
            super(appName, urls, agentClassLoader);

            this.joinpointClassLoader = joinpointClassLoader;
        }

        /* @see io.gemini.aop.classloader.AspectClassLoader#doFindJoinpointCL() 
         */
        @Override
        protected ClassLoader doFindJoinpointCL() {
            return this.joinpointClassLoader;
        }
    }
}

