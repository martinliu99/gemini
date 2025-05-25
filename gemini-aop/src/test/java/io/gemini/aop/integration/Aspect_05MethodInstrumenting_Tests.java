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
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class Aspect_05MethodInstrumenting_Tests extends AbstractIntegrationTests {

    @Test
    public void testTypeInitializer() {
        new TypeInitializer_Objects();

        {
            AdviceMethod adviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitializer_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(adviceMethodInvoker).isNotNull();
            assertThat(adviceMethodInvoker.isInvoked()).isTrue();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitializer_Objects.class.getName());
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
        }
    }

    public static class TypeInitializer_Objects {
        static {
            ExecutionMemento.putTargetMethodInvoker(TypeInitializer_Objects.class.getName(), 
                    new TargetMethod().withInvoked(true) );
        }

        static final Logger LOGGER;

        static {
            LOGGER = LoggerFactory.getLogger(TypeInitializer_Objects.class);
        }
    }

    @Aspect
    public static class TypeInitializer_Aspects {

        private static final String ADVICE_TYPE_INITIALIZER = "typeInitializer";

        @SuppressWarnings("rawtypes")
        @Before("staticinitialization(io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$TypeInitializer_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }
    }


    @Test
    public void testClassMethod() {
        ClassMethod_Objects.classMethod();

        AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.CLASS_METHOD_AFTER_ADVICE);
        assertThat(afterAdviceMethodInvoker).isNotNull();
        assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
    }

    public static class ClassMethod_Objects {

        public static void classMethod() {
        }
    }

    @Aspect
    public static class ClassMethod_Advices {

        private static final String CLASS_METHOD_POINTCUT = 
                "execution(public static void io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$ClassMethod_Objects.classMethod())";

        private static final String CLASS_METHOD_AFTER_ADVICE = "classMethod_after";

        @SuppressWarnings("rawtypes")
        @After(CLASS_METHOD_POINTCUT)
        public void classMethod_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(CLASS_METHOD_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }
    }


    @Test
    public void testInstanceConstructor() {
        new InstanceConstructor_Objects( (byte)1);

        AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.INSTANCE_CONSTRUCTOR_AFTER_ADVICE);
        assertThat(afterAdviceMethodInvoker).isNotNull();
        assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
    }

    public static class InstanceConstructor_Objects {

        public InstanceConstructor_Objects(byte input) {
        }
    }

    @Aspect
    public static class InstanceConstructor_Advices {

        private static final String INSTANCE_CONSTRUCTOR_POINTCUT = 
                "execution(public io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$InstanceConstructor_Objects.new(byte))";

        private static final String INSTANCE_CONSTRUCTOR_AFTER_ADVICE = "instanceConstructor_after";

        @SuppressWarnings("rawtypes")
        @After(INSTANCE_CONSTRUCTOR_POINTCUT)
        public void instanceConstructor_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(INSTANCE_CONSTRUCTOR_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testInstanceMethod() {
        InstanceMethod_Objects expected = new InstanceMethod_Objects();
        expected.instanceMethod();

        AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.INSTANCE_METHOD_AFTER_ADVICE);
        assertThat(afterAdviceMethodInvoker).isNotNull();
        assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
    }

    public static class InstanceMethod_Objects {

        public void instanceMethod() {
        }
    }

    @Aspect
    public static class InstanceMethod_Aspects {

        private static final String INSTANCE_METHOD_POINTCUT = 
                "execution(public void io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$InstanceMethod_Objects.instanceMethod())";

        private static final String INSTANCE_METHOD_AFTER_ADVICE = "instanceMethod_after";

        @SuppressWarnings("rawtypes")
        @After(INSTANCE_METHOD_POINTCUT)
        public void instanceMethod_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(INSTANCE_METHOD_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }
    }


    @Test
    public void testInterfaceMethod() {
        Interface_Objects expected = new InterfaceImplementor_Objects();

        {
            expected.interfaceMethod();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InterfaceImplementor_Aspects.INTERFACE_METHOD_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();

            AdviceMethod afterAdviceMethodInvoker2 = ExecutionMemento.getAdviceMethodInvoker(InterfaceImplementor_Aspects.IMPLEMENTOR_INTERFACE_METHOD_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker2).isNotNull();
            assertThat(afterAdviceMethodInvoker2.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker2.getStaticPart()).isEqualTo(InterfaceImplementor_Objects.IMPLEMENTOR_INTERFACE_METHOD);
        }

        {
            expected.defaultMethod();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InterfaceImplementor_Aspects.DEFAULT_METHOD_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getStaticPart()).isEqualTo(InterfaceImplementor_Objects.DEFAULT_METHOD);

            AdviceMethod afterAdviceMethodInvoker2 = ExecutionMemento.getAdviceMethodInvoker(InterfaceImplementor_Aspects.IMPLEMENTOR_DEFAULT_METHOD_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker2).isNotNull();
            assertThat(afterAdviceMethodInvoker2.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker2.getStaticPart()).isEqualTo(InterfaceImplementor_Objects.IMPLEMENTOR_DEFAULT_METHOD);
        }
    }

    static interface Interface_Objects {

        void interfaceMethod();

        default void defaultMethod() {
        }
    }
 
    public static class InterfaceImplementor_Objects implements Interface_Objects {

        private static final String IMPLEMENTOR_INTERFACE_METHOD_NAME = "interfaceMethod";
        public static final Method IMPLEMENTOR_INTERFACE_METHOD;

        private static final String DEFAULT_METHOD_NAME = "defaultMethod";
        public static final Method DEFAULT_METHOD;

        private static final String IMPLEMENTOR_DEFAULT_METHOD_NAME = "defaultMethod";
        public static final Method IMPLEMENTOR_DEFAULT_METHOD;

        static {
            Method implementorInterfaceMethod = null;
            Method defaultMethod = null;
            Method implementorDefaultMethod = null;
            try {
                implementorInterfaceMethod = InterfaceImplementor_Objects.class.getDeclaredMethod(IMPLEMENTOR_INTERFACE_METHOD_NAME);

                defaultMethod = Interface_Objects.class.getDeclaredMethod(DEFAULT_METHOD_NAME);
                implementorDefaultMethod = InterfaceImplementor_Objects.class.getDeclaredMethod(IMPLEMENTOR_DEFAULT_METHOD_NAME);
            } catch (Exception e) {
                e.printStackTrace();
            }

            IMPLEMENTOR_INTERFACE_METHOD = implementorInterfaceMethod;
            DEFAULT_METHOD = defaultMethod;
            IMPLEMENTOR_DEFAULT_METHOD = implementorDefaultMethod;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public void interfaceMethod() {
        }

        /**
         * 
         * {@inheritDoc}
         */
        @Override
        public void defaultMethod() {
            Interface_Objects.super.defaultMethod();
        }
    }

    @Aspect
    public static class InterfaceImplementor_Aspects {

        private static final String INTERFACE_METHOD_POINTCUT = 
                "execution(public void io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$Interface_Objects.interfaceMethod())";

        private static final String INTERFACE_METHOD_AFTER_ADVICE = "interfaceMethod_after";

        @SuppressWarnings("rawtypes")
        @After(INTERFACE_METHOD_POINTCUT)
        public void interfaceMethod_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(INTERFACE_METHOD_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withStaticPart(joinpoint.getStaticPart()) );
        }


        private static final String IMPLEMENTOR_INTERFACE_METHOD_POINTCUT = 
                "execution(public void io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$InterfaceImplementor_Objects.interfaceMethod())";

        private static final String IMPLEMENTOR_INTERFACE_METHOD_AFTER_ADVICE = "implementorInterfaceMethod_after";

        @SuppressWarnings("rawtypes")
        @After(IMPLEMENTOR_INTERFACE_METHOD_POINTCUT)
        public void implementorInterfaceMethod_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(IMPLEMENTOR_INTERFACE_METHOD_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withStaticPart(joinpoint.getStaticPart()) );
        }


        private static final String DEFAULT_METHOD_POINTCUT = 
                "execution(public void io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$Interface_Objects.defaultMethod())";

        private static final String DEFAULT_METHOD_AFTER_ADVICE = "defaultMethod_after";

        @SuppressWarnings("rawtypes")
        @After(DEFAULT_METHOD_POINTCUT)
        public void defaultMethod_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(DEFAULT_METHOD_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withStaticPart(joinpoint.getStaticPart()) );
        }


        private static final String IMPLEMENTOR_DEFAULT_METHOD_POINTCUT = 
                "execution(public void io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$InterfaceImplementor_Objects.defaultMethod())";

        private static final String IMPLEMENTOR_DEFAULT_METHOD_AFTER_ADVICE = "implementorDefaultMethod_after";

        @SuppressWarnings("rawtypes")
        @After(IMPLEMENTOR_DEFAULT_METHOD_POINTCUT)
        public void implementorDefaultMethod_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(IMPLEMENTOR_DEFAULT_METHOD_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testBridgeMethod() {
        BridgeMethod_Objects expected = new BridgeMethod_Objects();

        {
            expected.genericBridge();
            expected.genericBridge();
            expected.genericBridge();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(BridgeMethod_Aspects.GENERIC_BRIDGE_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getStaticPart()).isEqualTo(BridgeMethod_Objects.GENERIC_BRIDGE);

            AdviceMethod afterAdviceMethodInvoker2 = ExecutionMemento.getAdviceMethodInvoker(BridgeMethod_Aspects.OVERRIDDEN_GENERIC_BRIDGE_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker2).isNotNull();
            assertThat(afterAdviceMethodInvoker2.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker2.getStaticPart()).isEqualTo(BridgeMethod_Objects.GENERIC_BRIDGE);
        }

        {
            expected.covariantBridge();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(BridgeMethod_Aspects.COVARIANT_BRIDGE_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getStaticPart()).isEqualTo(BridgeMethod_Objects.COVARIANT_BRIDGE);

            AdviceMethod afterAdviceMethodInvoker2 = ExecutionMemento.getAdviceMethodInvoker(BridgeMethod_Aspects.OVERRIDDEN_COVARIANT_BRIDGE_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker2).isNotNull();
            assertThat(afterAdviceMethodInvoker2.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker2.getStaticPart()).isEqualTo(BridgeMethod_Objects.COVARIANT_BRIDGE);
        }
    }

    static interface GenericInterface_Objects<T> {

        public T genericBridge();

        public Number covariantBridge();
    }
 
    public static class BridgeMethod_Objects implements GenericInterface_Objects<String> {

        private static final String GENERIC_BRIDGE_NAME = "genericBridge";
        private static final Method GENERIC_BRIDGE;

        private static final String COVARIANT_BRIDGE_NAME = "covariantBridge";
        private static final Method COVARIANT_BRIDGE;

        static {
            Method genericBridge = null;
            Method covariantBridge = null;

            try {
                genericBridge = BridgeMethod_Objects.class.getDeclaredMethod(GENERIC_BRIDGE_NAME);
                covariantBridge = BridgeMethod_Objects.class.getDeclaredMethod(COVARIANT_BRIDGE_NAME);
            } catch (Exception e) {
                e.printStackTrace();
            }

            GENERIC_BRIDGE = genericBridge;
            COVARIANT_BRIDGE = covariantBridge;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public String genericBridge() {
            return null;
        }

        /** {@inheritDoc} 
         */
        @Override
        public Long covariantBridge() {
            return null;
        }
    }

    @Aspect
    public static class BridgeMethod_Aspects {

        private static final String GENERIC_BRIDGE_POINTCUT = 
                "execution(public java.lang.Object io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$BridgeMethod_Objects.genericBridge())";

        private static final String GENERIC_BRIDGE_AFTER_ADVICE = "genericBridge_after";

        @SuppressWarnings("rawtypes")
        @After(GENERIC_BRIDGE_POINTCUT)
        public void genericBridge_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(GENERIC_BRIDGE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withStaticPart(joinpoint.getStaticPart()) );
        }


        private static final String OVERRIDDEN_GENERIC_BRIDGE_POINTCUT = 
                "execution(public java.lang.String io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$BridgeMethod_Objects.genericBridge())";

        private static final String OVERRIDDEN_GENERIC_BRIDGE_AFTER_ADVICE = "overriddenGenericBridge_after";

        @SuppressWarnings("rawtypes")
        @After(OVERRIDDEN_GENERIC_BRIDGE_POINTCUT)
        public void overriddenGenericBridge_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(OVERRIDDEN_GENERIC_BRIDGE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withStaticPart(joinpoint.getStaticPart()) );
        }


        private static final String COVARIANT_BRIDGE_POINTCUT = 
                "execution(public java.lang.Number io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$BridgeMethod_Objects.covariantBridge())";

        private static final String COVARIANT_BRIDGE_AFTER_ADVICE = "covariantBridge_after";

        @SuppressWarnings("rawtypes")
        @After(COVARIANT_BRIDGE_POINTCUT)
        public void covariantBridge_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(COVARIANT_BRIDGE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withStaticPart(joinpoint.getStaticPart()) );
        }


        private static final String OVERRIDDEN_COVARIANT_BRIDGE_POINTCUT = 
                "execution(public java.lang.Long io.gemini.aop.integration.Aspect_05MethodInstrumenting_Tests$BridgeMethod_Objects.covariantBridge())";

        private static final String OVERRIDDEN_COVARIANT_BRIDGE_AFTER_ADVICE = "overriddenCovariantBridge_after";

        @SuppressWarnings("rawtypes")
        @After(OVERRIDDEN_COVARIANT_BRIDGE_POINTCUT)
        public void overriddenCovariantBridge_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(OVERRIDDEN_COVARIANT_BRIDGE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }
}
