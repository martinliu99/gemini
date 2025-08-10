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

import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.World;

import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface TypeWorld {

    World getWorld();

    ResolvedType resolve(String typeName);

    ResolvedType resolve(TypeDescription typeDescription);

    ReferenceType getReferenceType(String typeName);


    Shadow makeShadow(Member member);


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
        public ResolvedType resolve(TypeDescription typeDescription) {
            return getDelegate().resolve(typeDescription);
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
        public ReferenceType getReferenceType(String typeName) {
            return getDelegate().getReferenceType(typeName);
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
            if(typeDescription == null)
                return null;

            ReferenceType resolvedType = getDelegate().getReferenceType(typeDescription.getTypeName());
            return resolvedType != null ? resolvedType : new DelegatedReferenceType.LazyFacade(typeDescription, this.getDelegate());
        }

    }

    public static class CacheResolutionFacade extends WithDelegation {

        private final ConcurrentMap<TypeDescription, ResolvedType> resolvedTypeCache = new ConcurrentHashMap<>();
        private final ConcurrentMap<Member, Shadow> shadowCache = new ConcurrentHashMap<>();


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
            if(typeDescription == null) return null;

            return resolvedTypeCache.computeIfAbsent(
                    typeDescription, 
                    key -> getDelegate().resolve(typeDescription)
            );
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        public Shadow makeShadow(Member member) {
            if(member == null) return null;

            return this.shadowCache.computeIfAbsent(
                    member, 
                    key -> getDelegate().makeShadow(key)
            );
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
