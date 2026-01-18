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
package io.gemini.aop.weaver;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import io.gemini.core.bootstrap.BootstrapClassProvider;

/**
 * This class is one of core classes of AOP framework used by {@link io.gemini.aop.weaver.advice.XxxAdvice}
 * to invoke {@code Advice} instances at runtime.
 * 
 * 
 * <p>Class functionalities includes, 
 * <ol>
 * 
 * <li>get or set {@code Creator} instance.</li>
 * <li>create INDY BSM method to get {@code CallSite}.</li>
 * <li>break dispatcher invocation circularity.</li>
 * 
 * </ol>
 * 
 * 
 * <dl>
 * 
 * <dt>Class visibility.</dt>
 * <dd>To make instrumented target classes visible to this class, especially types under bootstrap 
 * ClassLoader, AOP framework annotates this classes with {@code @BootstrapClassProvider} annotation, 
 * to inject it into given package under bootstrap ClassLoader. The package name is defined by 
 * {@code @BootstrapClassProvider.scopeType}.
 * </dd>
 * 
 * <dt>Get and cache joinpoint metadata</dt>
 * <dd>AOP framework uses {@code Descriptor} to hold joinpoint metadata, such as {@code Lookup} instance,
 * {@code AccessibleObject} instance, all matched {@code Advice} instances,
 * <ul>
 * <li>Before JDK 7, query {@code Creator#createDescriptor(...)} for each joinpoint invocation,
 * <li>After JDK 7, use INDY BSM method {@code BootstrapDispatcher#createDescriptorCallSite()}.
 * </ul>
 * </dd>
 * 
 * <dt>Dispatcher circularity.</dt>
 * <dd>Bootstrap classes, such as java.lang.String, might be invoked in {@code BootstrapDispatcher} or
 * {@code Advice} which causes dispatcher circularity. AOP provides {@code BootstrapDispatcher#enterDispatcher()}
 * method to disable {@code BootstrapDispatcher} and {@code BootstrapDispatcher#existDispatcher()} to
 * enable {@code BootstrapDispatcher}.
 * </dd> 
 * </dl>
 * 
 *
 * @author   martin.liu
 * @since    1.0
 */
@BootstrapClassProvider( scopeType = String.class )
public abstract class BootstrapDispatcher {

    public static final String VAR_ADVICE_DISPATCHER = "_$$AdviceDispatcher$$";

    public static final Object[] ARGUMENTS = new Object[0];

    private static Creator CREATOR;

    private static ThreadLocal<Boolean> IN_DISPATCHER = new ThreadLocal<>();


    private BootstrapDispatcher() {}


    /**
     * Initializes {@code BootstrapDispatcher#Creator} field when AOP framework launches.
     * 
     * @param delegate
     */
    public static void setCreator(Creator delegate) {
        if (CREATOR != null) {
            System.err.println("BootstrapDispatcher.FACTORY already initialized with " + CREATOR);
            return;
        }

        if (delegate == null) {
            throw new IllegalArgumentException("BootstrapDispatcher.Creator must not be null.");
        }

        CREATOR = delegate;
    }

    /**
     * Get {@code BootstrapDispatcher#Creator}.
     * 
     * @return
     */
    public static Creator getCreator() {
        return CREATOR;
    }


    /**
     * Get INDY BSM method to get and Cache {@code Descriptor}.
     * @param lookup
     * @param bsmMethodName
     * @param bsmMethodType
     * @param args
     * @return
     */
    public static CallSite createDescriptorCallSite(MethodHandles.Lookup lookup, 
            String bsmMethodName,
            MethodType bsmMethodType,
            Object... args) {
        return CREATOR.createDescriptorCallSite(lookup, bsmMethodName, bsmMethodType, args);
    }


    /**
     * Check {@code BootstrapDispatcher} is in dispatch.
     * 
     * @return
     */
    public static boolean isInDispatch() {
        Boolean inDispatching = IN_DISPATCHER.get();
        return inDispatching != null;
    }

    /**
     * enable dispatching.
     */
    public static void enableDispatch() {
        IN_DISPATCHER.remove();
    }

    /**
     * disable dispatch.
     */
    public static void disableDispatch() {
        IN_DISPATCHER.set( Boolean.TRUE );
    }


    /**
     * This class provides below functionalities,
     * 
     * <ol>
     * <li>notify {@code Advice} instances before target method invocation,
     * and after target method invocation with returning or throwing of target method,
     * <li>modify arguments of target method. 
     * </ol>
     * 
     */
    @BootstrapClassProvider( scopeType = String.class )
    public static interface Dispatcher<T, E extends Throwable> {

        T dispatch() throws E;

        Object[] getArguments();

        void setReturning(T returning);

        void setThrowing(E throwing);


        boolean hasAdviceReturning();

        T getAdviceReturning();

        boolean hasAdviceThrowing();

        E getAdviceThrowing();
    }


    /**
     * This interface defines APIs to create {@link Descriptor} and {@code BootstrapDispatcher} instance.
     *
     */
    @BootstrapClassProvider( scopeType = String.class )
    public static interface Creator {

        /**
         * Create {@link Descriptor} instance to hold joinpoint metadata.
         * 
         * @param lookup
         * @param arguments
         * @return
         */
        Object createDescriptor(MethodHandles.Lookup lookup, 
                Object... arguments);


        /**
         * Create INDY CallSite to create {@link Descriptor} instance to hold joinpoint metadata.
         * 
         * @param lookup
         * @param bsmMethodName
         * @param bsmMethodType
         * @param arguments
         * @return
         */
        CallSite createDescriptorCallSite(MethodHandles.Lookup lookup, 
                String bsmMethodName, MethodType bsmMethodType, Object... arguments);


        /**
         * Create {@code BootstrapDispatcher} instance.
         * 
         * @param <T>
         * @param <E>
         * @param descriptor
         * @param thisObject
         * @param arguments
         * @return
         */
        <T, E extends Throwable> Dispatcher<T, E> createDispacther(
                Object descriptor, Object thisObject, Object[] arguments);
    }
}
