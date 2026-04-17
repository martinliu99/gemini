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
package io.gemini.core.classloader;


/**
 * Thread-local holder for the current context class loader and aspect type name.
 * <p>
 * Used by the Gemini AOP framework to propagate the target class loader and the
 * currently-processing aspect type across method call boundaries without passing
 * them as explicit parameters.
 * </p>
 *
 * @author   martin.liu
 */
public class ThreadContext {

    // store contextClassLoader per thread
    private final static ThreadLocal<ClassLoader> CONTEXT_CLASS_LOADER = new ThreadLocal<>();

    private final static ThreadLocal<String> CONTEXT_ASPECT_TYPE = new ThreadLocal<>();


    /**
     * Returns the context class loader stored for the current thread, or {@code null} if none is set.
     *
     * @return the context class loader, or {@code null}
     */
    public static ClassLoader getContextClassLoader() {
        return CONTEXT_CLASS_LOADER.get();
    }

    /**
     * Sets the context class loader for the current thread.
     *
     * @param contextClassLoader the class loader to store
     */
    public static void setContextClassLoader(ClassLoader contextClassLoader) {
        CONTEXT_CLASS_LOADER.set(contextClassLoader);
    }

    /**
     * Removes the context class loader stored for the current thread.
     */
    public static void removeContextClassLoader() {
        CONTEXT_CLASS_LOADER.remove();
    }


    /**
     * Returns the context aspect type name stored for the current thread, or {@code null} if none is set.
     *
     * @return the aspect type name, or {@code null}
     */
    public static String getContextAspectType() {
        return CONTEXT_ASPECT_TYPE.get();
    }

    /**
     * Sets the context aspect type name for the current thread.
     *
     * @param contextAspectType the fully-qualified aspect type name to store
     */
    public static void setContextAspectType(String contextAspectType) {
        CONTEXT_ASPECT_TYPE.set(contextAspectType);
    }

    /**
     * Removes the context aspect type name stored for the current thread.
     */
    public static void removeContextAspectType() {
        CONTEXT_ASPECT_TYPE.remove();
    }

}