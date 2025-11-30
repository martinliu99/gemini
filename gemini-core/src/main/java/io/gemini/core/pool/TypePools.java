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

import java.util.function.Supplier;

import net.bytebuddy.build.CachedReturnPlugin;
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
public interface TypePools {


    /**
     * This class exposes CacheProvider to use Type between AgentBuilder transformer and Pointcut matcher.
     *
     */
    class EagerResolutionTypePool extends TypePool.Default {

        private final String poolName;


        public EagerResolutionTypePool(String poolName, CacheProvider cacheProvider, ClassFileLocator classFileLocator, ReaderMode readerMode) {
            super(cacheProvider, classFileLocator, readerMode);

            this.poolName = poolName;
        }

        public EagerResolutionTypePool(String poolName, CacheProvider cacheProvider, ClassFileLocator classFileLocator, ReaderMode readerMode, TypePool parentPool) {
            super(cacheProvider, classFileLocator, readerMode, parentPool);

            this.poolName = poolName;
        }


        protected String getPoolName() {
            return poolName;
        }

        // expose cache provider to reuse
        public CacheProvider getCacheProvider() {
            return this.cacheProvider;
        }

        @Override
        public Resolution describe(String name) {
            Resolution resolution = cacheProvider.find(name);
            if (resolution != null && resolution.isResolved())
                return resolution;

            return super.describe(name);
        }

        @Override
        public String toString() {
            return poolName;
        }
    }


    /**
     * A variant of {@link EagerResolutionTypePool} that resolves type descriptions lazily. 
     *
     */
    class LazyResolutionTypePool extends EagerResolutionTypePool {

        /**
         * @param cacheProvider
         * @param classFileLocator
         * @param readerMode
         */
        public LazyResolutionTypePool(String poolName, CacheProvider cacheProvider, ClassFileLocator classFileLocator, ReaderMode readerMode) {
            super(poolName, cacheProvider, classFileLocator, readerMode);
        }

        public LazyResolutionTypePool(String poolName, CacheProvider cacheProvider, ClassFileLocator classFileLocator, ReaderMode readerMode, TypePool parentPool) {
            super(poolName, cacheProvider, classFileLocator, readerMode, parentPool);
        }


        @Override
        protected Resolution doDescribe(String name) {
            return new TypePools.LazyResolution(
                    name, 
                    new Supplier<Resolution>() {

                        // cache resolved type
                        private Resolution resolution;

                        @Override
                        public Resolution get() {
                            if (resolution == null)
                                resolution = LazyResolutionTypePool.super.doDescribe(name);

                            return resolution;
                        }
                    }
            );
        }
    }


    /**
     * The lazy resolution for a lazy facade for a type pool.
     */
    static class LazyResolution implements Resolution {

        /**
         * The type pool to delegate to.
         */
        private final Supplier<Resolution> resolution;

        LazyTypeDescription lazyTypeDescription;

        /**
         * Creates a lazy resolution for a lazy facade for a type pool.
         *
         * @param typePool The type pool to delegate to.
         * @param name     The name of the type that is represented by this resolution.
         */
        protected LazyResolution(String name, Supplier<Resolution> resolution) {
            this.resolution = resolution;
            this.lazyTypeDescription = new LazyTypeDescription(name, resolution);
        }

        /**
         * {@inheritDoc}
         */
        public boolean isResolved() {
            return resolution.get().isResolved();
        }

        /**
         * {@inheritDoc}
         */
        public TypeDescription resolve() {
            return lazyTypeDescription;
        }
    }


    /**
     * A description of a type that delegates to another type pool once a property that is not the name is resolved.
     */
    static class LazyTypeDescription extends TypeDescription.AbstractBase.OfSimpleType.WithDelegation
            implements TypeResolutionInspector {

        private ResolutionLevel resolutionLevel = ResolutionLevel.NO_RESOLUTION;

        /**
         * The type pool to delegate to.
         */
        private final Supplier<Resolution> resolution;

        /**
         * The name of the type that is represented by this resolution.
         */
        private final String name;

        /**
         * Creates a new lazy type resolution.
         *
         * @param name The type pool to delegate to.
         * @param resolution     The name of the type.
         */
        protected LazyTypeDescription(String name, Supplier<Resolution> resolution) {
            this.name = name;
            this.resolution = resolution;
        }

        /**
         * {@inheritDoc}
         */
        public String getName() {
            return name;
        }

        @Override
        @CachedReturnPlugin.Enhance("delegate")
        protected TypeDescription delegate() {
            this.setResolutionLevel(ResolutionLevel.TYPE_RESOLUTION);

            return resolution.get().resolve();
        }

        /**
         * {@inheritDoc}
         */
        public Generic getSuperClass() {
            this.setResolutionLevel(ResolutionLevel.SUPER_TYPE_RESOLUTION);

            return delegate().getSuperClass();
        }

        /**
         * {@inheritDoc}
         */
        public TypeList.Generic getInterfaces() {
            this.setResolutionLevel(ResolutionLevel.SUPER_TYPE_RESOLUTION);

            return delegate().getInterfaces();
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
        public void resetInspection() {
            this.resolutionLevel = ResolutionLevel.NO_RESOLUTION;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
