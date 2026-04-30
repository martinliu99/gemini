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

import org.framework.demo.service.DemoServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.annotation.ExprPointcut;
import net.bytebuddy.description.method.MethodDescription;

/**
 * Demo advice intercepting constructor of target class.
 *
 * @author   martin.liu
 */
@ExprPointcut(pointcutExpression = 
        "execution(public org.framework.demo.service.DemoServiceImpl.new())")
public class Sample03_02nstanceConstructor extends Advice.AbstractBeforeAfter<DemoServiceImpl, RuntimeException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Sample03_02nstanceConstructor.class);


    /** 
     * {@inheritDoc}
     */
    @Override
    public void before(MutableJoinpoint<DemoServiceImpl, RuntimeException> joinpoint) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Entering '{}.{}' with args: {}", 
                    joinpoint.getTargetClass(), MethodDescription.TYPE_INITIALIZER_INTERNAL_NAME, joinpoint.getArguments());
    }

    /** 
     * {@inheritDoc}
     */
    @Override
    public void after(MutableJoinpoint<DemoServiceImpl, RuntimeException> joinpoint) throws Throwable {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Exited '{}' with args: {}", joinpoint.getTargetObject(), joinpoint.getArguments());
    }
}
