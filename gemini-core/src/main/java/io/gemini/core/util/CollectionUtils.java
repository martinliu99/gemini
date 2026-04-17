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
package io.gemini.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for collection and array null/empty checks and merging operations.
 *
 * @author   martin.liu
 */
public abstract class CollectionUtils {

    /**
     * Returns {@code true} if the given collection is {@code null} or empty.
     *
     * @param collection the collection to check
     * @return {@code true} if {@code null} or empty
     */
    public static boolean isEmpty(Collection<?> collection) {
        return (collection == null || collection.isEmpty());
    }

    /**
     * Returns {@code true} if the given varargs array is {@code null} or has length 0.
     *
     * @param collection the array to check
     * @return {@code true} if {@code null} or empty
     */
    public static boolean isEmpty(Object... collection) {
        return (collection == null || collection.length == 0);
    }

    /**
     * Returns {@code true} if the given map is {@code null} or empty.
     *
     * @param map the map to check
     * @return {@code true} if {@code null} or empty
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return (map == null || map.isEmpty());
    }

    /**
     * Merges two lists into a new list, treating {@code null} inputs as empty lists.
     *
     * @param <T>   the element type
     * @param list1 the first list (may be {@code null})
     * @param list2 the second list (may be {@code null})
     * @return a new list containing all elements from both lists
     */
    public static <T> List<T> merge(List<? extends T> list1, List<? extends T> list2) {
        list1 = list1 == null ? Collections.emptyList() : list1;
        list2 = list2 == null ? Collections.emptyList() : list2;

        List<T> all = new ArrayList<>(list1.size() + list2.size());
        all.addAll(list1);
        all.addAll(list2);

        return all;
    }


    /**
     * Creates a {@code Map<K, Object>} from alternating key-value pairs.
     * Keys must be instances of {@code keyClass}.
     *
     * @param keyClass  the expected type of the keys
     * @param arguments alternating key-value pairs (must have even length)
     * @param <K>       the key type
     * @return a map built from the given pairs, or an empty map if {@code arguments} is empty
     * @throws IllegalArgumentException if the argument count is odd or a key has the wrong type
     */
    @SuppressWarnings("unchecked")
    public static <K> Map<K, Object> of(Class<?> keyClass, Object... arguments) {
        Assert.notNull(keyClass, "'keyClass' must not be null.");

        if (arguments == null || arguments.length == 0) 
            return Collections.emptyMap();
        Assert.isTrue(arguments.length % 2 == 0, "Odd argument count.");

        Map<K, Object> map = new LinkedHashMap<>(arguments.length / 2);
        for (int i = 0; i < arguments.length; i += 2) {
            Object key = arguments[i];
            Assert.isTrue(key != null && ClassUtils.isAssignableFrom(keyClass, key.getClass()), 
                    "key '" + key + "' must be instanceof '" + keyClass + "'.");

            Object value = arguments[i + 1];
            map.put( (K) key, value);
        }
        return map;
    }
}
