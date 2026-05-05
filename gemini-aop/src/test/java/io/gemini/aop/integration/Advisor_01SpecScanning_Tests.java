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
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Tests advisor spec scanning.
 *
 * @author   martin.liu
 */
public class Advisor_01SpecScanning_Tests {

    private static final String SCAN_SPEC_POINTCUT_EXPRESSION = 
            "execution(!private long io.gemini.aop.integration.Advisor_01SpecScanning_Tests$SpecScanning_Object.scanSpec(long))";
    private static final String IGNORE_ILLEGAL_SPEC_POINTCUT_EXPRESSION = 
            "execution(!private long io.gemini.aop.integration.Advisor_01SpecScanning_Tests$SpecScanning_Object.ignoreIllegalSpec(long))";


    @Test
    public void testSpecScanning() {
        new SpecScanning_Object().scanSpec(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_PojoPointcut_Advisor.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_ExprPointcut_Advisor.SCAN_SPEC_AFTER_ADVICE);
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

    static class SpecScanning_Object {

        public long scanSpec(long input) {
            return input;
        }

        public long ignoreIllegalSpec(long input) {
            return input;
        }
    }

    @PojoPointcut(SpecScanning_PojoPointcut_Advisor.class)
    public static class SpecScanning_PojoPointcut_Advisor extends Advice.AbstractAfter<Long, RuntimeException> implements Pointcut {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_PojoPointcut_Advisor.class.getName() + ".after";

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


    @ExprPointcut("execution(!private long io.gemini.aop.integration.Advisor_01SpecScanning_Tests$SpecScanning_Object.scanSpec(long))")
    public static class SpecScanning_ExprPointcut_Advisor extends Advice.AbstractAfter<Long, RuntimeException> {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_ExprPointcut_Advisor.class.getName() + ".after";

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

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_Aspect.class.getName() + ".after";

        @After(SCAN_SPEC_POINTCUT_EXPRESSION)
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

    @PojoPointcut(SpecScanning_Advice_Sub.class)
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
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NullPointcutMatcher_Advisor.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NullPointcutExpr_Advisor.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NoAdviceMethod_Aspect.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_InlineAdvice_Advisor.SCAN_SPEC_AFTER_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_InlineAdvice_Advisor.SCAN_SPEC_AFTER_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_InstanceMethodAdvice_Advisor.SCAN_SPEC_AFTER_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNull();
        }
    }

    @PojoPointcut(SpecScanning_NullPointcutMatcher_Advisor.class)
    public static class SpecScanning_NullPointcutMatcher_Advisor extends Advice.AbstractAfter<Long, RuntimeException> 
            implements Pointcut {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NullPointcutMatcher_Advisor.class.getName() + ".after";

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

    @ExprPointcut("")
    public static class SpecScanning_NullPointcutExpr_Advisor extends Advice.AbstractAfter<Long, RuntimeException> {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NullPointcutExpr_Advisor.class.getName() + ".after";

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
    public static class SpecScanning_NoAdviceMethod_Aspect {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NoAdviceMethod_Aspect.class.getName() + ".after";
    }

    @ExprPointcut(IGNORE_ILLEGAL_SPEC_POINTCUT_EXPRESSION)
    static class SpecScanning_DuplicateAdvice_Advisor {

        private static final String SCAN_SPEC_AFTER1_ADVICE = SpecScanning_InstanceMethodAdvice_Advisor.class.getName() + ".after1";
        private static final String SCAN_SPEC_AFTER2_ADVICE = SpecScanning_InstanceMethodAdvice_Advisor.class.getName() + ".after2";


        @OnMethodExit(inline = true)
        public static void after1() throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER1_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }

        @OnMethodExit(inline = true)
        public static void after2() throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER2_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }
    }

    @ExprPointcut(IGNORE_ILLEGAL_SPEC_POINTCUT_EXPRESSION)
    static class SpecScanning_InlineAdvice_Advisor {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_InstanceMethodAdvice_Advisor.class.getName() + ".after";


        @OnMethodExit(inline = true)
        public static void before() throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }
    }

    @ExprPointcut(IGNORE_ILLEGAL_SPEC_POINTCUT_EXPRESSION)
    static class SpecScanning_InstanceMethodAdvice_Advisor {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_InstanceMethodAdvice_Advisor.class.getName() + ".after";


        @OnMethodExit(inline = false)
        public void after(
                @net.bytebuddy.asm.Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returning) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }
    }
}