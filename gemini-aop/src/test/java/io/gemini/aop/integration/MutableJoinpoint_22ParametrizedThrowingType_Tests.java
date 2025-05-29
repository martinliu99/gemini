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

import java.io.IOException;

import org.aspectj.lang.annotation.After;
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
public class MutableJoinpoint_22ParametrizedThrowingType_Tests extends AbstractIntegrationTests {

    @Test
    public void testNoException() {
        new NoException_Object().throwNothing();

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(NoException_Aspect.THROW_NOTHING_RAW_TYPE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(NoException_Aspect.THROW_NOTHING_SUPER_TYPE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThrowing()).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(NoException_Aspect.THROW_NOTHING_WRONG_TYPE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    static class NoException_Object {

        String throwNothing() {
            return "";
        }
    }

    @Aspect
    public static class NoException_Aspect {

        private static final String THROW_NOTHING_POINTCUT = 
                "execution(java.lang.String io.gemini.aop.integration.MutableJoinpoint_22ParametrizedThrowingType_Tests$NoException_Object.throwNothing())";

        private static final String THROW_NOTHING_RAW_TYPE = "throwNothing_rawType";
        private static final String THROW_NOTHING_SUPER_TYPE = "throwNothing_superType";
        private static final String THROW_NOTHING_WRONG_TYPE = "throwNothing_wrongType";

        @SuppressWarnings("rawtypes")
        @After(THROW_NOTHING_POINTCUT)
        public void throwNothing_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(THROW_NOTHING_RAW_TYPE, new AdviceMethod().withInvoked(true));
        }

        @After(THROW_NOTHING_POINTCUT)
        public void throwNothing_superType(MutableJoinpoint<String, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(THROW_NOTHING_SUPER_TYPE, 
                    new AdviceMethod().withInvoked(true).withThrowing(joinpoint.getThrowing()) );
        }

        @After(THROW_NOTHING_POINTCUT)
        public void throwNothing_wrongType(MutableJoinpoint<String, IOException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(THROW_NOTHING_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testRuntimeException() {
        try {
            new RuntimeException_Object(new RuntimeException("expected")).throwRuntimeException();
            assertThat(false).isTrue();
        } catch(Throwable actualException) {
            {
                AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(RuntimeException_Aspect.THROW_RUNTIME_EXCEPTION_RAW_TYPE);
                assertThat(beforeAdviceMethodInvoker).isNotNull();
                assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(RuntimeException_Aspect.THROW_RUNTIME_EXCEPTION_SUPER_TYPE);
                assertThat(afterAdviceMethodInvoker).isNotNull();
                assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
                assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(actualException);
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(RuntimeException_Aspect.THROW_RUNTIME_EXCEPTION_WRONG_TYPE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }
        }
    }

    static class RuntimeException_Object {

        private RuntimeException cause;


        public RuntimeException_Object(RuntimeException cause) {
            this.cause = cause;
        }

        String throwRuntimeException() {
            throw cause;
        }
    }

    @Aspect
    public static class RuntimeException_Aspect {

        private static final String THROW_RUNTIME_EXCEPTION_POINTCUT = 
                "execution(java.lang.String io.gemini.aop.integration.MutableJoinpoint_22ParametrizedThrowingType_Tests$RuntimeException_Object.throwRuntimeException())";

        private static final String THROW_RUNTIME_EXCEPTION_RAW_TYPE = "throwRuntimeException_rawType";
        private static final String THROW_RUNTIME_EXCEPTION_SUPER_TYPE = "throwRuntimeException_superType";
        private static final String THROW_RUNTIME_EXCEPTION_WRONG_TYPE = "throwRuntimeException_wrongType";

        @SuppressWarnings("rawtypes")
        @After(THROW_RUNTIME_EXCEPTION_POINTCUT)
        public void throwRuntimeException_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(THROW_RUNTIME_EXCEPTION_RAW_TYPE, new AdviceMethod().withInvoked(true));
        }

        @After(THROW_RUNTIME_EXCEPTION_POINTCUT)
        public void throwRuntimeException_sameType(MutableJoinpoint<String, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(THROW_RUNTIME_EXCEPTION_SUPER_TYPE, 
                    new AdviceMethod().withInvoked(true).withThrowing(joinpoint.getThrowing()) );
        }

        @After(THROW_RUNTIME_EXCEPTION_POINTCUT)
        public void throwRuntimeException_wrongType(MutableJoinpoint<String, IOException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(THROW_RUNTIME_EXCEPTION_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testMultiException() {
        try {
            new MultiException_Object(new MultiException_Object.ExceptionA_Object("expected")).throwMultiExceptions();
            assertThat(false).isTrue();
        } catch(Throwable actualException) {
            {
                AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(MultiException_Aspect.THROW_RUNTIME_EXCEPTION_RAW_TYPE);
                assertThat(beforeAdviceMethodInvoker).isNotNull();
                assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(MultiException_Aspect.THROW_RUNTIME_EXCEPTION_SUPER_TYPE);
                assertThat(afterAdviceMethodInvoker).isNotNull();
                assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
                assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(actualException);
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(MultiException_Aspect.THROW_RUNTIME_EXCEPTION_WRONG_TYPE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }
        }
    }

    static class MultiException_Object {

        private ExceptionA_Object cause;


        public MultiException_Object(ExceptionA_Object cause) {
            this.cause = cause;
        }

        String throwMultiExceptions() throws ExceptionA_Object, ExceptionB_Object {
            throw cause;
        }

        static class ExceptionA_Object extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = 1107049581013249689L;

            public ExceptionA_Object(String message) {
                super(message);
            }
        }

        static class ExceptionB_Object extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = -5207248475132102808L;

            public ExceptionB_Object(String message) {
                super(message);
            }
        }
    }

    @Aspect
    public static class MultiException_Aspect {

        private static final String THROW_RUNTIME_EXCEPTION_POINTCUT = 
                "execution(java.lang.String io.gemini.aop.integration.MutableJoinpoint_22ParametrizedThrowingType_Tests$MultiException_Object.throwMultiExceptions())";

        private static final String THROW_RUNTIME_EXCEPTION_RAW_TYPE = "throwMultiException_rawType";
        private static final String THROW_RUNTIME_EXCEPTION_SUPER_TYPE = "throwMultiException_superType";
        private static final String THROW_RUNTIME_EXCEPTION_WRONG_TYPE = "throwMultiException_wrongType";

        @SuppressWarnings("rawtypes")
        @After(THROW_RUNTIME_EXCEPTION_POINTCUT)
        public void throwMultiException_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(THROW_RUNTIME_EXCEPTION_RAW_TYPE, new AdviceMethod().withInvoked(true));
        }

        @After(THROW_RUNTIME_EXCEPTION_POINTCUT)
        public void throwMultiException_superType(MutableJoinpoint<String, Exception> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(THROW_RUNTIME_EXCEPTION_SUPER_TYPE, 
                    new AdviceMethod().withInvoked(true).withThrowing(joinpoint.getThrowing()) );
        }

        @After(THROW_RUNTIME_EXCEPTION_POINTCUT)
        public void throwMultiException_wrongType(MutableJoinpoint<String, MultiException_Object.ExceptionA_Object> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(THROW_RUNTIME_EXCEPTION_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }
}
