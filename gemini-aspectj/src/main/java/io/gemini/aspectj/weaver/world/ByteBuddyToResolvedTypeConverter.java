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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.weaver.BoundedReferenceType;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.TypeFactory;
import org.aspectj.weaver.TypeVariable;
import org.aspectj.weaver.TypeVariableReferenceType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;

import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;

/**
 * Handles the translation of java.lang.reflect.Type objects into AspectJ UnresolvedTypes.
 * 
 */
public class ByteBuddyToResolvedTypeConverter {

    // Used to prevent recursion - we record what we are working on and return it if asked again *whilst* working on it
    private Map<TypeDefinition, TypeVariableReferenceType> typeVariablesInProgress = new HashMap<>();
    private final World world;

    public ByteBuddyToResolvedTypeConverter(World aWorld) {
        this.world = aWorld;
    }

    private World getWorld() {
        return this.world;
    }

    public ResolvedType fromType(TypeDefinition aType) {
        Generic asGeneric = aType.asGenericType();
        if (TypeDefinition.Sort.NON_GENERIC == asGeneric.getSort()) {
            String name = aType.getTypeName();
            /**
             * getName() can return:
             * 
             * 1. If this class object represents a reference type that is not an array type then the binary name of the class is
             * returned 2. If this class object represents a primitive type or void, then the name returned is a String equal to the
             * Java language keyword corresponding to the primitive type or void. 3. If this class object represents a class of
             * arrays, then the internal form of the name consists of the name of the element type preceded by one or more '['
             * characters representing the depth of the array nesting.
             */
            if (asGeneric.isArray()) {
                UnresolvedType ut = UnresolvedType.forSignature(name.replace('.', '/'));
                return getWorld().resolve(ut);
            } else {
                return getWorld().resolve(name);
            }
        } else if (TypeDefinition.Sort.PARAMETERIZED == asGeneric.getSort()) {
            ResolvedType baseType = fromType(asGeneric.asRawType());
            TypeList.Generic args = asGeneric.getTypeArguments();
            ResolvedType[] resolvedArgs = fromTypes(args);
            /*
             * StringBuilder sb = new StringBuilder(); for (int i = 0; i < resolvedArgs.length; i++) {
             * sb.append(resolvedArgs[i]).append(" "); } for (int i = 0; i < resolvedArgs.length; i++) { if (resolvedArgs[i] ==
             * null) { String ss = ""; try { ss = aType.toString(); } catch (Exception e) { } throw new
             * IllegalStateException("Parameterized type problem.  basetype=" + baseType + " arguments=" + sb.toString() + " ss=" +
             * ss); } }
             */
            return TypeFactory.createParameterizedType(baseType, resolvedArgs, getWorld());
        } else if (TypeDefinition.Sort.VARIABLE == asGeneric.getSort()) {
            TypeVariableReferenceType inprogressVar = typeVariablesInProgress.get(aType);
            if (inprogressVar != null) {
                return inprogressVar;
            }

            TypeVariable rt_tv = new TypeVariable(asGeneric.getActualName());
            TypeVariableReferenceType tvrt = new TypeVariableReferenceType(rt_tv, getWorld());

            typeVariablesInProgress.put(aType, tvrt); // record what we are working on, for recursion case

            TypeList.Generic bounds = asGeneric.getUpperBounds();
            ResolvedType[] resBounds = fromTypes(bounds);
            ResolvedType upperBound = resBounds[0];
            ResolvedType[] additionalBounds = new ResolvedType[0];
            if (resBounds.length > 1) {
                additionalBounds = new ResolvedType[resBounds.length - 1];
                System.arraycopy(resBounds, 1, additionalBounds, 0, additionalBounds.length);
            }
            rt_tv.setUpperBound(upperBound);
            rt_tv.setAdditionalInterfaceBounds(additionalBounds);

            typeVariablesInProgress.remove(aType); // we have finished working on it

            return tvrt;
        } else if (TypeDefinition.Sort.WILDCARD == asGeneric.getSort()) {
            TypeList.Generic lowerBounds = asGeneric.getLowerBounds();
            TypeList.Generic upperBounds = asGeneric.getUpperBounds();
            ResolvedType bound = null;
            boolean isExtends = lowerBounds.size() == 0;
            if (isExtends) {
                bound = fromType(upperBounds.get(0));
            } else {
                bound = fromType(lowerBounds.get(0));
            }
            return new BoundedReferenceType((ReferenceType) bound, isExtends, getWorld());
        } else if (TypeDefinition.Sort.GENERIC_ARRAY == asGeneric.getSort()) {
            Generic componentType = asGeneric.getComponentType();
            return UnresolvedType.makeArray(fromType(componentType), 1).resolve(getWorld());
        }
        return ResolvedType.MISSING;
    }

    public ResolvedType[] fromTypes(TypeDefinition... types) {
        ResolvedType[] ret = new ResolvedType[types.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = fromType(types[i]);
        }
        return ret;
    }
    
    public ResolvedType[] fromTypes(List<? extends TypeDefinition> types) {
        ResolvedType[] ret = new ResolvedType[types.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = fromType(types.get(i));
        }
        return ret;
    }

}
