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
/**
 * 
 */
package io.gemini.api.aop.matcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Factory utility providing custom ByteBuddy {@link ElementMatcher} implementations
 * for matching {@link TypeDescription} by their type hierarchy (superclasses and interfaces).
 *
 * @author   martin.liu
 */
public class TypeMatchers {

    /**
     * Abstract base matcher that holds an expression object used for matching and string representation.
     * Extends {@link net.bytebuddy.matcher.ElementMatcher.Junction.ForNonNullValues} to skip null targets.
     */
    abstract static class AbstractMatcher extends ElementMatcher.Junction.ForNonNullValues<TypeDescription> {

        /** The expression describing the match criteria. */
        protected Object expression;

        /**
         * Creates a new matcher with the given expression.
         *
         * @param expression the match expression (e.g. type names or patterns)
         */
        protected AbstractMatcher(Object expression) {
            this.expression = expression;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return expression.toString();
        }
    }


    /**
     * Returns a matcher that matches types whose type hierarchy (superclasses and interfaces)
     * includes all of the specified type names.
     * <p>
     * The target type itself is excluded from the check — only its ancestors are considered.
     * </p>
     *
     * @param superTypes one or more fully qualified type names that must appear in the hierarchy
     * @return an {@link ElementMatcher.Junction} that matches types extended from all given super types
     */
    public static ElementMatcher.Junction<TypeDescription> isExtendedFrom(final String... superTypes) {
        return new AbstractMatcher(superTypes) {
            @Override
            protected boolean doMatch(TypeDescription targetType) {
                return superTypeCheck(targetType, Arrays.asList(superTypes));
            }
        };
    }

    /**
     * Checks whether the given type's hierarchy contains all of the specified super type names.
     * <p>
     * Performs a breadth-first traversal over superclasses and interfaces. The target type itself
     * is not counted — it is excluded from the match set before traversal begins.
     * </p>
     *
     * @param targetType     the type whose hierarchy is traversed
     * @param superTypeNames the collection of fully qualified type names that must all be present
     * @return {@code true} if every name in {@code superTypeNames} is found in the hierarchy
     */
    private static boolean superTypeCheck(TypeDescription targetType, Collection<String> superTypeNames) {
        final Set<String> superTypeNameSet = new HashSet<>(superTypeNames);
        if (superTypeNameSet.contains(targetType.getTypeName())) {
            return false;
        }
        final Queue<TypeDefinition> queue = new LinkedList<>();
        queue.add(targetType);
        for (TypeDefinition current = queue.poll();
             current != null && !superTypeNameSet.isEmpty();
             current = queue.poll()) {
            superTypeNameSet.remove(current.getActualName());
            final TypeList.Generic interfaces = current.getInterfaces();
            if (!interfaces.isEmpty()) {
                queue.addAll(interfaces.asErasures());
            }
            final TypeDefinition superClass = current.getSuperClass();
            if (superClass != null) {
                queue.add(superClass.asErasure());
            }
        }
        return superTypeNameSet.isEmpty();
    }
}
