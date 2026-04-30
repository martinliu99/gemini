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
package io.gemini.aspectj.weaver;

import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ReferenceTypeDelegate;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;

import net.bytebuddy.description.type.TypeDescription;


/**
 * Provides AspectJ {@link ReferenceType} implementations that lazily delegate to
 * a resolved type from the Gemini AOP framework {@link TypeWorld}.
 * <p>
 * {@link Facade} wraps an already-resolved {@link ReferenceType}.
 * {@link LazyFacade} defers resolution until the delegate is first accessed,
 * avoiding eager class loading during type scanning.
 * </p>
 *
 * @author   martin.liu
 */
public interface ReferenceTypes {

    /**
     * Abstract base providing common logic: delegated reference type resolving,
     * instance equality testing.
     */
    abstract class WithDelegation extends ReferenceType {

        private ReferenceType delegateReferenceType;


        public WithDelegation(String signature, World world) {
            super(signature, world);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isGenericType() {
            return false;
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public ResolvedType resolve(World world) {
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ReferenceTypeDelegate getDelegate() {
            if (super.getDelegate() == null) {
                // initialize delegate with lazily resolved referenceType
                super.setDelegate(getDelegateReferenceType().getDelegate());
            }

            return super.getDelegate();
        }


        private ReferenceType getDelegateReferenceType() {
            if (delegateReferenceType == null) {
                delegateReferenceType = this.doResolveDelegateReferenceType();
            }
            return delegateReferenceType;
        }

        protected void setDelegateReferenceType(ReferenceType delegateReferenceType) {
            this.delegateReferenceType = delegateReferenceType;
        }

        protected ReferenceType doResolveDelegateReferenceType() {
            return null;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object other) {
            if (other instanceof ResolvedType) {
                ResolvedType resolvedType= (ResolvedType) other;

                // quickly compare signature
                if (this.getSignature().equals(resolvedType.getSignature()) == false)
                    return false;

                if (other instanceof WithDelegation)
                    // fetch delegatee
                    other = ((WithDelegation) other).getDelegateReferenceType();
            }

            return this.getDelegateReferenceType().equals(other);
        }
    }


    /**
     * Wraps an already-resolved {@link ReferenceType}.
     */
    class Facade extends WithDelegation {

        /**
         * Creates a {@code Facade} that immediately delegates to the given {@link ReferenceType}.
         *
         * @param referenceType the already-resolved reference type to wrap
         * @param world         the AspectJ world
         */
        public Facade(ReferenceType referenceType, World world) {
            super(referenceType.getSignature(), world);

            setDelegateReferenceType(referenceType);
        }
    }


    /**
     * Defers resolution until the delegate is first accessed, avoiding eager class loading during type scanning.
     */
    class LazyFacade extends WithDelegation {

        private final String typeName;
        private final TypeDescription typeDescription;
        private final TypeWorld typeWorld;


        /**
         * Creates a {@code LazyFacade} that resolves the type by name on first access.
         *
         * @param typeName  the fully-qualified type name to resolve lazily
         * @param typeWorld the type world used for lazy resolution
         */
        public LazyFacade(String typeName, TypeWorld typeWorld) {
            super(UnresolvedType.forName(typeName).getSignature(), typeWorld.getWorld());

            this.typeName = typeName;
            this.typeDescription = null;
            this.typeWorld = typeWorld;
        }

        /**
         * Creates a {@code LazyFacade} that resolves the type from a {@link TypeDescription} on first access.
         *
         * @param typeDescription the ByteBuddy type description to resolve lazily
         * @param typeWorld       the type world used for lazy resolution
         */
        public LazyFacade(TypeDescription typeDescription, TypeWorld typeWorld) {
            super(typeDescription.getDescriptor(), typeWorld.getWorld());

            this.typeName = typeDescription.getTypeName();
            this.typeDescription = typeDescription;
            this.typeWorld = typeWorld;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected ReferenceType doResolveDelegateReferenceType() {
            if (typeDescription != null)
                return (ReferenceType) typeWorld.resolve(typeDescription);
            else
                return (ReferenceType) typeWorld.resolve(typeName);
        }
    }
}
