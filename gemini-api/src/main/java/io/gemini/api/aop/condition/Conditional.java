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
package io.gemini.api.aop.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 * 
 * @author   martin.liu
 * @since    1.0
 */
@Target( {ElementType.TYPE, ElementType.METHOD} )
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Conditional {

    Class<? extends ElementMatcher<ConditionContext>>[] value() default {};

    String[] acceptableClassLoaderExpressions() default {};

    String[] requiredTypeExpressions() default {};

    String[] requiredFieldExpressions() default {};

    String[] requiredConstructorExpressions() default {};

    String[] requiredMethodExpressions() default {};

}
