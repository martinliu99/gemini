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

/**
 * Utility class for object identity operations.
 * <p>
 * Provides helpers to get an object's identity hash code as a hex string,
 * build a readable object ID string, and perform null-safe equality and hash code checks.
 * </p>
 *
 * @author   martin.liu
 */
public abstract class ObjectUtils {

    /**
     * Return a hex String form of an object's identity hash code.
     * @param obj the object
     * @return the object's identity code in hex notation
     */
    public static String getIdentityHexString(Object obj) {
        Assert.notNull(obj, "'obj' must not be null.");

        return Integer.toHexString(System.identityHashCode(obj));
    }

    /**
     * Returns a string of the form {@code "ClassName@hexHash"} for the given object.
     *
     * @param obj the object (must not be {@code null})
     * @return the object ID string
     */
    public static String getObjectId(Object obj) {
        Assert.notNull(obj, "'obj' must not be null.");

        return obj.getClass() + "@" + getIdentityHexString(obj);
    }


    /**
     * Returns {@code true} if the two objects are equal, handling {@code null} safely.
     *
     * @param left  the first object (may be {@code null})
     * @param right the second object (may be {@code null})
     * @return {@code true} if both are {@code null} or {@code left.equals(right)}
     */
    public static boolean equals(Object left, Object right) {
        if (left == right)
            return true;

        if (left == null || right == null)
            return false;

        return left.equals(right);
    }

    /**
     * Returns the hash code of the given object, or {@code 0} if {@code null}.
     *
     * @param obj the object (may be {@code null})
     * @return the hash code, or {@code 0}
     */
    public static int hashCode(Object obj) {
        if (obj == null)
            return 0;

        return obj.hashCode();
    }
}
