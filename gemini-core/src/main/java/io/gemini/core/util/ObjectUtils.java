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
package io.gemini.core.util;

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

    public static String getObjectId(Object obj) {
        Assert.notNull(obj, "'obj' must not be null.");

        return obj.getClass() + "@" + getIdentityHexString(obj);
    }


    public static boolean equals(Object left, Object right) {
        if (left == right)
            return true;

        if (left == null || right == null)
            return false;

        return left.equals(right);
    }

    public static int hashCode(Object obj) {
        if (obj == null)
            return 0;

        return obj.hashCode();
    }
}
