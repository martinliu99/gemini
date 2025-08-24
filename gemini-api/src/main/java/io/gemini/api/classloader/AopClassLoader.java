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
package io.gemini.api.classloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * <p>
 * This specialized ClassLoader is used by {@code AopLauncher} to load AOP framework and depended classes 
 * such as log4j2, aspectjweaver, bytebuddy, etc.
 * </p>
 *  
 * <i>Classes loaded by SystemClassLoader might conflict with classes loaded by AopClassLoader. 
 * To avoid this, AopClassLoader uses JavaSE ClassLoader, e.g., ExtClassLoader (JDK8-) or PlatformClassLoader(JDK9+) 
 * as parent ClassLoader (logical parent), and occasionally delegates to SystemClassLoader (actual parent) 
 * based on ParentFirstFilter.
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
 *                              | Aop CL | 
 *                              ------------
 * 
 * <p>
 * This ClassLoader supports below hook interfaces to customized class loading process.
 * <li> {@code ParentFirstFilter} filters classes an resources will be loaded from parent CL firstly
 * <li> {@code TypeFilter} filter class and resource name
 * <li> {@code TypeFinder} finds class byte code and resource
 * 
 *
 * @author   martin.liu
 * @since	 1.0
 */
public abstract class AopClassLoader extends URLClassLoader {

    /**
     * @param urls
     * @param parent
     */
    public AopClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }


    public abstract URL[] getUrls();


    public abstract void addParentFirstFilter(ParentFirstFilter parentFirstFilter);

    public abstract void addTypeFilter(TypeFilter typeilter);

    public abstract void addTypeFinder(TypeFinder typeFinder);


    /**
     * This interface filters classes and resources should be load from parent CL firstly 
     *
     */
    public static interface ParentFirstFilter {

        boolean isParentFirstClass(String name);

        boolean isParentFirstResource(String name);


        static class FilterChain implements ParentFirstFilter {

            private final List<ParentFirstFilter> chain = new ArrayList<>();

            public FilterChain addFilter(ParentFirstFilter parentFirstFilter) {
                if(parentFirstFilter == null)
                    return this;

                this.chain.add(parentFirstFilter);
                return this;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isParentFirstClass(String name) {
                for(ParentFirstFilter filter : chain) {
                    if(filter.isParentFirstClass(name))
                        return true;
                }

                return false;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isParentFirstResource(String name) {
                for(ParentFirstFilter filter : chain) {
                    if(filter.isParentFirstResource(name))
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
                if(typeFilter == null)
                    return this;

                this.chain.add(typeFilter);
                return this;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String filterTypeName(String name) {
                for(TypeFilter typeFilter : chain) {
                    name = typeFilter.filterTypeName(name);
                }
                return name;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String filterResourceName(String name) {
                for(TypeFilter typeFilter : chain) {
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
                if(typeFinder == null)
                    return this;

                this.chain.add(typeFinder);
                return this;
            }


            @Override
            public byte[] findByteCode(String name) {
                for(TypeFinder finder : chain) {
                    byte[] byteCode = finder.findByteCode(name);
                    if(byteCode != null)
                        return byteCode;
                }

                return null;
            }


            @Override
            public URL findResource(String name) {
                for(TypeFinder finder : chain) {
                    URL resource = finder.findResource(name);
                    if(resource != null)
                        return resource;
                }

                return null;
            }

            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                for(TypeFinder finder : chain) {
                    Enumeration<URL> resources = finder.findResources(name);
                    if(resources != null)
                        return resources;
                }

                return null;
            }
        }
    }
}
