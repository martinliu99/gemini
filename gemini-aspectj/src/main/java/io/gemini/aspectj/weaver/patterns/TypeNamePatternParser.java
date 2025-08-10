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

import java.lang.reflect.Method;
import java.util.Arrays;

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.patterns.WildTypePattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.util.ReflectionUtils;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class TypeNamePatternParser extends PatternParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypeNamePatternParser.class);

    /**
     * @param expression
     */
    public TypeNamePatternParser(String expression) {
        super(expression);
    }

    public TypePattern parseSingleTypePattern(boolean insideTypeParameters) {
        TypePattern typePattern = super.parseSingleTypePattern(insideTypeParameters);
        return typePattern instanceof WildTypePattern ? new WildTypeNamePattern( (WildTypePattern) typePattern) : typePattern;
    }


    public static class WildTypeNamePattern extends WildTypePattern {

        private static final Method MATCHES_EXACTLY_BY_NAME;

        static {
            Method method = null;
            Class<WildTypePattern> clazz = WildTypePattern.class;
            String methodName = "matchesExactlyByName";
            try {
                method = clazz.getDeclaredMethod(methodName, String.class, boolean.class, boolean.class);
                ReflectionUtils.makeAccessible(clazz, method);
            } catch (Exception e) {
                LOGGER.error("Failed to fetch method {} of class {}", methodName, clazz, e);
            }
            MATCHES_EXACTLY_BY_NAME = method;
        }


        /**
         * @param names
         * @param includeSubtypes
         * @param dim
         * @param endPos
         * @param isVarArg
         * @param typeParams
         * @param upperBound
         * @param additionalInterfaceBounds
         * @param lowerBound
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
        }

        @Override
        protected boolean matchesExactly(ResolvedType type, ResolvedType annotatedType) {
            if(MATCHES_EXACTLY_BY_NAME == null)
                return false;

            try {
                return (Boolean) MATCHES_EXACTLY_BY_NAME.invoke(this, type.getName(), false, false);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
