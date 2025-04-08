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
public class MutableJoinpoint_13AdviceReturning_Tests extends AbstractBaseTests {

    @Test
    public void testTypeInitilizer() {
        {
            new TypeInitilizer_BeforeAdvice_Objects();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_BeforeAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_BeforeAdvice_Objects.class.getName());
            assertThat(targetMethodInvoker).isNull();
        }

        {
            new TypeInitilizer_AfterAdvice_Objects();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_AfterAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_AfterAdvice_Objects.class.getName());
            assertThat(targetMethodInvoker).isNull();
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
        @Before("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_13AdviceReturning_Tests$TypeInitilizer_BeforeAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod().withInvoked(true) );
            joinpoint.setAdviceReturning(null);
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
        @Before("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_13AdviceReturning_Tests$TypeInitilizer_AfterAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod().withInvoked(true) );
            joinpoint.setAdviceReturning(null);
        }
    }


    @Test
    public void testClassMethod() {
        {
            int expected = ClassMethod_Objects.beforeAdviceReturning();

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.BEFORE_ADVICE_RETURNING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getReturning()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.BEFORE_ADVICE_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(ClassMethod_Objects.BEFORE_ADVICE_RETURNING);
            assertThat(targetMethodInvoker).isNull();
        }

        {
            int expected = ClassMethod_Objects.afterAdviceReturning();

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.AFTER_ADVICE_RETURNING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.AFTER_ADVICE_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(ClassMethod_Objects.AFTER_ADVICE_RETURNING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getReturning()).isNotEqualTo(expected);
        }
    }

    public static class ClassMethod_Objects {

        private static final String BEFORE_ADVICE_RETURNING = "beforeAdviceReturning";
        private static final String AFTER_ADVICE_RETURNING = "afterAdviceReturning";

        public static int beforeAdviceReturning() {
            int returning = 100;
            ExecutionMemento.putTargetMethodInvoker(BEFORE_ADVICE_RETURNING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
            return returning;
        }

        public static int afterAdviceReturning() {
            int returning = 100;
            ExecutionMemento.putTargetMethodInvoker(AFTER_ADVICE_RETURNING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
            return returning;
        }
    }

    @Aspect
    public static class ClassMethod_Advices {

        private static final String BEFORE_ADVICE_RETURNING_POINTCUT = 
                "execution(public static * io.gemini.aop.aspect.MutableJoinpoint_13AdviceReturning_Tests$ClassMethod_Objects.beforeAdviceReturning())";

        private static final String BEFORE_ADVICE_RETURNING_BEFORE_ADVICE = "beforeAdviceReturning_before";
        private static final String BEFORE_ADVICE_RETURNING_AFTER_ADVICE = "beforeAdviceReturning_after";

        @Before(BEFORE_ADVICE_RETURNING_POINTCUT)
        public void beforeAdviceReturning_before(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            int adviceReturning = -100;
            joinpoint.setAdviceReturning(adviceReturning);
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_RETURNING_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(adviceReturning) );
        }

        @After(BEFORE_ADVICE_RETURNING_POINTCUT)
        public void beforeAviceReturning_after(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }


        private static final String AFTER_ADVICE_RETURNING_POINTCUT = 
                "execution(public static * io.gemini.aop.aspect.MutableJoinpoint_13AdviceReturning_Tests$ClassMethod_Objects.afterAdviceReturning())";

        private static final String AFTER_ADVICE_RETURNING_BEFORE_ADVICE = "afterAdviceReturning_before";
        private static final String AFTER_ADVICE_RETURNING_AFTER_ADVICE = "afterAdviceReturning_after";

        @Before(AFTER_ADVICE_RETURNING_POINTCUT)
        public void afterAdviceReturning_before(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_RETURNING_BEFORE_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(AFTER_ADVICE_RETURNING_POINTCUT)
        public void afterAviceReturning_after(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            int adviceReturning = -100;
            joinpoint.setAdviceReturning(adviceReturning);
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(adviceReturning) );
        }
    }


    @Test
    public void testInstanceConstructor() {
        {
            InstanceConstructor_Objects expected = new InstanceConstructor_Objects(true);

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.BEFORE_ADVICE_RETURNING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getReturning()).isNotEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.BEFORE_ADVICE_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceConstructor_Objects.BEFORE_ADVICE_RETURNING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getReturning()).isEqualTo(expected);
        }

        {
            InstanceConstructor_Objects expected = new InstanceConstructor_Objects( (byte)1 );

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.AFTER_ADVICE_RETURNING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.AFTER_ADVICE_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isNotEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceConstructor_Objects.AFTER_ADVICE_RETURNING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getReturning()).isEqualTo(expected);
        }
    }

    public static class InstanceConstructor_Objects {

        private static final String BEFORE_ADVICE_RETURNING = "beforeAdviceReturning";
        private static final String AFTER_ADVICE_RETURNING = "afterAdviceReturning";

        public InstanceConstructor_Objects(boolean before) {
            ExecutionMemento.putTargetMethodInvoker(BEFORE_ADVICE_RETURNING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withReturning(this) );
        }

        public InstanceConstructor_Objects(byte after) {
            ExecutionMemento.putTargetMethodInvoker(AFTER_ADVICE_RETURNING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withReturning(this) );
        }

        public InstanceConstructor_Objects() {
        }
    }

    @Aspect
    public static class InstanceConstructor_Advices {

        private static final String BEFORE_ADVICE_RETURNING_POINTCUT = 
                "execution(public io.gemini.aop.aspect.MutableJoinpoint_13AdviceReturning_Tests$InstanceConstructor_Objects.new(boolean))";

        private static final String BEFORE_ADVICE_RETURNING_BEFORE_ADVICE = "beforeAdviceReturning_before";
        private static final String BEFORE_ADVICE_RETURNING_AFTER_ADVICE = "beforeAdviceReturning_after";

        @Before(BEFORE_ADVICE_RETURNING_POINTCUT)
        public void beforeAdviceReturning_before(MutableJoinpoint<InstanceConstructor_Objects, RuntimeException> joinpoint) {
            InstanceConstructor_Objects adviceReturning = new InstanceConstructor_Objects();
            joinpoint.setAdviceReturning(adviceReturning);
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_RETURNING_BEFORE_ADVICE, 
                    new AdviceMethod()
                    .withInvoked(true)
                    .withReturning(adviceReturning) );
        }

        @After(BEFORE_ADVICE_RETURNING_POINTCUT)
        public void beforeAviceReturning_after(MutableJoinpoint<InstanceConstructor_Objects, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                    .withInvoked(true)
                    .withReturning(joinpoint.getReturning()) );
        }


        private static final String AFTER_ADVICE_RETURNING_BEFORE_ADVICE = "afterAdviceReturning_before";
        private static final String AFTER_ADVICE_RETURNING_AFTER_ADVICE = "afterAdviceReturning_after";
        private static final String AFTER_ADVICE_RETURNING_POINTCUT = "execution(public io.gemini.aop.aspect.MutableJoinpoint_13AdviceReturning_Tests$InstanceConstructor_Objects.new(byte))";

        @Before(AFTER_ADVICE_RETURNING_POINTCUT)
        public void afterAdviceReturning_before(MutableJoinpoint<InstanceConstructor_Objects, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_RETURNING_BEFORE_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(AFTER_ADVICE_RETURNING_POINTCUT)
        public void afterAviceReturning_after(MutableJoinpoint<InstanceConstructor_Objects, RuntimeException> joinpoint) {
            InstanceConstructor_Objects adviceReturning = new InstanceConstructor_Objects();
            joinpoint.setAdviceReturning(adviceReturning);
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(adviceReturning) );
        }
    }


    @Test
    public void testInstanceMethod() {
        InstanceMethod_Objects thisObject = new InstanceMethod_Objects();

        {
            int expected = thisObject.beforeAdviceReturning();

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.BEFORE_ADVICE_RETURNING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getReturning()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.BEFORE_ADVICE_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceMethod_Objects.BEFORE_ADVICE_RETURNING);
            assertThat(targetMethodInvoker).isNull();
        }

        {
            int expected = thisObject.afterAdviceReturning();

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.AFTER_ADVICE_RETURNING_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.AFTER_ADVICE_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceMethod_Objects.AFTER_ADVICE_RETURNING);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getReturning()).isNotEqualTo(expected);
        }
    }

    public static class InstanceMethod_Objects {

        private static final String BEFORE_ADVICE_RETURNING = "beforeAdviceReturning";
        private static final String AFTER_ADVICE_RETURNING = "afterAdviceReturning";

        public int beforeAdviceReturning() {
            int returning = 100;
            ExecutionMemento.putTargetMethodInvoker(BEFORE_ADVICE_RETURNING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
            return returning;
        }

        public int afterAdviceReturning() {
            int returning = 100;
            ExecutionMemento.putTargetMethodInvoker(AFTER_ADVICE_RETURNING, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
            return returning;
        }
    }

    @Aspect
    public static class InstanceMethod_Aspects {

        private static final String BEFORE_ADVICE_RETURNING_POINTCUT = 
                "execution(public * io.gemini.aop.aspect.MutableJoinpoint_13AdviceReturning_Tests$InstanceMethod_Objects.beforeAdviceReturning())";

        private static final String BEFORE_ADVICE_RETURNING_BEFORE_ADVICE = "beforeAdviceReturning_before";
        private static final String BEFORE_ADVICE_RETURNING_AFTER_ADVICE = "beforeAdviceReturning_after";

        @Before(BEFORE_ADVICE_RETURNING_POINTCUT)
        public void beforeAdviceReturning_before(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            int adviceReturning = -100;
            joinpoint.setAdviceReturning(adviceReturning);
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_RETURNING_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(adviceReturning) );
        }

        @After(BEFORE_ADVICE_RETURNING_POINTCUT)
        public void beforeAviceReturning_after(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BEFORE_ADVICE_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }


        private static final String AFTER_ADVICE_RETURNING_POINTCUT = 
                "execution(public * io.gemini.aop.aspect.MutableJoinpoint_13AdviceReturning_Tests$InstanceMethod_Objects.afterAdviceReturning())";

        private static final String AFTER_ADVICE_RETURNING_BEFORE_ADVICE = "afterAdviceReturning_before";
        private static final String AFTER_ADVICE_RETURNING_AFTER_ADVICE = "afterAdviceReturning_after";

        @Before(AFTER_ADVICE_RETURNING_POINTCUT)
        public void afterAdviceReturning_before(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_RETURNING_BEFORE_ADVICE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(AFTER_ADVICE_RETURNING_POINTCUT)
        public void afterAviceReturning_after(MutableJoinpoint<Integer, RuntimeException> joinpoint) {
            int adviceReturning = -100;
            joinpoint.setAdviceReturning(adviceReturning);
            ExecutionMemento.putAdviceMethodInvoker(AFTER_ADVICE_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(adviceReturning) );
        }
    }
}
