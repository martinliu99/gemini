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
import org.aspectj.lang.annotation.Pointcut;
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
public class Advisor_11AdviceParamBinding_Tests extends AbstractIntegrationTests {

    @Test
    public void testTargetObjectBinding() {
        TargetObjectBinding_Object object = new TargetObjectBinding_Object();
        object.bindTargetObject();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetObjectBinding_Aspect.BIND_TARGET_OBJECT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThisObject()).isEqualTo(object);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetObjectBinding_Aspect.REFERENCE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThisObject()).isEqualTo(object);
        }
    }

    protected static class TargetObjectBinding_Object {

        public long bindTargetObject() {
            return 1l;
        }
    }

    @Aspect
    public static class TargetObjectBinding_Aspect {

        private static final String BIND_TARGET_OBJECT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_11AdviceParamBinding_Tests$TargetObjectBinding_Object.bindTargetObject()) && this(targetObject)";

        private static final String BIND_TARGET_OBJECT_AFTER_ADVICE = TargetObjectBinding_Aspect.class.getName() + ".bindTargetObject_afterAdvice";

        @After(value = BIND_TARGET_OBJECT_POINTCUT, argNames = "targetObject")
        public void bindTargetObject_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, TargetObjectBinding_Object targetObject) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_OBJECT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(targetObject) );
        }


        private static final String REFERENCE_POINTCUT_AFTER_ADVICE = TargetObjectBinding_Aspect.class.getName() + ".reference_pointcut_afterAdvice";

        @Pointcut(BIND_TARGET_OBJECT_POINTCUT)
        public void bindTargetObject(TargetObjectBinding_Object targetObject) {  }

        @After(value = "bindTargetObject(targetObject)", argNames = "targetObject")
        public void reference_pointcut_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, TargetObjectBinding_Object targetObject) {
            ExecutionMemento.putAdviceMethodInvoker(REFERENCE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(targetObject) );
        }
    }


    @Test
    public void testTargetArgumentBinding() {
        long arg1 = 1l;
        long arg2 = 2l;
        TargetArgumentBinding_Object object = new TargetArgumentBinding_Object();
        object.bindTargetArgument(arg1, arg2);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_Aspect.BIND_TARGET_ARGUMENT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(
                    new Object[] {arg1, arg2} );
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_Aspect.REFERENCE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(
                    new Object[] {arg1, arg2} );
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_Aspect.BIND_INCONSISTENT_PARAMS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_Aspect.BIND_PARTIAL_PARAMS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(
                    new Object[] {arg1} );
        }
    }

    public static class TargetArgumentBinding_Object {

        public long bindTargetArgument(long _long, Long string) {
            return _long + Long.valueOf(string);
        }
    }

    @Aspect
    public static class TargetArgumentBinding_Aspect {

        public static final String BIND_TARGET_ARGUMENT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_11AdviceParamBinding_Tests$TargetArgumentBinding_Object.bindTargetArgument(..)) && args(_long, string)";

        private static final String BIND_TARGET_ARGUMENT_AFTER_ADVICE = TargetArgumentBinding_Aspect.class.getName() + ".bindTargetArgument_afterAdvice";

        @After(value = BIND_TARGET_ARGUMENT_POINTCUT, argNames = "_long, string")
        public void bindTargetArgument_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, long _long, Number string) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_ARGUMENT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {_long, string}) );
        }


        private static final String REFERENCE_POINTCUT_AFTER_ADVICE = TargetArgumentBinding_Aspect.class.getName() + ".reference_pointcut_afterAdvice";

        @Pointcut(BIND_TARGET_ARGUMENT_POINTCUT)
        public void bindTargetArgument(long _long, long string) {  }

        @After(value = "bindTargetArgument(_long, string)", argNames = "string, _long")
        public void reference_pointcut_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, long string, long _long) {
            ExecutionMemento.putAdviceMethodInvoker(REFERENCE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {_long, string}) );
        }


        private static final String BIND_INCONSISTENT_PARAMS_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_11AdviceParamBinding_Tests$TargetArgumentBinding_Object.bindTargetArgument(..)) && args(_long, string)";

        private static final String BIND_INCONSISTENT_PARAMS_AFTER_ADVICE = TargetArgumentBinding_Aspect.class.getName() + ".bindInconsistentParams_afterAdvice";

        @After(value = BIND_INCONSISTENT_PARAMS_POINTCUT, argNames = "string, _long, integer")
        public void bindInconsistentParams_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, long string, long _long, int integer) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_INCONSISTENT_PARAMS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }


        private static final String BIND_PARTIAL_PARAMS_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_11AdviceParamBinding_Tests$TargetArgumentBinding_Object.bindTargetArgument(..)) && args(_long, ..)";

        private static final String BIND_PARTIAL_PARAMS_AFTER_ADVICE = TargetArgumentBinding_Aspect.class.getName() + ".bindPartialParams_afterAdvice";

        @After(value = BIND_PARTIAL_PARAMS_POINTCUT, argNames = "_long")
        public void bindPartialParams_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, long _long) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_PARTIAL_PARAMS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {_long}) );
        }
    }


    @Test
    public void testTargetReturningBinding() {
        TargetReturningBinding_Object object = new TargetReturningBinding_Object();
        long returning = object.bindTargetReturning();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspect.BIND_TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(returning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspect.REFERENCE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(returning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspect.BIND_SUPER_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(returning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspect.BIND_WRONG_TYPE_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspect.BIND_WRONG_PARAM_NAME_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class TargetReturningBinding_Object {

        public long bindTargetReturning() {
            return 1l;
        }
    }

    @Aspect
    public static class TargetReturningBinding_Aspect {

        private static final String BIND_TARGET_RETURNING_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advisor_11AdviceParamBinding_Tests$TargetReturningBinding_Object.bindTargetReturning())";

        private static final String BIND_TARGET_RETURNING_AFTER_ADVICE = TargetReturningBinding_Aspect.class.getName() + ".bindTargetReturning_afterAdvice";

        @AfterReturning(value = BIND_TARGET_RETURNING_POINTCUT, returning = "returning")
        public void bindTargetReturning_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, long returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }


        private static final String REFERENCE_POINTCUT_AFTER_ADVICE = TargetReturningBinding_Aspect.class.getName() + ".reference_pointcut_afterAdvice";

        @Pointcut(BIND_TARGET_RETURNING_POINTCUT)
        public void bindTargetReturning() {  }

        @AfterReturning(value = "bindTargetReturning()", returning = "returning")
        public void reference_pointcut_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, Long returning) {
            ExecutionMemento.putAdviceMethodInvoker(REFERENCE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }


        private static final String BIND_SUPER_RETURNING_AFTER_ADVICE = TargetReturningBinding_Aspect.class.getName() + ".bindSuperReturning_afterAdvice";

        @AfterReturning(value = BIND_TARGET_RETURNING_POINTCUT, returning = "returning")
        public void bindSuperReturning_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, Number returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_SUPER_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }


        private static final String BIND_WRONG_TYPE_POINTCUT = 
                "execution(* io.gemini.aop.integration.Advisor_11AdviceParamBinding_Tests$TargetReturningBinding_Object.bindTargetReturning())";

        private static final String BIND_WRONG_TYPE_AFTER_ADVICE = TargetReturningBinding_Aspect.class.getName() + ".bindWrongType_afterAdvice";

        @AfterReturning(value = BIND_WRONG_TYPE_POINTCUT, returning = "returning")
        public void bindWrongType_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, String returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_WRONG_TYPE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }


        private static final String BIND_WRONG_PARAM_NAME_POINTCUT = 
                "execution(* io.gemini.aop.integration.Advisor_11AdviceParamBinding_Tests$TargetReturningBinding_Object.bindTargetReturning())";

        private static final String BIND_WRONG_PARAM_NAME_AFTER_ADVICE = TargetReturningBinding_Aspect.class.getName() + ".bindWrongParamName_afterAdvice";

        @AfterReturning(value = BIND_WRONG_PARAM_NAME_POINTCUT, returning = "returning1")
        public void bindWrongParamName_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, String returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_WRONG_PARAM_NAME_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }
    }


    @Test
    public void testTargetThrowingBinding() {
        try {
            new TargetThrowingBinding_Object( new TargetThrowingBinding_Object.ExceptionA_Object("expected") )
            .bindTargetThrowing();
            assertThat(false).isTrue();
        } catch (Exception actualException) {
            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_Aspect.BIND_TARGET_THROWING_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNotNull();
                assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
                assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(actualException);
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_Aspect.REFERENCE_POINTCUT_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNotNull();
                assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
                assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(actualException);
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_Aspect.BIND_WRONG_TYPE_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_Aspect.BIND_WRONG_PARAM_NAME_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }
        }
    }

    protected static class TargetThrowingBinding_Object {

        private ExceptionA_Object cause;


        public TargetThrowingBinding_Object(ExceptionA_Object cause) {
            this.cause = cause;
        }

        public void bindTargetThrowing() throws ExceptionA_Object, ExceptionB_Object {
            throw cause;
        }


        protected static class ExceptionA_Object extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = 3890639293213333957L;

            public ExceptionA_Object(String message) {
                super(message);
            }
        }

        protected static class ExceptionB_Object extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = -6870668732159554323L;

            public ExceptionB_Object(String message) {
                super(message);
            }
        }
    }

    @Aspect
    public static class TargetThrowingBinding_Aspect {

        private static final String BIND_TARGET_THROWING_POINTCUT = 
                "execution(!private void io.gemini.aop.integration.Advisor_11AdviceParamBinding_Tests$TargetThrowingBinding_Object.bindTargetThrowing())";

        private static final String BIND_TARGET_THROWING_AFTER_ADVICE = TargetThrowingBinding_Aspect.class.getName() + ".bindTargetThrowing_afterAdvice";

        @AfterThrowing(value = BIND_TARGET_THROWING_POINTCUT, throwing = "throwing")
        public void bindTargetThrowing_afterAdvice(MutableJoinpoint<Void, Exception> joinpoint, Exception throwing) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }


        private static final String REFERENCE_POINTCUT_AFTER_ADVICE = TargetThrowingBinding_Aspect.class.getName() + ".reference_pointcut_afterAdvice";

        @Pointcut(BIND_TARGET_THROWING_POINTCUT)
        public void bindTargetThrowing() {  }

        @AfterThrowing(value = "bindTargetThrowing()", throwing = "throwing")
        public void reference_pointcut_afterAdvice(MutableJoinpoint<Void, Exception> joinpoint, Exception throwing) {
            ExecutionMemento.putAdviceMethodInvoker(REFERENCE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }


        private static final String BIND_WRONG_TYPE_AFTER_ADVICE = TargetThrowingBinding_Aspect.class.getName() + ".bindWrongType_afterAdvice";

        @AfterThrowing(value = BIND_TARGET_THROWING_POINTCUT, throwing = "throwing")
        public void bindWrongType_afterAdvice(MutableJoinpoint<Void, TargetThrowingBinding_Object.ExceptionA_Object> joinpoint, TargetThrowingBinding_Object.ExceptionA_Object throwing) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_WRONG_TYPE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }


        private static final String BIND_WRONG_PARAM_NAME_AFTER_ADVICE = TargetThrowingBinding_Aspect.class.getName() + ".bindWrongParamName_afterAdvice";

        @AfterThrowing(value = BIND_TARGET_THROWING_POINTCUT, throwing = "throwing1")
        public void bindWrongParamName_afterAdvice(MutableJoinpoint<Void, Exception> joinpoint, Exception throwing) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_WRONG_PARAM_NAME_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }
}
}
