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

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class StringUtils {

    public static boolean isEmpty(Object str) {
        return (str == null || "".equals(str));
    }


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

    public static String[] toStringArray(Collection<String> elements) {
        return (elements != null ? elements.toArray(new String[0]) : new String[0]);
    }

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


    public static boolean hasLength(CharSequence str) {
        return (str != null && str.length() > 0);
    }


    public static <S extends CharSequence> String join(Collection<S> elements, CharSequence delimiter) {
        return join(elements, delimiter, "", "");
    }

    public static <S extends CharSequence> String join(Collection<? extends S> elements, 
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        Stream<? extends S> stream = elements == null ? Stream.empty() : elements.stream();

        return join(stream, delimiter, prefix, suffix);
    }

    public static <T, S extends CharSequence> String join(Collection<? extends T> elements, 
            Function<T, S> mapper, CharSequence delimiter) {
        return join(elements, mapper, delimiter, "", "");
    }

    public static <T, S extends CharSequence> String join(Collection<? extends T> elements, 
            Function<T, S> mapper,
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        if (CollectionUtils.isEmpty(elements))
            return "";

        return join(elements.stream().map(mapper), delimiter, prefix, suffix);
    }

    protected static <S extends CharSequence> String join(Stream<S> elements, 
            CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        if (elements == null)
            return "";

        return elements
                .collect( Collectors.joining(delimiter, prefix, suffix) );
    }

}
