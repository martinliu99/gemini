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

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.junit.jupiter.api.Test;

import io.gemini.aop.test.AbstractIntegrationTests;
import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;


public class Pointcut_11AspectJExprMatching_Tests extends AbstractIntegrationTests {

    @Test
    public void testLogicalOperator() {
        LogicalOperator_Object object = new LogicalOperator_Object();
        object.matchLogicalOperator(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(LogicalOperator_Aspect.MATCH_LOGICAL_OPERATOR_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(LogicalOperator_Aspect.MATCH_REFERNECE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        ExecutionMemento.clearMemento();


        object.matchLogicalOperator( Long.valueOf(1l) );

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(LogicalOperator_Aspect.MATCH_LOGICAL_OPERATOR_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(LogicalOperator_Aspect.MATCH_REFERNECE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }
    }

    private static class LogicalOperator_Object {

        public long matchLogicalOperator(long input) {
            return input;
        }

        public Long matchLogicalOperator(Long input) {
            return input;
        }
    }

    @Aspect
    public static class LogicalOperator_Aspect {

        private static final String MATCH_LOGICAL_OPERATOR_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Pointcut_11AspectJExprMatching_Tests$LogicalOperator_Object.matchLogicalOperator(long))"
                + " or "
                + "execution(!private java.lang.Long io.gemini.aop.integration.Pointcut_11AspectJExprMatching_Tests$LogicalOperator_Object.matchLogicalOperator(java.lang.Long))";

        private static final String MATCH_LOGICAL_OPERATOR_AFTER_ADVICE = LogicalOperator_Aspect.class.getName() + ".matchLogicalOperator_afterAdvice";

        @After(MATCH_LOGICAL_OPERATOR_POINTCUT)
        public void matchLogicalOperator_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_LOGICAL_OPERATOR_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }


        private static final String MATCH_REFERNECE_POINTCUT_AFTER_ADVICE = LogicalOperator_Aspect.class.getName() + ".matchReferencePointcut_afterAdvice";

        @Pointcut("execution(!private long io.gemini.aop.integration.Pointcut_11AspectJExprMatching_Tests$LogicalOperator_Object.matchLogicalOperator(long))")
        public void matchLogicalOperator() { }

        @Pointcut("execution(!private java.lang.Long io.gemini.aop.integration.Pointcut_11AspectJExprMatching_Tests$LogicalOperator_Object.matchLogicalOperator(java.lang.Long))")
        public void matchLogicalOperator2() { }

        @After("matchLogicalOperator() or matchLogicalOperator2()")
        public void matchReferencePointcut_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_REFERNECE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }
    }


    @Test
    public void testPlaceholder() {
        Placeholder_Object object = new Placeholder_Object();
        object.matchPlaceholder(1l);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(Placeholder_Aspect.MATCH_PLACEHOLDER_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(Placeholder_Aspect.MATCH_REFERNECE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        ExecutionMemento.clearMemento();


        object.matchPlaceholder( Long.valueOf(1l) );

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(Placeholder_Aspect.MATCH_PLACEHOLDER_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(Placeholder_Aspect.MATCH_REFERNECE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        }
    }

    private static class Placeholder_Object {

        public long matchPlaceholder(long input) {
            return input;
        }

        public Long matchPlaceholder(Long input) {
            return input;
        }
    }

    @Aspect
    public static class Placeholder_Aspect {

        private static final String MATCH_PLACEHOLDER_AFTER_ADVICE = Placeholder_Aspect.class.getName() + ".matchPlaceholdrer_afterAdvice";

        @After("${user.Pointcut_01JoinpointMatching_Tests.placeholderAdvisorsPointcutExpr}")
        public void matchPlaceholder_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_PLACEHOLDER_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }


        private static final String MATCH_REFERNECE_POINTCUT_AFTER_ADVICE = Placeholder_Aspect.class.getName() + ".matchReferencePointcut_afterAdvice";

        @Pointcut("${user.Pointcut_01JoinpointMatching_Tests.placeholderAdvisorsPointcutExpr2}")
        public void matchPlaceholder2() { }

        @Pointcut("${user.Pointcut_01JoinpointMatching_Tests.placeholderAdvisorsPointcutExpr3}")
        public void matchPlaceholder3() { }

        @After("matchPlaceholder2() or matchPlaceholder3()")
        public void matchReferencePointcut_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_REFERNECE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }
    }
}