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
/**
 * 
 */
package io.gemini.api.aop.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.gemini.api.annotation.Order;
import io.gemini.api.aop.condition.OnClassLoaderCondition;


/**
 * Activates an advisor only when the target class loader matches the specified criteria.
 * <p>
 * Useful for restricting advice to specific class loader scopes, such as the bootstrap
 * class loader (for JDK core class instrumentation) or a named custom class loader.
 * </p>
 *
 * @author   martin.liu
 */
@Target( {ElementType.TYPE, ElementType.METHOD} )
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassLoaderCondition.class)
@Order(0)
public @interface ConditionalOnClassLoader {

    /** 
     * Expression matching the class loader class name. 
     */
    String classLoaderExpression() default "";

    /** 
     * If {@code true}, activates only for the bootstrap class loader.
     */
    boolean isBootstrapClassLoader() default false;

    /** 
     * If {@code true}, activates only for the ext/platform class loader. 
     */
    boolean isExtClassLoader() default false;

    /** 
     * If {@code true}, activates only for the application class loader. 
     */
    boolean isAppClassLoader() default false;
}
