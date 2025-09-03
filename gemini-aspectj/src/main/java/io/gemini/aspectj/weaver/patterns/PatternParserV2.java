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

public class PatternParserV2 extends PatternParser {

    public PatternParserV2(String expression) {
        super(expression);
    }

    public TypePattern parseSingleTypePattern(boolean insideTypeParameters) {
        TypePattern typePattern = super.parseSingleTypePattern(insideTypeParameters);
        if(typePattern instanceof WildTypePattern == false)
            return typePattern;

        return new WildTypePatternV2( (WildTypePattern) typePattern);
    }

    public Pointcut parseSinglePointcut() {
        Pointcut pointcut = super.parseSinglePointcut();
        if(pointcut instanceof KindedPointcut == false)
            return pointcut;

        return new KindedPointcutV2( (KindedPointcut) pointcut );
    }


    private static class WildTypePatternV2 extends WildTypePattern {


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

        @Override
        public TypePattern resolveBindings(IScope scope, Bindings bindings, boolean allowBinding, boolean requireExactType) {
            TypePattern typePattern = super.resolveBindings(scope, bindings, allowBinding, requireExactType);
            if(typePattern instanceof ExactTypePattern == false)
                return typePattern;

            ExactTypePattern exactTypePattern = (ExactTypePattern) typePattern;
            if(exactTypePattern.getType() instanceof ReferenceType == false)
                return exactTypePattern;

            return new ExactTypePattern(
                    new ReferenceTypes.Facade( (ReferenceType) exactTypePattern.getType(), scope.getWorld() ),
                    exactTypePattern.isIncludeSubtypes(), 
                    exactTypePattern.isVarArgs(), 
                    exactTypePattern.getTypeParameters());
        }

        @Override
        public TypePattern parameterizeWith(Map<String,UnresolvedType> typeVariableMap, World w) {
            TypePattern typePattern = super.parameterizeWith(typeVariableMap, w);
            if(typePattern instanceof WildTypePattern == false)
                return typePattern;

            return new WildTypePatternV2( (WildTypePattern) typePattern);
        }

        @Override
        protected boolean matchesExactly(ResolvedType type, ResolvedType annotatedType) {
            if(type instanceof ReferenceType == false)
                return super.matchesExactly(type, annotatedType);

            return super.matchesExactly(
                    new TopTypeFacade( (ReferenceType) type, null), 
                    new TopTypeFacade( (ReferenceType) annotatedType, null));
        }
    }


    private static class TopTypeFacade extends ReferenceTypes.Facade {

        /**
         * @param referenceType
         * @param world
         */
        public TopTypeFacade(ReferenceType referenceType, World world) {
            super(referenceType, world);
        }

        @Override
        public boolean isAnonymous() {
            return false;
        }

        @Override
        public boolean isNested() {
            return false;
        }
    }


    private static class KindedPointcutV2 extends KindedPointcut {

        public KindedPointcutV2(KindedPointcut kindedPointcut) {
            super(kindedPointcut.getKind(), kindedPointcut.getSignature());
        }

        private KindedPointcutV2(Kind kind, SignaturePattern signature, ShadowMunger munger) {
            super(kind, signature);
        }

        @Override
        public FuzzyBoolean fastMatch(FastMatchInfo info) {
            Kind infoKind = info.getKind();
            Kind kind = this.getKind();
            if(infoKind != null && infoKind != kind)
                return FuzzyBoolean.NO;

            if (this.getSignature().isExactDeclaringTypePattern()) {
                ExactTypePattern typePattern = (ExactTypePattern) this.getSignature().getDeclaringType();

                // TODO: traverse type hierarchy is expensive to load supper class and interfaces
                boolean traverseTypeHierarchy = false;
                if(typePattern.isIncludeSubtypes() == true) {
                    traverseTypeHierarchy = false;
                }

                if( Shadow.ConstructorExecution == kind || Shadow.StaticInitialization == kind
                        || (Shadow.MethodExecution == kind && traverseTypeHierarchy == false) ) {
                    return typePattern.matchesStatically(info.getType()) ? FuzzyBoolean.MAYBE: FuzzyBoolean.NO;
                }
            } else  if (this.getSignature().getDeclaringType() instanceof WildTypePattern) {
                final WildTypePattern pattern = (WildTypePattern) this.getSignature().getDeclaringType();
                final ResolvedType type = info.getType();
                return pattern.matches(type, TypePattern.STATIC);
            }

            return super.fastMatch(info);
        }

        @Override
        protected FuzzyBoolean matchInternal(Shadow shadow) {
            if (shadow.getKind() != getKind()) {
                return FuzzyBoolean.NO;
            }

            if (shadow.getKind() == Shadow.SynchronizationLock && getKind() == Shadow.SynchronizationLock) {
                return FuzzyBoolean.YES;
            }
            if (shadow.getKind() == Shadow.SynchronizationUnlock && getKind() == Shadow.SynchronizationUnlock) {
                return FuzzyBoolean.YES;
            }

            // allow bridge method
            if (!getSignature().matches(shadow.getMatchingSignature(), shadow.getIWorld(), true)) {
                return FuzzyBoolean.NO;
            }

            return FuzzyBoolean.YES;
        }

        @Override
        public Pointcut concretize1(ResolvedType inAspect, ResolvedType declaringType, IntMap bindings) {
            Pointcut pointcut = new KindedPointcutV2(this.getKind(), this.getSignature(), bindings.getEnclosingAdvice());
            pointcut.copyLocationFrom(this);
            return pointcut;
        }

        @Override
        public Pointcut parameterizeWith(Map<String,UnresolvedType> typeVariableMap, World w) {
            Pointcut pointcut = new KindedPointcutV2(this.getKind(), this.getSignature().parameterizeWith(typeVariableMap, w), null);
            pointcut.copyLocationFrom(this);
            return pointcut;
        }
    }
}
