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
        if(CollectionUtils.isEmpty(expressions))
            return ElementMatchers.none();

        List<ElementMatcher<? super String>> matchers = new ArrayList<>(expressions.size());
        for(String expression : expressions) {
            ElementMatcher<String> matcher = parseMatcher(expression);
            if(matcher != null)
                matchers.add(matcher);
        }

        return new ElementMatcher.Junction.Disjunction<>(matchers);
    }

    public ElementMatcher<String> parseMatcher(String expression) {
        if(StringUtils.hasText(expression) == false)
            return ElementMatchers.none();

        expression = expression.trim();
        if(isStar(expression)) {
            return ElementMatchers.any();
        }

        String fastMatchInfo = looksLikeStartsWith(expression);
        if(fastMatchInfo != null) {
            return new StringMatcher(fastMatchInfo, StringMatcher.Mode.STARTS_WITH );
        } else if( (fastMatchInfo = looksLikeExactName(expression)) != null) {
            return new StringMatcher( fastMatchInfo, StringMatcher.Mode.EQUALS_FULLY);
        } else if( (fastMatchInfo = looksLikeEndsWith(expression)) != null) {
            return new StringMatcher(fastMatchInfo, StringMatcher.Mode.ENDS_WITH);
        } else {
            return null;
        }
    }

    private boolean isStar(String expression) {
        return expression != null && expression.equals(STAR);
    }

    /**
     * Checks if the type expression looks like "com.foo..*"
     */
    private String looksLikeStartsWith(String expression) {
        if (hasSpaceAnnotationPlus(expression, 0) || expression.charAt(expression.length() - 1) != '*') {
            return null;
        }
        // now must looks like with "charsss..*" or "cha.rss..*" etc
        // note that "*" and "*..*" won't be fast matched
        int length = expression.length();
        expression = expression.endsWith("..*") && length > 3
                ? expression.substring(0, length - 2) // "charsss." or "char.rss." etc
                : expression.substring(0, length -1);

        return expression.indexOf("..") == -1 && expression.indexOf(STAR) == -1 
                ? expression : null;
    }

    /**
     * Checks if the expression looks like "*Exception"
     */
    private String looksLikeEndsWith(String expression) {
        if (expression.charAt(0) != '*') {
            return null;
        }
        if (hasSpaceAnnotationPlus(expression, 1) || hasStarDot(expression, 1)) {
            return null;
        }
        return expression.substring(1);
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

    /**
     * Determine if something in the string is going to affect our ability to optimize. Checks for: '*' '.'
     */
    private boolean hasStarDot(String string, int pos) {
        for (int i = pos, max = string.length(); i < max; i++) {
            char ch = string.charAt(i);
            if (ch == '*' || ch == '.') {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the expression looks like "com.foo.Bar" - an exact name
     */
    private String looksLikeExactName(String expression) {
        if (hasSpaceAnnotationPlus(expression, 0) || expression.contains(STAR)) {
            return null;
        }
        return expression;
    }
}
