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

public class KindedPointcutV2 extends KindedPointcut {

    public KindedPointcutV2(KindedPointcut kindedPointcut) {
        this(kindedPointcut.getKind(), kindedPointcut.getSignature());
    }

    public KindedPointcutV2(Kind kind, SignaturePattern signature) {
        super(kind, signature);
    }

    public KindedPointcutV2(Kind kind, SignaturePattern signature, ShadowMunger munger) {
        super(kind, signature);
    }

//    @Override
    public FuzzyBoolean fastMatch(FastMatchInfo info) {
        Kind infoKind = info.getKind();
        Kind kind = this.getKind();
        if(infoKind != null && infoKind != kind)
            return FuzzyBoolean.NO;

        if (this.getSignature().isExactDeclaringTypePattern()) {
            ExactTypePattern typePattern = (ExactTypePattern) this.getSignature().getDeclaringType();

            boolean traverseTypeHierarchy = true;
            if(typePattern.isIncludeSubtypes() == true) {
                traverseTypeHierarchy = false;
            }

            if( Shadow.ConstructorExecution == kind || Shadow.StaticInitialization == kind
                    || (Shadow.MethodExecution == kind && traverseTypeHierarchy == false) ) {
                return typePattern.matchesStatically(info.getType()) ? FuzzyBoolean.MAYBE: FuzzyBoolean.NO;
            }
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
