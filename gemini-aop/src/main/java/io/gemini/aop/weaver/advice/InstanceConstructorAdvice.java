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
import io.gemini.aop.java.lang.BootstrapAdvice.Dispatcher;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
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
public class InstanceConstructorAdvice {

    @Advice.OnMethodEnter(inline = true, prependLineNumber = true)
    public static void beforeConstructor(
            @DescriptorOffset.Descriptor Object descriptor,
            @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] arguments,
            @Advice.Local(value = Constants.LOCAL_VARIABLE_ADVICE_DISPATCHER) Dispatcher<Object, Throwable> dispatcher
            ) throws Throwable {
        if(descriptor == null)
            return;

        // 1.create dispatcher
        dispatcher = BootstrapAdvice.Bridger.dispacther(descriptor, null, arguments);
        if(dispatcher == null)
            // ignore instrumentation and execute instrumented method
            return;

        // 2.invoke BeforeAdvices
        dispatcher.dispatch();

        // check invocation result
        if(dispatcher.hasAdviceThrowing())
            throw dispatcher.getAdviceThrowing();

        // 3.replace arguments
        arguments = dispatcher.getArguments();
    }


    // refer to https://github.com/raphw/byte-buddy/issues/375
    // Advice.Thrown and try-catch is not allowed for constructor at this time
    @Advice.OnMethodExit(/* onThrowable = Throwable.class */ inline = true)
    public static void afterConstructor(
            @Advice.This(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object thisObject,
//            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returning,
//            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable throwing,
            @Advice.Local(value = Constants.LOCAL_VARIABLE_ADVICE_DISPATCHER) Dispatcher<Object, Throwable> dispatcher
            ) throws Throwable {
        if(dispatcher == null)
            return;

        // 1.set target returning
        dispatcher.setThrowing(null);       // bytebuddy does not catch exception for constructor
        dispatcher.setReturning(thisObject);

        // 2.invoke AfterAdvices
        dispatcher.dispatch();

        // check invocation result
        if(dispatcher.hasAdviceThrowing()) 
            throw dispatcher.getAdviceThrowing();
    }
}
