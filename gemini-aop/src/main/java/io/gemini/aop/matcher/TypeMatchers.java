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

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public final class TypeMatchers {

    abstract static class AbstractMatcher extends ElementMatcher.Junction.ForNonNullValues<TypeDescription> {

        protected Object expression;

        protected AbstractMatcher(Object pattern) {
            this.expression = pattern;
        }

        @Override
        public String toString() {
            return expression.toString();
        }
    }


    public static ElementMatcher.Junction<TypeDescription> TRUE = new AbstractMatcher(Boolean.TRUE) {
        @Override
        protected boolean doMatch(TypeDescription typeDescription) {
            return true;
        }
    };


    public static ElementMatcher.Junction<TypeDescription> FALSE = new AbstractMatcher(Boolean.FALSE) {
        protected boolean doMatch(TypeDescription typeDescription) {
            return false;
        }
    };


    public static ElementMatcher.Junction<TypeDescription> nameEquals(final String typeName) {
        return new AbstractMatcher(typeName) {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeDescription.getTypeName().equals(typeName);
            }
        };
    }


    public static ElementMatcher.Junction<TypeDescription> nameAnyOf(final String... typeNames) {
        return nameAnyOf(new HashSet<>(Arrays.asList(typeNames)));
    }


    public static ElementMatcher.Junction<TypeDescription> nameAnyOf(final Set<String> typeNames) {
        return new AbstractMatcher(typeNames) {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeNames.contains(typeDescription.getTypeName());
            }
        };
    }


    public static ElementMatcher.Junction<TypeDescription> nameStartsWith(final String prefix) {
        return new AbstractMatcher(prefix) {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeDescription.getTypeName().startsWith(prefix);
            }
        };
    }


    public static ElementMatcher.Junction<TypeDescription> nameEndsWith(final String suffix) {
        return new AbstractMatcher(suffix) {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeDescription.getTypeName().endsWith(suffix);
            }
        };
    }

    /**
     * 
     * @param infix
     * @return
     */
    public static ElementMatcher.Junction<TypeDescription> nameContains(final String infix) {
        return new AbstractMatcher(infix) {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeDescription.getTypeName().contains(infix);
            }
        };
    }


    public static ElementMatcher.Junction<TypeDescription> nameMatches(final String pattern) {
        return new AbstractMatcher(pattern) {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeDescription.getTypeName().matches(pattern);
            }
        };
    }


}
