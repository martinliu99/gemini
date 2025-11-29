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
package org.framework.aspect;

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

import io.gemini.api.aop.Joinpoint.MutableJoinpoint;

@Aspect
public class DemoServiceAspect_process {

    protected Logger LOGGER = null;

    public DemoServiceAspect_process() {
        LOGGER = LoggerFactory.getLogger(DemoServiceAspect_process.class);
    }

    @Pointcut("execution(* org.framework.demo.service..*Impl.process(org.framework.demo.api.Request))")
    public void process() {}

    @Before("process()")
//    @Before("execution(* org.framework.demo.service.*Impl.process(org.framework.demo.api.Request))")
    public void before(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("before '{}' with args: {}", this.getClass().getSimpleName(), joinpoint.getArguments());

        Request request = (Request) joinpoint.getArguments()[0];
        List<String> input = new ArrayList<>(request.getInput());
        input.add("DemoServiceAspect_process");
    }


    @AfterReturning(pointcut = "process()", returning="returning")
//    @After("@annotation(org.framework.demo.service.AspectJ)")
    public Object after(MutableJoinpoint<Response<String>, RuntimeException> joinpoint, Response<String> returning) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("after '{}' with args: {}", this.getClass().getSimpleName(), joinpoint.getArguments());

        return true;
    }
}
