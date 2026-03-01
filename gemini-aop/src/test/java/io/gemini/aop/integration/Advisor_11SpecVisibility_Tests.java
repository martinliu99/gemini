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
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
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
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.Pointcut;


public class Advisor_11SpecVisibility_Tests {

    @Test
    public void testAdvisorVisibility() {
        new SpecVisibility_Object().isVisible(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecVisibility_PojoPointcutSpec.ADVISOR_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecVisibility_ExprPointcutSpec.ADVISOR_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecVisibility_Aspect.ADVISOR_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }
    }

    private static class SpecVisibility_Object {

        public long isVisible(long input) {
            return input;
        }
    }

    private static class SpecVisibility_PojoPointcutSpec implements AdvisorSpec.PojoPointcutSpec {

        static final String ADVISOR_VISIBILITY_AFTER_ADVICE = SpecVisibility_PojoPointcutSpec.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isPerInstance() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getAdviceClassName() {
            return SpecVisibility_PojoPointcut_Advice.class.getName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getOrder() {
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isBreakCircularity() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pointcut getPointcut() {
            return new Pointcut.Default(
                    named("io.gemini.aop.integration.Advisor_11SpecVisibility_Tests$SpecVisibility_Object"),
                    named("isVisible")
                    .and(isPublic())
                    .and(takesArgument(0, is(long.class)))
                    .and(returns(long.class)) );
        }


        private static class SpecVisibility_PojoPointcut_Advice extends Advice.AbstractAfter<Long, RuntimeException> {

            /**
             * {@inheritDoc}
             */
            @Override
            public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
                ExecutionMemento.putAdviceMethodInvoker(ADVISOR_VISIBILITY_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    private static class SpecVisibility_ExprPointcutSpec implements AdvisorSpec.ExprPointcutSpec {

        private static final String ADVISOR_VISIBILITY_AFTER_ADVICE = SpecVisibility_ExprPointcutSpec.class.getName() + ".after";


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isPerInstance() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getAdviceClassName() {
            return SpecVisibility_ExprPointcut_Advice.class.getName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getOrder() {
            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isBreakCircularity() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getPointcutExpression() {
            return "execution(!private long io.gemini.aop.integration.Advisor_11SpecVisibility_Tests$SpecVisibility_Object.isVisible(long))";
        }


        private static class SpecVisibility_ExprPointcut_Advice extends Advice.AbstractAfter<Long, RuntimeException> {

            /**
             * {@inheritDoc}
             */
            @Override
            public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
                ExecutionMemento.putAdviceMethodInvoker(ADVISOR_VISIBILITY_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    @Aspect
    private static class SpecVisibility_Aspect {

        private static final String MATCH_ADVISOR_VISIBILITY_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_11SpecVisibility_Tests$SpecVisibility_Object.isVisible(long))";

        private static final String ADVISOR_VISIBILITY_AFTER_ADVICE = SpecVisibility_Aspect.class.getName() + ".after";

        @After(MATCH_ADVISOR_VISIBILITY_POINTCUT)
        public void aspectVisibility_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVISOR_VISIBILITY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }
}