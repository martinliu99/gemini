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

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.annotation.PojoPointcut;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.matcher.TypeMatchers;

/**
 * Demo advice that intercepts {@code run()} methods on classes implementing {@code Runnable}
 * within the AOP framework weaver transformer package.
 * <p>
 * Demonstrates the use of {@link TypeMatchers#isExtendedFrom(String...)} to match types
 * by their implemented interfaces.
 * </p>
 *
 * @author   martin.liu
 */
@PojoPointcut(pointcutClass = Sample02_10RunAdvice.class)
public class Sample02_10RunAdvice extends Advice.AbstractBeforeAfter<Object, RuntimeException> implements Pointcut {

    /**
     * {@inheritDoc}
     */
    @Override
    public void before(MutableJoinpoint<Object, RuntimeException> joinpoint) throws Throwable {
        LOGGER.info("Entering runnable {}.", joinpoint.getTargetObject());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void after(MutableJoinpoint<Object, RuntimeException> joinpoint) throws Throwable {
        LOGGER.info("Exited runnable {}.", joinpoint.getTargetObject());
    }

    /** 
     * {@inheritDoc}
     */
    @Override
    public ElementMatcher<TypeDescription> getTypeMatcher() {
        return nameStartsWith("io.gemini.weaver.transformer").and( TypeMatchers.isExtendedFrom("java.lang.Runnable"));
    }

    /** 
     * {@inheritDoc}
     */
    @Override
    public ElementMatcher<MethodDescription> getMethodMatcher() {
        return named("run");
    }

}
