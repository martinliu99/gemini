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

import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ASM9;
import static net.bytebuddy.jar.asm.Opcodes.ASTORE;
import static net.bytebuddy.jar.asm.Opcodes.ATHROW;
import static net.bytebuddy.jar.asm.Opcodes.GOTO;
import static net.bytebuddy.jar.asm.Opcodes.IFEQ;
import static net.bytebuddy.jar.asm.Opcodes.IFNE;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.IRETURN;
import static net.bytebuddy.jar.asm.Opcodes.RETURN;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.gemini.aop.weaver.AopWeaver;
import io.gemini.aop.weaver.BootstrapDispatcher;
import io.gemini.core.bootstrap.BootstrapClassConsumer;
import io.gemini.core.loading.ClassLoadingStrategySelector;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.asm.AsmVisitorWrapper.ForDeclaredMethods.MethodVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.TypeResolutionStrategy;
import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.implementation.bytecode.constant.DefaultValue;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaConstant;

/**
 * Generates bytecode that wraps ByteBudyy framework advice methods with circularity-breaking guards.
 * <p>
 * Two wrapping strategies are provided:
 * <ul>
 *   <li>{@link #wrapMethodImplementation(Class)} – rewrites the advice method body to check
 *       {@link BootstrapDispatcher#isDispatchable()} before executing, preventing re-entrant
 *       dispatch when the advice itself triggers the same instrumented joinpoint.</li>
 *   <li>{@link #wrapMethodCall(Class)} – replaces the advice method body with an INDY call
 *       that checks dispatchability before delegating to the original advice logic.</li>
 *   <li>{@link #postProcessTargetMethod(int)} – patches the callback slot constant in the
 *       target method's INDY instruction after the advice class has been generated.</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
@BootstrapClassConsumer
public enum CircularityBreakerCodeGenerator {

    INSTANCE;


    private static final String CIRCULARITY_BREAKER_CLASSNAME = "$CircularityBreaker";

    private static final TypeDescription BOOTSTRAP_DISPATCHER_TYPE;

    private static final MethodDescription IS_DISPATCHABLE_METHOD;
    private static final MethodDescription DISABLE_DISPATCH_METHOD;
    private static final MethodDescription ENABLE_DISPATCH_METHOD;


    static {
        BOOTSTRAP_DISPATCHER_TYPE = TypeDescription.ForLoadedType.of(BootstrapDispatcher.class);

        IS_DISPATCHABLE_METHOD = BOOTSTRAP_DISPATCHER_TYPE.getDeclaredMethods()
                .filter( 
                        isStatic()
                        .and( isPublic() )
                        .and( named("isDispatchable") )
                        .and( returns(boolean.class) )
                )
                .getOnly();
        DISABLE_DISPATCH_METHOD = BOOTSTRAP_DISPATCHER_TYPE.getDeclaredMethods()
                .filter( 
                        isStatic()
                        .and( isPublic() )
                        .and( named("disableDispatch") )
                        .and( returns( void.class) )
                )
                .getOnly();
        ENABLE_DISPATCH_METHOD = BOOTSTRAP_DISPATCHER_TYPE.getDeclaredMethods()
                .filter( 
                        isStatic()
                        .and( isPublic() )
                        .and( named("enableDispatch") )
                        .and( returns( void.class) )
                )
                .getOnly();
    }


    /**
     * Wraps the advice method implementation with a dispatchability guard.
     * The generated class checks {@link BootstrapDispatcher#isDispatchable()} at entry
     * and disables/re-enables dispatch around the original method body.
     *
     * @param adviceClass the original framework advice class to wrap
     * @return a loaded dynamic type containing the wrapped advice class
     */
    public DynamicType.Loaded<?> wrapMethodImplementation(Class<?> adviceClass) {
        return new ByteBuddy()
                .redefine(adviceClass)
                .name(adviceClass.getName() + CIRCULARITY_BREAKER_CLASSNAME)
                .visit(
                        new AsmVisitorWrapper.ForDeclaredMethods()
                        .readerFlags(ClassReader.EXPAND_FRAMES)
                        .writerFlags(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
                        .invokable(
                                adviceMethodMatcher(adviceClass), 
                                MethodImplementationWrapper.INSTANCE
                        ) 
                )
                .make(TypeResolutionStrategy.Lazy.INSTANCE)
                .load(adviceClass.getClassLoader(), ClassLoadingStrategySelector.Default.SINGLETON.select(adviceClass));
    }


    /**
     * Wraps the advice method call with a dispatchability guard using an INDY-based delegation.
     * The generated class replaces the method body with an INDY call that checks
     * {@link BootstrapDispatcher#isDispatchable()} before invoking the original advice logic.
     *
     * @param adviceClass the original ByteBuddy advice class to wrap
     * @return a loaded dynamic type containing the wrapped advice class
     */
    public DynamicType.Loaded<?> wrapMethodCall(Class<?> adviceClass) {
        return new ByteBuddy()
                .redefine(adviceClass)
                .name(adviceClass.getName() + CIRCULARITY_BREAKER_CLASSNAME)
                .visit(
                        new AsmVisitorWrapper.ForDeclaredMethods()
                        .readerFlags(ClassReader.EXPAND_FRAMES)
                        .writerFlags(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
                        .invokable(
                                adviceMethodMatcher(adviceClass),
                                MethodCallWrapper.INSTANCE
                        )
                )
                .make(TypeResolutionStrategy.Lazy.INSTANCE)
                .load(adviceClass.getClassLoader(), ClassLoadingStrategySelector.Default.SINGLETON.select(adviceClass))
                ;
    }


    private static ElementMatcher<MethodDescription> adviceMethodMatcher(Class<?> adviceClass) {
        List<ElementMatcher<? super MethodDescription>> adviceMethodMatchers = new ArrayList<>(2);
        for (Method method : adviceClass.getDeclaredMethods()) {
            if (method.getDeclaredAnnotation(OnMethodEnter.class) == null
                    && method.getDeclaredAnnotation(OnMethodExit.class) == null)
                continue;

            adviceMethodMatchers.add(
                    named( method.getName() )
                    .and( takesArguments( method.getParameterTypes() ) )
                    .and( returns( method.getReturnType() ) )
            );
        }
        return new ElementMatcher.Junction.Disjunction<MethodDescription>(adviceMethodMatchers);
    }


    /**
     * Returns a {@link MethodVisitorWrapper} that patches the callback slot constant
     * in the target method's INDY instruction to the given {@code callbackSlot} value.
     * Called after the advice class has been generated to bind the correct slot index.
     *
     * @param callbackSlot the callback slot index assigned to this advisor weaver
     * @return a method visitor wrapper that patches the INDY bootstrap argument
     */
    public MethodVisitorWrapper postProcessTargetMethod(int callbackSlot) {
        return new TargetMethodWrapper(callbackSlot);
    }


    /**
     * {@link MethodVisitorWrapper} that rewrites the advice method body to check
     * {@link io.gemini.aop.weaver.BootstrapDispatcher#isDispatchable()} and
     * disable/re-enable dispatch around the original method body.
     */
    static enum MethodImplementationWrapper implements MethodVisitorWrapper {

        INSTANCE;


        /**
         *  {@inheritDoc}
         */
        @Override
        public MethodVisitor wrap(TypeDescription instrumentedType, MethodDescription instrumentedMethod,
                MethodVisitor methodVisitor, Context implementationContext, TypePool typePool, int writerFlags,
                int readerFlags) {

            return new MethodImplementationVisitor(methodVisitor, instrumentedMethod);
        }


        /**
         * ASM {@link MethodVisitor} that inserts dispatchability guard code at the
         * beginning of the advice method and re-enables dispatch before each return.
         */
        static class MethodImplementationVisitor extends MethodVisitor {

            private final MethodDescription instrumentedMethod;

            private final Label methodEnter = new Label();
            private final Label methodExit = new Label();

            private final List<LocalVariableHolder> localVariables = new ArrayList<>();


            public MethodImplementationVisitor(MethodVisitor methodVisitor, MethodDescription instrumentedMethod) {
                super(ASM9, methodVisitor);

                this.instrumentedMethod = instrumentedMethod;
            }


            /**
             *  {@inheritDoc}
             */
            @Override
            public void visitCode() {
                // enter origin method body
                super.visitCode();

                MethodVisitor methodVisitor = this.getDelegate();

                Label ifTrue = new Label();

                visitLabel(methodEnter);

                // 1.check whether is dispatchable before disabling dispatch
                visitMethodInsn(
                        INVOKESTATIC,
                        BOOTSTRAP_DISPATCHER_TYPE.getInternalName(), 
                        IS_DISPATCHABLE_METHOD.getName(),
                        IS_DISPATCHABLE_METHOD.getDescriptor(),
                        false
                );

                visitJumpInsn(IFNE, ifTrue);

                DefaultValue.of(instrumentedMethod.getReturnType()).apply(methodVisitor, null);
                MethodReturn.of(instrumentedMethod.getReturnType()).apply(methodVisitor, null);


                visitLabel(ifTrue);

                visitMethodInsn(
                        INVOKESTATIC,
                        BOOTSTRAP_DISPATCHER_TYPE.getInternalName(), 
                        DISABLE_DISPATCH_METHOD.getName(),
                        DISABLE_DISPATCH_METHOD.getDescriptor(),
                        false
                );
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void visitInsn(int opcode) {
                if (opcode >= IRETURN && opcode <= RETURN) {
                    visitMethodInsn(
                            INVOKESTATIC,
                            BOOTSTRAP_DISPATCHER_TYPE.getInternalName(), 
                            ENABLE_DISPATCH_METHOD.getName(),
                            ENABLE_DISPATCH_METHOD.getDescriptor(),
                            false
                    );
                }

                super.visitInsn(opcode);
            }

            /** 
             * {@inheritDoc}
             */
            @Override
            public void visitLocalVariable(String name, String descriptor, String signature, 
                    Label start, Label end,
                    int index) {
                localVariables.add(
                        new LocalVariableHolder(name, descriptor, signature, index) );
            }

            /**
             *  {@inheritDoc}
             */
            @Override
            public void visitEnd() {
                visitLabel(methodExit);

                // adjust local variable table.
                for (LocalVariableHolder localVariable : localVariables)
                    super.visitLocalVariable(
                            localVariable.name, 
                            localVariable.descriptor, localVariable.signature, 
                            methodEnter, methodExit,
                            localVariable.index
                    );

                super.visitEnd();
            }
        }


        /**
         * Holds local variable metadata for re-emission in {@code visitEnd}.
         */
        static class LocalVariableHolder {

            public final String name;
            public final String descriptor;
            public final String signature;
            public final int index;


            public LocalVariableHolder(String name, String descriptor, String signature, int index) {
                super();
                this.name = name;
                this.descriptor = descriptor;
                this.signature = signature;
                this.index = index;
            }
        }
    }


    /**
     * {@link MethodVisitorWrapper} that replaces the advice method body with an INDY call
     * that checks dispatchability before delegating to the original advice logic.
     */
    static enum MethodCallWrapper implements MethodVisitorWrapper {

        INSTANCE;


        /**
         *  {@inheritDoc}
         */
        @Override
        public MethodVisitor wrap(TypeDescription instrumentedType, MethodDescription instrumentedMethod,
                MethodVisitor methodVisitor, Context implementationContext, TypePool typePool, int writerFlags,
                int readerFlags) {

            return new MethodCallVisitor(methodVisitor, instrumentedMethod);
        }


        /**
         * ASM {@link MethodVisitor} that replaces the method body with a dispatchability
         * check followed by an INDY call to the original advice logic.
         */
        static class MethodCallVisitor extends MethodVisitor {

            private static final String ON_METHOD_ENTER_DESCRIPTOR = TypeDescription.ForLoadedType.of(OnMethodEnter.class).getDescriptor();
            private static final String ON_METHOD_EXIT_DESCRIPTOR = TypeDescription.ForLoadedType.of(OnMethodExit.class).getDescriptor();

            private final MethodDescription instrumentedMethod;


            public MethodCallVisitor(MethodVisitor methodVisitor, MethodDescription instrumentedMethod) {
                super(ASM9, methodVisitor);

                this.instrumentedMethod = instrumentedMethod;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                AnnotationVisitor annotationVisitor =  super.visitAnnotation(descriptor, visible);
                if (descriptor.equals(ON_METHOD_ENTER_DESCRIPTOR) 
                        || descriptor.equals(ON_METHOD_EXIT_DESCRIPTOR))
                    return new AnnotationAttributeModifier(annotationVisitor);

                return annotationVisitor;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public void visitCode() {
                MethodVisitor methodVisitor = this.mv;

                // ignore existing method body
                this.mv = null; 

                methodVisitor.visitCode();

                Label ifFalse = new Label();
                Label tryStart = new Label();
                Label tryEnd = new Label();
                Label handlerStart = new Label();
                Label finallyStart = new Label();


                // 1.check whether is dispatchable before entering try block
                methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        BOOTSTRAP_DISPATCHER_TYPE.getInternalName(), 
                        IS_DISPATCHABLE_METHOD.getName(),
                        IS_DISPATCHABLE_METHOD.getDescriptor(),
                        false
                );

                methodVisitor.visitJumpInsn(IFEQ, ifFalse);


                // 2.enter try block
                methodVisitor.visitLabel(tryStart);

                methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        BOOTSTRAP_DISPATCHER_TYPE.getInternalName(), 
                        DISABLE_DISPATCH_METHOD.getName(),
                        DISABLE_DISPATCH_METHOD.getDescriptor(),
                        false
                );


                // invoke advice method via INDY
                MethodVariableAccess.allArgumentsOf(instrumentedMethod).apply(methodVisitor, null);

                MethodInvocation.invoke(AopWeaver.BOOTSTRAP_DISPATCHER_CALLBACK_METHOD).dynamic(
                        instrumentedMethod.getName(), 
                        instrumentedMethod.getReturnType().asErasure(), 
                        instrumentedMethod.getParameters().asTypeList().asErasures(), 
                        Arrays.asList(
                                JavaConstant.Simple.wrap("callbackSlotPlaceholder"),
                                JavaConstant.Simple.wrap(instrumentedMethod.getDeclaringType().getTypeName().replace(CIRCULARITY_BREAKER_CLASSNAME, ""))

                        )
                )
                .apply(methodVisitor, null);

                methodVisitor.visitJumpInsn(GOTO, finallyStart);

                // exit try block
                methodVisitor.visitLabel(tryEnd);


                // 3.enter exception handler block
                methodVisitor.visitLabel(handlerStart);

                int exceptionVar = instrumentedMethod.getStackSize();
                methodVisitor.visitVarInsn(ASTORE, exceptionVar);

                methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        BOOTSTRAP_DISPATCHER_TYPE.getInternalName(), 
                        ENABLE_DISPATCH_METHOD.getName(),
                        ENABLE_DISPATCH_METHOD.getDescriptor(),
                        false
                );

                methodVisitor.visitVarInsn(ALOAD, exceptionVar);
                methodVisitor.visitInsn(ATHROW);


                // 4.enter finally block
                methodVisitor.visitLabel(finallyStart);

                methodVisitor.visitMethodInsn(
                        INVOKESTATIC,
                        BOOTSTRAP_DISPATCHER_TYPE.getInternalName(), 
                        ENABLE_DISPATCH_METHOD.getName(),
                        ENABLE_DISPATCH_METHOD.getDescriptor(),
                        false
                );


                // 5.return
                methodVisitor.visitLabel(ifFalse);

                DefaultValue.of(instrumentedMethod.getReturnType()).apply(methodVisitor, null);
                MethodReturn.of(instrumentedMethod.getReturnType()).apply(methodVisitor, null);

                methodVisitor.visitTryCatchBlock(tryStart, tryEnd, handlerStart, null);

                methodVisitor.visitMaxs(0, 0);
                methodVisitor.visitEnd();
            }

        }


        /**
         * ASM {@link AnnotationVisitor} that forces {@code inline = true} on
         * {@code @OnMethodEnter} and {@code @OnMethodExit} annotations.
         */
        static class AnnotationAttributeModifier extends AnnotationVisitor {

            public AnnotationAttributeModifier(AnnotationVisitor annotationVisitor) {
                super(ASM9, annotationVisitor);
            }

            @Override
            public void visit(String name, Object value) {
                super.visit(name, name.equals("inline") ? true : value);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                return new AnnotationAttributeModifier(
                        super.visitAnnotation(name, descriptor) );
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return new AnnotationAttributeModifier(
                        super.visitArray(name) );
            }
        }
    }


    /**
     * {@link MethodVisitorWrapper} that patches the callback slot constant in the
     * target method's INDY instruction after the advice class has been generated.
     */
    static class TargetMethodWrapper implements MethodVisitorWrapper {

        private final int callbackSlot;


        public TargetMethodWrapper(int callbackSlot) {
            this.callbackSlot = callbackSlot;
        }


        /**
         *  {@inheritDoc}
         */
        @Override
        public MethodVisitor wrap(TypeDescription instrumentedType, MethodDescription instrumentedMethod,
                MethodVisitor methodVisitor, Context implementationContext, TypePool typePool, int writerFlags,
                int readerFlags) {

            return new TargetMethodVisitor(methodVisitor, callbackSlot);
        }


        /**
         * ASM {@link MethodVisitor} that patches the callback slot constant in the
         * INDY bootstrap arguments of the target method.
         */
        static class TargetMethodVisitor extends MethodVisitor {

            private final int callbackSlot;


            public TargetMethodVisitor(MethodVisitor methodVisitor, int callbackSlot) {
                super(ASM9, methodVisitor);

                this.callbackSlot = callbackSlot;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                    Object... bootstrapMethodArguments) {
                if (bootstrapMethodHandle.getName().equals(AopWeaver.BOOTSTRAP_DISPATCHER_CALLBACK_METHOD.getName())
                        && bootstrapMethodHandle.getOwner().equals(BOOTSTRAP_DISPATCHER_TYPE.getInternalName())) {
                    bootstrapMethodArguments[0] = callbackSlot;
                }

                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }
    }
}
