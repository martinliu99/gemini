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

import java.util.Map;

import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.IntMap;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.Shadow.Kind;
import org.aspectj.weaver.ShadowMunger;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.patterns.ExactTypePattern;
import org.aspectj.weaver.patterns.FastMatchInfo;
import org.aspectj.weaver.patterns.KindedPointcut;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.SignaturePattern;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.patterns.WildTypePattern;

public class KindedPointcutV2 extends KindedPointcut {


    public KindedPointcutV2(KindedPointcut kindedPointcut) {
        this(kindedPointcut.getKind(), kindedPointcut.getSignature());
    }

    public KindedPointcutV2(Kind kind, SignaturePattern signature) {
        super(kind, signature);
    }

    public KindedPointcutV2(Kind kind, SignaturePattern signature, ShadowMunger munger) {
        // TODO: might break in future aspectj version
        // TODO: try to fetch ShadowMunger

        super(kind, signature);
    }

    @Override
    public FuzzyBoolean fastMatch(FastMatchInfo info) {
        Kind kind = this.getKind();
        if (info.world.optimizedMatching) {

            // For now, just consider MethodExecution and Initialization
            if ((kind == Shadow.MethodExecution || kind == Shadow.ConstructorExecution
                    || kind == Shadow.Initialization || kind == Shadow.StaticInitialization) && info.getKind() == null) {
                if (this.getSignature().isExactDeclaringTypePattern()) {
                    ExactTypePattern typePattern = (ExactTypePattern) this.getSignature().getDeclaringType();
                    // Interface checks are more expensive, they could be anywhere...
//                    ResolvedType patternExactType = typePattern.getResolvedExactType(info.world);

//                    ResolvedType type=  info.world.resolve(typePattern.getExactType().getName());
                    if(typePattern.getType().equals(info.getType()) == false) {
                        return FuzzyBoolean.NO;
                    }
//                    if (patternExactType.isInterface()) {
//                        ResolvedType curr = info.getType();
//                        Iterator<ResolvedType> hierarchyWalker = curr.getHierarchy(true, true);
//                        boolean found = false;
//                        while (hierarchyWalker.hasNext()) {
//                            curr = hierarchyWalker.next();
//                            if (typePattern.matchesStatically(curr)) {
//                                found = true;
//                                break;
//                            }
//                        }
//                        if (!found) {
//                            return FuzzyBoolean.NO;
//                        }
//                    } else if (patternExactType.isClass()) {
//                        ResolvedType curr = info.getType();
//                        do {
//                            if (typePattern.matchesStatically(curr)) {
//                                break;
//                            }
//                            curr = curr.getSuperclass();
//                        } while (curr != null);
//                        if (curr == null) {
//                            return FuzzyBoolean.NO;
//                        }
//                    }
                } else if (this.getSignature().getDeclaringType() instanceof WildTypePattern) {
                    final WildTypePattern pattern = (WildTypePattern) this.getSignature().getDeclaringType();
                    final ResolvedType type = info.getType();
                    return pattern.matches(type, TypePattern.STATIC);
                }
            }
        }

        FuzzyBoolean ret = super.fastMatch(info);

        return ret;
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

        if (!getSignature().matches(shadow.getMatchingSignature(), shadow.getIWorld(), true)) {
            return FuzzyBoolean.NO;
        }

        return FuzzyBoolean.YES;
    }


    @Override
    public Pointcut concretize1(ResolvedType inAspect, ResolvedType declaringType, IntMap bindings) {
        Pointcut ret = new KindedPointcutV2(this.getKind(), this.getSignature(), bindings.getEnclosingAdvice());
        ret.copyLocationFrom(this);
        return ret;
    }

    @Override
    public Pointcut parameterizeWith(Map<String,UnresolvedType> typeVariableMap, World w) {
        Pointcut ret = new KindedPointcutV2(this.getKind(), this.getSignature().parameterizeWith(typeVariableMap, w), null);
        ret.copyLocationFrom(this);
        return ret;
    }

}
