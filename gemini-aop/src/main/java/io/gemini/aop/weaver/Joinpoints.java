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

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.Aspect;
import io.gemini.aop.java.lang.BootstrapAdvice;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
import io.gemini.api.aspect.Advice;
import io.gemini.api.aspect.Advice.After;
import io.gemini.api.aspect.Advice.Around;
import io.gemini.api.aspect.Advice.Before;
import io.gemini.api.aspect.Joinpoint;
import io.gemini.api.aspect.Joinpoint.MutableJoinpoint;
import io.gemini.api.aspect.Joinpoint.ProceedingJoinpoint;
import io.gemini.api.classloader.ThreadContext;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import net.bytebuddy.description.method.MethodDescription;

interface Joinpoints {

    class Descriptor {

        private final Lookup thisLookup;

        private final String accessibleName;
        private final AccessibleObject accessibleObject;

        private final boolean isTypeInitializer;
        private final boolean isConstructor;

        private final boolean isStatic;

        private final boolean isVoidReturning;

        // refresh at runtime
        private List<? extends Aspect> aspectChain;


        public Descriptor(Lookup thisLookup, String accessibleName, AccessibleObject accessibleObject, 
                List<? extends Aspect> aspectChain) {
            this.thisLookup = thisLookup;

            this.accessibleName = accessibleName;
            this.accessibleObject = accessibleObject;

            if(accessibleObject == null) {
                this.isTypeInitializer = true;

                this.isConstructor = false;
                this.isStatic = true;
                this.isVoidReturning = true;
            } else {
                this.isTypeInitializer = false;

                if(accessibleObject instanceof Constructor) {
                    this.isConstructor = true;
                    this.isStatic = false;
                    this.isVoidReturning = true;
                } else {
                    this.isConstructor = false;

                    Method method = (Method) accessibleObject;
                    this.isStatic = Modifier.isStatic(method.getModifiers());
                    this.isVoidReturning = method.getReturnType() == void.class;
                }
            }

            this.aspectChain = aspectChain;
        }


        public Lookup getThisLookup() {
            return thisLookup;
        }

        public Class<?> getThisClass() {
            return thisLookup.lookupClass();
        }

        public String getAccessibleName() {
            return accessibleName;
        }

        public Constructor<?> getConstructor() {
            return (this.isTypeInitializer || this.isConstructor == false) ? null : (Constructor<?>) this.getStaticPart();
        }

        public Method getMethod() {
            return (this.isTypeInitializer || this.isConstructor == true) ? null : (Method) this.getStaticPart();
        }

        private AccessibleObject getStaticPart() {
             return this.accessibleObject;
        }

        public boolean isTypeInitializer() {
            return isTypeInitializer;
        }

        public boolean isStatic() {
            return isStatic;
        }

        public boolean isConstructor() {
            return isConstructor;
        }

        public boolean isVoidReturning() {
            return isVoidReturning;
        }

        public List<? extends Aspect> getAspectChain() {
            return aspectChain;
        }
    }


    abstract class AbstractBase<T, E extends Throwable> implements Joinpoint {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractBase.class);

        protected static final Object UNDEFINED_RETURNING = new Object();
        protected static final Throwable UNDEFINED_THROWING = new Throwable();


        protected Descriptor descriptor;

        protected Object thisObject;
        protected final boolean lazyInitializeThis;

        protected Object[] arguments;

        protected final Map<String, Object> invocationContext;


        AbstractBase(Descriptor descriptor, Object thisObject, Object[] arguments) {
            this.descriptor = descriptor;

            if(descriptor.isConstructor() == true) {
                this.lazyInitializeThis = true;

                // ignore input thisObject
                this.thisObject = UNDEFINED_RETURNING;
            } else {
                this.lazyInitializeThis = false;

                if(descriptor.isStatic == false) {
                    Assert.notNull(thisObject, "'thisObject' must not be null.");

                    this.thisObject = thisObject;
                } else {
                    this.thisObject = null;
                }
            }

            if(arguments == null || arguments.length == 0) {
                this.arguments = new Object[0];
            } else {
                Object[] args = new Object[arguments.length];
                System.arraycopy(arguments, 0, args, 0, arguments.length);
                this.arguments = args;
            }

            this.invocationContext = new HashMap<>();
        }

        Descriptor getDescriptor() {
            return descriptor;
        }

        /**
         * get this class Lookup to access private member
         * @return
         */
        @Override
        public Lookup getThisLookup() {
            return descriptor.getThisLookup();
        }

        /**
         * get class information
         * @return
         */
        @Override
        public Class<?> getThisClass() {
            return descriptor.getThisClass();
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
        public Object getThisObject() {
            return (UNDEFINED_RETURNING == thisObject) ? null : this.thisObject;
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

        @SuppressWarnings("unchecked")
        private T returning = (T) UNDEFINED_RETURNING;
        @SuppressWarnings("unchecked")
        private E throwing = (E) UNDEFINED_THROWING;

        @SuppressWarnings("unchecked")
        private T adviceReturning = (T) UNDEFINED_RETURNING;
        @SuppressWarnings("unchecked")
        private E adviceThrowing = (E) UNDEFINED_THROWING;


        public DefaultMutableJoinpoint(Descriptor descriptor, Object thisObject, Object[] arguments) {
            super(descriptor, thisObject, arguments);
        }

        @Override
        public T getReturning() {
            if(UNDEFINED_RETURNING == this.returning)
                throw new IllegalStateException("returning is not initialized or unsupported!");

            return returning;
        }

        void setReturning(T returning) {
            if(getDescriptor().isTypeInitializer()) {
                this.returning = null;
            } else if(getDescriptor().isConstructor()) {
                this.thisObject = returning;
                this.returning = returning;
            } else {
                this.returning = returning;
            }
        }


        @Override
        public E getThrowing() {
            if(UNDEFINED_THROWING == this.throwing)
                throw new IllegalStateException("throwing is not initialized!");

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
            if(getDescriptor().isVoidReturning) {
                this.adviceReturning = null;
                return;
            }

            Class<?> returnType = getDescriptor().getMethod().getReturnType();
            if(returning == null || ClassUtils.isAssignableFrom(returnType, returning.getClass()) == false) {
                LOGGER.warn("Ignored advice returning '{}' which must be instance of {}.\n", returning, returnType);
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
            if(throwing == null) {
                LOGGER.warn("Ignored null advice throwing.\n");
                return;
            }

            Descriptor descriptor = getDescriptor();
            Class<?>[] exceptionTypes = descriptor.isTypeInitializer() 
                    ? null 
                    : descriptor.isConstructor() 
                        ? descriptor.getConstructor().getExceptionTypes() 
                        : descriptor.getMethod().getExceptionTypes();
            Class<? extends Throwable> throwingType = throwing.getClass();

            boolean assignable = false;
            if(exceptionTypes != null && exceptionTypes.length != 0) {
                for(Class<?> exceptionClass : exceptionTypes) {
                    if(exceptionClass.isAssignableFrom(throwingType)) {
                        assignable = true;
                        break;
                    }
                }
            }

            if(assignable == false && RuntimeException.class.isAssignableFrom(throwingType) == false) {
                String targetMethod = descriptor.isTypeInitializer 
                        ? MethodDescription.TYPE_INITIALIZER_INTERNAL_NAME
                        : descriptor.isConstructor() 
                            ? descriptor.getConstructor().toString() : descriptor.getMethod().toString();

                LOGGER.warn("Ignored advice throwing '{}' which must be instance of RuntimeException or exception types declared in signature '{}'.\n",
                        throwing, targetMethod);
                return;
            }

            this.adviceThrowing = throwing;
        }
    }



    @BootstrapClassConsumer
    class MutableJoinpointDispatcher<T, E extends Throwable> implements BootstrapAdvice.Dispatcher<T, E> {

        private static final Logger LOGGER = LoggerFactory.getLogger(MutableJoinpointDispatcher.class);

        private final AopContext aopContext;

        private DefaultMutableJoinpoint<T, E> joinpoint = null;

        private boolean dispatchBeforeAdvice = true;
        private List<? extends Advice.Before<T, E>> beforeAdvices = Collections.emptyList();
        private List<? extends Advice.After<T, E>> afterAdvices = Collections.emptyList();


        public MutableJoinpointDispatcher(Descriptor descriptor, Object thisObject, Object[] arguments, AopContext aopContext) {
            Assert.notNull(aopContext, "'aopContext' must not be null.");
            this.aopContext = aopContext;

            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

            Class<?> thisClass = descriptor.getThisClass();
            ClassLoader joinpointClassLoader = thisClass.getClassLoader();
            try {
                ThreadContext.setContextClassLoader(joinpointClassLoader);  // set joinpointClassLoader

                if(aopContext.isDiagnosticClass(thisClass.getName())) {
                    LOGGER.info("^Creating joinpoint '{}' loaded by ClassLoader '{}'.", descriptor.getAccessibleName(), joinpointClassLoader);
                }

                joinpoint = new DefaultMutableJoinpoint<T, E>(descriptor, thisObject, arguments);

                initialize(descriptor);
            } catch(Throwable t) {
                LOGGER.warn("Failed to create joinpoint '{}' loaded by ClassLoader '{}'.", descriptor.getAccessibleName(), joinpointClassLoader, t);
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }


        @SuppressWarnings("unchecked")
        protected void initialize(Descriptor descriptor) {
            // initialize aspectChain
            List<? extends Aspect> aspectChain = descriptor.getAspectChain();
            aspectChain = CollectionUtils.isEmpty(aspectChain) ? Collections.emptyList() : aspectChain;

            List<Advice.Before<T, E>> beforeAdvices = new ArrayList<>();
            List<Advice.After<T, E>> afterAdvices = new ArrayList<>();
            for(Aspect aspect : aspectChain) {
                Class<? extends Advice> adviceClass = aspect.getAdviceClass();
                boolean isBeforeAdvice = Advice.Before.class.isAssignableFrom(adviceClass);
                boolean isAfterAdvice = Advice.After.class.isAssignableFrom(adviceClass);

                if(isBeforeAdvice == false && isAfterAdvice == false) {
                    continue;
                }

                Advice advice = aspect.getAdvice();

                if(isBeforeAdvice == true && isAfterAdvice == true) {
                    beforeAdvices.add( (Advice.Before<T, E>) advice );
                    afterAdvices.add( (Advice.After<T, E>) advice );
                } else if(isBeforeAdvice == true && isAfterAdvice == false) {
                    beforeAdvices.add( (Advice.Before<T, E>) advice );
                } else if(isBeforeAdvice == false && isAfterAdvice == true) {
                    afterAdvices.add( (Advice.After<T, E>) advice );
                }
            }

            this.beforeAdvices = beforeAdvices;
            this.afterAdvices = afterAdvices;
        }


        @Override
        public T dispatch() throws E {
            if(dispatchBeforeAdvice == true && this.beforeAdvices.size() == 0) {
                dispatchBeforeAdvice = false;
                return null;
            }
            if(dispatchBeforeAdvice == false && this.afterAdvices.size() == 0) {
                return null;
            }

            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

            ClassLoader joinpointClassLoader = getThisClass().getClassLoader();
            String adviceMessage = dispatchBeforeAdvice ? "BeforeAdvices" : "AfterAdvices";
            try {
                ThreadContext.setContextClassLoader(joinpointClassLoader);  // set joinpointClassLoader

                if(aopContext.isDiagnosticClass(getThisClass().getName()))
                    LOGGER.info("^Invoking joinpoint '{}'. \n  ClassLoader: {} \n"
                            + "  {}: {} ", 
                            getAccessibleName(), joinpointClassLoader, adviceMessage,
                            (dispatchBeforeAdvice ? this.beforeAdvices : this.afterAdvices).stream().map( e -> e.getClass().getName() ).collect(Collectors.joining("\n    ", "\n    ", "\n") ) 
                    );

                if(dispatchBeforeAdvice) {
                    this.invokeBeforeAdvices(joinpointClassLoader);
                } else {
                    this.invokeAfterAdvices(joinpointClassLoader);
                }

                return null;
            } catch(Throwable t) {
                LOGGER.warn("$Failed to invoke joinpoint '{}'. \n  ClassLoader: {} \n"
                        + "  {}: {} ", 
                        getAccessibleName(), joinpointClassLoader, adviceMessage, 
                        (dispatchBeforeAdvice ? this.beforeAdvices : this.afterAdvices).stream().map( e -> e.getClass().getName() ).collect(Collectors.joining("\n    ", "\n    ", "\n") ),
                        t
                );

                return null;
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }

        private Class<?> getThisClass() {
            return joinpoint.getDescriptor().getThisClass();
        }

        /**
         * @return
         */
        private Object getAccessibleName() {
            return joinpoint.getDescriptor().getAccessibleName();
        }

        private void invokeBeforeAdvices(ClassLoader joinpointClassLoader) throws Throwable {
            for(int index = 0; index < this.beforeAdvices.size(); index++) {
                Before<T, E> advice = this.beforeAdvices.get(index);
                try {
                    advice.before(joinpoint);
                } catch(Throwable t) {
                    LOGGER.warn("$Failed to invoke joinpoint '{}'. \n  ClassLoader: {} \n  exceptional advice: {}", 
                            getAccessibleName(), joinpointClassLoader, advice, t);
                }

                if(hasAdviceReturning() || hasAdviceThrowing())
                    break;
            }
            this.dispatchBeforeAdvice = false;
        }

        private void invokeAfterAdvices(ClassLoader joinpointClassLoader) throws Throwable {
            for(int index = this.afterAdvices.size() - 1; index >= 0; index--) {
                After<T, E> advice = this.afterAdvices.get(index);
                try {
                    advice.after(joinpoint);
                } catch(Throwable t) {
                    LOGGER.warn("$Failed to invoke joinpoint '{}'. \n  ClassLoader: {} \n  exceptional advice: {}", 
                            getAccessibleName(), joinpointClassLoader, advice, t);
                }

                if(hasAdviceReturning() || hasAdviceThrowing())
                    break;
            }
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
    }


        class DefaultProceedingJoinpoint<T, E extends Throwable> extends AbstractBase<T, E> implements ProceedingJoinpoint<T, E> {

            private int currentAdviceIndex = 0;
            private final List<? extends Advice.Around<T, E>> aroundAdvices;


            @SuppressWarnings("unchecked")
            public DefaultProceedingJoinpoint(Descriptor descriptor, Object thisObject, Object[] arguments) {
                super(descriptor, thisObject, arguments);

                // initialize aspectChain
                List<? extends Aspect> aspectChain = descriptor.getAspectChain();
                aspectChain = CollectionUtils.isEmpty(aspectChain) ? Collections.emptyList() : aspectChain;

                List<Advice.Around<T, E>> aroundAdvices = new ArrayList<>();
                for(Aspect aspect : aspectChain) {
                    Class<? extends Advice> adviceClass = aspect.getAdviceClass();
                    boolean isAroundAdvice = Advice.Around.class.isAssignableFrom(adviceClass);

                    if(isAroundAdvice == false) {
                        continue;
                    }

                    Advice.Around<T, E> advice = (Advice.Around<T, E>) aspect.getAdvice();
                    aroundAdvices.add(advice);
                }
                this.aroundAdvices = aroundAdvices;
            }

            @Override
            public T proceed() throws E {
                if(currentAdviceIndex == this.aroundAdvices.size()) {
                    // TODO: super call
                    return null;
                }

                return this.aroundAdvices.get(currentAdviceIndex++).invoke(this);
            }

            @Override
            public T proceed(Object... arguments) throws E {
                this.arguments = arguments;

                return this.proceed();
            }
        }


        @BootstrapClassConsumer
        class ProceedingJoinpointDispatcher<T, E extends Throwable> implements BootstrapAdvice.Dispatcher<T, E> {

            private static final Logger LOGGER = LoggerFactory.getLogger(ProceedingJoinpointDispatcher.class);

            private final AopContext aopContext;

            private DefaultProceedingJoinpoint<T, E> joinpoint = null;


            public ProceedingJoinpointDispatcher(Descriptor descriptor, Object thisObject, Object[] arguments, AopContext aopContext) {
                Assert.notNull(aopContext, "'aopContext' must not be null.");
                this.aopContext = aopContext;

                ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

                Class<?> thisClass = descriptor.getThisClass();
                ClassLoader joinpointClassLoader = thisClass.getClassLoader();
                try {
                    ThreadContext.setContextClassLoader(joinpointClassLoader);  // set joinpointClassLoader

                    if(aopContext.isDiagnosticClass(thisClass.getName())) {
                        LOGGER.info("^Creating joinpoint '{}' loaded by ClassLoader '{}'.", descriptor.getAccessibleName(), joinpointClassLoader);
                    }

                    joinpoint = new DefaultProceedingJoinpoint<T, E>(descriptor, thisObject, arguments);
                } catch(Throwable t) {
                    LOGGER.warn("Failed to create joinpoint '{}' loaded by ClassLoader '{}'.", descriptor.getAccessibleName(), joinpointClassLoader, t);
                } finally {
                    ThreadContext.setContextClassLoader(existingClassLoader);
                }
            }


            @Override
            public T dispatch() throws E {
                ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

                ClassLoader joinpointClassLoader = getThisClass().getClassLoader();
                try {
                    ThreadContext.setContextClassLoader(joinpointClassLoader);  // set joinpointClassLoader

                    if(aopContext.isDiagnosticClass(getThisClass().getName())) {
                        LOGGER.info("^Proceeding joinpoint '{}'. \n  ClassLoader: {} \n"
                                + "  Around advices: {} ", 
                                getAccessibleName(), joinpointClassLoader,
                                getAroundAdvice().stream().map( e -> e.getClass().getName() ).collect(Collectors.joining("\n    ", "\n    ", "\n") )
                        );
                    }

                    return joinpoint.proceed();
                } catch(Throwable t) {
                    LOGGER.warn("$Failed to proceed joinpoint '{}'. \n  ClassLoader: {} \n"
                            + "  Around advices: {} ", 
                            getAccessibleName(), joinpointClassLoader,
                            getAroundAdvice().stream().map( e -> e.getClass().getName() ).collect(Collectors.joining("\n    ", "\n    ", "\n") ),
                            t
                    );

                    throw t;
                } finally {
                    ThreadContext.setContextClassLoader(existingClassLoader);
                }
            }

            private Class<?> getThisClass() {
                return joinpoint.getDescriptor().getThisClass();
            }

            /**
             * @return
             */
            private Object getAccessibleName() {
                return joinpoint.getDescriptor().getAccessibleName();
            }

            private List<? extends Around<T, E>> getAroundAdvice() {
                return joinpoint.aroundAdvices;
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
            public void setThrowing(E throwing) {
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
            public E getAdviceThrowing() {
                throw new UnsupportedOperationException();
            }
        }
    
}
