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

import io.gemini.aop.test.AbstractIntegrationTests;
import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.AdvisorSpec.ExprPointcutSpec;
import io.gemini.api.aop.AdvisorSpec.PojoPointcutSpec;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;


public class Advisor_22AdvisorVisibility_Tests extends AbstractIntegrationTests {

    @Test
    public void testAdvisorVisibility() {
        new AdvisorVisibility_Object().isVisible(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdvisorVisibility_PojoPointcutSpec.ADVISOR_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdvisorVisibility_PojoPointcutAdvice.ADVISOR_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdvisorVisibility_ExprPointcutSpec.ADVISOR_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdvisorVisibility_ExprPointcutAdvice.ADVISOR_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdvisorVisibility_Aspect.ADVISOR_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }
    }

    private static class AdvisorVisibility_Object {

        public long isVisible(long input) {
            return input;
        }
    }

    private static class AdvisorVisibility_PojoPointcutSpec implements AdvisorSpec.PojoPointcutSpec {

        static final String ADVISOR_VISIBILITY_AFTER_ADVICE = AdvisorVisibility_PojoPointcutSpec.class.getName() + ".after";

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
        public Pointcut getPointcut() {
            return new Pointcut.Default(
                    named("io.gemini.aop.integration.Advisor_22AdvisorVisibility_Tests$AdvisorVisibility_Object"),
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

    private static class AdvisorVisibility_PojoPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AdvisorSpec.PojoPointcutSpec.Factory {

        private static final String ADVISOR_VISIBILITY_AFTER_ADVICE = AdvisorVisibility_PojoPointcutAdvice.class.getName() + ".after";

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

        /**
         * {@inheritDoc}
         */
        @Override
        public PojoPointcutSpec getAdvisorSpec() {
            return new AdvisorSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Advisor_22AdvisorVisibility_Tests$AdvisorVisibility_Object") )
                    .methodMatcher(
                            named("isVisible")
                                .and(isPublic())
                                .and(takesArgument(0, is(long.class)))
                                .and(returns(long.class)) )
                    .builder();
        }
    }

    private static class AdvisorVisibility_ExprPointcutSpec implements AdvisorSpec.ExprPointcutSpec {

        private static final String ADVISOR_VISIBILITY_AFTER_ADVICE = AdvisorVisibility_ExprPointcutSpec.class.getName() + ".after";


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
        public String getPointcutExpression() {
            return "execution(!private long io.gemini.aop.integration.Advisor_22AdvisorVisibility_Tests$AdvisorVisibility_Object.isVisible(long))";
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

    private static class AdvisorVisibility_ExprPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AdvisorSpec.ExprPointcutSpec.Factory {

        private static final String ADVISOR_VISIBILITY_AFTER_ADVICE = AdvisorVisibility_ExprPointcutAdvice.class.getName() + ".after";

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

        /**
         * {@inheritDoc}
         */
        @Override
        public ExprPointcutSpec getAdvisorSpec() {
            return new AdvisorSpec.ExprPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .pointcutExpression("execution(!private long io.gemini.aop.integration.Advisor_22AdvisorVisibility_Tests$AdvisorVisibility_Object.isVisible(long))")
                    .builder();
        }
    }

    @Aspect
    private static class AdvisorVisibility_Aspect {

        private static final String MATCH_ADVISOR_VISIBILITY_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_22AdvisorVisibility_Tests$AdvisorVisibility_Object.isVisible(long))";

        private static final String ADVISOR_VISIBILITY_AFTER_ADVICE = AdvisorVisibility_Aspect.class.getName() + ".after";

        @After(MATCH_ADVISOR_VISIBILITY_POINTCUT)
        public void aspectVisibility_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVISOR_VISIBILITY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }
}