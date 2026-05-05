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

import org.junit.jupiter.api.Test;

import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.annotation.ExprPointcut;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.OnMethodExit;

/**
 * Tests parameter binding of ByteBuddy advice via {@link net.bytebuddy.asm.Advice} annotations.
 *
 * @author   martin.liu
 */
public class Advice_07ByteBuddyParamBinding {

    @Test
    public void testTargetObjectBinding() {
        TargetObjectBinding_Object object = new TargetObjectBinding_Object();
        object.bindTargetObject();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetObjectBinding_Advisor.BIND_TARGET_OBJECT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getTargetObject()).isEqualTo(object);
        }
    }

    protected static class TargetObjectBinding_Object {

        public long bindTargetObject() {
            return 1l;
        }
    }

    @ExprPointcut(TargetObjectBinding_Advisor.BIND_TARGET_OBJECT_POINTCUT)
    public static class TargetObjectBinding_Advisor {

        private static final String BIND_TARGET_OBJECT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advice_07ByteBuddyParamBinding$TargetObjectBinding_Object.bindTargetObject())";

        private static final String BIND_TARGET_OBJECT_AFTER_ADVICE = TargetObjectBinding_Advisor.class.getName() + ".bindTargetObject_afterAdvice";


        @OnMethodExit(inline = false)
        public static void bindTargetObject_afterAdvice(
                @Advice.This TargetObjectBinding_Object targetObject) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_OBJECT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withTargetObject(targetObject) );
        }
    }


    @Test
    public void testTargetArgumentBinding() {
        long arg1 = 1l;
        String arg2 = "2";
        TargetArgumentBinding_Object object = new TargetArgumentBinding_Object();
        object.bindTargetArgument(arg1, arg2);

        Object[] expectedArgs = new Object[] {arg1, arg2};

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_AllArguemtns_Advisor.BIND_TARGET_ARGUMENT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo( expectedArgs );
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_ImplicitBinding_Advisor.BIND_TARGET_ARGUMENT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo( expectedArgs );
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_ExplicitBinding_Advisor.BIND_TARGET_ARGUMENT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo( expectedArgs );
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetArgumentBinding_WrongType_Advisor.BIND_TARGET_ARGUMENT_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    public static class TargetArgumentBinding_Object {

        private static final String BIND_TARGET_ARGUMENT_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advice_07ByteBuddyParamBinding$TargetArgumentBinding_Object.bindTargetArgument(..))";


        /**
         * Here only primitive type could be bound to super type.
         */
        public long bindTargetArgument(long _long, String string) {
            return _long + Long.valueOf(string);
        }
    }

    @ExprPointcut(TargetArgumentBinding_Object.BIND_TARGET_ARGUMENT_POINTCUT)
    public static class TargetArgumentBinding_AllArguemtns_Advisor {

        private static final String BIND_TARGET_ARGUMENT_AFTER_ADVICE = TargetArgumentBinding_AllArguemtns_Advisor.class.getName() + ".bindTargetArgument_afterAdvice";


        @OnMethodExit(inline = false)
        public static void bindTargetArgument_afterAdvice(
                @Advice.AllArguments Object[] args) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_ARGUMENT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(args) );
        }
    }

    @ExprPointcut(TargetArgumentBinding_Object.BIND_TARGET_ARGUMENT_POINTCUT)
    public static class TargetArgumentBinding_ImplicitBinding_Advisor {

        private static final String BIND_TARGET_ARGUMENT_AFTER_ADVICE = TargetArgumentBinding_ImplicitBinding_Advisor.class.getName() + ".bindTargetArgument_afterAdvice";


        @OnMethodExit(inline = false)
        public static void bindTargetArgument_afterAdvice(Number _long, String string) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_ARGUMENT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {_long, string}) );
        }
    }

    @ExprPointcut(TargetArgumentBinding_Object.BIND_TARGET_ARGUMENT_POINTCUT)
    public static class TargetArgumentBinding_ExplicitBinding_Advisor {

        private static final String BIND_TARGET_ARGUMENT_AFTER_ADVICE = TargetArgumentBinding_ExplicitBinding_Advisor.class.getName() + ".bindTargetArgument_afterAdvice";


        @OnMethodExit(inline = false)
        public static void bindTargetArgument_afterAdvice(
                @Advice.Argument(1) String string,
                @Advice.Argument(0) Number _long) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_ARGUMENT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {_long, string}) );
        }
    }

//    @ExprPointcut(TargetArgumentBinding_Object.BIND_TARGET_ARGUMENT_POINTCUT)
    public static class TargetArgumentBinding_WrongType_Advisor {

        private static final String BIND_TARGET_ARGUMENT_AFTER_ADVICE = TargetArgumentBinding_WrongType_Advisor.class.getName() + ".bindTargetArgument_afterAdvice";


        @OnMethodExit(inline = false)
        public static void bindTargetArgument_afterAdvice(
                @Advice.Argument(0) Number _long, 
                @Advice.Argument(1) Number string) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_ARGUMENT_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(new Object[] {_long, string}) );
        }
    }


    @Test
    public void testTargetReturningBinding() {
        TargetReturningBinding_Object object = new TargetReturningBinding_Object();
        long returning = object.bindTargetReturning();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_Advisor.BIND_TARGET_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(returning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_SuperType_Advisor.BIND_SUPER_RETURNING_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(returning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetReturningBinding_WrongType_Advisor.BIND_WRONG_TYPE_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    static class TargetReturningBinding_Object {

        private static final String BIND_TARGET_RETURNING_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Advice_07ByteBuddyParamBinding$TargetReturningBinding_Object.bindTargetReturning())";


        public long bindTargetReturning() {
            return 1l;
        }
    }

    @ExprPointcut(TargetReturningBinding_Object.BIND_TARGET_RETURNING_POINTCUT)
    public static class TargetReturningBinding_Advisor {

        private static final String BIND_TARGET_RETURNING_AFTER_ADVICE = TargetReturningBinding_Advisor.class.getName() + ".bindTargetReturning_afterAdvice";

        @OnMethodExit(inline = false)
        public static void bindTargetReturning_afterAdvice(
                @Advice.Return long returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }
    }

    @ExprPointcut(TargetReturningBinding_Object.BIND_TARGET_RETURNING_POINTCUT)
    public static class TargetReturningBinding_SuperType_Advisor {

        private static final String BIND_SUPER_RETURNING_AFTER_ADVICE = TargetReturningBinding_SuperType_Advisor.class.getName() + ".bindSuperReturning_afterAdvice";

        @OnMethodExit(inline = false)
        public static void bindSuperReturning_afterAdvice(
                @Advice.Return Number returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_SUPER_RETURNING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(returning) );
        }
    }

//    @ExprPointcut(TargetReturningBinding_Object.BIND_TARGET_RETURNING_POINTCUT)
    public static class TargetReturningBinding_WrongType_Advisor {

        private static final String BIND_WRONG_TYPE_AFTER_ADVICE = TargetReturningBinding_Advisor.class.getName() + ".bindWrongType_afterAdvice";


        @OnMethodExit(inline = false)
        public static void bindWrongType_afterAdvice(
                @Advice.Return String returning) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_WRONG_TYPE_AFTER_ADVICE, 
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
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_Advisor.BIND_TARGET_THROWING_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNotNull();
                assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
                assertThat(afterAdviceMethodInvoker.getThrowing()).isEqualTo(actualException);
            }

            {
                AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TargetThrowingBinding_WrongType_Advisor.BIND_WRONG_TYPE_AFTER_ADVICE);
                assertThat(afterAdviceMethodInvoker).isNull();
            }
        }
    }

    protected static class TargetThrowingBinding_Object {

        private static final String BIND_TARGET_THROWING_POINTCUT = 
                "execution(!private void io.gemini.aop.integration.Advice_07ByteBuddyParamBinding$TargetThrowingBinding_Object.bindTargetThrowing())";


        private ExceptionA_Object cause;


        public TargetThrowingBinding_Object(ExceptionA_Object cause) {
            this.cause = cause;
        }

        public void bindTargetThrowing() throws ExceptionA_Object, ExceptionB_Object {
            throw cause;
        }


        protected static class ExceptionA_Object extends Exception {

            private static final long serialVersionUID = 3890639293213333957L;

            public ExceptionA_Object(String message) {
                super(message);
            }
        }

        protected static class ExceptionB_Object extends Exception {

            private static final long serialVersionUID = -6870668732159554323L;

            public ExceptionB_Object(String message) {
                super(message);
            }
        }

        protected static class ExceptionC_Object extends Exception {

            private static final long serialVersionUID = -6870668732159554323L;

            public ExceptionC_Object(String message) {
                super(message);
            }
        }
    }

    @ExprPointcut(TargetThrowingBinding_Object.BIND_TARGET_THROWING_POINTCUT)
    public static class TargetThrowingBinding_Advisor {

        private static final String BIND_TARGET_THROWING_AFTER_ADVICE = TargetThrowingBinding_Advisor.class.getName() + ".bindTargetThrowing_afterAdvice";


        @OnMethodExit(inline = false, onThrowable = Exception.class)
        public static void bindTargetThrowing_afterAdvice(
                @Advice.Thrown Exception throwing) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_TARGET_THROWING_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }
    }

    @ExprPointcut(TargetThrowingBinding_Object.BIND_TARGET_THROWING_POINTCUT)
    public static class TargetThrowingBinding_WrongType_Advisor {

        private static final String BIND_WRONG_TYPE_AFTER_ADVICE = TargetThrowingBinding_Advisor.class.getName() + ".bindWrongType_afterAdvice";

        @OnMethodExit(inline = false, onThrowable = TargetThrowingBinding_Object.ExceptionC_Object.class)
        public static void bindWrongType_afterAdvice(
                @Advice.Thrown TargetThrowingBinding_Object.ExceptionC_Object throwing) {
            ExecutionMemento.putAdviceMethodInvoker(BIND_WRONG_TYPE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withThrowing(throwing) );
        }
    }
}
