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
/**
 * 
 */
package io.gemini.aspectj.weaver.patterns;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 */
public class NameMatcherParserTests {

    @Test
    public void test() {
        // starts with
        {
            String expression = "com.test..*";
            ElementMatcher<String> matcher = NameMatcherParser.INSTANCE.parseMatcher(expression);

            assertThat(matcher).isNotNull();
            assertThat(matcher.toString()).isEqualTo("startsWith(com.test.)");
        }

        {
            String expression = "com.test*";
            ElementMatcher<String> matcher = NameMatcherParser.INSTANCE.parseMatcher(expression);

            assertThat(matcher).isNotNull();
            assertThat(matcher.toString()).isEqualTo("startsWith(com.test)");
        }

        // end s with
        {
            String expression = "*test.example";
            ElementMatcher<String> matcher = NameMatcherParser.INSTANCE.parseMatcher(expression);

            assertThat(matcher).isNotNull();
            assertThat(matcher.toString()).isEqualTo("endsWith(test.example)");
        }

        {
            String expression = "*..test.example";
            ElementMatcher<String> matcher = NameMatcherParser.INSTANCE.parseMatcher(expression);

            assertThat(matcher).isNotNull();
            assertThat(matcher.toString()).isEqualTo("endsWith(test.example)");
        }

        // contain
        {
            String expression = "*..test.example..*";
            ElementMatcher<String> matcher = NameMatcherParser.INSTANCE.parseMatcher(expression);

            assertThat(matcher).isNotNull();
            assertThat(matcher.toString()).isEqualTo("contains(test.example.)");
        }

        {
            String expression = "*test.example..*";
            ElementMatcher<String> matcher = NameMatcherParser.INSTANCE.parseMatcher(expression);

            assertThat(matcher).isNotNull();
            assertThat(matcher.toString()).isEqualTo("contains(test.example.)");
        }

        // equals
        {
            String expression = "com.test.example";
            ElementMatcher<String> matcher = NameMatcherParser.INSTANCE.parseMatcher(expression);

            assertThat(matcher).isNotNull();
            assertThat(matcher.toString()).isEqualTo("equals(com.test.example)");
        }
    }
}
