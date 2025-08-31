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

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface TypePools {

    /**
     * This type pool supports both eagerly and lazily type resolution.
     *
     * @author   martin.liu
     * @since	 1.0
     */
    class LazyResolutionTypePool extends TypePool.Default.WithLazyResolution {

        private final String poolName;

        /**
         * @param cacheProvider
         * @param classFileLocator
         * @param readerMode
         */
        public LazyResolutionTypePool(String poolName, CacheProvider cacheProvider, ClassFileLocator classFileLocator, ReaderMode readerMode) {
            super(cacheProvider, classFileLocator, readerMode);

            this.poolName = poolName;
        }

        public LazyResolutionTypePool(String poolName, CacheProvider cacheProvider, ClassFileLocator classFileLocator, ReaderMode readerMode, TypePool parentPool) {
            super(cacheProvider, classFileLocator, readerMode, parentPool);

            this.poolName = poolName;
        }

        // expose cache provider to reuse
        public CacheProvider getCacheProvider() {
            return this.cacheProvider;
        }

        @Override
        public String toString() {
            return poolName;
        }
    }


    /**
     * This type pool resolves type with explicitly type definitions.
     *
     * @author   martin.liu
     * @since	 1.0
     */
    class ExplicitTypePool implements TypePool {

        private final TypePool delegate;
        private final ConcurrentMap<String /* Type Name */, Resolution> typeResolutions = new ConcurrentHashMap<>();


        public ExplicitTypePool(TypePool typePool) {
            this.delegate = typePool == null ? TypePool.Empty.INSTANCE : typePool;
        }

        public TypePool getDelegate() {
            return delegate;
        }

        public CacheProvider getCacheProvider() {
            return delegate instanceof LazyResolutionTypePool == false
                ? null
                : ((LazyResolutionTypePool) delegate).getCacheProvider();
        }

        public void addTypeDescription(TypeDescription typeDescription) {
            if(typeDescription == null) return;

            this.typeResolutions.put(typeDescription.getTypeName(), new Resolution.Simple(typeDescription));
        }

        public void removeTypeDescription(String typeName) {
            this.typeResolutions.remove(typeName);
        }

        @Override
        public Resolution describe(String name) {
            Resolution resolution = typeResolutions.get(name);
            if(resolution != null) 
                return resolution;

            return delegate.describe(name);
        }

        @Override
        public void clear() {
            this.typeResolutions.clear();
        }


        @Override
        public String toString() {
            return ExplicitTypePool.class.getSimpleName() + "-" + delegate.toString();
        }
    }
}
