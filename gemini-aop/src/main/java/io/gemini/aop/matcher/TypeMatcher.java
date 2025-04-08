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
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public abstract class TypeMatcher extends ElementMatcher.Junction.ForNonNullValues<TypeDescription> {

    protected abstract boolean doMatch(TypeDescription typeDescription);


    public static TypeMatcher TRUE = new TypeMatcher() {
        @Override
        protected boolean doMatch(TypeDescription typeDescription) {
            return true;
        }
    };

    public static TypeMatcher FALSE = new TypeMatcher() {
        protected boolean doMatch(TypeDescription typeDescription) {
            return false;
        }
    };

    /**
     * 
     * @param typeName
     * @return
     */
    public static TypeMatcher nameEquals(final String typeName) {
        return new TypeMatcher() {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeDescription.getTypeName().equals(typeName);
            }
        };
    }

    /**
     * 
     * @param typeNames
     * @return
     */
    public static TypeMatcher nameAnyOf(final String... typeNames) {
        return nameAnyOf(new HashSet<>(Arrays.asList(typeNames)));
    }

    /**
     * 
     * @param typeNames
     * @return
     */
    public static TypeMatcher nameAnyOf(final Set<String> typeNames) {
        return new TypeMatcher() {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeNames.contains(typeDescription.getTypeName());
            }
        };
    }

    /**
     * 
     * @param prefix
     * @return
     */
    public static TypeMatcher nameStartsWith(final String prefix) {
        return new TypeMatcher() {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeDescription.getTypeName().startsWith(prefix);
            }
        };
    }

    /**
     * 
     * @param suffix
     * @return
     */
    public static TypeMatcher nameEndsWith(final String suffix) {
        return new TypeMatcher() {
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
    public static TypeMatcher nameContains(final String infix) {
        return new TypeMatcher() {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeDescription.getTypeName().contains(infix);
            }
        };
    }

    /**
     * 
     * @param pattern
     * @return
     */
    public static TypeMatcher nameMatches(final String pattern) {
        return new TypeMatcher() {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return typeDescription.getTypeName().matches(pattern);
            }
        };
    }

    public static TypeMatcher isExtendedFrom(final String... superTypes) {
        return new TypeMatcher() {
            @Override
            protected boolean doMatch(TypeDescription typeDescription) {
                return superTypeCheck(typeDescription, Arrays.asList(superTypes));
            }
        };
    }

    private static boolean superTypeCheck(TypeDescription typeDescription, Collection<String> superTypeNames) {
        final Set<String> superTypeNameSet = new HashSet<>(superTypeNames);
        if (superTypeNameSet.contains(typeDescription.asErasure().getActualName())) {
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
