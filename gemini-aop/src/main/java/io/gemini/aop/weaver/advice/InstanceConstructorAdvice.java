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

import static io.gemini.aop.weaver.BootstrapDispatcher.VAR_ADVICE_DISPATCHER;
import static io.gemini.aop.weaver.BootstrapDispatcher.getDelegator;

import io.gemini.aop.weaver.BootstrapDispatcher.Dispatcher;
import io.gemini.core.bootstrap.BootstrapClassConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * Framework ByteBuddy {@code @Advice} class for instance constructors.
 * <p>
 * Intercepts constructor invocations, passes arguments to before-advice, and
 * passes the newly created {@code this} reference to after-advice.
 * Note: exception catching in constructor exit advice is not supported by ByteBuddy.
 * </p>
 *
 * @author   martin.liu
 */
@BootstrapClassConsumer
public class InstanceConstructorAdvice {

    /**
     * Executed before the constructor body.
     * Creates a {@link Dispatcher}, invokes before-advice (which may modify arguments),
     * and throws if advice sets a throw value.
     *
     * @param descriptor  the cached joinpoint descriptor (injected via INDY)
     * @param arguments   the constructor arguments (may be replaced by advice)
     * @param dispatcher  thread-local dispatcher (injected via {@code @Advice.Local})
     * @throws Throwable if before-advice throws or sets a throw value
     */
    @Advice.OnMethodEnter(inline = true, prependLineNumber = true)
    public static void beforeConstructor(
            @DescriptorOffset.Descriptor Object descriptor,
            @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] arguments,
            @Advice.Local(value = VAR_ADVICE_DISPATCHER) Dispatcher<Object, Throwable> dispatcher
            ) throws Throwable {
        // 1.create dispatcher
        dispatcher = getDelegator().createDispacther(descriptor, null, arguments);
        if (dispatcher == null)
            // ignore instrumentation and execute instrumented method
            return;

        // 2.invoke BeforeAdvices
        dispatcher.dispatch();

        // check invocation result
        if (dispatcher.hasAdviceThrowing())
            throw dispatcher.getAdviceThrowing();

        // 3.replace arguments
        arguments = dispatcher.getArguments();
    }


    /**
     * Executed after the constructor body completes.
     * Passes the newly created {@code this} reference to after-advice as the "returning" value.
     * Exception catching is not supported for constructors in ByteBuddy.
     *
     * @param targetObject the newly constructed instance ({@code this})
     * @param dispatcher   the dispatcher created in {@link #beforeConstructor}
     * @throws Throwable if after-advice sets a throw value
     */
    // refer to https://github.com/raphw/byte-buddy/issues/375
    // Advice.Thrown and try-catch is not allowed for constructor at this time
    @Advice.OnMethodExit(/* onThrowable = Throwable.class */ inline = true)
    public static void afterConstructor(
            @Advice.This(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object targetObject,
//            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returning,
//            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable throwing,
            @Advice.Local(value = VAR_ADVICE_DISPATCHER) Dispatcher<Object, Throwable> dispatcher
            ) throws Throwable {
        if (dispatcher == null)
            return;

        // 1.set actual returning of target method
        dispatcher.setThrowing(null);       // could not catch exception for constructor
        dispatcher.setReturning(targetObject);

        // 2.invoke AfterAdvices
        dispatcher.dispatch();

        // check invocation result
        if (dispatcher.hasAdviceThrowing()) 
            throw dispatcher.getAdviceThrowing();
    }
}
