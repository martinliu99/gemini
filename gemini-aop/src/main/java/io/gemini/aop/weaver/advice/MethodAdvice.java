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
 * Framework ByteBuddy {@code @Advice} class for methods, including type initializer, class method, 
 * or instance method.
 * <p>
 * Intercepts method invocations, passes the target object and arguments to
 * before-advice (which may modify them), and propagates after-advice return/throw overrides.
 * </p>
 *
 * @author   martin.liu
 */
@BootstrapClassConsumer
public class MethodAdvice {

    /**
     * Executed before the method body.
     * Creates a {@link Dispatcher}, invokes before-advice (which may modify arguments),
     * and returns {@code true} to skip the method body if advice has set a return or throw value.
     *
     * @param descriptor    the cached joinpoint descriptor (injected via INDY)
     * @param targetObject  the target instance ({@code this})
     * @param arguments     the method arguments (may be replaced by advice)
     * @param dispatcher    thread-local dispatcher (injected via {@code @Advice.Local})
     * @return {@code true} to skip the method body, {@code false} to proceed normally
     * @throws Throwable if before-advice throws
     */
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, inline = true, prependLineNumber = true)
    public static boolean beforeMethod(
            @DescriptorOffset.Descriptor Object descriptor,
            @Advice.This(readOnly = false, typing = Assigner.Typing.DYNAMIC, optional = true) Object targetObject,
            @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] arguments,
            @Advice.Local(value = VAR_ADVICE_DISPATCHER) Dispatcher<Object, Throwable> dispatcher
            ) throws Throwable {
        // 1.create dispatcher
        dispatcher = getDelegator().createDispacther(descriptor, targetObject, arguments);
        if (dispatcher == null)
            // ignore instrumentation and execute instrumented method
            return false;

        // 2.invoke BeforeAdvices
        dispatcher.dispatch();

        // 3.replace arguments
        arguments = dispatcher.getArguments();

        return dispatcher.hasAdviceThrowing() || dispatcher.hasAdviceReturning();
    }


    /**
     * Executed after the method body (or after being skipped).
     * Invokes after-advice and propagates any advice-set return or throw value.
     *
     * @param returning   the actual return value of the method
     * @param throwing    the exception thrown by the method, or {@code null}
     * @param dispatcher  the dispatcher created in {@link #beforeMethod}
     * @throws Throwable if after-advice throws or if advice sets a throw value
     */
    @Advice.OnMethodExit(onThrowable = Throwable.class, inline = true)
    public static void afterMethod(
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returning,
            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable throwing,
            @Advice.Local(value = VAR_ADVICE_DISPATCHER) Dispatcher<Object, Throwable> dispatcher
            ) throws Throwable {
        if (dispatcher == null)
            return;

        // 1.assign return value if BeforeAdvices marked return before execute instrumented method
        if (dispatcher.hasAdviceThrowing()) {
            throwing = dispatcher.getAdviceThrowing();
            return;
        } else if (dispatcher.hasAdviceReturning()) {
            returning = dispatcher.getAdviceReturning();
            return;
        }

        // 2.set actual returning and throwing of target method
        dispatcher.setThrowing(throwing);
        dispatcher.setReturning(returning);

        // 3.invoke AfterAdvices
        dispatcher.dispatch();

        // check invocation result
        if (dispatcher.hasAdviceThrowing()) {
            throwing = dispatcher.getAdviceThrowing();
        } else if (dispatcher.hasAdviceReturning()) {
            returning = dispatcher.getAdviceReturning();
        }
    }
}
