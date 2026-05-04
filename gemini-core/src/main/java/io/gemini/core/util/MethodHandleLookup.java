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
package io.gemini.core.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.PrivilegedAction;

import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.build.AccessControllerPlugin;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.dispatcher.JavaDispatcher;

/**
 * Finds {@link MethodHandle} in refClass.
 * <p>
 * Two concrete implementations handle {@link MethodHandle} lookup including private class member.
 * <ul>
 *   <li>{@link UsingReflection} - under JDK8 or below, use reflection API to set {@link Lookup} under TRUSTED mode. </li>
 *   <li>{@link UsingPrivateLookup} - under JDK9 or high, use {@link MethodHandles#privateLookupIn(Class<?>, Lookup)}
 *   to get private Lookup. </li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public interface MethodHandleLookup {

    /** 
     * Refers to {@link Lookup#findStatic(Class, String, MethodType)}.
     * 
     * @param refClass the class from which the method is accessed
     * @param methodName the name of the method
     * @param methodType the type of the method
     * @return the desired method handle
     * @throws NoSuchMethodException if the method does not exist
     * @throws IllegalAccessException if access checking fails,
     *                                or if the method is not {@code static},
     *                                or if the method's variable arity modifier bit
     *                                is set and {@code asVarargsCollector} fails
     * @exception SecurityException if a security manager is present and it
     *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
     * @throws NullPointerException if any argument is null
     */
    MethodHandle findStatic(Class<?> refClass, String methodName, MethodType methodType)
            throws NoSuchMethodException, IllegalAccessException;


    /**
     * Selects MethodHandleLookup implementation based on current JVM version.
     * 
     * @param scopeType   the type to resolve the access scope for.
     * @param lookup       the caller lookup object
     * @return             a lookup object for the target class, with private access
     * @throws IllegalArgumentException if {@code targetClass} is a primitve type or array class
     * @throws NullPointerException     if {@code targetClass} or {@code caller} is {@code null}
     * @throws IllegalAccessException   if the access check specified above fails
     * @throws SecurityException        if denied by the security manager
     */
    static MethodHandleLookup select(Class<?> scopeType, Lookup lookup) throws IllegalAccessException {
        return ClassFileVersion.ofThisVm().isAtLeast(ClassFileVersion.JAVA_V9)
                ? MethodHandleLookup.UsingPrivateLookup.of(lookup).in(scopeType)
                : MethodHandleLookup.UsingReflection.of(lookup);
    }


    /**
     * Under JDK8 or below, use reflection API to set {@code Lookup} under TRUSTED mode.
     */
    class UsingReflection implements MethodHandleLookup {

        private static final Field MODES_FIELD;


        private final Lookup lookup;

        static {
            Field modesField = null;
            try {
                Class<Lookup> lookupClass = MethodHandles.Lookup.class;
                modesField = lookupClass.getDeclaredField("allowedModes");

                ReflectionUtils.makeAccessible(lookupClass, modesField);
            } catch (NoSuchFieldException | SecurityException e) {
                throw new IllegalStateException("Could not get 'allowedModes' field.", e);
            }
            MODES_FIELD = modesField;
        }


        public UsingReflection(Lookup lookup) {
            this.lookup = lookup;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public MethodHandle findStatic(Class<?> refClass, String methodName, MethodType methodType) 
                throws NoSuchMethodException, IllegalAccessException {
            return lookup.findStatic(refClass, methodName, methodType);
        }


        /**
         * Create {link MethodHandleLookup} instance using a method handle lookup.
         * 
         * @param lookup    the {@code java.lang.invoke.MethodHandles$Lookup} instance to use.
         * @return  An appropriate {link MethodHandleLookup}.
         * @throws IllegalAccessException if the access check specified above fails
         */
        public static UsingReflection of(Lookup lookup) throws IllegalAccessException {
            try {
                MODES_FIELD.set(lookup, -1);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw e;
            }

            return new UsingReflection(lookup);
        }
    }


    /**
     * Under JDK9 or high, use {@code MethodHandles#privateLookupIn(Class<?>, Lookup)} to get private Lookup.
     */
    class UsingPrivateLookup implements MethodHandleLookup {

        private static final MethodHandlesDispatcher METHOD_HANDLES = doPrivileged(JavaDispatcher.of(MethodHandlesDispatcher.class));
        private static final MethodHandlesDispatcher.LookupDispatcher METHOD_HANDLES_LOOKUP = doPrivileged(JavaDispatcher.of(MethodHandlesDispatcher.LookupDispatcher.class));


        private final Lookup lookup;

        public UsingPrivateLookup(Lookup lookup) {
            this.lookup = lookup;
        }

        /**
         * A proxy for {@code java.security.AccessController#doPrivileged} that is activated if available.
         *
         * @param action The action to execute from a privileged context.
         * @param <T>    The type of the action's resolved value.
         * @return The action's resolved value.
         */
        @AccessControllerPlugin.Enhance
        private static <T> T doPrivileged(PrivilegedAction<T> action) {
            return action.run();
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        public MethodHandle findStatic(Class<?> refClass, String methodName, MethodType methodType) 
                throws NoSuchMethodException, IllegalAccessException {
            return METHOD_HANDLES_LOOKUP.findStatic(lookup, refClass, methodName, methodType);
        }


        /**
         * Create {link MethodHandleLookup} instance using a method handle lookup.
         * 
         * @param lookup    the {@code java.lang.invoke.MethodHandles$Lookup} instance to use.
         * @return  An appropriate {link MethodHandleLookup}.
         * @throws IllegalAccessException if the access check specified above fails
         */
        public static UsingPrivateLookup of(Lookup lookup) throws IllegalAccessException {
            return new UsingPrivateLookup(lookup);
        }

        /**
         * Resolves {link MethodHandleLookup} to use the supplied type's scope.
         *
         * @param scopeType      the type to resolve the access scope for.
         * @return  an new {link MethodHandleLookup} with the specified type scope..
         */
        public UsingPrivateLookup in(Class<?> scopeType) {
            try {
                return new UsingPrivateLookup(METHOD_HANDLES.privateLookupIn(scopeType, lookup));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot access " + scopeType.getName() + " from " + lookup, exception);
            }
        }


        /**
         * A dispatcher for {@code java.lang.invoke.MethodHandles}.
         * Refers to ByteBuddy {@link ClassInjector.UsingLookup.MethodHandles}
         */
        @JavaDispatcher.Proxied("java.lang.invoke.MethodHandles")
        public interface MethodHandlesDispatcher {

            /**
             * Resolves the supplied lookup instance's access scope for the supplied type.
             *
             * @param scopeType the type to resolve the scope for.
             * @param lookup the lookup to resolve.
             * @return An appropriate lookup instance.
             * @throws IllegalAccessException If an illegal access occurs.
             */
            @JavaDispatcher.IsStatic
            Lookup privateLookupIn(Class<?> scopeType, Lookup lookup) throws IllegalAccessException;


            /**
             * A dispatcher for {@code java.lang.invoke.MethodHandles$Lookup}.
             */
            @JavaDispatcher.Proxied("java.lang.invoke.MethodHandles$Lookup")
            public interface LookupDispatcher {

                /**
                 * Refers to {@link Lookup#findStatic(Class, String, MethodType)}.
                 * 
                 * @param lookup the {@code java.lang.invoke.MethodHandles$Lookup} instance to use.
                 * @param refClass the class from which the method is accessed
                 * @param methodName the name of the method
                 * @param methodType the type of the method
                 * @return the desired method handle
                 * @throws NoSuchMethodException if the method does not exist
                 * @throws IllegalAccessException if access checking fails,
                 *                                or if the method is not {@code static},
                 *                                or if the method's variable arity modifier bit
                 *                                is set and {@code asVarargsCollector} fails
                 * @exception SecurityException if a security manager is present and it
                 *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
                 * @throws NullPointerException if any argument is null
                 *
                 */
                MethodHandle findStatic(Lookup lookup, Class<?> refClass, String methodName, MethodType methodType)
                        throws NoSuchMethodException, IllegalAccessException ;

                /**
                 * Refers to {@link Lookup#findVirtual(Class, String, MethodType)}.
                 * 
                 * @param lookup the {@code java.lang.invoke.MethodHandles$Lookup} instance to use.
                 * @param refClass the class or interface from which the method is accessed
                 * @param methodName the name of the method
                 * @param methodType the type of the method, with the receiver argument omitted
                 * @return the desired method handle
                 * @throws NoSuchMethodException if the method does not exist
                 * @throws IllegalAccessException if access checking fails,
                 *                                or if the method is {@code static}
                 *                                or if the method's variable arity modifier bit
                 *                                is set and {@code asVarargsCollector} fails
                 * @exception SecurityException if a security manager is present and it
                 *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
                 * @throws NullPointerException if any argument is null
                 */
                MethodHandle findVirtual(Lookup lookup, Class<?> refClass, String methodName, MethodType methodType)
                        throws NoSuchMethodException, IllegalAccessException ;

                /**
                 * Refers to {@link Lookup#findSpecial(Class, String, MethodType, Class)}.
                 * 
                 * @param lookup the {@code java.lang.invoke.MethodHandles$Lookup} instance to use.
                 * @param refClass the class or interface from which the method is accessed
                 * @param methodName the name of the method (which must not be "&lt;init&gt;")
                 * @param methodType the type of the method, with the receiver argument omitted
                 * @param specialCaller the proposed calling class to perform the {@code invokespecial}
                 * @return the desired method handle
                 * @throws NoSuchMethodException if the method does not exist
                 * @throws IllegalAccessException if access checking fails
                 *                                or if the method's variable arity modifier bit
                 *                                is set and {@code asVarargsCollector} fails
                 * @exception SecurityException if a security manager is present and it
                 *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
                 * @throws NullPointerException if any argument is null
                 */
                MethodHandle findSpecial(Lookup lookup, Class<?> refClass, String methodName, MethodType methodType, 
                        Class<?> specialCaller) throws NoSuchMethodException, IllegalAccessException;

                /**
                 * Refers to {@link Lookup#findConstructor(Class, MethodType)}.
                 * 
                 * @param lookup the {@code java.lang.invoke.MethodHandles$Lookup} instance to use.
                 * @param refClass the class or interface from which the method is accessed
                 * @param methodType the type of the method, with the receiver argument omitted, and a void return type
                 * @return the desired method handle
                 * @throws NoSuchMethodException if the constructor does not exist
                 * @throws IllegalAccessException if access checking fails
                 *                                or if the method's variable arity modifier bit
                 *                                is set and {@code asVarargsCollector} fails
                 * @exception SecurityException if a security manager is present and it
                 *                              <a href="MethodHandles.Lookup.html#secmgr">refuses access</a>
                 * @throws NullPointerException if any argument is null
                 */
                MethodHandle findConstructor(Lookup lookup, Class<?> refClass, MethodType methodType)
                        throws NoSuchMethodException, IllegalAccessException ;
            }
        }
    }
}
