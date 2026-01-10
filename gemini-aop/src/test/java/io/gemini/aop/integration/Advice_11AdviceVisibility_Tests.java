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

import org.junit.jupiter.api.Test;

import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.AdvisorSpec.ExprPointcutSpec;
import io.gemini.api.aop.AdvisorSpec.PojoPointcutSpec;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;


public class Advice_11AdviceVisibility_Tests {

    @Test
    public void testAdviceVisibility() {
        new AdviceVisibility_Object().isVisible(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceVisibility_PojoPointcutAdvice.ADVICE_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AdviceVisibility_ExprPointcutAdvice.ADVISOR_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }
    }

    private static class AdviceVisibility_Object {

        public long isVisible(long input) {
            return input;
        }
    }

    private static class AdviceVisibility_PojoPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AdvisorSpec.PojoPointcutSpec.Factory {

        private static final String ADVICE_VISIBILITY_AFTER_ADVICE = AdviceVisibility_PojoPointcutAdvice.class.getName() + ".after";

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
        public PojoPointcutSpec getAdvisorSpec() {
            return new AdvisorSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Advice_11AdviceVisibility_Tests$AdviceVisibility_Object") )
                    .methodMatcher(
                            named("isVisible")
                                .and(isPublic())
                                .and(takesArgument(0, is(long.class)))
                                .and(returns(long.class)) )
                    .builder();
        }
    }

    private static class AdviceVisibility_ExprPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AdvisorSpec.ExprPointcutSpec.Factory {

        private static final String ADVISOR_VISIBILITY_AFTER_ADVICE = AdviceVisibility_ExprPointcutAdvice.class.getName() + ".after";

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
                    .pointcutExpression("execution(!private long io.gemini.aop.integration.Advice_11AdviceVisibility_Tests$AdviceVisibility_Object.isVisible(long))")
                    .builder();
        }
    }
}