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
package io.gemini.aspectj.weaver.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.aspectj.weaver.AnnotationAJ;
import org.aspectj.weaver.ResolvedMemberImpl;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;

import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;


/**
 * Internal AspectJ {@link org.aspectj.weaver.ResolvedMemberImpl} implementation backed by
 * a ByteBuddy {@link net.bytebuddy.description.ByteCodeElement.Member} (method or field).
 * <p>
 * Bridges ByteBuddy's member model to AspectJ's resolved member system, providing
 * annotation metadata, parameter annotation types, and annotation default values
 * without requiring actual class loading.
 * </p>
 *
 * @author   martin.liu
 */
class InternalResolvedMember extends ResolvedMemberImpl {

    private static final ResolvedType[][] NO_PARAMETER_ANNOTATIONS = new ResolvedType[][] {};

    private final BytebuddyWorld typeWorld;

    private final Member member;
    private final MethodDescription methodDescription;

    private Map<ResolvedType, AnnotationDescription> annotationTypeMap;


    /**
     * Creates an {@code InternalResolvedMember} for a field.
     *
     * @param typeWorld        the ByteBuddy world used for type conversion
     * @param fieldDescription the ByteBuddy field description to wrap
     */
    public InternalResolvedMember(BytebuddyWorld typeWorld, FieldDescription fieldDescription) {
        super(
                org.aspectj.weaver.Member.FIELD,
                typeWorld.convertType(fieldDescription.getDeclaringType()), 
                fieldDescription.getModifiers(), 
                typeWorld.convertType(fieldDescription.getType()), 
                fieldDescription.getName(), 
                new UnresolvedType[0]
        );

        this.typeWorld = typeWorld;

        this.member = fieldDescription;
        this.methodDescription = null;
    }

    /**
     * Creates an {@code InternalResolvedMember} for a method or constructor.
     *
     * @param typeWorld         the ByteBuddy world used for type conversion
     * @param methodDescription the ByteBuddy method description to wrap
     */
    public InternalResolvedMember(BytebuddyWorld typeWorld, MethodDescription methodDescription) {
        super(
                methodDescription.isConstructor() 
                    ? org.aspectj.weaver.Member.CONSTRUCTOR 
                    : (methodDescription.isTypeInitializer() 
                            ? org.aspectj.weaver.Member.STATIC_INITIALIZATION 
                            : org.aspectj.weaver.Member.METHOD),
                typeWorld.convertType(methodDescription.getDeclaringType()), 
                methodDescription.getModifiers(), 
                typeWorld.convertType(methodDescription.getReturnType()), 
                methodDescription.getName(), 
                typeWorld.convertType(methodDescription.getParameters().asTypeList()), 
                typeWorld.convertType(methodDescription.getExceptionTypes())
            );

        this.typeWorld = typeWorld;

        this.member = methodDescription;
        this.methodDescription = methodDescription;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSynthetic() {
        return member.isSynthetic();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVarargsMethod() {
        return methodDescription == null ? false : methodDescription.isVarArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBridgeMethod() {
        return methodDescription == null ? false : methodDescription.isBridge();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAnnotation(UnresolvedType ofType) {
        getAnnotationTypeMap();

        return super.hasAnnotation(ofType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAnnotations() {
        getAnnotationTypeMap();

        return this.annotationTypeMap.size() > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedType[] getAnnotationTypes() {
        getAnnotationTypeMap();

        return super.getAnnotationTypes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AnnotationAJ getAnnotationOfType(UnresolvedType ofType) {
        getAnnotationTypeMap();

        for (Entry<ResolvedType, AnnotationDescription> entry : annotationTypeMap.entrySet()) {
            ResolvedType resolvedType = entry.getKey();
            if (resolvedType.getSignature().equals(ofType.getSignature())) {
                return new InternalAnnotationAJ(resolvedType, entry.getValue());
            }
        }
        return null;
    }

    protected Map<ResolvedType, AnnotationDescription> getAnnotationTypeMap() {
        if (annotationTypeMap != null) 
            return annotationTypeMap;

        AnnotationList declaredAnnotations = member.getDeclaredAnnotations();
        ResolvedType[] annotationTypes = new ResolvedType[declaredAnnotations.size()];
        Map<ResolvedType, AnnotationDescription> annotationTypeMap = new LinkedHashMap<>(declaredAnnotations.size());
        int i = 0;
        for (AnnotationDescription annotationDescription : declaredAnnotations) {
            ResolvedType resolvedType = typeWorld.resolve(annotationDescription.getAnnotationType().getTypeName());

            annotationTypes[i++] = resolvedType;
            annotationTypeMap.put(resolvedType, annotationDescription);
        }

        this.annotationTypes = annotationTypes;
        return this.annotationTypeMap = annotationTypeMap;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getAnnotationDefaultValue() {
        if (methodDescription== null) return null;

        AnnotationValue<?, ?> defaultValue = methodDescription.getDefaultValue();
        return defaultValue == null ? null : defaultValue.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedType[][] getParameterAnnotationTypes() {
        if (parameterAnnotationTypes != null) 
            return parameterAnnotationTypes;

        if (methodDescription != null)
            return NO_PARAMETER_ANNOTATIONS;

        ParameterList<?> parameterList = methodDescription.getParameters();

        ResolvedType[][] parameterAnnotationTypes = new ResolvedType[parameterList.size()][];
        int i = 0;
        for (ParameterDescription parameterDescription : parameterList) {
            AnnotationList annotationList = parameterDescription.getDeclaredAnnotations();
            ResolvedType[] annotationTypes = new ResolvedType[annotationList.size()];
            parameterAnnotationTypes[i++] = annotationTypes;

            int j = 0;
            for (AnnotationDescription annotationDescription : annotationList) {
                annotationTypes[j++] = typeWorld.resolve(annotationDescription.getAnnotationType().getTypeName());
            }
        }
        return this.parameterAnnotationTypes = parameterAnnotationTypes;
    }
}
