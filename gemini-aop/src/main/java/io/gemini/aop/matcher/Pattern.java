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
package io.gemini.aop.matcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;


/**
 * 
 *
 * @see {@code ClassLoaderWeavingAdaptor}
 * @author   martin.liu
 * @since	 1.0
 */
public class Pattern {

    public static final String STAR = "*";

    public enum Type {
        MATCH_ALL_PATTERN,
        STARTS_WITH_PATTERN,
        EQUALS_PATTERN,
        ENDS_WITH_PATTERN,
        COMPLEX_PATTERN
        ;
    }


    private final Type type;
    private final String expression;

    public Pattern(Type type, String expression) {
        Assert.notNull(type, "'type' must not be null.");
        this.type = type;

        Assert.hasText(expression, "'expression' must not be empty.");
        this.expression = expression;
    }

    public Type getType() {
        return type;
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }


    public static class Parser {

        public static List<Pattern> parsePatterns(Collection<String> patternExpressions) {
            return parsePatterns(patternExpressions, false);
        }

        public static List<Pattern> parsePatterns(Collection<String> patternExpressions, boolean isResourcePattern) {
            if(CollectionUtils.isEmpty(patternExpressions))
                return Collections.emptyList();;

            List<Pattern> patternList = new ArrayList<>(patternExpressions.size());
            for(String patternExpression : patternExpressions) {
                Pattern pattern = parsePattern(patternExpression, isResourcePattern);
                if(pattern != null)
                    patternList.add(pattern);
            }

            return patternList;
        }

        private static Pattern parsePattern(String patternExpression, boolean isResourcePattern) {
            if(StringUtils.hasText(patternExpression) == false)
                return null;

            patternExpression = patternExpression.trim();
            if(isStar(patternExpression)) {
                return new Pattern(Type.MATCH_ALL_PATTERN, STAR);
            }

            String fastMatchInfo = looksLikeStartsWith(patternExpression);
            if(fastMatchInfo != null) {
                return new Pattern(Type.STARTS_WITH_PATTERN, 
                        isResourcePattern ? ClassUtils.convertClassToResource(fastMatchInfo, false) : fastMatchInfo );
            } else if( (fastMatchInfo = looksLikeExactName(patternExpression)) != null) {
                return new Pattern(Type.EQUALS_PATTERN, 
                        isResourcePattern ? ClassUtils.convertClassToResource(fastMatchInfo, true) : fastMatchInfo );
            } else if( (fastMatchInfo = looksLikeEndsWith(patternExpression)) != null) {
                return new Pattern(Type.ENDS_WITH_PATTERN, 
                        isResourcePattern ? ClassUtils.convertClassToResource(fastMatchInfo, true) : fastMatchInfo );
            } else {
                return new Pattern(Type.COMPLEX_PATTERN, patternExpression);
            }
        }


        /**
         * Checks if the type pattern looks like "com.foo..*"
         */
        private static String looksLikeStartsWith(String expression) {
            if (hasSpaceAnnotationPlus(expression, 0) || expression.charAt(expression.length() - 1) != '*') {
                return null;
            }
            // now must looks like with "charsss..*" or "cha.rss..*" etc
            // note that "*" and "*..*" won't be fast matched
            // and that "charsss.*" will not neither
            int length = expression.length();
            if (expression.endsWith("..*") && length > 3) {
                if (expression.indexOf("..") == length - 3 // no ".." before last sequence
                        && expression.indexOf('*') == length - 1) { // no earlier '*'
                    expression = expression.substring(0, length - 2); // "charsss." or "char.rss." etc
                    length = expression.length();

                    return expression.endsWith("$.") && length > 2    // nested class under "char.rss$"
                        ? expression.substring(0, length - 1) : expression;
                }
            } else if (expression.endsWith("*")) {
                return expression.substring(0, expression.length()-1);
            }
            return null;
        }

        /**
         * Checks if the pattern looks like "*Exception"
         */
        private static String looksLikeEndsWith(String expression) {
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
        private static boolean hasSpaceAnnotationPlus(String string, int pos) {
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
        private static boolean hasStarDot(String string, int pos) {
            for (int i = pos, max = string.length(); i < max; i++) {
                char ch = string.charAt(i);
                if (ch == '*' || ch == '.') {
                    return true;
                }
            }
            return false;
        }

        /**
         * Checks if the pattern looks like "com.foo.Bar" - an exact name
         */
        private static String looksLikeExactName(String expression) {
            if (hasSpaceAnnotationPlus(expression, 0) || expression.contains(STAR)) {
                return null;
            }
            return expression;
        }

        private static boolean isStar(String expression) {
            return expression != null && expression.equals(STAR);
        }
    }
}
