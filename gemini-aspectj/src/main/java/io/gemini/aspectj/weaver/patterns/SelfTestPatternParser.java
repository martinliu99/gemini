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
import java.util.Map;

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.TypeVariableReference;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.patterns.Bindings;
import org.aspectj.weaver.patterns.ExactTypePattern;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.SignaturePattern;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.patterns.WildTypePattern;

public class SelfTestPatternParser extends PatternParser {

    private final static Method PARSE_ATOMIC_TYPE_PATTERN;

    private final World world;

    static {
        Method parseAtomicTypePattern = null;
        try {
            parseAtomicTypePattern = PatternParser.class.getDeclaredMethod("parseAtomicTypePattern", boolean.class, boolean.class);
            parseAtomicTypePattern.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        PARSE_ATOMIC_TYPE_PATTERN = parseAtomicTypePattern;
    }

    public SelfTestPatternParser(String data, World world) {
        super(data);
        this.world = world;
    }

    public TypePattern parseSingleTypePattern(boolean insideTypeParameters) {
//        return new WildTypePattern(names, includeSubtypes, dim + (isVarArgs ? 1 : 0), endPos, isVarArgs, typeParameters);
        TypePattern typePattern = super.parseSingleTypePattern(insideTypeParameters);
        return typePattern instanceof WildTypePattern ? new TypePatternWrapper( (WildTypePattern)typePattern, world ) : typePattern;
    }

    public SignaturePattern parseMethodOrConstructorSignaturePattern() {
        SignaturePattern origin = super.parseMethodOrConstructorSignaturePattern();
        return new SelfTestSignaturePattern(origin);
    }

    static class TypePatternWrapper extends WildTypePattern {

        private final World world;

        public TypePatternWrapper(WildTypePattern typePattern, World world) {
            super(Arrays.asList(typePattern.getNamePatterns()),
                    typePattern.isIncludeSubtypes(), typePattern.getDimensions(), typePattern.getEnd(), typePattern.isVarArgs(), typePattern.getTypeParameters());
            this.world = world;
        }

        @Override
        public TypePattern resolveBindings(IScope scope, Bindings bindings, boolean allowBinding, boolean requireExactType) {
            TypePattern typePattern = super.resolveBindings(scope, bindings, allowBinding, requireExactType);
            return typePattern instanceof ExactTypePattern ? new TestTypePattern( (ExactTypePattern)typePattern, world) : typePattern;
        }
    }

    static class TestTypePattern extends ExactTypePattern {

        private final World world;

        /**
         * @param type
         * @param includeSubtypes
         * @param isVarArgs
         * @param typeParams
         */
        public TestTypePattern(ExactTypePattern typePattern, World world) {
            super(typePattern.getExactType(), typePattern.isIncludeSubtypes(), typePattern.isVarArgs(), typePattern.getTypeParameters());
            this.world = world;
        }

        @Override
        protected boolean matchesExactly(ResolvedType matchType) {
            return super.matchesExactly( this.getResolvedExactType(world));
        }

        @Override
        public TypePattern parameterizeWith(Map<String,UnresolvedType> typeVariableMap, World w) {
            UnresolvedType newType = type;
            if (type.isTypeVariableReference()) {
                TypeVariableReference t = (TypeVariableReference) type;
                String key = t.getTypeVariable().getName();
                if (typeVariableMap.containsKey(key)) {
                    newType = typeVariableMap.get(key);
                }
            } else if (type.isParameterizedType()) {
                newType = w.resolve(type).parameterize(typeVariableMap);
            }
            ExactTypePattern ret = new ExactTypePattern(newType, includeSubtypes, isVarArgs, typeParameters);
            ret.setAnnotationTypePattern( annotationPattern.parameterizeWith(typeVariableMap, w) );
            ret.copyLocationFrom(this);
            return ret;
        }
    }
}
