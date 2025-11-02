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

import java.lang.reflect.AccessibleObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;

public abstract class ClassUtils {

    /**
     * The file extension for a Java class file.
     */
    public static final String CLASS_FILE_EXTENSION = ".class";

    /** The package separator character: {@code '.'}. */
    public static final char PACKAGE_SEPARATOR = '.';

    public static final char RESOURCE_SPERATOR = '/';


    private static final Map<String, Class<?>> PRIMITIVE_TYPE_MAP;
    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_MAP;
    private static final Map<TypeDescription, TypeDescription> PRIMITIVE_TYPE_DEFINITION_MAP;


    static {
        PRIMITIVE_WRAPPER_MAP = new LinkedHashMap<>();

        PRIMITIVE_WRAPPER_MAP.put(boolean.class, Boolean.class);
        PRIMITIVE_WRAPPER_MAP.put(byte.class, Byte.class);
        PRIMITIVE_WRAPPER_MAP.put(char.class, Character.class);
        PRIMITIVE_WRAPPER_MAP.put(short.class, Short.class);

        PRIMITIVE_WRAPPER_MAP.put(int.class, Integer.class);
        PRIMITIVE_WRAPPER_MAP.put(long.class, Long.class);

        PRIMITIVE_WRAPPER_MAP.put(float.class, Float.class);
        PRIMITIVE_WRAPPER_MAP.put(double.class, Double.class);

        PRIMITIVE_WRAPPER_MAP.put(void.class, Void.class);


        PRIMITIVE_TYPE_MAP = PRIMITIVE_WRAPPER_MAP.entrySet().stream()
                .collect( 
                        Collectors.toMap( 
                                entry -> entry.getKey().getName(), 
                                entry -> entry.getKey()
                        )
                );


        PRIMITIVE_TYPE_DEFINITION_MAP = PRIMITIVE_WRAPPER_MAP.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                entry -> TypeDescription.ForLoadedType.of(entry.getKey()), 
                                entry -> TypeDescription.ForLoadedType.of(entry.getValue())
                        )
                );
    }


    public static Class<?> forName(String className, ClassLoader classLoader) 
            throws ClassNotFoundException {
        return forName(className, false, classLoader);
    }

    /**
     * TODO: normal class name
     * @param className
     * @param initialize
     * @param classLoader
     * @return
     * @throws ClassNotFoundException
     */
    public static Class<?> forName(String className, boolean initialize, ClassLoader classLoader) 
            throws ClassNotFoundException {
        Assert.notNull(className, "'className' must not be null.");
        Class<?> clazz = resolvePrimitieType(className, classLoader);
        if (clazz != null)
            return clazz;

        try {
            return Class.forName(className, initialize, classLoader);
        } catch (ClassNotFoundException e) {
            throw e;
        }
    }

    private static Class<?> resolvePrimitieType(String className, ClassLoader classLoader) {
        return PRIMITIVE_TYPE_MAP.containsKey(className) ? PRIMITIVE_TYPE_MAP.get(className) : null;
    }


    public static String abbreviateClassName(String className) {
        if (StringUtils.hasText(className) == false)
            return "";

        String[] items = className.split("\\"+PACKAGE_SEPARATOR);
        if (items.length == 0)
            return className;

        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < items.length - 1; index++) {
            sb.append(items[index].charAt(0)).append(PACKAGE_SEPARATOR);
        }
        sb.append(items[items.length-1]);
        return sb.toString();
    }

    public static String abbreviate(String className, int targetLength) {
        return new TargetLengthBasedClassNameAbbreviator(targetLength).abbreviate(className);
    }


    public static AccessibleObject getAccessibleObject(Class<?> type, MethodDescription methodDescription) 
            throws ClassNotFoundException, NoSuchMethodException, SecurityException {
        Assert.notNull(type, "'type' must not be null.");
        Assert.notNull(methodDescription, "'methodDescription' must not be null.");

        ClassLoader classLoader = type.getClassLoader();
        String methodName = methodDescription.getName();

        List<Class<?>> parameterTypes = new ArrayList<>(methodDescription.getParameters().size());
        for (ParameterDescription paramDescription : methodDescription.getParameters()) {
            TypeDescription parameterType = paramDescription.getType().asErasure();
            parameterTypes.add( ClassUtils.forName(parameterType.getTypeName(), false, classLoader) );
        }

        Class<?>[] parameters = parameterTypes.toArray( new Class[] {});
        return methodDescription.isConstructor()
                ? type.getDeclaredConstructor(parameters)
                : type.getDeclaredMethod(methodName, parameters);
    }


    /**
     * Determines if the class or interface represented by leftType parameter 
     * is either the same as or is a super class or super interface represented
     * by rightType parameter.
     * 
     * @param leftType
     * @param rightType
     * @return
     */
    public static boolean isAssignableFrom(Class<?> leftType, Class<?> rightType) {
        if (leftType == null || rightType == null)
            return false;

        if (isAssignablePrimitiveFrom(leftType, rightType) == true) {
            return true;
        }

        return leftType.isAssignableFrom(rightType);
    }

    /**
     * @param leftType
     * @param rightType
     * @return
     */
    private static boolean isAssignablePrimitiveFrom(Class<?> leftType, Class<?> rightType) {
        if (leftType.isPrimitive() && rightType.isPrimitive()) {
            return leftType.equals(rightType);
        } else if (leftType.isPrimitive()) {
            Class<?> _leftType = PRIMITIVE_WRAPPER_MAP.get(leftType);
            return _leftType != null && _leftType.isAssignableFrom(rightType);
        } else if (rightType.isPrimitive()) {
            Class<?> _rightType = PRIMITIVE_WRAPPER_MAP.get(rightType);
            return _rightType != null && leftType.isAssignableFrom(_rightType);
        } else {
            return false;
        }
    }

    /**
     * Determines if the class or interface represented by leftType parameter 
     * is either the same as or is a super class or super interface represented
     * by rightType parameter.
     * 
     * @param leftType
     * @param rightType
     * @return
     */
    public static boolean isAssignableFrom(Generic leftType, Generic rightType) {
        if (leftType == null || rightType == null)
            return false;

        // 1.void return
        if (leftType.represents(void.class))
            return true;

        // 2.primitive return
        if (isAssignablePrimitiveFrom(leftType, rightType) == true)
            return true;

        // 3.reference return 
        return leftType.accept(TypeDescription.Generic.Visitor.Assigner.INSTANCE).isAssignableFrom(rightType);
    }

    private static boolean isAssignablePrimitiveFrom(Generic leftType, Generic rightType) {
        if (leftType.isPrimitive() && rightType.isPrimitive()) {
            return leftType.equals(rightType);
        } else if (leftType.isPrimitive()) {
            TypeDescription _leftType = PRIMITIVE_TYPE_DEFINITION_MAP.get(leftType.asErasure());
            return _leftType != null && _leftType.isAssignableFrom(rightType.asErasure());
        } else if (rightType.isPrimitive()) {
            TypeDescription _rightType = PRIMITIVE_TYPE_DEFINITION_MAP.get(rightType.asErasure());
            return _rightType != null && leftType.asErasure().isAssignableFrom(_rightType);
        }

        return false;
    }

    /**
     * Determines if leftType parameter equals to rightType parameter.
     * 
     * @param leftType
     * @param rightType
     * @return
     */
    public static boolean equals(Generic leftType, Generic rightType) {
        if (leftType== null || rightType == null)
            return false;

        if (isSamePrimitive(leftType, rightType) == true)
            return true;

        return leftType.equals(rightType);
    }

    private static boolean isSamePrimitive(Generic leftType, Generic rightType) {
        if (leftType.isPrimitive() && rightType.isPrimitive()) {
            return leftType.equals(rightType);
        } else if (leftType.isPrimitive()) {
            TypeDescription _leftType = PRIMITIVE_TYPE_DEFINITION_MAP.get(leftType.asErasure());
            return _leftType != null && _leftType.equals(rightType.asErasure());
        } else if (rightType.isPrimitive()) {
            TypeDescription _rightType = PRIMITIVE_TYPE_DEFINITION_MAP.get(rightType.asErasure());
            return _rightType != null && leftType.asErasure().equals(_rightType);
        }

        return false;
    }


    /**
     * Determine calleeType under different ClassLoader is visible to callerType.
     * 
     * @param callerType
     * @param calleeType
     * @return
     */
    public static boolean isVisibleTo(TypeDescription calleeType, TypeDescription callerType) {
        if (calleeType.isPrimitive())
            return true;

        // public or same package protected type
        return calleeType.isArray()
                ? isVisibleTo(calleeType.getComponentType(), callerType)
                : calleeType.isPublic() || (calleeType.isProtected() && calleeType.isSamePackage(callerType));
    }


    public static String convertClassToResource(String className) {
        return convertClassToResource(className, false);
    }

    public static String convertClassToResource(String className, boolean appendClassFileExt) {
        if (StringUtils.hasText(className) == false)
            return className;

        return className.replace(PACKAGE_SEPARATOR, RESOURCE_SPERATOR) 
                + (appendClassFileExt ? CLASS_FILE_EXTENSION : "");
    }
}