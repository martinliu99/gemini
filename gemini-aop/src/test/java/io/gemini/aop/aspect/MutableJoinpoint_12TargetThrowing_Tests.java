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
package io.gemini.aop.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.aspect.Joinpoint.MutableJoinpoint;
import io.gemini.aop.test.AbstractBaseTests;
import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.aop.test.ExecutionMemento.TargetMethod;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class MutableJoinpoint_12TargetThrowing_Tests extends AbstractBaseTests {

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
            try {
                new TypeInitilizer_AfterAdvice_Objects();
            } catch(ExceptionInInitializerError e) {
                AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_AfterAdviceAspect.ADVICE_TYPE_INITIALIZER);
                assertThat(adviceMethodInvoker).isNotNull();
                assertThat(adviceMethodInvoker.isInvoked()).isTrue();
                assertThat(adviceMethodInvoker.getThrowing()).isEqualTo(e.getCause());

                TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_AfterAdvice_Objects.class.getName());
                assertThat(targetMethodInvoker).isNotNull();
                assertThat(targetMethodInvoker.isInvoked()).isTrue();
                assertThat(targetMethodInvoker.getThrowing()).isEqualTo(e.getCause());
            }

            try {
                new TypeInitilizer_AfterAdvice_Objects();
            } catch(Throwable t) {
                assertThat(t).isInstanceOf(NoClassDefFoundError.class);
            }
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
        @Before("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_12TargetThrowing_Tests$TypeInitilizer_BeforeAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            try {
                joinpoint.getThrowing();
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
            try {
                throwing();
            } catch(RuntimeException e) {
                ExecutionMemento.putTargetMethodInvoker(TypeInitilizer_AfterAdvice_Objects.class.getName(), 
                        new TargetMethod()
                            .withInvoked(true)
                            .withThrowing(e) );
                throw e;
            }
        }

        static final Logger LOGGER;

        static {
            LOGGER = LoggerFactory.getLogger(TypeInitilizer_AfterAdvice_Objects.class);
        }

        private static void throwing() {
            throw new RuntimeException();
        }
    }

    @Aspect
    public static class TypeInitilizer_AfterAdviceAspect {

        private static final String ADVICE_TYPE_INITIALIZER = "typeInitializer";

        @SuppressWarnings("rawtypes")
        @After("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_12TargetThrowing_Tests$TypeInitilizer_AfterAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(joinpoint.getThrowing()) );
        }
    }


    @Test
    public void testClassMethod() {
        try {
            ClassMethod_Objects.targetThrowing();
            assertThat(false).isTrue();
        } catch(IOException expected) {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.TARGET_THROWING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getThrowing()).isInstanceOf(IllegalStateException.class);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.TARGET_THROWING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(ClassMethod_Objects.TARGET_THROWING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getThrowing()).isEqualTo(expected);
        }
    }

    public static class ClassMethod_Objects {

        private static final String TARGET_THROWING = "targetThrowing";

        public static void targetThrowing() throws IOException {
            IOException exp = new IOException();
            ExecutionMemento.putTargetMethodInvoker(TARGET_THROWING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThrowing(exp) );
            throw exp;
        }
    }

    @Aspect
    public static class ClassMethod_Advices {

        private static final String TARGET_THROWING_POINTCUT = 
                "execution(public static * io.gemini.aop.aspect.MutableJoinpoint_12TargetThrowing_Tests$ClassMethod_Objects.targetThrowing())";

        private static final String TARGET_THROWING_BEFORE_ADVICE = "targetThrowing_before";
        private static final String TARGET_THROWING_AFTER_ADVICE = "targetThrowing_after";

        @Before(TARGET_THROWING_POINTCUT)
        public void targetThrowing_before(MutableJoinpoint<Void, IOException> joinpoint) {
            try {
                joinpoint.getThrowing();
            } catch(Exception e) {
                ExecutionMemento.putAdviceMethodInvoker(TARGET_THROWING_BEFORE_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withThrowing(e) );
            }
        }

        @After(TARGET_THROWING_POINTCUT)
        public void targetThrowing_after(MutableJoinpoint<Void, IOException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(joinpoint.getThrowing()) );
        }
    }


    @Test
    public void testInstanceConstructor() {
        try {
            new InstanceConstructor_Objects(1l);
            assertThat(false).isTrue();
        } catch(IOException expected) {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.TARGET_THROWING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getThrowing()).isInstanceOf(IllegalStateException.class);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.TARGET_THROWING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceConstructor_Objects.TARGET_THROWING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getThrowing()).isEqualTo(expected);
        }
    }

    public static class InstanceConstructor_Objects {

        private static final String TARGET_THROWING = "targetThrowing";

        public InstanceConstructor_Objects(long throwing) throws IOException {
            IOException exp = new IOException();
            ExecutionMemento.putTargetMethodInvoker(TARGET_THROWING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThrowing(exp) );

            throw exp;
        }
    }

    @Aspect
    public static class InstanceConstructor_Advices {

        private static final String TARGET_THROWING_POINTCUT = 
                "execution(public io.gemini.aop.aspect.MutableJoinpoint_12TargetThrowing_Tests$InstanceConstructor_Objects.new(long))";

        private static final String TARGET_THROWING_BEFORE_ADVICE = "targetThrowing_before";
        private static final String TARGET_THROWING_AFTER_ADVICE = "targetThrowing_after";

        @SuppressWarnings("rawtypes")
        @Before(TARGET_THROWING_POINTCUT)
        public void targetThrowing_before(MutableJoinpoint joinpoint) {
            try {
                joinpoint.getThrowing();
            } catch(Exception e) {
                ExecutionMemento.putAdviceMethodInvoker(TARGET_THROWING_BEFORE_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withThrowing(e) );
            }
        }

        @SuppressWarnings("rawtypes")
        @After(TARGET_THROWING_POINTCUT)
        public void targetThrowing_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(joinpoint.getThrowing()) );
        }
    }


    @Test
    public void testInstanceMethod() {
        InstanceMethod_Objects thisObject = new InstanceMethod_Objects();

        try {
            thisObject.targetThrowing();
            assertThat(false).isTrue();
        } catch(IOException expected) {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.TARGET_THROWING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getThrowing()).isInstanceOf(IllegalStateException.class);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.TARGET_THROWING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceMethod_Objects.TARGET_THROWING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getThrowing()).isEqualTo(expected);
        }
    }

    public static class InstanceMethod_Objects {

        private static final String TARGET_THROWING = "targetThrowing";

        public void targetThrowing() throws IOException {
            IOException exp = new IOException();
            ExecutionMemento.putTargetMethodInvoker(TARGET_THROWING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThrowing(exp) );
            throw exp;
        }
    }

    @Aspect
    public static class InstanceMethod_Aspects {

        private static final String TARGET_THROWING_POINTCUT = 
                "execution(public * io.gemini.aop.aspect.MutableJoinpoint_12TargetThrowing_Tests$InstanceMethod_Objects.targetThrowing())";

        private static final String TARGET_THROWING_BEFORE_ADVICE = "targetThrowing_before";
        private static final String TARGET_THROWING_AFTER_ADVICE = "targetThrowing_after";

        @SuppressWarnings("rawtypes")
        @Before(TARGET_THROWING_POINTCUT)
        public void targetThrowing_before(MutableJoinpoint joinpoint) {
            try {
                joinpoint.getThrowing();
            } catch(Exception e) {
                ExecutionMemento.putAdviceMethodInvoker(TARGET_THROWING_BEFORE_ADVICE, 
                        new AdviceMethod()
                            .withInvoked(true)
                            .withThrowing(e) );
            }
        }

        @SuppressWarnings("rawtypes")
        @After(TARGET_THROWING_POINTCUT)
        public void targetThrowing_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(joinpoint.getThrowing()) );
        }
    }
}
