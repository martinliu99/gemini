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
package io.gemini.api.classloader;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Base class for the Gemini AOP framework's isolated class loader hierarchy.
 * <p>
 * Defines extension points for customizing class and resource loading:
 * <ul>
 *   <li>{@link LauncherFirstFilter} – determines which classes/resources should be loaded
 *       from the launcher class loader before the AOP class loader's own classpath</li>
 *   <li>{@link TypeFilter} – transforms class and resource names before loading</li>
 *   <li>{@link TypeFinder} – provides custom bytecode or resource lookup</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public abstract class AopClassLoader extends BaseClassLoader {

    static {
        // load class in parallel
        ClassLoader.registerAsParallelCapable();
    }


    /**
     * Creates a new {@code AopClassLoader} with the given classpath URLs and parent launcher class loader.
     *
     * @param urls                 the URLs forming this class loader's classpath
     * @param launcherClassLoader  the launcher (parent) class loader
     */
    public AopClassLoader(URL[] urls, ClassLoader launcherClassLoader) {
        super(urls, launcherClassLoader);
    }


    /**
     * Returns the URLs that make up this class loader's classpath.
     *
     * @return array of classpath URLs
     */
    public abstract URL[] getUrls();


    /**
     * Registers a {@link LauncherFirstFilter} that controls which classes and resources
     * are delegated to the launcher class loader before this class loader's own classpath.
     *
     * @param launcherFirstFilter the filter to add
     */
    public abstract void addLauncherFirstFilter(LauncherFirstFilter launcherFirstFilter);

    /**
     * Registers a {@link TypeFilter} that transforms class and resource names before loading.
     *
     * @param typeilter the filter to add
     */
    public abstract void addTypeFilter(TypeFilter typeilter);

    /**
     * Registers a {@link TypeFinder} that provides custom bytecode or resource lookup.
     *
     * @param typeFinder the finder to add
     */
    public abstract void addTypeFinder(TypeFinder typeFinder);


    /**
     * Determines which classes and resources should be loaded from the launcher class loader
     * before falling back to the AOP class loader's own classpath.
     * <p>
     * Implement this interface to give the launcher class loader priority for specific
     * class or resource names (e.g. shared API types or logging frameworks).
     * </p>
     */
    public static interface LauncherFirstFilter {

        /**
         * Returns whether the class with the given binary name should be loaded
         * from the launcher class loader first.
         *
         * @param name the binary name of the class
         * @return {@code true} if the launcher class loader should be tried first
         */
        boolean isLauncherFirstClass(String name);

        /**
         * Returns whether the resource with the given name should be loaded
         * from the launcher class loader first.
         *
         * @param name the resource name
         * @return {@code true} if the launcher class loader should be tried first
         */
        boolean isLauncherFirstResource(String name);


        /**
         * A composite {@link LauncherFirstFilter} that delegates to an ordered list of filters.
         * Returns {@code true} as soon as any filter in the chain matches.
         */
        static class FilterChain implements LauncherFirstFilter {

            private final List<LauncherFirstFilter> chain = new ArrayList<>();

            /**
             * Appends a filter to the chain. Null values are silently ignored.
             *
             * @param launcherFirstFilter the filter to add
             * @return this chain, for fluent chaining
             */
            public FilterChain addFilter(LauncherFirstFilter launcherFirstFilter) {
                if (launcherFirstFilter == null)
                    return this;

                this.chain.add(launcherFirstFilter);
                return this;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isLauncherFirstClass(String name) {
                for (LauncherFirstFilter filter : chain) {
                    if (filter.isLauncherFirstClass(name))
                        return true;
                }

                return false;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isLauncherFirstResource(String name) {
                for (LauncherFirstFilter filter : chain) {
                    if (filter.isLauncherFirstResource(name))
                        return true;
                }

                return false;
            }
            
        }
    }


    /**
     * Transforms class and resource names before they are looked up by the class loader.
     * <p>
     * Implement this interface to remap or rewrite names at load time (e.g. for relocation
     * or shading purposes).
     * </p>
     */
    public static interface TypeFilter {

        /**
         * Transforms the given binary class name before loading.
         *
         * @param name the original binary class name
         * @return the transformed class name to use for loading
         */
        String filterTypeName(String name);

        /**
         * Transforms the given resource name before loading.
         *
         * @param name the original resource name
         * @return the transformed resource name to use for loading
         */
        String filterResourceName(String name);


        /**
         * A no-op {@link TypeFilter} singleton that returns names unchanged.
         */
        enum NoOp implements TypeFilter {

            INSTANCE;


            /**
             * {@inheritDoc}
             */
            @Override
            public String filterTypeName(String name) {
                return name;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String filterResourceName(String name) {
                return name;
            }
        };

        /**
         * A composite {@link TypeFilter} that passes names through each filter in order,
         * feeding the output of one filter as the input to the next.
         */
        static class FilterChain implements TypeFilter {

            private final List<TypeFilter> chain = new ArrayList<>();

            /**
             * Appends a filter to the chain. Null values are silently ignored.
             *
             * @param typeFilter the filter to add
             * @return this chain, for fluent chaining
             */
            public FilterChain addFilter(TypeFilter typeFilter) {
                if (typeFilter == null)
                    return this;

                this.chain.add(typeFilter);
                return this;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String filterTypeName(String name) {
                for (TypeFilter typeFilter : chain) {
                    name = typeFilter.filterTypeName(name);
                }
                return name;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String filterResourceName(String name) {
                for (TypeFilter typeFilter : chain) {
                    name = typeFilter.filterResourceName(name);
                }
                return name;
            }
        }
    }


    /**
     * Provides custom bytecode and resource lookup for the AOP class loader.
     * <p>
     * Implement this interface to supply class bytecode or resources from non-standard
     * sources such as in-memory stores, remote repositories, or transformed archives.
     * </p>
     */
    public static interface TypeFinder {

        /**
         * Returns the raw bytecode for the class with the given binary name,
         * or {@code null} if this finder cannot supply it.
         *
         * @param name the binary class name
         * @return the class bytecode, or {@code null}
         */
        byte[] findByteCode(String name);

        /**
         * Returns the URL for the resource with the given name,
         * or {@code null} if this finder cannot supply it.
         *
         * @param name the resource name
         * @return the resource URL, or {@code null}
         */
        URL findResource(String name);

        /**
         * Returns an enumeration of all URLs for the resource with the given name,
         * or {@code null} if this finder cannot supply any.
         *
         * @param name the resource name
         * @return an enumeration of resource URLs, or {@code null}
         * @throws IOException if an I/O error occurs
         */
        Enumeration<URL> findResources(String name) throws IOException;


        /**
         * A no-op {@link TypeFinder} singleton that always returns {@code null}.
         */
        enum NoOp implements TypeFinder {

            INSTANCE;


            /**
             * {@inheritDoc}
             */
            @Override
            public byte[] findByteCode(String name) {
                return null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public URL findResource(String name) {
                return null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                return null;
            }
        }

        /**
         * A composite {@link TypeFinder} that delegates to an ordered list of finders,
         * returning the first non-null result found.
         */
        static class FinderChain implements TypeFinder {

            private final List<TypeFinder> chain = new ArrayList<>();

            /**
             * Appends a finder to the chain. Null values are silently ignored.
             *
             * @param typeFinder the finder to add
             * @return this chain, for fluent chaining
             */
            public FinderChain addFilter(TypeFinder typeFinder) {
                if (typeFinder == null)
                    return this;

                this.chain.add(typeFinder);
                return this;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public byte[] findByteCode(String name) {
                for (TypeFinder finder : chain) {
                    byte[] byteCode = finder.findByteCode(name);
                    if (byteCode != null)
                        return byteCode;
                }

                return null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public URL findResource(String name) {
                for (TypeFinder finder : chain) {
                    URL resource = finder.findResource(name);
                    if (resource != null)
                        return resource;
                }

                return null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                for (TypeFinder finder : chain) {
                    Enumeration<URL> resources = finder.findResources(name);
                    if (resources != null)
                        return resources;
                }

                return null;
            }
        }
    }
}
