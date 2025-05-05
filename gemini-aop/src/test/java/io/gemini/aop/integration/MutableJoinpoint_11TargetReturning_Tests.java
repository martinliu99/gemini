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
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.test.AbstractIntegrationTests;
import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.aop.test.ExecutionMemento.TargetMethod;
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class MutableJoinpoint_11TargetReturning_Tests extends AbstractIntegrationTests {

    @Test
    public void testTypeInitilizer() {
        {
            new TypeInitilizer_BeforeAdvice_Objects();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_BeforeAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();
            assertThat(adviceMethodInvoker.getThrowing()).isInstanceOf(IllegalStateException.class);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_BeforeAdvice_Objects.class.getName());
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
        }

        {
            new TypeInitilizer_AfterAdvice_Objects();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_AfterAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();
            assertThat(adviceMethodInvoker.getReturning()).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_AfterAdvice_Objects.class.getName());
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
        }
    }

    public static class TypeInitilizer_BeforeAdvice_Objects {
        static {
            ExecutionMemento.putTargetMethodInvoker(TypeInitilizer_BeforeAdvice_Objects.class.getName(), 
                    new TargetMethod().withInvoked(true) );
        }

        static final Logger LOGGER;

        static {
            LOGGER = LoggerFactory.getLogger(TypeInitilizer_BeforeAdvice_Objects.class);
        }
    }

    @Aspect
    public static class TypeInitilizer_BeforeAdvice_Aspects {

        private static final String ADVICE_TYPE_INITIALIZER = "typeInitializer";

        @SuppressWarnings("rawtypes")
        @Before("staticinitialization(io.gemini.aop.integration.MutableJoinpoint_11TargetReturning_Tests$TypeInitilizer_BeforeAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            try {
                joinpoint.getReturning();
            } catch(Exception e) {
                ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withThrowing(e) );
            }
        }
    }


    public static class TypeInitilizer_AfterAdvice_Objects {
        static {
            ExecutionMemento.putTargetMethodInvoker(TypeInitilizer_AfterAdvice_Objects.class.getName(), 
                    new TargetMethod().withInvoked(true) );
        }

        static final Logger LOGGER;

        static {
            LOGGER = LoggerFactory.getLogger(TypeInitilizer_AfterAdvice_Objects.class);
        }
    }

    @Aspect
    public static class TypeInitilizer_AfterAdvice_Aspects {

        private static final String ADVICE_TYPE_INITIALIZER = "typeInitializer";

        @SuppressWarnings("rawtypes")
        @After("staticinitialization(io.gemini.aop.integration.MutableJoinpoint_11TargetReturning_Tests$TypeInitilizer_AfterAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testClassMethod() {
        {
            int expected = ClassMethod_Objects.targetReturning();

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.TARGET_RETURNING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getReturning()).isInstanceOf(IllegalStateException.class);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(ClassMethod_Objects.TARGET_RETURNING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getReturning()).isEqualTo(expected);
        }
    }

    public static class ClassMethod_Objects {

        private static final String TARGET_RETURNING = "targetReturning";

        public static int targetReturning() {
            int returning = 100;
            ExecutionMemento.putTargetMethodInvoker(TARGET_RETURNING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
            return returning;
        }
    }

    @Aspect
    public static class ClassMethod_Advices {

        private static final String TARGET_RETURNING_POINTCUT = 
                "execution(public static * io.gemini.aop.integration.MutableJoinpoint_11TargetReturning_Tests$ClassMethod_Objects.targetReturning())";

        private static final String TARGET_RETURNING_BEFORE_ADVICE = "targetReturning_before";
        private static final String TARGET_RETURNING_AFTER_ADVICE = "targetReturning_after";

        @Before(TARGET_RETURNING_POINTCUT)
        public void targetReturning_before(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            try {
                joinpoint.getReturning();
            } catch(Exception e) {
                ExecutionMemento.putAdviceMethodInvoker(TARGET_RETURNING_BEFORE_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(e) );
            }
        }

        @After(TARGET_RETURNING_POINTCUT)
        public void targetReturning_after(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testInstanceConstructor() {

        {
            InstanceConstructor_Objects expected = new InstanceConstructor_Objects(1);

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.TARGET_RETURNING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getReturning()).isInstanceOf(IllegalStateException.class);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceConstructor_Objects.TARGET_RETURNING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getReturning()).isEqualTo(expected);
        }
    }

    public static class InstanceConstructor_Objects {

        private static final String TARGET_RETURNING = "targetReturning";

        public InstanceConstructor_Objects(int returning) {
            ExecutionMemento.putTargetMethodInvoker(TARGET_RETURNING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withReturning(this) );
        }
    }

    @Aspect
    public static class InstanceConstructor_Advices {

        private static final String TARGET_RETURNING_POINTCUT = 
                "execution(public io.gemini.aop.integration.MutableJoinpoint_11TargetReturning_Tests$InstanceConstructor_Objects.new(int))";

        private static final String TARGET_RETURNING_BEFORE_ADVICE = "targetReturning_before";
        private static final String TARGET_RETURNING_AFTER_ADVICE = "targetReturning_after";

        @SuppressWarnings("rawtypes")
        @Before(TARGET_RETURNING_POINTCUT)
        public void targetReturning_before(MutableJoinpoint joinpoint) {
            try {
                joinpoint.getReturning();
            } catch(Exception e) {
                ExecutionMemento.putAdviceMethodInvoker(TARGET_RETURNING_BEFORE_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(e) );
            }
        }

        @SuppressWarnings("rawtypes")
        @After(TARGET_RETURNING_POINTCUT)
        public void targetReturning_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testInstanceMethod() {
        InstanceMethod_Objects thisObject = new InstanceMethod_Objects();

        {
            int expected = thisObject.targetReturning();

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.TARGET_RETURNING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getReturning()).isInstanceOf(IllegalStateException.class);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceMethod_Objects.TARGET_RETURNING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getReturning()).isEqualTo(expected);
        }
    }

    public static class InstanceMethod_Objects {

        private static final String TARGET_RETURNING = "targetReturning";

        public int targetReturning() {
            int returning = 100;
            ExecutionMemento.putTargetMethodInvoker(TARGET_RETURNING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
            return returning;
        }
    }

    @Aspect
    public static class InstanceMethod_Aspects {

        private static final String TARGET_RETURNING_POINTCUT = 
                "execution(public * io.gemini.aop.integration.MutableJoinpoint_11TargetReturning_Tests$InstanceMethod_Objects.targetReturning())";

        private static final String TARGET_RETURNING_BEFORE_ADVICE = "targetReturning_before";
        private static final String TARGET_RETURNING_AFTER_ADVICE = "targetReturning_after";

        @Before(TARGET_RETURNING_POINTCUT)
        public void targetReturning_before(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            try {
                joinpoint.getReturning();
            } catch(Exception e) {
                ExecutionMemento.putAdviceMethodInvoker(TARGET_RETURNING_BEFORE_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withReturning(e) );
            }
        }

        @After(TARGET_RETURNING_POINTCUT)
        public void targetReturning_after(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }
}
