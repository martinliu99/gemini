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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.gemini.aop.weaver.AopWeaver;
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
import net.bytebuddy.implementation.bytecode.constant.NullConstant;
import net.bytebuddy.implementation.bytecode.member.Invokedynamic;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.utility.JavaConstant;

/**
 * ByteBuddy {@link OffsetMapping.Factory} that injects the {@link io.gemini.aop.weaver.Joinpoints.Descriptor}
 * (or a {@link java.lang.invoke.CallSite} wrapping it) into framework advice methods.
 * <p>
 * For JDK 7+ targets, uses {@code invokedynamic} ({@link ForDynamicInvocation}) for efficient
 * call-site caching. For JDK 6 targets, falls back to a regular static method invocation
 * ({@link ForRegularInvocation}).
 * </p>
 *
 * @author   martin.liu
 */
public interface DescriptorOffset {

    int CALLSITE_DESCRIPTOR_FLAG = 1;


    static OffsetMapping.Factory<Descriptor> create(MethodDescription targetMethod, Object... arguments) {
        boolean greatThanJDK6 = ClassFileVersion.JAVA_V6.isLessThan(targetMethod.getDeclaringType().asErasure().getClassFileVersion());
        Object[] args = new Object[arguments.length + 1];
        System.arraycopy(arguments, 0, args, 0, arguments.length);
        args[arguments.length] = greatThanJDK6 ? CALLSITE_DESCRIPTOR_FLAG : 0;

        return greatThanJDK6
                ? new DescriptorOffset.ForDynamicInvocation(targetMethod, args)
                : new DescriptorOffset.ForRegularInvocation(targetMethod, args);
    }


    @Target( {ElementType.PARAMETER} )
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Descriptor {

    }


    /**
     * Abstract base for {@link net.bytebuddy.asm.Advice.OffsetMapping.Factory} implementations
     * that inject the joinpoint descriptor into advice methods.
     */
    abstract class AbstractBase implements OffsetMapping.Factory<Descriptor> {

        protected static final Generic OBJECT_TYPE = TypeDefinition.Sort.describe(Object.class);


        protected final MethodDescription targetMethod;
        protected final Object[] arguments;


        public AbstractBase(MethodDescription targetMethod, Object[] arguments) {
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
            for (Object argument : arguments) {
                javaConstants.add( 
                        JavaConstant.Simple.wrap(argument) );
            }

            return javaConstants;
        }

        protected List<StackManipulation> doGetMethodArgumentStackManipulations() {
            List<StackManipulation> stackManipulations = new ArrayList<>(arguments.length);
            for (Object argument : arguments) {
                StackManipulation stackManipulation = JavaConstant.Simple.wrap(argument).toStackManipulation();
                if (argument == null) {
                    stackManipulations.add( stackManipulation );
                    continue;
                }

                TypeDescription argumentType = TypeDescription.ForLoadedType.of( argument.getClass() );
                if (argumentType.isPrimitiveWrapper()) {
                    stackManipulation = new StackManipulation.Compound(
                            stackManipulation,
                            Assigner.DEFAULT.assign(argumentType.asUnboxed().asGenericType(), argumentType.asGenericType(), Assigner.Typing.DYNAMIC)
                    );
                }

                stackManipulations.add( stackManipulation );
            }

            return stackManipulations;
        }
    }


    /**
     * Uses a regular static method invocation (for JDK 6 targets) to inject the
     * {@link io.gemini.aop.weaver.Joinpoints.Descriptor} into the advice method.
     */
    class ForRegularInvocation extends AbstractBase {

        public ForRegularInvocation(MethodDescription targetMethod, Object... arguments) {
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
                            MethodInvocation.lookup(),
                            NullConstant.INSTANCE,
                            NullConstant.INSTANCE,
                            ArrayFactory.forType(OBJECT_TYPE).withValues(
                                    doGetMethodArgumentStackManipulations()),
                            MethodInvocation.invoke(AopWeaver.BOOTSTRAP_DISPATCHER_CALLBACK_METHOD)
                    ),
                    targetParameter.getType(), 
                    targetParameter.getType(), 
                    Assigner.Typing.STATIC
            );
        }
    }


    /**
     * Uses {@code invokedynamic} (for JDK 7+ targets) to inject the
     * {@link io.gemini.aop.weaver.Joinpoints.Descriptor} via a cached {@link java.lang.invoke.CallSite}.
     */
    class ForDynamicInvocation extends AbstractBase {

        public ForDynamicInvocation(MethodDescription targetMethod, Object... arguments) {
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
                            AopWeaver.BOOTSTRAP_DISPATCHER_CALLBACK_METHOD.getName(),
                            JavaConstant.MethodType.of(
                                    target.getType().asErasure(), 
                                    Collections.<TypeDescription>emptyList()),
                            JavaConstant.MethodHandle.of(AopWeaver.BOOTSTRAP_DISPATCHER_CALLBACK_METHOD),
                            doGetMethodArgumentJavaConstants()
                    ),
                    target.getType(), 
                    target.getType(), 
                    Assigner.Typing.STATIC
            );
        }
    } 
}
