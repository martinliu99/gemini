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

/**
 * Utility class providing assertion methods that throw {@link IllegalArgumentException}
 * when a condition is not met. Used throughout the framework to validate method arguments.
 *
 * @author   martin.liu
 */
public abstract class Assert {

    /**
     * Asserts that the given boolean expression is {@code true}.
     *
     * @param predicate the boolean expression to test
     * @param message   the exception message if the assertion fails
     * @throws IllegalArgumentException if {@code predicate} is {@code false}
     */
    public static void isTrue(boolean predicate, String message) {
        if (predicate != true) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that the given object is not {@code null}.
     *
     * @param object  the object to check
     * @param message the exception message if the assertion fails
     * @throws IllegalArgumentException if {@code object} is {@code null}
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that the given string contains at least one non-whitespace character.
     *
     * @param text    the string to check
     * @param message the exception message if the assertion fails
     * @throws IllegalArgumentException if {@code text} is {@code null}, empty, or blank
     */
    public static void hasText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that the given collection is not {@code null} and not empty.
     *
     * @param collection the collection to check
     * @param message    the exception message if the assertion fails
     * @throws IllegalArgumentException if {@code collection} is {@code null} or empty
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that the given array is not {@code null} and not empty.
     *
     * @param array   the array to check
     * @param message the exception message if the assertion fails
     * @throws IllegalArgumentException if {@code array} is {@code null} or empty
     */
    public static void notEmpty(Object[] array, String message) {
        if (CollectionUtils.isEmpty(array)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Asserts that the given string array is not {@code null} and not empty.
     *
     * @param elements the string array to check
     * @param message  the exception message if the assertion fails
     * @throws IllegalArgumentException if {@code elements} is {@code null} or has length 0
     */
    public static void notEmpty(String[] elements, String message) {
        if (elements == null || elements.length == 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
