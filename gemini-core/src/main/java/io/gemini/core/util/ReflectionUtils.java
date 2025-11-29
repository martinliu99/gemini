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

public abstract class ReflectionUtils {

    private static Set<String> BUILTIN_METHOD_NAMES;

    static {
        BUILTIN_METHOD_NAMES = new HashSet<>();

        for (Method method : Annotation.class.getDeclaredMethods()) {
            BUILTIN_METHOD_NAMES.add( method.getName() );
        }
    }


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
 
    public static void makeAccessible(Class<?> clazz, Executable executable) {
        if ((!Modifier.isPublic(executable.getModifiers()) ||
                !Modifier.isPublic(clazz.getModifiers())) && !executable.isAccessible()) {
            executable.setAccessible(true);
        }
    }

    public static void makeAccessible(Class<?> clazz, Field field) {
        if ((!Modifier.isPublic(field.getModifiers()) ||
                !Modifier.isPublic(clazz.getModifiers()) ||
                Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
            field.setAccessible(true);
        }
    }


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

    public static Map<String, Object> getAttributeValues(Annotation annotation) 
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Assert.notNull(annotation, "'annotation' must not be null.");

        Class<? extends Annotation> annotationClass = annotation.getClass();
        List<Method> attributeMethods = ReflectionUtils.getAttributeMethods(annotationClass);
        Map<String, Object> attributeValues = new LinkedHashMap<>(attributeMethods.size());
        for (Method method : attributeMethods) {
            makeAccessible(annotationClass, method);

            attributeValues.put( method.getName(), method.invoke(annotation) );
        }

        return attributeValues;
    }
}
