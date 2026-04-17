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

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for common string operations used throughout the AOP framework.
 * <p>
 * Provides helpers for null/empty checks, text presence checks, string replacement,
 * and joining collections with delimiters and optional prefix/suffix.
 * </p>
 *
 * @author   martin.liu
 */
public abstract class StringUtils {

    /**
     * Returns {@code true} if the given object is {@code null} or an empty string.
     *
     * @param str the object to check
     * @return {@code true} if {@code null} or {@code ""}
     */
    public static boolean isEmpty(Object str) {
        return (str == null || "".equals(str));
    }

    /**
     * Returns {@code true} if the given string is non-null, non-empty, and contains at least
     * one non-whitespace character.
     *
     * @param str the string to check
     * @return {@code true} if the string has text content
     */
    public static boolean hasText(String str) {
        return (str != null && !str.isEmpty() && containsText(str));
    }
    
    private static boolean containsText(CharSequence str) {
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a collection of strings to a {@code String[]} array.
     *
     * @param elements the collection to convert (may be {@code null})
     * @return a string array, or an empty array if {@code elements} is {@code null}
     */
    public static String[] toStringArray(Collection<String> elements) {
        return (elements != null ? elements.toArray(new String[0]) : new String[0]);
    }

    /**
     * Replaces all occurrences of {@code oldPattern} in {@code inString} with {@code newPattern}.
     *
     * @param inString    the original string
     * @param oldPattern  the substring to replace
     * @param newPattern  the replacement string
     * @return the modified string, or the original if no occurrences are found
     */
    public static String replace(String inString, String oldPattern, String newPattern) {
        if (!hasLength(inString) || !hasLength(oldPattern) || newPattern == null) {
            return inString;
        }
        int index = inString.indexOf(oldPattern);
        if (index == -1) {
            // no occurrence -> can return input as-is
            return inString;
        }

        int capacity = inString.length();
        if (newPattern.length() > oldPattern.length()) {
            capacity += 16;
        }
        StringBuilder sb = new StringBuilder(capacity);

        int pos = 0;  // our position in the old string
        int patLen = oldPattern.length();
        while (index >= 0) {
            sb.append(inString, pos, index);
            sb.append(newPattern);
            pos = index + patLen;
            index = inString.indexOf(oldPattern, pos);
        }

        // append any characters to the right of a match
        sb.append(inString, pos, inString.length());
        return sb.toString();
    }


    /**
     * Returns {@code true} if the given string is non-null and has length greater than 0.
     *
     * @param str the string to check
     * @return {@code true} if non-null and non-empty
     */
    public static boolean hasLength(CharSequence str) {
        return (str != null && str.length() > 0);
    }


    /**
     * Joins the elements of the given collection into a single string with the given delimiter.
     *
     * @param elements  the collection of strings to join
     * @param delimiter the delimiter to place between elements
     * @param <S>       the string type
     * @return the joined string, or an empty string if the collection is empty
     */
    public static <S extends CharSequence> String join(
            Collection<S> elements, CharSequence delimiter) {
        if (CollectionUtils.isEmpty(elements))
            return "";

        return join(elements, delimiter, "", "");
    }

    /**
     * Joins the elements of the given collection, applying a mapper function, with the given delimiter.
     *
     * @param elements  the collection of elements to join
     * @param mapper    the function to convert each element to a string
     * @param delimiter the delimiter to place between elements
     * @param <T>       the element type
     * @param <S>       the string type
     * @return the joined string
     */
    public static <T, S extends CharSequence> String join(
            Collection<? extends T> elements, Function<T, S> mapper, CharSequence delimiter) {
        return join(elements, mapper, 
                delimiter, "", "");
    }

    /**
     * Joins the elements of the given collection with the given delimiter, prefix, and suffix.
     *
     * @param elements  the collection of strings to join
     * @param delimiter the delimiter to place between elements
     * @param prefix    the prefix to prepend
     * @param suffix    the suffix to append
     * @param <S>       the string type
     * @return the joined string
     */
    public static <S extends CharSequence> String join(
            Collection<S> elements, 
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        if (CollectionUtils.isEmpty(elements))
            return suffix.toString();

        return joinInternal(elements.stream(), 
                delimiter, prefix, suffix);
    }

    /**
     * Joins the elements of the given collection, applying a mapper function, with delimiter, prefix, and suffix.
     *
     * @param elements  the collection of elements to join
     * @param mapper    the function to convert each element to a string
     * @param delimiter the delimiter to place between elements
     * @param prefix    the prefix to prepend
     * @param suffix    the suffix to append
     * @param <T>       the element type
     * @param <S>       the string type
     * @return the joined string
     */
    public static <T, S extends CharSequence> String join(
            Collection<? extends T> elements, Function<T, S> mapper,
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        if (CollectionUtils.isEmpty(elements))
            return suffix.toString();

        return joinInternal(elements.stream().map(mapper),
                delimiter, prefix, suffix);
    }

    /**
     * Joins the elements of the given stream with the given delimiter, prefix, and suffix.
     *
     * @param elements  the stream of strings to join
     * @param delimiter the delimiter to place between elements
     * @param prefix    the prefix to prepend
     * @param suffix    the suffix to append
     * @param <S>       the string type
     * @return the joined string
     */
    public static <S extends CharSequence> String join(
            Stream<S> elements, 
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        if (CollectionUtils.isEmpty(elements))
            return suffix.toString();

        return joinInternal(elements, 
                delimiter, prefix, suffix);
    }

    private static <S extends CharSequence> String joinInternal(Stream<S> elements, 
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        Collector<CharSequence, ?, String> joining = Collectors.joining(delimiter, prefix, suffix);
        return elements
                .collect( joining );
    }

}
