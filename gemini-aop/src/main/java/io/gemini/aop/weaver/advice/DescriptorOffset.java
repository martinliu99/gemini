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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
import net.bytebuddy.implementation.bytecode.member.Invokedynamic;
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


        /**
         * {@inheritDoc}
         */
        @Override
        public Class<Descriptor> getAnnotationType() {
            return Descriptor.class;
        }

        protected List<JavaConstant> doGetMethodArgumentJavaConstants() {
            return Arrays.asList( 
                    JavaConstant.Simple.wrap(methodSignature) );
        }

        protected List<StackManipulation> doGetMethodArgumentStackManipulations() {
            return Arrays.asList( 
                    JavaConstant.Simple.wrap(methodSignature).toStackManipulation() );
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

        /**
         * {@inheritDoc}
         */
        @Override
        public OffsetMapping make(InDefinedShape target, Loadable<Descriptor> annotation,
                AdviceType adviceType) {
            return new ForStackManipulation(
                    new StackManipulation.Compound(
//                            NullConstant.INSTANCE,
                            MethodInvocation.lookup(),
                            ArrayFactory.forType(STRING).withValues(
                                    doGetMethodArgumentStackManipulations()),
                            MethodInvocation.invoke(CREATE_DESCRIPTOR_METHOD)
                    ),
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

        /**
         * {@inheritDoc}
         */
        @Override
        public OffsetMapping make(InDefinedShape target, Loadable<Descriptor> annotation,
                AdviceType adviceType) {
            return new ForStackManipulation(
                    new Invokedynamic(
                            CREATE_DESCRIPTOR_INDY_BSM.getName(),
                            JavaConstant.MethodType.of(
                                    target.getType().asErasure(), 
                                    Collections.<TypeDescription>emptyList()),
                            JavaConstant.MethodHandle.of(CREATE_DESCRIPTOR_INDY_BSM),
                            doGetMethodArgumentJavaConstants()
                    ), 
                    target.getType(), 
                    target.getType(), 
                    Assigner.Typing.STATIC
            );
        }
    } 
}
