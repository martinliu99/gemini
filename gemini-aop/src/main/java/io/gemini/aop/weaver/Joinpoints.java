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

interface Joinpoints {

    class Descriptor {

        private final Lookup targetLookup;

        private final String accessibleName;
        private final AccessibleObject accessibleObject;

        private final boolean typeInitializer;
        private final boolean constructor;

        private final boolean staticMethod;

        private final boolean voidReturning;

        // refresh at runtime
        private List<? extends Advisor> advisorChain;


        public Descriptor(Lookup thisLookup, String accessibleName, AccessibleObject accessibleObject, 
                List<? extends Advisor> advisorChain) {
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

            this.advisorChain = advisorChain;
        }


        public Lookup getTargetLookup() {
            return targetLookup;
        }

        public Class<?> getTargetClass() {
            return targetLookup.lookupClass();
        }

        public String getAccessibleName() {
            return accessibleName;
        }

        public Constructor<?> getTargetConstructor() {
            return (this.typeInitializer || this.constructor == false) ? null : (Constructor<?>) this.getStaticPart();
        }

        public Method getTargetMethod() {
            return (this.typeInitializer || this.constructor == true) ? null : (Method) this.getStaticPart();
        }

        private AccessibleObject getStaticPart() {
             return this.accessibleObject;
        }

        public boolean isTypeInitializer() {
            return typeInitializer;
        }

        public boolean isStaticMethod() {
            return staticMethod;
        }

        public boolean isConstructor() {
            return constructor;
        }

        public boolean isVoidReturning() {
            return voidReturning;
        }

        public List<? extends Advisor> getAdvisorChain() {
            return advisorChain;
        }
    }


    abstract class AbstractBase<T, E extends Throwable> implements Joinpoint {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractBase.class);

        protected static final Object UNDEFINED_RETURNING = new Object();
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

        Descriptor getDescriptor() {
            return descriptor;
        }

        /**
         * get this class Lookup to access private member
         * @return
         */
        @Override
        public Lookup getTargetLookup() {
            return descriptor.getTargetLookup();
        }

        /**
         * get class information
         * @return
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

        @Override
        public Object getInvocationContext(String key) {
            return this.invocationContext.get(key);
        }

        @Override
        public void setInvocationContext(String key, Object value) {
            this.invocationContext.put(key, value);
        }
    }


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

        @Override
        public T getReturning() {
            if (UNDEFINED_RETURNING == this.returning)
                throw new IllegalStateException("Uninitialized or unsupported returning");

            return returning;
        }

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


        @Override
        public E getThrowing() {
            if (UNDEFINED_THROWING == this.throwing)
                throw new IllegalStateException("Uninitialized throwing");

            return throwing;
        }

        void setThrowing(E throwing) {
            this.throwing = throwing;
        }


        boolean hasAdviceReturning() {
            return this.adviceReturning != UNDEFINED_RETURNING;
        }

        T getAdviceReturning() {
            return this.adviceReturning;
        }

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
                            + "  Method: {} \n"
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


        boolean hasAdviceThrowing() {
            return this.adviceThrowing != UNDEFINED_THROWING;
        }

        E getAdviceThrowing() {
            return this.adviceThrowing;
        }

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
                            + "  Method: {} \n"
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


    @BootstrapClassConsumer
    class MutableJoinpointDispatcher<T, E extends Throwable> implements BootstrapDispatcher.Dispatcher<T, E>, Supplier<Advice> {

        private static final Logger LOGGER = LoggerFactory.getLogger(MutableJoinpointDispatcher.class);


        private final AopContext aopContext;

        private DefaultMutableJoinpoint<T, E> joinpoint = null;

        private boolean dispatchBeforeAdvice = true;
        private List<? extends Advice.Before<T, E>> beforeAdvices = Collections.emptyList();
        private List<? extends Advice.After<T, E>> afterAdvices = Collections.emptyList();

        private Advice currentAdvice;


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
                            + "  Method: {} \n", 
                            targetClass.getName(), 
                            targetClassLoader, 
                            descriptor.getAccessibleName(), t);

                Throwables.throwIfRequired(t);
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }


        @SuppressWarnings("unchecked")
        protected void doInitialize(Descriptor descriptor, Object targetObject, Object[] arguments) {
            // create Joinpoint instance
            joinpoint = new DefaultMutableJoinpoint<T, E>(descriptor, targetObject, arguments, this);

            // initialize advisorChain
            List<? extends Advisor> advisorChain = descriptor.getAdvisorChain();
            advisorChain = CollectionUtils.isEmpty(advisorChain) ? Collections.emptyList() : advisorChain;

            List<Advice.Before<T, E>> beforeAdvices = new ArrayList<>();
            List<Advice.After<T, E>> afterAdvices = new ArrayList<>();
            for (Iterator<? extends Advisor> iterator = advisorChain.iterator(); iterator.hasNext(); ) {
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
                            + "  Method: {} \n"
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

        protected void doInvokeBeforeAdvices(ClassLoader targetClassLoader) throws Throwable {
            for (int index = 0; index < this.beforeAdvices.size(); index++) {
                Before<T, E> advice = this.beforeAdvices.get(index);

                this.currentAdvice = advice;
                try {
                    advice.before(joinpoint);
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("$Could not invoke joinpoint instance of target type '{}', \n"
                                + "  CurrentAdvice: {}", 
                                getTargetClass().getTypeName(),
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

        protected void doInvokeAfterAdvices(ClassLoader targetClassLoader) throws Throwable {
            for (int index = this.afterAdvices.size() - 1; index >= 0; index--) {
                After<T, E> advice = this.afterAdvices.get(index);

                this.currentAdvice = advice;
                try {
                    advice.after(joinpoint);
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("$Could not invoke joinpoint instance of target type '{}', \n"
                                + "  CurrentAdvice: {}", 
                                getTargetClass().getTypeName(),
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
        @Override
        public Advice get() {
            return this.currentAdvice;
        }


        @Override
        public Object[] getArguments() {
            return joinpoint.getArguments();
        }

        @Override
        public void setReturning(T returning) {
            joinpoint.setReturning(returning);
        }

        @Override
        public void setThrowing(E throwing) {
            joinpoint.setThrowing(throwing);
        }


        @Override
        public boolean hasAdviceReturning() {
            return joinpoint.hasAdviceReturning();
        }

        @Override
        public T getAdviceReturning() {
            return joinpoint.getAdviceReturning();
        }

        @Override
        public boolean hasAdviceThrowing() {
            return joinpoint.hasAdviceThrowing();
        }

        @Override
        public E getAdviceThrowing() {
            return joinpoint.getAdviceThrowing();
        }


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
                            + "  Method: {} \n", 
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
                            + "  Method: {} \n"
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
                            + "  Method: {} \n"
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


    class DefaultProceedingJoinpoint<T> extends AbstractBase<T, Throwable> implements ProceedingJoinpoint<T, Throwable> {

        private int currentAdviceIndex = 0;
        private final List<? extends Advice.Around<T, Throwable>> aroundAdvices;


        @SuppressWarnings("unchecked")
        public DefaultProceedingJoinpoint(Descriptor descriptor, 
                Object targetObject, Object[] arguments) {
            super(descriptor, targetObject, arguments);

            // initialize advisorChain
            List<? extends Advisor> advisorChain = descriptor.getAdvisorChain();
            advisorChain = CollectionUtils.isEmpty(advisorChain) ? Collections.emptyList() : advisorChain;

            List<Advice.Around<T, Throwable>> aroundAdvices = new ArrayList<>();
            for (Iterator<? extends Advisor> iterator = advisorChain.iterator(); iterator.hasNext(); ) {
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


    @BootstrapClassConsumer
    class ProceedingJoinpointDispatcher<T> implements BootstrapDispatcher.Dispatcher<T, Throwable> {

        private static final Logger LOGGER = LoggerFactory.getLogger(ProceedingJoinpointDispatcher.class);

        private final AopContext aopContext;

        private DefaultProceedingJoinpoint<T> joinpoint = null;


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
                            + "  Method: {} \n", 
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


        @Override
        public T dispatch() throws Throwable {
            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

            String targetTypeName = getTargetClass().getName();
            ClassLoader targetClassLoader = getTargetClass().getClassLoader();
            try {
                ThreadContext.setContextClassLoader(targetClassLoader);  // set targetClassLoader

                return doDospatch();
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("$Could not proceed joinpoint instance of target type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  Method: {} \n"
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

        protected T doDospatch() throws Throwable {
            return joinpoint.proceed();
        }


        @Override
        public Object[] getArguments() {
            return joinpoint.getArguments();
        }

        @Override
        public void setReturning(T returning) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setThrowing(Throwable throwing) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasAdviceReturning() {
            throw new UnsupportedOperationException();
        }

        @Override
        public T getAdviceReturning() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasAdviceThrowing() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Throwable getAdviceThrowing() {
            throw new UnsupportedOperationException();
        }


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
                            + "  Method: {} \n", 
                            targetTypeName, 
                            targetClass.getClassLoader(), 
                            descriptor.getAccessibleName()
                    );

                super.doInitialize(descriptor, targetObject, arguments);
            }


            @Override
            protected T doDospatch() throws Throwable {
                String targetTypeName = getTargetClass().getName();
                if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                    LOGGER.info("^Proceeding joinpoint instance of target type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  Method: {} \n"
                            + "  Around advices: \n"
                            + "    {} \n", 
                            targetTypeName,
                            getTargetClass().getClassLoader(),
                            getAccessibleName(), 
                            StringUtils.join(getAroundAdvice(), e -> e.getClass().getName(), "\n    ")
                    );

                return doDospatch();
            }
        }
    }
}
