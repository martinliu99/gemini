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
package io.gemini.core.converter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import io.gemini.core.util.OrderedProperties;

/**
 * Utility class for converting between typed maps and {@code Map<String, Object>},
 * and for converting {@link OrderedProperties} to a map.
 *
 * @author   martin.liu
 */
public abstract class Converters {

    /**
     * Converts a typed {@code Map<String, T>} to a {@code Map<String, Object>}.
     *
     * @param source the source map (may be {@code null})
     * @param <T>    the value type
     * @return a new map with the same entries, or {@code null} if {@code source} is {@code null}
     */
    public static <T> Map<String, Object> to(Map<String, T> source) {
        if (source == null)
            return null;

        Map<String, Object> dest = new LinkedHashMap<>();
        for (Entry<String, T> entry : source.entrySet()) {
            dest.put(entry.getKey(), entry.getValue());
        }
        return dest;
    }

    /**
     * Converts an {@link OrderedProperties} instance to a {@code Map<String, Object>}.
     *
     * @param source the properties to convert (may be {@code null})
     * @return a new map with the same entries, or {@code null} if {@code source} is {@code null}
     */
    public static Map<String, Object> to(OrderedProperties source) {
        if (source == null)
            return null;

        Map<String, Object> dest = new LinkedHashMap<>();
        for (String key : source.stringPropertyNames()) {
            dest.put(key, source.getProperty(key));
        }
        return dest;
    }
}
