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
public class MutableJoinpoint_02TargetObject_Tests extends AbstractIntegrationTests {

    @Test
    public void testTypeInitilizer() {
        {
            new TypeInitilizer_BeforeAdvice_Objects();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_BeforeAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();
            assertThat(adviceMethodInvoker.getThisObject()).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_BeforeAdvice_Objects.class.getName());
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
        }

        {
            new TypeInitilizer_AfterAdvice_Objects();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_AfterAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();
            assertThat(adviceMethodInvoker.getThisObject()).isNull();

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
        @Before("staticinitialization(io.gemini.aop.integration.MutableJoinpoint_02TargetObject_Tests$TypeInitilizer_BeforeAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(joinpoint.getThisObject()) );
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
        @Before("staticinitialization(io.gemini.aop.integration.MutableJoinpoint_02TargetObject_Tests$TypeInitilizer_AfterAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(joinpoint.getThisObject()) );
        }
    }


    @Test
    public void testClassMethod() {
        ClassMethod_Objects.targetObject();
        Object expected = null;

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.TARGET_OBJECT_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(beforeAdviceMethodInvoker.getThisObject()).isEqualTo(expected);

        AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.TARGET_OBJECT_AFTER_ADVICE);
        assertThat(afterAdviceMethodInvoker).isNotNull();
        assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(afterAdviceMethodInvoker.getThisObject()).isEqualTo(expected);

        TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(ClassMethod_Objects.TARGET_OBJECT);
        assertThat(targetMethodInvoker).isNotNull();
        assertThat(targetMethodInvoker.isInvoked()).isTrue();
        assertThat(targetMethodInvoker.getThisObject()).isEqualTo(expected);
    }

    public static class ClassMethod_Objects {

        private static final String TARGET_OBJECT = "targetObject";

        public static void targetObject() {
            ExecutionMemento.putTargetMethodInvoker(TARGET_OBJECT, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThisObject(null) );
        }
    }

    @Aspect
    public static class ClassMethod_Advices {

        private static final String TARGET_OBJECT_POINTCUT = 
                "execution(public static void io.gemini.aop.integration.MutableJoinpoint_02TargetObject_Tests$ClassMethod_Objects.targetObject())";

        private static final String TARGET_OBJECT_BEFORE_ADVICE = "targetObject_before";
        private static final String TARGET_OBJECT_AFTER_ADVICE = "targetObject_after";

        @SuppressWarnings("rawtypes")
        @Before(TARGET_OBJECT_POINTCUT)
        public void targetObject_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_OBJECT_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(joinpoint.getThisObject()) );
        }

        @SuppressWarnings("rawtypes")
        @After(TARGET_OBJECT_POINTCUT)
        public void targetObject_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_OBJECT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(joinpoint.getThisObject()) );
        }
    }


    @Test
    public void testInstanceConstructor() {
        InstanceConstructor_Objects expected = new InstanceConstructor_Objects(true);

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.TARGET_OBJECT_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(beforeAdviceMethodInvoker.getThisObject()).isEqualTo(null);

        AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.TARGET_OBJECT_AFTER_ADVICE);
        assertThat(afterAdviceMethodInvoker).isNotNull();
        assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(afterAdviceMethodInvoker.getThisObject()).isEqualTo(expected);

        TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceConstructor_Objects.TARGET_OBJECT);
        assertThat(targetMethodInvoker).isNotNull();
        assertThat(targetMethodInvoker.isInvoked()).isTrue();
        assertThat(targetMethodInvoker.getThisObject()).isEqualTo(expected);
    }

    public static class InstanceConstructor_Objects {

        private static final String TARGET_OBJECT = "Objects";

        public InstanceConstructor_Objects(boolean targetObject) {
            ExecutionMemento.putTargetMethodInvoker(TARGET_OBJECT, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThisObject(this) );
        }
    }

    @Aspect
    public static class InstanceConstructor_Advices {

        private static final String TARGET_OBJECT_POINTCUT = 
                "execution(public io.gemini.aop.integration.MutableJoinpoint_02TargetObject_Tests$InstanceConstructor_Objects.new(boolean))";

        private static final String TARGET_OBJECT_BEFORE_ADVICE = "targetObject_before";
        private static final String TARGET_OBJECT_AFTER_ADVICE = "targetObject_after";

        @SuppressWarnings("rawtypes")
        @Before(TARGET_OBJECT_POINTCUT)
        public void targetObject_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_OBJECT_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(joinpoint.getThisObject()) );
        }

        @SuppressWarnings("rawtypes")
        @After(TARGET_OBJECT_POINTCUT)
        public void targetObject_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_OBJECT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(joinpoint.getThisObject()) );
        }
    }


    @Test
    public void testInstanceMethod() {
        InstanceMethod_Objects expected = new InstanceMethod_Objects();
        expected.targetObject();

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.TARGET_OBJECT_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(beforeAdviceMethodInvoker.getThisObject()).isEqualTo(expected);

        AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.TARGET_OBJECT_AFTER_ADVICE);
        assertThat(afterAdviceMethodInvoker).isNotNull();
        assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(afterAdviceMethodInvoker.getThisObject()).isEqualTo(expected);

        TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceMethod_Objects.TARGET_OBJECT);
        assertThat(targetMethodInvoker).isNotNull();
        assertThat(targetMethodInvoker.isInvoked()).isTrue();
        assertThat(targetMethodInvoker.getThisObject()).isEqualTo(expected);
    }

    public static class InstanceMethod_Objects {

        private static final String TARGET_OBJECT = "targetObject";

        public void targetObject() {
            ExecutionMemento.putTargetMethodInvoker(TARGET_OBJECT, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThisObject(this) );
        }
    }

    @Aspect
    public static class InstanceMethod_Aspects {

        private static final String TARGET_OBJECT_POINTCUT = 
                "execution(public void io.gemini.aop.integration.MutableJoinpoint_02TargetObject_Tests$InstanceMethod_Objects.targetObject())";

        private static final String TARGET_OBJECT_BEFORE_ADVICE = "targetObject_before";
        private static final String TARGET_OBJECT_AFTER_ADVICE = "targetObject_after";

        @SuppressWarnings("rawtypes")
        @Before(TARGET_OBJECT_POINTCUT)
        public void targetObject_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_OBJECT_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(joinpoint.getThisObject()) );
        }

        @SuppressWarnings("rawtypes")
        @After(TARGET_OBJECT_POINTCUT)
        public void targetObject_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_OBJECT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(joinpoint.getThisObject()) );
        }
    }
}
