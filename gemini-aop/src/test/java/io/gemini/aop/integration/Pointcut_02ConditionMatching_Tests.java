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
/**
 * 
 */
package io.gemini.aop.integration;

import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;

import io.gemini.aop.test.AbstractIntegrationTests;
import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.AdvisorSpec.PojoPointcutSpec;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.MatchingContext;
import io.gemini.api.aop.annotation.Conditional;
import io.gemini.api.aop.annotation.ConditionalOnClassLoader;
import io.gemini.api.aop.annotation.ConditionalOnField;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 */
public class Pointcut_02ConditionMatching_Tests extends AbstractIntegrationTests {

    @Test
    public void testVoidMatching() {
        new VoidMatching_Object().matchVoid();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(VoidMatching_Aspect.MATCH_VOID_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(VoidMatching_Advice.MATCH_VOID_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isNull();
        }
    }

    private static class VoidMatching_Object {

        private void matchVoid() {
            new ConditionMatching_Object().conditionMethod();

            return;
        }
    }

    static class ConditionMatching_Object {

        @SuppressWarnings("unused")
        private Condition_Object condition_Object; 

        private void conditionMethod() {
            return;
        }
    }

    static class Condition_Object {}


    @Aspect
    @ConditionalOnMark
    @ConditionalOnClassLoader(classLoaderExpression = "AppClassLoader")
    public static class VoidMatching_Aspect {

        private static final String MATCH_VOID_POINTCUT = 
                "execution(void io.gemini.aop.integration.Pointcut_02ConditionMatching_Tests$VoidMatching_Object.matchVoid())";

        private static final String MATCH_VOID_AFTER_ADVICE = VoidMatching_Aspect.class.getName() + ".matchVoid_afterAdvice";

        @After(MATCH_VOID_POINTCUT)
        @ConditionalOnField(fieldExpression = "io.gemini.aop.integration.Pointcut_02ConditionMatching_Tests$Condition_Object io.gemini.aop.integration.Pointcut_02ConditionMatching_Tests$ConditionMatching_Object.condition_Object")
        public void matchVoid_afterAdvice(MutableJoinpoint<Void, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_VOID_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class VoidMatching_Advice extends Advice.AbstractAfter<Void, RuntimeException> 
            implements AdvisorSpec.PojoPointcutSpec.Factory {

        private static final String MATCH_VOID_AFTER_ADVICE = VoidMatching_Advice.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_VOID_AFTER_ADVICE, 
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
                            VoidMatching_Advice.class.getName() )
                    .condition(new ElementMatcher<MatchingContext>() {
                        @Override
                        public boolean matches(MatchingContext context) {
                            return context.hasMethod("private void io.gemini.aop.integration.Pointcut_02ConditionMatching_Tests$ConditionMatching_Object.conditionMethod()");
                        }
                    })
                    .typeMatcher(
                            named("io.gemini.aop.integration.Pointcut_02ConditionMatching_Tests$VoidMatching_Object") )
                    .methodMatcher(
                            named("matchVoid")
                                .and(isPrivate())
                                .and(returns(void.class)) )
                    .builder();
        }
    }


    @Conditional(OnMarkCondition.class)
    @interface ConditionalOnMark {

        boolean mark() default false;
    }


    private static class OnMarkCondition implements ElementMatcher<MatchingContext> {

        @SuppressWarnings("unused")
        public OnMarkCondition(boolean mark) {
            
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(MatchingContext context) {
            return context.hasType("io.gemini.aop.integration.Pointcut_02ConditionMatching_Tests$ConditionMatching_Object");
        }
        
    }
}
