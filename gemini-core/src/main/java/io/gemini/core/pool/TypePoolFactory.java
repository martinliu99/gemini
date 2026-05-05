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
package io.gemini.core.pool;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import io.gemini.api.classloader.BaseClassLoader;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.pool.TypePools.DelegatedTypeDescription;
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

/**
 * Factory for ByteBuddy {@link TypePool} instances used during class scanning and bytecode transformation.
 * <p>
 * Implements both {@link PoolStrategy} and {@link DescriptionStrategy} so it can be plugged directly
 * into ByteBuddy's {@link net.bytebuddy.agent.builder.AgentBuilder}.
 * Maintains a per-class-loader cache of {@link TypePool} instances and an explicit pool for
 * types that have already been described during transformation.
 * </p>
 *
 * @author   martin.liu
 */
public interface TypePoolFactory {

    /**
     * Returns the {@link DescriptionStrategy} for use with ByteBuddy's AgentBuilder.
     *
     * @return the description strategy
     */
    DescriptionStrategy getDescriptionStrategy();

    /**
     * Returns the {@link PoolStrategy} for use with ByteBuddy's AgentBuilder.
     *
     * @return the pool strategy
     */
    PoolStrategy getPoolStrategy();

    /**
     * Returns the {@link LocationStrategy} used to locate class files.
     *
     * @return the location strategy
     */
    LocationStrategy getLocationStrategy();

    /**
     * Creates or retrieves a cached {@link TypePool} for the given class loader and module.
     *
     * @param classLoader the class loader to create a pool for
     * @param javaModule  the Java module (may be {@code null})
     * @return the type pool
     */
    TypePool createTypePool(ClassLoader classLoader, JavaModule javaModule);

    /**
     * Registers a existing type pool for the given class loader and module.
     * 
     * @param classLoader the class loader to create a pool for
     * @param javaModule  the Java module (may be {@code null})
     * @param typePool the type pool to be registered
     */
    void registerTypePool(ClassLoader classLoader, JavaModule javaModule, TypePool typePool);

    /**
     * Removes and returns the cached type resolution for the given type name.
     *
     * @param typeName the fully-qualified type name
     * @return the removed resolution, or {@code null} if not cached
     */
    Resolution removeTypeResolution(String typeName);


    /**
     * Default implementation of {@link TypePoolFactory} that caches one {@link TypePool}
     * per class loader and implements both {@link PoolStrategy} and {@link DescriptionStrategy}
     * for direct use with ByteBuddy's {@link net.bytebuddy.agent.builder.AgentBuilder}.
     */
    class Default implements PoolStrategy, DescriptionStrategy, TypePoolFactory {

        private final LocationStrategy locationStrategy;
        private final ConcurrentMap<ClassLoader, TypePool> types;

        private final TypePools.Explicit explicitTypePool;


        /**
         * Constructs a {@code Default} factory with the default weak class-file location strategy.
         */
        public Default() {
            this(null);
        }

        /**
         * Constructs a {@code Default} factory with the given location strategy.
         *
         * @param locationStrategy the strategy for locating class files (may be {@code null} for default)
         */
        public Default(LocationStrategy locationStrategy) {
            this.locationStrategy = locationStrategy == null ? LocationStrategy.ForClassLoader.WEAK : locationStrategy;
            this.types = new ConcurrentReferenceHashMap<>();

            this.explicitTypePool = new TypePools.Explicit();
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
        public DescriptionStrategy getDescriptionStrategy() {
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
        public TypePool typePool(ClassFileLocator classFileLocator, ClassLoader classLoader) {
            return this.typePool(classFileLocator, classLoader, null);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public TypePool typePool(ClassFileLocator classFileLocator, ClassLoader classLoader, String name) {
            // 1.create TypePool for Pointcut matcher in advance
            TypePool typePool = this.createTypePool(classLoader, null);
            if (typePool instanceof TypePools.Default == false) 
                return new TypePool.LazyFacade(typePool);


            // 2.lazily lookup TypePool if property other than the type's name is looked up.
            // share cached type between AgentBuilder transformer and Pointcut matcher if possible
            return doCreateByteBuddyTypePool(
                    ClassLoaderUtils.getClassLoaderName(classLoader),
                    ((TypePools.Default) typePool).getCacheProvider(), 
                    classFileLocator,
                    ReaderMode.FAST
            );
        }

        protected TypePool doCreateByteBuddyTypePool(String poolName, CacheProvider cacheProvider, 
                ClassFileLocator classFileLocator, ReaderMode readerMode) {
            return new TypePool.LazyFacade(
                    new TypePool.Default.WithLazyResolution(
                            cacheProvider, 
                            classFileLocator, 
                            readerMode
                    )
            );
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        public boolean isLoadedFirst() {
            return true;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public TypeDescription apply(String typeName, Class<?> type, TypePool byteBuddyTypePool, 
                CircularityLock circularityLock, ClassLoader classLoader, JavaModule module) {
            // 1.lookup resolved type from CacheProvider
            TypePool cachedTypePool = createTypePool(classLoader, module);

            if ( cachedTypePool instanceof TypePools.Default) {
                TypePools.Default frameworkTypePool = (TypePools.Default) cachedTypePool;
                Resolution resolution = frameworkTypePool.getCacheProvider().find(typeName);
                if (resolution != null)
                    return resolution.resolve();
            }


            // 2.cache TypeDescription
            Resolution resolution = type != null
                    ? resolution = doCreateTypeResolution( TypeDescription.ForLoadedType.of(type) )
                    : byteBuddyTypePool.describe(typeName);

            this.explicitTypePool.addTypeResolution(typeName, resolution);
            return resolution.resolve();
        }

        protected Resolution doCreateTypeResolution(TypeDescription typeDescription) {
            return new Resolution.Simple(typeDescription);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public TypePool createTypePool(ClassLoader classLoader, JavaModule javaModule) {
            return this.types.computeIfAbsent(
                    ClassLoaderUtils.maskNull(classLoader), 
                    key -> createTypePool(classLoader, javaModule, null, null) 
            );
        }

        private TypePool createTypePool(ClassLoader classLoader, JavaModule javaModule,
                CacheProvider cacheProvider, ClassFileLocator classFileLocator) {
            if (classLoader instanceof BaseClassLoader)
                // reuse loaded Aop framework classes for better performance
                return TypePool.ClassLoading.of(classLoader);
            else
                // eagerly look up TypeDescription of target type.
                return doCreateFrameworkTypePool(
                        ClassLoaderUtils.getClassLoaderName(classLoader),
                        cacheProvider != null ? cacheProvider : new TypePool.CacheProvider.Simple.UsingSoftReference(), 
                        classFileLocator != null ? classFileLocator : getLocationStrategy().classFileLocator(classLoader, javaModule),
                        ReaderMode.FAST,
                        explicitTypePool
                );
        }

        protected TypePool doCreateFrameworkTypePool(String poolName, CacheProvider cacheProvider, 
                ClassFileLocator classFileLocator, ReaderMode readerMode, TypePool parentPool) {
            return new TypePools.Default(
                    poolName,
                    cacheProvider, 
                    classFileLocator, 
                    readerMode,
                    parentPool
            );
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        public void registerTypePool(ClassLoader classLoader, JavaModule javaModule, TypePool typePool) {
            TypePool existingTypePool = this.types.putIfAbsent(ClassLoaderUtils.maskNull(classLoader), typePool);
            if (existingTypePool != null)
                throw new IllegalStateException(
                        String.format("Already registered type pool %s for class loader %s", existingTypePool, classLoader) );
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        public Resolution removeTypeResolution(String typeName) {
            return this.explicitTypePool.removeTypeResolution(typeName);
        }


        /**
         * Extends {@link TypePoolFactory.Default} to wrap type resolutions in
         * {@link io.gemini.core.pool.TypePools.DelegatedResolution} instances that track
         * which type properties are accessed during pointcut matching.
         */
        public static class TyepResolutionDetector extends TypePoolFactory.Default {

            /**
             * Constructs a {@code TyepResolutionDetector} with the given location strategy.
             *
             * @param locationStrategy the strategy for locating class files
             */
            public TyepResolutionDetector(LocationStrategy locationStrategy) {
                super(locationStrategy);
            }


            @Override
            protected TypePool doCreateByteBuddyTypePool(String poolName, CacheProvider cacheProvider, 
                    ClassFileLocator classFileLocator, ReaderMode readerMode) {
                return new TypePools.TyepResolutionDetector(
                        poolName,
                        cacheProvider, 
                        classFileLocator, 
                        readerMode,
                        true
                );
            }

            @Override
            protected TypePool doCreateFrameworkTypePool(String poolName, CacheProvider cacheProvider, 
                    ClassFileLocator classFileLocator, ReaderMode readerMode, TypePool parentPool) {
                return new TypePools.TyepResolutionDetector(
                        poolName,
                        cacheProvider, 
                        classFileLocator, 
                        readerMode,
                        false,
                        parentPool
                );
            }

            @Override
            protected Resolution doCreateTypeResolution(TypeDescription typeDescription) {
                String typeName = typeDescription.getTypeName();
                Supplier<Resolution> resolutionSupplier = () -> new Resolution.Simple(typeDescription);

                return new TypePools.DelegatedResolution(
                        typeName, 
                        resolutionSupplier,
                        new DelegatedTypeDescription.TyepResolutionDetector(typeName, resolutionSupplier)
                );
            }
        }
    }
}
