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
import io.gemini.api.aop.annotation.AdvisorName;
import io.gemini.api.aop.annotation.ConditionalOnClassLoader;
import io.gemini.api.aop.annotation.ExprPointcut;

@AdvisorName("ThreadPoolAdvisor")
@ConditionalOnClassLoader(isBootstrapClassLoader = true)
@ExprPointcut(pointcutExpression = "execution(public void java.util.concurrent.ThreadPoolExecutor.execute(java.lang.Runnable))")
public class Sample02_ThreadPoolAdvice extends Advice.AbstractBeforeAfter<Void, RuntimeException> {

    @Override
    public void before(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
//        LOGGER.info("before threadpool's execute: " + joinpoint);
    }

    @Override
    public void after(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
//        LOGGER.info("after threadpool's execute: " + joinpoint);
    }
}
