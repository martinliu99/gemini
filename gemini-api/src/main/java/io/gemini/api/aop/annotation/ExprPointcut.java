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


/**
 * Declares an AspectJ-style pointcut expression for an advice class.
 * <p>
 * When placed on an {@link io.gemini.api.aop.Advice} implementation, the Gemini AOP framework 
 * will parse the {@link #pointcutExpression()} using AspectJ syntax to determine 
 * which joinpoints the advice applies to.
 * </p>
 *
 * <pre>{@literal
 * @}ExprPointcut(pointcutExpression = "execution(* com.example.service.*.*(..))")
 * public class MyAdvice extends Advice.AbstractBeforeAfter&lt;Object, RuntimeException&gt; { ... }
 * </pre>
 *
 * @author   martin.liu
 */
@Target( {ElementType.TYPE} )
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExprPointcut {

    /**
     * The AspectJ pointcut expression that defines where this advice applies.
     *
     * @return the pointcut expression, e.g. {@code "execution(* com.example..*(..))"} 
     */
    String pointcutExpression() default "";

}
