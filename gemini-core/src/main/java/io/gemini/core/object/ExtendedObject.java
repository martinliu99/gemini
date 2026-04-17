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
package io.gemini.core.object;

import io.gemini.core.concurrent.ConcurrentReferenceHashMap;

/**
 * Associates additional state with existing object instances without modifying their class.
 * <p>
 * Useful for attaching per-instance metadata to objects that cannot be subclassed.
 * The {@link Factory} creates and caches {@link ExtendedObject} instances keyed by
 * the type pair (object type, extension type).
 * </p>
 *
 * @param <T> the type of the base object
 * @param <E> the type of the extension data
 *
 * @author   martin.liu
 */
public interface ExtendedObject<T, E> {

    /**
     * Returns the extension data associated with the given object instance.
     *
     * @param type the base object instance
     * @return the associated extension data, or {@code null} if none
     */
    E get(T type);

    /**
     * Associates extension data with the given object instance.
     *
     * @param type the base object instance
     * @param ext  the extension data to associate
     * @return the previously associated extension data, or {@code null}
     */
    E set(T type, E ext);

    /**
     * Removes and returns the extension data associated with the given object instance.
     *
     * @param type the base object instance
     * @return the removed extension data, or {@code null} if none
     */
    E remove(T type);


    /**
     * Factory that creates and caches {@link ExtendedObject} instances keyed by
     * the (object type, extension type) pair.
     */
    class Factory<T, E> {

        private final ConcurrentReferenceHashMap<Class<T>, ConcurrentReferenceHashMap<Class<E>, ExtendedObject<T, E>>> cache = 
                new ConcurrentReferenceHashMap<>();

        /**
         * Returns or creates an {@link ExtendedObject} for the given object type and extension type pair.
         *
         * @param typeClass the class of the base object
         * @param extClass  the class of the extension data
         * @return the cached or newly created {@link ExtendedObject}
         */
        public ExtendedObject<T, E> create(Class<T> typeClass, Class<E> extClass) {
            return cache.computeIfAbsent(typeClass, type -> new ConcurrentReferenceHashMap<Class<E>, ExtendedObject<T, E>>())
                    .computeIfAbsent(extClass, ext -> new Default<T, E>());
        }
    }


    /**
     * Default {@link ExtendedObject} implementation backed by a
     * {@link io.gemini.core.concurrent.ConcurrentReferenceHashMap} with weak keys.
     */
    class Default<T, E> implements ExtendedObject<T, E> {

        private final ConcurrentReferenceHashMap<T, E> cache = new ConcurrentReferenceHashMap<>();

        /**
         * {@inheritDoc}
         */
        @Override
        public E get(T type) {
            return cache.get(type);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public E set(T type, E ext) {
            return cache.put(type, ext);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public E remove(T type) {
            return cache.remove(type);
        }
    }

}
