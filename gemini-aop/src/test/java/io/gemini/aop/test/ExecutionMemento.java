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
package io.gemini.aop.test;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class ExecutionMemento<T> {

    private static ThreadLocal<Map<String, TargetMethod>> TARGET_METHOD_MEMENTOES;
    private static ThreadLocal<Map<String, AdviceMethod>> ADVICE_METHOD_MEMENTOES;


    private T memento;

    private boolean invoked;

    private Lookup thisLookup;
    private Class<?> thisClass;
    private AccessibleObject accessibleObject;

    private Object thisObject;
    private Object[] arguments;
    private Object returning;
    private Throwable throwing;


    static {
        Map<String, TargetMethod> targetMethodMementoes = new HashMap<>();
        TARGET_METHOD_MEMENTOES = new ThreadLocal<>();
        TARGET_METHOD_MEMENTOES.set(targetMethodMementoes);

        Map<String, AdviceMethod> adviceMethodMementoes = new HashMap<>();
        ADVICE_METHOD_MEMENTOES = new ThreadLocal<>();
        ADVICE_METHOD_MEMENTOES.set(adviceMethodMementoes);
    }

    public static void clearMemento() {
        TARGET_METHOD_MEMENTOES.get().clear();
        ADVICE_METHOD_MEMENTOES.get().clear();
    }

    public static void putTargetMethodInvoker(String targetMethod, TargetMethod memento) {
        TARGET_METHOD_MEMENTOES.get().put(targetMethod, memento);
    }

    public static TargetMethod getTargetMethodInvoker(String targetMethod) {
        return TARGET_METHOD_MEMENTOES.get().get(targetMethod);
    }

    public static void putAdviceMethodInvoker(String adviceMethod, AdviceMethod memento) {
        ADVICE_METHOD_MEMENTOES.get().put(adviceMethod, memento);
    }

    public static AdviceMethod getAdviceMethodInvoker(String adviceMethod) {
        return ADVICE_METHOD_MEMENTOES.get().get(adviceMethod);
    }

    protected T setInvoker(T invoker) {
        this.memento = invoker;
        return this.memento;
    }

    public boolean isInvoked() {
        return invoked;
    }

    public T withInvoked(boolean invoked) {
        this.invoked = invoked;
        return memento;
    }

    public Lookup getThisLookup() {
        return thisLookup;
    }

    public T withThisLookup(Lookup thisLookup) {
        this.thisLookup = thisLookup;
        return memento;
    }

    public Class<?> getThisClass() {
        return thisClass;
    }

    public T withThisClass(Class<?> thisClass) {
        this.thisClass = thisClass;
        return memento;
    }

    public AccessibleObject getStaticPart() {
        return accessibleObject;
    }

    public T withStaticPart(AccessibleObject accessibleObject) {
        this.accessibleObject = accessibleObject;
        return memento;
    }

    public Object getThisObject() {
        return this.thisObject;
    }

    public T withThisObject(Object thisObject) {
        this.thisObject = thisObject;
        return memento;
    }

    public Object[] getArguments() {
        return this.arguments;
    }

    public T withArgumnts(Object[] arguments) {
        this.arguments = Arrays.copyOf(arguments, arguments.length);
        return memento;
    }

    public T withVArgumnts(Object... arguments) {
        this.arguments = Arrays.copyOf(arguments, arguments.length);
        return memento;
    }

    public Object getReturning() {
        return this.returning;
    }

    public T withReturning(Object returning) {
        this.returning = returning;
        return memento;
    }

    public Throwable getThrowing() {
        return this.throwing;
    }

    public T withThrowing(Throwable throwing) {
        this.throwing = throwing;
        return memento;
    }

    public static class TargetMethod extends ExecutionMemento<TargetMethod> {

        public TargetMethod() {
            this.setInvoker(this);
        }
    }


    public static class AdviceMethod extends ExecutionMemento<AdviceMethod> {

        public AdviceMethod() {
            this.setInvoker(this);
        }
    }
}
