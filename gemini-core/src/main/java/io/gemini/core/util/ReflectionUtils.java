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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public abstract class ReflectionUtils {

    public static Constructor<?> getDefaultConstructor(Class<?> type) {
        if(type == null) 
            return null;

        if(type.isInterface() || Modifier.isAbstract(type.getModifiers()) ) 
            return null;

        for(Constructor<?> constructor : type.getDeclaredConstructors()) {
            if(constructor.getParameterCount() == 0)
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
}
