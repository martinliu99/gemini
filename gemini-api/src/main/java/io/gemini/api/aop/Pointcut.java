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
package io.gemini.api.aop;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public interface Pointcut {

    ElementMatcher<String> getClassLoaderMatcher();

    ElementMatcher<TypeDescription> getTypeMatcher();

    ElementMatcher<MethodDescription> getMethodMatcher();


    abstract class AbstractBase implements Pointcut {

        @Override
        public ElementMatcher<String> getClassLoaderMatcher() {
            return null;
        }

    }

    class Default extends AbstractBase {

        private final ElementMatcher<String> classLoaderMatcher;
        private final ElementMatcher<TypeDescription> typeMatcher;
        private final ElementMatcher<MethodDescription> methodMatcher;


        public Default(
                ElementMatcher<TypeDescription> typeMatcher,
                ElementMatcher<MethodDescription> methodMatcher) {
            this(null, typeMatcher, methodMatcher);
        }

        public Default(ElementMatcher<String> classLoaderMatcher,
                ElementMatcher<TypeDescription> typeMatcher,
                ElementMatcher<MethodDescription> methodMatcher) {
            this.classLoaderMatcher = classLoaderMatcher;
            this.typeMatcher = typeMatcher;
            this.methodMatcher = methodMatcher;
        }

        @Override
        public ElementMatcher<String> getClassLoaderMatcher() {
            return classLoaderMatcher;
        }

        @Override
        public ElementMatcher<TypeDescription> getTypeMatcher() {
            return typeMatcher;
        }

        @Override
        public ElementMatcher<MethodDescription> getMethodMatcher() {
            return methodMatcher;
        }
    }
}
