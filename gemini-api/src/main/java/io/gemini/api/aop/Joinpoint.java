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

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;

/**
 * Represents the runtime context of a method, constructor or type initializer invocation 
 * intercepted by the Gemini AOP framework.
 * 
 * <p>
 * Provides access to the target object, its class, the intercepted method/constructor,
 * and the invocation arguments. 
 * Shares additional contextual key value pair across one method invocation.
 * </p>
 *
 * @author   martin.liu
 */
public interface Joinpoint {

    /**
     * get Lookup of target class to access members of target object
     * @return lookup of target class
     */
    Lookup getTargetLookup();

    /**
     * get target class
     * @return target class
     */
    Class<?> getTargetClass();

    /**
     * Returns the static part of this joinpoint.
     *
     * <p>The static part is an accessible object on which a chain of
     * advices are installed. */
    AccessibleObject getStaticPart();


    /**
     * Returns the object that holds the current joinpoint's static
     * part.
     *
     * <p>For instance, the target object for an invocation.
     *
     * @return the object (can be null if the accessible object is
     * static). */
    Object getTargetObject();

    /**
     * Get the arguments as an array object.
     * It is possible to change element values within this
     * array to change the arguments.
     *
     * @return the argument of the invocation */
    Object[] getArguments();


    /**
     * Get attached additional context value for provided key.
     * 
     * @param key   the key of context value
     * @return      context value
     */
    Object getInvocationContext(String key);

    /**
     * Attach additional contextual key value pair across one method invocation.
     * 
     * @param key   the key of context value
     * @param value context value
     */
    void setInvocationContext(String key, Object value);


    /**
     * The {@link MutableJoinpoint} sub-interface additionally exposes the 
     * returning value and throwing exception, and allows advice to override them. 
     *
     * @param <T>   returning value
     * @param <E>   throwing exception
     */
    interface MutableJoinpoint<T, E extends Throwable> extends Joinpoint {

        /**
         * Get the return value of target method invocation and throw IllegalStateException 
         * when called in {@link io.gemini.api.aop.Advice.Before} advice.
         * 
         * @return      returning value
         * @throws IllegalStateException    thrown exception when called in {@link io.gemini.api.aop.Advice.Before} advice
         */
        T getReturning() throws IllegalStateException;

        /**
         * Override the return value of target method invocation.
         * 
         * @param returning     returning value
         */
        void setAdviceReturning(T returning);


        /**
         * Get the thrown exception of target method invocation and throw IllegalStateException 
         * when called in {@link io.gemini.api.aop.Advice.Before} advice.
         * 
         * @return throwing     throwing exception
         * @throws IllegalStateException    thrown exception when called in {@link io.gemini.api.aop.Advice.Before} advice.
         */
        E getThrowing() throws IllegalStateException;

        /**
         * Override the thrown exception of target method invocation.
         *
         * @param throwing throwing exception
         */
        void setAdviceThrowing(E throwing);
    }


    /**
     * The {@link ProceedingJoinpoint} sub-interface additionally exposes the proceed(..) 
     * method to support {@link io.gemini.api.aop.Advice.Around} advice. 
     * 
     * TODO: not supported
     * 
     * @param <T>   returning value
     * @param <E>   throwing exception
     */
    interface ProceedingJoinpoint<T, E extends Throwable> extends Joinpoint {

        /**
         * Proceeds to the next advice in the chain or target method invocation.
         *
         * @return returning value
         */
        T proceed() throws E;

        /**
         * Proceeds to the next advice in the chain or target method invocation 
         * with overrode arguments.
         * 
         * @param arguments     the arguments of method invocation
         * @return              returning value of method invocation
         * @throws Throwable    throwing exception
         */
        T proceed(Object... arguments) throws E;
    }
}
