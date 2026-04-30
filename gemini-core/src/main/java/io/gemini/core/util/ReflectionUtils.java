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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for Java reflection operations used throughout the AOP framework.
 * <p>
 * Provides helpers to:
 * <ul>
 *   <li>Find a no-arg constructor on a class</li>
 *   <li>Make constructors, methods, and fields accessible</li>
 *   <li>Enumerate annotation attribute methods (excluding built-in {@link java.lang.annotation.Annotation} methods)</li>
 *   <li>Read all attribute values from an annotation instance</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public abstract class ReflectionUtils {

    private static Set<String> BUILTIN_METHOD_NAMES;

    static {
        BUILTIN_METHOD_NAMES = new HashSet<>();

        for (Method method : Annotation.class.getDeclaredMethods()) {
            BUILTIN_METHOD_NAMES.add( method.getName() );
        }
    }


    /**
     * Returns the no-arg constructor of the given type, or {@code null} if none exists
     * or the type is an interface or abstract class.
     *
     * @param type the class to inspect (may be {@code null})
     * @return the no-arg constructor, or {@code null}
     */
    public static Constructor<?> getDefaultConstructor(Class<?> type) {
        if (type == null) 
            return null;

        if (type.isInterface() || Modifier.isAbstract(type.getModifiers()) ) 
            return null;

        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0)
                return constructor;
        }

        return null;
    }
 
    /**
     * Makes the given constructor or method accessible if it is not already public.
     *
     * @param clazz      the declaring class
     * @param executable the constructor or method to make accessible
     */
    public static void makeAccessible(Class<?> clazz, Executable executable) {
        if ((!Modifier.isPublic(executable.getModifiers()) ||
                !Modifier.isPublic(clazz.getModifiers())) && !executable.isAccessible()) {
            executable.setAccessible(true);
        }
    }

    /**
     * Makes the given field accessible if it is not already public or is final.
     *
     * @param clazz the declaring class
     * @param field the field to make accessible
     */
    public static void makeAccessible(Class<?> clazz, Field field) {
        if ((!Modifier.isPublic(field.getModifiers()) ||
                !Modifier.isPublic(clazz.getModifiers()) ||
                Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
            field.setAccessible(true);
        }
    }


    /**
     * Returns all attribute methods of the given annotation class, excluding built-in
     * {@link java.lang.annotation.Annotation} methods ({@code equals}, {@code hashCode}, etc.).
     *
     * @param annotationClass the annotation class to inspect
     * @return list of attribute methods
     */
    public static List<Method> getAttributeMethods(Class<? extends Annotation> annotationClass) {
        Assert.notNull(annotationClass, "'annotationClass' must not be null.");

        Method[] methods = annotationClass.getDeclaredMethods();
        List<Method> attributeMethods = new ArrayList<>(methods.length);
        for (Method method : methods) {
            String methodName = method.getName();
            if (BUILTIN_METHOD_NAMES.contains(methodName)) 
                continue;

            if (method.getReturnType() == void.class || method.getParameterCount() > 0)
                continue;

            attributeMethods.add(method);
        }

        return attributeMethods;
    }

    /**
     * Reads all attribute values from the given annotation instance.
     *
     * @param annotation the annotation to read
     * @return a map from attribute name to attribute value
     * @throws IllegalAccessException    if an attribute method is inaccessible
     * @throws IllegalArgumentException  if an attribute method cannot be invoked
     * @throws InvocationTargetException if an attribute method throws an exception
     */
    public static Map<String, Object> getAttributeValues(Annotation annotation) 
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Assert.notNull(annotation, "'annotation' must not be null.");

        Class<? extends Annotation> annotationClass = annotation.annotationType();
        List<Method> attributeMethods = ReflectionUtils.getAttributeMethods(annotationClass);
        Map<String, Object> attributeValues = new LinkedHashMap<>(attributeMethods.size());
        for (Method method : attributeMethods) {
            makeAccessible(annotationClass, method);

            attributeValues.put( method.getName(), method.invoke(annotation) );
        }

        return attributeValues;
    }
}
