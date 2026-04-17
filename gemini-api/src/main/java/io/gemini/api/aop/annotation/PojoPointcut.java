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
package io.gemini.api.aop.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.gemini.api.aop.Pointcut;


/**
 * Associates a POJO-style {@link io.gemini.api.aop.Pointcut} implementation with an advice class.
 * <p>
 * The referenced {@code pointcutClass} must implement {@link io.gemini.api.aop.Pointcut} and
 * provide ByteBuddy {@link net.bytebuddy.matcher.ElementMatcher} instances for type and method matching.
 * </p>
 *
 * <pre>{@code
 * @PojoPointcut(pointcutClass = MyPointcut.class)
 * public class MyAdvice extends Advice.AbstractBeforeAfter<Object, RuntimeException> { ... }
 * }</pre>
 *
 * @author   martin.liu
 */
@Target( {ElementType.TYPE} )
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PojoPointcut {

    /**
     * The {@link io.gemini.api.aop.Pointcut} implementation class that defines type and method matchers.
     *
     * @return the pointcut class
     */
    Class<? extends Pointcut> pointcutClass();

}
