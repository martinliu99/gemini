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
package io.gemini.aspectj.weaver;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.World;

import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface TypeWorld {

    TypeWorld EMPTY_WORLD = new TypeWorld.LazyFacade( new BytebuddyWorld(TypePool.Empty.INSTANCE, null) );


    World getWorld();

    ResolvedType resolve(String typeName);

    ResolvedType resolve(TypeDescription typeDescription);

    TypeDescription describeType(String typeName);


    ResolvedMember resolve(Member member);

    Shadow makeShadow(Member member);


    class WorldLintException extends RuntimeException {

        private static final long serialVersionUID = 816600136638029684L;

        public WorldLintException(String message) {
            super(message);
        }
    }


    abstract class WithDelegation implements TypeWorld {

        private final TypeWorld delegate;


        public WithDelegation(TypeWorld typeWorld) {
            this.delegate = typeWorld;
        }

        protected TypeWorld getDelegate() {
            return this.delegate;
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        public World getWorld() {
            return getDelegate().getWorld();
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public ResolvedType resolve(String typeName) {
            return getDelegate().resolve(typeName);
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public ResolvedType resolve(TypeDescription typeDescription) {
            return getDelegate().resolve(typeDescription);
        }

        /**
         * {@inheritDoc}
         */
        public TypeDescription describeType(String typeName) {
            return getDelegate().describeType(typeName);
        }


        /**
         * {@inheritDoc}
         */
        public ResolvedMember resolve(Member member) {
            return getDelegate().resolve(member);
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        public Shadow makeShadow(Member member) {
            return getDelegate().makeShadow(member);
        }


        @Override
        public String toString() {
            return getClass().getSimpleName() + "-" + getDelegate().toString();
        }
    }


    public static class LazyFacade extends WithDelegation {

        /**
         * @param typeWorld
         */
        public LazyFacade(TypeWorld typeWorld) {
            super(typeWorld);
        }

        public ResolvedType resolve(TypeDescription typeDescription) {
            if (typeDescription == null)
                return null;

            ResolvedType resolvedType = getDelegate().getWorld().getTypeMap().get(typeDescription.getTypeName());
            return resolvedType != null ? resolvedType : new ReferenceTypes.LazyFacade(typeDescription, this.getDelegate());
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public ResolvedType resolve(String typeName) {
            ResolvedType resolvedType = getDelegate().getWorld().getTypeMap().get(typeName);
            return resolvedType != null ? resolvedType : new ReferenceTypes.LazyFacade(typeName, this.getDelegate());
        }
    }


    public static class CacheResolutionFacade extends WithDelegation {

        private final ConcurrentMap<TypeDescription, Resolution> resolutionCache = new ConcurrentHashMap<>();


        /**
         * @param typeWorld
         */
        public CacheResolutionFacade(TypeWorld typeWorld) {
            super(typeWorld);
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public ResolvedType resolve(TypeDescription typeDescription) {
            if (typeDescription == null) return null;

            return doResolve(typeDescription).resolvedType;
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        public Shadow makeShadow(Member member) {
            if (member == null) return null;

            return doResolve(member.getDeclaringType().asErasure()).shadowCache.computeIfAbsent(
                    member, 
                    key -> getDelegate().makeShadow(key)
            );
        }

        protected Resolution doResolve(TypeDescription typeDescription) {
            return resolutionCache.computeIfAbsent(
                    typeDescription, 
                    key -> new Resolution(
                            getDelegate().resolve(key) )
            );
        }


        public void releaseCache(TypeDefinition typeDefinition) {
            if (typeDefinition == null) return;

            resolutionCache.remove(typeDefinition);
        }


        private static class Resolution {

            final ResolvedType resolvedType;
            ConcurrentMap<Member, Shadow> shadowCache;

            public Resolution(ResolvedType resolvedType) {
                this.resolvedType = resolvedType;
                this.shadowCache = new ConcurrentHashMap<>();
            }
        }
    }


    public static class LocalWorld extends WithDelegation {

        private final TypePool typePool;

        /**
         * @param typeWorld
         */
        public LocalWorld(TypeWorld typeWorld, TypePool typePool) {
            super(typeWorld);
            this.typePool = typePool;
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public ResolvedType resolve(String typeName) {
            // TODO: check existence?
            typePool.describe(typeName).resolve();
            return getDelegate().resolve(typeName);
        }
    }
}
