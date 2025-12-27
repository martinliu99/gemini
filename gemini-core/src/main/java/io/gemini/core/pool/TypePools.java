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
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
interface TypePools {

    /**
     * This TypePool extends {@code TypePool.Default} and expose cache provider.
     *
     */
    class Default extends TypePool.Default {

        private final String poolName;


        public Default(String poolName, 
                CacheProvider cacheProvider, 
                ClassFileLocator classFileLocator, 
                ReaderMode readerMode) {
            super(cacheProvider, classFileLocator, readerMode);

            this.poolName = poolName;
        }

        public Default(String poolName, 
                CacheProvider cacheProvider, 
                ClassFileLocator classFileLocator, 
                ReaderMode readerMode,
                TypePool parentPool) {
            super(cacheProvider, classFileLocator, readerMode, parentPool);

            this.poolName = poolName;
        }


        protected String getPoolName() {
            return poolName;
        }


        /**
         * expose cache provider to reuse type between AgentBuilder transformer and Pointcut matcher
         * 
         * @return
         */
        public CacheProvider getCacheProvider() {
            return this.cacheProvider;
        }


        @Override
        public String toString() {
            return poolName;
        }
    }


    class Explicit implements TypePool {

        private final ConcurrentMap<String, Resolution> resolutions;


        public Explicit() {
            this.resolutions = new ConcurrentHashMap<>();
        }

        public void addTypeResolution(String typeName, Resolution resolution) {
            if (StringUtils.hasText(typeName) == false || resolution == null) return;

            this.resolutions.put(typeName, resolution);
        }

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


    class TyepResolutionDetector extends TypePools.Default {

        private final boolean lazyResolution;


        public TyepResolutionDetector(String poolName, 
                CacheProvider cacheProvider,
                ClassFileLocator classFileLocator, 
                ReaderMode readerMode, 
                boolean lazyResolution) {
            this(poolName, cacheProvider, classFileLocator, readerMode, lazyResolution, TypePool.Empty.INSTANCE);
        }

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
     * A delegated resolution holds resolution supplier and {@code DelegatedTypeDescription}.
     */
    class DelegatedResolution implements Resolution {

        private final Supplier<Resolution> delegateSupplier;
        private final DelegatedTypeDescription delegatedTypeDescription;


        protected DelegatedResolution(String name, Supplier<Resolution> delegateSupplier) {
            this(name, delegateSupplier, new DelegatedTypeDescription(name, delegateSupplier));
        }

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
     * A description of a type that delegates to another type resolution supplier once a property that is not the name is resolved.
     */
    class DelegatedTypeDescription extends TypeDescription.AbstractBase.OfSimpleType.WithDelegation {

        private final String name;
        private final Supplier<Resolution> delegateSupplier;


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


        @Override
        public String toString() {
            return name;
        }


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
