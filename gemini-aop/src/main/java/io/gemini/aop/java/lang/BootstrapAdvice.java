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
package io.gemini.aop.java.lang;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
@BootstrapClassProvider
public interface BootstrapAdvice {


    @BootstrapClassProvider
    interface Dispatcher<T, E extends Throwable> {

        static final Object[] ARGUMENTS = new Object[0];


        T dispatch() throws E;

        Object[] getArguments();

        void setReturning(T returning);

        void setThrowing(E throwing);


        boolean hasAdviceReturning();

        T getAdviceReturning();

        boolean hasAdviceThrowing();

        E getAdviceThrowing();
    }


    @BootstrapClassProvider
    interface Factory {

        /**
         * Create {@link Descriptor} instance to hold joinpoint metadata.
         * 
         * @param lookup
         * @param arguments
         * @return
         */
        Object createDescriptor(MethodHandles.Lookup lookup, Object... arguments);


        /**
         * Get INDY CallSite to create {@link Descriptor} instance to hold joinpoint metadata.
         * 
         * @param lookup
         * @param bsmMethodName
         * @param bsmMethodType
         * @param arguments
         * @return
         */
        CallSite createDescriptorCallSite(MethodHandles.Lookup lookup, String bsmMethodName, MethodType bsmMethodType, Object... arguments);


        /**
         * 
         * @param <T>
         * @param <E>
         * @param descriptor
         * @param thisObject
         * @param arguments
         * @return
         */
        <T, E extends Throwable> Dispatcher<T, E> dispacther(Object descriptor, Object thisObject, Object[] arguments);

    }


    @BootstrapClassProvider
    public class Bridger {

        private static final Method CREATE_DESCRIPTOR_METHOD;
        private static final Method CREATE_DESCRIPTOR_INDY_BSM;

        private static Factory FACTORY;

        static {
            Method bsmMethod = null;
            try {
                 bsmMethod = Bridger.class.getMethod("createDescriptorCallSite", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class);
                 bsmMethod.setAccessible(true);
            } catch (Exception e) {
                System.err.println("Could not fetch BootstrapAdvice.Bridger#createDescriptorCallSite() method. \n"
                        + "  Error reason: " + e.getMessage());
            }
            CREATE_DESCRIPTOR_INDY_BSM = bsmMethod;


            Method regularMethod = null;
            try {
                regularMethod = BootstrapAdvice.Bridger.class.getMethod("createDescriptor", Lookup.class, Object[].class);
                regularMethod.setAccessible(true);
            } catch (Exception e) {
                System.err.println("Could not fetch BootstrapAdvice.Bridger#createDescriptor() method. \n"
                        + "  Error reason: " + e.getMessage());
            }
            CREATE_DESCRIPTOR_METHOD = regularMethod;
        }


        public static void setFactory(Factory factory) {
            if (FACTORY != null) {
                System.err.println("BootstrapAdvice.Bridger.FACTORY already initialized with " + FACTORY);
                return;
            }

            if (factory == null) {
                throw new IllegalArgumentException("BootstrapAdvice.Bridger.FACTORY must not be null.");
            }

            FACTORY = factory;
        }


        public static Method createDescriptorMethod() {
            return CREATE_DESCRIPTOR_METHOD;
        }

        public static Object createDescriptor(MethodHandles.Lookup lookup, Object... args) {
            return FACTORY.createDescriptor(lookup, args);
        }


        public static Method createDescriptorIndyBSM() {
            return CREATE_DESCRIPTOR_INDY_BSM;
        }

        public static CallSite createDescriptorCallSite(MethodHandles.Lookup lookup, 
                String bsmMethodName,
                MethodType bsmMethodType,
                Object... args) {
            return FACTORY.createDescriptorCallSite(lookup, bsmMethodName, bsmMethodType, args);
        }


        public static <T, E extends Throwable> Dispatcher<T, E> dispacther(Object descriptor, Object thisObject, Object[] arguments) {
            return FACTORY.dispacther(descriptor, thisObject, arguments);
        }
    }
}
