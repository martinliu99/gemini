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
 * Selects the appropriate ByteBuddy {@link ClassLoadingStrategy} based on JVM capabilities.
 * <p>
 * Uses {@link ClassInjector.UsingLookup} when available (JDK 9+), falling back to
 * {@link ClassLoadingStrategy.ForUnsafeInjection} on older JVMs.
 * </p>
 *
 * @author   martin.liu
 */
public interface ClassLoadingStrategySelector {

    /**
     * Selects the appropriate {@link ClassLoadingStrategy} for the given scope type.
     *
     * @param scopeType the type whose class loader and module are used for injection
     * @return the selected class loading strategy
     */
    ClassLoadingStrategy<? super ClassLoader> select(Class<?> scopeType);


    /**
     * Default singleton implementation of {@link ClassLoadingStrategySelector}.
     */
    enum Default implements ClassLoadingStrategySelector {

        SINGLETON;


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


        /**
         * A {@link ClassLoadingStrategy.UsingLookup} subclass used when
         * {@link ClassInjector.UsingLookup} is available (JDK 9+).
         */
        static class WithLookup extends ClassLoadingStrategy.UsingLookup {

            /**
             * Constructs a {@code WithLookup} strategy using the given class injector.
             *
             * @param classInjector the lookup-based class injector
             */
            protected WithLookup(ClassInjector classInjector) {
                super(classInjector);
            }
        }
    }
}
