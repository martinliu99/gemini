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
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class Aspect_21JoinpointVisibility_Tests extends AbstractIntegrationTests {

    @Test
    public void testTargetObjectVisibility() {
        TargetObjectVisibility_Objects objects = new TargetObjectVisibility_Objects();
        objects.accessTargetObject();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetObjectVisibility_Aspects.ACCESS_TARGET_OBJECT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class TargetObjectVisibility_Objects {

        public long accessTargetObject() {
            return 1l;
        }
    }

    @Aspect
    public static class TargetObjectVisibility_Aspects {

        private static final String ACCESS_TARGET_OBJECT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Aspect_21JoinpointVisibility_Tests$TargetObjectVisibility_Objects.accessTargetObject()) && this(targetObject)";

        private static final String ACCESS_TARGET_OBJECT_AFTER_ADVICE = TargetObjectVisibility_Aspects.class.getName() + ".accessTargetObject_afterAdvice";

        @After(value = ACCESS_TARGET_OBJECT_POINTCUT, argNames = "targetObject")
        public void accessTargetObject_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, TargetObjectVisibility_Objects targetObject) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_OBJECT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(targetObject) );
        }
    }


    @Test
    public void testTargetArgumentVisibility() {
        TargetArgumentVisibility_Objects objects = new TargetArgumentVisibility_Objects();
        objects.accessTargetArgument(new TargetArgumentVisibility_Objects.Argument());

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentVisibility_Aspects.ACCESS_TARGET_ARGUMENT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class TargetArgumentVisibility_Objects {

        public long accessTargetArgument(Argument argument) {
            return 1l;
        }

        private static class Argument {}
    }

    @Aspect
    public static class TargetArgumentVisibility_Aspects {

        private static final String ACCESS_TARGET_ARGUMENT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Aspect_21JoinpointVisibility_Tests$TargetArgumentVisibility_Objects.accessTargetArgument(..)) && args(argument)";

        private static final String ACCESS_TARGET_ARGUMENT_AFTER_ADVICE = TargetArgumentVisibility_Aspects.class.getName() + ".accessTargetArgument_afterAdvice";

        @After(value = ACCESS_TARGET_ARGUMENT_POINTCUT, argNames = "argument")
        public void accessTargetArgument_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, TargetArgumentVisibility_Objects.Argument argument) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_ARGUMENT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {argument}) );
        }
    }


    @Test
    public void testTargetReturningVisibility() {
        TargetReturningVisibility_Objects objects = new TargetReturningVisibility_Objects();
        objects.accessTargetReturning();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningVisibility_Aspects.ACCESS_TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class TargetReturningVisibility_Objects {

        public Returning accessTargetReturning() {
            return new Returning();
        }

        static class Returning {}
    }

    @Aspect
    public static class TargetReturningVisibility_Aspects {

        private static final String ACCESS_TARGET_RETURNING_POINTCUT = 
                "execution(* io.gemini.aop.integration.Aspect_21JoinpointVisibility_Tests$TargetReturningVisibility_Objects.accessTargetReturning())";

        private static final String ACCESS_TARGET_RETURNING_AFTER_ADVICE = TargetReturningVisibility_Aspects.class.getName() + ".accessTargetReturning_afterAdvice";

        @SuppressWarnings("rawtypes")
        @AfterReturning(value = ACCESS_TARGET_RETURNING_POINTCUT, returning = "returning")
        public void accessTargetReturning_afterAdvice(MutableJoinpoint joinpoint, TargetReturningVisibility_Objects.Returning returning) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }
    }


    @Test
    public void testTargetThrowingVisibility() {
        try {
            new TargetThrowingVisibility_Objects( new TargetThrowingVisibility_Objects.ExceptionA_Objects("expected") )
            .accessTargetThrowing();
            assertThat(false).isTrue();
        } catch(Exception actualException) {
            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingVisibility_Aspects.ACCESS_TARGET_THROWING_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }
        }
    }

    protected static class TargetThrowingVisibility_Objects {

        private ExceptionA_Objects cause;


        public TargetThrowingVisibility_Objects(ExceptionA_Objects cause) {
            this.cause = cause;
        }

        public void accessTargetThrowing() throws ExceptionA_Objects {
            throw cause;
        }


        static class ExceptionA_Objects extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = 3890639293213333957L;

            public ExceptionA_Objects(String message) {
                super(message);
            }
        }
    }

    @Aspect
    public static class TargetThrowingVisibility_Aspects {

        private static final String ACCESS_TARGET_THROWING_POINTCUT = 
                "execution(!private void io.gemini.aop.integration.Aspect_21JoinpointVisibility_Tests$TargetThrowingVisibility_Objects.accessTargetThrowing())";

        private static final String ACCESS_TARGET_THROWING_AFTER_ADVICE = TargetThrowingVisibility_Aspects.class.getName() + ".accessTargetThrowing_afterAdvice";

        @SuppressWarnings("rawtypes")
        @AfterThrowing(value = ACCESS_TARGET_THROWING_POINTCUT, throwing = "throwing")
        public void accessTargetThrowing_afterAdvice(MutableJoinpoint joinpoint, TargetThrowingVisibility_Objects.ExceptionA_Objects throwing) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }
    }


    @Test
    public void testParametrizedReturningVisibility() {
        ParametrizedReturningVisibility_Objects objects = new ParametrizedReturningVisibility_Objects();
        objects.accessParametrizedReturning();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ParametrizedReturningVisibility_Aspects.ACCESS_TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class ParametrizedReturningVisibility_Objects {

        public Returning accessParametrizedReturning() {
            return new Returning();
        }

        private static class Returning {}
    }

    @Aspect
    public static class ParametrizedReturningVisibility_Aspects {

        private static final String ACCESS_TARGET_RETURNING_POINTCUT = 
                "execution(!private io.gemini.aop.integration.Aspect_21JoinpointVisibility_Tests$ParametrizedReturningVisibility_Objects$Returning io.gemini.aop.integration.Aspect_21JoinpointVisibility_Tests$ParametrizedReturningVisibility_Objects.accessParametrizedReturning())";

        private static final String ACCESS_TARGET_RETURNING_AFTER_ADVICE = ParametrizedReturningVisibility_Aspects.class.getName() + ".accessParametrizedReturning_afterAdvice";

        @After(value = ACCESS_TARGET_RETURNING_POINTCUT)
        public void accessParametrizedReturning_afterAdvice(MutableJoinpoint<ParametrizedReturningVisibility_Objects.Returning, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testParametrizedThrowingingVisibility() {
        try {
            new ParametrizedThrowingingVisibility_Objects( new ParametrizedThrowingingVisibility_Objects.ExceptionA_Objects("expected") )
            .accessParametrizedThrowinging();
            assertThat(false).isTrue();
        } catch(Exception actualException) {
            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ParametrizedThrowingingVisibility_Aspects.ACCESS_TARGET_THROWING_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }
        }
    }

    protected static class ParametrizedThrowingingVisibility_Objects {

        private ExceptionA_Objects cause;


        public ParametrizedThrowingingVisibility_Objects(ExceptionA_Objects cause) {
            this.cause = cause;
        }

        public void accessParametrizedThrowinging() throws ExceptionA_Objects {
            throw cause;
        }


        private static class ExceptionA_Objects extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = 3890639293213333957L;

            public ExceptionA_Objects(String message) {
                super(message);
            }
        }
    }

    @Aspect
    public static class ParametrizedThrowingingVisibility_Aspects {

        private static final String ACCESS_TARGET_THROWING_POINTCUT = 
                "execution(!private void io.gemini.aop.integration.Aspect_21JoinpointVisibility_Tests$ParametrizedThrowingingVisibility_Objects.accessParametrizedThrowinging())";

        private static final String ACCESS_TARGET_THROWING_AFTER_ADVICE = ParametrizedThrowingingVisibility_Aspects.class.getName() + ".accessParametrizedThrowinging_afterAdvice";

        @After(value = ACCESS_TARGET_THROWING_POINTCUT)
        public void accessParametrizedThrowinging_afterAdvice(MutableJoinpoint<Void, ParametrizedThrowingingVisibility_Objects.ExceptionA_Objects> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ACCESS_TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(joinpoint.getThrowing()) );
        }
    }
}
