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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class MutableJoinpoint_01Descriptor_Tests extends AbstractIntegrationTests {

    @Test
    public void testTypeInitializer() {
        {
            new TypeInitializer_BeforeAdvice_Object();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitializer_BeforeAdvice_Aspect.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();
            assertThat(adviceMethodInvoker.getThisLookup()).isNotNull();
            assertThat(adviceMethodInvoker.getThisClass()).isEqualTo(TypeInitializer_BeforeAdvice_Object.class);
            assertThat(adviceMethodInvoker.getStaticPart()).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitializer_BeforeAdvice_Object.class.getName());
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
        }

        {
            new TypeInitializer_AfterAdvice_Object();

            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitializer_AfterAdvice_Aspect.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();
            assertThat(adviceMethodInvoker.getThisLookup()).isNotNull();
            assertThat(adviceMethodInvoker.getThisClass()).isEqualTo(TypeInitializer_AfterAdvice_Object.class);
            assertThat(adviceMethodInvoker.getStaticPart()).isNull();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitializer_AfterAdvice_Object.class.getName());
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
        }
    }


    public static class TypeInitializer_BeforeAdvice_Object {

        static {
            ExecutionMemento.putTargetMethodInvoker(TypeInitializer_BeforeAdvice_Object.class.getName(), new TargetMethod().withInvoked(true));
        }

        static final Logger LOGGER;

        static {
            LOGGER = LoggerFactory.getLogger(TypeInitializer_BeforeAdvice_Object.class);
        }
    }

    @Aspect
    public static class TypeInitializer_BeforeAdvice_Aspect {

        private static final String ADVICE_TYPE_INITIALIZER = "typeInitializer";

        @SuppressWarnings("rawtypes")
        @Before("staticinitialization(io.gemini.aop.integration.MutableJoinpoint_01Descriptor_Tests$TypeInitializer_BeforeAdvice_Object)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass())
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    public static class TypeInitializer_AfterAdvice_Object {
        static {
            ExecutionMemento.putTargetMethodInvoker(TypeInitializer_AfterAdvice_Object.class.getName(), 
                    new TargetMethod().withInvoked(true) );
        }

        static final Logger LOGGER;

        static {
            LOGGER = LoggerFactory.getLogger(TypeInitializer_AfterAdvice_Object.class);
        }
    }

    @Aspect
    public static class TypeInitializer_AfterAdvice_Aspect {

        private static final String ADVICE_TYPE_INITIALIZER = "typeInitializer";

        @SuppressWarnings("rawtypes")
        @Before("staticinitialization(io.gemini.aop.integration.MutableJoinpoint_01Descriptor_Tests$TypeInitializer_AfterAdvice_Object)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass())
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testClassMethod() {
        ClassMethod_Object.descriptor();

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Aspect.DESCRIPTOR_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(beforeAdviceMethodInvoker.getThisClass()).isEqualTo(ClassMethod_Object.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(ClassMethod_Object.DESCRIPTOR_METHOD);

        AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Aspect.DESCRIPTOR_AFTER_ADVICE);
        assertThat(afterAdviceMethodInvoker).isNotNull();
        assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(afterAdviceMethodInvoker.getThisClass()).isEqualTo(ClassMethod_Object.class);
        assertThat(afterAdviceMethodInvoker.getStaticPart()).isEqualTo(ClassMethod_Object.DESCRIPTOR_METHOD);
    }

    public static class ClassMethod_Object {

        public static final Method DESCRIPTOR_METHOD;

        static {
            Method method = null;
            try {
                method = ClassMethod_Object.class.getMethod(ClassMethod_Object.DESCRIPTOR);
            } catch (Exception e) {
                e.printStackTrace();
            }
            DESCRIPTOR_METHOD = method;
        }

 
        private static final String DESCRIPTOR = "descriptor";

        public static void descriptor() {
        }
    }

    @Aspect
    public static class ClassMethod_Aspect {

        private static final String DESCRIPTOR_POINTCUT = 
                "execution(public static void io.gemini.aop.integration.MutableJoinpoint_01Descriptor_Tests$ClassMethod_Object.descriptor())";

        private static final String DESCRIPTOR_BEFORE_ADVICE = "descriptor_before";
        private static final String DESCRIPTOR_AFTER_ADVICE = "descriptor_after";

        @SuppressWarnings("rawtypes")
        @Before(DESCRIPTOR_POINTCUT)
        public void descriptor_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(DESCRIPTOR_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }

        @SuppressWarnings("rawtypes")
        @After(DESCRIPTOR_POINTCUT)
        public void descriptor_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(DESCRIPTOR_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testInstanceConstructor() {
        new InstanceConstructor_Object( (byte)1);

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Aspect.DESCRIPTOR_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(beforeAdviceMethodInvoker.getThisClass()).isEqualTo(InstanceConstructor_Object.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(InstanceConstructor_Object.DESCRIPTOR_CONSTRUCTOR);

        AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Aspect.DESCRIPTOR_AFTER_ADVICE);
        assertThat(afterAdviceMethodInvoker).isNotNull();
        assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(afterAdviceMethodInvoker.getThisClass()).isEqualTo(InstanceConstructor_Object.class);
        assertThat(afterAdviceMethodInvoker.getStaticPart()).isEqualTo(InstanceConstructor_Object.DESCRIPTOR_CONSTRUCTOR);
    }

    public static class InstanceConstructor_Object {

        public static final Constructor<?> DESCRIPTOR_CONSTRUCTOR;

        static {
            Constructor<?> method = null;
            try {
                method = InstanceConstructor_Object.class.getConstructor(byte.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            DESCRIPTOR_CONSTRUCTOR = method;
        }

        private static final String DESCRIPTOR = "InstanceConstructor_Object";

        public InstanceConstructor_Object(byte descriptor) {
            ExecutionMemento.putTargetMethodInvoker(DESCRIPTOR, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withThisObject(this) );
        }
    }

    @Aspect
    public static class InstanceConstructor_Aspect {

        private static final String DESCRIPTOR_POINTCUT = 
                "execution(public io.gemini.aop.integration.MutableJoinpoint_01Descriptor_Tests$InstanceConstructor_Object.new(byte))";

        private static final String DESCRIPTOR_BEFORE_ADVICE = "descriptor_before";
        private static final String DESCRIPTOR_AFTER_ADVICE = "descriptor_after";

        @SuppressWarnings("rawtypes")
        @Before(DESCRIPTOR_POINTCUT)
        public void descriptor_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(DESCRIPTOR_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }

        @SuppressWarnings("rawtypes")
        @After(DESCRIPTOR_POINTCUT)
        public void descriptor_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(DESCRIPTOR_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testInstanceMethod() {
        InstanceMethod_Object expected = new InstanceMethod_Object();
        expected.descriptor();

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspect.DESCRIPTOR_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(beforeAdviceMethodInvoker.getThisClass()).isEqualTo(InstanceMethod_Object.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(InstanceMethod_Object.DESCRIPTOR_METHOD);

        AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspect.DESCRIPTOR_AFTER_ADVICE);
        assertThat(afterAdviceMethodInvoker).isNotNull();
        assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
        assertThat(afterAdviceMethodInvoker.getThisClass()).isEqualTo(InstanceMethod_Object.class);
        assertThat(afterAdviceMethodInvoker.getStaticPart()).isEqualTo(InstanceMethod_Object.DESCRIPTOR_METHOD);
    }

    public static class InstanceMethod_Object {

        public static final Method DESCRIPTOR_METHOD;

        static {
            Method method = null;
            try {
                method = InstanceMethod_Object.class.getMethod(InstanceMethod_Object.DESCRIPTOR);
            } catch (Exception e) {
                e.printStackTrace();
            }
            DESCRIPTOR_METHOD = method;
        }

        private static final String DESCRIPTOR = "descriptor";

        public void descriptor() {
        }
    }

    @Aspect
    public static class InstanceMethod_Aspect {

        private static final String DESCRIPTOR_POINTCUT = 
                "execution(public void io.gemini.aop.integration.MutableJoinpoint_01Descriptor_Tests$InstanceMethod_Object.descriptor())";

        private static final String DESCRIPTOR_BEFORE_ADVICE = "descriptor_before";
        private static final String DESCRIPTOR_AFTER_ADVICE = "descriptor_after";

        @SuppressWarnings("rawtypes")
        @Before(DESCRIPTOR_POINTCUT)
        public void descriptor_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(DESCRIPTOR_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }

        @SuppressWarnings("rawtypes")
        @After(DESCRIPTOR_POINTCUT)
        public void descriptor_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(DESCRIPTOR_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }
}
