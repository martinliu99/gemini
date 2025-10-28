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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import io.gemini.api.classloader.ClassLoaders;

public abstract class ClassLoaderUtils {

    public static final ClassLoader BOOTSTRAP_CLASSLOADER = new BootstrapClassLoader();


    public static ClassLoader maskNull(ClassLoader classLoader) {
        return classLoader == null ? BOOTSTRAP_CLASSLOADER : classLoader;
    }

    public static String getClassLoaderName(ClassLoader classLoader) {
        return classLoader == null || classLoader == BOOTSTRAP_CLASSLOADER 
                ? ClassLoaders.BOOTSTRAP_CLASSLOADER_NAME : classLoader.getClass().getName();
    }

    public static String getClassLoaderId(ClassLoader classLoader) {
        return classLoader == null || classLoader == BOOTSTRAP_CLASSLOADER
                ? ClassLoaders.BOOTSTRAP_CLASSLOADER_NAME 
                : ( classLoader.getClass().getName() + "@" + ObjectUtils.getIdentityHexString(classLoader) );
    }


    public static void validate(ClassLoader classLoader) {
        if(classLoader == null)
            return;

        try {
            Enumeration<URL> indexLists = classLoader.getResources("META-INF/INDEX.LIST");
            List<URL> urls = Collections.list(indexLists);
            if(urls.size() > 0) {
                System.err.println("Below jars contain INDEX.LIST files might impact ClassLoader::getResources().");
                System.err.println("  " + urls);
                System.err.println("For more details, please refer to https://bugs.openjdk.org/browse/JDK-8305597");
            }
        } catch (IOException e) { /* ignored */ }
    }

    public static List<String> getClassPaths() {
        String classpathStr = System.getProperty("java.class.path");
        classpathStr = classpathStr.replace('\\', '/');
        String[] classPathValues = classpathStr.split(File.pathSeparator);

        List<String> classPaths = new ArrayList<>(classPathValues.length);
        for(String classPath : classPathValues) {
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
