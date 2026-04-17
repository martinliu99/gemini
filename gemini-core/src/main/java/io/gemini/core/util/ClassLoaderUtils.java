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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import io.gemini.api.classloader.ClassLoaders;

/**
 * Utility class for class loader identification and validation.
 * <p>
 * Provides helpers to mask {@code null} (bootstrap) class loaders with a sentinel object,
 * retrieve human-readable class loader names and IDs, and validate class loader classpath
 * entries for known JDK issues (e.g., INDEX.LIST conflicts).
 * </p>
 *
 * @author   martin.liu
 */
public abstract class ClassLoaderUtils {

    private static final ClassLoader BOOTSTRAP_CLASSLOADER = new BootstrapClassLoader();


    /**
     * Returns a sentinel non-null object representing the bootstrap class loader ({@code null}).
     * Used as a map key where {@code null} keys are not allowed.
     *
     * @param classLoader the class loader to mask
     * @return the original class loader, or the bootstrap sentinel if {@code null}
     */
    public static ClassLoader maskNull(ClassLoader classLoader) {
        return classLoader == null ? BOOTSTRAP_CLASSLOADER : classLoader;
    }

    /**
     * Returns a human-readable name for the given class loader.
     * Returns {@code "BootstrapClassLoader"} for {@code null} or the bootstrap sentinel.
     *
     * @param classLoader the class loader
     * @return the class loader's class name, or {@code "BootstrapClassLoader"}
     */
    public static String getClassLoaderName(ClassLoader classLoader) {
        return classLoader == null || classLoader == BOOTSTRAP_CLASSLOADER 
                ? ClassLoaders.BOOTSTRAP_CLASSLOADER_NAME : classLoader.getClass().getName();
    }

    /**
     * Returns a unique identity string for the given class loader, including its identity hash code.
     * Returns {@code "BootstrapClassLoader"} for {@code null} or the bootstrap sentinel.
     *
     * @param classLoader the class loader
     * @return a string of the form {@code "ClassName@hexHash"}, or {@code "BootstrapClassLoader"}
     */
    public static String getClassLoaderId(ClassLoader classLoader) {
        return classLoader == null || classLoader == BOOTSTRAP_CLASSLOADER
                ? ClassLoaders.BOOTSTRAP_CLASSLOADER_NAME 
                : ( classLoader.getClass().getName() + "@" + ObjectUtils.getIdentityHexString(classLoader) );
    }

    /**
     * Validates the given class loader's classpath for known JDK issues.
     * Logs a warning if any JAR contains an {@code INDEX.LIST} file, which can cause
     * {@link ClassLoader#getResources(String)} to return incorrect results.
     *
     * @param classLoader the class loader to validate (may be {@code null})
     */
    public static void validate(ClassLoader classLoader) {
        if (classLoader == null)
            return;

        try {
            Enumeration<URL> indexLists = classLoader.getResources("META-INF/INDEX.LIST");
            List<URL> urls = Collections.list(indexLists);
            if (urls.size() > 0) {
                System.err.println("Below jars contain INDEX.LIST files might impact ClassLoader::getResources().");
                System.err.println("  " + urls);
                System.err.println("For more details, please refer to https://bugs.openjdk.org/browse/JDK-8305597");
            }
        } catch (IOException e) { /* ignored */ }
    }

    /**
     * Returns the list of all classpath entries from the JVM system property {@code java.class.path}.
     *
     * @return list of classpath entry strings
     */
    public static List<String> getClassPaths() {
        String classpathStr = System.getProperty("java.class.path");
        classpathStr = classpathStr.replace('\\', '/');
        String[] classPathValues = classpathStr.split(File.pathSeparator);

        List<String> classPaths = new ArrayList<>(classPathValues.length);
        for (String classPath : classPathValues) {
            classPath = classPath.trim();
            classPath = classPath.charAt(classPath.length()-1) == File.pathSeparatorChar ? classPath.substring(0, classPath.length()-1) : classPath;
            classPaths.add(classPath);
        }

        return classPaths;
    }


    private static class BootstrapClassLoader extends ClassLoader {

        @Override
        public String toString() {
            return ClassLoaders.BOOTSTRAP_CLASSLOADER_NAME;
        }
    }
}
