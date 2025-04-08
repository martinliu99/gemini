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
import java.util.Arrays;
import java.util.List;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;

import io.gemini.aop.aspect.Joinpoint.MutableJoinpoint;
import io.gemini.aop.test.AbstractBaseTests;
import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class MutableJoinpoint_21ParametrizedReturningType_Tests extends AbstractBaseTests {

    @Test
    public void testVoidReturning() {
        new VoidReturning_Objects().returnVoid();

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(VoidReturning_Aspects.RETURN_VOID_RAW_TYPE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(beforeAdviceMethodInvoker.getReturning()).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(VoidReturning_Aspects.RETURN_VOID_SAME_TYPE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isNull();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(VoidReturning_Aspects.RETURN_VOID_WRONG_TYPE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    static class VoidReturning_Objects {

        void returnVoid() {
            return;
        }

    }

    @Aspect
    public static class VoidReturning_Aspects {

        private static final String RETURN_VOID_POINTCUT = 
                "execution(void io.gemini.aop.aspect.MutableJoinpoint_21ParametrizedReturningType_Tests$VoidReturning_Objects.returnVoid())";

        private static final String RETURN_VOID_RAW_TYPE = "returnVoid_rawType";
        private static final String RETURN_VOID_SAME_TYPE = "returnVoid_sameType";
        private static final String RETURN_VOID_WRONG_TYPE = "returnVoid_primitiveType";

        @SuppressWarnings("rawtypes")
        @Before(RETURN_VOID_POINTCUT)
        public void returnVoid_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_VOID_RAW_TYPE, 
                    new AdviceMethod().withInvoked(true)  );
        }

        @After(RETURN_VOID_POINTCUT)
        public void returnVoid_sameType(MutableJoinpoint<Void, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_VOID_SAME_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }

        @After(RETURN_VOID_POINTCUT)
        public void returnVoid_wrongType(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_VOID_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testPrimitiveReturning() {
        long actualReturning = new PrimitiveReturning_Objects(1l).returnPrimitive();

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(PrimitiveReturning_Aspects.RETURN_PRIMITIVE_RAW_TYPE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(PrimitiveReturning_Aspects.RETURN_PRIMITIVE_SAME_TYPE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(actualReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(PrimitiveReturning_Aspects.RETURN_PRIMITIVE_WRONG_TYPE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    static class PrimitiveReturning_Objects {

        private long counter;


        public PrimitiveReturning_Objects(long counter) {
            this.counter = counter;
        }

        long returnPrimitive() {
            return this.counter;
        }

    }

    @Aspect
    public static class PrimitiveReturning_Aspects {

        private static final String RETURN_PRIMITIVE_POINTCUT = 
                "execution(long io.gemini.aop.aspect.MutableJoinpoint_21ParametrizedReturningType_Tests$PrimitiveReturning_Objects.returnPrimitive())";

        private static final String RETURN_PRIMITIVE_RAW_TYPE = "returnPrimitive_rawType";
        private static final String RETURN_PRIMITIVE_SAME_TYPE = "returnPrimitive_sameType";
        private static final String RETURN_PRIMITIVE_WRONG_TYPE = "returnPrimitive_wrongType";

        @SuppressWarnings("rawtypes")
        @Before(RETURN_PRIMITIVE_POINTCUT)
        public void returnPrimitive_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_PRIMITIVE_RAW_TYPE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(RETURN_PRIMITIVE_POINTCUT)
        public void returnPrimitive_sameType(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            long updatedReturning = -1 * joinpoint.getReturning();
            joinpoint.setAdviceReturning(updatedReturning);

            ExecutionMemento.putAdviceMethodInvoker(RETURN_PRIMITIVE_SAME_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(updatedReturning) );
        }

        @After(RETURN_PRIMITIVE_POINTCUT)
        public void returnPrimitive_wrongType(MutableJoinpoint<Object, Throwable> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_PRIMITIVE_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testGenericReturning() {
        Long actualReturning = new GenericReturning_Objects<Long>(1l).returnGeneric();

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericReturning_Aspects.RETURN_GENERIC_RAW_TYPE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericReturning_Aspects.RETURN_GENERIC_SAME_TYPE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(actualReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericReturning_Aspects.RETURN_GENERIC_WRONG_TYPE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    static class GenericReturning_Objects<T extends Number> {

        T counter;


        public GenericReturning_Objects(T counter) {
            this.counter = counter;
        }

        T returnGeneric() {
            return counter;
        }
    }

    @Aspect
    public static class GenericReturning_Aspects {

        private static final String RETURN_GENERIC_POINTCUT = 
                "execution(java.lang.Number io.gemini.aop.aspect.MutableJoinpoint_21ParametrizedReturningType_Tests$GenericReturning_Objects.returnGeneric())";

        private static final String RETURN_GENERIC_RAW_TYPE = "returnGeneric_rawType";
        private static final String RETURN_GENERIC_SAME_TYPE = "returnGeneric_genericType";
        private static final String RETURN_GENERIC_WRONG_TYPE = "returnGeneric_wrongType";

        @SuppressWarnings("rawtypes")
        @Before(RETURN_GENERIC_POINTCUT)
        public void returnGeneric_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_GENERIC_RAW_TYPE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(RETURN_GENERIC_POINTCUT)
        public void returnGeneric_genericType(MutableJoinpoint<Number, RuntimeException> joinpoint) {
            Number returning = joinpoint.getReturning();
            if(returning != null && returning instanceof Long) {
                Long updatedReturning = -1 * (Long) returning;
                joinpoint.setAdviceReturning(updatedReturning);

                ExecutionMemento.putAdviceMethodInvoker(RETURN_GENERIC_SAME_TYPE, 
                        new AdviceMethod().withInvoked(true).withReturning(updatedReturning) );
            }
        }

        @After(RETURN_GENERIC_POINTCUT)
        public void returnGeneric_wrongType(MutableJoinpoint<Integer, Throwable> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_GENERIC_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testGenericArrayReturning() {
        Long[] actualReturning = new GenericArrayReturning_Objects<Long>(new Long[] {1l, 2l})
                .returnGenericArray();

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericArrayReturning_Aspects.RETURN_GENERIC_ARRAY_RAW_TYPE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericArrayReturning_Aspects.RETURN_GENERIC_ARRAY_SAME_TYPE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(actualReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericArrayReturning_Aspects.RETURN_GENERIC_ARRAY_WRONG_TYPE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    static class GenericArrayReturning_Objects<T extends Number> {

        T[] counters;


        public GenericArrayReturning_Objects(T[] counters) {
            this.counters = counters;
        }

        T[] returnGenericArray() {
            return counters;
        }
    }

    @Aspect
    public static class GenericArrayReturning_Aspects {

        private static final String RETURN_GENERIC_ARRAY_POINTCUT = 
                "execution(java.lang.Number[] io.gemini.aop.aspect.MutableJoinpoint_21ParametrizedReturningType_Tests$GenericArrayReturning_Objects.returnGenericArray())";

        private static final String RETURN_GENERIC_ARRAY_RAW_TYPE = "returnGenericArray_rawType";
        private static final String RETURN_GENERIC_ARRAY_SAME_TYPE = "returnGenericArray_genericType";
        private static final String RETURN_GENERIC_ARRAY_WRONG_TYPE = "returnGenericArray_wrongType";

        @SuppressWarnings("rawtypes")
        @Before(RETURN_GENERIC_ARRAY_POINTCUT)
        public void returnGenericArray_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_GENERIC_ARRAY_RAW_TYPE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(RETURN_GENERIC_ARRAY_POINTCUT)
        public void returnGenericArray_genericType(MutableJoinpoint<Number[], RuntimeException> joinpoint) {
            Number[] returning = joinpoint.getReturning();
            if(returning != null && returning instanceof Long[]) {
                List<Long> updatedReturning = new ArrayList<>();
                for(Long l : (Long[]) returning) 
                    updatedReturning.add(l);

                updatedReturning.addAll(Arrays.asList(-1l, -2l));

                Long[] _updatedReturning = updatedReturning.toArray(new Long[] {});
                joinpoint.setAdviceReturning(_updatedReturning);

                ExecutionMemento.putAdviceMethodInvoker(RETURN_GENERIC_ARRAY_SAME_TYPE, 
                        new AdviceMethod().withInvoked(true).withReturning(_updatedReturning) );
            }
        }

        @After(RETURN_GENERIC_ARRAY_POINTCUT)
        public void returnGenericArray_wrongType(MutableJoinpoint<Integer[], Throwable> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_GENERIC_ARRAY_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testGenericCollectionReturning() {
        List<Long> actualReturning = new GenericCollectionReturning_Objects<Long>(Arrays.asList(1l, 2l, 3l))
                .returnGenericCollection();

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericCollectionReturning_Aspects.RETURN_GENERIC_COLLECTION_RAW_TYPE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericCollectionReturning_Aspects.RETURN_GENERIC_COLLECTION_SAME_TYPE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(actualReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(GenericCollectionReturning_Aspects.RETURN_GENERIC_COLLECTION_WRONG_TYPE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    static class GenericCollectionReturning_Objects<T extends Number> {

        List<T> counters;


        public GenericCollectionReturning_Objects(List<T> counters) {
            this.counters = counters;
        }

        List<T> returnGenericCollection() {
            return counters;
        }
    }

    @Aspect
    public static class GenericCollectionReturning_Aspects {

        private static final String RETURN_GENERIC_COLLECTION_POINTCUT = 
                "execution(java.util.List io.gemini.aop.aspect.MutableJoinpoint_21ParametrizedReturningType_Tests$GenericCollectionReturning_Objects.returnGenericCollection())";

        private static final String RETURN_GENERIC_COLLECTION_SAME_TYPE = "returnGenericCollection_genericType";
        private static final String RETURN_GENERIC_COLLECTION_RAW_TYPE = "returnGenericCollection_rawType";
        private static final String RETURN_GENERIC_COLLECTION_WRONG_TYPE = "returnGenericCollection_wrongType";

        @SuppressWarnings("rawtypes")
        @Before(RETURN_GENERIC_COLLECTION_POINTCUT)
        public void returnGenericCollection_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_GENERIC_COLLECTION_RAW_TYPE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @After(RETURN_GENERIC_COLLECTION_POINTCUT)
        public void returnGenericCollection_genericType(MutableJoinpoint<List, RuntimeException> joinpoint) {
            List<Long> updatedReturning = new ArrayList<>(joinpoint.getReturning());
            updatedReturning.addAll( Arrays.asList(-1l, -2l) );

            joinpoint.setAdviceReturning(updatedReturning);

            ExecutionMemento.putAdviceMethodInvoker(RETURN_GENERIC_COLLECTION_SAME_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(updatedReturning) );
        }

        @After(RETURN_GENERIC_COLLECTION_POINTCUT)
        public void returnGenericCollection_wrongType(MutableJoinpoint<Integer, Throwable> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_GENERIC_COLLECTION_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testParameterizedCollectionReturning() {
        List<String> actualReturning = new ParameterizedCollectionReturning_Objects(Arrays.asList("1", "2", "3"))
                .returnParameterizedCollection();

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ParameterizedCollectionReturning_Aspects.RETURN_PARAMETERIZED_COLLECTION_RAW_TYPE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ParameterizedCollectionReturning_Aspects.RETURN_PARAMETERIZED_COLLECTION_SAME_TYPE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(actualReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ParameterizedCollectionReturning_Aspects.RETURN_PARAMETERIZED_COLLECTION_WRONG_TYPE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    static class ParameterizedCollectionReturning_Objects {

        private List<String> counters;


        public ParameterizedCollectionReturning_Objects(List<String> counters) {
            this.counters = counters;
        }

        List<String> returnParameterizedCollection() {
            return counters;
        }
    }

    @Aspect
    public static class ParameterizedCollectionReturning_Aspects {

        private static final String RETURN_PARAMETERIZED_COLLECTION_POINTCUT = 
                "execution(java.util.List<java.lang.String> io.gemini.aop.aspect.MutableJoinpoint_21ParametrizedReturningType_Tests$ParameterizedCollectionReturning_Objects.returnParameterizedCollection())";

        private static final String RETURN_PARAMETERIZED_COLLECTION_SAME_TYPE = "returnParameterizedCollection_genericType";
        private static final String RETURN_PARAMETERIZED_COLLECTION_RAW_TYPE = "returnParameterizedCollection_rawType";
        private static final String RETURN_PARAMETERIZED_COLLECTION_WRONG_TYPE = "returnParameterizedCollection_wrongType";

        @SuppressWarnings("rawtypes")
        @Before(RETURN_PARAMETERIZED_COLLECTION_POINTCUT)
        public void returnParameterizedCollection_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_PARAMETERIZED_COLLECTION_RAW_TYPE,
                    new AdviceMethod().withInvoked(true) );
        }

        @After(RETURN_PARAMETERIZED_COLLECTION_POINTCUT)
        public void returnParameterizedCollection_genericType(MutableJoinpoint<List<String>, RuntimeException> joinpoint) {
            List<String> updatedReturning = new ArrayList<String>(joinpoint.getReturning());
            updatedReturning.addAll( Arrays.asList("-1", "-2") );

            joinpoint.setAdviceReturning(updatedReturning);

            ExecutionMemento.putAdviceMethodInvoker(RETURN_PARAMETERIZED_COLLECTION_SAME_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(updatedReturning) );
        }

        @After(RETURN_PARAMETERIZED_COLLECTION_POINTCUT)
        public void returnParameterizedCollection_wrongType(MutableJoinpoint<Integer, Throwable> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_PARAMETERIZED_COLLECTION_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }


    @Test
    public void testWildCardCollectionReturning() {
        List<? extends Number> actualReturning = new WildCardCollectionReturning_Objects(Arrays.asList(1, 2, 3))
                .returnWildCardCollection();

        {
            AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(WildCardCollectionReturning_Aspects.RETURN_WILD_CARD_COLLECTION_RAW_TYPE);
            assertThat(beforeAdviceMethodInvoker).isNotNull();
            assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(WildCardCollectionReturning_Aspects.RETURN_WILD_CARD_COLLECTION_SAME_TYPE);
            assertThat(afterAdviceMethodInvoker).isNotNull();
            assertThat(afterAdviceMethodInvoker.isInvoked()).isTrue();
            assertThat(afterAdviceMethodInvoker.getReturning()).isEqualTo(actualReturning);
        }

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(WildCardCollectionReturning_Aspects.RETURN_WILD_CARD_COLLECTION_WRONG_TYPE);
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

    static class WildCardCollectionReturning_Objects {

        private List<? extends Number> counters;


        public WildCardCollectionReturning_Objects(List<? extends Number> counters) {
            this.counters = counters;
        }

        List<? extends Number> returnWildCardCollection() {
            return counters;
        }
    }

    @Aspect
    public static class WildCardCollectionReturning_Aspects {

        private static final String RETURN_WILD_CARD_COLLECTION_POINTCUT = 
                "execution(java.util.List<? extends java.lang.Number> io.gemini.aop.aspect.MutableJoinpoint_21ParametrizedReturningType_Tests$WildCardCollectionReturning_Objects.returnWildCardCollection())";

        private static final String RETURN_WILD_CARD_COLLECTION_SAME_TYPE = "returnWildCardCollection_genericType";
        private static final String RETURN_WILD_CARD_COLLECTION_RAW_TYPE = "returnWildCardCollection_rawType";
        private static final String RETURN_WILD_CARD_COLLECTION_WRONG_TYPE = "returnWildCardCollection_wrongType";

        @SuppressWarnings("rawtypes")
        @Before(RETURN_WILD_CARD_COLLECTION_POINTCUT)
        public void returnWildCardCollection_rawType(MutableJoinpoint joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_WILD_CARD_COLLECTION_RAW_TYPE, 
                    new AdviceMethod().withInvoked(true) );
        }

        @After(RETURN_WILD_CARD_COLLECTION_POINTCUT)
        public void returnWildCardCollection_genericType(MutableJoinpoint<List<? extends java.lang.Number>, RuntimeException> joinpoint) {
            List<Number> updatedReturning = new ArrayList<>(joinpoint.getReturning());
            updatedReturning.addAll( Arrays.asList(-1l, -2l) );
            joinpoint.setAdviceReturning(updatedReturning);

            ExecutionMemento.putAdviceMethodInvoker(RETURN_WILD_CARD_COLLECTION_SAME_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(updatedReturning) );
        }

        @After(RETURN_WILD_CARD_COLLECTION_POINTCUT)
        public void returnWildCardCollection_wrongType(MutableJoinpoint<Integer, Throwable> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(RETURN_WILD_CARD_COLLECTION_WRONG_TYPE, 
                    new AdviceMethod().withInvoked(true).withReturning(joinpoint.getReturning()) );
        }
    }
}
