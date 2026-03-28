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

import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.annotation.ExprPointcut;

@ExprPointcut(pointcutExpression = "execution(java.lang.String org.framework.demo.service.DemoServiceImpl.process2(java.lang.String))")
public class Sample01_DemoServiceExprPointcutAdvice extends Advice.AbstractBeforeAfter<String, RuntimeException> {

    @Override
    public void before(MutableJoinpoint<String, RuntimeException> joinpoint) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("before '{}' with args: {}", this.getClass().getSimpleName(), joinpoint.getArguments());

        String request = (String) joinpoint.getArguments()[0];

        // TODO:
        joinpoint.getArguments()[0] = "modified-" + request;
    }

    @Override
    public void after(MutableJoinpoint<String, RuntimeException> joinpoint) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("after '{}' with args: {}", this.getClass().getSimpleName(), joinpoint.getArguments());
    }
}
