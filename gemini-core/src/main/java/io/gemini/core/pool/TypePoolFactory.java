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

import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.ClassLoaderUtils;
import net.bytebuddy.agent.builder.AgentBuilder.LocationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.CacheProvider;
import net.bytebuddy.pool.TypePool.Default.ReaderMode;
import net.bytebuddy.utility.JavaModule;

public interface TypePoolFactory {

    PoolStrategy getPoolStrategy();

    LocationStrategy getLocationStrategy();

    TypePool createTypePool(ClassLoader classLoader, JavaModule javaModule);


    class Default implements TypePoolFactory, PoolStrategy {

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

            if(this.typePoolCache.containsKey(cacheKey) == false) {
                this.typePoolCache.computeIfAbsent(
                        cacheKey, 
                        key -> doCreateTypePool(classLoader, javaModule)
                );
            }

            return this.typePoolCache.get(cacheKey);
        }

        protected TypePool doCreateTypePool(ClassLoader classLoader, JavaModule javaModule) {
            TypePool parentTypePool = null;
            if(classLoader != null)
                parentTypePool = createTypePool(classLoader.getParent(), null);

            if(classLoader instanceof AopClassLoader)
                // reuse loaded Aop framework classes for better performance
                return parentTypePool != null 
                        ? TypePool.ClassLoading.of(classLoader, parentTypePool)
                        : TypePool.ClassLoading.of(classLoader);
            else {
                // load cached type from parent type pool if existing
                return parentTypePool != null
                        ? new TypePool.Default(
                            CacheProvider.Simple.withObjectType(), 
                            this.locationStrategy.classFileLocator(classLoader, javaModule), 
                            ReaderMode.FAST,
                            parentTypePool)
                        : new TypePool.Default(
                            CacheProvider.Simple.withObjectType(), 
                            this.locationStrategy.classFileLocator(classLoader, javaModule), 
                            ReaderMode.FAST);
            }
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
            TypePool typePool = this.createTypePool(classLoader, null);

            // share cached type between AgentBuilder transformer and Pointcut matcher if possible
            CacheProvider cacheProvider = typePool instanceof LazyTypePool 
                    ? ((LazyTypePool) typePool).getCacheProvider() : CacheProvider.Simple.withObjectType();

            return new TypePool.LazyFacade(
                    new TypePool.Default.WithLazyResolution(
                            cacheProvider,
                            classFileLocator,
                            ReaderMode.FAST) );
        }


        static class LazyTypePool extends TypePool.Default.WithLazyResolution {

            /**
             * @param cacheProvider
             * @param classFileLocator
             * @param readerMode
             */
            public LazyTypePool(CacheProvider cacheProvider, ClassFileLocator classFileLocator, ReaderMode readerMode) {
                super(cacheProvider, classFileLocator, readerMode);
            }

            public LazyTypePool(CacheProvider cacheProvider, ClassFileLocator classFileLocator, ReaderMode readerMode, TypePool parentPool) {
                super(cacheProvider, classFileLocator, readerMode, parentPool);
            }

            public CacheProvider getCacheProvider() {
                return this.cacheProvider;
            }
        }
    }
}
