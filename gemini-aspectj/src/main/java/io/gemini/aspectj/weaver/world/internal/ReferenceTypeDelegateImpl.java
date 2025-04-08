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

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.Collections;

import org.aspectj.weaver.AjAttribute.WeaverVersionInfo;
import org.aspectj.weaver.AnnotationAJ;
import org.aspectj.weaver.AnnotationTargetKind;
import org.aspectj.weaver.ISourceContext;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ReferenceTypeDelegate;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.SourceContextImpl;
import org.aspectj.weaver.TypeVariable;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.WeaverStateInfo;
import org.aspectj.weaver.patterns.PerClause;
import org.aspectj.weaver.reflect.ReflectionBasedResolvedMemberImpl;

import io.gemini.aspectj.weaver.world.TypeWorld;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;

/**
 * @author colyer A delegate for a resolved type that uses runtime type information (java.lang.reflect) to answer questions. This
 *         class uses only Java 1.4 features to answer questions. In a Java 1.5 environment use the
 *         Java5ReflectionBasedReferenceTypeDelegate subtype.
 */
public class ReferenceTypeDelegateImpl implements ReferenceTypeDelegate {

    protected TypeDescription typeDescription = null;
    protected TypeWorld typeWorld;
    private ReferenceType resolvedType;
    private ResolvedMember[] fields = null;
    private ResolvedMember[] methods = null;
    private ResolvedType[] interfaces = null;

    public ReferenceTypeDelegateImpl(TypeDescription typeDescription, TypeWorld inWorld, ReferenceType resolvedType) {
        initialize(resolvedType, typeDescription, inWorld);
    }

    /** for reflective construction only */
    public ReferenceTypeDelegateImpl() {
    }

    public void initialize(ReferenceType aType, TypeDescription typeDescription, TypeWorld typeWorld) {
        this.typeDescription = typeDescription;
        this.resolvedType = aType;
        this.typeWorld = typeWorld;
    }

    protected TypeDescription getBaseClass() {
        return this.typeDescription;
    }

    protected TypeWorld getTypeWorld() {
        return this.typeWorld;
    }

    public ReferenceType buildGenericType() {
        throw new UnsupportedOperationException("Shouldn't be asking for generic type at 1.4 source level or lower");
    }

    public boolean isAspect() {
        // we could do better than this in Java 5 by looking at the annotations
        // on the type...
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#isAnnotationStyleAspect()
     */
    public boolean isAnnotationStyleAspect() {
        // we could do better than this in Java 5 by looking at the annotations
        // on the type...
        return false;
    }

    public boolean isInterface() {
        return this.typeDescription.isInterface();
    }

    public boolean isEnum() {
        // cant be an enum in Java 1.4 or prior
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#isAnnotationWithRuntimeRetention ()
     */
    public boolean isAnnotationWithRuntimeRetention() {
        // cant be an annotation in Java 1.4 or prior
        return false;
    }

    public boolean isAnnotation() {
        // cant be an annotation in Java 1.4 or prior
        return false;
    }

    public String getRetentionPolicy() {
        // cant be an annotation in Java 1.4 or prior
        return null;
    }

    public boolean canAnnotationTargetType() {
        return false;
    }

    public AnnotationTargetKind[] getAnnotationTargetKinds() {
        return null;
    }

    public boolean isClass() {
        return !this.typeDescription.isInterface() && !this.typeDescription.isPrimitive() && !this.typeDescription.isArray();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#isGeneric()
     */
    public boolean isGeneric() {
        // cant be generic in 1.4
        return false;
    }

    public boolean isAnonymous() {
        // this isn't in < Java 1.5 but I think we are moving beyond the need to support those levels
        return this.typeDescription.isAnonymousType();
    }

    public boolean isNested() {
        // this isn't in < Java 1.5 but I think we are moving beyond the need to support those levels
        return this.typeDescription.isMemberType();
    }

    public ResolvedType getOuterClass() {
        // this isn't in < Java 1.5 but I think we are moving beyond the need to support those levels
        return ReferenceTypeDelegateFactoryImpl.resolveTypeInWorld(
                     typeDescription.getEnclosingType(), typeWorld);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#isExposedToWeaver()
     */
    public boolean isExposedToWeaver() {
        // reflection based types are never exposed to the weaver
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#hasAnnotation(org.aspectj.weaver .UnresolvedType)
     */
    public boolean hasAnnotation(UnresolvedType ofType) {
        // in Java 1.4 we cant have an annotation
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getAnnotations()
     */
    public AnnotationAJ[] getAnnotations() {
        // no annotations in Java 1.4
        return AnnotationAJ.EMPTY_ARRAY;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getAnnotationTypes()
     */
    public ResolvedType[] getAnnotationTypes() {
        // no annotations in Java 1.4
        return new ResolvedType[0];
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getDeclaredFields()
     */
    public ResolvedMember[] getDeclaredFields() {
        if (fields == null) {
            FieldList<FieldDescription.InDefinedShape> reflectFields = this.typeDescription.getDeclaredFields();
            ResolvedMember[] rFields = new ResolvedMember[reflectFields.size()];
            for (int i = 0; i < reflectFields.size(); i++) {
                rFields[i] = ReferenceTypeDelegateFactoryImpl.createResolvedMember(reflectFields.get(i), typeWorld);
            }
            this.fields = rFields;
        }
        return fields;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getDeclaredInterfaces()
     */
    public ResolvedType[] getDeclaredInterfaces() {
        if (interfaces == null) {
            TypeList.Generic reflectInterfaces = this.typeDescription.getInterfaces();
            ResolvedType[] rInterfaces = new ResolvedType[reflectInterfaces.size()];
            for (int i = 0; i < reflectInterfaces.size(); i++) {
                rInterfaces[i] = ReferenceTypeDelegateFactoryImpl.resolveTypeInWorld(reflectInterfaces.get(i).asErasure(), typeWorld);
            }
            this.interfaces = rInterfaces;
        }
        return interfaces;
    }

    public boolean isCacheable() {
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getDeclaredMethods()
     */
    public ResolvedMember[] getDeclaredMethods() {
        if (methods == null) {
            MethodList<MethodDescription.InDefinedShape> reflectMethods = this.typeDescription.getDeclaredMethods();
            ResolvedMember[] rMethods = new ResolvedMember[reflectMethods.size()];
            for (int i = 0; i < reflectMethods.size(); i++) {
                MethodDescription.InDefinedShape method = reflectMethods.get(i);
                rMethods[i] = ReferenceTypeDelegateFactoryImpl.createResolvedMember(method, typeWorld);
            }
            
            this.methods = rMethods;
        }
        
        return methods;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getDeclaredPointcuts()
     */
    public ResolvedMember[] getDeclaredPointcuts() {
        return new ResolvedMember[0];
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getTypeVariables()
     */
    public TypeVariable[] getTypeVariables() {
        // no type variables in Java 1.4
        return new TypeVariable[0];
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getPerClause()
     */
    public PerClause getPerClause() {
        // no per clause...
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getDeclares()
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection getDeclares() {
        // no declares
        return Collections.EMPTY_SET;
    }

    /* 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getTypeMungers()
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection getTypeMungers() {
        // no type mungers
        return Collections.EMPTY_SET;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getPrivilegedAccesses()
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection getPrivilegedAccesses() {
        // no aspect members..., not used for weaving
        return Collections.EMPTY_SET;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getModifiers()
     */
    public int getModifiers() {
        return this.typeDescription.getModifiers();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getSuperclass()
     */
    public ResolvedType getSuperclass() {
        if (this.typeDescription.getSuperClass() == null) {
            if ("java.lang.Object".equals(typeDescription.getTypeName())) {
                return null;
            }
            return typeWorld.resolve(UnresolvedType.OBJECT);
        }
        return ReferenceTypeDelegateFactoryImpl.resolveTypeInWorld(this.typeDescription.getSuperClass().asErasure(), typeWorld);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getWeaverState()
     */
    public WeaverStateInfo getWeaverState() {
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getResolvedTypeX()
     */
    public ReferenceType getResolvedTypeX() {
        return this.resolvedType;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#doesNotExposeShadowMungers()
     */
    public boolean doesNotExposeShadowMungers() {
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.ReferenceTypeDelegate#getDeclaredGenericSignature()
     */
    public String getDeclaredGenericSignature() {
        // no generic sig in 1.4
        return null;
    }

    public ReflectionBasedResolvedMemberImpl createResolvedMemberFor(Member aMember) {
        return null;
    }

    public String getSourcefilename() {
        // crappy guess..
        return resolvedType.getName() + ".class";
    }

    public ISourceContext getSourceContext() {
        return SourceContextImpl.UNKNOWN_SOURCE_CONTEXT;
    }

    public boolean copySourceContext() {
        return true;
    }

    public int getCompilerVersion() {
        return WeaverVersionInfo.getCurrentWeaverMajorVersion();
    }

    public void ensureConsistent() {

    }

    public boolean isWeavable() {
        return false;
    }

    public boolean hasBeenWoven() {
        return false;
    }

//    @Override
    public boolean hasAnnotations() {
        return false;
    }
}
