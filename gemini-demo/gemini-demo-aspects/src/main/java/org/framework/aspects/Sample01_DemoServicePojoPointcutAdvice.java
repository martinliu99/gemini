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

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.util.ArrayList;
import java.util.List;

import org.framework.demo.api.Request;
import org.framework.demo.api.Response;

import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.annotation.PojoPointcut;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@PojoPointcut(pointcutClass = Sample01_DemoServicePojoPointcutAdvice.class)
public class Sample01_DemoServicePojoPointcutAdvice extends Advice.AbstractBeforeAfter<Response<String>, RuntimeException> 
        implements Pointcut {

    private static final String DEMO_SERVICE_ADVICE = Sample01_DemoServicePojoPointcutAdvice.class.getSimpleName();


    @Override
    public void before(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("before '{}' with args: {}", this.getClass().getSimpleName(), joinpoint.getArguments());

        // update argument
        Request request = (Request) joinpoint.getArguments()[0];
        List<String> input = new ArrayList<>(request.getInput());
        input.add(DEMO_SERVICE_ADVICE);
    }

    @Override
    public void after(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("after '{}' with args: {}", this.getClass().getSimpleName(), joinpoint.getArguments());
    }

    /** 
     * {@inheritDoc}
     */
    @Override
    public ElementMatcher<TypeDescription> getTypeMatcher() {
        return named("org.framework.demo.service.DemoServiceImpl");
    }

    /** 
     * {@inheritDoc}
     */
    @Override
    public ElementMatcher<MethodDescription> getMethodMatcher() {
        return named("process");
    }
}
