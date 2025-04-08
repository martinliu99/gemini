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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public abstract class MethodMatcher extends ElementMatcher.Junction.ForNonNullValues<MethodDescription> {

    protected abstract boolean doMatch(MethodDescription methodDescription);


    public static MethodMatcher TRUE = new MethodMatcher() {
        @Override
        protected boolean doMatch(MethodDescription methodDescription) {
            return true;
        }
    };

    public static MethodMatcher FALSE = new MethodMatcher() {
        protected boolean doMatch(MethodDescription methodDescription) {
            return false;
        }
    };

    /**
     * 
     * @param methodName
     * @return
     */
    public static MethodMatcher nameEquals(final String methodName) {
        return new MethodMatcher() {
            @Override
            protected boolean doMatch(MethodDescription methodDescription) {
                return methodDescription.getName().equals(methodName);
            }
        };
    }

    /**
     * 
     * @param methodNames
     * @return
     */
    public static MethodMatcher nameAnyOf(final String... methodNames) {
        return nameAnyOf(new HashSet<>(Arrays.asList(methodNames)));
    }

    /**
     * 
     * @param methodNames
     * @return
     */
    public static MethodMatcher nameAnyOf(final Set<String> methodNames) {
        return new MethodMatcher() {
            @Override
            protected boolean doMatch(MethodDescription methodDescription) {
                return methodNames.contains(methodDescription.getName());
            }
        };
    }

    /**
     * 
     * @param prefix
     * @return
     */
    public static MethodMatcher nameStartsWith(final String prefix) {
        return new MethodMatcher() {
            @Override
            protected boolean doMatch(MethodDescription methodDescription) {
                return methodDescription.getName().startsWith(prefix);
            }
        };
    }

    /**
     * 
     * @param suffix
     * @return
     */
    public static MethodMatcher nameEndsWith(final String suffix) {
        return new MethodMatcher() {
            @Override
            protected boolean doMatch(MethodDescription methodDescription) {
                return methodDescription.getName().endsWith(suffix);
            }
        };
    }

    /**
     * 
     * @param infix
     * @return
     */
    public static MethodMatcher nameContainsWith(final String infix) {
        return new MethodMatcher() {
            @Override
            protected boolean doMatch(MethodDescription methodDescription) {
                return methodDescription.getName().contains(infix);
            }
        };
    }

    /**
     * 
     * @param pattern
     * @return
     */
    public static MethodMatcher nameMatches(final String pattern) {
        return new MethodMatcher() {
            @Override
            protected boolean doMatch(MethodDescription methodDescription) {
                return methodDescription.getName().matches(pattern);
            }
        };
    }
}
