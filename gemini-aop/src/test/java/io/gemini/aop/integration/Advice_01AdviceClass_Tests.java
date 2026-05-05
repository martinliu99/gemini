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

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;

import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.annotation.ExprPointcut;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * Tests advice, e.g., Gemini advice, AspectJ adivce, or ByteBuddy advice, etc.
 *
 * @author   martin.liu
 */
public class Advice_01AdviceClass_Tests {

    static final String POINTCUT_EXPRESSION = "execution(!private void io.gemini.aop.integration.Advice_01AdviceClass_Tests$AdviceClass_Object.match())";


    @Test
    public void testAdviceClass() {
        new AdviceClass_Object().match();

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceClass_PojoAdvice_Advisor.ADVICE_CLASS_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceClass_PojoAdvice_Advisor.ADVICE_CLASS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isNull();
        }

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceClass_ByteBuddyAdvice_Advisor.ADVICE_CLASS_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceClass_ByteBuddyAdvice_Advisor.ADVICE_CLASS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isNull();
        }

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceClass_AspectJAdvice_Aspect.ADVICE_CLASS_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceClass_AspectJAdvice_Aspect.ADVICE_CLASS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isNull();
        }
    }

    private static class AdviceClass_Object {

        void match() {
        }
    }

    @ExprPointcut(Advice_01AdviceClass_Tests.POINTCUT_EXPRESSION)
    private static class AdviceClass_PojoAdvice_Advisor extends Advice.AbstractBeforeAfter<Void, RuntimeException> {

        private static final String ADVICE_CLASS_BEFORE_ADVICE = AdviceClass_PojoAdvice_Advisor.class.getName() + ".before";
        private static final String ADVICE_CLASS_AFTER_ADVICE = AdviceClass_PojoAdvice_Advisor.class.getName() + ".after";


        /**
         * {@inheritDoc}
         */
        @Override
        public void before(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_CLASS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_CLASS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    @Aspect
    private static class AdviceClass_AspectJAdvice_Aspect {

        private static final String ADVICE_CLASS_BEFORE_ADVICE = AdviceClass_AspectJAdvice_Aspect.class.getName() + ".before";
        private static final String ADVICE_CLASS_AFTER_ADVICE = AdviceClass_AspectJAdvice_Aspect.class.getName() + ".after";


        @SuppressWarnings("rawtypes")
        @Before(Advice_01AdviceClass_Tests.POINTCUT_EXPRESSION)
        public void before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_CLASS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }

        @SuppressWarnings("rawtypes")
        @AfterReturning(pointcut = Advice_01AdviceClass_Tests.POINTCUT_EXPRESSION, returning = "returning")
        public void after(MutableJoinpoint joinpoint, Object returning) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_CLASS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetObject(returning) );
        }
    }

    @ExprPointcut(Advice_01AdviceClass_Tests.POINTCUT_EXPRESSION)
    static class AdviceClass_ByteBuddyAdvice_Advisor {

        private static final String ADVICE_CLASS_BEFORE_ADVICE = AdviceClass_ByteBuddyAdvice_Advisor.class.getName() + ".before";
        private static final String ADVICE_CLASS_AFTER_ADVICE = AdviceClass_ByteBuddyAdvice_Advisor.class.getName() + ".after";


        @OnMethodEnter(inline = false)
        public static void before() throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_CLASS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }

        @OnMethodExit(inline = false)
        public static void after(
                @net.bytebuddy.asm.Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returning) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_CLASS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }
    }
}
