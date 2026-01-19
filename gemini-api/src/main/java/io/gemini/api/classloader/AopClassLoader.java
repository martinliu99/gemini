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
 * <p>
 * This base ClassLoader defines APIs and interfaces for implementation classes to 
 * customize AOP framework and depended classes loading.
 * 
 * 
 * @author martin.liu
 * @since  1.0.0
 *
 */
public abstract class AopClassLoader extends BaseClassLoader {

    /**
     * @param urls
     * @param launcherClassLoader
     */
    public AopClassLoader(URL[] urls, ClassLoader launcherClassLoader) {
        super(urls, launcherClassLoader);
    }


    public abstract URL[] getUrls();


    public abstract void addLauncherFirstFilter(LauncherFirstFilter launcherFirstFilter);

    public abstract void addTypeFilter(TypeFilter typeilter);

    public abstract void addTypeFinder(TypeFinder typeFinder);


    /**
     * This interface filters classes and resources should be loaded from 
     * Launcher ClassLoader firstly 
     *
     */
    public static interface LauncherFirstFilter {

        boolean isLauncherFirstClass(String name);

        boolean isLauncherFirstResource(String name);


        static class FilterChain implements LauncherFirstFilter {

            private final List<LauncherFirstFilter> chain = new ArrayList<>();

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
     * This interface filter class and resource 
     *
     */
    public static interface TypeFilter {

        String filterTypeName(String name);

        String filterResourceName(String name);


        enum NoOp implements TypeFilter {

            INSTANCE;


            @Override
            public String filterTypeName(String name) {
                return name;
            }

            @Override
            public String filterResourceName(String name) {
                return name;
            }
        };

        static class FilterChain implements TypeFilter {

            private final List<TypeFilter> chain = new ArrayList<>();

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
     * This interface finds class byte code or resource
     *
     */
    public static interface TypeFinder {

        byte[] findByteCode(String name);


        URL findResource(String name);

        Enumeration<URL> findResources(String name) throws IOException;


        enum NoOp implements TypeFinder {

            INSTANCE;


            @Override
            public byte[] findByteCode(String name) {
                return null;
            }


            @Override
            public URL findResource(String name) {
                return null;
            }

            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                return null;
            }
        }

        static class FinderChain implements TypeFinder {

            private final List<TypeFinder> chain = new ArrayList<>();

            public FinderChain addFilter(TypeFinder typeFinder) {
                if (typeFinder == null)
                    return this;

                this.chain.add(typeFinder);
                return this;
            }


            @Override
            public byte[] findByteCode(String name) {
                for (TypeFinder finder : chain) {
                    byte[] byteCode = finder.findByteCode(name);
                    if (byteCode != null)
                        return byteCode;
                }

                return null;
            }


            @Override
            public URL findResource(String name) {
                for (TypeFinder finder : chain) {
                    URL resource = finder.findResource(name);
                    if (resource != null)
                        return resource;
                }

                return null;
            }

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
