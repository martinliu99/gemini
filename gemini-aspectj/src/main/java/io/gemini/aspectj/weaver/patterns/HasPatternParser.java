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

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.aspectj.weaver.Member;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.patterns.Bindings;
import org.aspectj.weaver.patterns.ExactTypePattern;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.SignaturePattern;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.patterns.WildTypePattern;


/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class HasPatternParser extends PatternParser {


    public HasPatternParser(String expression) {
        super(expression);
    }

    @Override
    public TypePattern parseSingleTypePattern(boolean insideTypeParameters) {
        TypePattern typePattern = super.parseSingleTypePattern(insideTypeParameters);
        if (typePattern instanceof WildTypePattern == false)
            return typePattern;

        return new HasTypePattern( (WildTypePattern) typePattern);
    }


    @Override
    public SignaturePattern parseFieldSignaturePattern() {
        return new HasSignaturePattern( 
                super.parseFieldSignaturePattern(), true );
    }


    @Override
    public SignaturePattern parseMethodOrConstructorSignaturePattern() {
        return new HasSignaturePattern( 
                super.parseMethodOrConstructorSignaturePattern(), false );
    }


    private static class HasTypePattern extends WildTypePattern {

        private boolean existType = false;

        public HasTypePattern(WildTypePattern typePattern) {
            super(Arrays.asList(typePattern.getNamePatterns()),
                    typePattern.isIncludeSubtypes(), 
                    typePattern.getDimensions(), 
                    typePattern.getEnd(), 
                    typePattern.isVarArgs(), 
                    typePattern.getTypeParameters(),
                    typePattern.getUpperBound(), 
                    typePattern.getAdditionalIntefaceBounds(), 
                    typePattern.getLowerBound() );
        }

        @Override
        public TypePattern resolveBindings(IScope scope, Bindings bindings, boolean allowBinding, boolean requireExactType) {
            TypePattern typePattern = super.resolveBindings(scope, bindings, allowBinding, requireExactType);
            if (typePattern instanceof ExactTypePattern == false) 
                return typePattern;

            ExactTypePattern exactTypePattern = (ExactTypePattern) typePattern;
            ResolvedType resolvedType = exactTypePattern.getResolvedExactType(scope.getWorld());
            existType = resolvedType.isMissing() == false;

            return new BooleanTypePattern(exactTypePattern, existType);
        }

        @Override
        public TypePattern parameterizeWith(Map<String,UnresolvedType> typeVariableMap, World w) {
            TypePattern typePattern = super.parameterizeWith(typeVariableMap, w);
            if (typePattern instanceof WildTypePattern == false)
                return typePattern;

            return new HasTypePattern( (WildTypePattern) typePattern);
        }

        @Override
        public boolean matchesStatically(ResolvedType type) {
            return existType;
        }
    }


    private static class BooleanTypePattern extends ExactTypePattern {

        private final boolean existType;

        /**
         * @param names
         * @param includeSubtypes
         * @param dim
         */
        public BooleanTypePattern(ExactTypePattern exactTypePattern, boolean existType) {
            super(exactTypePattern.getExactType(),
                    exactTypePattern.isIncludeSubtypes(),
                    exactTypePattern.isVarArgs(),
                    exactTypePattern.getTypeParameters());

            this.existType = existType;
        }

        @Override
        public boolean matchesStatically(ResolvedType type) {
            return existType;
        }
    }


    private class HasSignaturePattern extends SignaturePattern {

        private final boolean matchField;
        private boolean existSignature = false;


        public HasSignaturePattern(SignaturePattern signaturePattern, boolean matchField) {
            super(signaturePattern.getKind(), 
                    signaturePattern.getModifiers(), 
                    signaturePattern.getReturnType(),
                    signaturePattern.getDeclaringType(), 
                    signaturePattern.getName(), 
                    signaturePattern.getParameterTypes(),
                    signaturePattern.getThrowsPattern(), 
                    signaturePattern.getAnnotationPattern() );

            this.matchField = matchField;
        }

        @Override
        public SignaturePattern parameterizeWith(Map<String, UnresolvedType> typeVariableMap, World w) {
            return new HasSignaturePattern(
                    super.parameterizeWith(typeVariableMap, w), matchField );
        }

        @Override
        public SignaturePattern resolveBindings(IScope scope, Bindings bindings) {
            SignaturePattern signaturePattern = super.resolveBindings(scope, bindings);

            if (this.isExactDeclaringTypePattern() == false)
                return signaturePattern;

            ExactTypePattern exactTypePattern = (ExactTypePattern) this.getDeclaringType();
            ResolvedType resolvedType = exactTypePattern.getResolvedExactType(scope.getWorld());
            if (resolvedType.isMissing())
                return signaturePattern;

            // try to fetch type and validate field or method members
            for (Iterator<ResolvedMember> iter = this.matchField ? resolvedType.getFields() : resolvedType.getMethods(true, true); iter.hasNext();) {
                Member method = iter.next();
                if (super.matches(method, scope.getWorld(), true) == true)
                    existSignature = true;
            }

            return signaturePattern;
        }

        @Override
        public boolean matches(Member member, World world, boolean b) {
            return existSignature;
        }
    }
}
