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
 * An AspectJ {@link PatternParser} extension that overrides type and signature pattern
 * parsing to perform static <em>existence</em> checks rather than runtime matching.
 * <p>
 * Instead of returning patterns that match at weave time, the produced patterns resolve
 * immediately during binding and return {@code true} from {@code matchesStatically} only
 * when the referenced type, field, or method actually exists in the world.
 * </p>
 *
 * @author   martin.liu
 */
public class HasPatternParser extends PatternParser {


    /**
     * Creates a new {@code HasPatternParser} for the given expression.
     *
     * @param expression the AspectJ pattern expression to parse
     */
    public HasPatternParser(String expression) {
        super(expression);
    }

    /**
     * Parses a single type pattern, wrapping any {@link WildTypePattern} in a
     * {@link HasTypePattern} that performs static existence checking.
     *
     * {@inheritDoc}
     */
    @Override
    public TypePattern parseSingleTypePattern(boolean insideTypeParameters) {
        TypePattern typePattern = super.parseSingleTypePattern(insideTypeParameters);
        if (typePattern instanceof WildTypePattern == false)
            return typePattern;

        return new HasTypePattern( (WildTypePattern) typePattern);
    }


    /**
     * Parses a field signature pattern, wrapping it in a {@link HasSignaturePattern}
     * that checks for the existence of a matching field.
     *
     * {@inheritDoc}
     */
    @Override
    public SignaturePattern parseFieldSignaturePattern() {
        return new HasSignaturePattern( 
                super.parseFieldSignaturePattern(), true );
    }


    /**
     * Parses a method or constructor signature pattern, wrapping it in a
     * {@link HasSignaturePattern} that checks for the existence of a matching method or constructor.
     *
     * {@inheritDoc}
     */
    @Override
    public SignaturePattern parseMethodOrConstructorSignaturePattern() {
        return new HasSignaturePattern( 
                super.parseMethodOrConstructorSignaturePattern(), false );
    }


    /**
     * A {@link WildTypePattern} that resolves immediately during binding and records
     * whether the named type actually exists in the world. {@link #matchesStatically}
     * returns the cached existence flag rather than performing a runtime type check.
     */
    private static class HasTypePattern extends WildTypePattern {

        private boolean existType = false;

        /**
         * Creates a {@code HasTypePattern} by copying all attributes from the given
         * {@link WildTypePattern}.
         *
         * @param typePattern the source wild-type pattern to copy
         */
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

        /**
         * Resolves the pattern against the given scope. If the result is an
         * {@link ExactTypePattern}, checks whether the type exists in the world and
         * returns a {@link BooleanTypePattern} carrying that result.
         *
         * {@inheritDoc}
         */
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

        /**
         * Re-wraps the parameterized result in a new {@code HasTypePattern} so that
         * existence-check semantics are preserved after generic substitution.
         *
         * {@inheritDoc}
         */
        @Override
        public TypePattern parameterizeWith(Map<String,UnresolvedType> typeVariableMap, World w) {
            TypePattern typePattern = super.parameterizeWith(typeVariableMap, w);
            if (typePattern instanceof WildTypePattern == false)
                return typePattern;

            return new HasTypePattern( (WildTypePattern) typePattern);
        }

        /**
         * Returns {@code true} if the type named by this pattern was found in the world
         * during {@link #resolveBindings}.
         *
         * {@inheritDoc}
         */
        @Override
        public boolean matchesStatically(ResolvedType type) {
            return existType;
        }
    }


    /**
     * An {@link ExactTypePattern} whose {@link #matchesStatically} result is fixed at
     * construction time to the boolean value determined during binding resolution.
     * This avoids any further type-system lookup at match time.
     */
    private static class BooleanTypePattern extends ExactTypePattern {

        private final boolean existType;

        /**
         * Creates a {@code BooleanTypePattern} that copies the exact type, subtype flag,
         * varargs flag, and type parameters from the given pattern, and fixes the static
         * match result to {@code existType}.
         *
         * @param exactTypePattern the resolved exact type pattern to copy attributes from
         * @param existType        {@code true} if the type was found in the world
         */
        public BooleanTypePattern(ExactTypePattern exactTypePattern, boolean existType) {
            super(exactTypePattern.getExactType(),
                    exactTypePattern.isIncludeSubtypes(),
                    exactTypePattern.isVarArgs(),
                    exactTypePattern.getTypeParameters());

            this.existType = existType;
        }

        /**
         * Returns the pre-computed existence flag regardless of the supplied type.
         *
         * {@inheritDoc}
         */
        @Override
        public boolean matchesStatically(ResolvedType type) {
            return existType;
        }
    }


    /**
     * A {@link SignaturePattern} that resolves immediately during binding and records
     * whether a matching field or method actually exists on the declaring type.
     * {@link #matches} returns the cached existence flag rather than re-evaluating
     * the pattern at weave time.
     */
    private class HasSignaturePattern extends SignaturePattern {

        private final boolean matchField;
        private boolean existSignature = false;


        /**
         * Creates a {@code HasSignaturePattern} by copying all attributes from the given
         * {@link SignaturePattern}.
         *
         * @param signaturePattern the source signature pattern to copy
         * @param matchField       {@code true} to check fields; {@code false} to check methods/constructors
         */
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

        /**
         * Re-wraps the parameterized result in a new {@code HasSignaturePattern} so that
         * existence-check semantics are preserved after generic substitution.
         *
         * {@inheritDoc}
         */
        @Override
        public SignaturePattern parameterizeWith(Map<String, UnresolvedType> typeVariableMap, World w) {
            return new HasSignaturePattern(
                    super.parameterizeWith(typeVariableMap, w), matchField );
        }

        /**
         * Resolves the pattern against the given scope. If the declaring type is exact and
         * present in the world, iterates its fields or methods to determine whether a
         * matching member exists, caching the result in {@code existSignature}.
         *
         * {@inheritDoc}
         */
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

        /**
         * Returns the pre-computed existence flag regardless of the supplied member.
         *
         * {@inheritDoc}
         */
        @Override
        public boolean matches(Member member, World world, boolean b) {
            return existSignature;
        }
    }
}
