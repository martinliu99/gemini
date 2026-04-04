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
package io.gemini.core.loading;

import java.lang.invoke.MethodHandles;

import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy.ForUnsafeInjection;

/**
 * 
 */
public interface ClassLoadingStrategySelector {

    ClassLoadingStrategy<? super ClassLoader> select(Class<?> scopeType);


    enum Default implements ClassLoadingStrategySelector {

        SINGLETON;


        /**
         * 
         */
        private static final ForUnsafeInjection FOR_UNSAFE_INJECTION = new ClassLoadingStrategy.ForUnsafeInjection();


        /**
         * {@inheritDoc}
         */
        @Override
        public ClassLoadingStrategy<? super ClassLoader> select(Class<?> scopeType) {
            if (ClassInjector.UsingLookup.isAvailable() == false)
                return FOR_UNSAFE_INJECTION;

            return new WithLookup(
                    ClassInjector.UsingLookup.of(MethodHandles.lookup()).in(scopeType)
            );
        }


        static class WithLookup extends ClassLoadingStrategy.UsingLookup {

            /**
             * @param classInjector
             */
            protected WithLookup(ClassInjector classInjector) {
                super(classInjector);
            }
        }
    }
}
