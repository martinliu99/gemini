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
package io.gemini.aop.test;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-local memento for capturing joinpoint invocation state in integration tests.
 * <p>
 * Stores per-thread maps of target method and advice method invocation records,
 * allowing test assertions to verify that advice was invoked with the expected
 * target object, arguments, return value, and thrown exception.
 * </p>
 *
 * @param <T> the concrete memento type (either {@link TargetMethod} or {@link AdviceMethod})
 *
 * @author   martin.liu
 */
@SuppressWarnings("rawtypes")
public class ExecutionMemento<T> {

    // do not use subtype to avoid possible class initialization deadlock
    private static ThreadLocal<Map<String, ExecutionMemento>> TARGET_METHOD_MEMENTOES;
    private static ThreadLocal<Map<String, ExecutionMemento>> ADVICE_METHOD_MEMENTOES;


    private T memento;

    private boolean invoked;

    private Lookup targetLookup;
    private Class<?> targetClass;
    private AccessibleObject accessibleObject;

    private Object targetObject;
    private Object[] arguments;
    private Object returning;
    private Throwable throwing;


    static {
        TARGET_METHOD_MEMENTOES = new ThreadLocal<Map<String, ExecutionMemento>>() {

            @Override
            protected Map<String, ExecutionMemento> initialValue() {
                return new ConcurrentHashMap<>();
            }
        };

        ADVICE_METHOD_MEMENTOES = new ThreadLocal<Map<String, ExecutionMemento>>() {

            @Override
            protected Map<String, ExecutionMemento> initialValue() {
                return new ConcurrentHashMap<>();
            }
        };
    }

    /**
     * Clears all target method and advice method mementos stored for the current thread.
     * Should be called in test teardown to prevent state leaking between test cases.
     */
    public static void clearMemento() {
        TARGET_METHOD_MEMENTOES.get().clear();
        ADVICE_METHOD_MEMENTOES.get().clear();
    }

    /**
     * Stores a {@link TargetMethod} memento under the given key for the current thread.
     *
     * @param targetMethod the key identifying the target method (typically its signature)
     * @param memento      the memento to store
     */
    public static void putTargetMethodInvoker(String targetMethod, TargetMethod memento) {
        TARGET_METHOD_MEMENTOES.get().put(targetMethod, memento);
    }

    /**
     * Retrieves the {@link TargetMethod} memento stored under the given key for the current thread.
     *
     * @param targetMethod the key identifying the target method
     * @return the stored memento, or {@code null} if none has been recorded
     */
    public static TargetMethod getTargetMethodInvoker(String targetMethod) {
        return (TargetMethod) TARGET_METHOD_MEMENTOES.get().get(targetMethod);
    }

    /**
     * Stores an {@link AdviceMethod} memento under the given key for the current thread.
     *
     * @param adviceMethod the key identifying the advice method (typically its signature)
     * @param memento      the memento to store
     */
    public static void putAdviceMethodInvoker(String adviceMethod, AdviceMethod memento) {
        ADVICE_METHOD_MEMENTOES.get().put(adviceMethod, memento);
    }

    /**
     * Retrieves the {@link AdviceMethod} memento stored under the given key for the current thread.
     *
     * @param adviceMethod the key identifying the advice method
     * @return the stored memento, or {@code null} if none has been recorded
     */
    public static AdviceMethod getAdviceMethodInvoker(String adviceMethod) {
        return (AdviceMethod) ADVICE_METHOD_MEMENTOES.get().get(adviceMethod);
    }

    protected T setInvoker(T invoker) {
        this.memento = invoker;
        return this.memento;
    }

    /**
     * Returns whether the joinpoint was invoked.
     *
     * @return {@code true} if the target or advice method was invoked
     */
    public boolean isInvoked() {
        return invoked;
    }

    /**
     * Records whether the joinpoint was invoked and returns this memento for chaining.
     *
     * @param invoked {@code true} if the method was invoked
     * @return this memento instance
     */
    public T withInvoked(boolean invoked) {
        this.invoked = invoked;
        return memento;
    }

    /**
     * Returns the {@link Lookup} captured at the joinpoint, representing the caller's
     * access context.
     *
     * @return the target lookup, or {@code null} if not set
     */
    public Lookup getTargetLookup() {
        return targetLookup;
    }

    /**
     * Records the {@link Lookup} at the joinpoint and returns this memento for chaining.
     *
     * @param targetLookup the lookup to record
     * @return this memento instance
     */
    public T withTargetLookup(Lookup targetLookup) {
        this.targetLookup = targetLookup;
        return memento;
    }

    /**
     * Returns the target class captured at the joinpoint.
     *
     * @return the target class, or {@code null} if not set
     */
    public Class<?> getTargetClass() {
        return targetClass;
    }

    /**
     * Records the target class at the joinpoint and returns this memento for chaining.
     *
     * @param targetClass the target class to record
     * @return this memento instance
     */
    public T withTargetClass(Class<?> targetClass) {
        this.targetClass = targetClass;
        return memento;
    }

    /**
     * Returns the static part of the joinpoint (the reflective {@link AccessibleObject}
     * representing the method or constructor).
     *
     * @return the static part, or {@code null} if not set
     */
    public AccessibleObject getStaticPart() {
        return accessibleObject;
    }

    /**
     * Records the static part of the joinpoint and returns this memento for chaining.
     *
     * @param accessibleObject the reflective object representing the joinpoint
     * @return this memento instance
     */
    public T withStaticPart(AccessibleObject accessibleObject) {
        this.accessibleObject = accessibleObject;
        return memento;
    }

    /**
     * Returns the {@code this} / target object captured at the joinpoint.
     *
     * @return the target object, or {@code null} for static methods
     */
    public Object getTargetObject() {
        return this.targetObject;
    }

    /**
     * Records the target object at the joinpoint and returns this memento for chaining.
     *
     * @param targetObject the target object to record
     * @return this memento instance
     */
    public T withTargetObject(Object targetObject) {
        this.targetObject = targetObject;
        return memento;
    }

    /**
     * Returns a copy of the arguments captured at the joinpoint.
     *
     * @return the argument array, or {@code null} if not set
     */
    public Object[] getArguments() {
        return this.arguments;
    }

    /**
     * Records the arguments at the joinpoint (copied defensively) and returns this memento for chaining.
     *
     * @param arguments the argument array to record
     * @return this memento instance
     */
    public T withArgumnts(Object[] arguments) {
        this.arguments = Arrays.copyOf(arguments, arguments.length);
        return memento;
    }

    /**
     * Records varargs arguments at the joinpoint (copied defensively) and returns this memento for chaining.
     *
     * @param arguments the varargs to record
     * @return this memento instance
     */
    public T withVArgumnts(Object... arguments) {
        this.arguments = Arrays.copyOf(arguments, arguments.length);
        return memento;
    }

    /**
     * Returns the return value captured after the joinpoint completes normally.
     *
     * @return the return value, or {@code null} if the method returned void or threw
     */
    public Object getReturning() {
        return this.returning;
    }

    /**
     * Records the return value after normal completion and returns this memento for chaining.
     *
     * @param returning the return value to record
     * @return this memento instance
     */
    public T withReturning(Object returning) {
        this.returning = returning;
        return memento;
    }

    /**
     * Returns the exception thrown at the joinpoint, if any.
     *
     * @return the thrown exception, or {@code null} if the method completed normally
     */
    public Throwable getThrowing() {
        return this.throwing;
    }

    /**
     * Records the exception thrown at the joinpoint and returns this memento for chaining.
     *
     * @param throwing the exception to record
     * @return this memento instance
     */
    public T withThrowing(Throwable throwing) {
        this.throwing = throwing;
        return memento;
    }

    /**
     * Memento for recording the invocation state of a target (intercepted) method.
     * Use {@link ExecutionMemento#putTargetMethodInvoker} and
     * {@link ExecutionMemento#getTargetMethodInvoker} to store and retrieve instances.
     */
    public static class TargetMethod extends ExecutionMemento<TargetMethod> {

        /**
         * Creates a new {@code TargetMethod} memento and registers itself as its own invoker.
         */
        public TargetMethod() {
            this.setInvoker(this);
        }
    }


    /**
     * Memento for recording the invocation state of an advice method.
     * Use {@link ExecutionMemento#putAdviceMethodInvoker} and
     * {@link ExecutionMemento#getAdviceMethodInvoker} to store and retrieve instances.
     */
    public static class AdviceMethod extends ExecutionMemento<AdviceMethod> {

        /**
         * Creates a new {@code AdviceMethod} memento and registers itself as its own invoker.
         */
        public AdviceMethod() {
            this.setInvoker(this);
        }
    }
}
