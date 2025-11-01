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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.bridge.AbortException;
import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.IMessageHandler;
import org.aspectj.weaver.BoundedReferenceType;
import org.aspectj.weaver.IWeavingSupport;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ReferenceTypeDelegate;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.Shadow.Kind;
import org.aspectj.weaver.TypeFactory;
import org.aspectj.weaver.TypeVariable;
import org.aspectj.weaver.TypeVariableReferenceType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;

import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.pool.TypePool;


public class BytebuddyWorld extends World implements TypeWorld {

    protected final static TypeDescription  OBJECT_DESCRIPTION = TypeDescription.ForLoadedType.of(Object.class);

    protected final TypePool typePool;
    private final PlaceholderHelper placeholderHelper;

    private final ResolvedType objectType;

    // Used to prevent recursion - we record what we are working on and return it if asked again *whilst* working on it
    private Map<TypeDefinition, TypeVariableReferenceType> typeVariablesInProgress = new ConcurrentHashMap<>();


    public BytebuddyWorld(TypePool typePool, PlaceholderHelper placeholderHelper) {
        this.setMessageHandler(new ExceptionBasedMessageHandler());
        setBehaveInJava5Way(true);

        this.typePool = typePool;
        this.placeholderHelper = placeholderHelper;

        this.objectType = this.resolve(UnresolvedType.OBJECT);
    }


    /** {@inheritDoc} 
     */
    @Override
    public IWeavingSupport getWeavingSupport() {
        return null;
    }

    /** {@inheritDoc} 
     */
    @Override
    public boolean isLoadtimeWeaving() {
        return true;
    }

    protected PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }

    protected ResolvedType getObjectType() {
        return objectType;
    }


    /** 
     * {@inheritDoc}
     */
    @Override
    public World getWorld() {
        return this;
    }

    @Override
    public ResolvedType resolve(TypeDescription typeDescription) {
        if (typeDescription == null)
            return null;

        String typeName = typeDescription.getTypeName();
        if (typeDescription.isArray()) {
            UnresolvedType ut = UnresolvedType.forSignature(typeName.replace('.', '/'));
            return this.resolve(ut, false);
        } else {
            return this.resolve(
                    UnresolvedType.forName(typeName), false);
        }
    }


    @Override
    protected ReferenceTypeDelegate resolveDelegate(ReferenceType referenceType) {
        try {
            return new InternalReferenceTypeDelegate(
                    this, 
                    describeType(referenceType.getName()), 
                    referenceType);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * {@inheritDoc}
     */
    public TypeDescription describeType(String typeName) {
        if ("java.lang.Object".equals(typeName)) return OBJECT_DESCRIPTION;

        return this.typePool.describe(typeName).resolve();
    }


    /**
     * {@inheritDoc}
     */
    public ResolvedMember resolve(Member member) {
        return doResolve(member);
    }

    /** 
     * {@inheritDoc}
     */
    @Override
    public Shadow makeShadow(Member member) {
        if (member == null)
            return null;

        ResolvedMember resolvedMember = this.doResolve(member);
        return makeExecutionShadow(resolvedMember);
    }

    protected ResolvedMember doResolve(Member member) {
        if (member instanceof MethodDescription) {
            return new InternalResolvedMember(this, (MethodDescription) member);
        } else if (member instanceof FieldDescription) {
            return new InternalResolvedMember(this, (FieldDescription) member);
        }

        throw new IllegalStateException("impossible execution path, member: " + member);
    }

    protected Shadow makeExecutionShadow(ResolvedMember member) {
        Kind kind = org.aspectj.weaver.Member.CONSTRUCTOR == member.getKind()
                ? Shadow.ConstructorExecution
                : (org.aspectj.weaver.Member.STATIC_INITIALIZATION == member.getKind()
                        ? Shadow.StaticInitialization
                        : Shadow.MethodExecution);

        ResolvedType enclosingType = member.getDeclaringType().resolve(this);

        return new InternalShadow(this, kind, member, null, enclosingType, null);
    }

    protected ResolvedType[] convertType(TypeDefinition... typeDefinitions) {
        return convertType(Arrays.asList(typeDefinitions));
    }

    protected ResolvedType[] convertType(List<? extends TypeDefinition> typeDefinitions) {
        if (CollectionUtils.isEmpty(typeDefinitions))
            return new ResolvedType[0];

        ResolvedType[] resolvedTypes = new ResolvedType[typeDefinitions.size()];
        for (int i = 0; i < resolvedTypes.length; i++) {
            resolvedTypes[i] = convertType(typeDefinitions.get(i));
        }
        return resolvedTypes;
    }

    protected ResolvedType convertType(TypeDefinition typeDefinition) {
        if (TypeDefinition.Sort.NON_GENERIC == typeDefinition.getSort()) {
            return resolve(typeDefinition.asErasure());
        } else if (TypeDefinition.Sort.PARAMETERIZED == typeDefinition.getSort()) {
            Generic asGeneric = typeDefinition.asGenericType();
            ResolvedType baseType = this.convertType(asGeneric.asRawType());

            if (baseType.isSimpleType() && asGeneric.getTypeArguments().size() == 0 && asGeneric.getOwnerType() != null) {
                // 'type' is an inner type of some outer parameterized type
                // For now just return the base type - in future create the parameterized form of the outer
                // and use it with the inner. We return the base type to be compatible with what the
                // code does that accesses the info from the bytecode (unlike this code which accesses it
                // reflectively).
                return baseType;
            }

            ResolvedType[] resolvedTypeArgs = this.convertType( asGeneric.getTypeArguments() );
            return TypeFactory.createParameterizedType(baseType, resolvedTypeArgs, this);
        } else if (TypeDefinition.Sort.VARIABLE == typeDefinition.getSort()) {
            TypeVariableReferenceType inprogressTypeVar = typeVariablesInProgress.get(typeDefinition);
            if (inprogressTypeVar != null) {
                return inprogressTypeVar;
            }

            try {
                Generic generic = typeDefinition.asGenericType();
                TypeVariable typeVariable = new TypeVariable(generic.getActualName());
                TypeVariableReferenceType typeVariableReference = new TypeVariableReferenceType(typeVariable, this);
                typeVariablesInProgress.put(typeDefinition, typeVariableReference); // record what we are working on, for recursion case

                TypeList.Generic bounds = generic.getUpperBounds();
                ResolvedType[] resBounds = this.convertType(bounds);
                ResolvedType upperBound = resBounds[0];
                ResolvedType[] additionalBounds = new ResolvedType[0];
                if (resBounds.length > 1) {
                    additionalBounds = new ResolvedType[resBounds.length - 1];
                    System.arraycopy(resBounds, 1, additionalBounds, 0, additionalBounds.length);
                }
                typeVariable.setUpperBound(upperBound);
                typeVariable.setAdditionalInterfaceBounds(additionalBounds);

                return typeVariableReference;
            } finally {
                typeVariablesInProgress.remove(typeDefinition); // we have finished working on it
            }
        } else if (TypeDefinition.Sort.WILDCARD == typeDefinition.getSort()) {
            Generic generic = typeDefinition.asGenericType();

            TypeList.Generic lowerBounds = generic.getLowerBounds();
            TypeList.Generic upperBounds = generic.getUpperBounds();
            ResolvedType bound = null;
            boolean isExtends = lowerBounds.size() == 0;
            if (isExtends) {
                bound = this.convertType(upperBounds.get(0));
            } else {
                bound = this.convertType(lowerBounds.get(0));
            }
            return new BoundedReferenceType((ReferenceType) bound, isExtends, this);
        } else if (TypeDefinition.Sort.GENERIC_ARRAY == typeDefinition.getSort()) {
            TypeDescription componentType = typeDefinition.asErasure().getComponentType();
            return UnresolvedType.makeArray(resolve(componentType), 1).resolve(this);
        }

        return ResolvedType.MISSING;
    }


    @Override
    public String toString() {
        return typePool.toString();
    }


    private static class ExceptionBasedMessageHandler implements IMessageHandler {

        public boolean handleMessage(IMessage message) throws AbortException {
            throw new WorldLintException(message.toString());
        }

        public boolean isIgnoring(org.aspectj.bridge.IMessage.Kind kind) {
            if (kind == IMessage.INFO) {
                return true;
            } else {
                return false;
            }
        }

        public void dontIgnore(org.aspectj.bridge.IMessage.Kind kind) {
            // empty
        }

        public void ignore(org.aspectj.bridge.IMessage.Kind kind) {
            // empty
        }
    }
}
