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
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.pool.TypeResolutionInspector;
import io.gemini.core.pool.TypeResolutionInspector.ResolutionLevel;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatchers;


/**
 * AspectJ {@link ReferenceTypeDelegate} implementation backed by a ByteBuddy {@link TypeDescription}.
 * <p>
 * Bridges ByteBuddy's type model to AspectJ's type resolution system, providing
 * type metadata (annotations, interfaces, superclass, fields, methods, pointcuts)
 * without requiring actual class loading.
 * The inner {@link TyepResolutionDetector} subclass additionally tracks which
 * type properties (superclass/interfaces) were accessed during pointcut matching.
 * </p>
 *
 * @author   martin.liu
 */
class InternalReferenceTypeDelegate implements ReferenceTypeDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalReferenceTypeDelegate.class);


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

    private List<PointcutMethod> pointcutMethods;
    private ConcurrentMap<ClassLoader, ResolvedMember[]> pointcutsPerClassLoaderMap;


    /**
     * Creates a new delegate backed by the given ByteBuddy type description.
     *
     * @param typeWorld       the ByteBuddy world used for type resolution
     * @param typeDescription the ByteBuddy type description providing type metadata
     * @param resolvedTypeX   the AspectJ reference type this delegate belongs to
     */
    public InternalReferenceTypeDelegate(BytebuddyWorld typeWorld, 
            TypeDescription typeDescription, ReferenceType resolvedTypeX) {
        this.typeWorld = typeWorld;

        this.typeDescription = typeDescription;
        this.resolvedTypeX = resolvedTypeX;
    }


    /**
     * Returns the ByteBuddy {@link TypeDescription} backing this delegate.
     *
     * @return the type description; never {@code null}
     */
    protected TypeDescription getTypeDescription() {
        return typeDescription;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReferenceType getResolvedTypeX() {
        return this.resolvedTypeX;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAspect() {
        return this.typeDescription.getDeclaredAnnotations().isAnnotationPresent(Aspect.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAnnotationStyleAspect() {
        return this.isAspect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInterface() {
        return this.typeDescription.isInterface();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnum() {
        return this.typeDescription.isEnum();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAnnotation() {
        return this.typeDescription.isAnnotation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable String getRetentionPolicy() {
        RetentionPolicy retentionPolicy = getRetentionPolicyInternal();
        return retentionPolicy == null ? null : retentionPolicy.name();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAnnotationWithRuntimeRetention() {
        if (!isAnnotation()) {
            return false;
        }

        return getRetentionPolicyInternal() == RetentionPolicy.RUNTIME;
    }

    private RetentionPolicy getRetentionPolicyInternal() {
        if (this.typeDescription.getDeclaredAnnotations().isAnnotationPresent(Retention.class) == false) 
            return null;

        AnnotationDescription annotationDescription = this.typeDescription.getDeclaredAnnotations()
                .filter(ElementMatchers.annotationType(Retention.class)).get(0);
        return annotationDescription.getRetention();
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedType[] getAnnotationTypes() {
        return getAnnotationTypeMap().keySet().toArray( new ResolvedType[0] );
    }

    /**
     * Returns a lazily-built map from each declared annotation's resolved type to its
     * {@link AnnotationDescription}, preserving declaration order.
     *
     * @return the annotation type map; never {@code null}
     */
    protected Map<ResolvedType, AnnotationDescription> getAnnotationTypeMap() {
        if (annotationTypeMap != null)
            return annotationTypeMap;

        AnnotationList annotationList = typeDescription.getDeclaredAnnotations();
        Map<ResolvedType, AnnotationDescription> annotationTypeMap = new LinkedHashMap<>( annotationList.size() );
        for (AnnotationDescription annotationDescription : annotationList) {
            ResolvedType resolvedType = typeWorld.resolve(annotationDescription.getAnnotationType().getName());

            annotationTypeMap.put(resolvedType, annotationDescription);
        }

        return this.annotationTypeMap = annotationTypeMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAnnotations() {
        return typeDescription.getDeclaredAnnotations().size() > 0;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClass() {
        return !this.typeDescription.isInterface() && !this.typeDescription.isPrimitive() && !this.typeDescription.isArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isGeneric() {
        return this.typeDescription.getTypeVariables().size() > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAnonymous() {
        return this.typeDescription.isAnonymousType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNested() {
        return this.typeDescription.isMemberType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getModifiers() {
        return this.typeDescription.getModifiers();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedType[] getDeclaredInterfaces() {
        if (interfaces != null)
            return interfaces;

        TypeList.Generic genericInterfaces = this.typeDescription.getInterfaces();
        return this.interfaces = typeWorld.convertType(genericInterfaces);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable ResolvedType getSuperclass() {
        // Superclass of object is null
        if (this.typeDescription.represents(Object.class))
            return null;

        if (superclass != null)
            return superclass;

        TypeDescription.Generic superClass = this.typeDescription.getSuperClass();
        return this.superclass = superClass != null 
                ? typeWorld.convertType(superClass)
                : this.typeWorld.getObjectType();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeclaredGenericSignature() {
        if (this.genericSignature == null) {
            this.genericSignature = this.typeDescription.getGenericSignature();
        }
        return genericSignature;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeVariable[] getTypeVariables() {
        if (this.typeVariables != null) 
            return this.typeVariables;

        TypeList.Generic typeVariableList = this.typeDescription.getTypeVariables();
        TypeVariable[] typeVariables = new TypeVariable[typeVariableList.size()];

        // basic initialization
        int i = 0;
        for (TypeDescription.Generic typeDescription : typeVariableList) {
            typeVariables[i++] = new TypeVariable(typeDescription.getTypeName());
        }

        // now fill in the details...
        for (i = 0; i < typeVariableList.size(); i++) {
            TypeVariableReferenceType typeVariableReference = ((TypeVariableReferenceType) typeWorld.convertType(typeVariableList.get(i)));
            TypeVariable tv = typeVariableReference.getTypeVariable();
            if (tv == null) {
                continue;
            }

            TypeVariable typeVariable = typeVariables[i];
            typeVariable.setSuperclass(tv.getSuperclass());
            typeVariable.setAdditionalInterfaceBounds(tv.getSuperInterfaces());
            typeVariable.setDeclaringElement(tv.getDeclaringElement());
            typeVariable.setDeclaringElementKind(tv.getDeclaringElementKind());
            typeVariable.setRank(tv.getRank());
        }

        return this.typeVariables = typeVariables;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedMember[] getDeclaredFields() {
        if (fields != null) 
            return this.fields;

        FieldList<FieldDescription.InDefinedShape> declaredFields = this.typeDescription.getDeclaredFields();
        ResolvedMember[] resolvedFields = new ResolvedMember[declaredFields.size()];
        int i = 0;
        for (FieldDescription.InDefinedShape fieldDescription : declaredFields) {
            resolvedFields[i++] = this.typeWorld.doResolve(fieldDescription);
        }
        return this.fields = resolvedFields;
    }


    // TODO: advice methods
    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedMember[] getDeclaredMethods() {
        if (methods != null) 
            return this.methods;

        MethodList<MethodDescription.InDefinedShape> declaredMethods = this.typeDescription.getDeclaredMethods();
        ResolvedMember[] resolvedMethods = new ResolvedMember[declaredMethods.size()];
        int i = 0; 
        for (MethodDescription.InDefinedShape methodDescription : declaredMethods) {
            resolvedMethods[i++] = this.typeWorld.doResolve(methodDescription);
        }
        return this.methods = resolvedMethods;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedMember[] getDeclaredPointcuts() {
        List<PointcutMethod> pointcutMethods = this.getDeclaredPointcutMethods(typeDescription);
        if (pointcutMethods.size() == 0)
            return new ResolvedPointcutDefinition[0];

        this.pointcutsPerClassLoaderMap = this.pointcutsPerClassLoaderMap == null 
                ? new ConcurrentReferenceHashMap<>() : this.pointcutsPerClassLoaderMap;

        // cache declared pointcuts per target class loader to avoid cached pointcut referring to 
        // same Resolved type, and mismatching to target type under different target class loader.
        return this.pointcutsPerClassLoaderMap.computeIfAbsent(
                ClassLoaderUtils.maskNull(ThreadContext.getContextClassLoader()), 
                key -> createDeclaredPointcuts(pointcutMethods)
        );
    }

    private List<PointcutMethod> getDeclaredPointcutMethods(TypeDescription typeDescription) {
        if (pointcutMethods != null)
            return pointcutMethods;

        List<PointcutMethod> pointcutMethods = new CopyOnWriteArrayList<>();
        for (MethodDescription methodDescription : typeDescription.getDeclaredMethods()) {
            AnnotationList filter = methodDescription.getDeclaredAnnotations().filter(
                    ElementMatchers.annotationType(org.aspectj.lang.annotation.Pointcut.class));
            if (filter.size() == 0) continue;

            pointcutMethods.add(
                    new PointcutMethod(methodDescription, typeWorld.getPlaceholderHelper(), filter.get(0)) );
        }
        return (this.pointcutMethods = pointcutMethods);
    }

    private ResolvedMember[] createDeclaredPointcuts(List<PointcutMethod> pointcutMethods) {
        List<ResolvedPointcutDefinition> pointcutDefs = new ArrayList<ResolvedPointcutDefinition>(pointcutMethods.size());
        PointcutParser pointcutParser = new PointcutParser(typeWorld);

        // phase 1, create legitimate entries in pointcuts[] before we
        // attempt to resolve *any* of the pointcuts
        // resolution can sometimes cause us to recurse, and this two stage
        // process allows us to cope with that
        for (int i = 0; i < pointcutMethods.size(); i++) {
            PointcutMethod pointcutMethod = pointcutMethods.get(i);
            pointcutDefs.add( 
                    new ResolvedPointcutDefinition(
                            getResolvedTypeX(), 
                            pointcutMethod.getModifiers(), 
                            pointcutMethod.getPointcutName(), 
                            typeWorld.convertType(pointcutMethod.getParameterTypes()), 
                            null
                    )
            );
        }

        // phase 2, now go back round and resolve in-place all of the pointcuts
        List<Map<String, ? extends TypeDefinition>> formalParameterList = new ArrayList<>(pointcutMethods.size());
        for (int i = 0; i < pointcutDefs.size(); i++) {
            ResolvedPointcutDefinition pointcutDef = pointcutDefs.get(i);
            if (pointcutDef == null)
                continue;

            // validate parameters
            PointcutMethod pointcutMethod = pointcutMethods.get(i);
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
            Map<String, TypeDefinition> formalParameters = new LinkedHashMap<>(parameterTypes.length);
            formalParameterList.add(formalParameters);
            for (int j = 0; j < parameterNames.length; j++) {
                formalParameters.put(parameterNames[j], parameterTypes[j]);
            }

            String pointcutExpression = pointcutMethod.getPointcutExpression();
            try {
                Pointcut pointcut = pointcutParser.resolvePointcutExpression(
                        pointcutExpression, this.resolvedTypeX, formalParameters);
                pointcutDef.setParameterNames(parameterNames);
                pointcutDef.setPointcut(pointcut);
            } catch (Exception e) {
                handleException(pointcutMethod, e);

                pointcutDefs.set(i, null);
            }
        }

        // phase 3, now concretize them all
        for (int i = 0; i < pointcutDefs.size(); i++) {
            ResolvedPointcutDefinition pointcutDef = pointcutDefs.get(i);
            if (pointcutDef == null)
                continue;

            PointcutMethod pointcutMethod = pointcutMethods.get(i);
            try {
                Pointcut pointcut = pointcutDef.getPointcut();
                pointcut = pointcutParser.concretizePointcutExpression(
                        pointcut, this.resolvedTypeX, formalParameterList.get(i));
                pointcutDef.setPointcut(pointcut);

                i++;
            } catch (Exception e) {
                handleException(pointcutMethod, e);

                pointcutDefs.set(i, null);
            }
        }

        // 4.remove ignored pointcutDef & pointcutMethod
        for (int i = pointcutDefs.size() - 1; i >= 0; i--) {
            if (pointcutDefs.get(i) != null)
                continue;

            pointcutDefs.remove(i);
            pointcutMethods.remove(i);
        }

        return pointcutDefs.toArray( new ResolvedPointcutDefinition[0]);
    }

    private String[] tryToDiscoverParameterNames(PointcutMethod pointcutMethod) {
        MethodDescription methodDescription = (MethodDescription) pointcutMethod.getMethodDescription();
        ParameterList<?> parameterList = methodDescription.getParameters();

        int i = 0;
        String[] ret = new String[parameterList.size()];
        for (ParameterDescription parameterDescription : parameterList) {
            ret[i++] = parameterDescription.getName();
        }
        return ret;
    }

    private void handleException(PointcutMethod pointcutMethod, Exception e) {
        if (e instanceof ExprParser.ExprParseException) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Ignored named @Pointcut with unparsable PointcutExpression. \n"
                        + "  DeclaringType: {} \n"
                        + "  @Pointcut: {} \n"
                        + "  PointcutExpression: {} \n"
                        + "  Syntax Error: {} \n", 
                        typeDescription.getTypeName(),
                        MethodUtils.getMethodSignature(pointcutMethod.getMethodDescription()),
                        pointcutMethod.getPointcutExpression(), 
                        e.getMessage()
                );
        } else if (e instanceof ExprParser.ExprLintException) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Ignored named @Pointcut with lint PointcutExpression. \n"
                        + "  DeclaringType: {} \n"
                        + "  @Pointcut: {} \n"
                        + "  PointcutExpression: {} \n"
                        + "  Lint message: {} \n", 
                        typeDescription.getTypeName(),
                        MethodUtils.getMethodSignature(pointcutMethod.getMethodDescription()),
                        pointcutMethod.getPointcutExpression(), 
                        e.getMessage()
                );
        } else if (e instanceof ExprParser.ExprUnknownException) {
            if (LOGGER.isWarnEnabled()) {
                Throwable cause = e.getCause();
                LOGGER.warn("Ignored named @Pointcut with illegal PointcutExpression. \n"
                        + "  DeclaringType: {} \n"
                        + "  @Pointcut: {} \n"
                        + "  PointcutExpression: {} \n"
                        + "  Error reason: {} \n", 
                        typeDescription.getTypeName(),
                        MethodUtils.getMethodSignature(pointcutMethod.getMethodDescription()),
                        pointcutMethod.getPointcutExpression(), 
                        cause.getMessage(), 
                        cause
                );
            }
        } else {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Ignored named @Pointcut with illegal PointcutExpression. \n"
                        + "  DeclaringType: {} \n"
                        + "  @Pointcut: {} \n"
                        + "  PointcutExpression: {} \n"
                        + "  Error reason: {} \n", 
                        typeDescription.getTypeName(),
                        MethodUtils.getMethodSignature(pointcutMethod.getMethodDescription()),
                        pointcutMethod.getPointcutExpression(), 
                        e.getMessage(), 
                        e
                );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable ResolvedType getOuterClass() {
         return typeWorld.resolve(typeDescription.getEnclosingType()); 
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCacheable() {
        return true;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable PerClause getPerClause() {
        // no per clause...
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection getDeclares() {
        // no declares
        return Collections.EMPTY_SET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection getTypeMungers() {
        // no type mungers
        return Collections.EMPTY_SET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection getPrivilegedAccesses() {
        // no aspect members..., not used for weaving
        return Collections.EMPTY_SET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable WeaverStateInfo getWeaverState() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean doesNotExposeShadowMungers() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSourcefilename() {
        // crappy guess..
        return resolvedTypeX.getName() + ".class";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ISourceContext getSourceContext() {
        return SourceContextImpl.UNKNOWN_SOURCE_CONTEXT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean copySourceContext() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCompilerVersion() {
        return WeaverVersionInfo.getCurrentWeaverMajorVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ensureConsistent() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWeavable() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasBeenWoven() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isExposedToWeaver() {
        // reflection based types are never exposed to the weaver
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canAnnotationTargetType() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable AnnotationTargetKind[] getAnnotationTargetKinds() {
        return null;
    }


    /**
     * Internal value object that captures the metadata of a method annotated with
     * {@link org.aspectj.lang.annotation.Pointcut}, including its expression, parameter
     * types, and argument names. Used during the three-phase pointcut resolution in
     * {@link #getDeclaredPointcuts()}.
     */
    private static class PointcutMethod {

        private final MethodDescription methodDescription;

        private final String pointcutExpression;
        private String[] argNames = new String[0];
        private TypeDescription[] parameterTypes;


        /**
         * Creates a {@code PointcutMethod} from the given method and its
         * {@code @Pointcut} annotation descriptor.
         * <p>
         * The pointcut expression is read from the annotation's {@code value} attribute
         * and optionally processed through {@code placeholderHelper} for property
         * substitution. Argument names are read from the annotation's {@code argNames}
         * attribute.
         * </p>
         *
         * @param methodDescription     the method carrying the {@code @Pointcut} annotation
         * @param placeholderHelper     optional helper for resolving placeholders in the
         *                              expression; may be {@code null}
         * @param annotationDescription the {@code @Pointcut} annotation descriptor
         */
        public PointcutMethod(MethodDescription methodDescription, PlaceholderHelper placeholderHelper, AnnotationDescription annotationDescription) {
            this.methodDescription = methodDescription;

            String pointcutExpression = annotationDescription.getValue("value").resolve().toString();
            if (placeholderHelper != null) {
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
         * Returns the declared name of the pointcut (i.e., the method name).
         *
         * @return the pointcut name; never {@code null}
         */
        public String getPointcutName() {
            return methodDescription.getName();
        }

        /**
         * Returns the underlying ByteBuddy {@link MethodDescription} for this pointcut method.
         *
         * @return the method description; never {@code null}
         */
        public MethodDescription getMethodDescription() {
            return methodDescription;
        }

        /**
         * Returns the modifier flags of the pointcut method.
         *
         * @return the modifiers as a bitmask (see {@link java.lang.reflect.Modifier})
         */
        public int getModifiers() {
            return methodDescription.getModifiers();
        }

        /**
         * Returns the erased parameter types of the pointcut method, lazily computed
         * from the underlying {@link MethodDescription}.
         *
         * @return an array of {@link TypeDescription} for each parameter; never {@code null}
         */
        public TypeDescription[] getParameterTypes() {
            if (parameterTypes != null)
                return parameterTypes;

            ParameterList<?> baseParamTypes =  methodDescription.getParameters();
            TypeDescription[] ajParamTypes = new TypeDescription[baseParamTypes.size()];
            for (int i = 0; i < ajParamTypes.length; i++) {
                ajParamTypes[i] = baseParamTypes.get(i).getType().asErasure();
            }
            return (parameterTypes = ajParamTypes);
        }

        /**
         * Returns the argument names declared in the {@code @Pointcut} annotation's
         * {@code argNames} attribute. Returns an empty array if none were specified.
         *
         * @return the argument name array; never {@code null}
         */
        public String[] getArgNames() {
            return argNames;
        }

        /**
         * Returns the pointcut expression string, after any placeholder substitution.
         *
         * @return the pointcut expression; never {@code null}
         */
        public String getPointcutExpression() {
            return pointcutExpression;
        }

        /**
         * Returns a human-readable representation of this pointcut method in the form
         * {@code name(Type arg, ...) : expression}.
         *
         * @return the string representation
         */
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


    /**
     * A {@link InternalReferenceTypeDelegate} variant that additionally marks the
     * underlying {@link TypeDescription} with a {@link ResolutionLevel#SUPER_TYPE_RESOLUTION}
     * flag whenever superclass or interface metadata is accessed during pointcut matching.
     * This allows callers to detect which types required deep resolution.
     */
    static class TyepResolutionDetector extends InternalReferenceTypeDelegate {

        /**
         * Creates a {@code TyepResolutionDetector} delegate that additionally marks the
         * type's resolution level when superclass or interface information is accessed.
         *
         * @param typeWorld       the ByteBuddy world
         * @param typeDescription the ByteBuddy type description
         * @param resolvedTypeX   the AspectJ reference type this delegate belongs to
         */
        public TyepResolutionDetector(BytebuddyWorld typeWorld, TypeDescription typeDescription, ReferenceType resolvedTypeX) {
            super(typeWorld, typeDescription, resolvedTypeX);
        }

        /**
         * Marks the type as requiring super-type resolution, then delegates to the parent.
         *
         * {@inheritDoc}
         */
        @Override
        public ResolvedType[] getDeclaredInterfaces() {
            this.setResolutionLevel(ResolutionLevel.SUPER_TYPE_RESOLUTION);

            return super.getDeclaredInterfaces();
        }

        /**
         * Delegates to the parent and, if a non-null superclass is returned, marks the
         * type as requiring super-type resolution.
         *
         * {@inheritDoc}
         */
        @Override
        public ResolvedType getSuperclass() {
            ResolvedType superClass = super.getSuperclass();

            if (superClass != null)
                this.setResolutionLevel(ResolutionLevel.SUPER_TYPE_RESOLUTION);

            return superClass;
        }

        private void setResolutionLevel(ResolutionLevel resolutionLevel) {
            TypeDescription typeDescription = getTypeDescription();
            if (typeDescription instanceof TypeResolutionInspector == false)
                return;

            ((TypeResolutionInspector) typeDescription).setResolutionLevel(resolutionLevel);;
        }
    }
}
