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

import io.gemini.aop.java.lang.BootstrapAdvice;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
import io.gemini.aop.java.lang.BootstrapAdvice.Dispatcher;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;


/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
@BootstrapClassConsumer
public class InstanceMethodAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, inline = true, prependLineNumber = true)
    public static boolean beforeMethod(
            @DescriptorOffset.Descriptor Object descriptor,
            @Advice.This(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object thisObject,
            @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] arguments,
            @Advice.Local(value = Constants.LOCAL_VARIABLE_ADVICE_DISPATCHER) Dispatcher<Object, Throwable> dispatcher
            ) throws Throwable {
        if(descriptor == null)
            return false;

        // 1.create dispatcher
        dispatcher = BootstrapAdvice.Bridger.dispacther(descriptor, thisObject, arguments);
        if(dispatcher == null)
            // ignore instrumentation and execute instrumented method
            return false;

        // 2.invoke BeforeAdvices
        dispatcher.dispatch();

        // 3.replace arguments
        arguments = dispatcher.getArguments();

        return dispatcher.hasAdviceThrowing() || dispatcher.hasAdviceReturning();
    }


    @Advice.OnMethodExit(onThrowable = Throwable.class, inline = true)
    public static void afterMethod(
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returning,
            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable throwing,
            @Advice.Local(value = Constants.LOCAL_VARIABLE_ADVICE_DISPATCHER) Dispatcher<Object, Throwable> dispatcher
            ) throws Throwable {
        if(dispatcher == null)
            return;

        // 1.assign return value if BeforeAdvices marked return before execute instrumented method
        if(dispatcher.hasAdviceThrowing()) {
            throwing = dispatcher.getAdviceThrowing();
            return;
        } else if(dispatcher.hasAdviceReturning()) {
            returning = dispatcher.getAdviceReturning();
            return;
        }

        // 2.set target returning
        dispatcher.setThrowing(throwing);
        dispatcher.setReturning(returning);

        // 3.invoke AfterAdvices
        dispatcher.dispatch();

        // check invocation result
        if(dispatcher.hasAdviceThrowing()) {
            throwing = dispatcher.getAdviceThrowing();
        } else if(dispatcher.hasAdviceReturning()) {
            returning = dispatcher.getAdviceReturning();
        }
    }
}
