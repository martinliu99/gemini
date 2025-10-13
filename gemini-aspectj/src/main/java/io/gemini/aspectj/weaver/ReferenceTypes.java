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

import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ReferenceTypeDelegate;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;

import net.bytebuddy.description.type.TypeDescription;


public interface ReferenceTypes {

    abstract class WithDelegation extends ReferenceType {

        private ReferenceType delegateReferenceType;


        public WithDelegation(String signature, World world) {
            super(signature, world);
        }

        @Override
        public boolean isGenericType() {
            return false;
        }

        /** {@inheritDoc} 
         */
        public ResolvedType resolve(World world) {
            return this;
        }

        @Override
        public ReferenceTypeDelegate getDelegate() {
            if(super.getDelegate() == null) {
                // initialize delegate with lazily resolved referenceType
                super.setDelegate(getDelegateReferenceType().getDelegate());
            }

            return super.getDelegate();
        }


        private ReferenceType getDelegateReferenceType() {
            if(delegateReferenceType == null) {
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


        @Override
        public int hashCode() {
            return this.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof ResolvedType) {
                ResolvedType resolvedType= (ResolvedType) other;

                // quickly compare signature
                if(this.getSignature().equals(resolvedType.getSignature()) == false)
                    return false;

                if(other instanceof WithDelegation)
                    // fetch delegatee
                    other = ((WithDelegation) other).getDelegateReferenceType();
            }

            return this.getDelegateReferenceType().equals(other);
        }
    }


    class Facade extends WithDelegation {

        /**
         * @param signature
         * @param world
         */
        public Facade(ReferenceType referenceType, World world) {
            super(referenceType.getSignature(), world);

            setDelegateReferenceType(referenceType);
        }
    }


    class LazyFacade extends WithDelegation {

        private final String typeName;
        private final TypeDescription typeDescription;
        private final TypeWorld typeWorld;


        public LazyFacade(String typeName, TypeWorld typeWorld) {
            super(UnresolvedType.forName(typeName).getSignature(), typeWorld.getWorld());

            this.typeName = typeName;
            this.typeDescription = null;
            this.typeWorld = typeWorld;
        }

        /**
         * @param signature
         * @param typeWorld
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
            if(typeDescription != null)
                return (ReferenceType) typeWorld.resolve(typeDescription);
            else
                return (ReferenceType) typeWorld.resolve(typeName);
        }
    }
}
