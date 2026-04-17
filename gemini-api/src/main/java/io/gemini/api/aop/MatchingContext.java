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

import net.bytebuddy.pool.TypePool;


/**
 * Provides contextual information about the target class loader and its type landscape
 * during advisor condition evaluation.
 * <p>
 * Used by {@link io.gemini.api.aop.annotation.Conditional} implementations to check
 * whether specific types, fields, constructors, or methods are present in the target
 * class loader's classpath or specified implementation are met.
 * 
 * </p>
 *
 * @author   martin.liu
 */
public interface MatchingContext {

    /**
     * Returns the {@link TypePool} associated with the target class loader, allowing
     * type resolution without actually loading classes.
     *
     * @return the TypePool of the target class loader
     */
    TypePool getTargetTypePool();


    /**
     * Returns whether the target class loader is the bootstrap class loader.
     *
     * @return {@code true} if the target class loader is the bootstrap class loader
     */
    boolean isBootstrapClassLoader();

    /**
     * Returns whether the target class loader is the extension (ext) class loader.
     *
     * @return {@code true} if the target class loader is the ext class loader
     */
    boolean isExtClassLoader();

    /**
     * Returns whether the target class loader is the application (app) class loader.
     *
     * @return {@code true} if the target class loader is the app class loader
     */
    boolean isAppClassLoader();

    /**
     * Returns whether the target class loader matches the given class loader expression.
     *
     * @param classLoaderExpression a class loader matching expression (e.g. a class name or wildcard pattern)
     * @return {@code true} if the target class loader matches the expression
     */
    boolean isClassLoader(String classLoaderExpression);


    /**
     * Returns whether the given type is present in the target class loader's classpath.
     *
     * @param typeExpression a type expression (e.g. a fully qualified class name)
     * @return {@code true} if the type exists
     */
    boolean hasType(String typeExpression);

    /**
     * Returns whether the given field is present in the target type.
     *
     * @param fieldExpression a field expression (e.g. {@code com.example.Foo#bar})
     * @return {@code true} if the field exists
     */
    boolean hasFiled(String fieldExpression);

    /**
     * Returns whether the given constructor is present in the target type.
     *
     * @param constructorExpression a constructor expression (e.g. {@code com.example.Foo(int, String)})
     * @return {@code true} if the constructor exists
     */
    boolean hasConstructor(String constructorExpression);

    /**
     * Returns whether the given method is present in the target type.
     *
     * @param methodsExpression a method expression (e.g. {@code com.example.Foo#bar(int)})
     * @return {@code true} if the method exists
     */
    boolean hasMethod(String methodsExpression);

}