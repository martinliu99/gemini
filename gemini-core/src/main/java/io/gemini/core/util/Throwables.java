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
/**
 * 
 */
package io.gemini.core.util;

import io.gemini.api.aop.AopException.WrappedException;

/**
 * 
 */
public abstract class Throwables {

    public static void throwIfRequired(Throwable t) {
        if (t instanceof WrappedException)
            throw (WrappedException) t;

        throwIfJvmFatal(t);
    }

    /**
     * Throws a particular {@code Throwable} only if it belongs to a set of "fatal" error
     * varieties native to the JVM. These varieties are as follows:
     * <ul> <li>{@link VirtualMachineError}</li> <li>{@link ThreadDeath}</li>
     *
     * @param t the exception to evaluate
     */
    private static void throwIfJvmFatal(Throwable t) {
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }

        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }

        // ignore LinkageError since advisor class might conflict with joinpoint class
//        if (t instanceof LinkageError) {
//            throw (LinkageError) t;
//        }
    }


    public static void propagate(Throwable t) {
        throwIfRequired(t);

        if (t instanceof RuntimeException)
            throw (RuntimeException) t;

        throw new WrappedException(t);
    }

    public static Throwable unwrap(Throwable t) {
        Throwable cause = t;
        while (cause instanceof WrappedException)
            cause = cause.getCause();
        return cause != null ? cause : t;
    }

}
