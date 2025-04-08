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

import java.util.HashSet;
import java.util.Set;

import org.aspectj.weaver.AnnotationAJ;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;

import io.gemini.aspectj.weaver.world.AnnotationFinder;
import io.gemini.aspectj.weaver.world.ArgNameFinder;
import io.gemini.aspectj.weaver.world.TypeWorld;
import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;

/**
 * Find the given annotation (if present) on the given object
 * 
 */
public class AnnotationFinderImpl implements AnnotationFinder, ArgNameFinder {

//    private Repository bcelRepository;
//    private BcelWeakClassLoaderReference classLoaderRef;

    private TypeWorld typeWorld;

    // must have no-arg constructor for reflective construction
    public AnnotationFinderImpl() {
    }

//    public void setClassLoader(ClassLoader aLoader) {
//        // TODO: No easy way to ask the world factory for the right kind of
//        // repository so
//        // default to the safe one! (pr160674)
//        this.classLoaderRef = new BcelWeakClassLoaderReference(aLoader);
//        this.bcelRepository = new NonCachingClassLoaderRepository(classLoaderRef);
//    }

    public void setTypeWorld(TypeWorld aWorld) {
        this.typeWorld = aWorld;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.reflect.AnnotationFinder#getAnnotation(org.aspectj .weaver.ResolvedType, java.lang.Object)
     */
    public AnnotationDescription getAnnotation(ResolvedType annotationType, Object onObject) {
//        try {
//            Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) Class.forName(annotationType.getName(),
//                    false, getClassLoader());
//            if (onObject.getClass().isAnnotationPresent(annotationClass)) {
//                return onObject.getClass().getAnnotation(annotationClass);
//            }
//        } catch (ClassNotFoundException ex) {
//            // just return null
//        }
//        return null;
        
        TypeDescription annotationDescription = typeWorld.getTypePool().describe(annotationType.getName()).resolve();

        TypeDescription typeDescription = typeWorld.getTypePool().describe(onObject.getClass().getName()).resolve();
        for(AnnotationDescription elementDescription : typeDescription.getDeclaredAnnotations()) {
            if(elementDescription.getAnnotationType().equals(annotationDescription)) {
                return elementDescription;
            }
        }
        
        return null;
    }

    public AnnotationDescription getAnnotationFromClass(ResolvedType annotationType, TypeDescription typeDescription) {
//        try {
//            Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) Class.forName(annotationType.getName(),
//                    false, getClassLoader());
//            if (aClass.isAnnotationPresent(annotationClass)) {
//                return aClass.getAnnotation(annotationClass);
//            }
//        } catch (ClassNotFoundException ex) {
//            // just return null
//        }
//        return null;
        
        TypeDescription annotationDescription = typeWorld.getTypePool().describe(annotationType.getName()).resolve();
        for(AnnotationDescription elementDescription : typeDescription.getDeclaredAnnotations()) {
            if(elementDescription.getAnnotationType().equals(annotationDescription)) {
                return elementDescription;
            }
        }
        
        return null;
    }

    public AnnotationDescription getAnnotationFromMember(ResolvedType annotationType, Member aMember) {
//        if (!(aMember instanceof AccessibleObject))
//            return null;
//        AccessibleObject ao = (AccessibleObject) aMember;
//        try {
//            Class annotationClass = Class.forName(annotationType.getName(), false, getClassLoader());
//            if (ao.isAnnotationPresent(annotationClass)) {
//                return ao.getAnnotation(annotationClass);
//            }
//        } catch (ClassNotFoundException ex) {
//            // just return null
//        }
//        return null;
        
        TypeDescription annotationDescription = typeWorld.getTypePool().describe(annotationType.getName()).resolve();
        for(AnnotationDescription elementDescription : aMember.getDeclaredAnnotations()) {
            if(elementDescription.getAnnotationType().equals(annotationDescription)) {
                return elementDescription;
            }
        }
        
        return null;
    }

//    private ClassLoader getClassLoader() {
//        return classLoaderRef.getClassLoader();
//    }

    public AnnotationAJ getAnnotationOfType(UnresolvedType ofType, Member onMember) {
//        if (!(onMember instanceof AccessibleObject))
//            return null;
//        // here we really want both the runtime visible AND the class visible
//        // annotations
//        // so we bail out to Bcel and then chuck away the JavaClass so that we
//        // don't hog
//        // memory.
//        try {
//            JavaClass jc = bcelRepository.loadClass(onMember.getDeclaringClass());
//            org.aspectj.apache.bcel.classfile.annotation.AnnotationGen[] anns = new org.aspectj.apache.bcel.classfile.annotation.AnnotationGen[0];
//            if (onMember instanceof Method) {
//                org.aspectj.apache.bcel.classfile.Method bcelMethod = jc.getMethod((Method) onMember);
//                if (bcelMethod == null) {
//                    // pr220430
//                    // System.err.println(
//                    // "Unexpected problem in Java15AnnotationFinder: cannot retrieve annotations on method '"
//                    // +
//                    // onMember.getName()+"' in class '"+jc.getClassName()+"'");
//                } else {
//                    anns = bcelMethod.getAnnotations();
//                }
//            } else if (onMember instanceof Constructor) {
//                org.aspectj.apache.bcel.classfile.Method bcelCons = jc.getMethod((Constructor) onMember);
//                anns = bcelCons.getAnnotations();
//            } else if (onMember instanceof Field) {
//                org.aspectj.apache.bcel.classfile.Field bcelField = jc.getField((Field) onMember);
//                anns = bcelField.getAnnotations();
//            }
//            // the answer is cached and we don't want to hold on to memory
//            bcelRepository.clear();
//            // OPTIMIZE make constant 0 size array for sharing
//            if (anns == null)
//                anns = new org.aspectj.apache.bcel.classfile.annotation.AnnotationGen[0];
//            // convert to our Annotation type
//            for (int i = 0; i < anns.length; i++) {
//                if (anns[i].getTypeSignature().equals(ofType.getSignature())) {
//                    return new BcelAnnotation(anns[i], world);
//                }
//            }
//            return null;
//        } catch (ClassNotFoundException cnfEx) {
//            // just use reflection then
//        }
//
//        return null;
        

        TypeDescription annotationDescription = typeWorld.getTypePool().describe(ofType.getName()).resolve();
        for(AnnotationDescription elementDescription : onMember.getDeclaredAnnotations()) {
            if(elementDescription.getAnnotationType().equals(annotationDescription)) {
                return new AnnotationAJImpl(
                        UnresolvedType.forSignature(elementDescription.getAnnotationType().getActualName()).resolve(typeWorld), 
                        elementDescription);
            }
        }
        
        return null;
    }

    public String getAnnotationDefaultValue(Member onMember) {
//        try {
//            JavaClass jc = bcelRepository.loadClass(onMember.getDeclaringClass());
//            if (onMember instanceof Method) {
//                org.aspectj.apache.bcel.classfile.Method bcelMethod = jc.getMethod((Method) onMember);
//
//                if (bcelMethod == null) {
//                    // pr220430
//                    // System.err.println(
//                    // "Unexpected problem in Java15AnnotationFinder: cannot retrieve annotations on method '"
//                    // +
//                    // onMember.getName()+"' in class '"+jc.getClassName()+"'");
//                } else {
//                    Attribute[] attrs = bcelMethod.getAttributes();
//                    for (int i = 0; i < attrs.length; i++) {
//                        Attribute attribute = attrs[i];
//                        if (attribute.getName().equals("AnnotationDefault")) {
//                            AnnotationDefault def = (AnnotationDefault) attribute;
//                            return def.getElementValue().stringifyValue();
//                        }
//                    }
//                    return null;
//                }
//            }
//        } catch (ClassNotFoundException cnfEx) {
//            // just use reflection then
//        }
//
//        return null;
        if(onMember == null)
            return null;

        if(onMember instanceof MethodDescription == false)
            return null;

        AnnotationValue<?, ?> defaultValue = ((MethodDescription) onMember).getDefaultValue();
        return defaultValue == null ? null : defaultValue.toString();
    }

    public Set<ResolvedType> getAnnotations(Member onMember) {
//        if (!(onMember instanceof AccessibleObject))
//            return Collections.EMPTY_SET;
//        // here we really want both the runtime visible AND the class visible
//        // annotations
//        // so we bail out to Bcel and then chuck away the JavaClass so that we
//        // don't hog
//        // memory.
//        try {
//            JavaClass jc = bcelRepository.loadClass(onMember.getDeclaringClass());
//            org.aspectj.apache.bcel.classfile.annotation.AnnotationGen[] anns = new org.aspectj.apache.bcel.classfile.annotation.AnnotationGen[0];
//            if (onMember instanceof Method) {
//                org.aspectj.apache.bcel.classfile.Method bcelMethod = jc.getMethod((Method) onMember);
//                if (bcelMethod == null) {
//                    // fallback on reflection - see pr220430
//                    // System.err.println(
//                    // "Unexpected problem in Java15AnnotationFinder: cannot retrieve annotations on method '"
//                    // +
//                    // onMember.getName()+"' in class '"+jc.getClassName()+"'");
//                } else {
//                    anns = bcelMethod.getAnnotations();
//                }
//            } else if (onMember instanceof Constructor) {
//                org.aspectj.apache.bcel.classfile.Method bcelCons = jc.getMethod((Constructor) onMember);
//                anns = bcelCons.getAnnotations();
//            } else if (onMember instanceof Field) {
//                org.aspectj.apache.bcel.classfile.Field bcelField = jc.getField((Field) onMember);
//                anns = bcelField.getAnnotations();
//            }
//            // the answer is cached and we don't want to hold on to memory
//            bcelRepository.clear();
//            // OPTIMIZE make this a constant 0 size array
//            if (anns == null)
//                anns = new org.aspectj.apache.bcel.classfile.annotation.AnnotationGen[0];
//            // convert to our Annotation type
//            Set<ResolvedType> annSet = new HashSet<>();
//            for (int i = 0; i < anns.length; i++) {
//                annSet.add(world.resolve(UnresolvedType.forSignature(anns[i].getTypeSignature())));
//            }
//            return annSet;
//        } catch (ClassNotFoundException cnfEx) {
//            // just use reflection then
//        }
//
//        AccessibleObject ao = (AccessibleObject) onMember;
//        Annotation[] anns = ao.getDeclaredAnnotations();
//        Set<UnresolvedType> annSet = new HashSet<>();
//        for (int i = 0; i < anns.length; i++) {
//            annSet.add(UnresolvedType.forName(anns[i].annotationType().getName()).resolve(world));
//        }
//        return annSet;
        

        Set<ResolvedType> annSet = new HashSet<>();
        for(AnnotationDescription elementDescription : onMember.getDeclaredAnnotations()) {
            annSet.add( typeWorld.resolve(
                    UnresolvedType.forName(elementDescription.getAnnotationType().getTypeName()) ) );
        }
        
        return annSet;
    }

    public ResolvedType[] getAnnotations(TypeDescription forClass, World inWorld) {
//        // here we really want both the runtime visible AND the class visible
//        // annotations
//        // so we bail out to Bcel and then chuck away the JavaClass so that we
//        // don't hog
//        // memory.
//        try {
//            JavaClass jc = bcelRepository.loadClass(forClass);
//            org.aspectj.apache.bcel.classfile.annotation.AnnotationGen[] anns = jc.getAnnotations();
//            bcelRepository.clear();
//            if (anns == null)
//                return new ResolvedType[0];
//            ResolvedType[] ret = new ResolvedType[anns.length];
//            for (int i = 0; i < ret.length; i++) {
//                ret[i] = inWorld.resolve(UnresolvedType.forSignature(anns[i].getTypeSignature()));
//            }
//            return ret;
//        } catch (ClassNotFoundException cnfEx) {
//            // just use reflection then
//        }
//
//        Annotation[] classAnnotations = forClass.getAnnotations();
//        ResolvedType[] ret = new ResolvedType[classAnnotations.length];
//        for (int i = 0; i < classAnnotations.length; i++) {
//            ret[i] = inWorld.resolve(classAnnotations[i].annotationType().getName());
//        }
//
//        return ret;
        
        AnnotationList annotationList = forClass.getDeclaredAnnotations();
        ResolvedType[] ret = new ResolvedType[ annotationList.size() ];
        int i = 0;
        for(AnnotationDescription elementDescription : annotationList) {
            ret[i++] = inWorld.resolve(elementDescription.getAnnotationType().getActualName());
        }
        
        return ret;
    }

    public String[] getParameterNames(Member forMember) {
//        if (!(forMember instanceof AccessibleObject))
//            return null;
//
//        try {
//            JavaClass jc = bcelRepository.loadClass(forMember.getDeclaringClass());
//            LocalVariableTable lvt = null;
//            int numVars = 0;
//            if (forMember instanceof Method) {
//                org.aspectj.apache.bcel.classfile.Method bcelMethod = jc.getMethod((Method) forMember);
//                lvt = bcelMethod.getLocalVariableTable();
//                numVars = bcelMethod.getArgumentTypes().length;
//            } else if (forMember instanceof Constructor) {
//                org.aspectj.apache.bcel.classfile.Method bcelCons = jc.getMethod((Constructor) forMember);
//                lvt = bcelCons.getLocalVariableTable();
//                numVars = bcelCons.getArgumentTypes().length;
//            }
//            return getParameterNamesFromLVT(lvt, numVars);
//        } catch (ClassNotFoundException cnfEx) {
//            ; // no luck
//        }
//
//        return null;
//        
//        if(forMember instanceof MethodDescription == false)
//            return null;
        

        if(forMember instanceof MethodDescription == false)
            return null;

        MethodDescription methodDescription = (MethodDescription) forMember;
        ParameterList<?> parameterList = methodDescription.getParameters();
        
        int i = 0;
        String[] ret = new String[parameterList.size()];
        for(ParameterDescription parameterDescription : parameterList) {
            ret[i++] = parameterDescription.getName();
        }
        
        return ret;
    }

//    private String[] getParameterNamesFromLVT(LocalVariableTable lvt, int numVars) {
//        if (lvt == null)
//            return null;// pr222987 - prevent NPE
//        LocalVariable[] vars = lvt.getLocalVariableTable();
//        if (vars.length < numVars) {
//            // basic error, we can't get the names...
//            return null;
//        }
//        String[] ret = new String[numVars];
//        for (int i = 0; i < numVars; i++) {
//            ret[i] = vars[i + 1].getName();
//        }
//        return ret;
//    }

    public static final ResolvedType[][] NO_PARAMETER_ANNOTATIONS = new ResolvedType[][] {};

    public ResolvedType[][] getParameterAnnotationTypes(Member onMember) {
//        if (!(onMember instanceof AccessibleObject))
//            return NO_PARAMETER_ANNOTATIONS;
//        // here we really want both the runtime visible AND the class visible
//        // annotations
//        // so we bail out to Bcel and then chuck away the JavaClass so that we
//        // don't hog
//        // memory.
//        try {
//            JavaClass jc = bcelRepository.loadClass(onMember.getDeclaringClass());
//            org.aspectj.apache.bcel.classfile.annotation.AnnotationGen[][] anns = null;
//            if (onMember instanceof Method) {
//                org.aspectj.apache.bcel.classfile.Method bcelMethod = jc.getMethod((Method) onMember);
//                if (bcelMethod == null) {
//                    // pr220430
//                    // System.err.println(
//                    // "Unexpected problem in Java15AnnotationFinder: cannot retrieve annotations on method '"
//                    // +
//                    // onMember.getName()+"' in class '"+jc.getClassName()+"'");
//                } else {
//                    anns = bcelMethod.getParameterAnnotations();
//                }
//            } else if (onMember instanceof Constructor) {
//                org.aspectj.apache.bcel.classfile.Method bcelCons = jc.getMethod((Constructor) onMember);
//                anns = bcelCons.getParameterAnnotations();
//            } else if (onMember instanceof Field) {
//                // anns = null;
//            }
//            // the answer is cached and we don't want to hold on to memory
//            bcelRepository.clear();
//            if (anns == null)
//                return NO_PARAMETER_ANNOTATIONS;
//            ResolvedType[][] result = new ResolvedType[anns.length][];
//            // CACHING??
//            for (int i = 0; i < anns.length; i++) {
//                if (anns[i] != null) {
//                    result[i] = new ResolvedType[anns[i].length];
//                    for (int j = 0; j < anns[i].length; j++) {
//                        result[i][j] = world.resolve(UnresolvedType.forSignature(anns[i][j].getTypeSignature()));
//                    }
//                }
//            }
//            return result;
//        } catch (ClassNotFoundException cnfEx) {
//            // just use reflection then
//        }
//
//        // reflection...
//        AccessibleObject ao = (AccessibleObject) onMember;
//        Annotation[][] anns = null;
//        if (onMember instanceof Method) {
//            anns = ((Method) ao).getParameterAnnotations();
//        } else if (onMember instanceof Constructor) {
//            anns = ((Constructor) ao).getParameterAnnotations();
//        } else if (onMember instanceof Field) {
//            // anns = null;
//        }
//        if (anns == null)
//            return NO_PARAMETER_ANNOTATIONS;
//        ResolvedType[][] result = new ResolvedType[anns.length][];
//        // CACHING??
//        for (int i = 0; i < anns.length; i++) {
//            if (anns[i] != null) {
//                result[i] = new ResolvedType[anns[i].length];
//                for (int j = 0; j < anns[i].length; j++) {
//                    result[i][j] = UnresolvedType.forName(anns[i][j].annotationType().getName()).resolve(world);
//                }
//            }
//        }
//        return result;

    
        if(onMember instanceof MethodDescription == false)
            return NO_PARAMETER_ANNOTATIONS;

        // here we really want both the runtime visible AND the class visible
        // annotations
        // so we bail out to Bcel and then chuck away the JavaClass so that we
        // don't hog
        // memory.
        MethodDescription methodDescription = (MethodDescription) onMember;
        ParameterList<?> parameterList = methodDescription.getParameters();
        
        ResolvedType[][] result = new ResolvedType[parameterList.size()][];
        int i = 0;
        for(ParameterDescription parameterDescription : parameterList) {
            AnnotationList annotationList = parameterDescription.getDeclaredAnnotations();
            
            ResolvedType[] anns = new ResolvedType[annotationList.size()];
            result[i++] = anns;
            int j = 0;
            for(AnnotationDescription annotationDescription : annotationList) {
                anns[j++] = typeWorld.resolve(UnresolvedType.forSignature(annotationDescription.getAnnotationType().getTypeName()));
            }
        }
        return result;
    }

}
