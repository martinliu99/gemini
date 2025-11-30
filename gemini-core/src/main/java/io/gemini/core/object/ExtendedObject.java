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
package io.gemini.core.object;

import io.gemini.core.concurrent.ConcurrentReferenceHashMap;

/**
 * This class adds states and behaviors to given object instance.
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface ExtendedObject<T, E> {

    E get(T type);

    E set(T type, E ext);

    E remove(T type);


    class Factory<T, E> {

        private final ConcurrentReferenceHashMap<Class<T>, ConcurrentReferenceHashMap<Class<E>, ExtendedObject<T, E>>> cache = 
                new ConcurrentReferenceHashMap<>();

        public ExtendedObject<T, E> create(Class<T> typeClass, Class<E> extClass) {
            return cache.computeIfAbsent(typeClass, type -> new ConcurrentReferenceHashMap<Class<E>, ExtendedObject<T, E>>())
                    .computeIfAbsent(extClass, ext -> new Default<T, E>());
        }
    }


    class Default<T, E> implements ExtendedObject<T, E> {

        private final ConcurrentReferenceHashMap<T, E> cache = new ConcurrentReferenceHashMap<>();

        @Override
        public E get(T type) {
            return cache.get(type);
        }

        @Override
        public E set(T type, E ext) {
            return cache.put(type, ext);
        }

        @Override
        public E remove(T type) {
            return cache.remove(type);
        }
    }

}
