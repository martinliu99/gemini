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

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.framework.demo.api.Request;
import org.framework.demo.api.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.annotation.Order;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;

/**
 * Demo aspect using AspectJ annotation style ({@code @Aspect}, {@code @Before}, {@code @AfterReturning}).
 * <p>
 * Demonstrates how to write an AspectJ-style aspect that integrates with the Gemini AOP framework.
 * The pointcut targets {@code process(Request)} methods on all {@code *Impl} classes
 * under the {@code org.framework.demo.service} package.
 * </p>
 *
 * @author   martin.liu
 */
@Aspect
@Order(Sample01_03DemoServiceAspectJAspect.ADVICE_INDEX)
public class Sample01_03DemoServiceAspectJAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(Sample01_03DemoServiceAspectJAspect.class);

    public static final int ADVICE_INDEX = 3;


    @Pointcut("execution(* org.framework.demo.service.*Impl.process(org.framework.demo.api.Request)) && args(request)")
    public void process(Request request) {}

    @Pointcut("args(request)")
    public void args(Request request) {}

    // refer to named pointcut in current class
    @Before(value = "process(request) ", argNames = "request")
    public void before(MutableJoinpoint<Response<String>, RuntimeException> joinpoint, Request request) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Entering '{}' with args: {}", joinpoint.getTargetObject(), joinpoint.getArguments());

        List<String> input = new ArrayList<>(request.getInput());
        input.add(Sample01_03DemoServiceAspectJAspect.class.getSimpleName());

        Request newRequest = new Request(input);
        joinpoint.getArguments()[0] = newRequest;
    }


    // refer to named pointcut in another class
    @AfterReturning(pointcut = "org.framework.aspects.Sample01_03DemoServiceAspectJAspect$CommonPointcuts.process()", returning="returning")
    public Object after(MutableJoinpoint<Response<String>, RuntimeException> joinpoint, Response<String> returning) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Exited '{}' with args: {}", joinpoint.getTargetObject(), joinpoint.getArguments());

        return true;
    }


    @Aspect
    public static class CommonPointcuts {

        @Pointcut("execution(* org.framework.demo.service.*Impl.process(org.framework.demo.api.Request))")
        public void process() {}
    }
}
