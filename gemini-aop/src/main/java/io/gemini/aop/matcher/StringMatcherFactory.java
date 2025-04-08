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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.matcher.Pattern.Type;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class StringMatcherFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(StringMatcherFactory.class);

    private final ConcurrentMap<Object, ElementMatcher<String>> stringMatchers = new ConcurrentHashMap<>();


    public ElementMatcher<String> createStringMatcher(
            String ruleName, Collection<Pattern> patterns, 
            boolean acceptMatchAllPattern, boolean acceptComplexPattern) {
        if(this.stringMatchers.containsKey(patterns)) {
            return this.stringMatchers.get(patterns);
        }

        ElementMatcher<String> stringMatcher = doCreateStringMatcher(ruleName, patterns, acceptMatchAllPattern, acceptComplexPattern);
        this.stringMatchers.put(patterns, stringMatcher);
        return stringMatcher;
    }

    protected ElementMatcher<String> doCreateStringMatcher(
            String ruleName, Collection<Pattern> patterns,
            boolean acceptMatchAllPattern, boolean acceptComplexPattern) {
        if(CollectionUtils.isEmpty(patterns)) {
            return ElementMatchers.none();
        }

        if(patterns.stream()
                .filter( e -> 
                    Type.MATCH_ALL_PATTERN == e.getType() )
                .count() > 0) {
            if(acceptMatchAllPattern)
                return ElementMatchers.any();
            else
                LOGGER.warn("Ignored {} '{}' in rule '{}', patterns: {}.\n", 
                        Pattern.Type.MATCH_ALL_PATTERN, Pattern.STAR, ruleName, patterns);
        }

        List<Pattern> complexPatterns = patterns.stream()
                .filter( e -> 
                    Type.COMPLEX_PATTERN == e.getType() )
                .collect( Collectors.toList() );
        if(complexPatterns.size() > 0) {
            if(acceptComplexPattern == false)
                LOGGER.warn("Ignored {} '{}' in rule '{}', patterns: {}.\n", 
                        Pattern.Type.COMPLEX_PATTERN, complexPatterns, ruleName, patterns);
        }

        return new ElementMatcher<String>() {

            @Override
            public boolean matches(String target) {
                if(StringUtils.hasText(target) == false)
                    return false;

                for(Pattern pattern : patterns) {
                    Type type = pattern.getType();
                    String expression = pattern.getExpression();

                    if(Pattern.Type.STARTS_WITH_PATTERN == type && target.startsWith(expression)) {
                        return true;
                    }

                    if(Pattern.Type.EQUALS_PATTERN == type && target.equals(expression)) {
                        return true;
                    }

                    if(Pattern.Type.ENDS_WITH_PATTERN == type && target.endsWith(expression)) {
                        return true;
                    }
                }

                return false;
            }
        };
    }
}
