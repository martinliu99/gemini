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

import org.aspectj.weaver.UnresolvedType;

import io.gemini.aspectj.weaver.world.ByteBuddyToResolvedTypeConverter;
import io.gemini.aspectj.weaver.world.GenericSignatureInformationProvider;
import io.gemini.aspectj.weaver.world.TypeWorld;
import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeList;

/**
 * Uses Java 1.5 reflection APIs to determine generic signatures
 */
public class GenericSignatureInformationProviderImpl implements
        GenericSignatureInformationProvider {

    private final TypeWorld typeWorld;
    
    public GenericSignatureInformationProviderImpl(TypeWorld typeWorld) {
        this.typeWorld = typeWorld;
    }
    
    /* (non-Javadoc)
     * @see org.aspectj.weaver.reflect.GenericSignatureInformationProvider#getGenericParameterTypes(org.aspectj.weaver.reflect.InternalResolvedMemberImpl)
     */
    public UnresolvedType[] getGenericParameterTypes(
            InternalResolvedMember resolvedMember) {
        ByteBuddyToResolvedTypeConverter typeConverter = new ByteBuddyToResolvedTypeConverter(typeWorld);
        Member member = resolvedMember.getMember();
        if (member instanceof MethodDescription) {
            MethodDescription methodDescription = (MethodDescription) member;
            TypeList.Generic pTypes = methodDescription.getParameters().asTypeList();
            return typeConverter.fromTypes(pTypes);
        }
        return typeConverter.fromTypes();
    }

    /* (non-Javadoc)
     * @see org.aspectj.weaver.reflect.GenericSignatureInformationProvider#getGenericReturnType(org.aspectj.weaver.reflect.InternalResolvedMemberImpl)
     */
    public UnresolvedType getGenericReturnType(
            InternalResolvedMember resolvedMember) {
        ByteBuddyToResolvedTypeConverter typeConverter = new ByteBuddyToResolvedTypeConverter(typeWorld);
        Member member = resolvedMember.getMember();
        if (member instanceof FieldDescription) {
            FieldDescription fieldDescription = (FieldDescription) member;
            return typeConverter.fromType(fieldDescription.getType());
        } else if (member instanceof MethodDescription) {
            MethodDescription methodDescription = (MethodDescription) member;

            if(methodDescription.isMethod())
                return typeConverter.fromType(methodDescription.getReturnType());
            else if(methodDescription.isConstructor())
                return typeConverter.fromType(methodDescription.getDeclaringType());
            else {
                throw new IllegalStateException("unexpected member type: " + member); 
            }
        } else {
            throw new IllegalStateException("unexpected member type: " + member); 
        }
    }
    
    /* (non-Javadoc)
     * @see org.aspectj.weaver.reflect.GenericSignatureInformationProvider#isBridge()
     */
    public boolean isBridge(InternalResolvedMember resolvedMember) {
        Member member =  resolvedMember.getMember();
        if (member instanceof MethodDescription) {
            MethodDescription methodDescription = (MethodDescription) member;
            return methodDescription.isBridge();
        } else {
            return false;
        }
    }
    
    /* (non-Javadoc)
     * @see org.aspectj.weaver.reflect.GenericSignatureInformationProvider#isVarArgs()
     */
    public boolean isVarArgs(InternalResolvedMember resolvedMember) {
        Member member = resolvedMember.getMember();
        if (member instanceof MethodDescription) {
            MethodDescription methodDescription = (MethodDescription) member;
            return methodDescription.isVarArgs();
        } else {
            return false;
        }
    }
    
    /* (non-Javadoc)
     * @see org.aspectj.weaver.reflect.GenericSignatureInformationProvider#isSynthetic()
     */
    public boolean isSynthetic(InternalResolvedMember resolvedMember) {
        Member member =  resolvedMember.getMember();
        return member.isSynthetic();
    }

}
