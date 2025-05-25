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

import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;

import io.gemini.aop.test.AbstractIntegrationTests;
import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aspect.Advice;
import io.gemini.api.aspect.AspectSpec;
import io.gemini.api.aspect.AspectSpec.PojoPointcutSpec;
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;


public class Pointcut_01JoinpointMatching_Tests extends AbstractIntegrationTests {

    @Test
    public void testVoidMatching() {
        new VoidMatching_Objects().matchVoid();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(VoidMatching_Aspects.MATCH_VOID_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(VoidMatching_Advices.MATCH_VOID_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isNull();
        }
    }

    private static class VoidMatching_Objects {

        private void matchVoid() {
            return;
        }
    }

    @Aspect
    public static class VoidMatching_Aspects {

        private static final String MATCH_VOID_POINTCUT = 
                "execution(void io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$VoidMatching_Objects.matchVoid())";

        private static final String MATCH_VOID_AFTER_ADVICE = VoidMatching_Aspects.class.getName() + ".matchVoid_afterAdvice";

        @After(MATCH_VOID_POINTCUT)
        public void matchVoid_afterAdvice(MutableJoinpoint<Void, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_VOID_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class VoidMatching_Advices extends Advice.AbstractAfter<Void, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String MATCH_VOID_AFTER_ADVICE = VoidMatching_Advices.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_VOID_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            VoidMatching_Advices.class.getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$VoidMatching_Objects") )
                    .methodMatcher(
                            named("matchVoid")
                                .and(isPrivate())
                                .and(returns(void.class)) )
                    .builder();
        }
    }


    @Test
    public void testPrimitiveMatching() {
        long expectReturning = 1l;
        PrimitiveMatching_Objects objects = new PrimitiveMatching_Objects();
        objects.matchPrimitive(expectReturning);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(PrimitiveMatching_Aspects.MATCH_PRIMITIVE_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(PrimitiveMatching_Aspects.MATCH_PRIMITIVE2_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(PrimitiveMatching_Advices.MATCH_PRIMITIVE_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }
    }

    private static class PrimitiveMatching_Objects {

        public long matchPrimitive(long input) {
            return input;
        }
    }

    @Aspect
    public static class PrimitiveMatching_Aspects {

        private static final String MATCH_PRIMITIVE_POINTCUT = 
                "execution(!private long io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$PrimitiveMatching_Objects.matchPrimitive(long))";

        private static final String MATCH_PRIMITIVE_AFTER_ADVICE = PrimitiveMatching_Aspects.class.getName() + ".matchPrimitive_afterAdvice";

        @After(MATCH_PRIMITIVE_POINTCUT)
        public void matchPrimitive_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_PRIMITIVE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }


        private static final String MATCH_PRIMITIVE2_POINTCUT = 
                "execution(!private java.lang.Long io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$PrimitiveMatching_Objects.matchPrimitive(java.lang.Long))";

        private static final String MATCH_PRIMITIVE2_AFTER_ADVICE = PrimitiveMatching_Aspects.class.getName() + ".matchPrimitive2_afterAdvice";

        @After(MATCH_PRIMITIVE2_POINTCUT)
        public void matchPrimitive2_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_PRIMITIVE2_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class PrimitiveMatching_Advices extends Advice.AbstractAfter<Long, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String MATCH_PRIMITIVE_AFTER_ADVICE = PrimitiveMatching_Advices.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_PRIMITIVE_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            this.getClass().getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$PrimitiveMatching_Objects") )
                    .methodMatcher(
                            named("matchPrimitive")
                                .and(isPublic())
                                .and(takesArgument(0, is(long.class)))
                                .and(returns(long.class)) )
                    .builder();
        }
    }


    @Test
    public void testGenericMatching() {
        long expectReturning = 1l;
        new GenericMatching_Objects<Long>().matchGeneric(expectReturning);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericMatching_Aspects.MATCH_GENERIC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericMatching_Advices.MATCH_GENERIC_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }
    }

    private static class GenericMatching_Objects<T extends Number & Comparable<T>> {

        public T matchGeneric(T input) {
            return input;
        }
    }

    @Aspect
    public static class GenericMatching_Aspects {

        private static final String MATCH_GENERIC_POINTCUT = 
                "execution(java.lang.Number io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$GenericMatching_Objects.matchGeneric(java.lang.Number))";

        private static final String MATCH_GENERIC_AFTER_ADVICE = GenericMatching_Aspects.class.getName() + ".matchGeneric_afterAdvice";

        @After(MATCH_GENERIC_POINTCUT)
        public void matchGeneric_afterAdvice(MutableJoinpoint<Number, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_GENERIC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class GenericMatching_Advices extends Advice.AbstractAfter<Number, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String MATCH_GENERIC_AFTER_ADVICE = GenericMatching_Advices.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Number, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_GENERIC_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            GenericMatching_Advices.class.getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$GenericMatching_Objects") )
                    .methodMatcher(
                            named("matchGeneric")
                                .and(isPublic())
                                .and(takesArgument(0, is(Number.class)))
                                .and(returns(Number.class)) )
                    .builder();
        }
    }


    @Test
    public void testGenericArrayMatching() {
        Long[] expectReturning = new Long[] {1l, 2l};
        new GenericArrayMatching_Objects<Long>().matchGenericArray(expectReturning);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericArrayMatching_Aspects.MATCH_GENERIC_ARRAY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericArrayMatching_Advices.MATCH_GENERIC_ARRAY_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }
    }

    private static class GenericArrayMatching_Objects<T extends Number> {

        public T[] matchGenericArray(T[] input) {
            return input;
        }
    }

    @Aspect
    public static class GenericArrayMatching_Aspects {

        private static final String MATCH_GENERIC_ARRAY_POINTCUT = 
                "execution(java.lang.Number[] io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$GenericArrayMatching_Objects.matchGenericArray(java.lang.Number[]))";

        private static final String MATCH_GENERIC_ARRAY_AFTER_ADVICE = GenericArrayMatching_Aspects.class.getName() + ".matchGenericArray_afterAdvice";

        @After(MATCH_GENERIC_ARRAY_POINTCUT)
        public void matchGenericArray_afterAdvice(MutableJoinpoint<Number[], RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_GENERIC_ARRAY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class GenericArrayMatching_Advices extends Advice.AbstractAfter<Number[], RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String MATCH_GENERIC_ARRAY_AFTER_ADVICE = GenericArrayMatching_Advices.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<Number[], RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_GENERIC_ARRAY_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            GenericArrayMatching_Advices.class.getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$GenericArrayMatching_Objects") )
                    .methodMatcher(
                            named("matchGenericArray")
                                .and(isPublic())
                                .and(takesArgument(0, is(Number[].class)))
                                .and(returns(Number[].class)) )
                    .builder();
        }
    }


    @Test
    public void testGenericCollectionMatching() {
        List<Long> expectReturning = Arrays.asList(1l, 2l);
        new GenericCollectionMatching_Objects<Long>().matchGenericCollection(expectReturning);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericCollectionMatching_Aspects.MATCH_GENERIC_COLLECTION_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericCollectionMatching_Advices.MATCH_GENERIC_COLLECTION_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }
    }

    private static class GenericCollectionMatching_Objects<T extends Number> {

        public List<T> matchGenericCollection(List<T> input) {
            return input;
        }
    }

    @Aspect
    public static class GenericCollectionMatching_Aspects {

        private static final String MATCH_GENERIC_COLLECTION_POINTCUT = 
                "execution(java.util.List io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$GenericCollectionMatching_Objects.matchGenericCollection(java.util.List))";

        private static final String MATCH_GENERIC_COLLECTION_AFTER_ADVICE = GenericCollectionMatching_Aspects.class.getName() + ".matchGenericCollection_afterAdvice";

        @SuppressWarnings("rawtypes")
        @After(MATCH_GENERIC_COLLECTION_POINTCUT)
        public void matchGenericCollection_afterAdvice(MutableJoinpoint<List, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_GENERIC_COLLECTION_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    @SuppressWarnings("rawtypes")
    public static class GenericCollectionMatching_Advices extends Advice.AbstractAfter<List, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String MATCH_GENERIC_COLLECTION_AFTER_ADVICE = GenericCollectionMatching_Advices.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<List, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_GENERIC_COLLECTION_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            GenericCollectionMatching_Advices.class.getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$GenericCollectionMatching_Objects") )
                    .methodMatcher(
                            named("matchGenericCollection")
                                .and(isPublic())
                                .and(takesArgument(0, is(List.class)))
                                .and(returns(List.class)) )
                    .builder();
        }
    }


    @Test
    public void testParameterizedCollectionMatching() {
        List<String> expectReturning = Arrays.asList("1", "2");
        new ParameterizedCollectionMatching_Objects().matchParameterizedCollection(expectReturning);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ParameterizedCollectionMatching_Aspects.MATCH_PARAMETERIZED_COLLECTION_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ParameterizedCollectionMatching_Advices.MATCH_PARAMETERIZED_COLLECTION_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }
    }

    private static class ParameterizedCollectionMatching_Objects {

        public List<String> matchParameterizedCollection(List<String> input) {
            return input;
        }
    }

    @Aspect
    public static class ParameterizedCollectionMatching_Aspects {

        private static final String MATCH_PARAMETERIZED_COLLECTION_POINTCUT = 
                "execution(java.util.List<java.lang.String> io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$ParameterizedCollectionMatching_Objects.matchParameterizedCollection(java.util.List<java.lang.String>))";

        private static final String MATCH_PARAMETERIZED_COLLECTION_AFTER_ADVICE = ParameterizedCollectionMatching_Aspects.class.getName() + ".matchParameterizedCollection_afterAdvice";

        @After(MATCH_PARAMETERIZED_COLLECTION_POINTCUT)
        public void matchParameterizedCollection_afterAdvice(MutableJoinpoint<List<String>, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_PARAMETERIZED_COLLECTION_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class ParameterizedCollectionMatching_Advices extends Advice.AbstractAfter<List<String>, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String MATCH_PARAMETERIZED_COLLECTION_AFTER_ADVICE = ParameterizedCollectionMatching_Advices.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<List<String>, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_PARAMETERIZED_COLLECTION_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            ParameterizedCollectionMatching_Advices.class.getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$ParameterizedCollectionMatching_Objects") )
                    .methodMatcher(
                            named("matchParameterizedCollection")
                                .and(isPublic())
                                .and(takesArgument(0, is(List.class)))
                                .and(returns(List.class)) )
                    .builder();
        }
    }


    @Test
    public void testWildCardCollectionMatching() {
        List<? extends Number> expectReturning = Arrays.asList(1l, 2l);
        new WildCardCollectionMatching_Objects().matchWildCardCollection(expectReturning);

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(WildCardCollectionMatching_Aspects.MATCH_WILD_CARD_COLLECTION_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(WildCardCollectionMatching_Advices.MATCH_WILD_CARD_COLLECTION_AFTER_ADVICE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(expectReturning);
        }
    }

    private static class WildCardCollectionMatching_Objects {

        public List<? extends Number> matchWildCardCollection(List<? extends Number> input) {
            return input;
        }
    }

    @Aspect
    public static class WildCardCollectionMatching_Aspects {

        private static final String MATCH_WILD_CARD_COLLECTION_POINTCUT = 
                "execution(java.util.List<? extends java.lang.Number> io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$WildCardCollectionMatching_Objects.matchWildCardCollection(java.util.List<? extends java.lang.Number>))";

        private static final String MATCH_WILD_CARD_COLLECTION_AFTER_ADVICE = WildCardCollectionMatching_Aspects.class.getName() + ".matchWildCardCollection_afterAdvice";

        @After(MATCH_WILD_CARD_COLLECTION_POINTCUT)
        public void matchWildCardCollection_afterAdvice(MutableJoinpoint<List<? extends Number>, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_WILD_CARD_COLLECTION_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }
    }

    public static class WildCardCollectionMatching_Advices extends Advice.AbstractAfter<List<? extends Number>, RuntimeException> 
            implements AspectSpec.PojoPointcutSpec.Factory {

        private static final String MATCH_WILD_CARD_COLLECTION_AFTER_ADVICE = WildCardCollectionMatching_Advices.class.getName() + ".after";

        /**
         * {@inheritDoc}
         */
        @Override
        public void after(MutableJoinpoint<List<? extends Number>, RuntimeException> joinpoint) throws Throwable {
            ExecutionMemento.putAdviceMethodInvoker(MATCH_WILD_CARD_COLLECTION_AFTER_ADVICE, 
                    new AdviceMethod()
                        .withInvoked(true)
                        .withReturning(joinpoint.getReturning()) );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PojoPointcutSpec getAspectSpec() {
            return new AspectSpec.PojoPointcutSpec.Builder()
                    .adviceClassName(
                            WildCardCollectionMatching_Advices.class.getName() )
                    .typeMatcher(
                            named("io.gemini.aop.integration.Pointcut_01JoinpointMatching_Tests$WildCardCollectionMatching_Objects") )
                    .methodMatcher(
                            named("matchWildCardCollection")
                                .and(isPublic())
                                .and(takesArgument(0, is(List.class)))
                                .and(returns(List.class)) )
                    .builder();
        }
    }
}