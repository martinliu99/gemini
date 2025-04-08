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

import java.util.ArrayList;
import java.util.List;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool.Resolution.NoSuchTypeException;

public class MethodUtils {

    /**
     * Gets all MethodDescriptions including type initializer.
     * @param typeDescription
     * @return
     */
    public static List<MethodDescription.InDefinedShape> getAllMethodDescriptions(TypeDescription typeDescription) {
        Assert.notNull(typeDescription, "'typeDescription' must not be null.");

        MethodList<MethodDescription.InDefinedShape> methodList= typeDescription.getDeclaredMethods();
        List<MethodDescription.InDefinedShape> methodDescriptions = new ArrayList<>(methodList.size() + 1);

        methodDescriptions.addAll(methodList);
        methodDescriptions.add(new MethodDescription.Latent.TypeInitializer(typeDescription));

        return methodDescriptions;
    }

    /**
     * Gets method name with method type.
     * 
     * @param methodDescription
     * @return
     */
    public static String getMethodName(MethodDescription methodDescription) {
        if(methodDescription == null) return "";

        return methodDescription.isTypeInitializer() 
                ? MethodDescription.TYPE_INITIALIZER_INTERNAL_NAME 
                : (methodDescription.isConstructor() ? MethodDescription.CONSTRUCTOR_INTERNAL_NAME : methodDescription.getName());
    }

    public static String getMethodSignature(MethodDescription methodDescription) {
        if(methodDescription == null)
            return "";

        try {
            return methodDescription.toGenericString();
        } catch(NoSuchTypeException e) {
            return methodDescription.getGenericSignature();
        }
    }
}
