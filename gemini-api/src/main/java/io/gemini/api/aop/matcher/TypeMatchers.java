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
 * 
 */
public class TypeMatchers {

    abstract static class AbstractMatcher extends ElementMatcher.Junction.ForNonNullValues<TypeDescription> {

        protected Object expression;

        protected AbstractMatcher(Object expression) {
            this.expression = expression;
        }

        @Override
        public String toString() {
            return expression.toString();
        }
    }


    public static ElementMatcher.Junction<TypeDescription> isExtendedFrom(final String... superTypes) {
        return new AbstractMatcher(superTypes) {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return superTypeCheck(typeDescription, Arrays.asList(superTypes));
            }
        };
    }

    private static boolean superTypeCheck(TypeDescription typeDescription, Collection<String> superTypeNames) {
        final Set<String> superTypeNameSet = new HashSet<>(superTypeNames);
        if (superTypeNameSet.contains(typeDescription.getTypeName())) {
            return false;
        }
        final Queue<TypeDefinition> queue = new LinkedList<>();
        queue.add(typeDescription);
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
