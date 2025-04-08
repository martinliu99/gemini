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
package io.gemini.aspectj.weaver.world.internal;

import java.util.Set;

import org.aspectj.weaver.AnnotationAJ;
import org.aspectj.weaver.MemberKind;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedMemberImpl;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;

import io.gemini.aspectj.weaver.world.AnnotationFinder;
import io.gemini.aspectj.weaver.world.GenericSignatureInformationProvider;
import net.bytebuddy.description.ByteCodeElement.Member;

/**
 * Subtype of ResolvedMemberImpl used in reflection world. Knows how to get annotations from a java.lang.reflect.Member
 * 
 */
public class InternalResolvedMember extends ResolvedMemberImpl {

    private AnnotationFinder annotationFinder = null;
    private GenericSignatureInformationProvider gsigInfoProvider = null;
    //new Java14GenericSignatureInformationProvider();

    private Member member;

    /**
     * @param kind
     * @param declaringType
     * @param modifiers
     * @param returnType
     * @param name
     * @param parameterTypes
     */
    public InternalResolvedMember(MemberKind kind, UnresolvedType declaringType, int modifiers,
            UnresolvedType returnType, String name, UnresolvedType[] parameterTypes, Member member) {
        super(kind, declaringType, modifiers, returnType, name, parameterTypes);
        this.member = member;
    }

    /**
     * @param kind
     * @param declaringType
     * @param modifiers
     * @param returnType
     * @param name
     * @param parameterTypes
     * @param checkedExceptions
     */
    public InternalResolvedMember(MemberKind kind, UnresolvedType declaringType, int modifiers,
            UnresolvedType returnType, String name, UnresolvedType[] parameterTypes, UnresolvedType[] checkedExceptions,
            Member member) {
        super(kind, declaringType, modifiers, returnType, name, parameterTypes, checkedExceptions);
        this.member = member;
    }

    /**
     * @param kind
     * @param declaringType
     * @param modifiers
     * @param returnType
     * @param name
     * @param parameterTypes
     * @param checkedExceptions
     * @param backingGenericMember
     */
    public InternalResolvedMember(MemberKind kind, UnresolvedType declaringType, int modifiers,
            UnresolvedType returnType, String name, UnresolvedType[] parameterTypes, UnresolvedType[] checkedExceptions,
            ResolvedMember backingGenericMember, Member member) {
        super(kind, declaringType, modifiers, returnType, name, parameterTypes, checkedExceptions, backingGenericMember);
        this.member = member;
    }

    /**
     * @param kind
     * @param declaringType
     * @param modifiers
     * @param name
     * @param signature
     */
    public InternalResolvedMember(MemberKind kind, UnresolvedType declaringType, int modifiers, String name,
            String signature, Member member) {
        super(kind, declaringType, modifiers, name, signature);
        this.member = member;
    }

    public Member getMember() {
        return this.member;
    }

    // generic signature support

    public void setGenericSignatureInformationProvider(GenericSignatureInformationProvider gsigProvider) {
        this.gsigInfoProvider = gsigProvider;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ResolvedMemberImpl#getGenericParameterTypes()
     */
    @Override
    public UnresolvedType[] getGenericParameterTypes() {
        return this.gsigInfoProvider.getGenericParameterTypes(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ResolvedMemberImpl#getGenericReturnType()
     */
    @Override
    public UnresolvedType getGenericReturnType() {
        return this.gsigInfoProvider.getGenericReturnType(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ResolvedMemberImpl#isSynthetic()
     */
    @Override
    public boolean isSynthetic() {
        return this.gsigInfoProvider.isSynthetic(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ResolvedMemberImpl#isVarargsMethod()
     */
    @Override
    public boolean isVarargsMethod() {
        return this.gsigInfoProvider.isVarArgs(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ResolvedMemberImpl#isBridgeMethod()
     */
    @Override
    public boolean isBridgeMethod() {
        return this.gsigInfoProvider.isBridge(this);
    }

    // annotation support

    public void setAnnotationFinder(AnnotationFinder finder) {
        this.annotationFinder = finder;
    }

    @Override
    public boolean hasAnnotation(UnresolvedType ofType) {
        unpackAnnotations();
        return super.hasAnnotation(ofType);
    }

    @Override
    public boolean hasAnnotations() {
        unpackAnnotations();
        return super.hasAnnotations();
    }

    @Override
    public ResolvedType[] getAnnotationTypes() {
        unpackAnnotations();
        return super.getAnnotationTypes();
    }

    @Override
    public AnnotationAJ getAnnotationOfType(UnresolvedType ofType) {
        unpackAnnotations();
        if (annotationFinder == null || annotationTypes == null) {
            return null;
        }
        for (ResolvedType type : annotationTypes) {
            if (type.getSignature().equals(ofType.getSignature())) {
                return annotationFinder.getAnnotationOfType(ofType, member);
            }
        }
        return null;
    }

    @Override
    public String getAnnotationDefaultValue() {
        if (annotationFinder == null) {
            return null;
        }
        return annotationFinder.getAnnotationDefaultValue(member);
    }

    @Override
    public ResolvedType[][] getParameterAnnotationTypes() {
        if (parameterAnnotationTypes == null && annotationFinder != null) {
            parameterAnnotationTypes = annotationFinder.getParameterAnnotationTypes(member);
        }
        return parameterAnnotationTypes;
    }

    private void unpackAnnotations() {
        if (annotationTypes == null && annotationFinder != null) {
            Set<?> s = annotationFinder.getAnnotations(member);
            if (s.size() == 0) {
                annotationTypes = ResolvedType.EMPTY_ARRAY;
            } else {
                annotationTypes = new ResolvedType[s.size()];
                int i = 0;
                for (Object o : s) {
                    annotationTypes[i++] = (ResolvedType) o;
                }
            }
        }
    }
}
