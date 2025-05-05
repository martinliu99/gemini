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
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;

/**
 * 
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class Aspect_11AdviceParamBinding_Tests extends AbstractIntegrationTests {

    @Test
    public void testTargetObjectBinding() {
        TargetObjectBinding_Objects objects = new TargetObjectBinding_Objects();
        objects.bindTargetObject();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetObjectBinding_Aspects.BIND_TARGET_OBJECT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThisObject()).isEqualTo(objects);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetObjectBinding_Aspects.REFERENCE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getThisObject()).isEqualTo(objects);
        }
    }

    protected static class TargetObjectBinding_Objects {

        public long bindTargetObject() {
            return 1l;
        }
    }

    @Aspect
    public static class TargetObjectBinding_Aspects {

        private static final String BIND_TARGET_OBJECT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Aspect_11AdviceParamBinding_Tests$TargetObjectBinding_Objects.bindTargetObject()) && this(targetObject)";

        private static final String BIND_TARGET_OBJECT_AFTER_ADVICE = TargetObjectBinding_Aspects.class.getName() + ".bindTargetObject_afterAdvice";

        @After(value = BIND_TARGET_OBJECT_POINTCUT, argNames = "targetObject")
        public void bindTargetObject_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, TargetObjectBinding_Objects targetObject) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_OBJECT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(targetObject) );
        }


        private static final String REFERENCE_POINTCUT_AFTER_ADVICE = TargetObjectBinding_Aspects.class.getName() + ".reference_pointcut_afterAdvice";

        @Pointcut(BIND_TARGET_OBJECT_POINTCUT)
        public void bindTargetObject(TargetObjectBinding_Objects targetObject) {  }

        @After(value = "bindTargetObject(targetObject)", argNames = "targetObject")
        public void reference_pointcut_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, TargetObjectBinding_Objects targetObject) {
            ExecutionMemento.putAdviceMethodInvoker(REFERENCE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThisObject(targetObject) );
        }
    }


    @Test
    public void testTargetArgumentBinding() {
        long arg1 = 1l;
        String arg2 = "2";
        TargetArgumentBinding_Objects objects = new TargetArgumentBinding_Objects();
        objects.bindTargetArgument(arg1, arg2);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_Aspects.BIND_TARGET_ARGUMENT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(
                    new Object[] {arg1, arg2} );
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_Aspects.REFERENCE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(
                    new Object[] {arg1, arg2} );
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_Aspects.BIND_INCONSISTENT_PARAMS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_Aspects.BIND_PARTIAL_PARAMS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(
                    new Object[] {arg1} );
        }
    }

    private static class TargetArgumentBinding_Objects {

        public long bindTargetArgument(long _long, String string) {
            return _long + Long.valueOf(string);
        }
    }

    @Aspect
    public static class TargetArgumentBinding_Aspects {

        private static final String BIND_TARGET_ARGUMENT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Aspect_11AdviceParamBinding_Tests$TargetArgumentBinding_Objects.bindTargetArgument(..)) && args(_long, string)";

        private static final String BIND_TARGET_ARGUMENT_AFTER_ADVICE = TargetArgumentBinding_Aspects.class.getName() + ".bindTargetArgument_afterAdvice";

        @After(value = BIND_TARGET_ARGUMENT_POINTCUT, argNames = "_long, string")
        public void bindTargetArgument_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, long _long, String string) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_ARGUMENT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {_long, string}) );
        }


        private static final String REFERENCE_POINTCUT_AFTER_ADVICE = TargetArgumentBinding_Aspects.class.getName() + ".reference_pointcut_afterAdvice";

        @Pointcut(BIND_TARGET_ARGUMENT_POINTCUT)
        public void bindTargetArgument(long _long, String string) {  }

        @After(value = "bindTargetArgument(_long, string)", argNames = "string, _long")
        public void reference_pointcut_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, String string, long _long) {
            ExecutionMemento.putAdviceMethodInvoker(REFERENCE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {_long, string}) );
        }


        private static final String BIND_INCONSISTENT_PARAMS_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Aspect_11AdviceParamBinding_Tests$TargetArgumentBinding_Objects.bindTargetArgument(..)) && args(_long, string)";

        private static final String BIND_INCONSISTENT_PARAMS_AFTER_ADVICE = TargetArgumentBinding_Aspects.class.getName() + ".bindInconsistentParams_afterAdvice";

        @After(value = BIND_INCONSISTENT_PARAMS_POINTCUT, argNames = "string, _long, integer")
        public void bindInconsistentParams_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, String string, long _long, int integer) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_INCONSISTENT_PARAMS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true) );
        }


        private static final String BIND_PARTIAL_PARAMS_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Aspect_11AdviceParamBinding_Tests$TargetArgumentBinding_Objects.bindTargetArgument(..)) && args(_long, ..)";

        private static final String BIND_PARTIAL_PARAMS_AFTER_ADVICE = TargetArgumentBinding_Aspects.class.getName() + ".bindPartialParams_afterAdvice";

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
        TargetReturningBinding_Objects objects = new TargetReturningBinding_Objects();
        long returning = objects.bindTargetReturning();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspects.BIND_TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(returning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspects.REFERENCE_POINTCUT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(returning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspects.BIND_SUPER_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(returning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspects.BIND_WRONG_TYPE_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Aspects.BIND_WRONG_PARAM_NAME_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    private static class TargetReturningBinding_Objects {

        public long bindTargetReturning() {
            return 1l;
        }
    }

    @Aspect
    public static class TargetReturningBinding_Aspects {

        private static final String BIND_TARGET_RETURNING_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Aspect_11AdviceParamBinding_Tests$TargetReturningBinding_Objects.bindTargetReturning())";

        private static final String BIND_TARGET_RETURNING_AFTER_ADVICE = TargetReturningBinding_Aspects.class.getName() + ".bindTargetReturning_afterAdvice";

        @AfterReturning(value = BIND_TARGET_RETURNING_POINTCUT, returning = "returning")
        public void bindTargetReturning_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, long returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }


        private static final String REFERENCE_POINTCUT_AFTER_ADVICE = TargetReturningBinding_Aspects.class.getName() + ".reference_pointcut_afterAdvice";

        @Pointcut(BIND_TARGET_RETURNING_POINTCUT)
        public void bindTargetReturning() {  }

        @AfterReturning(value = "bindTargetReturning()", returning = "returning")
        public void reference_pointcut_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, Long returning) {
            ExecutionMemento.putAdviceMethodInvoker(REFERENCE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }


        private static final String BIND_SUPER_RETURNING_AFTER_ADVICE = TargetReturningBinding_Aspects.class.getName() + ".bindSuperReturning_afterAdvice";

        @AfterReturning(value = BIND_TARGET_RETURNING_POINTCUT, returning = "returning")
        public void bindSuperReturning_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, Number returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_SUPER_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }


        private static final String BIND_WRONG_TYPE_POINTCUT = 
                "execution(* io.gemini.aop.integration.Aspect_11AdviceParamBinding_Tests$TargetReturningBinding_Objects.bindTargetReturning())";

        private static final String BIND_WRONG_TYPE_AFTER_ADVICE = TargetReturningBinding_Aspects.class.getName() + ".bindWrongType_afterAdvice";

        @AfterReturning(value = BIND_WRONG_TYPE_POINTCUT, returning = "returning")
        public void bindWrongType_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint, String returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_WRONG_TYPE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }


        private static final String BIND_WRONG_PARAM_NAME_POINTCUT = 
                "execution(* io.gemini.aop.integration.Aspect_11AdviceParamBinding_Tests$TargetReturningBinding_Objects.bindTargetReturning())";

        private static final String BIND_WRONG_PARAM_NAME_AFTER_ADVICE = TargetReturningBinding_Aspects.class.getName() + ".bindWrongParamName_afterAdvice";

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
            new TargetThrowingBinding_Objects( new TargetThrowingBinding_Objects.ExceptionA_Objects("expected") )
            .bindTargetThrowing();
            assertThat(false).isTrue();
        } catch(Exception actualException) {
            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_Aspects.BIND_TARGET_THROWING_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNotNull();
                assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
                assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(actualException);
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_Aspects.REFERENCE_POINTCUT_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNotNull();
                assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
                assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(actualException);
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_Aspects.BIND_WRONG_TYPE_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_Aspects.BIND_WRONG_PARAM_NAME_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }
        }
    }

    protected static class TargetThrowingBinding_Objects {

        private ExceptionA_Objects cause;


        public TargetThrowingBinding_Objects(ExceptionA_Objects cause) {
            this.cause = cause;
        }

        public void bindTargetThrowing() throws ExceptionA_Objects, ExceptionB_Objects {
            throw cause;
        }


        protected static class ExceptionA_Objects extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = 3890639293213333957L;

            public ExceptionA_Objects(String message) {
                super(message);
            }
        }

        protected static class ExceptionB_Objects extends Exception {

            /**
             * 
             */
            private static final long serialVersionUID = -6870668732159554323L;

            public ExceptionB_Objects(String message) {
                super(message);
            }
        }
    }

    @Aspect
    public static class TargetThrowingBinding_Aspects {

        private static final String BIND_TARGET_THROWING_POINTCUT = 
                "execution(!private void io.gemini.aop.integration.Aspect_11AdviceParamBinding_Tests$TargetThrowingBinding_Objects.bindTargetThrowing())";

        private static final String BIND_TARGET_THROWING_AFTER_ADVICE = TargetThrowingBinding_Aspects.class.getName() + ".bindTargetThrowing_afterAdvice";

        @AfterThrowing(value = BIND_TARGET_THROWING_POINTCUT, throwing = "throwing")
        public void bindTargetThrowing_afterAdvice(MutableJoinpoint<Void, Exception> joinpoint, Exception throwing) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }


        private static final String REFERENCE_POINTCUT_AFTER_ADVICE = TargetThrowingBinding_Aspects.class.getName() + ".reference_pointcut_afterAdvice";

        @Pointcut(BIND_TARGET_THROWING_POINTCUT)
        public void bindTargetThrowing() {  }

        @AfterThrowing(value = "bindTargetThrowing()", throwing = "throwing")
        public void reference_pointcut_afterAdvice(MutableJoinpoint<Void, Exception> joinpoint, Exception throwing) {
            ExecutionMemento.putAdviceMethodInvoker(REFERENCE_POINTCUT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }


        private static final String BIND_WRONG_TYPE_AFTER_ADVICE = TargetThrowingBinding_Aspects.class.getName() + ".bindWrongType_afterAdvice";

        @AfterThrowing(value = BIND_TARGET_THROWING_POINTCUT, throwing = "throwing")
        public void bindWrongType_afterAdvice(MutableJoinpoint<Void, TargetThrowingBinding_Objects.ExceptionA_Objects> joinpoint, TargetThrowingBinding_Objects.ExceptionA_Objects throwing) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_WRONG_TYPE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }


        private static final String BIND_WRONG_PARAM_NAME_AFTER_ADVICE = TargetThrowingBinding_Aspects.class.getName() + ".bindWrongParamName_afterAdvice";

        @AfterThrowing(value = BIND_TARGET_THROWING_POINTCUT, throwing = "throwing1")
        public void bindWrongParamName_afterAdvice(MutableJoinpoint<Void, Exception> joinpoint, Exception throwing) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_WRONG_PARAM_NAME_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }
}
}
