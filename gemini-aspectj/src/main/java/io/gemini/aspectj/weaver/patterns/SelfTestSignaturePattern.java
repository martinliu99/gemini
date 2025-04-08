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

import java.util.Iterator;
import java.util.Map;

import org.aspectj.weaver.Member;
import org.aspectj.weaver.MemberKind;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.patterns.AnnotationTypePattern;
import org.aspectj.weaver.patterns.ExactTypePattern;
import org.aspectj.weaver.patterns.ModifiersPattern;
import org.aspectj.weaver.patterns.NamePattern;
import org.aspectj.weaver.patterns.SignaturePattern;
import org.aspectj.weaver.patterns.ThrowsPattern;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.patterns.TypePatternList;

public class SelfTestSignaturePattern extends SignaturePattern {

    public SelfTestSignaturePattern(SignaturePattern delegatee) {
        // TODO: might break in future aspectj version
        this(delegatee.getKind(), delegatee.getModifiers(), delegatee.getReturnType(),
            delegatee.getDeclaringType(), delegatee.getName(), delegatee.getParameterTypes(),
            delegatee.getThrowsPattern(), delegatee.getAnnotationPattern());
    }

    public SelfTestSignaturePattern(MemberKind kind, ModifiersPattern modifiers, TypePattern returnType, TypePattern declaringType,
            NamePattern name, TypePatternList parameterTypes, ThrowsPattern throwsPattern, AnnotationTypePattern annotationPattern) {
        super(kind, modifiers, returnType,
                declaringType, name, parameterTypes,
                throwsPattern, annotationPattern);
    }

    @Override
    public boolean matches(Member member, World world, boolean b) {
        if(this.isExactDeclaringTypePattern() == false)
            return false;

        ExactTypePattern exact = (ExactTypePattern) this.getDeclaringType();
        ResolvedType type = exact.getResolvedExactType(world);

        // try to fetch type and validate method members
        for (Iterator<ResolvedMember> iter = type.getMethods(true, true); iter.hasNext();) {
            Member method = iter.next();
            if(super.matches(method, world, true) == true)
                return true;
        }
        return false;
    }

    @Override
    public SignaturePattern parameterizeWith(Map<String, UnresolvedType> typeVariableMap, World w) {
        SelfTestSignaturePattern ret = new SelfTestSignaturePattern(this.getKind(), this.getModifiers(), this.getReturnType().parameterizeWith(typeVariableMap, w), 
                this.getDeclaringType().parameterizeWith(typeVariableMap, w), this.getName(), this.getParameterTypes().parameterizeWith(typeVariableMap, w), 
                this.getThrowsPattern().parameterizeWith(typeVariableMap, w), this.getAnnotationPattern().parameterizeWith(typeVariableMap, w));
        ret.copyLocationFrom(this);

        return ret;
    }
}