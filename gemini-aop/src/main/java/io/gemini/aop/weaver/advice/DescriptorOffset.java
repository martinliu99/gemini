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
package io.gemini.aop.weaver.advice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.gemini.aop.java.lang.BootstrapAdvice;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
import net.bytebuddy.asm.Advice.OffsetMapping;
import net.bytebuddy.asm.Advice.OffsetMapping.ForStackManipulation;
import net.bytebuddy.description.annotation.AnnotationDescription.Loadable;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.collection.ArrayFactory;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.utility.JavaConstant;

public interface DescriptorOffset {


    @Target( {ElementType.PARAMETER} )
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Descriptor {

    }


    abstract class AbstractBase implements OffsetMapping.Factory<Descriptor> {

        protected final String methodSignature;
        protected final MethodDescription methodDescription;


        public AbstractBase(String methodSignature, MethodDescription methodDescription) {
            this.methodSignature = methodSignature;
            this.methodDescription = methodDescription;
        }


        /*
         * @see net.bytebuddy.asm.Advice.OffsetMapping.Factory#getAnnotationType()
         */
        @Override
        public Class<Descriptor> getAnnotationType() {
            return Descriptor.class;
        }


        /**
         * @param methodSignature 
         * @param methodDescription
         * @return
         */
        protected List<JavaConstant> doGetMethodArguments(String methodSignature) {
            return Arrays.asList( 
                    JavaConstant.Simple.wrap(methodSignature) );
        }
    }


    @BootstrapClassConsumer
    class ForRegularInvocation extends AbstractBase {

        private static final Generic STRING = TypeDefinition.Sort.describe(String.class);

        private static final MethodDescription.InDefinedShape CREATE_DESCRIPTOR_METHOD
                = new MethodDescription.ForLoadedMethod( BootstrapAdvice.Bridger.createDescriptorMethod() );


        /**
         * @param methodSignature
         * @param methodDescription
         */
        public ForRegularInvocation(String methodSignature, MethodDescription methodDescription) {
            super(methodSignature, methodDescription);
        }


        /* @see net.bytebuddy.asm.Advice.OffsetMapping.Factory#make(net.bytebuddy.description.method.ParameterDescription.InDefinedShape, net.bytebuddy.description.annotation.AnnotationDescription.Loadable, net.bytebuddy.asm.Advice.OffsetMapping.Factory.AdviceType)
         */
        @Override
        public OffsetMapping make(InDefinedShape target, Loadable<Descriptor> annotation,
                AdviceType adviceType) {
            List<JavaConstant> methodArguments = doGetMethodArguments(methodSignature);

            List<StackManipulation> createDescriptorMethod = new ArrayList<StackManipulation>(1 + 1 + 1);
            createDescriptorMethod.add( MethodInvocation.lookup() );
            createDescriptorMethod.add( 
                    ArrayFactory.forType(STRING).withValues(
                            methodArguments.stream()
                            .map( arg -> arg.toStackManipulation() )
                            .collect(Collectors.toList())
                    )
            );
            createDescriptorMethod.add( MethodInvocation.invoke(CREATE_DESCRIPTOR_METHOD) );

            return new ForStackManipulation(
                    new StackManipulation.Compound(createDescriptorMethod),
                    target.getType(), 
                    target.getType(), 
                    Assigner.Typing.STATIC
            );
        }
    }


    @BootstrapClassConsumer
    class ForDynamicInvocation extends AbstractBase {

        private static final MethodDescription.InDefinedShape CREATE_DESCRIPTOR_INDY_BSM 
                = new MethodDescription.ForLoadedMethod( BootstrapAdvice.Bridger.createDescriptorIndyBSM() );


        public ForDynamicInvocation(String methodSignature, MethodDescription methodDescription) {
            super(methodSignature, methodDescription);
        }

        @Override
        public OffsetMapping make(InDefinedShape target, Loadable<Descriptor> annotation,
                AdviceType adviceType) {
            List<JavaConstant> descriptorIndyBSMArgs = doGetMethodArguments(methodSignature);

            StackManipulation createDescriptorBSM = MethodInvocation.invoke(CREATE_DESCRIPTOR_INDY_BSM).dynamic(
                    CREATE_DESCRIPTOR_INDY_BSM.getName(),
                    target.getType().asErasure(),
                    Collections.<TypeDescription>emptyList(),
                    descriptorIndyBSMArgs);

            return new ForStackManipulation(
                    createDescriptorBSM, 
                    target.getType(), 
                    target.getType(), 
                    Assigner.Typing.STATIC
            );
        }
    } 
}
