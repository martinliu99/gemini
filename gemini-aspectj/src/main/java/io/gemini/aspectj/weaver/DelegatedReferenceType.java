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
import org.aspectj.weaver.World;

import net.bytebuddy.description.type.TypeDescription;


public abstract class DelegatedReferenceType extends ReferenceType {

    private ReferenceType referenceType;


    public DelegatedReferenceType(String signature, World world) {
        super(signature, world);
    }

    @Override
    public ReferenceTypeDelegate getDelegate() {
        if(super.getDelegate() == null) {
            // initialize delegate with lazily resolved referenceType
            super.setDelegate(doResolveReferenceType().getDelegate());
        }

        return super.getDelegate();
    }

    ReferenceType getResolvedType() {
        if(referenceType == null) {
            referenceType = this.doResolveReferenceType();
        }
        return referenceType;
    }

    protected abstract ReferenceType doResolveReferenceType();

    @Override
    public int hashCode() {
        return this.getResolvedType().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof DelegatedReferenceType)
            obj = ((DelegatedReferenceType) obj).doResolveReferenceType();

        return this.getResolvedType().equals(obj);
    }



    public static class Facade extends DelegatedReferenceType {

        private final ReferenceType referenceType;

        /**
         * @param signature
         * @param world
         */
        public Facade(ReferenceType referenceType, World world) {
            super(referenceType.getSignature(), world);

            this.referenceType = referenceType;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected ReferenceType doResolveReferenceType() {
            return referenceType;
        }
    }


    public static class LazyFacade extends DelegatedReferenceType {

        private final TypeDescription typeDescription;
        private final TypeWorld typeWorld;


        /**
         * @param signature
         * @param typeWorld
         */
        public LazyFacade(TypeDescription typeDescription, TypeWorld typeWorld) {
            super(typeDescription.getDescriptor(), typeWorld.getWorld());

            this.typeDescription = typeDescription;
            this.typeWorld = typeWorld;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        protected ReferenceType doResolveReferenceType() {
            return (ReferenceType) typeWorld.resolve(typeDescription);
        }
    }
}
