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
package io.gemini.api.aop;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Defines the pointcut contract used by POJO-style advisors.
 * <p>
 * A pointcut specifies which types and methods should be intercepted using ByteBuddy
 * {@link ElementMatcher} instances. Use {@link io.gemini.api.aop.annotation.PojoPointcut}
 * to associate a pointcut class with an advice class.
 * </p>
 *
 * @author   martin.liu
 */
public interface Pointcut {

    /**
     * Returns the matcher that selects target types to instrument.
     *
     * @return a ByteBuddy {@link ElementMatcher} for type descriptions
     */
    ElementMatcher<TypeDescription> getTypeMatcher();

    /**
     * Returns the matcher that selects target methods within matched types.
     *
     * @return a ByteBuddy {@link ElementMatcher} for method descriptions
     */
    ElementMatcher<MethodDescription> getMethodMatcher();


    /**
     * Default implementation of {@link Pointcut} that holds a type matcher and a method matcher
     * supplied at construction time.
     */
    class Default implements Pointcut {

        private final ElementMatcher<TypeDescription> typeMatcher;
        private final ElementMatcher<MethodDescription> methodMatcher;


        /**
         * Creates a new {@code Default} pointcut with the given matchers.
         *
         * @param typeMatcher   the matcher used to select target types
         * @param methodMatcher the matcher used to select target methods within matched types
         */
        public Default(
                ElementMatcher<TypeDescription> typeMatcher,
                ElementMatcher<MethodDescription> methodMatcher) {
            this.typeMatcher = typeMatcher;
            this.methodMatcher = methodMatcher;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<TypeDescription> getTypeMatcher() {
            return typeMatcher;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ElementMatcher<MethodDescription> getMethodMatcher() {
            return methodMatcher;
        }
    }
}
