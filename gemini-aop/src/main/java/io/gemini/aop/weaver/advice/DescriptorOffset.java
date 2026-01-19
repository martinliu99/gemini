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
package io.gemini.aop.weaver.advice;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.gemini.aop.weaver.BootstrapDispatcher;
import io.gemini.core.bootstrap.BootstrapClassConsumer;
import net.bytebuddy.ClassFileVersion;
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

/**
 * byte code version lower than JDK7 does not support invoke dynamic instruction

 */
public interface DescriptorOffset {

    static OffsetMapping.Factory<Descriptor> createDescriptorOffset(
            MethodDescription targetMethod, String... arguments) {
        return ClassFileVersion.JAVA_V6.isLessThan(targetMethod.getDeclaringType().asErasure().getClassFileVersion())
                ? new DescriptorOffset.ForDynamicInvocation(targetMethod, arguments)
                : new DescriptorOffset.ForRegularInvocation(targetMethod, arguments);
    }


    @Target( {ElementType.PARAMETER} )
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Descriptor {

    }


    @BootstrapClassConsumer
    abstract class AbstractBase implements OffsetMapping.Factory<Descriptor> {

        protected static final TypeDescription BOOTSTRAP_DISPATCHER_TYPE = 
                TypeDescription.ForLoadedType.of(BootstrapDispatcher.class);


        protected final MethodDescription targetMethod;
        protected final String[] arguments;


        public AbstractBase(MethodDescription targetMethod, String[] arguments) {
            this.targetMethod = targetMethod;
            this.arguments = arguments;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public Class<Descriptor> getAnnotationType() {
            return Descriptor.class;
        }

        protected List<JavaConstant> doGetMethodArgumentJavaConstants() {
            List<JavaConstant> javaConstants = new ArrayList<>(arguments.length);
            for (String argument : arguments) {
                javaConstants.add( 
                    JavaConstant.Simple.wrap(argument) );
            }

            return javaConstants;
        }

        protected List<StackManipulation> doGetMethodArgumentStackManipulations() {
            List<StackManipulation> stackManipulations = new ArrayList<>(arguments.length);
            for (String argument : arguments) {
                stackManipulations.add( 
                    JavaConstant.Simple.wrap(argument).toStackManipulation() );
            }

            return stackManipulations;
        }
    }


    @BootstrapClassConsumer
    class ForRegularInvocation extends AbstractBase {

        private static final Generic STRING = TypeDefinition.Sort.describe(String.class);

        private static final MethodDescription GET_CREATOR_METHOD = 
                BOOTSTRAP_DISPATCHER_TYPE.getDeclaredMethods().filter( named("getCreator") ).getOnly();

        private static final MethodDescription.InDefinedShape CREATE_DESCRIPTOR_METHOD =
                TypeDescription.ForLoadedType.of(BootstrapDispatcher.Creator.class).getDeclaredMethods()
                .filter( 
                        isPublic().and(
                                named("createDescriptor").and(
                                        takesArguments(
                                                Lookup.class, Object[].class)
                                        ) 
                                )
                        )
                .getOnly();


        /**
         * 
         * @param targetMethod
         * @param arguments
         */
        public ForRegularInvocation(MethodDescription targetMethod, String... arguments) {
            super(targetMethod, arguments);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public OffsetMapping make(InDefinedShape targetParameter, Loadable<Descriptor> annotation,
                AdviceType adviceType) {
            return new ForStackManipulation(
                    new StackManipulation.Compound(
                            MethodInvocation.invoke(GET_CREATOR_METHOD),
                            MethodInvocation.lookup(),
                            ArrayFactory.forType(STRING).withValues(
                                    doGetMethodArgumentStackManipulations()),
                            MethodInvocation.invoke(CREATE_DESCRIPTOR_METHOD)
                    ),
                    targetParameter.getType(), 
                    targetParameter.getType(), 
                    Assigner.Typing.STATIC
            );
        }
    }


    @BootstrapClassConsumer
    class ForDynamicInvocation extends AbstractBase {

        private static final MethodDescription.InDefinedShape CREATE_DESCRIPTOR_INDY_BSM = 
                BOOTSTRAP_DISPATCHER_TYPE.getDeclaredMethods()
                .filter( 
                        isPublic().and(
                                named("createDescriptorCallSite").and(
                                        takesArguments(
                                                MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class)
                                        ) 
                                )
                        )
                .getOnly();


        public ForDynamicInvocation(MethodDescription targetMethod, String... arguments) {
            super(targetMethod, arguments);
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
