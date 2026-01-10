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

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;

import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.annotation.Advisor;
import io.gemini.api.aop.annotation.ConditionalOnClassLoader;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class Target_01TargetClass_Tests {

    private LoadedClass_Object loadedClass_Object = new LoadedClass_Object();

    @Test
    public void testLoadedClass() {
//        this.loadedClass_Object = new Load
    }

    public static class LoadedClass_Object {

        public void test() {}
    }


    @Test
    public void testJdkClass() {
//        String object = new String();
//        object.toString();
        List<String> list = new LinkedList<>();
        list.add("");
//        Executor executor = Executors.newCachedThreadPool();
//        executor.execute(() -> System.out.println());

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(JdkClass_Aspect.JDKCLASS_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getThisClass()).isEqualTo(Object.class);
        assertThat(beforeAdviceMethodInvoker.getStaticPart()).isEqualTo(JdkClass_Aspect.JDKCLASS_METHOD);
    }

    @Aspect
    public static class JdkClass_Aspect {

        private static final String JDKCLASS_POINTCUT = 
                "execution(public boolean java.util.LinkedList.add(java.lang.Object))";
//                "execution(public void java.util.concurrent.ThreadPoolExecutor.execute(java.lang.Runnable))";

        private static final String JDKCLASS_BEFORE_ADVICE = JdkClass_Aspect.class.getName() + ".jdkClass_before";

        private static final Method JDKCLASS_METHOD;

        static {
            Method method = null;
            try {
                method = ToStringBuilder.class.getMethod("toString");
            } catch (Exception e) {
                e.printStackTrace();
            }
            JDKCLASS_METHOD = method;
        }


        @SuppressWarnings("rawtypes")
        @Before(JDKCLASS_POINTCUT)
        @Advisor(inheritClassLoaderMatcher = false, inheritTypeMatcher = false, perInstance = false)
        @ConditionalOnClassLoader(isBootstrapClassLoader = true)
        public void jdkClass_before(MutableJoinpoint joinpoint) {
//            ExecutionMemento.putAdviceMethodInvoker(JDKCLASS_BEFORE_ADVICE, 
//                    new AdviceMethod()
//                        .withInvoked(true)
//                        .withThisLookup(joinpoint.getThisLookup())
//                        .withThisClass(joinpoint.getThisClass()) 
//                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }


    @Test
    public void testByteCode1x() {
        ToStringBuilder object = new ToStringBuilder(new Object());
        object.append("name", "test");

        AdviceMethod beforeAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(ByteCode1x_Aspect.BYTECODE_1X_BEFORE_ADVICE);
        assertThat(beforeAdviceMethodInvoker).isNotNull();
        assertThat(beforeAdviceMethodInvoker.isInvoked()).isTrue();

        assertThat(beforeAdviceMethodInvoker.getThisLookup()).isNotNull();
        assertThat(beforeAdviceMethodInvoker.getThisLookup().lookupModes() & Lookup.PRIVATE).isNotEqualTo(0);

        assertThat(beforeAdviceMethodInvoker.getThisClass()).isEqualTo(ToStringBuilder.class);
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
                        .withThisLookup(joinpoint.getThisLookup())
                        .withThisClass(joinpoint.getThisClass()) 
                        .withStaticPart(joinpoint.getStaticPart()) );
        }
    }
}
