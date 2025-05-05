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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.weaver.AnnotationAJ;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedPointcutDefinition;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.TypeVariable;
import org.aspectj.weaver.TypeVariableReferenceType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.tools.PointcutDesignatorHandler;

import io.gemini.aspectj.weaver.tools.PointcutParameter;
import io.gemini.aspectj.weaver.world.ArgNameFinder;
import io.gemini.aspectj.weaver.world.ByteBuddyToResolvedTypeConverter;
import io.gemini.aspectj.weaver.world.InternalUseOnlyPointcutParser;
import io.gemini.aspectj.weaver.world.Pointcut;
import io.gemini.aspectj.weaver.world.TypeWorld;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Provides Java 5 behaviour in reflection based delegates (overriding 1.4 behaviour from superclass where
 * appropriate)
 * 
 * @author Adrian Colyer
 * @author Andy Clement
 */
public class Java15ReferenceTypeDelegateImpl extends ReferenceTypeDelegateImpl {

//    private AjType<?> myType;
    private ResolvedType[] annotations;
    private ResolvedMember[] pointcuts;
    private ResolvedMember[] methods;
    private ResolvedMember[] fields;
    private TypeVariable[] typeVariables;
    private ResolvedType superclass;
    private ResolvedType[] superInterfaces;
    private String genericSignature = null;
    private ByteBuddyToResolvedTypeConverter typeConverter;
    private AnnotationFinderImpl annotationFinder = null;
    private ArgNameFinder argNameFinder = null;

    public Java15ReferenceTypeDelegateImpl() {
    }

    @Override
    public void initialize(ReferenceType aType, TypeDescription typeDescription, TypeWorld typeWorld) {
        super.initialize(aType, typeDescription, typeWorld);
//        myType = AjTypeSystem.getAjType(aClass);
        annotationFinder = new AnnotationFinderImpl();
        argNameFinder = annotationFinder;
        annotationFinder.setTypeWorld(typeWorld);
        this.typeConverter = new ByteBuddyToResolvedTypeConverter(typeWorld);
    }

    @Override
    public ReferenceType buildGenericType() {
        return (ReferenceType) UnresolvedType.forGenericTypeVariables(getResolvedTypeX().getSignature(), getTypeVariables())
                .resolve(getTypeWorld());
    }

    @Override
    public AnnotationAJ[] getAnnotations() {
        // AMC - we seem not to need to implement this method...
        // throw new UnsupportedOperationException(
        // "getAnnotations on Java15ReflectionBasedReferenceTypeDelegate is not implemented yet"
        // );
        // FIXME is this the right implementation in the reflective case?
        return super.getAnnotations();
    }

    @Override
    public ResolvedType[] getAnnotationTypes() {
        if (annotations == null) {
            annotations = annotationFinder.getAnnotations(getBaseClass(), getTypeWorld());
        }
        return annotations;
    }
    
    @Override
    public boolean hasAnnotations() {
        if (annotations == null) {
            annotations = annotationFinder.getAnnotations(getBaseClass(), getTypeWorld());
        }
        return annotations.length != 0;
    }

    @Override
    public boolean hasAnnotation(UnresolvedType ofType) {
        ResolvedType[] myAnns = getAnnotationTypes();
        ResolvedType toLookFor = ofType.resolve(getTypeWorld());
        for (int i = 0; i < myAnns.length; i++) {
            if (myAnns[i] == toLookFor) {
                return true;
            }
        }
        return false;
    }

    // use the MAP to ensure that any aj-synthetic fields are filtered out
    @Override
    public ResolvedMember[] getDeclaredFields() {
//        if (fields == null) {
//            Field[] reflectFields = this.typeDescription.getDeclaredFields();
//            ResolvedMember[] rFields = new ResolvedMember[reflectFields.length];
//            for (int i = 0; i < reflectFields.length; i++) {
//                rFields[i] = createGenericFieldMember(reflectFields[i]);
//            }
//            this.fields = rFields;
//        }
//        return fields;

    
        if (fields == null) {
            FieldList<FieldDescription.InDefinedShape> fieldList = this.typeDescription.getDeclaredFields();
            ResolvedMember[] rFields = new ResolvedMember[fieldList.size()];
            int i = 0;
            for (FieldDescription.InDefinedShape fieldDescription : fieldList) {
                rFields[i++] = createGenericFieldMember(fieldDescription);
            }
            this.fields = rFields;
        }
        return fields;
    }

    @Override
    public String getDeclaredGenericSignature() {
        if (this.genericSignature == null && isGeneric()) {
            // BUG? what the hell is this doing - see testcode in MemberTestCase15.testMemberSignatureCreation() and run it
            // off a Reflection World
        }
        return genericSignature;
    }

    @Override
    public ResolvedType[] getDeclaredInterfaces() {
//        if (superInterfaces == null) {
//            Type[] genericInterfaces = getBaseClass().getGenericInterfaces();
//            this.superInterfaces = typeConverter.fromTypes(genericInterfaces);
//        }
//        return superInterfaces;

        if (superInterfaces == null) {
            TypeList.Generic genericInterfaces = getBaseClass().getInterfaces();
            this.superInterfaces = typeConverter.fromTypes(genericInterfaces);
        }
        return superInterfaces;
    }

    @Override
    public ResolvedType getSuperclass() {
        // Superclass of object is null
//        if (superclass == null && getBaseClass() != Object.class) {
//            Type t = this.getBaseClass().getGenericSuperclass();
//            if (t != null) {
//                superclass = typeConverter.fromType(t);
//            }
//            if (t == null) {
//                // If the superclass is null, return Object - same as bcel does
//                superclass = getWorld().resolve(UnresolvedType.OBJECT);
//            }
//        }
//        return superclass;

        if (superclass == null && "java.lang.Object".equals(getBaseClass().getTypeName()) == false) {
            TypeDescription.Generic t = this.getBaseClass().getSuperClass();
            if (t != null) {
                superclass = typeConverter.fromType(t);
            }
            if (t == null) {
                // If the superclass is null, return Object - same as bcel does
                superclass = getTypeWorld().resolve(UnresolvedType.OBJECT);
            }
        }
        return superclass;
    
    }

    @Override
    public TypeVariable[] getTypeVariables() {
//        TypeVariable[] workInProgressSetOfVariables = getResolvedTypeX().getWorld().getTypeVariablesCurrentlyBeingProcessed(
//                getBaseClass());
//        if (workInProgressSetOfVariables != null) {
//            return workInProgressSetOfVariables;
//        }
//        if (this.typeVariables == null) {
//            java.lang.reflect.TypeVariable[] tVars = this.getBaseClass().getTypeParameters();
//            TypeVariable[] rTypeVariables = new TypeVariable[tVars.length];
//            // basic initialization
//            for (int i = 0; i < tVars.length; i++) {
//                rTypeVariables[i] = new TypeVariable(tVars[i].getName());
//            }
//            // stash it
//            this.getResolvedTypeX().getWorld().recordTypeVariablesCurrentlyBeingProcessed(getBaseClass(), rTypeVariables);
//            // now fill in the details...
//            for (int i = 0; i < tVars.length; i++) {
//                TypeVariableReferenceType tvrt = ((TypeVariableReferenceType) typeConverter.fromType(tVars[i]));
//                TypeVariable tv = tvrt.getTypeVariable();
//                rTypeVariables[i].setSuperclass(tv.getSuperclass());
//                rTypeVariables[i].setAdditionalInterfaceBounds(tv.getSuperInterfaces());
//                rTypeVariables[i].setDeclaringElement(tv.getDeclaringElement());
//                rTypeVariables[i].setDeclaringElementKind(tv.getDeclaringElementKind());
//                rTypeVariables[i].setRank(tv.getRank());
//            }
//            this.typeVariables = rTypeVariables;
//            this.getResolvedTypeX().getWorld().forgetTypeVariablesCurrentlyBeingProcessed(getBaseClass());
//        }
//        return this.typeVariables;


        World world = getResolvedTypeX().getWorld();
        if(world instanceof TypeWorld == false)
            return new TypeVariable[0];

        TypeWorld typeWorld = (TypeWorld) world;

        TypeVariable[] workInProgressSetOfVariables = typeWorld.getTypeVariablesCurrentlyBeingProcessed(
                getBaseClass());
        if (workInProgressSetOfVariables != null) {
            return workInProgressSetOfVariables;
        }
        if (this.typeVariables == null) {
            TypeList.Generic tVars = this.getBaseClass().getTypeVariables();
            TypeVariable[] rTypeVariables = new TypeVariable[tVars.size()];

            // basic initialization
            int i = 0;
            for (TypeDescription.Generic typeDescription : tVars) {
                rTypeVariables[i++] = new TypeVariable(typeDescription.getTypeName());
            }
            // stash it
            typeWorld.recordTypeVariablesCurrentlyBeingProcessed(getBaseClass(), rTypeVariables);
            // now fill in the details...
            for (i = 0; i < tVars.size(); i++) {
                TypeVariableReferenceType tvrt = ((TypeVariableReferenceType) typeConverter.fromType(tVars.get(i)));
                TypeVariable tv = tvrt.getTypeVariable();
                if(tv == null) {
                    continue;
                }

                rTypeVariables[i].setSuperclass(tv.getSuperclass());
                rTypeVariables[i].setAdditionalInterfaceBounds(tv.getSuperInterfaces());
                rTypeVariables[i].setDeclaringElement(tv.getDeclaringElement());
                rTypeVariables[i].setDeclaringElementKind(tv.getDeclaringElementKind());
                rTypeVariables[i].setRank(tv.getRank());

                i++;
            }
            this.typeVariables = rTypeVariables;
            typeWorld.forgetTypeVariablesCurrentlyBeingProcessed(getBaseClass());
        }
        return this.typeVariables;
    }

    // overrides super method since by using the MAP we can filter out advice
    // methods that really shouldn't be seen in this list
    @Override
    public ResolvedMember[] getDeclaredMethods() {
//        if (methods == null) {
//            Method[] reflectMethods = this.myType.getDeclaredMethods();
//            Constructor[] reflectCons = this.myType.getDeclaredConstructors();
//            ResolvedMember[] rMethods = new ResolvedMember[reflectMethods.length + reflectCons.length];
//            for (int i = 0; i < reflectMethods.length; i++) {
//                rMethods[i] = createGenericMethodMember(reflectMethods[i]);
//            }
//            for (int i = 0; i < reflectCons.length; i++) {
//                rMethods[i + reflectMethods.length] = createGenericConstructorMember(reflectCons[i]);
//            }
//            this.methods = rMethods;
//        }
//        return methods;
        
        
        if (methods == null) {
            MethodList<MethodDescription.InDefinedShape> reflectMethods = this.typeDescription.getDeclaredMethods();
            ResolvedMember[] rMethods = new ResolvedMember[reflectMethods.size()];
            int i = 0; 
            for (MethodDescription.InDefinedShape methodDescription : reflectMethods) {
                if(methodDescription.isConstructor()) {
                    rMethods[i] = createGenericConstructorMember(methodDescription);
                }
                if(methodDescription.isMethod()) {
                    rMethods[i] = createGenericMethodMember(methodDescription);
                }
                
                i++;
            }
            this.methods = rMethods;
        }
        return methods;
    }

    /**
     * Returns the generic type, regardless of the resolvedType we 'know about'
     */
    public ResolvedType getGenericResolvedType() {
        ResolvedType rt = getResolvedTypeX();
        if (rt.isParameterizedType() || rt.isRawType()) {
            return rt.getGenericType();
        }
        return rt;
    }

    private ResolvedMember createGenericMethodMember(MethodDescription.InDefinedShape methodDescription) {
//        ReflectionBasedResolvedMemberImpl ret = new ReflectionBasedResolvedMemberImpl(org.aspectj.weaver.Member.METHOD,
//                getGenericResolvedType(), methodDescription.getModifiers(), typeConverter.fromType(methodDescription.getReturnType()),
//                methodDescription.getName(), typeConverter.fromTypes(methodDescription.getParameterTypes()), typeConverter.fromTypes(methodDescription
//                        .getExceptionTypes()), methodDescription);
//        ret.setAnnotationFinder(this.annotationFinder);
//        ret.setGenericSignatureInformationProvider(new Java15GenericSignatureInformationProvider(this.getWorld()));
//        return ret;
        
        InternalResolvedMember ret = new InternalResolvedMember(org.aspectj.weaver.Member.METHOD,
                getGenericResolvedType(), methodDescription.getModifiers(), typeConverter.fromType(methodDescription.getReturnType()),
                methodDescription.getName(), typeConverter.fromTypes(methodDescription.getParameters().asTypeList()), typeConverter.fromTypes(methodDescription
                        .getExceptionTypes()), methodDescription);
        ret.setAnnotationFinder(this.annotationFinder);
        ret.setGenericSignatureInformationProvider(new GenericSignatureInformationProviderImpl(this.getTypeWorld()));
        return ret;

    }

    private ResolvedMember createGenericConstructorMember(MethodDescription.InDefinedShape forConstructor) {
//        ReflectionBasedResolvedMemberImpl ret = new ReflectionBasedResolvedMemberImpl(org.aspectj.weaver.Member.METHOD,
//                getGenericResolvedType(), forConstructor.getModifiers(),
//                // to return what BCEL returns the return type is void
//                UnresolvedType.VOID,// getGenericResolvedType(),
//                "<init>", typeConverter.fromTypes(forConstructor.getParameterTypes()), typeConverter.fromTypes(forConstructor
//                        .getExceptionTypes()), forConstructor);
//        ret.setAnnotationFinder(this.annotationFinder);
//        ret.setGenericSignatureInformationProvider(new Java15GenericSignatureInformationProvider(this.getWorld()));
//        return ret;

        InternalResolvedMember ret = new InternalResolvedMember(org.aspectj.weaver.Member.METHOD,
                getGenericResolvedType(), forConstructor.getModifiers(),
                // to return what BCEL returns the return type is void
                UnresolvedType.VOID,// getGenericResolvedType(),
                "<init>", typeConverter.fromTypes(forConstructor.getParameters().asTypeList()), typeConverter.fromTypes(forConstructor
                        .getExceptionTypes()), forConstructor);
        ret.setAnnotationFinder(this.annotationFinder);
        ret.setGenericSignatureInformationProvider(new GenericSignatureInformationProviderImpl(this.getTypeWorld()));
        return ret;

    }

    private ResolvedMember createGenericFieldMember(FieldDescription.InDefinedShape fieldDescription) {
//        ReflectionBasedResolvedMemberImpl ret = new ReflectionBasedResolvedMemberImpl(org.aspectj.weaver.Member.FIELD,
//                getGenericResolvedType(), forField.getModifiers(), typeConverter.fromType(forField.getType()), forField.getName(),
//                new UnresolvedType[0], forField);
//        ret.setAnnotationFinder(this.annotationFinder);
//        ret.setGenericSignatureInformationProvider(new Java15GenericSignatureInformationProvider(this.getWorld()));
//        return ret;

    
        InternalResolvedMember ret = new InternalResolvedMember(org.aspectj.weaver.Member.FIELD,
                getGenericResolvedType(), fieldDescription.getModifiers(), typeConverter.fromType(fieldDescription.getDeclaringType()), fieldDescription.getName(),
                new UnresolvedType[0], fieldDescription);
        ret.setAnnotationFinder(this.annotationFinder);
        ret.setGenericSignatureInformationProvider(new GenericSignatureInformationProviderImpl(this.getTypeWorld()));
        return ret;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public ResolvedMember[] getDeclaredPointcuts() {
//        if (pointcuts == null) {
//            Pointcut[] pcs = this.myType.getDeclaredPointcuts();
//            pointcuts = new ResolvedMember[pcs.length];
//            InternalUseOnlyPointcutParser parser = null;
//            World world = getWorld();
//            if (world instanceof ReflectionWorld) {
//                parser = new InternalUseOnlyPointcutParser(classLoaderReference.getClassLoader(), (ReflectionWorld) getWorld());
//            } else {
//                parser = new InternalUseOnlyPointcutParser(classLoaderReference.getClassLoader());
//            }
//            Set additionalPointcutHandlers = world.getRegisteredPointcutHandlers();
//            for (Iterator handlerIterator = additionalPointcutHandlers.iterator(); handlerIterator.hasNext();) {
//                PointcutDesignatorHandler handler = (PointcutDesignatorHandler) handlerIterator.next();
//                parser.registerPointcutDesignatorHandler(handler);
//            }
//
//            // phase 1, create legitimate entries in pointcuts[] before we
//            // attempt to resolve *any* of the pointcuts
//            // resolution can sometimes cause us to recurse, and this two stage
//            // process allows us to cope with that
//            for (int i = 0; i < pcs.length; i++) {
//                AjType<?>[] ptypes = pcs[i].getParameterTypes();
//                UnresolvedType[] weaverPTypes = new UnresolvedType[ptypes.length];
//                for (int j = 0; j < weaverPTypes.length; j++) {
//                    weaverPTypes[j] = this.typeConverter.fromType(ptypes[j].getJavaClass());
//                }
//                pointcuts[i] = new DeferredResolvedPointcutDefinition(getResolvedTypeX(), pcs[i].getModifiers(), pcs[i].getName(),
//                        weaverPTypes);
//            }
//            // phase 2, now go back round and resolve in-place all of the
//            // pointcuts
//            PointcutParameter[][] parameters = new PointcutParameter[pcs.length][];
//            for (int i = 0; i < pcs.length; i++) {
//                AjType<?>[] ptypes = pcs[i].getParameterTypes();
//                String[] pnames = pcs[i].getParameterNames();
//                if (pnames.length != ptypes.length) {
//                    pnames = tryToDiscoverParameterNames(pcs[i]);
//                    if (pnames == null || (pnames.length != ptypes.length)) {
//                        throw new IllegalStateException("Required parameter names not available when parsing pointcut "
//                                + pcs[i].getName() + " in type " + getResolvedTypeX().getName());
//                    }
//                }
//                parameters[i] = new PointcutParameter[ptypes.length];
//                for (int j = 0; j < parameters[i].length; j++) {
//                    parameters[i][j] = parser.createPointcutParameter(pnames[j], ptypes[j].getJavaClass());
//                }
//                String pcExpr = pcs[i].getPointcutExpression().toString();
//                org.aspectj.weaver.patterns.Pointcut pc = parser.resolvePointcutExpression(pcExpr, getBaseClass(), parameters[i]);
//                ((ResolvedPointcutDefinition) pointcuts[i]).setParameterNames(pnames);
//                ((ResolvedPointcutDefinition) pointcuts[i]).setPointcut(pc);
//            }
//            // phase 3, now concretize them all
//            for (int i = 0; i < pointcuts.length; i++) {
//                ResolvedPointcutDefinition rpd = (ResolvedPointcutDefinition) pointcuts[i];
//                rpd.setPointcut(parser.concretizePointcutExpression(rpd.getPointcut(), getBaseClass(), parameters[i]));
//            }
//        }
//        return pointcuts;
        
        if (pointcuts != null) {
            return this.pointcuts;
        }

        Pointcut[] pcs = this.getDeclaredPointcuts(typeDescription);
        ResolvedMember[] pointcuts = new ResolvedMember[pcs.length];

        TypeWorld typeWorld = getTypeWorld();
        InternalUseOnlyPointcutParser parser = new InternalUseOnlyPointcutParser(typeWorld);


//        if (world instanceof ReflectionWorld) {
//            parser = new InternalUseOnlyPointcutParser(classLoaderReference.getClassLoader(), (ReflectionWorld) getWorld());
//        } else {
//            parser = new InternalUseOnlyPointcutParser(classLoaderReference.getClassLoader());
//        }
        Set additionalPointcutHandlers = typeWorld.getRegisteredPointcutHandlers();
        for (Iterator handlerIterator = additionalPointcutHandlers.iterator(); handlerIterator.hasNext();) {
            PointcutDesignatorHandler handler = (PointcutDesignatorHandler) handlerIterator.next();
            parser.registerPointcutDesignatorHandler(handler);
        }

        // phase 1, create legitimate entries in pointcuts[] before we
        // attempt to resolve *any* of the pointcuts
        // resolution can sometimes cause us to recurse, and this two stage
        // process allows us to cope with that
        for (int i = 0; i < pcs.length; i++) {
            TypeDescription[] ptypes = pcs[i].getParameterTypes();
            UnresolvedType[] weaverPTypes = new UnresolvedType[ptypes.length];
            for (int j = 0; j < weaverPTypes.length; j++) {
                weaverPTypes[j] = this.typeConverter.fromType(ptypes[j]);
            }
            pointcuts[i] = new ResolvedPointcutDefinition(getResolvedTypeX(), pcs[i].getModifiers(), pcs[i].getName(), weaverPTypes, null);
        }
        // phase 2, now go back round and resolve in-place all of the
        // pointcuts
        PointcutParameter[][] parameters = new PointcutParameter[pcs.length][];
        for (int i = 0; i < pcs.length; i++) {
            TypeDescription[] ptypes = pcs[i].getParameterTypes();
            String[] pnames = pcs[i].getParameterNames();
            if (pnames.length != ptypes.length) {
                pnames = tryToDiscoverParameterNames(pcs[i]);
                if (pnames == null || (pnames.length != ptypes.length)) {
                    throw new IllegalStateException("Required parameter names not available when parsing pointcut "
                            + pcs[i].getName() + " in type " + getResolvedTypeX().getName());
                }
            }
            parameters[i] = new PointcutParameter[ptypes.length];
            for (int j = 0; j < parameters[i].length; j++) {
                parameters[i][j] = parser.createPointcutParameter(pnames[j], ptypes[j]);
            }

            String pcExpr = pcs[i].getPointcutExpression().toString();
            org.aspectj.weaver.patterns.Pointcut pc = parser.resolvePointcutExpression(pcExpr, getBaseClass(), parameters[i]);
            ((ResolvedPointcutDefinition) pointcuts[i]).setParameterNames(pnames);
            ((ResolvedPointcutDefinition) pointcuts[i]).setPointcut(pc);
        }
        // phase 3, now concretize them all
        for (int i = 0; i < pointcuts.length; i++) {
            ResolvedPointcutDefinition rpd = (ResolvedPointcutDefinition) pointcuts[i];
            rpd.setPointcut(parser.concretizePointcutExpression(rpd.getPointcut(), getBaseClass(), parameters[i]));
        }

        this.pointcuts = pointcuts;
        return pointcuts;
    }

    public Pointcut[] getDeclaredPointcuts(TypeDescription typeDescription) {
        List<Pointcut> pointcuts = new ArrayList<>();
        MethodList<?> methods = typeDescription.getDeclaredMethods();
        for (MethodDescription method : methods) {
            Pointcut pc = asPointcut(typeDescription, method);
            if (pc != null) pointcuts.add(pc);
        }
        Pointcut[] ret = new Pointcut[pointcuts.size()];
        pointcuts.toArray(ret);
        return ret;
    }

    

    private Pointcut asPointcut(TypeDescription typeDescription, MethodDescription method) {
//        org.aspectj.lang.annotation.Pointcut pcAnn = method.getAnnotation(org.aspectj.lang.annotation.Pointcut.class);
//        if (pcAnn != null) {
//            String name = method.getName();
//            if (name.startsWith(ajcMagic)) {
//                // extract real name
//                int nameStart = name.indexOf("$$");
//                name = name.substring(nameStart +2,name.length());
//                int nextDollar = name.indexOf("$");
//                if (nextDollar != -1) name = name.substring(0,nextDollar);
//            }
//            return new PointcutImpl(name,pcAnn.value(),method,AjTypeSystem.getAjType(method.getDeclaringClass()),pcAnn.argNames());
//        } else {
//            return null;
//        }
//        System.out.println("method" + method + ", getDeclaredAnnotations(): " + method.getDeclaredAnnotations());

//        if(method.getDeclaredAnnotations().isAnnotationPresent(org.aspectj.lang.annotation.Pointcut.class) == false)
//            return null;
        
        
        
        AnnotationList filter = method.getDeclaredAnnotations().filter(
                ElementMatchers.annotationType(org.aspectj.lang.annotation.Pointcut.class));
        if(filter.size() == 0)
            return null;
        
        AnnotationDescription pcAnn = filter.get(0);

        String name = method.getName();
//        if (name.startsWith(ajcMagic)) {
//            // extract real name
//            int nameStart = name.indexOf("$$");
//            name = name.substring(nameStart +2,name.length());
//            int nextDollar = name.indexOf("$");
//            if (nextDollar != -1) name = name.substring(0,nextDollar);
//        }
        String expression = pcAnn.getValue("value").resolve().toString();

        PlaceholderHelper placeholderHelper = this.getTypeWorld().getPlaceholderHelper();
        if(placeholderHelper != null) {
            expression = placeholderHelper.replace(expression);
        }

        return new PointcutImpl(name,
                expression, method, typeDescription, pcAnn.getValue("argNames").resolve().toString());
    }

    
    
    // for @AspectJ pointcuts compiled by javac only...
    private String[] tryToDiscoverParameterNames(Pointcut pcut) {
//        Method[] ms = pcut.getDeclaringType().getJavaClass().getDeclaredMethods();
//        for (Method m : ms) {
//            if (m.getName().equals(pcut.getName())) {
//                return argNameFinder.getParameterNames(m);
//            }
//        }
//        return null;

        TypeDescription typeDescription = typeWorld.getTypePool().describe(pcut.getDeclaringType().getName()).resolve();
        for (MethodDescription.InDefinedShape m : typeDescription.getDeclaredMethods()) {
            if (m.getName().equals(pcut.getName())) {
                return argNameFinder.getParameterNames(m);
            }
        }
        return null;
    }

    @Override
    public boolean isAnnotation() {
        return getBaseClass().isAnnotation();
    }

    @Override
    public boolean isAnnotationStyleAspect() {
        return getBaseClass().getDeclaredAnnotations().isAnnotationPresent(Aspect.class);
    }

    @Override
    public boolean isAnnotationWithRuntimeRetention() {
//        if (!isAnnotation()) {
//            return false;
//        }
//        if (getBaseClass().isAnnotationPresent(Retention.class)) {
//            Retention retention = (Retention) getBaseClass().getAnnotation(Retention.class);
//            RetentionPolicy policy = retention.value();
//            return policy == RetentionPolicy.RUNTIME;
//        } else {
//            return false;
//        }
        
        if (!isAnnotation()) {
            return false;
        }
        if (getBaseClass().getDeclaredAnnotations().isAnnotationPresent(Retention.class)) {
            AnnotationDescription annotationDescription = getBaseClass().getDeclaredAnnotations().filter(ElementMatchers.annotationType(Retention.class)).get(0);
            RetentionPolicy policy = annotationDescription.getRetention();
            return policy == RetentionPolicy.RUNTIME;
        } else {
            return false;
        }
    }

    @Override
    public boolean isAspect() {
        return getBaseClass().getDeclaredAnnotations().isAnnotationPresent(Aspect.class);
    }

    @Override
    public boolean isEnum() {
        return getBaseClass().isEnum();
    }

    @Override
    public boolean isGeneric() {
        // return false; // for now
        return getBaseClass().getTypeVariables().size() > 0;
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
    public ResolvedType getOuterClass() {
         return ReferenceTypeDelegateFactoryImpl.resolveTypeInWorld(
                     typeDescription.getEnclosingType(), typeWorld); 
    }

}
