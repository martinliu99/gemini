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

import java.util.List;

import org.framework.demo.api.Request;
import org.framework.demo.api.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.annotation.Order;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.annotation.PojoPointcut;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Demo advice using the {@link PojoPointcut} annotation with a programmatic ByteBuddy matcher.
 * <p>
 * Demonstrates how to write a POJO advice class that also implements {@link io.gemini.api.aop.Pointcut}
 * directly, providing {@link net.bytebuddy.matcher.ElementMatcher} instances for type and method selection.
 * </p>
 *
 * @author   martin.liu
 */
@PojoPointcut(Sample01_01PojoPointcut_Advisor.class)
@Order(Sample01_01PojoPointcut_Advisor.ADVICE_INDEX)
public class Sample01_01PojoPointcut_Advisor extends Advice.AbstractBeforeAfter<Response<String>, RuntimeException> 
        implements Pointcut {

    private static final Logger LOGGER = LoggerFactory.getLogger(Sample01_01PojoPointcut_Advisor.class);

    public static final int ADVICE_INDEX = 1;


    /**
     * {@inheritDoc}
     */
    @Override
    public void before(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Entering '{}' with args: {}", joinpoint.getTargetObject(), joinpoint.getArguments());

        // update Request's list
        Request request = (Request) joinpoint.getArguments()[0];
        List<String> input = request.getInput();
        input.add(Sample01_01PojoPointcut_Advisor.class.getSimpleName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void after(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Exited '{}' with args: {}", joinpoint.getTargetObject(), joinpoint.getArguments());
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
