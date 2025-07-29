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
package io.gemini.aspectj.weaver.world;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.weaver.AjAttribute.WeaverVersionInfo;
import org.aspectj.weaver.AnnotationAJ;
import org.aspectj.weaver.AnnotationTargetKind;
import org.aspectj.weaver.ISourceContext;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ReferenceTypeDelegate;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedPointcutDefinition;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.SourceContextImpl;
import org.aspectj.weaver.TypeVariable;
import org.aspectj.weaver.TypeVariableReferenceType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.WeaverStateInfo;
import org.aspectj.weaver.patterns.PerClause;
import org.aspectj.weaver.patterns.Pointcut;

import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatchers;


class InternalReferenceTypeDelegate implements ReferenceTypeDelegate {

    private final BytebuddyWorld typeWorld;

    private final TypeDescription typeDescription;
    private final ReferenceType resolvedTypeX;

    private AnnotationAJ[] annotationAJs = null;
    private Map<ResolvedType, AnnotationDescription> annotationTypeMap;

    private ResolvedType[] interfaces;
    private ResolvedType superclass;

    private ResolvedMember[] methods;
    private ResolvedMember[] fields;

    private TypeVariable[] typeVariables;

    private String genericSignature = null;

    private ResolvedPointcutDefinition[] pointcuts;


    public InternalReferenceTypeDelegate(BytebuddyWorld typeWorld, 
            TypeDescription typeDescription, ReferenceType resolvedTypeX) {
        this.typeWorld = typeWorld;

        this.typeDescription = typeDescription;
        this.resolvedTypeX = resolvedTypeX;
    }

    @Override
    public ReferenceType getResolvedTypeX() {
        return this.resolvedTypeX;
    }

    @Override
    public boolean isAspect() {
        return this.typeDescription.getDeclaredAnnotations().isAnnotationPresent(Aspect.class);
    }

    @Override
    public boolean isAnnotationStyleAspect() {
        return this.isAspect();
    }

    @Override
    public boolean isInterface() {
        return this.typeDescription.isInterface();
    }

    @Override
    public boolean isEnum() {
        return this.typeDescription.isEnum();
    }

    @Override
    public boolean isAnnotation() {
        return this.typeDescription.isAnnotation();
    }

    @Override
    public String getRetentionPolicy() {
        RetentionPolicy retentionPolicy = _getRetentionPolicy();
        return retentionPolicy == null ? null : retentionPolicy.name();
    }

    @Override
    public boolean isAnnotationWithRuntimeRetention() {
        if (!isAnnotation()) {
            return false;
        }

        return _getRetentionPolicy() == RetentionPolicy.RUNTIME;
    }

    private RetentionPolicy _getRetentionPolicy() {
        if (this.typeDescription.getDeclaredAnnotations().isAnnotationPresent(Retention.class) == false) 
            return null;

        AnnotationDescription annotationDescription = this.typeDescription.getDeclaredAnnotations()
                .filter(ElementMatchers.annotationType(Retention.class)).get(0);
        return annotationDescription.getRetention();
    }

    @Override
    public AnnotationAJ[] getAnnotations() {
        if (annotationAJs != null)
            return annotationAJs;

        AnnotationAJ[] annotationAJs = new AnnotationAJ[annotationTypeMap.size()];
        int i = 0;
        for (Entry<ResolvedType, AnnotationDescription> entry : getAnnotationTypeMap().entrySet()) {
            ResolvedType resolvedType = entry.getKey();
            annotationAJs[i++] = new InternalAnnotationAJ(resolvedType, entry.getValue());
        }

        return this.annotationAJs = annotationAJs;
    }

    @Override
    public ResolvedType[] getAnnotationTypes() {
        return getAnnotationTypeMap().keySet().toArray( new ResolvedType[0] );
    }

    protected Map<ResolvedType, AnnotationDescription> getAnnotationTypeMap() {
        if(annotationTypeMap != null)
            return annotationTypeMap;

        AnnotationList annotationList = typeDescription.getDeclaredAnnotations();
        Map<ResolvedType, AnnotationDescription> annotationTypeMap = new LinkedHashMap<>( annotationList.size() );
        for(AnnotationDescription annotationDescription : annotationList) {
            ResolvedType resolvedType = typeWorld.resolve(annotationDescription.getAnnotationType().getName());

            annotationTypeMap.put(resolvedType, annotationDescription);
        }

        return this.annotationTypeMap = annotationTypeMap;
    }

    @Override
    public boolean hasAnnotations() {
        return typeDescription.getDeclaredAnnotations().size() > 0;
    }

    @Override
    public boolean hasAnnotation(UnresolvedType type) {
        for (Entry<ResolvedType, AnnotationDescription> entry : getAnnotationTypeMap().entrySet()) {
            ResolvedType resolvedType = entry.getKey();
            if (resolvedType.getSignature().equals(type.getSignature())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isClass() {
        return !this.typeDescription.isInterface() && !this.typeDescription.isPrimitive() && !this.typeDescription.isArray();
    }

    @Override
    public boolean isGeneric() {
        return this.getDeclaredGenericSignature() != null;
    }

    @Override
    public boolean isAnonymous() {
        return this.typeDescription.isAnonymousType();
    }

    @Override
    public boolean isNested() {
        return this.typeDescription.isMemberType();
    }

    @Override
    public int getModifiers() {
        return this.typeDescription.getModifiers();
    }


    @Override
    public ResolvedType[] getDeclaredInterfaces() {
        if (interfaces != null) 
            return interfaces;

        TypeList.Generic genericInterfaces = this.typeDescription.getInterfaces();
        return this.interfaces = typeWorld.convertType(genericInterfaces);
    }

    @Override
    public ResolvedType getSuperclass() {
        // Superclass of object is null
        if(this.typeDescription.represents(Object.class))
            return null;

        if (superclass != null) 
            return superclass;

        TypeDescription.Generic superClass = this.typeDescription.getSuperClass();
        return this.superclass = superClass != null 
                ? typeWorld.convertType(superClass)
                : this.typeWorld.getObjectType();
    }


    @Override
    public String getDeclaredGenericSignature() {
        if (this.genericSignature == null) {
            this.genericSignature = this.typeDescription.getGenericSignature();
        }
        return genericSignature;
    }

    @Override
    public TypeVariable[] getTypeVariables() {
        if (this.typeVariables != null) 
            return this.typeVariables;

        TypeList.Generic typeVariableList = this.typeDescription.getTypeVariables();
        this.typeVariables = new TypeVariable[typeVariableList.size()];

        // basic initialization
        int i = 0;
        for (TypeDescription.Generic typeDescription : typeVariableList) {
            typeVariables[i++] = new TypeVariable(typeDescription.getTypeName());
        }

        // now fill in the details...
        for (i = 0; i < typeVariableList.size(); i++) {
            TypeVariableReferenceType typeVariableReference = ((TypeVariableReferenceType) typeWorld.convertType(typeVariableList.get(i)));
            TypeVariable tv = typeVariableReference.getTypeVariable();
            if(tv == null) {
                continue;
            }

            TypeVariable typeVariable = typeVariables[i];
            typeVariable.setSuperclass(tv.getSuperclass());
            typeVariable.setAdditionalInterfaceBounds(tv.getSuperInterfaces());
            typeVariable.setDeclaringElement(tv.getDeclaringElement());
            typeVariable.setDeclaringElementKind(tv.getDeclaringElementKind());
            typeVariable.setRank(tv.getRank());
        }

        return this.typeVariables;
    }


    @Override
    public ResolvedMember[] getDeclaredFields() {
        if (fields != null) 
            return this.fields;

        FieldList<FieldDescription.InDefinedShape> declaredFields = this.typeDescription.getDeclaredFields();
        ResolvedMember[] resolvedFields = new ResolvedMember[declaredFields.size()];
        int i = 0;
        for (FieldDescription.InDefinedShape fieldDescription : declaredFields) {
            resolvedFields[i++] = this.typeWorld.resolved(fieldDescription);
        }
        return this.fields = resolvedFields;
    }


    // TODO: advice methods
    @Override
    public ResolvedMember[] getDeclaredMethods() {
        if (methods != null) 
            return this.methods;

        MethodList<MethodDescription.InDefinedShape> declaredMethods = this.typeDescription.getDeclaredMethods();
        ResolvedMember[] resolvedMethods = new ResolvedMember[declaredMethods.size()];
        int i = 0; 
        for (MethodDescription.InDefinedShape methodDescription : declaredMethods) {
            resolvedMethods[i++] = this.typeWorld.resolved(methodDescription);
        }
        return this.methods = resolvedMethods;
    }

    @Override
    public ResolvedMember[] getDeclaredPointcuts() {
        if (pointcuts != null) {
            return this.pointcuts;
        }

        List<PointcutMethod> pointcutMethods = this.getDeclaredPointcutMethods(typeDescription);
        this.pointcuts = new ResolvedPointcutDefinition[pointcutMethods.size()];

        PointcutParser parser = PointcutParser.createPointcutParser(typeWorld);

        // phase 1, create legitimate entries in pointcuts[] before we
        // attempt to resolve *any* of the pointcuts
        // resolution can sometimes cause us to recurse, and this two stage
        // process allows us to cope with that
        for(int i = 0; i < pointcutMethods.size(); i++) {
            PointcutMethod pointcutMethod = pointcutMethods.get(i);
            pointcuts[i] = new ResolvedPointcutDefinition(
                    getResolvedTypeX(), 
                    pointcutMethod.getModifiers(), 
                    pointcutMethod.getPointcutName(), 
                    typeWorld.convertType(pointcutMethod.getParameterTypes()), null);
        }

        // phase 2, now go back round and resolve in-place all of the pointcuts
        List<Map<String, TypeDescription>> formalParameterList = new ArrayList<>(pointcutMethods.size());
        for(int i = 0; i < pointcutMethods.size(); i++) {
            PointcutMethod pointcutMethod = pointcutMethods.get(i);

            // validate parameters
            TypeDescription[] parameterTypes = pointcutMethod.getParameterTypes();
            String[] parameterNames = pointcutMethod.getArgNames();
            if (parameterNames.length != parameterTypes.length) {
                parameterNames = tryToDiscoverParameterNames(pointcutMethod);
                if (parameterNames == null || (parameterNames.length != parameterTypes.length)) {
                    throw new IllegalStateException("Required parameter names not available when parsing pointcut "
                            + pointcutMethod.getPointcutName() + " in type " + getResolvedTypeX().getName());
                }
            }

            // parse Pointcut expression
            Map<String, TypeDescription> formalParameters = new LinkedHashMap<String, TypeDescription>(parameterTypes.length);
            formalParameterList.add(formalParameters);
            for(int j = 0; j < parameterNames.length; j++) {
                formalParameters.put(parameterNames[j], parameterTypes[j]);
            }

            Pointcut pointcut = parser.parsePointcut(pointcutMethod.getPointcutExpression(), this.typeDescription, formalParameters);
            ResolvedPointcutDefinition resolvedMember = pointcuts[i];
            resolvedMember.setParameterNames(parameterNames);
            resolvedMember.setPointcut(pointcut);
        }

        // phase 3, now concretize them all
        for(int i = 0; i < pointcuts.length; i++) {
            pointcuts[i].setPointcut(parser.concretizePointcutExpression(pointcuts[i].getPointcut(), this.typeDescription, formalParameterList.get(i)));
        }

        return pointcuts;
    }

    private List<PointcutMethod> getDeclaredPointcutMethods(TypeDescription typeDescription) {
        List<PointcutMethod> pointcutMethods = new ArrayList<>();
        for (MethodDescription methodDescription : typeDescription.getDeclaredMethods()) {
            AnnotationList filter = methodDescription.getDeclaredAnnotations().filter(
                    ElementMatchers.annotationType(org.aspectj.lang.annotation.Pointcut.class));
            if(filter.size() == 0) continue;

            pointcutMethods.add(
                    new PointcutMethod(methodDescription, typeWorld.getPlaceholderHelper(), filter.get(0)) );
        }
        return pointcutMethods;
    }


    private String[] tryToDiscoverParameterNames(PointcutMethod pointcutMethod) {
        MethodDescription methodDescription = (MethodDescription) pointcutMethod.getMethodDescription();
        ParameterList<?> parameterList = methodDescription.getParameters();

        int i = 0;
        String[] ret = new String[parameterList.size()];
        for(ParameterDescription parameterDescription : parameterList) {
            ret[i++] = parameterDescription.getName();
        }
        return ret;
    }


    @Override
    public ResolvedType getOuterClass() {
         return typeWorld.resolve(typeDescription.getEnclosingType()); 
    }

    @Override
    public boolean isCacheable() {
        return true;
    }


    @Override
    public PerClause getPerClause() {
        // no per clause...
        return null;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection getDeclares() {
        // no declares
        return Collections.EMPTY_SET;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection getTypeMungers() {
        // no type mungers
        return Collections.EMPTY_SET;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection getPrivilegedAccesses() {
        // no aspect members..., not used for weaving
        return Collections.EMPTY_SET;
    }

    @Override
    public WeaverStateInfo getWeaverState() {
        return null;
    }

    @Override
    public boolean doesNotExposeShadowMungers() {
        return false;
    }

    @Override
    public String getSourcefilename() {
        // crappy guess..
        return resolvedTypeX.getName() + ".class";
    }

    @Override
    public ISourceContext getSourceContext() {
        return SourceContextImpl.UNKNOWN_SOURCE_CONTEXT;
    }

    @Override
    public boolean copySourceContext() {
        return true;
    }

    @Override
    public int getCompilerVersion() {
        return WeaverVersionInfo.getCurrentWeaverMajorVersion();
    }

    @Override
    public void ensureConsistent() {
    }

    @Override
    public boolean isWeavable() {
        return false;
    }

    @Override
    public boolean hasBeenWoven() {
        return false;
    }

    @Override
    public boolean isExposedToWeaver() {
        // reflection based types are never exposed to the weaver
        return false;
    }

    @Override
    public boolean canAnnotationTargetType() {
        return false;
    }

    @Override
    public AnnotationTargetKind[] getAnnotationTargetKinds() {
        return null;
    }


    private static class PointcutMethod {

        private final MethodDescription methodDescription;

        private final String pointcutExpression;
        private String[] argNames = new String[0];
        private TypeDescription[] parameterTypes;


        public PointcutMethod(MethodDescription methodDescription, PlaceholderHelper placeholderHelper, AnnotationDescription annotationDescription) {
            this.methodDescription = methodDescription;

            String pointcutExpression = annotationDescription.getValue("value").resolve().toString();
            if(placeholderHelper != null) {
                pointcutExpression = placeholderHelper.replace(pointcutExpression);
            }
            this.pointcutExpression = pointcutExpression;

            this.argNames = splitOnComma(annotationDescription.getValue("argNames").resolve().toString());
        }

        private String[] splitOnComma(String s) {
            StringTokenizer strTok = new StringTokenizer(s,",");
            String[] ret = new String[strTok.countTokens()];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = strTok.nextToken().trim();
            }
            return ret;
        }

        /**
         * The declared name of the pointcut.
         */
        public String getPointcutName() {
            return methodDescription.getName();
        }

        public MethodDescription getMethodDescription() {
            return methodDescription;
        }

        public int getModifiers() {
            return methodDescription.getModifiers();
        }

        /**
         * The pointcut parameter types.
         */
        public TypeDescription[] getParameterTypes() {
            if(parameterTypes != null)
                return parameterTypes;

            ParameterList<?> baseParamTypes =  methodDescription.getParameters();
            TypeDescription[] ajParamTypes = new TypeDescription[baseParamTypes.size()];
            for (int i = 0; i < ajParamTypes.length; i++) {
                ajParamTypes[i] = baseParamTypes.get(i).getType().asErasure();
            }
            return (parameterTypes = ajParamTypes);

        }

        /**
         * The pointcut parameter names. Returns an array of empty strings
         * of length getParameterTypes().length if parameter names are not
         * available at runtime.
         */
        public String[] getArgNames() {
            return argNames;
        }

        /**
         * The pointcut expression associated with this pointcut.
         */
        public String getPointcutExpression() {
            return pointcutExpression;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append(getPointcutName());
            sb.append("(");
            TypeDescription[] ptypes = getParameterTypes();
            for (int i = 0; i < ptypes.length; i++) {
                sb.append(ptypes[i].getName());
                if (this.argNames != null && this.argNames[i] != null) {
                    sb.append(" ");
                    sb.append(this.argNames[i]);
                }
                if (i+1 < ptypes.length) sb.append(",");
            }
            sb.append(") : ");
            sb.append(pointcutExpression);
            return sb.toString();
        }
    }
}
