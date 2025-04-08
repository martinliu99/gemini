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
package io.gemini.aop.agent.classloader;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * <p>
 * This specialized ClassLoader is used by Java Agent to load AOP framework and depended classes 
 * such as log4j2, aspectjweaver, bytebuddy, etc, and used-defined resources.
 * </p>
 *  
 * <i>Classes loaded by SystemClassLoader might conflict with classes loaded AgentClassLoader. To avoid this, 
 * AgentClassLoader uses JavaSE ClassLoader, e.g., ExtClassLoader (JDK8-) or PlatformClassLoader(JDK9+) 
 * as parent ClassLoader (logical parent), and occasionally delegates to SystemClassLoader (actual parent) 
 * based on parentFirstClassPrefixes and parentFirstResourcePrefixes settings.
 * 
 * Below figure demonstrates runtime relationship between ClassLoaders. 
 * 
 *                           Logical Parent CL          Actual Parent CL        Jointpoint CL
 * ----------------         -------------------          -------------          -----------
 * | BootStrap CL |  <----  | Ext/Platform CL |  <----   | System CL |  <----   |  XXX CL |
 * ----------------         -------------------          -------------          -----------
 *                                   ^                         ^ 
 *                                   | JavaSE class            | parent-first 
 *                              ----P1------P2-----------------|     class
 *                              | Agent CL | 
 *                              ------------
 * 
 * <i>
 * This ClassLoader accepts and caches resource files to resolve URLClassLoader limits that only loads resource files 
 * under given URL folder.
 * 
 * @author martin.liu
 * @since  1.0.0
 *
 */
public class AgentClassLoader extends URLClassLoader {

    private static final ClassLoader EXT_CLASSLOADER;

    private static final Set<String> BUILTIN_PARENT_FIRST_CLASS_PREFIXES = new LinkedHashSet<>();


    private final URL[] urls;
    private final Map<String, URL> resourceUrlMap;

    private final ClassLoader deletgateClassLoader;

    private volatile Set<String> parentFirstClassPrefixes;
    private volatile Set<String> parentFirstResourcePrefixes;


    static {
        // invoke registerAsParallelCapable directly since JDK 7+
        registerAsParallelCapable();

        // search ExtClassLoader
        ClassLoader classLoader = getSystemClassLoader();
        while (classLoader.getParent() != null) {
            classLoader = classLoader.getParent();
        }
        EXT_CLASSLOADER = classLoader;

        BUILTIN_PARENT_FIRST_CLASS_PREFIXES.add("io.gemini.aop.agent.");
    }


    public AgentClassLoader(URL[] urls, ClassLoader parentClassLoader) {
        this(urls, null, parentClassLoader);
    }

    public AgentClassLoader(URL[] urls, URL[] resourceUrls, ClassLoader parentClassLoader) {
        // use ExtClassLoader/PlatformClassLoader as parent ClassLoader.
        super(urls, EXT_CLASSLOADER);
    
        this.urls = urls;

        if(resourceUrls == null) {
            this.resourceUrlMap = new LinkedHashMap<>();
        } else {
            this.resourceUrlMap = new LinkedHashMap<>(resourceUrls.length);
            for(URL resourceUrl : resourceUrls) {
                try {
                    File file = new File(resourceUrl.toURI());
                    if(file.exists() == false)
                        continue;

                    if(file.isFile()) {
                        resourceUrlMap.put(file.getName(), resourceUrl);
                    }
                } catch (URISyntaxException e) {
                    //
                }
            }
        }

        // refer to actual parent ClassLoader
        this.deletgateClassLoader = parentClassLoader;

        this.parentFirstClassPrefixes = new LinkedHashSet<>(BUILTIN_PARENT_FIRST_CLASS_PREFIXES);
        this.parentFirstResourcePrefixes = new LinkedHashSet<>();
    }

    public URL[] getUrls() {
        return this.urls;
    }

    public void setParentFirstClassPrefixes(Set<String> packagePrefixes) {
        if(packagePrefixes == null || packagePrefixes.size() == 0)
            return;

        this.parentFirstClassPrefixes = new LinkedHashSet<>();
        this.parentFirstClassPrefixes.addAll(packagePrefixes);
    }

    public void setParentFirstResourcePrefixes(Set<String> resourcePrefixes) {
        if(resourcePrefixes == null || resourcePrefixes.size() == 0)
            return;

        this.parentFirstResourcePrefixes = new LinkedHashSet<>();
        this.parentFirstResourcePrefixes.addAll(resourcePrefixes);
    }


    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (super.getClassLoadingLock(name)) {
            // 1.find loaded class from local cache.
            Class<?> type = super.findLoadedClass(name);
            if (type != null) {
                return type;
            }

            // 2.if delegation loading is required, try to load from actual parent ClassLoader.
            boolean delegationLoad = false;
            {
                for(String prefix : parentFirstClassPrefixes) {
                    if(name.startsWith(prefix)) {
                        delegationLoad = true;
                        break;
                    }
                }
            }
            if(delegationLoad == true) {
                try {
                    type = this.deletgateClassLoader.loadClass(name);
                    if(type != null) {
                        if(resolve == true) {
                            this.resolveClass(type);
                        }

                        return type;
                    }
                } catch(ClassNotFoundException ignored) { /* ignored */ }
            }

            // 3.try to load class
            return super.loadClass(name, resolve);
        }
    }

    /**
     * {@inheritDoc}
     */
    public URL getResource(String name) {
        // TODO: cache resource?
        URL url = null;

        // 1.if delegation loading is required, try to load from actual parent ClassLoader.
        boolean delegateLoad = false;
        {
            for(String prefix : parentFirstResourcePrefixes) {
                if(name.startsWith(prefix)) {
                    delegateLoad = true;
                    break;
                }
            }
        }
        if(delegateLoad == true) {
            url = this.deletgateClassLoader.getResource(name);
            if(url != null) {
                return url;
            }
        }


        // 2.try to load resource with URLClassLoader
        url = super.getResource(name);
        if(url != null)
            return url;

        // 3.check local cached resource
        return this.resourceUrlMap.containsKey(name) ? this.resourceUrlMap.get(name) : null;
    }


    public URL getResourceWithoutParent(String name) {
        return this.findResource(name);
    }


    @SuppressWarnings("unchecked")
    public Enumeration<URL> getResources(String name) throws IOException {
        List<Enumeration<URL>> resourceUrlList = new ArrayList<>();
        Enumeration<URL> resourceUrls = null;

        // 1.if delegation loading is required, try to load from actual parent ClassLoader.
        boolean delegateLoad = false;
        {
            for(String prefix : parentFirstResourcePrefixes) {
                if(name.startsWith(prefix)) {
                    delegateLoad = true;
                    break;
                }
            }
        }
        if(delegateLoad == true) {
            resourceUrls = this.deletgateClassLoader.getResources(name);
            if(resourceUrls != null) {
                resourceUrlList.add(resourceUrls);
            }
        }


        // 2.try to load resources with URLClassLoader
        resourceUrls =  super.getResources(name);
        if(resourceUrls != null) {
            resourceUrlList.add(resourceUrls);
        }

        // 3.check local cached resource
        if(resourceUrlMap.containsKey(name)) {
            resourceUrls = new Enumeration<URL>() {

                URL url = resourceUrlMap.get(name);

                public boolean hasMoreElements() {
                    return url != null;
                }

                public URL nextElement() {
                    if (url == null) {
                        throw new NoSuchElementException();
                    } else {
                        URL local = url;
                        url = null;
                        return local;
                    }
                }
            };
            resourceUrlList.add(resourceUrls);
        }

        return new CompoundEnumeration<>( resourceUrlList.toArray(new Enumeration[0]) );
    }
}
