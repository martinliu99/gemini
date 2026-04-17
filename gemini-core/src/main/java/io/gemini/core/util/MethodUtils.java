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

import java.util.Collections;
import java.util.List;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.CompoundList;

/**
 * Utility class for working with ByteBuddy {@link MethodDescription} objects.
 * <p>
 * Provides helpers to retrieve all method descriptions (including type initializers),
 * get human-readable method names, and safely compute generic method signatures.
 * </p>
 *
 * @author   martin.liu
 */
public abstract class MethodUtils {

    /**
     * Returns all method descriptions for the given type, including the type initializer ({@code <clinit>}).
     *
     * @param typeDescription the type to inspect
     * @return list of all method descriptions including the type initializer
     */
    public static List<MethodDescription.InDefinedShape> getAllMethodDescriptions(TypeDescription typeDescription) {
        Assert.notNull(typeDescription, "'typeDescription' must not be null.");

        try {
            return CompoundList.<MethodDescription.InDefinedShape>of(
                    typeDescription.getDeclaredMethods(), 
                    new MethodDescription.Latent.TypeInitializer(typeDescription)
            );
        } catch (NoClassDefFoundError e) {
            return Collections.emptyList();
        }
    }

    /**
     * Returns a human-readable method name for the given method description.
     * Returns {@code "<clinit>"} for type initializers and {@code "<init>"} for constructors.
     *
     * @param methodDescription the method description (may be {@code null})
     * @return the method name, or an empty string if {@code null}
     */
    public static String getMethodName(MethodDescription methodDescription) {
        if (methodDescription == null) return "";

        return methodDescription.isTypeInitializer() 
                ? MethodDescription.TYPE_INITIALIZER_INTERNAL_NAME 
                : (methodDescription.isConstructor() ? MethodDescription.CONSTRUCTOR_INTERNAL_NAME : methodDescription.getName());
    }

    /**
     * Returns the generic signature string for the given method description.
     * Falls back to the non-generic signature or a simple {@code "ClassName.methodName(...)"} string
     * if the generic signature cannot be computed.
     *
     * @param methodDescription the method description (may be {@code null})
     * @return the method signature string, or an empty string if {@code null}
     */
    public static String getMethodSignature(MethodDescription methodDescription) {
        if (methodDescription == null)
            return "";

        try {
            return methodDescription.toGenericString();
        } catch (Exception e) {
            // get generic signature from LazyMethodDescription
            try {
                return methodDescription.getGenericSignature();
            } catch (Exception e2) {
                return methodDescription.getDeclaringType().getTypeName() + "." + methodDescription.getName() + "(...)";
            }
        }
    }
}
