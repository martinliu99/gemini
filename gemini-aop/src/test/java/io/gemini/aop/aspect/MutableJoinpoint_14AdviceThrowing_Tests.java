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
public class MutableJoinpoint_14AdviceThrowing_Tests extends AbstractBaseTests {

    @Test
    public void testTypeInitilizer() {
        {
            try {
                new TypeInitilizer_BeforeAdvice_Objects();
            } catch(ExceptionInInitializerError e) {
                AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_BeforeAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
                assertThat(adviceMethodInvoker).isNotNull();
                assertThat(adviceMethodInvoker.isInvoked()).isTrue();

                assertThat(e.getCause()).isEqualTo(adviceMethodInvoker.getThrowing());

                TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_BeforeAdvice_Objects.class.getName());
                assertThat(targetMethodInvoker).isNull();
            }

            try {
                new TypeInitilizer_BeforeAdvice_Objects();
            } catch(Throwable t) {
                assertThat(t).isInstanceOf(NoClassDefFoundError.class);
            }
        }

        {
            new TypeInitilizer_BeforeAdvice_WithWrongType_Objects();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_BeforeAdvice_WithWrongType_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_BeforeAdvice_WithWrongType_Objects.class.getName());
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
        }

        {
            try {
                new TypeInitilizer_AfterAdvice_Objects();
            } catch(ExceptionInInitializerError e) {
                AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_AfterAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
                assertThat(adviceMethodInvoker).isNotNull();
                assertThat(adviceMethodInvoker.isInvoked()).isTrue();

                assertThat(e.getCause()).isEqualTo(adviceMethodInvoker.getThrowing());

                TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_AfterAdvice_Objects.class.getName());
                assertThat(targetMethodInvoker).isNull();
            }

            try {
                new TypeInitilizer_AfterAdvice_Objects();
            } catch(Throwable t) {
                assertThat(t).isInstanceOf(NoClassDefFoundError.class);
            }
        }

        {
            new TypeInitilizer_AfterAdvice_WithWrongType_Objects();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_AfterAdvice_WithWrongType_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_AfterAdvice_WithWrongType_Objects.class.getName());
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

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Before("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$TypeInitilizer_BeforeAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            RuntimeException throwing = new RuntimeException();
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
            joinpoint.setAdviceThrowing(throwing);
        }
    }


    public static class TypeInitilizer_BeforeAdvice_WithWrongType_Objects {
        static {
            ExecutionMemento.putTargetMethodInvoker(TypeInitilizer_BeforeAdvice_WithWrongType_Objects.class.getName(), 
                    new TargetMethod().withInvoked(true) );
        }

        static final Logger LOGGER;

        static {
            LOGGER = LoggerFactory.getLogger(TypeInitilizer_BeforeAdvice_WithWrongType_Objects.class);
        }
    }


    @Aspect
    public static class TypeInitilizer_BeforeAdvice_WithWrongType_Aspects {

        private static final String ADVICE_TYPE_INITIALIZER = "typeInitializer";

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Before("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$TypeInitilizer_BeforeAdvice_WithWrongType_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            Exception throwing = new Exception();
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
            joinpoint.setAdviceThrowing(throwing);
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

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Before("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$TypeInitilizer_AfterAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            RuntimeException throwing = new RuntimeException();
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
            joinpoint.setAdviceThrowing(throwing);
        }
    }


    public static class TypeInitilizer_AfterAdvice_WithWrongType_Objects {
        static {
            ExecutionMemento.putTargetMethodInvoker(TypeInitilizer_AfterAdvice_WithWrongType_Objects.class.getName(), 
                    new TargetMethod().withInvoked(true) );
        }

        static final Logger LOGGER;

        static {
            LOGGER = LoggerFactory.getLogger(TypeInitilizer_AfterAdvice_WithWrongType_Objects.class);
        }
    }


    @Aspect
    public static class TypeInitilizer_AfterAdvice_WithWrongType_Aspects {

        private static final String ADVICE_TYPE_INITIALIZER = "typeInitializer";

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Before("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$TypeInitilizer_AfterAdvice_WithWrongType_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            Exception throwing = new Exception();
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
            joinpoint.setAdviceThrowing(throwing);
        }
    }


    @Test
    public void testClassMethod() {
        try {
            ClassMethod_Objects.beforeAdviceThrowing();
        } catch(Throwable expected) {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.BEFORE_ADVICE_THROWING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getThrowing()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.BEFORE_ADVICE_THROWING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(ClassMethod_Objects.BEFORE_ADVICE_THROWING);
            assertThat(targetMethodInvoker).isNull();
        }

        try {
            ClassMethod_Objects.afterAdviceThrowing();
        } catch(Throwable expected) {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.AFTER_ADVICE_THROWING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.AFTER_ADVICE_THROWING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(ClassMethod_Objects.AFTER_ADVICE_THROWING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getThrowing()).isNotEqualTo(expected);
        }
    }

    public static class ClassMethod_Objects {

        private static final String BEFORE_ADVICE_THROWING = "beforeAdviceThrowing";
        private static final String AFTER_ADVICE_THROWING = "afterAdviceThrowing";

        public static int beforeAdviceThrowing() throws IOException {
            IOException exp = new IOException();
            ExecutionMemento.putTargetMethodInvoker(BEFORE_ADVICE_THROWING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThrowing(exp) );
            throw exp;
        }

        public static int afterAdviceThrowing() throws IOException {
            IOException exp = new IOException();
            ExecutionMemento.putTargetMethodInvoker(AFTER_ADVICE_THROWING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThrowing(exp) );
            throw exp;
        }
    }

    @Aspect
    public static class ClassMethod_Advices {

        private static final String BEFORE_ADVICE_THROWING_POINTCUT = 
                "execution(public static * io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$ClassMethod_Objects.beforeAdviceThrowing())";

        private static final String BEFORE_ADVICE_THROWING_BEFORE_ADVICE = "beforeAdviceThrowing_before";
        private static final String BEFORE_ADVICE_THROWING_AFTER_ADVICE = "beforeAdviceThrowWing_after";

        @Before(BEFORE_ADVICE_THROWING_POINTCUT)
        public void beforeAdviceThrowing_before(MutableJoinpoint<Integer, IOException> joinpoint) {
            IOException adviceExp = new IOException();
            joinpoint.setAdviceThrowing(adviceExp);
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_THROWING_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(adviceExp) );
        }

        @After(BEFORE_ADVICE_THROWING_POINTCUT)
        public void beforeAviceThrowing_after(MutableJoinpoint<Integer, IOException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_THROWING_AFTER_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }


        private static final String AFTER_ADVICE_THROWING_BEFORE_ADVICE = "afterAdviceThrowing_before";
        private static final String AFTER_ADVICE_THROWING_AFTER_ADVICE = "afterAdviceThrowing_after";
        private static final String AFTER_ADVICE_THROWING_POINTCUT = "execution(public static * io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$ClassMethod_Objects.afterAdviceThrowing())";

        @Before(AFTER_ADVICE_THROWING_POINTCUT)
        public void afterAdviceThrowing_before(MutableJoinpoint<Integer, IOException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_THROWING_BEFORE_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(AFTER_ADVICE_THROWING_POINTCUT)
        public void afterAviceThrowing_after(MutableJoinpoint<Integer, IOException> joinpoint) {
            IOException adviceExp = new IOException();
            joinpoint.setAdviceThrowing(adviceExp);
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(adviceExp) );
        }
    }


    @Test
    public void testInstanceConstructor() {
        try {
            new InstanceConstructor_Objects(true);
        } catch(Throwable expected) {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.BEFORE_ADVICE_THROWING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getThrowing()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.BEFORE_ADVICE_THROWING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceConstructor_Objects.BEFORE_ADVICE_THROWING);
            assertThat(targetMethodInvoker).isNull();
        }

        try {
            new InstanceConstructor_Objects( (byte)1);
        } catch(Throwable expected) {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.AFTER_ADVICE_THROWING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.AFTER_ADVICE_THROWING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceConstructor_Objects.AFTER_ADVICE_THROWING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getThrowing()).isNull();
        }
    }

    public static class InstanceConstructor_Objects {

        private static final String BEFORE_ADVICE_THROWING = "beforeAdviceThrowing";
        private static final String AFTER_ADVICE_THROWING = "afterAdviceThrowing";

        public InstanceConstructor_Objects(boolean before) throws IOException {
            ExecutionMemento.putTargetMethodInvoker(BEFORE_ADVICE_THROWING, 
                    new TargetMethod().withInvoked(true) );
        }

        public InstanceConstructor_Objects(byte after) throws IOException {
            ExecutionMemento.putTargetMethodInvoker(AFTER_ADVICE_THROWING, 
                    new TargetMethod().withInvoked(true) );
        }

        public InstanceConstructor_Objects() {
        }
    }

    @Aspect
    public static class InstanceConstructor_Advices {

        private static final String BEFORE_ADVICE_THROWING_POINTCUT = 
                "execution(public io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$InstanceConstructor_Objects.new(boolean))";

        private static final String BEFORE_ADVICE_THROWING_BEFORE_ADVICE = "beforeAdviceThrowing_before";
        private static final String BEFORE_ADVICE_THROWING_AFTER_ADVICE = "beforeAdviceThrowWing_after";

        @Before(BEFORE_ADVICE_THROWING_POINTCUT)
        public void beforeAdviceThrowing_before(MutableJoinpoint<InstanceConstructor_Objects, IOException> joinpoint) {
            IOException adviceExp = new IOException();
            joinpoint.setAdviceThrowing(adviceExp);
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_THROWING_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(adviceExp) );
        }

        @After(BEFORE_ADVICE_THROWING_POINTCUT)
        public void beforeAviceThrowing_after(MutableJoinpoint<InstanceConstructor_Objects, IOException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_THROWING_AFTER_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }


        private static final String AFTER_ADVICE_THROWING_POINTCUT = 
                "execution(public io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$InstanceConstructor_Objects.new(byte))";

        private static final String AFTER_ADVICE_THROWING_BEFORE_ADVICE = "afterAdviceThrowing_before";
        private static final String AFTER_ADVICE_THROWING_AFTER_ADVICE = "afterAdviceThrowing_after";

        @Before(AFTER_ADVICE_THROWING_POINTCUT)
        public void afterAdviceThrowing_before(MutableJoinpoint<InstanceConstructor_Objects, IOException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_THROWING_BEFORE_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(AFTER_ADVICE_THROWING_POINTCUT)
        public void afterAviceThrowing_after(MutableJoinpoint<InstanceConstructor_Objects, IOException> joinpoint) {
            IOException adviceExp = new IOException();
            joinpoint.setAdviceThrowing(adviceExp);
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(adviceExp) );
        }
    }


    @Test
    public void testInstanceMethod() {
        InstanceMethod_Objects thisObject = new InstanceMethod_Objects();

        try {
            thisObject.beforeAdviceThrowing();
        } catch(Throwable expected) {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.BEFORE_ADVICE_THROWING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getThrowing()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.BEFORE_ADVICE_THROWING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceMethod_Objects.BEFORE_ADVICE_THROWING);
            assertThat(targetMethodInvoker).isNull();
        }

        try {
            thisObject.afterAdviceThrowing();
        } catch(Throwable expected) {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.AFTER_ADVICE_THROWING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.AFTER_ADVICE_THROWING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceMethod_Objects.AFTER_ADVICE_THROWING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getThrowing()).isNotEqualTo(expected);
        }
    }

    public static class InstanceMethod_Objects {

        private static final String BEFORE_ADVICE_THROWING = "beforeAdviceThrowing";
        private static final String AFTER_ADVICE_THROWING = "afterAdviceThrowing";

        public int beforeAdviceThrowing() throws IOException {
            IOException exp = new IOException();
            ExecutionMemento.putTargetMethodInvoker(BEFORE_ADVICE_THROWING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThrowing(exp) );
            throw exp;
        }

        public int afterAdviceThrowing() throws IOException {
            IOException exp = new IOException();
            ExecutionMemento.putTargetMethodInvoker(AFTER_ADVICE_THROWING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThrowing(exp) );
            throw exp;
        }
    }

    @Aspect
    public static class InstanceMethod_Aspects {

        private static final String BEFORE_ADVICE_THROWING_POINTCUT = 
                "execution(public * io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$InstanceMethod_Objects.beforeAdviceThrowing())";

        private static final String BEFORE_ADVICE_THROWING_BEFORE_ADVICE = "beforeAdviceThrowing_before";
        private static final String BEFORE_ADVICE_THROWING_AFTER_ADVICE = "beforeAdviceThrowWing_after";

        @Before(BEFORE_ADVICE_THROWING_POINTCUT)
        public void beforeAdviceThrowing_before(MutableJoinpoint<Integer, IOException> joinpoint) {
            IOException adviceExp = new IOException();
            joinpoint.setAdviceThrowing(adviceExp);
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_THROWING_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(adviceExp) );
        }

        @After(BEFORE_ADVICE_THROWING_POINTCUT)
        public void beforeAviceThrowing_after(MutableJoinpoint<Integer, IOException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_THROWING_AFTER_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }


        private static final String AFTER_ADVICE_THROWING_POINTCUT = 
                "execution(public * io.gemini.aop.aspect.MutableJoinpoint_14AdviceThrowing_Tests$InstanceMethod_Objects.afterAdviceThrowing())";

        private static final String AFTER_ADVICE_THROWING_BEFORE_ADVICE = "afterAdviceThrowing_before";
        private static final String AFTER_ADVICE_THROWING_AFTER_ADVICE = "afterAdviceThrowing_after";

        @Before(AFTER_ADVICE_THROWING_POINTCUT)
        public void afterAdviceThrowing_before(MutableJoinpoint<Integer, IOException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_THROWING_BEFORE_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(AFTER_ADVICE_THROWING_POINTCUT)
        public void afterAviceThrowing_after(MutableJoinpoint<Integer, IOException> joinpoint) {
            IOException adviceExp = new IOException();
            joinpoint.setAdviceThrowing(adviceExp);
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(adviceExp) );
        }
    }
}
