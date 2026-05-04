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
package io.gemini.aop.integration;


import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;

import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.annotation.ExprPointcut;
import io.gemini.api.aop.annotation.PojoPointcut;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Tests advice class visibility.
 *
 * @author   martin.liu
 */
public class Advice_11AdviceVisibility_Tests {

    private static final String POINTCUT_EXPRESSION = "execution(!private long io.gemini.aop.integration.Advice_11AdviceVisibility_Tests$AdviceVisibility_Object.match(long))";


    @Test
    public void testAdviceVisibility() {
        new AdviceVisibility_Object().match(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceVisibility_PojoPointcut_Advisor.ADVICE_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceVisibility_ExprPointcut_Advisor.ADVICE_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceVisibility_Aspect.ADVICE_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker2 = ExecutionMemento.getAdviceMethodInvoker(AdviceVisibility_Aspect.ADVICE_VISIBILITY_AFTER_ADVICE_INVISIBLE);
            assertThat(afterAdviceMethodInvoker2).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceVisibility_ByteBuddyAdvice_Advisor.ADVICE_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker2 = ExecutionMemento.getAdviceMethodInvoker(AdviceVisibility_Aspect.ADVICE_VISIBILITY_AFTER_ADVICE_INVISIBLE);
            assertThat(afterAdviceMethodInvoker2).isNull();
        }
    }

    private static class AdviceVisibility_Object {

        long match(long input) {
            return input;
        }
    }

    @PojoPointcut(pointcutClass = AdviceVisibility_PojoPointcut_Advisor.class)
    private static class AdviceVisibility_PojoPointcut_Advisor extends Advice.AbstractAfter<Long, RuntimeException> implements Pointcut {

        private static final String ADVICE_VISIBILITY_AFTER_ADVICE = AdviceVisibility_PojoPointcut_Advisor.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_VISIBILITY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<TypeDescription> getTypeMatcher() {
            return named("io.gemini.aop.integration.Advice_11AdviceVisibility_Tests$AdviceVisibility_Object");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<MethodDescription> getMethodMatcher() {
            return named("match")
                    .and(isPackagePrivate())
                    .and(takesArgument(0, is(long.class)))
                    .and(returns(long.class));
        }
    }

    @ExprPointcut(pointcutExpression = Advice_11AdviceVisibility_Tests.POINTCUT_EXPRESSION)
    private static class AdviceVisibility_ExprPointcut_Advisor extends Advice.AbstractAfter<Long, RuntimeException> {

        private static final String ADVICE_VISIBILITY_AFTER_ADVICE = AdviceVisibility_ExprPointcut_Advisor.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_VISIBILITY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    @Aspect
    private static class AdviceVisibility_Aspect {

        private static final String ADVICE_VISIBILITY_AFTER_ADVICE = AdviceVisibility_Aspect.class.getName() + ".after";
        private static final String ADVICE_VISIBILITY_AFTER_ADVICE_INVISIBLE = AdviceVisibility_Aspect.class.getName() + ".after_invisible";

        @SuppressWarnings("rawtypes")
        @After(Advice_11AdviceVisibility_Tests.POINTCUT_EXPRESSION)
        void after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_VISIBILITY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }

        @SuppressWarnings("UnusedMethod")
        @After(Advice_11AdviceVisibility_Tests.POINTCUT_EXPRESSION)
        private void after_invisible() {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_VISIBILITY_AFTER_ADVICE_INVISIBLE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }
    }

    @ExprPointcut(pointcutExpression = Advice_11AdviceVisibility_Tests.POINTCUT_EXPRESSION)
    private static class AdviceVisibility_ByteBuddyAdvice_Advisor {

        private static final String ADVICE_VISIBILITY_BEFORE_ADVICE = AdviceVisibility_ByteBuddyAdvice_Advisor.class.getName() + ".before";
        private static final String ADVICE_VISIBILITY_AFTER_ADVICE = AdviceVisibility_ByteBuddyAdvice_Advisor.class.getName() + ".after";


        @OnMethodEnter(inline = false)
        static void before() throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_VISIBILITY_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }

        @OnMethodExit(inline = false)
        static void after(
                @net.bytebuddy.asm.Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returning) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_VISIBILITY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }
    }
}