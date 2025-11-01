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
package io.gemini.aspectj.weaver.patterns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.matcher.StringMatcher;


/**
 * 
 * refer to {@code org.aspectj.weaver.loadtime.ClassLoaderWeavingAdaptor}
 *
 * @author   martin.liu
 * @since	 1.0
 */
enum NameMatcherParser {

    INSTANCE;


    private final String STAR = "*";

    public ElementMatcher<String> parseMatcher(Collection<String> expressions) {
        if (CollectionUtils.isEmpty(expressions))
            return ElementMatchers.none();

        List<ElementMatcher<? super String>> matchers = new ArrayList<>(expressions.size());
        for (String expression : expressions) {
            ElementMatcher<String> matcher = parseMatcher(expression);
            if (matcher != null)
                matchers.add(matcher);
        }

        return new ElementMatcher.Junction.Disjunction<>(matchers);
    }

    public ElementMatcher<String> parseMatcher(String expression) {
        if (StringUtils.hasText(expression) == false)
            return ElementMatchers.none();

        expression = expression.trim();
        if (isStar(expression))
            return ElementMatchers.any();

        if (hasSpaceAnnotationPlus(expression, 0))
            return null;


        boolean mightStartWith = expression.endsWith(STAR);
        if (mightStartWith) {
            int length = expression.length();
            expression = expression.endsWith("..*") && length > 3
                    ? expression.substring(0, length - 2)
                    : expression.substring(0, length - 1);
        }

        boolean mightEndWith = expression.startsWith(STAR);
        if (mightEndWith) {
            int length = expression.length();
            expression = expression.startsWith("*..") && length > 3
                    ? expression.substring(3)
                    : expression.substring(1);
        }

        if (expression.indexOf("..") != -1 || expression.indexOf(STAR) != -1)
            return null;
        else if (mightStartWith && mightEndWith)
            return new StringMatcher(expression, StringMatcher.Mode.CONTAINS );
        else if (mightStartWith)
            return new StringMatcher(expression, StringMatcher.Mode.STARTS_WITH );
        else if (mightEndWith)
            return new StringMatcher(expression, StringMatcher.Mode.ENDS_WITH );
        else
            return new StringMatcher(expression, StringMatcher.Mode.EQUALS_FULLY );
    }

    private boolean isStar(String expression) {
        return expression != null && expression.equals(STAR);
    }

    /**
     * Determine if something in the string is going to affect our ability to optimize. Checks for: ' ' '@' '+'
     */
    private boolean hasSpaceAnnotationPlus(String string, int pos) {
        for (int i = pos, max = string.length(); i < max; i++) {
            char ch = string.charAt(i);
            if (ch == ' ' || ch == '@' || ch == '+') {
                return true;
            }
        }
        return false;
    }
}
