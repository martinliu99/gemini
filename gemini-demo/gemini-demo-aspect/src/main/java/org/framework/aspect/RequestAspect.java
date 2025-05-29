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

import java.util.List;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.aop.Joinpoint.MutableJoinpoint;

@Aspect
public class RequestAspect {

    protected Logger LOGGER = LoggerFactory.getLogger(RequestAspect.class);

    @Before("execution(* org.framework.demo.api.Request.getInput())")
    public void before(MutableJoinpoint<List<String>, RuntimeException> joinpoint) throws Throwable {

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @After("execution(* org.framework.demo.api.Request.getInput())")
    public Object after(MutableJoinpoint<List<String>, RuntimeException> joinpoint) throws Throwable {
        Object rtn = joinpoint.getReturning();
        if(rtn instanceof List) {
            ((List) rtn).add("after");
        }

        LOGGER.info("  after rquest::input with {}", rtn);
        return rtn;
    }
}
