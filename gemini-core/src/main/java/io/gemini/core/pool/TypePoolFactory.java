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
import io.gemini.core.pool.TypePools.EagerResolutionTypePool;
import io.gemini.core.pool.TypePools.ExplicitTypePool;
import io.gemini.core.pool.TypePools.LazyResolutionTypePool;
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

    ExplicitTypePool createTypePool(ClassLoader classLoader, JavaModule javaModule);


    class Default implements TypePoolFactory, PoolStrategy {

        private LocationStrategy locationStrategy;

        private final ConcurrentMap<ClassLoader, ExplicitTypePool> typePoolCache = new ConcurrentReferenceHashMap<>();


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
        public ExplicitTypePool createTypePool(ClassLoader classLoader, JavaModule javaModule) {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

            this.typePoolCache.computeIfAbsent(
                    cacheKey, 
                    key -> new ExplicitTypePool( 
                            doCreateTypePool(classLoader, javaModule) )
            );

            return this.typePoolCache.get(cacheKey);
        }

        protected TypePool doCreateTypePool(ClassLoader classLoader, JavaModule javaModule) {
            if(classLoader instanceof AopClassLoader)
                // reuse loaded Aop framework classes for better performance
                return TypePool.ClassLoading.of(classLoader);
            else
                return new EagerResolutionTypePool(
                        ClassLoaderUtils.getClassLoaderName(classLoader),
                        new CacheProvider.Simple.UsingSoftReference(), 
                        this.locationStrategy.classFileLocator(classLoader, javaModule), 
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
            ExplicitTypePool typePool = this.createTypePool(classLoader, null);

            // share cached type between AgentBuilder transformer and Pointcut matcher if possible
            CacheProvider cacheProvider = typePool.getCacheProvider();
            if(cacheProvider == null)
                cacheProvider = CacheProvider.Simple.withObjectType();

            return new TypePool.LazyFacade(
                    new LazyResolutionTypePool(        // new TypePool with changed classFileLocator
                            ClassLoaderUtils.getClassLoaderName(classLoader),
                            cacheProvider,
                            classFileLocator,
                            ReaderMode.FAST) )
            ;
        }
    }
}
