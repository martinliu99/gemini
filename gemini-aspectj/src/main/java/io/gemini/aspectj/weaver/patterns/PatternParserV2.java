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
import java.util.Map;

import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.IntMap;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.Shadow.Kind;
import org.aspectj.weaver.ShadowMunger;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.patterns.Bindings;
import org.aspectj.weaver.patterns.ExactTypePattern;
import org.aspectj.weaver.patterns.FastMatchInfo;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.KindedPointcut;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.SignaturePattern;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.patterns.WildTypePattern;

import io.gemini.aspectj.weaver.ReferenceTypes;
import net.bytebuddy.pool.TypePool.Resolution.NoSuchTypeException;

/**
 * Extended AspectJ {@link PatternParser} that overrides type and pointcut pattern parsing
 * to integrate with Gemini's {@link ReferenceTypes} lazy resolution mechanism.
 * <p>
 * Key overrides:
 * <ul>
 *   <li>{@link WildTypePatternV2} – wraps resolved {@link ExactTypePattern} in a
 *       {@link ReferenceTypes.Facade} to defer class loading</li>
 *   <li>{@link KindedPointcutV2} – optimizes {@code fastMatch} to avoid expensive
 *       type hierarchy traversal and handles {@link NoSuchTypeException} gracefully</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public class PatternParserV2 extends PatternParser {

    /**
     * Creates a new {@code PatternParserV2} for the given expression.
     *
     * @param expression the AspectJ pattern expression to parse
     */
    public PatternParserV2(String expression) {
        super(expression);
    }

    /**
     * Parses a single type pattern, wrapping any {@link WildTypePattern} in a
     * {@link WildTypePatternV2} that integrates with Gemini's lazy type resolution.
     *
     * {@inheritDoc}
     */
    @Override
    public TypePattern parseSingleTypePattern(boolean insideTypeParameters) {
        TypePattern typePattern = super.parseSingleTypePattern(insideTypeParameters);
        if (typePattern instanceof WildTypePattern == false)
            return typePattern;

        return new WildTypePatternV2( (WildTypePattern) typePattern);
    }

    /**
     * Parses a single pointcut, wrapping any {@link KindedPointcut} in a
     * {@link KindedPointcutV2} that optimizes fast-match and handles missing types gracefully.
     *
     * {@inheritDoc}
     */
    @Override
    public Pointcut parseSinglePointcut() {
        Pointcut pointcut = super.parseSinglePointcut();
        if (pointcut instanceof KindedPointcut == false)
            return pointcut;

        return new KindedPointcutV2( (KindedPointcut) pointcut );
    }


    /**
     * A {@link WildTypePattern} variant that wraps the resolved {@link ExactTypePattern}
     * in a {@link ReferenceTypes.Facade}, deferring actual class loading until the type's
     * delegate is first accessed. Also overrides {@code matchesExactly} to strip anonymous
     * and nested flags via {@link TopTypeFacade} before delegating to the super implementation.
     */
    static class WildTypePatternV2 extends WildTypePattern {

        /**
         * Creates a {@code WildTypePatternV2} by copying all attributes from the given
         * {@link WildTypePattern}.
         *
         * @param typePattern the source wild-type pattern to copy
         */
        public WildTypePatternV2(WildTypePattern typePattern) {
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
         * {@inheritDoc}
         */
        @Override
        public TypePattern resolveBindings(IScope scope, Bindings bindings, boolean allowBinding, boolean requireExactType) {
            TypePattern typePattern = super.resolveBindings(scope, bindings, allowBinding, requireExactType);
            if (typePattern instanceof ExactTypePattern == false)
                return typePattern;

            ExactTypePattern exactTypePattern = (ExactTypePattern) typePattern;
            if (exactTypePattern.getType() instanceof ReferenceType == false)
                return exactTypePattern;

            ReferenceType type = (ReferenceType) exactTypePattern.getType();
            return new ExactTypePattern(
                    new ReferenceTypes.Facade( type, type.getWorld() ),
                    exactTypePattern.isIncludeSubtypes(), 
                    exactTypePattern.isVarArgs(), 
                    exactTypePattern.getTypeParameters());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TypePattern parameterizeWith(Map<String,UnresolvedType> typeVariableMap, World w) {
            TypePattern typePattern = super.parameterizeWith(typeVariableMap, w);
            if (typePattern instanceof WildTypePattern == false)
                return typePattern;

            return new WildTypePatternV2( (WildTypePattern) typePattern);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean matchesExactly(ResolvedType type, ResolvedType annotatedType) {
            if (type instanceof ReferenceType == false)
                return super.matchesExactly(type, annotatedType);

            return super.matchesExactly(
                    new TopTypeFacade( (ReferenceType) type, null), 
                    new TopTypeFacade( (ReferenceType) annotatedType, null));
        }
    }


    /**
     * A {@link ReferenceTypes.Facade} that overrides {@link #isAnonymous()} and
     * {@link #isNested()} to always return {@code false}, ensuring that AspectJ's
     * {@code matchesExactly} logic treats the type as a plain top-level class.
     */
    static class TopTypeFacade extends ReferenceTypes.Facade {

        /**
         * Creates a {@code TopTypeFacade} that wraps the given reference type and
         * always reports {@code isAnonymous() == false} and {@code isNested() == false},
         * preventing AspectJ from skipping top-level type matching.
         *
         * @param referenceType the reference type to wrap
         * @param world         the AspectJ world (may be {@code null} when the type already
         *                      carries its own world reference)
         */
        public TopTypeFacade(ReferenceType referenceType, World world) {
            super(referenceType, world);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isAnonymous() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isNested() {
            return false;
        }
    }


    /**
     * An optimized {@link KindedPointcut} that short-circuits {@code fastMatch} for
     * exact declaring-type patterns (avoiding expensive supertype traversal) and
     * silently returns {@link FuzzyBoolean#NO} when a {@link NoSuchTypeException} is
     * thrown during type-argument or supertype lookup.
     */
    static class KindedPointcutV2 extends KindedPointcut {

        /**
         * Creates a {@code KindedPointcutV2} by copying the kind and signature from
         * the given {@link KindedPointcut}.
         *
         * @param kindedPointcut the source pointcut to copy
         */
        public KindedPointcutV2(KindedPointcut kindedPointcut) {
            super(kindedPointcut.getKind(), kindedPointcut.getSignature());
        }

        public KindedPointcutV2(Kind kind, SignaturePattern signature, ShadowMunger munger) {
            super(kind, signature, munger);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public FuzzyBoolean fastMatch(FastMatchInfo info) {
            Kind infoKind = info.getKind();
            Kind kind = this.getKind();
            if (infoKind != null && infoKind.equals(kind))
                return FuzzyBoolean.NO;

            try {
                if (this.getSignature().isExactDeclaringTypePattern()) {
                    ExactTypePattern typePattern = (ExactTypePattern) this.getSignature().getDeclaringType();

                    // TODO: traverse type hierarchy is expensive to load supper class and interfaces
                    boolean traverseTypeHierarchy = false;
                    if (typePattern.isIncludeSubtypes() == true) {
                        traverseTypeHierarchy = false;
                    }

                    if ( Shadow.ConstructorExecution.equals(kind) || Shadow.StaticInitialization.equals(kind)
                            || (Shadow.MethodExecution.equals(kind) && traverseTypeHierarchy == false) ) {
                        return typePattern.matchesStatically(info.getType()) ? FuzzyBoolean.MAYBE: FuzzyBoolean.NO;
                    }
                } else  if (this.getSignature().getDeclaringType() instanceof WildTypePattern) {
                    final WildTypePattern pattern = (WildTypePattern) this.getSignature().getDeclaringType();
                    final ResolvedType type = info.getType();
                    return pattern.matches(type, TypePattern.STATIC);
                }

                return super.fastMatch(info);
            } catch (NoSuchTypeException e) {
                // ignore NoSuchTypeException when looks up Type Arguments, Super Class, or Interfaces
                return FuzzyBoolean.NO;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected FuzzyBoolean matchInternal(Shadow shadow) {
            Kind shadowKind = shadow.getKind();
            Kind kind = getKind();
            if (shadowKind.equals(kind) == false) {
                return FuzzyBoolean.NO;
            }

            if (Shadow.SynchronizationLock.equals(shadowKind) && Shadow.SynchronizationLock.equals(kind)) {
                return FuzzyBoolean.YES;
            }
            if (Shadow.SynchronizationUnlock.equals(shadowKind) && Shadow.SynchronizationUnlock.equals(kind)) {
                return FuzzyBoolean.YES;
            }

            // allow bridge method
            if (!getSignature().matches(shadow.getMatchingSignature(), shadow.getIWorld(), true)) {
                return FuzzyBoolean.NO;
            }

            return FuzzyBoolean.YES;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pointcut concretize1(ResolvedType inAspect, ResolvedType declaringType, IntMap bindings) {
            Pointcut pointcut = new KindedPointcutV2(this.getKind(), this.getSignature(), bindings.getEnclosingAdvice());
            pointcut.copyLocationFrom(this);
            return pointcut;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pointcut parameterizeWith(Map<String,UnresolvedType> typeVariableMap, World w) {
            Pointcut pointcut = new KindedPointcutV2(this.getKind(), this.getSignature().parameterizeWith(typeVariableMap, w), null);
            pointcut.copyLocationFrom(this);
            return pointcut;
        }
    }
}
