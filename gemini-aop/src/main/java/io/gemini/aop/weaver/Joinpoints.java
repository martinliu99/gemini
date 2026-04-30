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
package io.gemini.aop.weaver;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.AopContext;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Advice.After;
import io.gemini.api.aop.Advice.Around;
import io.gemini.api.aop.Advice.Before;
import io.gemini.api.aop.Joinpoint;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.Joinpoint.ProceedingJoinpoint;
import io.gemini.core.bootstrap.BootstrapClassConsumer;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;

/**
 * Holds joinpoint metadata and runtime dispatch logic for the Gemini AOP framework.
 * <p>
 * Key inner classes:
 * <ul>
 *   <li>{@link Descriptor} – immutable metadata about a joinpoint (target class, method/constructor,
 *       advisors). Created once per instrumented method and cached.</li>
 *   <li>{@link DefaultMutableJoinpoint} – mutable joinpoint passed to before/after advice,
 *       allowing argument modification and return/throw override.</li>
 *   <li>{@link MutableJoinpointDispatcher} – drives the before/after advices at runtime.</li>
 *   <li>{@link DefaultProceedingJoinpoint} – joinpoint passed to around advice, supporting
 *       {@code proceed()} to invoke the original method.</li>
 *   <li>{@link ProceedingJoinpointDispatcher} – drives the around advices at runtime.</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public interface Joinpoints {

    /**
     * Immutable descriptor capturing all static metadata about an instrumented joinpoint.
     * Cached per method/constructor to avoid repeated reflection lookups at runtime.
     */
    class Descriptor {

        private final Lookup targetLookup;

        private final String accessibleName;
        private final AccessibleObject accessibleObject;

        private final boolean typeInitializer;
        private final boolean constructor;

        private final boolean staticMethod;

        private final boolean voidReturning;

        // refresh at runtime
        private List<? extends Advisor> advisors;


        /**
         * Creates a new {@code Descriptor} for the given joinpoint.
         *
         * @param thisLookup       the lookup context of the instrumented class
         * @param accessibleName   the string representation of the method or constructor
         * @param accessibleObject the target {@link Method} or {@link Constructor}, or {@code null} for type initializers
         * @param advisors     the list of advisors to apply at this joinpoint
         */
        public Descriptor(Lookup thisLookup, String accessibleName, AccessibleObject accessibleObject, 
                List<? extends Advisor> advisors) {
            this.targetLookup = thisLookup;

            this.accessibleName = accessibleName;
            this.accessibleObject = accessibleObject;

            if (accessibleObject == null) {
                this.typeInitializer = true;

                this.constructor = false;
                this.staticMethod = true;
                this.voidReturning = true;
            } else {
                this.typeInitializer = false;

                if (accessibleObject instanceof Constructor) {
                    this.constructor = true;
                    this.staticMethod = false;
                    this.voidReturning = true;
                } else {
                    this.constructor = false;

                    Method method = (Method) accessibleObject;
                    this.staticMethod = Modifier.isStatic(method.getModifiers());
                    this.voidReturning = method.getReturnType() == void.class;
                }
            }

            this.advisors = advisors;
        }


        /**
         * Returns the {@link java.lang.invoke.MethodHandles.Lookup} for the instrumented class,
         * used to access private members of the target class.
         *
         * @return the target class lookup
         */
        public Lookup getTargetLookup() {
            return targetLookup;
        }

        /**
         * Returns the class being instrumented at this joinpoint.
         *
         * @return the target class
         */
        public Class<?> getTargetClass() {
            return targetLookup.lookupClass();
        }

        /**
         * Returns the string representation of the target method or constructor signature.
         *
         * @return the accessible name
         */
        public String getAccessibleName() {
            return accessibleName;
        }

        /**
         * Returns the target {@link Constructor}, or {@code null} if this is not a constructor joinpoint.
         *
         * @return the target constructor, or {@code null}
         */
        public Constructor<?> getTargetConstructor() {
            return (this.typeInitializer || this.constructor == false) ? null : (Constructor<?>) this.getStaticPart();
        }

        /**
         * Returns the target {@link Method}, or {@code null} if this is not a method joinpoint.
         *
         * @return the target method, or {@code null}
         */
        public Method getTargetMethod() {
            return (this.typeInitializer || this.constructor == true) ? null : (Method) this.getStaticPart();
        }

        private AccessibleObject getStaticPart() {
            return this.accessibleObject;
        }

        /**
         * Returns {@code true} if this joinpoint is a static class initializer ({@code <clinit>}).
         *
         * @return {@code true} for type initializer joinpoints
         */
        public boolean isTypeInitializer() {
            return typeInitializer;
        }

        /**
         * Returns {@code true} if the target method is static.
         *
         * @return {@code true} for static method joinpoints
         */
        public boolean isStaticMethod() {
            return staticMethod;
        }

        /**
         * Returns {@code true} if this joinpoint is a constructor.
         *
         * @return {@code true} for constructor joinpoints
         */
        public boolean isConstructor() {
            return constructor;
        }

        /**
         * Returns {@code true} if the target method returns {@code void}.
         *
         * @return {@code true} for void-returning joinpoints
         */
        public boolean isVoidReturning() {
            return voidReturning;
        }

        /**
         * Returns the advisors applied at this joinpoint.
         *
         * @return the list of advisors
         */
        public List<? extends Advisor> getAdvisors() {
            return advisors;
        }
    }


    /**
     * Abstract base for all joinpoint implementations, providing common state:
     * descriptor, target object, arguments, and invocation context.
     */
    abstract class AbstractBase<T, E extends Throwable> implements Joinpoint {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractBase.class);

        protected static final Object UNDEFINED_RETURNING = new Object();

        @SuppressWarnings("StaticAssignmentOfThrowable")
        protected static final Throwable UNDEFINED_THROWING = new Throwable();


        protected Descriptor descriptor;

        protected Object targetObject;
        protected final boolean lazyInitializeThis;

        protected Object[] arguments;

        protected final Map<String, Object> invocationContext;


        AbstractBase(Descriptor descriptor, Object targetObject, Object[] arguments) {
            this.descriptor = descriptor;

            if (descriptor.isConstructor() == true) {
                this.lazyInitializeThis = true;

                // ignore input targetObject
                this.targetObject = UNDEFINED_RETURNING;
            } else {
                this.lazyInitializeThis = false;

                if (descriptor.staticMethod == false) {
                    Assert.notNull(targetObject, "'targetObject' must not be null.");

                    this.targetObject = targetObject;
                } else {
                    this.targetObject = null;
                }
            }

            if (arguments == null || arguments.length == 0) {
                this.arguments = new Object[0];
            } else {
                Object[] args = new Object[arguments.length];
                System.arraycopy(arguments, 0, args, 0, arguments.length);
                this.arguments = args;
            }

            this.invocationContext = new LinkedHashMap<>();
        }

        /**
         * Returns the {@link Descriptor} holding static joinpoint metadata.
         *
         * @return the joinpoint descriptor
         */
        Descriptor getDescriptor() {
            return descriptor;
        }

        /**
         * get this class Lookup to access private member
         * @return lookup of target type
         */
        @Override
        public Lookup getTargetLookup() {
            return descriptor.getTargetLookup();
        }

        /**
         * get class information
         * @return target type
         */
        @Override
        public Class<?> getTargetClass() {
            return descriptor.getTargetClass();
        }

        /**
         * Returns the static part of this joinpoint.
         *
         * <p>The static part is an accessible object on which a chain of
         * interceptors are installed. */
        @Override
        public AccessibleObject getStaticPart() {
            return descriptor.getStaticPart();
        }


        /**
         * Returns the Spring AOP proxy. Cannot be {@code null}.
         */
        @Override
        public Object getTargetObject() {
            return (UNDEFINED_RETURNING == targetObject) ? null : this.targetObject;
        }

        @Override
        public Object[] getArguments() {
            return this.arguments;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getInvocationContext(String key) {
            return this.invocationContext.get(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setInvocationContext(String key, Object value) {
            this.invocationContext.put(key, value);
        }
    }


    /**
     * Mutable joinpoint passed to {@link io.gemini.api.aop.Advice.Before} and
     * {@link io.gemini.api.aop.Advice.After} advice. Allows advice to inspect and
     * override the target method's return value or thrown exception.
     */
    class DefaultMutableJoinpoint<T, E extends Throwable> extends AbstractBase<T, E> implements MutableJoinpoint<T, E> {

        private final Supplier<? extends Advice> currentAdviceSupplier;

        @SuppressWarnings("unchecked")
        private T returning = (T) UNDEFINED_RETURNING;
        @SuppressWarnings("unchecked")
        private E throwing = (E) UNDEFINED_THROWING;

        @SuppressWarnings("unchecked")
        private T adviceReturning = (T) UNDEFINED_RETURNING;
        @SuppressWarnings("unchecked")
        private E adviceThrowing = (E) UNDEFINED_THROWING;


        public DefaultMutableJoinpoint(Descriptor descriptor, Object targetObject, 
                Object[] arguments, Supplier<? extends Advice> currentAdviceSupplier) {
            super(descriptor, targetObject, arguments);

            this.currentAdviceSupplier = currentAdviceSupplier;
        }

        /**
         * {@inheritDoc}
         * <p>Throws {@link IllegalStateException} if the return value has not been set yet,
         * such as calling in {@link io.gemini.api.aop.Advice.Before}.</p>
         */
        @Override
        public T getReturning() throws IllegalStateException {
            if (UNDEFINED_RETURNING == this.returning)
                throw new IllegalStateException("Uninitialized or unsupported returning");

            return returning;
        }

        /**
         * Sets the actual return value from the target method invocation.
         * For constructors, also updates the {@code targetObject} reference.
         *
         * @param returning the return value from the target method
         */
        void setReturning(T returning) {
            if (getDescriptor().isTypeInitializer()) {
                this.returning = null;
            } else if (getDescriptor().isConstructor()) {
                this.targetObject = returning;
                this.returning = returning;
            } else {
                this.returning = returning;
            }
        }


        /**
         * {@inheritDoc}
         * <p>Throws {@link IllegalStateException} if the exception has not been set yet,
         * such as calling in {@link io.gemini.api.aop.Advice.Before}.</p>
         */
        @Override
        public E getThrowing() throws IllegalStateException {
            if (UNDEFINED_THROWING == this.throwing)
                throw new IllegalStateException("Uninitialized throwing");

            return throwing;
        }

        /**
         * Sets the actual exception thrown by the target method.
         *
         * @param throwing the exception thrown, or {@code null} if none
         */
        void setThrowing(E throwing) {
            this.throwing = throwing;
        }


        /** 
         * Returns {@code true} if advice has set an override return value. 
         */
        boolean hasAdviceReturning() {
            return this.adviceReturning != UNDEFINED_RETURNING;
        }

        /** 
         * Returns the override return value set by advice, or the sentinel if not set. 
         */
        T getAdviceReturning() {
            return this.adviceReturning;
        }

        /**
         * Sets the override return value that advice wants to substitute for the target method's result.
         * Validates that the value is assignment-compatible with the method's declared return type.
         * Ignored with a warning if the type is incompatible.
         *
         * @param returning the override return value to set
         */
        @Override
        public void setAdviceReturning(T returning) {
            Descriptor descriptor = getDescriptor();
            if (descriptor.voidReturning) {
                this.adviceReturning = null;
                return;
            }

            Class<?> returnType = descriptor.getTargetMethod().getReturnType();
            if (returning == null || ClassUtils.isAssignableFrom(returnType, returning.getClass()) == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored advice returning object '{}' which must be instance of Method's returning type. \n"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n"
                            + "  CurrentAdvice: {} \n",
                            returning, 
                            descriptor.getTargetClass().getClassLoader(),
                            descriptor.getAccessibleName(),
                            currentAdviceSupplier.get()
                    );

                return;
            }

            this.adviceReturning = returning;
        }


        /** 
         * Returns {@code true} if advice has set an override exception. 
         */
        boolean hasAdviceThrowing() {
            return this.adviceThrowing != UNDEFINED_THROWING;
        }

        /** 
         * Returns the override exception set by advice, or the sentinel if not set. 
         */
        E getAdviceThrowing() {
            return this.adviceThrowing;
        }

        /**
         * Sets the override exception that advice wants to substitute for the target method's thrown exception.
         * Validates that the exception is a {@link RuntimeException} or a declared checked exception.
         * Ignored with a warning if the type is incompatible.
         *
         * @param throwing the override exception to set; must not be {@code null}
         */
        @Override
        public void setAdviceThrowing(E throwing) {
            if (throwing == null) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored null advice throwing.\n");

                return;
            }

            Descriptor descriptor = getDescriptor();
            Class<?>[] exceptionTypes = descriptor.isTypeInitializer() 
                    ? null 
                    : descriptor.isConstructor() 
                        ? descriptor.getTargetConstructor().getExceptionTypes() 
                        : descriptor.getTargetMethod().getExceptionTypes();
            Class<? extends Throwable> throwingType = throwing.getClass();

            boolean assignable = false;
            if (exceptionTypes != null && exceptionTypes.length != 0) {
                for (Class<?> exceptionClass : exceptionTypes) {
                    if (exceptionClass.isAssignableFrom(throwingType)) {
                        assignable = true;
                        break;
                    }
                }
            }

            if (assignable == false && RuntimeException.class.isAssignableFrom(throwingType) == false) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored advice throwing object '{}' which must be instance of RuntimeException or declared exception types. \n"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n"
                            + "  CurrentAdvice: {} \n",
                            throwing, 
                            descriptor.getTargetClass().getClassLoader(),
                            descriptor.getAccessibleName(),
                            currentAdviceSupplier.get()
                    );

                return;
            }

            this.adviceThrowing = throwing;
        }
    }


    /**
     * Runtime dispatcher for before advice, and after advice chains.
     * Created by the {@link io.gemini.aop.weaver.BootstrapDispatcher.Delegator} at each
     * instrumented joinpoint invocation and drives the {@link DefaultMutableJoinpoint}.
     */
    @BootstrapClassConsumer
    class MutableJoinpointDispatcher<T, E extends Throwable> implements BootstrapDispatcher.Dispatcher<T, E>, Supplier<Advice> {

        private static final Logger LOGGER = LoggerFactory.getLogger(MutableJoinpointDispatcher.class);


        private final AopContext aopContext;

        private DefaultMutableJoinpoint<T, E> joinpoint = null;

        private boolean dispatchBeforeAdvice = true;
        private List<? extends Advice.Before<T, E>> beforeAdvices = Collections.emptyList();
        private List<? extends Advice.After<T, E>> afterAdvices = Collections.emptyList();

        private Advice currentAdvice;


        /**
         * Creates a new {@code MutableJoinpointDispatcher} for the given joinpoint.
         * Initializes the joinpoint instance and splits the advisor chain into before/after lists.
         *
         * @param descriptor    the joinpoint descriptor
         * @param targetObject  the target object instance
         * @param arguments     the method arguments
         * @param aopContext    the central AOP context
         */
        public MutableJoinpointDispatcher(Descriptor descriptor, Object targetObject, 
                Object[] arguments, AopContext aopContext) {
            Assert.notNull(aopContext, "'aopContext' must not be null.");
            this.aopContext = aopContext;

            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

            Class<?> targetClass = descriptor.getTargetClass();
            ClassLoader targetClassLoader = targetClass.getClassLoader();
            try {
                ThreadContext.setContextClassLoader(targetClassLoader);  // set targetClassLoader

                doInitialize(descriptor, targetObject, arguments);
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not create joinpoint instance of target type '{}',"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n", 
                            targetClass.getName(), 
                            targetClassLoader, 
                            descriptor.getAccessibleName(), t);

                Throwables.throwIfRequired(t);
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }


        /**
         * Initializes the joinpoint and splits the advisor chain into before/after advice lists.
         *
         * @param descriptor    the joinpoint descriptor
         * @param targetObject  the target object instance
         * @param arguments     the method arguments
         */
        @SuppressWarnings("unchecked")
        protected void doInitialize(Descriptor descriptor, Object targetObject, Object[] arguments) {
            // create Joinpoint instance
            joinpoint = new DefaultMutableJoinpoint<T, E>(descriptor, targetObject, arguments, this);

            // initialize advisors
            List<? extends Advisor> advisors = descriptor.getAdvisors();
            advisors = CollectionUtils.isEmpty(advisors) ? Collections.emptyList() : advisors;

            List<Advice.Before<T, E>> beforeAdvices = new ArrayList<>();
            List<Advice.After<T, E>> afterAdvices = new ArrayList<>();
            for (Iterator<? extends Advisor> iterator = advisors.iterator(); iterator.hasNext(); ) {
                Advisor advisor = iterator.next();
                Class<? extends Advice> adviceClass = advisor.getAdviceClass();
                if (adviceClass == null)
                    iterator.remove();

                boolean isBeforeAdvice = Advice.Before.class.isAssignableFrom(adviceClass);
                boolean isAfterAdvice = Advice.After.class.isAssignableFrom(adviceClass);
                if (isBeforeAdvice == false && isAfterAdvice == false) {
                    continue;
                }

                Advice advice = advisor.getAdvice();
                if (advice == null)
                    iterator.remove();

                if (isBeforeAdvice == true && isAfterAdvice == true) {
                    beforeAdvices.add( (Advice.Before<T, E>) advice );
                    afterAdvices.add( (Advice.After<T, E>) advice );
                } else if (isBeforeAdvice == true && isAfterAdvice == false) {
                    beforeAdvices.add( (Advice.Before<T, E>) advice );
                } else if (isBeforeAdvice == false && isAfterAdvice == true) {
                    afterAdvices.add( (Advice.After<T, E>) advice );
                }
            }

            this.beforeAdvices = beforeAdvices;
            this.afterAdvices = afterAdvices;
        }


        protected AopContext getAopContext() {
            return aopContext;
        }

        protected List<? extends Advice.Before<T, E>> getBeforeAdvices() {
            return beforeAdvices;
        }

        protected List<? extends Advice.After<T, E>> getAfterAdvices() {
            return afterAdvices;
        }

        protected Class<?> getTargetClass() {
            return joinpoint.getDescriptor().getTargetClass();
        }

        protected Object getAccessibleName() {
            return joinpoint.getDescriptor().getAccessibleName();
        }


        /**
         * Dispatches the before or after advice chain depending on the current phase.
         * Before-advice is invoked first; after-advice is invoked on the second call.
         * Stops early if any advice sets an override return or throw value.
         *
         * @return always {@code null} for before/after advice
         * @throws E if any advice throws an unrecoverable exception
         */
        @Override
        public T dispatch() throws E {
            if (dispatchBeforeAdvice == true && this.beforeAdvices.size() == 0) {
                dispatchBeforeAdvice = false;
                return null;
            }
            if (dispatchBeforeAdvice == false && this.afterAdvices.size() == 0) {
                return null;
            }

            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

            String targetTypeName = getTargetClass().getName();
            ClassLoader targetClassLoader = getTargetClass().getClassLoader();
            try {
                ThreadContext.setContextClassLoader(targetClassLoader);  // set targetClassLoader

                if (dispatchBeforeAdvice) {
                    this.doInvokeBeforeAdvices(targetClassLoader);
                } else {
                    this.doInvokeAfterAdvices(targetClassLoader);
                }

                return null;
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("$Could not invoke {} for joinpoint instance of target type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n"
                            + "  Advices: \n"
                            + "    {} \n",
                            dispatchBeforeAdvice ? "BeforeAdvices" : "AfterAdvices", 
                            targetTypeName,
                            targetClassLoader, 
                            getAccessibleName(), 
                            StringUtils.join(dispatchBeforeAdvice ? this.beforeAdvices : this.afterAdvices, e -> e.getClass().getName(), "\n    "),
                            t
                    );

                Throwables.throwIfRequired(t);
                return null;
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }

        /**
         * Invokes all before-advice instances in order, stopping early if any advice
         * sets an override return or throw value.
         *
         * @param targetClassLoader the target class loader (used for thread context)
         * @throws Throwable if any advice throws
         */
        protected void doInvokeBeforeAdvices(ClassLoader targetClassLoader) throws Throwable {
            for (int index = 0; index < this.beforeAdvices.size(); index++) {
                Before<T, E> advice = this.beforeAdvices.get(index);

                this.currentAdvice = advice;
                try {
                    advice.before(joinpoint);
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("$Could not invoke joinpoint instance of target type '{}', \n"
                                + "  ClassLoader: {} \n"
                                + "  TargetMethod: {} \n"
                                + "  CurrentAdvice: {}", 
                                getTargetClass().getTypeName(),
                                targetClassLoader,
                                getAccessibleName(),
                                advice, 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }

                if (hasAdviceReturning() || hasAdviceThrowing())
                    break;
            }

            this.currentAdvice = null;
            this.dispatchBeforeAdvice = false;
        }

        /**
         * Invokes all after-advice instances in reverse order, stopping early if any advice
         * sets an override return or throw value.
         *
         * @param targetClassLoader the target class loader (used for thread context)
         * @throws Throwable if any advice throws
         */
        protected void doInvokeAfterAdvices(ClassLoader targetClassLoader) throws Throwable {
            for (int index = this.afterAdvices.size() - 1; index >= 0; index--) {
                After<T, E> advice = this.afterAdvices.get(index);

                this.currentAdvice = advice;
                try {
                    advice.after(joinpoint);
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("$Could not invoke joinpoint instance of target type '{}', \n"
                                + "  ClassLoader: {} \n"
                                + "  TargetMethod: {} \n"
                                + "  CurrentAdvice: {}", 
                                getTargetClass().getTypeName(),
                                targetClassLoader,
                                getAccessibleName(),
                                advice, 
                                t
                        );

                    Throwables.throwIfRequired(t);
                }

                if (hasAdviceReturning() || hasAdviceThrowing())
                    break;
            }

            this.currentAdvice = null;
        }

        /**
         * {@inheritDoc}
         */
        /**
         * Returns the currently executing {@link Advice} instance, used by the joinpoint
         * to log the advice name in warning messages.
         *
         * @return the current advice, or {@code null} between advice invocations
         */
        @Override
        public Advice get() {
            return this.currentAdvice;
        }


        /**
         * {@inheritDoc}
         * <p>Returns the arguments from the underlying joinpoint, which may have been modified by before-advice.</p>
         */
        @Override
        public Object[] getArguments() {
            return joinpoint.getArguments();
        }

        /**
         * {@inheritDoc}
         * <p>Delegates to the underlying joinpoint to record the target method's return value.</p>
         */
        @Override
        public void setReturning(T returning) {
            joinpoint.setReturning(returning);
        }

        /**
         * {@inheritDoc}
         * <p>Delegates to the underlying joinpoint to record the target method's thrown exception.</p>
         */
        @Override
        public void setThrowing(E throwing) {
            joinpoint.setThrowing(throwing);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasAdviceReturning() {
            return joinpoint.hasAdviceReturning();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public T getAdviceReturning() {
            return joinpoint.getAdviceReturning();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasAdviceThrowing() {
            return joinpoint.hasAdviceThrowing();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public E getAdviceThrowing() {
            return joinpoint.getAdviceThrowing();
        }


        /**
         * Extends {@link MutableJoinpointDispatcher} to add per-type diagnostic logging
         * for joinpoint creation and before/after advice invocation.
         */
        static class Diagnostic<T, E extends Throwable> extends MutableJoinpointDispatcher<T, E> {

            private static final Logger LOGGER = LoggerFactory.getLogger(Diagnostic.class);


            public Diagnostic(Descriptor descriptor, 
                    Object targetObject, Object[] arguments, AopContext aopContext) {
                super(descriptor, targetObject, arguments, aopContext);
            }

            @Override
            protected void doInitialize(Descriptor descriptor, Object targetObject, Object[] arguments) {
                Class<?> targetClass = descriptor.getTargetClass();
                String targetTypeName = targetClass.getName();
                if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName)) {
                    LOGGER.info("^Creating joinpoint instance of target type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n", 
                            targetTypeName, 
                            targetClass.getClassLoader(), 
                            descriptor.getAccessibleName()
                    );
                }

                super.doInitialize(descriptor, targetObject, arguments);
            }


            @Override
            protected void doInvokeBeforeAdvices(ClassLoader targetClassLoader) throws Throwable {
                Class<?> targetClass = getTargetClass();
                String targetTypeName = targetClass.getName();
                if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                    LOGGER.info("^Invoking BeforeAdvices for joinpoint instance of target type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n"
                            + "  Advices: \n"
                            + "    {} \n", 
                            targetTypeName,
                            targetClassLoader, 
                            getAccessibleName(), 
                            StringUtils.join(getBeforeAdvices(), e -> e.getClass().getName(), "\n    ") 
                    );

                super.doInvokeBeforeAdvices(targetClassLoader);
            }

            @Override
            protected void doInvokeAfterAdvices(ClassLoader targetClassLoader) throws Throwable {
                Class<?> targetClass = getTargetClass();
                String targetTypeName = targetClass.getName();
                if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                    LOGGER.info("^Invoking AfterAdvices for joinpoint instance of target type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n"
                            + "  Advices: \n"
                            + "    {} \n", 
                            targetTypeName,
                            targetClassLoader, 
                            getAccessibleName(), 
                            StringUtils.join(getAfterAdvices(), e -> e.getClass().getName(), "\n    ") 
                    );

                super.doInvokeAfterAdvices(targetClassLoader);
            }
        }
    }


    /**
     * Proceeding joinpoint passed to {@link io.gemini.api.aop.Advice.Around} advice.
     * Drives the around advice chain by calling each advice's {@code invoke} method in order,
     * and ultimately invokes the original target method via {@link #proceed()}.
     */
    class DefaultProceedingJoinpoint<T> extends AbstractBase<T, Throwable> implements ProceedingJoinpoint<T, Throwable> {

        private int currentAdviceIndex = 0;
        private final List<? extends Advice.Around<T, Throwable>> aroundAdvices;


        @SuppressWarnings("unchecked")
        public DefaultProceedingJoinpoint(Descriptor descriptor, 
                Object targetObject, Object[] arguments) {
            super(descriptor, targetObject, arguments);

            // initialize advisors
            List<? extends Advisor> advisors = descriptor.getAdvisors();
            advisors = CollectionUtils.isEmpty(advisors) ? Collections.emptyList() : advisors;

            List<Advice.Around<T, Throwable>> aroundAdvices = new ArrayList<>();
            for (Iterator<? extends Advisor> iterator = advisors.iterator(); iterator.hasNext(); ) {
                Advisor advisor = iterator.next();
                Class<? extends Advice> adviceClass = advisor.getAdviceClass();
                if (adviceClass == null)
                    iterator.remove();

                boolean isAroundAdvice = Advice.Around.class.isAssignableFrom(adviceClass);
                if (isAroundAdvice == false) {
                    continue;
                }

                Advice.Around<T, Throwable> advice = (Advice.Around<T, Throwable>) advisor.getAdvice();
                if (advice == null)
                    iterator.remove();

                aroundAdvices.add(advice);
            }
            this.aroundAdvices = aroundAdvices;
        }

        @Override
        public T proceed() throws Throwable {
            if (currentAdviceIndex == this.aroundAdvices.size()) {
                try {
                    // TODO: super call
                    return null;
                } catch (Throwable t) {
                    if (t instanceof InterruptedException) 
                        Thread.currentThread().interrupt();

                    Throwables.propagate(t);
                }
            }

            return this.aroundAdvices.get(currentAdviceIndex++).invoke(this);
        }

        @Override
        public T proceed(Object... arguments) throws Throwable {
            this.arguments = arguments;

            return this.proceed();
        }
    }


    /**
     * Runtime dispatcher for around advice chains.
     * Created by the {@link io.gemini.aop.weaver.BootstrapDispatcher.Delegator} at each
     * instrumented joinpoint invocation and drives the {@link DefaultProceedingJoinpoint} chain.
     */
    @BootstrapClassConsumer
    class ProceedingJoinpointDispatcher<T> implements BootstrapDispatcher.Dispatcher<T, Throwable> {

        private static final Logger LOGGER = LoggerFactory.getLogger(ProceedingJoinpointDispatcher.class);

        private final AopContext aopContext;

        private DefaultProceedingJoinpoint<T> joinpoint = null;


        /**
         * Creates a new {@code ProceedingJoinpointDispatcher} for the given around-advice joinpoint.
         *
         * @param descriptor    the joinpoint descriptor
         * @param targetObject  the target object instance
         * @param arguments     the method arguments
         * @param aopContext    the central AOP context
         */
        public ProceedingJoinpointDispatcher(Descriptor descriptor, 
                Object targetObject, Object[] arguments, AopContext aopContext) {
            Assert.notNull(aopContext, "'aopContext' must not be null.");
            this.aopContext = aopContext;

            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

            Class<?> targetClass = descriptor.getTargetClass();
            String targetTypeName = targetClass.getName();
            ClassLoader targetClassLoader = targetClass.getClassLoader();
            try {
                ThreadContext.setContextClassLoader(targetClassLoader);  // set targetClassLoader

                doInitialize(descriptor, targetObject, arguments);
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not create joinpoint instance of target type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n", 
                            targetTypeName, 
                            targetClassLoader, 
                            descriptor.getAccessibleName(), 
                            t
                    );

                Throwables.throwIfRequired(t);
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }

        protected void doInitialize(Descriptor descriptor, Object targetObject, Object[] arguments) {
            joinpoint = new DefaultProceedingJoinpoint<T>(descriptor, targetObject, arguments);
        }


        protected AopContext getAopContext() {
            return aopContext;
        }

        protected Class<?> getTargetClass() {
            return joinpoint.getDescriptor().getTargetClass();
        }

        protected Object getAccessibleName() {
            return joinpoint.getDescriptor().getAccessibleName();
        }

        protected List<? extends Around<T, Throwable>> getAroundAdvice() {
            return joinpoint.aroundAdvices;
        }


        /**
         * Dispatches the around advice chain by invoking {@link DefaultProceedingJoinpoint#proceed()}.
         * Sets the thread context class loader to the target class loader for the duration of the call.
         *
         * @return the result of the around advice chain
         * @throws Throwable if any advice or the target method throws
         */
        @Override
        public T dispatch() throws Throwable {
            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

            String targetTypeName = getTargetClass().getName();
            ClassLoader targetClassLoader = getTargetClass().getClassLoader();
            try {
                ThreadContext.setContextClassLoader(targetClassLoader);  // set targetClassLoader

                return doDispatch();
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("$Could not proceed joinpoint instance of target type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n"
                            + "  Around advices: \n"
                            + "    {} \n", 
                            targetTypeName,
                            targetClassLoader,
                            getAccessibleName(), 
                            StringUtils.join(getAroundAdvice(), e -> e.getClass().getName(), "\n    "),
                            t
                    );

                // throw joinpoint exception
                Throwable rootCause = Throwables.unwrap(t);
                if (rootCause != null)
                    throw rootCause;

                // throw fatal error
                Throwables.throwIfRequired(t);
                return null;
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }

        protected T doDispatch() throws Throwable {
            return joinpoint.proceed();
        }


        /**
         * {@inheritDoc}
         * <p>Returns the arguments from the underlying proceeding joinpoint.</p>
         */
        @Override
        public Object[] getArguments() {
            return joinpoint.getArguments();
        }

        /**
         * {@inheritDoc}
         * <p>Not supported for around advice — throws {@link UnsupportedOperationException}.</p>
         */
        @Override
        public void setReturning(T returning) {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         * <p>Not supported for around advice — throws {@link UnsupportedOperationException}.</p>
         */
        @Override
        public void setThrowing(Throwable throwing) {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         * <p>Not supported for around advice — throws {@link UnsupportedOperationException}.</p>
         */
        @Override
        public boolean hasAdviceReturning() {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         * <p>Not supported for around advice — throws {@link UnsupportedOperationException}.</p>
         */
        @Override
        public T getAdviceReturning() {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         * <p>Not supported for around advice — throws {@link UnsupportedOperationException}.</p>
         */
        @Override
        public boolean hasAdviceThrowing() {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         * <p>Not supported for around advice — throws {@link UnsupportedOperationException}.</p>
         */
        @Override
        public Throwable getAdviceThrowing() {
            throw new UnsupportedOperationException();
        }


        /**
         * Extends {@link ProceedingJoinpointDispatcher} to add per-type diagnostic logging
         * for joinpoint creation and around advice dispatch.
         */
        static class Diagnostic<T> extends ProceedingJoinpointDispatcher<T> {

            private static final Logger LOGGER = LoggerFactory.getLogger(Diagnostic.class);


            public Diagnostic(Descriptor descriptor, 
                    Object targetObject, Object[] arguments, AopContext aopContext) {
                super(descriptor, targetObject, arguments, aopContext);
            }


            @Override
            protected void doInitialize(Descriptor descriptor, Object targetObject, Object[] arguments) {
                Class<?> targetClass = descriptor.getTargetClass();
                String targetTypeName = targetClass.getName();
                if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                    LOGGER.info("^Creating joinpoint instance of target type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  TargetMethod: {} \n", 
                            targetTypeName, 
                            targetClass.getClassLoader(), 
                            descriptor.getAccessibleName()
                    );

                super.doInitialize(descriptor, targetObject, arguments);
            }


            @Override
            protected T doDispatch() throws Throwable {
                String targetTypeName = getTargetClass().getName();
                if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                    LOGGER.info("^Proceeding joinpoint instance of target type '{}', \n"
                            + "  ClassTargetLoader: {} \n"
                            + "  TargetMethod: {} \n"
                            + "  Around advices: \n"
                            + "    {} \n", 
                            targetTypeName,
                            getTargetClass().getClassLoader(),
                            getAccessibleName(), 
                            StringUtils.join(getAroundAdvice(), e -> e.getClass().getName(), "\n    ")
                    );

                return super.doDispatch();
            }
        }
    }
}
