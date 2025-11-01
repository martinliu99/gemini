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
/**
 * 
 */
package io.gemini.api.classloader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 */
public abstract class ClassLoaders {

    public static final String BOOTSTRAP_CLASSLOADER_NAME = "BootstrapClassLoader";
    public static final String EXT_CLASSLOADER_NAME = "ExtClassLoader";
    public static final String APP_CLASSLOADER_NAME = "AppClassLoader";

    private static final ClassLoader EXT_CLASSLOADER;
    private static final ClassLoader APP_CLASSLOADER;

    private static final Set<ClassLoader> BUILTIN_CLASSLOADERS;


    static {
        APP_CLASSLOADER = ClassLoader.getSystemClassLoader();

        ClassLoader classLoader = APP_CLASSLOADER;
        while (classLoader.getParent() != null) 
            classLoader = classLoader.getParent();
        EXT_CLASSLOADER = classLoader;

        Set<ClassLoader> classLoaders = new HashSet<>();
        classLoaders.add(null);
        classLoaders.add(EXT_CLASSLOADER);
        classLoaders.add(APP_CLASSLOADER);
        BUILTIN_CLASSLOADERS = Collections.unmodifiableSet(classLoaders);
    }


    public static boolean isBootstrapClassLoader(ClassLoader classLoader) {
        return classLoader == null;
    }

    public static boolean isExtClassLoader(ClassLoader classLoader) {
        return classLoader == EXT_CLASSLOADER;
    }

    public static boolean isAppClassLoader(ClassLoader classLoader) {
        return classLoader == APP_CLASSLOADER;
    }

    public static ClassLoader getExtClassLoader() {
        return EXT_CLASSLOADER;
    }

    public static ClassLoader getAppClassLoader() {
        return APP_CLASSLOADER;
    }

    public static Set<ClassLoader> getBuiltinClassLoaders() {
        return BUILTIN_CLASSLOADERS;
    }
}
