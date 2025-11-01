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
package io.gemini.core.object;

import java.net.URLClassLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.gemini.core.DiagnosticLevel;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class ObjectFactoryTests {

    private ObjectFactory objectFactory;

    @BeforeEach
    public void setup() {
        ClassLoader classLoader = this.getClass().getClassLoader();
        ClassScanner classScanner = new ClassScanner.Builder()
                .scannedClassLoaders(classLoader)
                .acceptPackages(ObjectFactoryTests.class.getPackage().getName())
                .acceptJarPatterns("*.jar", "classes")
                .filteredClasspathElementUrls( ( (URLClassLoader)classLoader ).getURLs() )
                .build();

        objectFactory = new ObjectFactory.Simple(DiagnosticLevel.SIMPLE, classLoader, classScanner);
    }


    @Test
    public void testInnerClass() {
        objectFactory.createObject(InnerClass.class);
    }

    private static class InnerClass {

        @SuppressWarnings("unused")
        public InnerClass() {}
    }
}
