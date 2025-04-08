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

import java.util.ArrayList;
import java.util.List;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.assertj.core.util.Lists;
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
public class MutableJoinpoint_03TargetArgument_Tests extends AbstractBaseTests {

    @Test
    public void testTypeInitilizer() {
        {
            new TypeInitilizer_BeforeAdvice_Objects();

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_BeforeAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getArguments()).isEmpty();

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(TypeInitilizer_AfterAdvice_Aspects.ADVICE_TYPE_INITIALIZER);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEmpty();

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(TypeInitilizer_BeforeAdvice_Objects.class.getName());
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
        @Before("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_03TargetArgument_Tests$TypeInitilizer_BeforeAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
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
        @After("staticinitialization(io.gemini.aop.aspect.MutableJoinpoint_03TargetArgument_Tests$TypeInitilizer_AfterAdvice_Objects)")
        public void typeInitializer(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(ADVICE_TYPE_INITIALIZER, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }
    }


    @Test
    public void testClassMethod() {
        {
            List<Integer> input = Lists.list(1, 2, 3);
            ClassMethod_Objects.targetArguments(input);

            Object[] expected = new Object[] {input};

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.TARGET_ARGUEMTNS_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.TARGET_ARGUEMTNS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(ClassMethod_Objects.TAGRET_ARGUMENTS);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getArguments()).isEqualTo(expected);
        }

        {
            List<Long> input = Lists.list(1L, 2L, 3L);
            long l = 0L;
            ClassMethod_Objects.modifyTargetArguments(l, input);

            List<Long> input2 = new ArrayList<>(input);
            input2.add(0, l);
            Object[] expected = new Object[] {l, input2};

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.MODIFY_TARGET_ARGUMENTS_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ClassMethod_Advices.MODIFY_TARGET_ARGUMENTS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(ClassMethod_Objects.MODIFY_TARGET_ARGUMENTS);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getArguments()).isEqualTo(expected);
        }
    }

    public static class ClassMethod_Objects {

        private static final String TAGRET_ARGUMENTS = "targetArguments";
        private static final String MODIFY_TARGET_ARGUMENTS = "modifyTargetArguments";

        public static List<Integer> targetArguments(List<Integer> input) {
            ExecutionMemento.putTargetMethodInvoker(TAGRET_ARGUMENTS, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withVArgumnts(input) );
            return input;
        }

        public static List<Long> modifyTargetArguments(long l, List<Long> input) {
            ExecutionMemento.putTargetMethodInvoker(MODIFY_TARGET_ARGUMENTS, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withVArgumnts(l, input) );
            return input;
        }
    }

    @Aspect
    public static class ClassMethod_Advices {

        private static final String TARGET_ARGUMENTS_POINTCUT = 
                "execution(public static * io.gemini.aop.aspect.MutableJoinpoint_03TargetArgument_Tests$ClassMethod_Objects.targetArguments(..))";

        private static final String TARGET_ARGUEMTNS_BEFORE_ADVICE = "targetArguments_before";
        private static final String TARGET_ARGUEMTNS_AFTER_ADVICE = "targetArguments_after";

        @SuppressWarnings("rawtypes")
        @Before(TARGET_ARGUMENTS_POINTCUT)
        public void targetArguments_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_ARGUEMTNS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }

        @SuppressWarnings("rawtypes")
        @After(TARGET_ARGUMENTS_POINTCUT)
        public void targetArguments_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_ARGUEMTNS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }


        private static final String MODIFY_TARGET_ARGUMENTS_POINTCUT = 
                "execution(public static * io.gemini.aop.aspect.MutableJoinpoint_03TargetArgument_Tests$ClassMethod_Objects.modifyTargetArguments(..))";

        private static final String MODIFY_TARGET_ARGUMENTS_BEFORE_ADVICE = "modifyTargetArguments_before";
        private static final String MODIFY_TARGET_ARGUMENTS_AFTER_ADVICE = "modifyTargetArguments_after";

        @SuppressWarnings("unchecked")
        @Before(MODIFY_TARGET_ARGUMENTS_POINTCUT)
        public void modifyTargetArguments_before(MutableJoinpoint<List<Long>, RuntimeException> joinpoint) {
            long l = (Long) joinpoint.getArguments()[0];
            List<Long> input = (List<Long>) joinpoint.getArguments()[1];
            input = new ArrayList<>(input);
            input.add(0, l);
            joinpoint.getArguments()[1] = input;

            ExecutionMemento.putAdviceMethodInvoker(MODIFY_TARGET_ARGUMENTS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }

        @After(MODIFY_TARGET_ARGUMENTS_POINTCUT)
        public void modifyTargetArguments_after(MutableJoinpoint<List<Long>, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MODIFY_TARGET_ARGUMENTS_AFTER_ADVICE, 
                    new AdviceMethod()
                    .withInvoked(true)
                    .withArgumnts(joinpoint.getArguments()) );
        }
    }


    @Test
    public void testInstanceConstructor() {
        {
            List<Integer> input = Lists.list(1, 2, 3);
            new InstanceConstructor_Objects(input);

            Object[] expected = new Object[] {input};

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.TARGET_ARGUEMTNS_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.TARGET_ARGUEMTNS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceConstructor_Objects.TAGRET_ARGUMENTS);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getArguments()).isEqualTo(expected);
        }

        {
            List<Long> input = Lists.list(1L, 2L, 3L);
            long l = 0L;
            new InstanceConstructor_Objects(l, input);

            List<Long> input2 = new ArrayList<>(input);
            input2.add(0, l);
            Object[] expected = new Object[] {l, input2};

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.MODIFY_TARGET_ARGUMENTS_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceConstructor_Advices.MODIFY_TARGET_ARGUMENTS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceConstructor_Objects.MODIFY_TARGET_ARGUMENTS);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getArguments()).isEqualTo(expected);
        }
    }

    public static class InstanceConstructor_Objects {

        private static final String TAGRET_ARGUMENTS = "targetArguments";
        private static final String MODIFY_TARGET_ARGUMENTS = "modifyTargetArguments";

        public InstanceConstructor_Objects(List<Integer> targetArguments) {
            ExecutionMemento.putTargetMethodInvoker(TAGRET_ARGUMENTS, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withVArgumnts(targetArguments) );
        }

        public InstanceConstructor_Objects(long l, List<Long> modifyTargetArguments) {
            ExecutionMemento.putTargetMethodInvoker(MODIFY_TARGET_ARGUMENTS, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withVArgumnts(l, modifyTargetArguments) );
        }
    }

    @Aspect
    public static class InstanceConstructor_Advices {

        private static final String TARGET_ARGUMENTS_POINTCUT = 
                "execution(public io.gemini.aop.aspect.MutableJoinpoint_03TargetArgument_Tests$InstanceConstructor_Objects.new(java.util.List<java.lang.Integer>))";

        private static final String TARGET_ARGUEMTNS_BEFORE_ADVICE = "targetArguments_before";
        private static final String TARGET_ARGUEMTNS_AFTER_ADVICE = "targetArguments_after";

        @SuppressWarnings("rawtypes")
        @Before(TARGET_ARGUMENTS_POINTCUT)
        public void targetArguments_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_ARGUEMTNS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }

        @SuppressWarnings("rawtypes")
        @After(TARGET_ARGUMENTS_POINTCUT)
        public void targetArguments_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_ARGUEMTNS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }


        private static final String MODIFY_TARGET_ARGUMENTS_POINTCUT = 
                "execution(public io.gemini.aop.aspect.MutableJoinpoint_03TargetArgument_Tests$InstanceConstructor_Objects.new(long, java.util.List<java.lang.Long>))";

        private static final String MODIFY_TARGET_ARGUMENTS_BEFORE_ADVICE = "modifyTargetArguments_before";
        private static final String MODIFY_TARGET_ARGUMENTS_AFTER_ADVICE = "modifyTargetArguments_after";

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Before(MODIFY_TARGET_ARGUMENTS_POINTCUT)
        public void modifyTargetArguments_before(MutableJoinpoint joinpoint) {
            long l = (Long) joinpoint.getArguments()[0];
            List<Long> input = (List<Long>) joinpoint.getArguments()[1];
            input = new ArrayList<>(input);
            input.add(0, l);
            joinpoint.getArguments()[1] = input;

            ExecutionMemento.putAdviceMethodInvoker(MODIFY_TARGET_ARGUMENTS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }

        @After(MODIFY_TARGET_ARGUMENTS_POINTCUT)
        public void modifyTargetArguments_after(@SuppressWarnings("rawtypes") MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MODIFY_TARGET_ARGUMENTS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }
    }


    @Test
    public void testInstanceMethod() {
        InstanceMethod_Objects thisObject = new InstanceMethod_Objects();

        {
            List<Integer> input = Lists.list(1, 2, 3);
            thisObject.targetArguments(input);

            Object[] expected = new Object[] {input};

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.TARGET_ARGUEMTNS_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.TARGET_ARGUEMTNS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceMethod_Objects.TAGRET_ARGUMENTS);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getArguments()).isEqualTo(expected);
        }

        {
            List<Long> input = Lists.list(1L, 2L, 3L);
            long l = 0L;
            thisObject.modifyTargetArguments(l, input);

            List<Long> input2 = new ArrayList<>(input);
            input2.add(0, l);
            Object[] expected = new Object[] {l, input2};

            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.MODIFY_TARGET_ARGUMENTS_BEFORE_ADVICE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(InstanceMethod_Aspects.MODIFY_TARGET_ARGUMENTS_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getArguments()).isEqualTo(expected);

            TargetMethod targetMethodInvoker = ExecutionMemento.getTargetMethodInvoker(InstanceMethod_Objects.MODIFY_TARGET_ARGUMENTS);
            assertThat(targetMethodInvoker).isNotNull();
            assertThat(targetMethodInvoker.isInvoked()).isTrue();
            assertThat(targetMethodInvoker.getArguments()).isEqualTo(expected);
        }
    }

    public static class InstanceMethod_Objects {

        private static final String TAGRET_ARGUMENTS = "targetArguments";
        private static final String MODIFY_TARGET_ARGUMENTS = "modifyTargetArguments";

        public List<Integer> targetArguments(List<Integer> input) {
            ExecutionMemento.putTargetMethodInvoker(TAGRET_ARGUMENTS, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withVArgumnts(input) );
            return input;
        }

        public List<Long> modifyTargetArguments(long l, List<Long> input) {
            ExecutionMemento.putTargetMethodInvoker(MODIFY_TARGET_ARGUMENTS, 
                    new TargetMethod()
                        .withInvoked(true)
                        .withVArgumnts(l, input) );
            return input;
        }
    }

    @Aspect
    public static class InstanceMethod_Aspects {

        private static final String TARGET_ARGUMENTS_POINTCUT = 
                "execution(public * io.gemini.aop.aspect.MutableJoinpoint_03TargetArgument_Tests$InstanceMethod_Objects.targetArguments(..))";

        private static final String TARGET_ARGUEMTNS_BEFORE_ADVICE = "targetArguments_before";
        private static final String TARGET_ARGUEMTNS_AFTER_ADVICE = "targetArguments_after";

        @SuppressWarnings("rawtypes")
        @Before(TARGET_ARGUMENTS_POINTCUT)
        public void targetArguments_before(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_ARGUEMTNS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }

        @SuppressWarnings("rawtypes")
        @After(TARGET_ARGUMENTS_POINTCUT)
        public void targetArguments_after(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(TARGET_ARGUEMTNS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }


        private static final String MODIFY_TARGET_ARGUMENTS_POINTCUT = 
                "execution(public * io.gemini.aop.aspect.MutableJoinpoint_03TargetArgument_Tests$InstanceMethod_Objects.modifyTargetArguments(..))";

        private static final String MODIFY_TARGET_ARGUMENTS_BEFORE_ADVICE = "modifyTargetArguments_before";
        private static final String MODIFY_TARGET_ARGUMENTS_AFTER_ADVICE = "modifyTargetArguments_after";

        @SuppressWarnings("unchecked")
        @Before(MODIFY_TARGET_ARGUMENTS_POINTCUT)
        public void modifyTargetArguments_before(MutableJoinpoint<List<Long>, RuntimeException> joinpoint) {
            long l = (Long) joinpoint.getArguments()[0];
            List<Long> input = (List<Long>) joinpoint.getArguments()[1];
            input = new ArrayList<>(input);
            input.add(0, l);
            joinpoint.getArguments()[1] = input;

            ExecutionMemento.putAdviceMethodInvoker(MODIFY_TARGET_ARGUMENTS_BEFORE_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }

        @After(MODIFY_TARGET_ARGUMENTS_POINTCUT)
        public void modifyTargetArguments_after(MutableJoinpoint<List<Long>, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MODIFY_TARGET_ARGUMENTS_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withArgumnts(joinpoint.getArguments()) );
        }
    }
}
