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
package org.framework.aspects;

import io.gemini.aop.aspect.Advice;
import io.gemini.aop.aspect.Joinpoint.MutableJoinpoint;

public class TestAdvice1 extends Advice.AbstractBeforeAfter<Object, RuntimeException> {

    @Override
    public void before(MutableJoinpoint<Object, RuntimeException> joinpoint) throws Throwable {
        LOGGER.info("before '{}' with args: {}", this.getClass().getSimpleName(), joinpoint.getArguments());
        LOGGER.info("before change, arguemnts '{}'", joinpoint.getArguments());
        joinpoint.getArguments()[0] = "1-advice1";
        LOGGER.info("after change, arguemnts '{}'", joinpoint.getArguments());

    }

    @Override
    public void after(MutableJoinpoint<Object, RuntimeException> joinpoint) throws Throwable {
        LOGGER.info("after '{}': ", this.getClass().getSimpleName());
    }


}
