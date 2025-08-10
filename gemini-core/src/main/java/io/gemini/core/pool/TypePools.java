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
 */
public interface TypePools {

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
