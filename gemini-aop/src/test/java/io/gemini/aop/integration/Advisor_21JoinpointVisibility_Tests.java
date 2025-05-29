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
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;

import io.gemini.aop.test.AbstractIntegrationTests;
import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class Advisor_21JoinpointVisibility_Tests extends AbstractIntegrationTests {

    @Test
    public void testTargetObjectVisibility() {
        TargetObjectVisibility_Object object = new TargetObjectVisibility_Object();
        object.accessTargetObject();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetObjectVisibility_Aspect.ACCESS_TARGET_OBJECT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class TargetObjectVisibility_Object {

        public long accessTargetObject() {
            return 1l;
        }
    }

    @Aspect
    public static class TargetObjectVisibility_Aspect {

        private static final String ACCESS_TARGET_OBJECT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_21JoinpointVisibility_Tests$TargetObjectVisibility_Object.accessTargetObject()) && this(targetObject)";

        private static final String ACCESS_TARGET_OBJECT_AFTER_ADVICE = TargetObjectVisibility_Aspect.class.getName() + ".accessTargetObject_afterAdvice";

        @After(value = ACCESS_TARGET_OBJECT_POINTCUT, argNames = "targetObject")
        public void accessTargetObject_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, TargetObjectVisibility_Object targetObject) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_OBJECT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(targetObject) );
        }
    }


    @Test
    public void testTargetArgumentVisibility() {
        TargetArgumentVisibility_Object object = new TargetArgumentVisibility_Object();
        object.accessTargetArgument(new TargetArgumentVisibility_Object.Argument());

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentVisibility_Aspect.ACCESS_TARGET_ARGUMENT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class TargetArgumentVisibility_Object {

        public long accessTargetArgument(Argument argument) {
            return 1l;
        }

        private static class Argument {}
    }

    @Aspect
    public static class TargetArgumentVisibility_Aspect {

        private static final String ACCESS_TARGET_ARGUMENT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_21JoinpointVisibility_Tests$TargetArgumentVisibility_Object.accessTargetArgument(..)) && args(argument)";

        private static final String ACCESS_TARGET_ARGUMENT_AFTER_ADVICE = TargetArgumentVisibility_Aspect.class.getName() + ".accessTargetArgument_afterAdvice";

        @After(value = ACCESS_TARGET_ARGUMENT_POINTCUT, argNames = "argument")
        public void accessTargetArgument_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, TargetArgumentVisibility_Object.Argument argument) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_ARGUMENT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {argument}) );
        }
    }


    @Test
    public void testTargetReturningVisibility() {
        TargetReturningVisibility_Object object = new TargetReturningVisibility_Object();
        object.accessTargetReturning();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningVisibility_Aspect.ACCESS_TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class TargetReturningVisibility_Object {

        public Returning accessTargetReturning() {
            return new Returning();
        }

        static class Returning {}
    }

    @Aspect
    public static class TargetReturningVisibility_Aspect {

        private static final String ACCESS_TARGET_RETURNING_POINTCUT = 
                "execution(* io.gemini.aop.integration.Advisor_21JoinpointVisibility_Tests$TargetReturningVisibility_Object.accessTargetReturning())";

        private static final String ACCESS_TARGET_RETURNING_AFTER_ADVICE = TargetReturningVisibility_Aspect.class.getName() + ".accessTargetReturning_afterAdvice";

        @SuppressWarnings("rawtypes")
        @AfterReturning(value = ACCESS_TARGET_RETURNING_POINTCUT, returning = "returning")
        public void accessTargetReturning_afterAdvice(MutableJoinpoint joinpoint, TargetReturningVisibility_Object.Returning returning) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }
    }


    @Test
    public void testTargetThrowingVisibility() {
        try {
            new TargetThrowingVisibility_Object( new TargetThrowingVisibility_Object.ExceptionA_Object("expected") )
            .accessTargetThrowing();
            assertThat(false).isTrue();
        } catch(Exception actualException) {
            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingVisibility_Aspect.ACCESS_TARGET_THROWING_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }
        }
    }

    protected static class TargetThrowingVisibility_Object {

        private ExceptionA_Object cause;


        public TargetThrowingVisibility_Object(ExceptionA_Object cause) {
            this.cause = cause;
        }

        public void accessTargetThrowing() throws ExceptionA_Object {
            throw cause;
        }


        static class ExceptionA_Object extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = 3890639293213333957L;

            public ExceptionA_Object(String message) {
                super(message);
            }
        }
    }

    @Aspect
    public static class TargetThrowingVisibility_Aspect {

        private static final String ACCESS_TARGET_THROWING_POINTCUT = 
                "execution(!private void io.gemini.aop.integration.Advisor_21JoinpointVisibility_Tests$TargetThrowingVisibility_Object.accessTargetThrowing())";

        private static final String ACCESS_TARGET_THROWING_AFTER_ADVICE = TargetThrowingVisibility_Aspect.class.getName() + ".accessTargetThrowing_afterAdvice";

        @SuppressWarnings("rawtypes")
        @AfterThrowing(value = ACCESS_TARGET_THROWING_POINTCUT, throwing = "throwing")
        public void accessTargetThrowing_afterAdvice(MutableJoinpoint joinpoint, TargetThrowingVisibility_Object.ExceptionA_Object throwing) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }
    }


    @Test
    public void testParametrizedReturningVisibility() {
        ParametrizedReturningVisibility_Object object = new ParametrizedReturningVisibility_Object();
        object.accessParametrizedReturning();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ParametrizedReturningVisibility_Aspect.ACCESS_TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class ParametrizedReturningVisibility_Object {

        public Returning accessParametrizedReturning() {
            return new Returning();
        }

        private static class Returning {}
    }

    @Aspect
    public static class ParametrizedReturningVisibility_Aspect {

        private static final String ACCESS_TARGET_RETURNING_POINTCUT = 
                "execution(!private io.gemini.aop.integration.Advisor_21JoinpointVisibility_Tests$ParametrizedReturningVisibility_Object$Returning io.gemini.aop.integration.Advisor_21JoinpointVisibility_Tests$ParametrizedReturningVisibility_Object.accessParametrizedReturning())";

        private static final String ACCESS_TARGET_RETURNING_AFTER_ADVICE = ParametrizedReturningVisibility_Aspect.class.getName() + ".accessParametrizedReturning_afterAdvice";

        @After(value = ACCESS_TARGET_RETURNING_POINTCUT)
        public void accessParametrizedReturning_afterAdvice(MutableJoinpoint<ParametrizedReturningVisibility_Object.Returning, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testParametrizedThrowingingVisibility() {
        try {
            new ParametrizedThrowingingVisibility_Object( new ParametrizedThrowingingVisibility_Object.ExceptionA_Object("expected") )
            .accessParametrizedThrowinging();
            assertThat(false).isTrue();
        } catch(Exception actualException) {
            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ParametrizedThrowingingVisibility_Aspect.ACCESS_TARGET_THROWING_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }
        }
    }

    protected static class ParametrizedThrowingingVisibility_Object {

        private ExceptionA_Object cause;


        public ParametrizedThrowingingVisibility_Object(ExceptionA_Object cause) {
            this.cause = cause;
        }

        public void accessParametrizedThrowinging() throws ExceptionA_Object {
            throw cause;
        }


        private static class ExceptionA_Object extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = 3890639293213333957L;

            public ExceptionA_Object(String message) {
                super(message);
            }
        }
    }

    @Aspect
    public static class ParametrizedThrowingingVisibility_Aspect {

        private static final String ACCESS_TARGET_THROWING_POINTCUT = 
                "execution(!private void io.gemini.aop.integration.Advisor_21JoinpointVisibility_Tests$ParametrizedThrowingingVisibility_Object.accessParametrizedThrowinging())";

        private static final String ACCESS_TARGET_THROWING_AFTER_ADVICE = ParametrizedThrowingingVisibility_Aspect.class.getName() + ".accessParametrizedThrowinging_afterAdvice";

        @After(value = ACCESS_TARGET_THROWING_POINTCUT)
        public void accessParametrizedThrowinging_afterAdvice(MutableJoinpoint<Void, ParametrizedThrowingingVisibility_Object.ExceptionA_Object> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(joinpoint.getThrowing()) );
        }
    }
}
