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
import io.gemini.api.aspect.Advice;
import io.gemini.api.aspect.AspectSpec;
import io.gemini.api.aspect.AspectSpec.ExprPointcutSpec;
import io.gemini.api.aspect.AspectSpec.PojoPointcutSpec;
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;
import io.gemini.api.aspect.Pointcut;


public class Aspect_22AspectVisibility_Tests extends AbstractIntegrationTests {

    @Test
    public void testAspectVisibility() {
        new AspectVisibility_Objects().isVisible(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AspectVisibility_PojoPointcutSpec.ASPECT_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AspectVisibility_PojoPointcutAdvice.ASPECT_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AspectVisibility_ExprPointcutSpec.ASPECT_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AspectVisibility_ExprPointcutAdvice.ASPECT_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AspectVisibility_AspectJSpec.ASPECT_VISIBILITY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }
    }

    private static class AspectVisibility_Objects {

        public long isVisible(long input) {
            return input;
        }
    }

    private static class AspectVisibility_PojoPointcutSpec implements AspectSpec.PojoPointcutSpec {

        static final String ASPECT_VISIBILITY_AFTER_ADVICE = AspectVisibility_PojoPointcutSpec.class.getName() + ".after";

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
                    named("io.gemini.aop.integration.Aspect_22AspectVisibility_Tests$AspectVisibility_Objects"),
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
                ExecutionMemento.putAdviceMethodInvoker(ASPECT_VISIBILITY_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    private static class AspectVisibility_PojoPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String ASPECT_VISIBILITY_AFTER_ADVICE = AspectVisibility_PojoPointcutAdvice.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ASPECT_VISIBILITY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Aspect_22AspectVisibility_Tests$AspectVisibility_Objects") )
                    .methodMatcher(
                            named("isVisible")
                                .and(isPublic())
                                .and(takesArgument(0, is(long.class)))
                                .and(returns(long.class)) )
                    .builder();
        }
    }

    private static class AspectVisibility_ExprPointcutSpec implements AspectSpec.ExprPointcutSpec {

        private static final String ASPECT_VISIBILITY_AFTER_ADVICE = AspectVisibility_ExprPointcutSpec.class.getName() + ".after";


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
            return "execution(!private long io.gemini.aop.integration.Aspect_22AspectVisibility_Tests$AspectVisibility_Objects.isVisible(long))";
        }


        private static class SpecVisibility_ExprPointcut_Advice extends Advice.AbstractAfter<Long, RuntimeException> {

            /**
             * {@inheritDoc}
             */
            @Override
            public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
                ExecutionMemento.putAdviceMethodInvoker(ASPECT_VISIBILITY_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    private static class AspectVisibility_ExprPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AspectSpec.ExprPointcutSpec.Factory {

        private static final String ASPECT_VISIBILITY_AFTER_ADVICE = AspectVisibility_ExprPointcutAdvice.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(ASPECT_VISIBILITY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ExprPointcutSpec getAspectSpec() {
            return new AspectSpec.ExprPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .pointcutExpression("execution(!private long io.gemini.aop.integration.Aspect_22AspectVisibility_Tests$AspectVisibility_Objects.isVisible(long))")
                    .builder();
        }
    }

    @Aspect
    private static class AspectVisibility_AspectJSpec {

        private static final String MATCH_ASPECT_VISIBILITY_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Aspect_22AspectVisibility_Tests$AspectVisibility_Objects.isVisible(long))";

        private static final String ASPECT_VISIBILITY_AFTER_ADVICE = AspectVisibility_AspectJSpec.class.getName() + ".after";

        @After(MATCH_ASPECT_VISIBILITY_POINTCUT)
        public void aspectVisibility_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ASPECT_VISIBILITY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }
}