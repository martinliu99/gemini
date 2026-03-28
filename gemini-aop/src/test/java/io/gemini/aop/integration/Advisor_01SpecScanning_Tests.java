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
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.annotation.ExprPointcut;
import io.gemini.api.aop.annotation.PojoPointcut;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;


public class Advisor_01SpecScanning_Tests {

    @Test
    public void testSpecScanning() {
        new SpecScanning_Object().scanSpec(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_AtPojoPointcutAdvice.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_AtExprPointcutAdvice.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }


        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_Aspect.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_Advice_Sub.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }
    }

    private static class SpecScanning_Object {

        public long scanSpec(long input) {
            return input;
        }

        public long ignoreIllegalSpec(long input) {
            return input;
        }
    }

    @PojoPointcut(pointcutClass = SpecScanning_AtPojoPointcutAdvice.class)
    public static class SpecScanning_AtPojoPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> implements Pointcut {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_AtPojoPointcutAdvice.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<TypeDescription> getTypeMatcher() {
            return named("io.gemini.aop.integration.Advisor_01SpecScanning_Tests$SpecScanning_Object");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<MethodDescription> getMethodMatcher() {
            return named("scanSpec")
                    .and(isPublic())
                    .and(takesArgument(0, is(long.class)))
                    .and(returns(long.class));
        }
    }


    @ExprPointcut(pointcutExpression = "execution(!private long io.gemini.aop.integration.Advisor_01SpecScanning_Tests$SpecScanning_Object.scanSpec(long))")
    public static class SpecScanning_AtExprPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_AtExprPointcutAdvice.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }


    @Aspect
    public static class SpecScanning_Aspect {

        private static final String MATCH_ADVISOR_SPEC_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_01SpecScanning_Tests$SpecScanning_Object.scanSpec(long))";

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_Aspect.class.getName() + ".after";

        @After(MATCH_ADVISOR_SPEC_POINTCUT)
        public void scanSpec_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class SpecScanning_Advice_Base extends Advice.AbstractAfter<Long, RuntimeException> {

        static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_Advice_Base.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    @PojoPointcut(pointcutClass = SpecScanning_Advice_Sub.class)
    public static class SpecScanning_Advice_Sub extends SpecScanning_Advice_Base implements Pointcut {

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<TypeDescription> getTypeMatcher() {
            return named("io.gemini.aop.integration.Advisor_01SpecScanning_Tests$SpecScanning_Object");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<MethodDescription> getMethodMatcher() {
            return named("scanSpec")
                    .and(isPublic())
                    .and(takesArgument(0, is(long.class)))
                    .and(returns(long.class));
        }
    }


    @Test
    public void testIllegalSpec() {
        new SpecScanning_Object().ignoreIllegalSpec(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NullAdvisorSpec_PojoPointcutAdvice.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NullAdvisorSpec_ExprPointcutAdvice.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        
    }

    @PojoPointcut(pointcutClass = SpecScanning_NullAdvisorSpec_PojoPointcutAdvice.class)
    public static class SpecScanning_NullAdvisorSpec_PojoPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> 
            implements Pointcut {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NullAdvisorSpec_PojoPointcutAdvice.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<TypeDescription> getTypeMatcher() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<MethodDescription> getMethodMatcher() {
            return null;
        }
    }

    @ExprPointcut(pointcutExpression = "")
    public static class SpecScanning_NullAdvisorSpec_ExprPointcutAdvice extends Advice.AbstractAfter<Long, RuntimeException> {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NullAdvisorSpec_ExprPointcutAdvice.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    @Aspect
    public static class SpecScanning_NoAdvice_Aspect {
    }
}