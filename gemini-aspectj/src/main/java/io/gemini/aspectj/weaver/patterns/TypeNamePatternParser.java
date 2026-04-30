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
package io.gemini.aspectj.weaver.patterns;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.patterns.WildTypePattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.util.ReflectionUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.matcher.ElementMatcher;


/**
 * An AspectJ {@link PatternParser} extension that overrides single-type-pattern parsing
 * to produce {@link WildTypeNamePattern} instances.
 * <p>
 * {@link WildTypeNamePattern} uses a fast {@link net.bytebuddy.matcher.StringMatcher}-based
 * name check (via {@link NameMatcherParser}) instead of the full AspectJ type-hierarchy
 * traversal, making type-name matching significantly cheaper during pointcut evaluation.
 * </p>
 *
 * @author   martin.liu
 */
public class TypeNamePatternParser extends PatternParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypeNamePatternParser.class);

    /**
     * Creates a new {@code TypeNamePatternParser} for the given expression.
     *
     * @param expression the type name pattern expression to parse
     */
    public TypeNamePatternParser(String expression) {
        super(expression);
    }

    /**
     * Parses a single type pattern, wrapping any {@link WildTypePattern} in a
     * {@link WildTypeNamePattern} that uses a fast string-based name matcher.
     *
     * {@inheritDoc}
     */
    @Override
    public TypePattern parseSingleTypePattern(boolean insideTypeParameters) {
        TypePattern typePattern = super.parseSingleTypePattern(insideTypeParameters);
        return typePattern instanceof WildTypePattern ? new WildTypeNamePattern( (WildTypePattern) typePattern) : typePattern;
    }


    /**
     * A {@link WildTypePattern} variant that replaces the default AspectJ static-match
     * logic with a fast string-based name check built from {@link NameMatcherParser}.
     * <p>
     * Falls back to the reflectively-obtained {@code matchesExactlyByName} method handle
     * when the expression cannot be reduced to a simple string matcher.
     * </p>
     */
    static class WildTypeNamePattern extends WildTypePattern {

        private static final MethodHandle MATCHES_EXACTLY_BY_NAME_METHOD_HANDLE;

        private final ElementMatcher<String> nameMatcher;

        static {
            Class<WildTypePattern> clazz = WildTypePattern.class;
            String methodName = "matchesExactlyByName";
            MethodHandle methodHanlde = null;
            try {
                Method method = clazz.getDeclaredMethod(methodName, String.class, boolean.class, boolean.class);
                ReflectionUtils.makeAccessible(clazz, method);

                methodHanlde = MethodHandles.lookup().unreflect(method);
            } catch (Exception e) {
                LOGGER.error("Could not fetch method handle {} of class {}", methodName, clazz, e);
            }
            MATCHES_EXACTLY_BY_NAME_METHOD_HANDLE = methodHanlde;
        }


        /**
         * Creates a {@code WildTypeNamePattern} by copying all attributes from the given
         * {@link WildTypePattern} and pre-building a {@link net.bytebuddy.matcher.StringMatcher}
         * from the pattern's string representation.
         *
         * @param typePattern the source wild-type pattern to copy
         */
        public WildTypeNamePattern(WildTypePattern typePattern) {
            super(Arrays.asList(typePattern.getNamePatterns()), 
                    typePattern.isIncludeSubtypes(), 
                    typePattern.getDimensions(), 
                    typePattern.getEnd(), 
                    typePattern.isVarArgs(), 
                    typePattern.getTypeParameters(), 
                    typePattern.getUpperBound(), 
                    typePattern.getAdditionalIntefaceBounds(), 
                    typePattern.getLowerBound());

            nameMatcher = NameMatcherParser.INSTANCE.parseMatcher( this.toString() );
        }

        /**
         * Returns {@code true} if the given type's name matches this pattern.
         * Uses the pre-built string matcher when available; otherwise falls back to
         * the reflective {@code matchesExactlyByName} method handle.
         *
         * {@inheritDoc}
         */
        @Override
        public boolean matchesStatically(ResolvedType type) {
            String typeName = type.getName();

            if (nameMatcher != null)
                return nameMatcher.matches(typeName);

            try {
                return (boolean) MATCHES_EXACTLY_BY_NAME_METHOD_HANDLE.invoke(
                        this, 
                        typeName, 
                        false, 
                        false
                    );
            } catch (Throwable t) {
                Throwables.throwIfRequired(t);
                return false;
            }
        }
    }
}
