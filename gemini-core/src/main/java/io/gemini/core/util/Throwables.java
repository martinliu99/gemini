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
package io.gemini.core.util;

import io.gemini.api.aop.AopException.WrappedException;

/**
 * Utility class for safe exception propagation in the AOP framework.
 * <p>
 * Provides helpers to:
 * <ul>
 *   <li>Re-throw JVM-fatal errors ({@link VirtualMachineError}, {@link ThreadDeath})</li>
 *   <li>Re-throw {@link io.gemini.api.aop.AopException.WrappedException} from advice chains</li>
 *   <li>Wrap checked exceptions in {@code WrappedException} for propagation</li>
 *   <li>Unwrap nested {@code WrappedException} to recover the original cause</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public abstract class Throwables {

    /**
     * Re-throws the given throwable if it is a {@link io.gemini.api.aop.AopException.WrappedException}
     * or a JVM-fatal error. Otherwise, the throwable is silently swallowed.
     *
     * @param t the throwable to evaluate
     */
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

        // ignore LinkageError since advisor class might conflict with target class
//        if (t instanceof LinkageError) {
//            throw (LinkageError) t;
//        }
    }


    /**
     * Propagates the given throwable: re-throws it if it is a {@link RuntimeException},
     * or wraps it in a {@link io.gemini.api.aop.AopException.WrappedException} otherwise.
     * Also re-throws JVM-fatal errors.
     *
     * @param t the throwable to propagate
     */
    public static void propagate(Throwable t) {
        throwIfRequired(t);

        if (t instanceof RuntimeException)
            throw (RuntimeException) t;

        throw new WrappedException(t);
    }

    /**
     * Unwraps nested {@link io.gemini.api.aop.AopException.WrappedException} instances to
     * recover the original cause.
     *
     * @param t the throwable to unwrap
     * @return the innermost non-{@code WrappedException} cause, or {@code t} if none
     */
    public static Throwable unwrap(Throwable t) {
        Throwable cause = t;
        while (cause instanceof WrappedException)
            cause = cause.getCause();
        return cause != null ? cause : t;
    }


    public static Throwable findCause(Throwable t) {
        while (t != null) {
            if (t.getCause() != null)
                t = t.getCause();
            else
                return t;
        }

        return null;
    }
}
