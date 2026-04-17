/*
 * Copyright © 2023 - present, the original author or authors. All Rights Reserved.
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

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.activation.MimeType;
import javax.servlet.ServletException;

import org.apache.commons.beanutils.converters.BooleanConverter;
import org.apache.commons.cli.Option;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.log4j.MDC;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.annotation.ConditionalOnClassLoader;
import io.gemini.api.aop.annotation.EnableCircularityBreaker;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 *
 *
 * @author   martin.liu
 */
public class Target_01TargetClass_Tests {

    private static final Logger LOGGER = LoggerFactory.getLogger(Target_01TargetClass_Tests.class);


    @Test
    public void testJdkClass1() {
        String object = new String();
        object.toString();

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(JdkClass1_Aspect.JDKCLASS_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getTargetLookup()).isNotNull();
        assertThat(beforeAdviceMethodInvoker.getTargetLookup().lookupModes() & Lookup.PRIVATE).isNotEqualTo(0);

        assertThat(beforeAdviceMethodInvoker.getTargetClass()).isEqualTo(String.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(JdkClass1_Aspect.JDKCLASS_METHOD);
    }

    @Aspect
    public static class JdkClass1_Aspect {

        private static final String JDKCLASS_POINTCUT = 
                "execution(public java.lang.String java.lang.String.toString())";

        private static final String JDKCLASS_BEFORE_ADVICE = JdkClass1_Aspect.class.getName() + ".jdkClass_before";

        private static final Method JDKCLASS_METHOD;

        static {
            Method method = null;
            try {
                method = String.class.getDeclaredMethod("toString");
            } catch (Exception e) {
                e.printStackTrace();
            }
            JDKCLASS_METHOD = method;
        }


        @SuppressWarnings("rawtypes")
        @ConditionalOnClassLoader(isBootstrapClassLoader = true)
        @EnableCircularityBreaker
        @Before(JDKCLASS_POINTCUT)
        public void jdkClass_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(JDKCLASS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetLookup(joinpoint.getTargetLookup())
                        .withTargetClass(joinpoint.getTargetClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }

    @ConditionalOnClassLoader(isBootstrapClassLoader = true)
//    @ExprPointcut(pointcutExpression = JdkClass1_Aspect.JDKCLASS_POINTCUT)
    @EnableCircularityBreaker
    public static class TestAdvice1 {

        @Advice.OnMethodEnter(inline = false, prependLineNumber = true)
        public static void beforeInstanceMethod(
                @Advice.This Object targetObject,
                @Advice.AllArguments(readOnly = true, typing = Assigner.Typing.DYNAMIC) Object[] arguments
        ) throws Throwable {
//            System.out.println("before InstanceMethod: ");
        }


        @Advice.OnMethodExit(inline = false)
        public static void afterInstanceMethod(
                ) throws Throwable {
//            System.out.println("after InstanceMethod");
        }
    }

    @Test
    public void testJdkClass2() {
        Executor executor = Executors.newCachedThreadPool();
        executor.execute(() -> LOGGER.info("Test JDK ThreadPoolExecutor."));

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(JdkClass2_Aspect.JDKCLASS_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getTargetLookup()).isNotNull();
        assertThat(beforeAdviceMethodInvoker.getTargetLookup().lookupModes() & Lookup.PRIVATE).isNotEqualTo(0);

        assertThat(beforeAdviceMethodInvoker.getTargetClass()).isEqualTo(ThreadPoolExecutor.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(JdkClass2_Aspect.JDKCLASS_METHOD);
    }

    @Aspect
    public static class JdkClass2_Aspect {

        private static final String JDKCLASS_POINTCUT = 
                "execution(public void java.util.concurrent.ThreadPoolExecutor.execute(java.lang.Runnable))";

        private static final String JDKCLASS_BEFORE_ADVICE = JdkClass2_Aspect.class.getName() + ".jdkClass_before";

        private static final Method JDKCLASS_METHOD;

        static {
            Method method = null;
            try {
                method = ThreadPoolExecutor.class.getDeclaredMethod("execute", Runnable.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            JDKCLASS_METHOD = method;
        }


        @SuppressWarnings("rawtypes")
        @ConditionalOnClassLoader(isBootstrapClassLoader = true)
        @Before(JDKCLASS_POINTCUT)
        public void jdkClass_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(JDKCLASS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetLookup(joinpoint.getTargetLookup())
                        .withTargetClass(joinpoint.getTargetClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }

//    @Advisor(inheritClassLoaderMatcher = false, inheritTypeMatcher = false, perInstance = false)
//    @ExprPointcut(pointcutExpression = JdkClass2_Aspect.JDKCLASS_POINTCUT)
    public static class TestAdvice {

        private static final String JDKCLASS_BEFORE_ADVICE = TestAdvice.class.getName() + ".jdkClass_before";

        @Advice.OnMethodEnter(inline = false, prependLineNumber = true)
        public static void beforeInstanceMethod(
                @Advice.This Object targetObject
//                @Advice.AllArguments(readOnly = true, typing = Assigner.Typing.DYNAMIC) Object[] arguments
        ) throws Throwable {
            System.out.println("before InstanceMethod: " + targetObject);
            ExecutionMemento.putAdviceMethodInvoker(JDKCLASS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        );
        }


        @Advice.OnMethodExit(inline = false)
        public static void afterInstanceMethod(
                ) throws Throwable {
            System.out.println("after InstanceMethod");
            ExecutionMemento.putAdviceMethodInvoker(JDKCLASS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        );
        }
    }

//    @Advisor(inheritClassLoaderMatcher = false, inheritTypeMatcher = false, perInstance = false)
//    @ExprPointcut(pointcutExpression = JdkClass2_Aspect.JDKCLASS_POINTCUT)
//    public static class TestAdvice2 {
//
//        @Advice.OnMethodEnter(inline = false, prependLineNumber = true)
//        public static void beforeInstanceMethod(
//                @Advice.This Object targetObject
////                @Advice.AllArguments(readOnly = true, typing = Assigner.Typing.DYNAMIC) Object[] arguments
//        ) throws Throwable {
//            System.out.println("before InstanceMethod2: " + targetObject);
//        }
//
//
//        @Advice.OnMethodExit(inline = false)
//        public static void afterInstanceMethod(
//                ) throws Throwable {
//            System.out.println("after InstanceMethod2");
//        }
//    }


    @Test
    public void testByteCode1x() {
        ToStringBuilder object = new ToStringBuilder(new Object());
        object.append("name", "test");

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ByteCode1x_Aspect.BYTECODE_1X_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getTargetLookup()).isNotNull();
        assertThat(beforeAdviceMethodInvoker.getTargetLookup().lookupModes() & Lookup.PRIVATE).isNotEqualTo(0);

        assertThat(beforeAdviceMethodInvoker.getTargetClass()).isEqualTo(ToStringBuilder.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(ByteCode1x_Aspect.BYTECODE_1X_METHOD);
    }

    @Aspect
    public static class ByteCode1x_Aspect {

        private static final String BYTECODE_1X_POINTCUT = 
                "execution(!private org.apache.commons.lang.builder.ToStringBuilder org.apache.commons.lang.builder.ToStringBuilder.append(..))";

        private static final String BYTECODE_1X_BEFORE_ADVICE = ByteCode1x_Aspect.class.getName() + ".byteCode1x_before";

        private static final Method BYTECODE_1X_METHOD;

        static {
            Method method = null;
            try {
                method = ToStringBuilder.class.getMethod("append", String.class, Object.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            BYTECODE_1X_METHOD = method;
        }


        @SuppressWarnings("rawtypes")
        @Before(BYTECODE_1X_POINTCUT)
        public void byteCode1x_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BYTECODE_1X_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetLookup(joinpoint.getTargetLookup())
                        .withTargetClass(joinpoint.getTargetClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testByteCode2x() {
        BooleanConverter converter = new BooleanConverter();
        converter.convert(Boolean.class, "true");

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ByteCode2x_Aspect.BYTECODE_2X_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getTargetLookup()).isNotNull();
        assertThat(beforeAdviceMethodInvoker.getTargetLookup().lookupModes() & Lookup.PRIVATE).isNotEqualTo(0);

        assertThat(beforeAdviceMethodInvoker.getTargetClass()).isEqualTo(BooleanConverter.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(ByteCode2x_Aspect.BYTECODE_2X_METHOD);
    }

    @Aspect
    public static class ByteCode2x_Aspect {

        private static final String BYTECODE_2X_POINTCUT = 
                "execution(public java.lang.Object org.apache.commons.beanutils.converters.BooleanConverter.convert(..))";

        private static final String BYTECODE_2X_BEFORE_ADVICE = ByteCode2x_Aspect.class.getName() + ".byteCode2x_before";

        private static final Method BYTECODE_2X_METHOD;

        static {
            Method method = null;
            try {
                method = BooleanConverter.class.getMethod("convert", Class.class, Object.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            BYTECODE_2X_METHOD = method;
        }


        @SuppressWarnings("rawtypes")
        @Before(BYTECODE_2X_POINTCUT)
        public void byteCode2x_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BYTECODE_2X_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetLookup(joinpoint.getTargetLookup())
                        .withTargetClass(joinpoint.getTargetClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testByteCode3x() {
        Option option = new Option("", "");
        option.getArgs();

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ByteCode3x_Aspect.BYTECODE_3X_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getTargetLookup()).isNotNull();
        assertThat(beforeAdviceMethodInvoker.getTargetLookup().lookupModes() & Lookup.PRIVATE).isNotEqualTo(0);

        assertThat(beforeAdviceMethodInvoker.getTargetClass()).isEqualTo(Option.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(ByteCode3x_Aspect.BYTECODE_3X_METHOD);
    }

    @Aspect
    public static class ByteCode3x_Aspect {

        private static final String BYTECODE_3X_POINTCUT = 
                "execution(public int org.apache.commons.cli.Option.getArgs())";

        private static final String BYTECODE_3X_BEFORE_ADVICE = ByteCode2x_Aspect.class.getName() + ".byteCode3x_before";

        private static final Method BYTECODE_3X_METHOD;

        static {
            Method method = null;
            try {
                method = Option.class.getMethod("getArgs");
            } catch (Exception e) {
                e.printStackTrace();
            }
            BYTECODE_3X_METHOD = method;
        }


        @SuppressWarnings("rawtypes")
        @Before(BYTECODE_3X_POINTCUT)
        public void byteCode3x_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BYTECODE_3X_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetLookup(joinpoint.getTargetLookup())
                        .withTargetClass(joinpoint.getTargetClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testByteCode4x() {
        MimeType mimeType = new MimeType();
        mimeType.getBaseType();

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ByteCode4x_Aspect.BYTECODE_4X_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getTargetLookup()).isNotNull();
        assertThat(beforeAdviceMethodInvoker.getTargetLookup().lookupModes() & Lookup.PRIVATE).isNotEqualTo(0);

        assertThat(beforeAdviceMethodInvoker.getTargetClass()).isEqualTo(MimeType.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(ByteCode4x_Aspect.BYTECODE_4X_METHOD);
    }

    @Aspect
    public static class ByteCode4x_Aspect {

        private static final String BYTECODE_4X_POINTCUT = 
                "execution(public java.lang.String javax.activation.MimeType.getBaseType())";

        private static final String BYTECODE_4X_BEFORE_ADVICE = ByteCode2x_Aspect.class.getName() + ".byteCode4x_before";

        private static final Method BYTECODE_4X_METHOD;

        static {
            Method method = null;
            try {
                method = MimeType.class.getMethod("getBaseType");
            } catch (Exception e) {
                e.printStackTrace();
            }
            BYTECODE_4X_METHOD = method;
        }


        /**
         * {@code MimeType} is loaded by BootstrapClassLoader under JDK8 or below, 
         * but loaded by AppClassLoader under JDK9 or above.
         * 
         * @param joinpoint
         */
        @SuppressWarnings("rawtypes")
        @ConditionalOnClassLoader(classLoaderExpression = "BootstrapClassLoader || AppClassLoader")
        @Before(BYTECODE_4X_POINTCUT)
        public void byteCode4x_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BYTECODE_4X_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetLookup(joinpoint.getTargetLookup())
                        .withTargetClass(joinpoint.getTargetClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testByteCode5x() {
        ServletException exception = new ServletException();
        exception.getRootCause();

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ByteCode5x_Aspect.BYTECODE_5X_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getTargetLookup()).isNotNull();
        assertThat(beforeAdviceMethodInvoker.getTargetLookup().lookupModes() & Lookup.PRIVATE).isNotEqualTo(0);

        assertThat(beforeAdviceMethodInvoker.getTargetClass()).isEqualTo(ServletException.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(ByteCode5x_Aspect.BYTECODE_5X_METHOD);
    }

    @Aspect
    public static class ByteCode5x_Aspect {

        private static final String BYTECODE_5X_POINTCUT = 
                "execution(public java.lang.Throwable javax.servlet.ServletException.getRootCause(..))";

        private static final String BYTECODE_5X_BEFORE_ADVICE = ByteCode2x_Aspect.class.getName() + ".byteCode6x_before";

        private static final Method BYTECODE_5X_METHOD;

        static {
            Method method = null;
            try {
                method = ServletException.class.getMethod("getRootCause");
            } catch (Exception e) {
                e.printStackTrace();
            }
            BYTECODE_5X_METHOD = method;
        }


        @SuppressWarnings("rawtypes")
        @Before(BYTECODE_5X_POINTCUT)
        public void byteCode5x_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BYTECODE_5X_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetLookup(joinpoint.getTargetLookup())
                        .withTargetClass(joinpoint.getTargetClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testByteCode6x() {
        MDC.get("");

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ByteCode6x_Aspect.BYTECODE_6X_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getTargetLookup()).isNotNull();
        assertThat(beforeAdviceMethodInvoker.getTargetLookup().lookupModes() & Lookup.PRIVATE).isNotEqualTo(0);

        assertThat(beforeAdviceMethodInvoker.getTargetClass()).isEqualTo(MDC.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(ByteCode6x_Aspect.BYTECODE_6X_METHOD);
    }

    @Aspect
    public static class ByteCode6x_Aspect {

        private static final String BYTECODE_6X_POINTCUT = 
                "execution(public static java.lang.Object org.apache.log4j.MDC.get(java.lang.String))";

        private static final String BYTECODE_6X_BEFORE_ADVICE = ByteCode2x_Aspect.class.getName() + ".byteCode6x_before";

        private static final Method BYTECODE_6X_METHOD;

        static {
            Method method = null;
            try {
                method = MDC.class.getMethod("get", String.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            BYTECODE_6X_METHOD = method;
        }


        @SuppressWarnings("rawtypes")
        @Before(BYTECODE_6X_POINTCUT)
        public void byteCode6x_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(BYTECODE_6X_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetLookup(joinpoint.getTargetLookup())
                        .withTargetClass(joinpoint.getTargetClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }
}
