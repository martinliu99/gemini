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
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;

import io.gemini.aop.test.AbstractIntegrationTests;
import io.gemini.aop.test.ExecutionMemento;
import io.gemini.aop.test.ExecutionMemento.AdviceMethod;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;


public class Aspect_22AspectAccessibility_Tests extends AbstractIntegrationTests {

    @Test
    public void testAspectSpecAccessibility() {
        AspectSpecAccessibility_Objects objects = new AspectSpecAccessibility_Objects();
        objects.accessAspectSpec();

        {
            AdviceMethod afterAdviceMethodInvoker = ExecutionMemento.getAdviceMethodInvoker(AspectSpecAccessibility_Aspects.class.getName());
            assertThat(afterAdviceMethodInvoker).isNull();
        }
    }

     static class AspectSpecAccessibility_Objects {

        public long accessAspectSpec() {
            return 1l;
        }
    }

    @NoScanning
    @Aspect
    static abstract class AspectSpecAccessibility_Aspects {

        private static final String ACCESS_ASPECT_SPEC_POINTCUT = 
                "execution(public long io.gemini.aop.integration.Aspect_22AspectAccessibility_Tests$AspectSpecAccessibility_Objects.accessAspectSpec())";

        @After(value = ACCESS_ASPECT_SPEC_POINTCUT)
        public void accessAspectSpec_afterAdvice(MutableJoinpoint<Long, RuntimeException> joinpoint) {
            ExecutionMemento.putAdviceMethodInvoker(getClass().getName(), 
                    new AdviceMethod()
                        .withInvoked(true)
             );
        }
    }
}