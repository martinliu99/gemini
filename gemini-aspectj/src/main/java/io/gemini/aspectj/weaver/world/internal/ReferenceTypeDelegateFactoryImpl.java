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

import java.lang.reflect.Modifier;
import java.util.List;

import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedMemberImpl;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.world.GenericSignatureInformationProvider;
import io.gemini.aspectj.weaver.world.TypeWorld;
import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;

public class ReferenceTypeDelegateFactoryImpl {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ReferenceTypeDelegateFactoryImpl.class);

    public static ReferenceTypeDelegateImpl createDelegate(ReferenceType forReferenceType, TypeWorld typeWorld) {
        String typeName = forReferenceType.getName();

        TypeDescription typeDescription = typeWorld.getTypePool().describe(typeName).resolve();
        ReferenceTypeDelegateImpl rbrtd = create15Delegate(forReferenceType, typeDescription, typeWorld);
        if (rbrtd != null) {
            return rbrtd; // can be null if we didn't find the class the delegate logic loads
        }
        return new ReferenceTypeDelegateImpl(typeDescription, typeWorld, forReferenceType);
    }

    
    // can return 'null' if we can't find the class
    private static ReferenceTypeDelegateImpl create15Delegate(ReferenceType forReferenceType, TypeDescription typeDescription, TypeWorld typeWorld) {
        ReferenceTypeDelegateImpl ret = new Java15ReferenceTypeDelegateImpl();
        ret.initialize(forReferenceType, typeDescription, typeWorld);

        return ret;

//        try {
//            Class<?> delegateClass = Class.forName("io.gemini.aspectj.pattern.support.Java15ReferenceTypeDelegateImpl");
//            ReferenceTypeDelegateImpl ret = (ReferenceTypeDelegateImpl) delegateClass.newInstance();
//            
//            // TODO:??
//            ret.initialize(forReferenceType, typeDescription, typePool, inWorld);
//            return ret;
//        } catch (ClassNotFoundException cnfEx) {
//            throw new IllegalStateException(
//                    "Attempted to create Java 1.5 reflection based delegate but org.aspectj.weaver.reflect.Java15ReflectionBasedReferenceTypeDelegate was not found on classpath");
//        } catch (InstantiationException insEx) {
//            throw new IllegalStateException("Attempted to create Java 1.5 reflection based delegate but InstantiationException: "
//                    + insEx + " occured");
//        } catch (IllegalAccessException illAccEx) {
//            throw new IllegalStateException("Attempted to create Java 1.5 reflection based delegate but IllegalAccessException: "
//                    + illAccEx + " occured");
//        }
    }

    private static GenericSignatureInformationProvider createGenericSignatureProvider(TypeWorld typeWorld) {
        
        return new GenericSignatureInformationProviderImpl(typeWorld);

//        try {
//            Class<?> providerClass = Class.forName("io.gemini.aspectj.pattern.support.GenericSignatureInformationProviderImpl");
//            Constructor<?> cons = providerClass.getConstructor(new Class[] { World.class });
//            GenericSignatureInformationProvider ret = (GenericSignatureInformationProvider) cons
//                    .newInstance(new Object[] { inWorld });
//            return ret;
//        } catch (ClassNotFoundException cnfEx) {
//            // drop through and create a 14 provider...
//             throw new IllegalStateException("Attempted to create Java 1.5 generic signature provider but org.aspectj.weaver.reflect.Java15GenericSignatureInformationProvider was not found on classpath");
//        } catch (NoSuchMethodException nsmEx) {
//            throw new IllegalStateException("Attempted to create Java 1.5 generic signature provider but: " + nsmEx
//                    + " occured");
//        } catch (InstantiationException insEx) {
//            throw new IllegalStateException("Attempted to create Java 1.5 generic signature provider but: " + insEx
//                    + " occured");
//        } catch (InvocationTargetException invEx) {
//            throw new IllegalStateException("Attempted to create Java 1.5 generic signature provider but: " + invEx
//                    + " occured");
//        } catch (IllegalAccessException illAcc) {
//            throw new IllegalStateException("Attempted to create Java 1.5 generic signature provider but: " + illAcc
//                    + " occured");
//        }
    }

    
    
    public static ResolvedMember createResolvedMember(Member member, TypeWorld typeWorld) {
        if (member instanceof MethodDescription) {
            MethodDescription method = (MethodDescription) member;

            if(method.isConstructor())
                return createResolvedConstructor(method, typeWorld);
            else if(method.isMethod())
                return createResolvedMethod( method, typeWorld);
        } else if (member instanceof FieldDescription) {
            return createResolvedField( (FieldDescription) member, typeWorld);
        }

        throw new IllegalStateException("impossible execution path");
    }

    public static ResolvedMember createResolvedConstructor(MethodDescription methodDescription, TypeWorld typeWorld) {
        InternalResolvedMember ret = new InternalResolvedMember(
                org.aspectj.weaver.Member.CONSTRUCTOR,
                toResolvedType(methodDescription.getDeclaringType(), typeWorld), 
                methodDescription.getModifiers(),
                // to return what BCEL returns, the return type for ctor is void
                UnresolvedType.VOID,// toResolvedType(aConstructor.getDeclaringClass(),(TypeWorld)inWorld),
                MethodDescription.CONSTRUCTOR_INTERNAL_NAME, 
                toResolvedTypeArray(methodDescription.getParameters().asTypeList(), typeWorld), 
                toResolvedTypeArray(methodDescription.getExceptionTypes(), typeWorld), 
                methodDescription);

        ret.setAnnotationFinder(typeWorld.getAnnotationFinder());
        ret.setGenericSignatureInformationProvider(createGenericSignatureProvider(typeWorld));

        return ret;
    }

    public static ResolvedMember createResolvedMethod(MethodDescription methodDescription, TypeWorld typeWorld) {
        InternalResolvedMember ret = new InternalResolvedMember(
                org.aspectj.weaver.Member.METHOD,
                toResolvedType(methodDescription.getDeclaringType(), typeWorld), 
                methodDescription.getModifiers(), 
                toResolvedType(methodDescription.getReturnType(), typeWorld), 
                methodDescription.getName(), 
                toResolvedTypeArray(methodDescription.getParameters().asTypeList(), typeWorld), 
                toResolvedTypeArray(methodDescription.getExceptionTypes(), typeWorld), 
                methodDescription);

        ret.setAnnotationFinder(typeWorld.getAnnotationFinder());
        ret.setGenericSignatureInformationProvider(createGenericSignatureProvider(typeWorld));

        return ret;
    }


    public static ResolvedMember createResolvedAdviceMember(MethodDescription methodDescription, TypeWorld typeWorld) {
        InternalResolvedMember ret = new InternalResolvedMember(
                org.aspectj.weaver.Member.ADVICE,
                toResolvedType(methodDescription.getDeclaringType(), typeWorld), 
                methodDescription.getModifiers(), 
                toResolvedType(methodDescription.getReturnType(), typeWorld), 
                methodDescription.getName(), 
                toResolvedTypeArray(methodDescription.getParameters().asTypeList(), typeWorld), 
                toResolvedTypeArray(methodDescription.getExceptionTypes(), typeWorld), 
                methodDescription);

        ret.setAnnotationFinder(typeWorld.getAnnotationFinder());
        ret.setGenericSignatureInformationProvider(createGenericSignatureProvider(typeWorld));

        return ret;
    }

    public static ResolvedMember createStaticInitMember(TypeDescription forType, TypeWorld typeWorld) {
        return new ResolvedMemberImpl(
                org.aspectj.weaver.Member.STATIC_INITIALIZATION, 
                toResolvedType(forType, typeWorld), 
                Modifier.STATIC, 
                UnresolvedType.VOID, 
                MethodDescription.TYPE_INITIALIZER_INTERNAL_NAME, 
                new UnresolvedType[0],
                new UnresolvedType[0]);
    }

    public static ResolvedMember createResolvedField(FieldDescription aField, TypeWorld typeWorld) {
        InternalResolvedMember ret = new InternalResolvedMember(
                org.aspectj.weaver.Member.FIELD,
                toResolvedType(aField.getDeclaringType(), typeWorld), 
                aField.getModifiers(), 
                toResolvedType(aField.getType(), typeWorld), 
                aField.getName(), 
                new UnresolvedType[0], 
                aField);

        ret.setAnnotationFinder(((TypeWorld) typeWorld).getAnnotationFinder());
        ret.setGenericSignatureInformationProvider(createGenericSignatureProvider(typeWorld));
        return ret;
    }

    public static ResolvedMember createHandlerMember(TypeDescription exceptionType, TypeDefinition inType, TypeWorld typeWorld) {
        return new ResolvedMemberImpl(
                org.aspectj.weaver.Member.HANDLER, 
                toResolvedType(inType, typeWorld),
                Modifier.STATIC, 
                "<catch>", 
                "(" + typeWorld.resolve(exceptionType.getName()).getSignature() + ")V");
    }

    public static ResolvedType resolveTypeInWorld(TypeDefinition typeDefinition, TypeWorld typeWorld) {
        // classes that represent arrays return a class name that is the signature of the array type, ho-hum...
        String className = typeDefinition.getTypeName();
        if (typeDefinition.isArray()) {
            return typeWorld.resolve(UnresolvedType.forSignature(className.replace('.', '/')));
        } else {
            return typeWorld.resolve(className);
        }
    }

    private static ResolvedType toResolvedType(TypeDefinition typeDefinition, TypeWorld typeWorld) {
        return typeWorld.resolve(typeDefinition);
    }

    private static ResolvedType[] toResolvedTypeArray(List<? extends TypeDefinition> typeDefinitions, TypeWorld typeWorld) {
        ResolvedType[] ret = new ResolvedType[typeDefinitions.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = typeWorld.resolve(typeDefinitions.get(i));
        }
        return ret;
    }
}
