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
/**
 * 
 */
package io.gemini.core.pool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.Resolution;

/**
 * Internal collection of ByteBuddy {@link TypePool} implementations used by the Gemini AOP framework.
 * <p>
 * Key inner classes:
 * <ul>
 *   <li>{@link Default} – extends {@link TypePool.Default} and exposes the cache provider for sharing</li>
 *   <li>{@link Explicit} – a simple map-backed pool for types that have already been described</li>
 *   <li>{@link TyepResolutionDetector} – wraps resolutions to track which type properties were accessed</li>
 *   <li>{@link DelegatedResolution} – a lazy resolution that delegates to a supplier</li>
 *   <li>{@link DelegatedTypeDescription} – a type description that defers full resolution until needed</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
interface TypePools {

    /**
     * This TypePool extends {@code TypePool.Default} and exposes the cache provider
     * so it can be shared between the AgentBuilder transformer and the pointcut matcher.
     */
    class Default extends TypePool.Default {

        private final String poolName;


        /**
         * Constructs a {@code Default} type pool without a parent pool.
         *
         * @param poolName         a human-readable name for this pool
         * @param cacheProvider    the cache provider for resolved types
         * @param classFileLocator the locator for class files
         * @param readerMode       the ASM reader mode
         */
        public Default(String poolName, 
                CacheProvider cacheProvider, 
                ClassFileLocator classFileLocator, 
                ReaderMode readerMode) {
            super(cacheProvider, classFileLocator, readerMode);

            this.poolName = poolName;
        }

        /**
         * Constructs a {@code Default} type pool with a parent pool for fallback resolution.
         *
         * @param poolName         a human-readable name for this pool
         * @param cacheProvider    the cache provider for resolved types
         * @param classFileLocator the locator for class files
         * @param readerMode       the ASM reader mode
         * @param parentPool       the parent pool to delegate to when a type is not found
         */
        public Default(String poolName, 
                CacheProvider cacheProvider, 
                ClassFileLocator classFileLocator, 
                ReaderMode readerMode,
                TypePool parentPool) {
            super(cacheProvider, classFileLocator, readerMode, parentPool);

            this.poolName = poolName;
        }


        /**
         * Returns the pool name.
         *
         * @return the pool name
         */
        protected String getPoolName() {
            return poolName;
        }


        /**
         * Exposes the cache provider to allow sharing cached types between the
         * AgentBuilder transformer and the pointcut matcher.
         *
         * @return the cache provider
         */
        public CacheProvider getCacheProvider() {
            return this.cacheProvider;
        }


        @Override
        public String toString() {
            return poolName;
        }
    }


    /**
     * An in-memory {@link TypePool} backed by a {@link java.util.concurrent.ConcurrentMap}.
     * Used to cache type resolutions that have already been computed during bytecode transformation,
     * so they can be reused by the pointcut matcher without re-reading the class file.
     */
    class Explicit implements TypePool {

        private final ConcurrentMap<String, Resolution> resolutions;


        /**
         * Constructs an empty {@code Explicit} type pool.
         */
        public Explicit() {
            this.resolutions = new ConcurrentHashMap<>();
        }

        /**
         * Adds a type resolution to this pool.
         *
         * @param typeName   the fully-qualified type name
         * @param resolution the resolution to cache
         */
        public void addTypeResolution(String typeName, Resolution resolution) {
            if (StringUtils.hasText(typeName) == false || resolution == null) return;

            this.resolutions.put(typeName, resolution);
        }

        /**
         * Removes and returns the cached resolution for the given type name.
         *
         * @param typeName the fully-qualified type name
         * @return the removed resolution, or {@code null} if not cached
         */
        public Resolution removeTypeResolution(String typeName) {
            if (StringUtils.hasText(typeName) == false) return null;

            return resolutions.remove(typeName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Resolution describe(String typeName) {
            Resolution resolution;
            return (resolution = resolutions.get(typeName)) != null
                    ? resolution
                    : new Resolution.Illegal(typeName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() {
        }
    }


    /**
     * Extends {@link TypePools.Default} to wrap each type resolution in a
     * {@link DelegatedResolution} that tracks which type properties were accessed
     * (for type resolution performance analysis).
     */
    class TyepResolutionDetector extends TypePools.Default {

        private final boolean lazyResolution;


        /**
         * Constructs a {@code TyepResolutionDetector} without a parent pool.
         *
         * @param poolName         a human-readable name for this pool
         * @param cacheProvider    the cache provider for resolved types
         * @param classFileLocator the locator for class files
         * @param readerMode       the ASM reader mode
         * @param lazyResolution   whether to resolve types lazily
         */
        public TyepResolutionDetector(String poolName, 
                CacheProvider cacheProvider,
                ClassFileLocator classFileLocator, 
                ReaderMode readerMode, 
                boolean lazyResolution) {
            this(poolName, cacheProvider, classFileLocator, readerMode, lazyResolution, TypePool.Empty.INSTANCE);
        }

        /**
         * Constructs a {@code TyepResolutionDetector} with a parent pool.
         *
         * @param poolName         a human-readable name for this pool
         * @param cacheProvider    the cache provider for resolved types
         * @param classFileLocator the locator for class files
         * @param readerMode       the ASM reader mode
         * @param lazyResolution   whether to resolve types lazily
         * @param parentPool       the parent pool to delegate to when a type is not found
         */
        public TyepResolutionDetector(String poolName, 
                CacheProvider cacheProvider,
                ClassFileLocator classFileLocator, 
                ReaderMode readerMode, 
                boolean lazyResolution,
                TypePool parentPool) {
            super(poolName, cacheProvider, classFileLocator, readerMode, parentPool);

            this.lazyResolution = lazyResolution;
        }


        @Override
        protected Resolution doDescribe(String name) {
            if (lazyResolution == false) {
                // resolve type eagerly
                Resolution resolution = super.doDescribe(name);
                Supplier<Resolution> resolutionSupplier = () -> resolution;

                return new DelegatedResolution(
                        name,
                        resolutionSupplier,
                        new DelegatedTypeDescription.TyepResolutionDetector(name, resolutionSupplier)
                );
            } else {
                Supplier<Resolution> resolutionSupplier = new Supplier<Resolution>() {

                    // cache resolved type
                    private Resolution resolution;

                    @Override
                    public Resolution get() {
                        if (resolution == null)
                            resolution = TyepResolutionDetector.super.doDescribe(name);

                        return resolution;
                    }
                };

                // resolve type lazily
                return new DelegatedResolution(
                        name, 
                        resolutionSupplier,
                        new DelegatedTypeDescription.TyepResolutionDetector(name, resolutionSupplier)
                );
            }
        }
    }


    /**
     * A delegated resolution that holds a resolution supplier and a {@link DelegatedTypeDescription}.
     */
    class DelegatedResolution implements Resolution {

        private final Supplier<Resolution> delegateSupplier;
        private final DelegatedTypeDescription delegatedTypeDescription;


        /**
         * Constructs a {@code DelegatedResolution} with a default {@link DelegatedTypeDescription}.
         *
         * @param name             the type name
         * @param delegateSupplier the supplier that provides the actual resolution
         */
        protected DelegatedResolution(String name, Supplier<Resolution> delegateSupplier) {
            this(name, delegateSupplier, new DelegatedTypeDescription(name, delegateSupplier));
        }

        /**
         * Constructs a {@code DelegatedResolution} with a custom {@link DelegatedTypeDescription}.
         *
         * @param name                     the type name
         * @param delegateSupplier         the supplier that provides the actual resolution
         * @param delegatedTypeDescription the type description to return from {@link #resolve()}
         */
        protected DelegatedResolution(String name, Supplier<Resolution> delegateSupplier, DelegatedTypeDescription delegatedTypeDescription) {
            this.delegateSupplier = delegateSupplier;
            this.delegatedTypeDescription = delegatedTypeDescription;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isResolved() {
            return delegateSupplier.get().isResolved();
        }

        /**
         * {@inheritDoc}
         */
        public TypeDescription resolve() {
            return delegatedTypeDescription;
        }
    }


    /**
     * A type description that delegates to another resolution supplier once a property
     * other than the type name is accessed.
     */
    class DelegatedTypeDescription extends TypeDescription.AbstractBase.OfSimpleType.WithDelegation {

        private final String name;
        private final Supplier<Resolution> delegateSupplier;


        /**
         * Constructs a {@code DelegatedTypeDescription} for the given type name.
         *
         * @param name             the type name
         * @param delegateSupplier the supplier that provides the actual resolution
         */
        protected DelegatedTypeDescription(String name, Supplier<Resolution> delegateSupplier) {
            this.name = name;
            this.delegateSupplier = delegateSupplier;
        }

        /**
         * {@inheritDoc}
         */
        public String getName() {
            return name;
        }


        @Override
        protected TypeDescription delegate() {
            return delegateSupplier.get().resolve();
        }

        /**
         * {@inheritDoc}
         */
        public Generic getSuperClass() {
            return delegateSupplier.get().resolve().getSuperClass();
        }

        /**
         * {@inheritDoc}
         */
        public TypeList.Generic getInterfaces() {
            return delegateSupplier.get().resolve().getInterfaces();
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return name;
        }


        /**
         * Extends {@link DelegatedTypeDescription} to implement {@link TypeResolutionInspector},
         * recording whether superclass or interface information was accessed during matching.
         */
        static class TyepResolutionDetector extends DelegatedTypeDescription implements TypeResolutionInspector {

            private ResolutionLevel resolutionLevel = ResolutionLevel.NO_RESOLUTION;


            protected TyepResolutionDetector(String name, Supplier<Resolution> delegateSupplier) {
                super(name, delegateSupplier);
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public void resetInspection() {
                this.resolutionLevel = ResolutionLevel.NO_RESOLUTION;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public ResolutionLevel getResolutionLevel() {
                return resolutionLevel;
            }

            /** 
             * {@inheritDoc} 
             */
            @Override
            public void setResolutionLevel(ResolutionLevel resolutionLevel) {
                if (this.resolutionLevel.ordinal() < resolutionLevel.ordinal())
                    this.resolutionLevel = resolutionLevel;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            protected TypeDescription delegate() {
                this.setResolutionLevel(ResolutionLevel.TYPE_RESOLUTION);

                return super.delegate();
            }

            /**
             * {@inheritDoc}
             */
            public Generic getSuperClass() {
                this.setResolutionLevel(ResolutionLevel.SUPER_TYPE_RESOLUTION);

                return super.delegate().getSuperClass();
            }

            /**
             * {@inheritDoc}
             */
            public TypeList.Generic getInterfaces() {
                this.setResolutionLevel(ResolutionLevel.SUPER_TYPE_RESOLUTION);

                return super.delegate().getInterfaces();
            }
        }
    }
}
