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
package org.framework.aspects;

import java.util.ArrayList;
import java.util.List;

import org.framework.demo.api.DemoService;
import org.framework.demo.api.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.aop.annotation.ExprPointcut;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * Demo ByteBuddy advice using the {@link ExprPointcut} annotation with an AspectJ execution expression.
 * <p>
 * Demonstrates how to write a ByteBuddy advice class where the pointcut is declared via
 * {@code @ExprPointcut(pointcutExpression = "...")} rather than implementing {@link io.gemini.api.aop.Pointcut}.
 * The advice modifies the first argument before the target method executes.
 * </p>
 *
 * @author   martin.liu
 */
@ExprPointcut(pointcutExpression = "execution(* org.framework.demo.service..*Impl.process(org.framework.demo.api.Request))")
public class Sample01_11ByteBuddyAdvice_Advisor {

    private static final Logger LOGGER = LoggerFactory.getLogger(Sample01_11ByteBuddyAdvice_Advisor.class);

    public static final int ADVICE_INDEX = 4;


    @OnMethodEnter(inline = false)
    @Advice.AssignReturned.AsScalar
    @Advice.AssignReturned.ToAllArguments(typing = Assigner.Typing.DYNAMIC)
    public static Object[] before(
            @Advice.This DemoService targetObject,
            @Advice.AllArguments(typing = Assigner.Typing.DYNAMIC) Object[] arguments) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Entering '{}' with args: {}", targetObject, arguments);

        // update argument
        Request request = (Request) arguments[0];

        List<String> input = new ArrayList<>(request.getInput());
        input.add(Sample01_11ByteBuddyAdvice_Advisor.class.getSimpleName());

        Request newRequest = new Request(input);
        return new Object[] {newRequest};
    }


    @ExprPointcut(pointcutExpression = "execution(* org.framework.demo.service..*Impl.process(org.framework.demo.api.Request))")
    @SuppressWarnings("UnusedNestedClass")
    private static class Test {

        @OnMethodExit(inline = false)
        static Object after(
                @Advice.This DemoService targetObject,
                @Advice.Argument(value = 0, readOnly = true, typing = Assigner.Typing.DYNAMIC) Request request) throws Throwable {
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Exited '{}' with args: {}", targetObject, request);

            return true;
        }

    }
}
