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
import io.gemini.api.aspect.Pointcut;
import io.gemini.api.aspect.AspectSpec.ExprPointcutSpec;
import io.gemini.api.aspect.AspectSpec.PojoPointcutSpec;
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;


public class Aspect_01SpecScanning_Tests extends AbstractIntegrationTests {

    @Test
    public void testSpecScanning() {
        new SpecScanning_Objects().scanSpec(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_PojoPointcutSpec1.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_PojoPointcutSpec2.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }


        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_ExprPointcutSpec1.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_ExprPointcutSpec2.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }


        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_AspectJSpec.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_Advice_Sub.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }
    }

    private static class SpecScanning_Objects {

        public long scanSpec(long input) {
            return input;
        }

        public long ignoreIllegalSpec(long input) {
            return input;
        }
    }

    public static class SpecScanning_PojoPointcutSpec1 implements AspectSpec.PojoPointcutSpec {

        static final String SCAN_SPEC_AFTER_ADVICE = ".after";//SpecScanning_PojoPointcutSpec1.class.getName() + ".after";


        /* @see io.gemini.aop.aspect.AspectSpec#isPerInstance() 
         */
        @Override
        public boolean isPerInstance() {
            return false;
        }

        /* @see io.gemini.aop.aspect.SpecScanning#getAdviceClassName() 
         */
        @Override
        public String getAdviceClassName() {
            return SpecScanning_PojoPointcut_Advice.class.getName();
        }

        /* @see io.gemini.aop.aspect.AspectSpec#getOrder() 
         */
        @Override
        public int getOrder() {
            return 0;
        }

        /* @see io.gemini.aop.aspect.SpecScanning.PojoPointcutSpec#getPointcut() 
         */
        @Override
        public Pointcut getPointcut() {
            return new Pointcut.Default(
                    named("io.gemini.aop.integration.Aspect_01SpecScanning_Tests$SpecScanning_Objects"),
                    named("scanSpec")
                    .and(isPublic())
                    .and(takesArgument(0, is(long.class)))
                    .and(returns(long.class)) );
        }


        public static class SpecScanning_PojoPointcut_Advice extends Advice.AbstractAfter<Long, RuntimeException> {

            /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
             */
            @Override
            public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
                ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    public static class SpecScanning_PojoPointcutSpec2 extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_PojoPointcutSpec2.class.getName() + ".after";
        
        /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /* @see io.gemini.aop.aspect.AspectSpec.PojoPointcutSpec.Factory#getAspectSpec()
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Aspect_01SpecScanning_Tests$SpecScanning_Objects") )
                    .methodMatcher(
                            named("scanSpec")
                                .and(isPublic())
                                .and(takesArgument(0, is(long.class)))
                                .and(returns(long.class)) )
                    .builder();
        }
    }

    public static class SpecScanning_ExprPointcutSpec1 implements AspectSpec.ExprPointcutSpec {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_ExprPointcutSpec1.class.getName() + ".after";


        /* @see io.gemini.aop.aspect.AspectSpec#isPerInstance() 
         */
        @Override
        public boolean isPerInstance() {
            return false;
        }

        /* @see io.gemini.aop.aspect.SpecScanning#getAdviceClassName() 
         */
        @Override
        public String getAdviceClassName() {
            return SpecScanning_ExprPointcut_Advice.class.getName();
        }

        /* @see io.gemini.aop.aspect.AspectSpec#getOrder() 
         */
        @Override
        public int getOrder() {
            return 0;
        }

        /* @see io.gemini.aop.aspect.AspectSpec.ExprPointcutSpec#getPointcutExpression() 
         */
        @Override
        public String getPointcutExpression() {
            return "execution(!private long io.gemini.aop.integration.Aspect_01SpecScanning_Tests$SpecScanning_Objects.scanSpec(long))";
        }


        public static class SpecScanning_ExprPointcut_Advice extends Advice.AbstractAfter<Long, RuntimeException> {

            /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
             */
            @Override
            public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
                ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    public static class SpecScanning_ExprPointcutSpec2 extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AspectSpec.ExprPointcutSpec.Factory {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_ExprPointcutSpec2.class.getName() + ".after";
        
        /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /* @see io.gemini.aop.aspect.AspectSpec.PojoPointcutSpec.Factory#getAspectSpec()
         */
        @Override
        public ExprPointcutSpec getAspectSpec() {
            return new AspectSpec.ExprPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .pointcutExpression("execution(!private long io.gemini.aop.integration.Aspect_01SpecScanning_Tests$SpecScanning_Objects.scanSpec(long))")
                    .builder();
        }
    }

    @Aspect
    public static class SpecScanning_AspectJSpec {

        private static final String MATCH_ASPECT_SPEC_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Aspect_01SpecScanning_Tests$SpecScanning_Objects.scanSpec(long))";

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_AspectJSpec.class.getName() + ".scanSpec_afterAdvice";

        @After(MATCH_ASPECT_SPEC_POINTCUT)
        public void scanSpec_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class SpecScanning_Advice_Base extends Advice.AbstractAfter<Long, RuntimeException> {

        static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_Advice_Base.class.getName() + ".after";

        /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class SpecScanning_Advice_Sub extends SpecScanning_Advice_Base
            implements AspectSpec.PojoPointcutSpec.Factory {

        /* @see io.gemini.aop.aspect.AspectSpec.PojoPointcutSpec.Factory#getAspectSpec()
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Aspect_01SpecScanning_Tests$SpecScanning_Objects") )
                    .methodMatcher(
                            named("scanSpec")
                                .and(isPublic())
                                .and(takesArgument(0, is(long.class)))
                                .and(returns(long.class)) )
                    .builder();
        }
    }


    @Test
    public void testIllegalSpec() {
        new SpecScanning_Objects().ignoreIllegalSpec(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NoAdviceClass_PojoPointcutSpec1.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NoAdviceClass_PojoPointcutSpec2.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NoPointcut_PojoPointcutSpec1.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NoPointcut_PojoPointcutSpec2.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }


        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NoExpr_ExprPointcutSpec1.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(SpecScanning_NoExpr_ExprPointcutSpec2.SCAN_SPEC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    public static class SpecScanning_NoAdviceClass_PojoPointcutSpec1 implements AspectSpec.PojoPointcutSpec {

        static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NoAdviceClass_PojoPointcutSpec1.class.getName() + ".after";


        /* @see io.gemini.aop.aspect.AspectSpec#isPerInstance() 
         */
        @Override
        public boolean isPerInstance() {
            return false;
        }

        /* @see io.gemini.aop.aspect.SpecScanning#getAdviceClassName() 
         */
        @Override
        public String getAdviceClassName() {
            return null;
        }

        /* @see io.gemini.aop.aspect.AspectSpec#getOrder() 
         */
        @Override
        public int getOrder() {
            return 0;
        }

        /* @see io.gemini.aop.aspect.SpecScanning.PojoPointcutSpec#getPointcut() 
         */
        @Override
        public Pointcut getPointcut() {
            return new Pointcut.Default(
                    named("io.gemini.aop.integration.Aspect_01SpecScanning_Tests$SpecScanning_Objects"),
                    named("ignoreIllegalSpec")
                    .and(isPublic())
                    .and(takesArgument(0, is(long.class)))
                    .and(returns(long.class)) );
        }


        public static class SpecScanning_PojoPointcut_Advice extends Advice.AbstractAfter<Long, RuntimeException> {

            /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
             */
            @Override
            public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
                ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    public static class SpecScanning_NoAdviceClass_PojoPointcutSpec2 extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_PojoPointcutSpec2.class.getName() + ".after";

        /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /* @see io.gemini.aop.aspect.AspectSpec.PojoPointcutSpec.Factory#getAspectSpec()
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .typeMatcher(
                            named("io.gemini.aop.integration.Aspect_01SpecScanning_Tests$SpecScanning_Objects") )
                    .methodMatcher(
                            named("ignoreIllegalSpec")
                                .and(isPublic())
                                .and(takesArgument(0, is(long.class)))
                                .and(returns(long.class)) )
                    .builder();
        }
    }

    public static class SpecScanning_NoPointcut_PojoPointcutSpec1 implements AspectSpec.PojoPointcutSpec {

        static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NoPointcut_PojoPointcutSpec1.class.getName() + ".after";


        /* @see io.gemini.aop.aspect.AspectSpec#isPerInstance() 
         */
        @Override
        public boolean isPerInstance() {
            return false;
        }

        /* @see io.gemini.aop.aspect.SpecScanning#getAdviceClassName() 
         */
        @Override
        public String getAdviceClassName() {
            return SpecScanning_PojoPointcut_Advice.class.getName();
        }

        /* @see io.gemini.aop.aspect.AspectSpec#getOrder() 
         */
        @Override
        public int getOrder() {
            return 0;
        }

        /* @see io.gemini.aop.aspect.SpecScanning.PojoPointcutSpec#getPointcut() 
         */
        @Override
        public Pointcut getPointcut() {
            return null;
        }


        public static class SpecScanning_PojoPointcut_Advice extends Advice.AbstractAfter<Long, RuntimeException> {

            /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
             */
            @Override
            public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
                ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    public static class SpecScanning_NoPointcut_PojoPointcutSpec2 extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NoPointcut_PojoPointcutSpec2.class.getName() + ".after";

        /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /* @see io.gemini.aop.aspect.AspectSpec.PojoPointcutSpec.Factory#getAspectSpec()
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(SpecScanning_PojoPointcut_Advice.class.getName())
                    .builder();
        }


        public static class SpecScanning_PojoPointcut_Advice extends Advice.AbstractAfter<Long, RuntimeException> {

            /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
             */
            @Override
            public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
                ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    public static class SpecScanning_NoExpr_ExprPointcutSpec1 implements AspectSpec.ExprPointcutSpec {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NoExpr_ExprPointcutSpec1.class.getName() + ".after";


        /* @see io.gemini.aop.aspect.AspectSpec#isPerInstance() 
         */
        @Override
        public boolean isPerInstance() {
            return false;
        }

        /* @see io.gemini.aop.aspect.SpecScanning#getAdviceClassName() 
         */
        @Override
        public String getAdviceClassName() {
            return SpecScanning_ExprPointcut_Advice.class.getName();
        }

        /* @see io.gemini.aop.aspect.AspectSpec#getOrder() 
         */
        @Override
        public int getOrder() {
            return 0;
        }

        /* @see io.gemini.aop.aspect.AspectSpec.ExprPointcutSpec#getPointcutExpression() 
         */
        @Override
        public String getPointcutExpression() {
            return "";
        }


        public static class SpecScanning_ExprPointcut_Advice extends Advice.AbstractAfter<Long, RuntimeException> {

            /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
             */
            @Override
            public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
                ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(joinpoint.getReturning()) );
            }
        }
    }

    public static class SpecScanning_NoExpr_ExprPointcutSpec2 extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AspectSpec.ExprPointcutSpec.Factory {

        private static final String SCAN_SPEC_AFTER_ADVICE = SpecScanning_NoExpr_ExprPointcutSpec2.class.getName() + ".after";
        
        /* @see io.gemini.aop.aspect.Advice.After#after(io.gemini.aop.aspect.Joinpoint.MutableJoinpoint)
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(SCAN_SPEC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /* @see io.gemini.aop.aspect.AspectSpec.PojoPointcutSpec.Factory#getAspectSpec()
         */
        @Override
        public ExprPointcutSpec getAspectSpec() {
            return new AspectSpec.ExprPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .builder();
        }
    }


    @Aspect
    public static class SpecScanning_NoAdvice_AspectJSpec {
        
    }
}