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

import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.annotation.ConditionalOnClassLoader;
import io.gemini.api.aop.annotation.PojoPointcut;
import io.gemini.api.aop.Pointcut;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@ConditionalOnClassLoader(isBootstrapClassLoader = true)
@PojoPointcut(pointcutClass = Sample02_ThreadAdvice.class)
public class Sample02_ThreadAdvice extends Advice.AbstractBeforeAfter<Void, RuntimeException> implements Pointcut {

    @Override
    public void before(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
//        LOGGER.info("before thread's run: " + joinpoint);
    }

    @Override
    public void after(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
//        LOGGER.info("after thread's run: " + joinpoint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ElementMatcher<TypeDescription> getTypeMatcher() {
        return named("java.lang.Thread");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ElementMatcher<MethodDescription> getMethodMatcher() {
        return named("start");
    }


}
