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
package io.gemini.aop.factory.classloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

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
 * This specialized ClassLoader loads classes and resources via two CLassLoaders. 
 * <li>
 * One ClassLoader holds Aspect resources and delegates to {@code AopClassLoader} to 
 * load user-defined Advice, Pointcut, AdvisorSpec classes and AOP framework classes.
 * <li>
 * The other delegates to {@code ThreadContext} (runtime application ClassLoader) or explicitly defined
 * target ClassLoader to load target classes.
 * 
 * <p>
 * AspectClassLoader loads class from Aspect resources firstly. If not found, then delegates to target 
 * ClassLoader, and generally this loading class should be target class. 
 * If one class could be loaded by two ClassLoaders, and there might have class conflicting, 
 * targetTypeMatcher could be used to load class by target ClassLoader firstly.
 * 
 * <p>
 * Below figure demonstrates runtime relationship between ClassLoaders. 
 * 
 *                           Logical Parent CL           Actual Parent CL           Target CL
 * ----------------         -------------------          ---------------           -----------
 * | Bootstrap CL |  <----  | Ext/Platform CL |  <----   | Launcher CL |   <----   |  XXX CL |
 * ----------------         -------------------          ---------------           -----------
 *                                   ^                         ^                        ^
 *                                   | 1.JavaSE class          | 2.launcher-first       | 2.target-first 
 *                                   |                         |       class            |     class
 *                              ---------- --------------------|                   -------------
 *                              | Aop CL | <-------------------------------------- | Aspect CL |
 *                              ----------                        1.AOP class      -------------
 *
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AspectClassLoader extends BaseClassLoader {

    private final String loaderName;

    private ElementMatcher<String> targetFirstTypeMatcher = ElementMatchers.none();
    private ElementMatcher<String> targetFirstResourceMatcher = ElementMatchers.none();


    static {
        // invoke registerAsParallelCapable directly since JDK 7+
        ClassLoader.registerAsParallelCapable();
    }


    /**
     * Create AspectClassLoader instance with AopClassLoader.
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


    public void setTargetFirstTypeMatcher(ElementMatcher<String> targetFirstTypeMatcher) {
        this.targetFirstTypeMatcher = targetFirstTypeMatcher;
    }

    public void setTargetFirstResourceMatcher(ElementMatcher<String> targetFirstResourceMatcher) {
        this.targetFirstResourceMatcher = targetFirstResourceMatcher;
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


            ClassLoader targetCL = getTargetClassLoader();
            if (this.targetFirstTypeMatcher.matches(name) == true) {
                // 3.delegate to target CL to load target first classes
                type = targetCL == null ? null : this.loadClassFromTargetCL(targetCL, name, resolve, false);
                if (type != null) {
                    return type;
                }

                // 4.delegate to current CL to load Aspect relevant classes
                return this.loadClassFromCurrentCL(name, resolve, true);
            } else {
                // 3.delegate to current CL to load Aspect relevant classes
                type = this.loadClassFromCurrentCL(name, resolve, targetCL == null);
                if (type != null) {
                    return type;
                }

                // 4.delegate to Target CL to load target classes
                return targetCL == null ? null : this.loadClassFromTargetCL(targetCL, name, resolve, true);
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

    private Class<?> loadClassFromTargetCL(ClassLoader targetCL, String name, boolean resolve, 
            boolean throwException) throws ClassNotFoundException {
        try {
            Class<?> type = targetCL.loadClass(name);
            if (resolve) {
                resolveClass(type);
            }

            return type;
        } catch (ClassNotFoundException e) {
            if (throwException) throw e;
        }

        return null;
    }


    public ClassLoader getTargetClassLoader() {
        ClassLoader targetCL =  ThreadContext.getContextClassLoader();
        return this == targetCL ? null : targetCL;
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


        ClassLoader targetCL = getTargetClassLoader();

        // 2.delegate to target CL to load target resources
        if (targetCL != null && this.targetFirstResourceMatcher.matches(name) == true) {
            // load resource by TargetClassLoader
            return this.findResourceWithTargetCL(targetCL, name);
        }


        // 3.delegate to target CL and current CL
        // load resource by current ClassLoader
        url = this.findResource(name);
        if (url != null) {
            return url;
        }

        // load resource by TargetClassLoader
        return targetCL != null ? this.findResourceWithTargetCL(targetCL, name) : null;
    }


    private URL findResourceWithTargetCL(ClassLoader targetCL, String name) {
        return targetCL.getResource(name);
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


        ClassLoader targetCL = getTargetClassLoader();

        // 2.delegate to target CL to load target resources
        if (targetCL != null && this.targetFirstResourceMatcher.matches(name) == true) {
            // load resources by TargetClassLoader
            urls = this.findResourcesWithTargetCL(targetCL, name);
            if (urls != null) {
                urlsList.add(urls);
            }

            return new CompoundEnumeration<>( urlsList );
        }


        // 3.delegate to target CL and current CL
        // load resources by current ClassLoader
        urls = this.findResources(name);
        if (urls != null) {
            urlsList.add(urls);
        }

        // load resources by TargetClassLoader
        urls = targetCL != null ? this.findResourcesWithTargetCL(targetCL, name) : null;
        if (urls != null) {
            urlsList.add(urls);
        }

        return new CompoundEnumeration<>( urlsList );
    }

    private Enumeration<URL> findResourcesWithTargetCL(ClassLoader targetCL, String name) throws IOException {
        return targetCL.getResources(name);
    }

    private String getLoaderName() {
        return super.toString()
                + "-" + loaderName
                + "-" + ClassLoaderUtils.getClassLoaderName(this.getTargetClassLoader());
    }

    
    @Override
    public String toString() {
        return getLoaderName();
    }
}