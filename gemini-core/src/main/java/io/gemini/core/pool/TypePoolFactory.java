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
package io.gemini.core.pool;

import java.util.concurrent.ConcurrentMap;

import io.gemini.api.classloader.BaseClassLoader;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.pool.TypePools.EagerResolutionTypePool;
import io.gemini.core.pool.TypePools.LazyResolutionTypePool;
import io.gemini.core.util.ClassLoaderUtils;
import net.bytebuddy.agent.builder.AgentBuilder.CircularityLock;
import net.bytebuddy.agent.builder.AgentBuilder.DescriptionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.LocationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.CacheProvider;
import net.bytebuddy.pool.TypePool.Default.ReaderMode;
import net.bytebuddy.pool.TypePool.Resolution;
import net.bytebuddy.utility.JavaModule;

public interface TypePoolFactory {

    DescriptionStrategy getDescriptionStrategy();

    PoolStrategy getPoolStrategy();

    LocationStrategy getLocationStrategy();

    TypePool createTypePool(ClassLoader classLoader, JavaModule javaModule);


    class Default implements TypePoolFactory, PoolStrategy, DescriptionStrategy {

        private LocationStrategy locationStrategy;

        private final ConcurrentMap<ClassLoader, TypePool> typePoolCache = new ConcurrentReferenceHashMap<>();


        public Default() {
            this(null);
        }

        public Default(LocationStrategy locationStrategy) {
            this.locationStrategy = locationStrategy == null ? LocationStrategy.ForClassLoader.WEAK : locationStrategy;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public DescriptionStrategy getDescriptionStrategy() {
            return this;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public PoolStrategy getPoolStrategy() {
            return this;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public LocationStrategy getLocationStrategy() {
            return this.locationStrategy;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public TypePool createTypePool(ClassLoader classLoader, JavaModule javaModule) {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

            this.typePoolCache.computeIfAbsent(
                    cacheKey, 
                    key -> doCreateTypePool(classLoader, javaModule, null, null) 
            );

            return this.typePoolCache.get(cacheKey);
        }

        protected TypePool doCreateTypePool(ClassLoader classLoader, JavaModule javaModule,
                CacheProvider cacheProvider, ClassFileLocator classFileLocator) {
            if (classLoader instanceof BaseClassLoader)
                // reuse loaded Aop framework classes for better performance
                return TypePool.ClassLoading.of(classLoader);
            else
                // eagerly look up TypeDescription of joinpoint type.
                return new EagerResolutionTypePool(
                        ClassLoaderUtils.getClassLoaderName(classLoader),
                        cacheProvider != null ? cacheProvider : new CacheProvider.Simple.UsingSoftReference(), 
                        classFileLocator != null ? classFileLocator : this.locationStrategy.classFileLocator(classLoader, javaModule), 
                        ReaderMode.FAST);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public TypePool typePool(ClassFileLocator classFileLocator, ClassLoader classLoader) {
            return this.typePool(classFileLocator, classLoader, null);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public TypePool typePool(ClassFileLocator classFileLocator, ClassLoader classLoader, String name) {
            // create TypePool for Pointcut matcher in advance
            TypePool typePool = this.createTypePool(classLoader, null);

//            TypePool pool = typePool.getDelegate();
            if (typePool instanceof EagerResolutionTypePool == false) 
                return new TypePool.LazyFacade(typePool);

            // share cached type between AgentBuilder transformer and Pointcut matcher if possible
            CacheProvider cacheProvider = ((EagerResolutionTypePool) typePool).getCacheProvider();
            if (cacheProvider == null)
                cacheProvider = new CacheProvider.Simple.UsingSoftReference();

            // lazily lookup TypePool if property other than the type's name is looked up.
            return new LazyResolutionTypePool(
                    ClassLoaderUtils.getClassLoaderName(classLoader),
                    cacheProvider, 
                    classFileLocator, 
                    ReaderMode.FAST);
        }

        /* {@inheritDoc}
         */
        @Override
        public boolean isLoadedFirst() {
            return true;
        }

        /* {@inheritDoc}
         */
        @Override
        public TypeDescription apply(String name, Class<?> type, TypePool typePool, CircularityLock circularityLock,
                ClassLoader classLoader, JavaModule module) {
            TypeDescription typeDescription = type == null
                    ? typePool.describe(name).resolve()
                    : TypeDescription.ForLoadedType.of(type);

            if (type != null && typePool instanceof LazyResolutionTypePool) {
                // cache TypeDescription of loaded Type
                ((LazyResolutionTypePool) typePool).getCacheProvider().register(
                        name, new Resolution.Simple(typeDescription));
            }

            return typeDescription;
        }

    }
}
