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
package io.gemini.api.aspect;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;

public interface Joinpoint {

    /**
     * get this class Lookup to access private member
     * @return
     */
    Lookup getThisLookup();

    /**
     * get class information
     * @return
     */
    Class<?> getThisClass();

    /**
     * Returns the static part of this joinpoint.
     *
     * <p>The static part is an accessible object on which a chain of
     * interceptors are installed. */
    AccessibleObject getStaticPart();


    /**
     * Returns the object that holds the current joinpoint's static
     * part.
     *
     * <p>For instance, the target object for an invocation.
     *
     * @return the object (can be null if the accessible object is
     * static). */
    Object getThisObject();

    /**
     * Get the arguments as an array object.
     * It is possible to change element values within this
     * array to change the arguments.
     *
     * @return the argument of the invocation */
    Object[] getArguments();


    // TODO: per advice
    Object getInvocationContext(String key);

    void setInvocationContext(String key, Object value);


    interface MutableJoinpoint<T, E extends Throwable> extends Joinpoint {

        T getReturning();

        E getThrowing();

        void setAdviceReturning(T returning);

        void setAdviceThrowing(E throwing);
    }


    interface ProceedingJoinpoint<T, E extends Throwable> extends Joinpoint {

        /**
         * Proceeds to the next advice in the chain.
         *
         * <p>The implementation and the semantics of this method depends
         * on the actual joinpoint type (see the children interfaces).
         *
         * @return see the children interfaces' proceed definition.
         *
         * @throws Throwable if the joinpoint throws an exception. */
        T proceed() throws E;

        /**
         * 
         * @param arguments
         * @return
         * @throws Throwable
         */
        T proceed(Object... arguments) throws E;
    }
}
