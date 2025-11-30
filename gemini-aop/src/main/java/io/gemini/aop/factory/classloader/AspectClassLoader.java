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
package io.gemini.aop.factory.classloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import io.gemini.api.annotation.NoMatching;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.api.classloader.BaseClassLoader;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CompoundEnumeration;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * <p>
 * This specialized ClassLoader contains two parent CLassLoaders. One ClassLoader includes Aspect resources 
 * and delegates to {@code AopClassLoader} to load user-defined Advice, Pointcut, AdvisorSpec classes and AOP framework 
 * classes, the other delegates to ThreadContextClassLoader (runtime application ClassLoader) or explicitly defined
 * application ClassLoader to load joinpoint relevant classes.
 * 
 * <p>
 * AspectClassLoader loads class from Aspect resources firstly. If not found, then delegates to application 
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
 *                              | Aop CL | <---------------------------------| Aspect CL |
 *                              ------------                                   P1-----------
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
@NoMatching(classLoader = true)
public class AspectClassLoader extends BaseClassLoader {

    private final String loaderName;

    private ElementMatcher<String> joinpointFirstTypeMatcher = ElementMatchers.none();
    private ElementMatcher<String> joinpointFirstResourceMatcher = ElementMatchers.none();


    static {
        // invoke registerAsParallelCapable directly since JDK 7+
        ClassLoader.registerAsParallelCapable();
    }


    /**
     * Create AspectClassLoader instance working in dedicated mode with predefined JoinpointClassLoader 
     * when instantiation AspectClassLoader.
     * 
     * @param loaderName
     * @param urls
     * @param aopClassLoader
     */
    public AspectClassLoader(String loaderName, URL[] urls, AopClassLoader aopClassLoader) {
        super(urls, aopClassLoader);

        Assert.hasText(loaderName, "'loaderName' must not be empty.");
        this.loaderName = loaderName;
    }


    public AopClassLoader getAopClassLoader() {
        return (AopClassLoader) this.getParent();
    }


    public void setJoinpointFirstTypeMatcher(ElementMatcher<String> joinpointFirstTypeMatcher) {
        this.joinpointFirstTypeMatcher = joinpointFirstTypeMatcher;
    }

    public void setJoinpointFirstResourceMatcher(ElementMatcher<String> joinpointFirstResourceMatcher) {
        this.joinpointFirstResourceMatcher = joinpointFirstResourceMatcher;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Assert.notNull(name, "'name' must not be empty.");

        synchronized (super.getClassLoadingLock(name)) {
            // 1.check local cache
            Class<?> type = this.findLoadedClass(name);
            if (type != null)
                return type;


            // 2.delegate to parent CL(AopClassLoader) to load AOP framework classes
            try {
                type = this.getParent().loadClass(name);
                if (resolve) {
                    resolveClass(type);
                }

                return type;
            } catch (ClassNotFoundException ignored) { /* ignored */ }


            ClassLoader joinpointCL = getJoinpointClassLoader();
            if (this.joinpointFirstTypeMatcher.matches(name) == true) {
                // 3.delegate to joinpoint CL to load joinpoint first classes
                type = joinpointCL == null ? null : this.loadClassFromJoinpointCL(joinpointCL, name, resolve, false);
                if (type != null) {
                    return type;
                }

                // 4.delegate to current CL to load Aspect relevant classes
                return this.loadClassFromCurrentCL(name, resolve, true);
            } else {
                // 3.delegate to current CL to load Aspect relevant classes
                type = this.loadClassFromCurrentCL(name, resolve, joinpointCL == null);
                if (type != null) {
                    return type;
                }

                // 4.delegate to joinpoint CL to load joinpoint relevant classes
                return joinpointCL == null ? null : this.loadClassFromJoinpointCL(joinpointCL, name, resolve, true);
            }
        }
    }

    private Class<?> loadClassFromCurrentCL(String name, boolean resolve, 
            boolean throwException) throws ClassNotFoundException {
        try {
            Class<?> type = this.findClass(name);
            if (resolve) {
                resolveClass(type);
            }

            return type;
        } catch (ClassNotFoundException e) {
            if (throwException) throw e;
        }

        return null;
    }

    private Class<?> loadClassFromJoinpointCL(ClassLoader joinpointCL, String name, boolean resolve, 
            boolean throwException) throws ClassNotFoundException {
        try {
            Class<?> type = joinpointCL.loadClass(name);
            if (resolve) {
                resolveClass(type);
            }

            return type;
        } catch (ClassNotFoundException e) {
            if (throwException) throw e;
        }

        return null;
    }


    public ClassLoader getJoinpointClassLoader() {
        ClassLoader joinpointCL =  ThreadContext.getContextClassLoader();
        return this == joinpointCL ? null : joinpointCL;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public URL getResource(String name) {
        Assert.notNull(name, "'name' must not be empty.");

        // 1.delegate to parent CL(AopClassLoader) to load AOP framework resources
        URL url = this.getParent().getResource(name);
        if (url != null) {
            return url;
        }


        ClassLoader joinpointCL = getJoinpointClassLoader();

        // 2.delegate to joinpoint CL to load joinpoint only resources
        if (joinpointCL != null && this.joinpointFirstResourceMatcher.matches(name) == true) {
            // load resource by JoinpointClassLoader
            return this.findResourceWithJoinpointCL(joinpointCL, name);
        }


        // 3.delegate to joinpoint CL and current CL
        // load resource by current ClassLoader
        url = this.findResource(name);
        if (url != null) {
            return url;
        }

        // load resource by JoinpointClassLoader
        return joinpointCL != null ? this.findResourceWithJoinpointCL(joinpointCL, name) : null;
    }


    private URL findResourceWithJoinpointCL(ClassLoader joinpointCL, String name) {
        return joinpointCL.getResource(name);
    }


    public URL getAspectResource(String name) {
        return super.getResource(name);
    }

    public InputStream getAspectResourceAsStream(String name) {
        URL url = getAspectResource(name);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException e) {
            return null;
        }
    }


    public Enumeration<URL> getResources(String name) throws IOException {
        List<Enumeration<URL>> urlsList = new ArrayList<>();
        Enumeration<URL> urls = null;

        // 1.delegate to parent CL(AopClassLoader) to load AOP framework resources
        urls = super.getResources(name);
        if (urls != null) {
            urlsList.add(urls);
        }


        ClassLoader joinpointCL = getJoinpointClassLoader();

        // 2.delegate to joinpoint CL to load joinpoint only resources
        if (joinpointCL != null && this.joinpointFirstResourceMatcher.matches(name) == true) {
            // load resources by JoinpointClassLoader
            urls = this.findResourcesWithJoinpointCL(joinpointCL, name);
            if (urls != null) {
                urlsList.add(urls);
            }

            return new CompoundEnumeration<>( urlsList );
        }


        // 3.delegate to joinpoint CL and current CL
        // load resources by current ClassLoader
        urls = this.findResources(name);
        if (urls != null) {
            urlsList.add(urls);
        }

        // load resources by JoinpointClassLoader
        urls = joinpointCL != null ? this.findResourcesWithJoinpointCL(joinpointCL, name) : null;
        if (urls != null) {
            urlsList.add(urls);
        }

        return new CompoundEnumeration<>( urlsList );
    }

    private Enumeration<URL> findResourcesWithJoinpointCL(ClassLoader joinpointCL, String name) throws IOException {
        return joinpointCL.getResources(name);
    }

    private String getLoaderName() {
        return super.toString()
                + "-" + loaderName
                + "-" + ClassLoaderUtils.getClassLoaderName(this.getJoinpointClassLoader());
    }

    
    @Override
    public String toString() {
        return getLoaderName();
    }
}