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
package io.gemini.activation.classloader;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import io.gemini.api.annotation.NoMatching;
import io.gemini.api.classloader.AopClassLoader;

/**
 * <p>
 * This specialized ClassLoader is used by {@code AopActivator} to load AOP framework and depended classes 
 * such as log4j2, aspectjweaver, bytebuddy, etc.
 * 
 * 
 * @author martin.liu
 * @since  1.0.0
 *
 */
@NoMatching(classLoader = true)
public class DefaultAopClassLoader extends AopClassLoader {

    private static final ClassLoader EXT_CLASSLOADER;

    private static final Set<String> BUILTIN_PARENT_FIRST_CLASS_PREFIXES = new LinkedHashSet<>();
    private static final Set<String> BUILTIN_PARENT_FIRST_RESOURCE_PREFIXES = new LinkedHashSet<>();


    private final URL[] urls;

    private final ClassLoader deletgateClassLoader;

    private ParentFirstFilter.FilterChain parentFirstFilters;

    private TypeFilter.FilterChain typeFilters;

    private TypeFinder.FinderChain typeFinders;


    static {
        // invoke registerAsParallelCapable directly since JDK 7+
        registerAsParallelCapable();

        // search ExtClassLoader
        ClassLoader classLoader = getSystemClassLoader();
        while (classLoader.getParent() != null) {
            classLoader = classLoader.getParent();
        }
        EXT_CLASSLOADER = classLoader;

        BUILTIN_PARENT_FIRST_CLASS_PREFIXES.add("io.gemini.api.activation.");
        BUILTIN_PARENT_FIRST_CLASS_PREFIXES.add("io.gemini.api.classloader.");

        // TODO:  support jar mode
//        BUILTIN_PARENT_FIRST_CLASS_PREFIXES.add("io.gemini.api.");
//        BUILTIN_PARENT_FIRST_CLASS_PREFIXES.add("net.bytebuddy.");

        for(String classPrefix : BUILTIN_PARENT_FIRST_CLASS_PREFIXES)
            BUILTIN_PARENT_FIRST_RESOURCE_PREFIXES.add(classPrefix.replace(".", "/"));
    }


    public DefaultAopClassLoader(URL[] urls, ClassLoader parentClassLoader) {
        // use ExtClassLoader/PlatformClassLoader as parent ClassLoader.
        super(urls, EXT_CLASSLOADER);
    
        this.urls = urls;

        // refer to actual parent ClassLoader
        this.deletgateClassLoader = parentClassLoader;

        this.parentFirstFilters = new ParentFirstFilter.FilterChain()
                .addFilter(Default.INSTANCE);

        this.typeFilters = new TypeFilter.FilterChain();

        this.typeFinders = new TypeFinder.FinderChain();
    }

    public URL[] getUrls() {
        return this.urls;
    }

    public void addParentFirstFilter(ParentFirstFilter parentFirstFilter) {
        if(parentFirstFilter == null) 
            return;

        this.parentFirstFilters.addFilter(parentFirstFilter);
    }

    public void addTypeFilter(TypeFilter typeilter) {
        if(typeilter == null)
            return;

        this.typeFilters.addFilter(typeilter);
    }

    public void addTypeFinder(TypeFinder typeFinder) {
        if(typeFinder == null)
            return;

        this.typeFinders.addFilter(typeFinder);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if(name == null || "".equals(name.trim()))
            throw new IllegalArgumentException("'name' must not be empty.");

        synchronized (super.getClassLoadingLock(name)) {
            // 1.find loaded class from local cache.
            Class<?> type = super.findLoadedClass(name);
            if (type != null) {
                return type;
            }

            // 2.if delegation loading is required, try to load from actual parent ClassLoader.
            if(this.parentFirstFilters.isParentFirstClass(name) == true) {
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
            return super.loadClass( typeFilters.filterTypeName(name), resolve );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] byteCode = typeFinders.findByteCode(name);
        return byteCode!= null ? super.defineClass(name, byteCode, 0, byteCode.length) : super.findClass(name);
    }

    /**
     * {@inheritDoc}
     */
    public URL getResource(String name) {
        if(name == null || "".equals(name.trim()))
            throw new IllegalArgumentException("'name' must not be empty.");

        URL url = null;

        // 1.if delegation loading is required, try to load from actual parent ClassLoader.
        if(this.parentFirstFilters.isParentFirstResource(name) == true) {
            url = this.deletgateClassLoader.getResource(name);
            if(url != null) {
                return url;
            }
        }

        // 2.try to load resource
        return super.getResource( typeFilters.filterResourceName(name) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL findResource(String name) {
        URL resource = typeFinders.findResource(name);
        return resource != null ? resource : super.findResource(name);
    }


    /**
     * {@inheritDoc}
     */
    public Enumeration<URL> getResources(String name) throws IOException {
        // 1.if delegation loading is required, try to load from actual parent ClassLoader.
        if(this.parentFirstFilters.isParentFirstResource(name) == true) {
            return this.deletgateClassLoader.getResources(name);
        }


        // 2.try to load resources with URLClassLoader
        return super.getResources( typeFilters.filterResourceName(name) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<URL> findResources(final String name) throws IOException {
        Enumeration<URL> resources = typeFinders.findResources(name);
        return resources != null ? resources : super.findResources(name);
    }


    enum Default implements ParentFirstFilter {

        INSTANCE;


        @Override
        public boolean isParentFirstClass(String name) {
            for(String classPrefix : BUILTIN_PARENT_FIRST_CLASS_PREFIXES) {
                if(name.startsWith(classPrefix))
                    return true;
            }

            return false;
        }

        @Override
        public boolean isParentFirstResource(String name) {
            for(String resourcePrefix : BUILTIN_PARENT_FIRST_RESOURCE_PREFIXES) {
                if(name.startsWith(resourcePrefix))
                    return true;
            }

            return false;
        }
    };
}