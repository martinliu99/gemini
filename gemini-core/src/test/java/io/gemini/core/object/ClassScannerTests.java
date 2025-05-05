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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URLClassLoader;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.gemini.api.annotation.NoScanning;

public class ClassScannerTests {

    private ClassScanner classScanner;

    @BeforeEach
    public void setup() {
        ClassLoader classLoader = this.getClass().getClassLoader();
        classScanner = new ClassScanner.Builder()
                .scannedClassLoaders(classLoader)
                .acceptPackages(ObjectFactoryTests.class.getPackage().getName())
                .acceptJarPatterns("*.jar", "classes")
                .filteredClasspathElementUrls( ( (URLClassLoader)classLoader ).getURLs() )
                .build();
    }

    
    @Test
    public void test_getClassesImplementing() {
        class LocalClass implements Marker {};
        Marker anonymousObj = new Marker() {};

        {
            List<String> implementors = classScanner.getClassNamesImplementing(Marker.class.getName());
            assertThat(implementors).containsExactlyInAnyOrder(
                    InnerClass.class.getName(),
                    NestedClass.class.getName(),
                    PrivateNestedClass.class.getName(),
                    LocalClass.class.getName(),
                    anonymousObj.getClass().getName()
            );
        }

        {
            List<String> implementors = classScanner.getClassesImplementing(Marker.class.getName())
                    .filter(new ClassScanner.AccessibleClassInfoFilter())
                    .getNames();
            assertThat(implementors).containsExactlyInAnyOrder(
                    NestedClass.class.getName()
            );
        }
    }


    @Test
    public void test_getClassesWithAnnotation() {
        @AtMarker
        class LocalClass {};

        @SuppressWarnings("unused")
        @AtMarker 
        Object anonymousObj = new Object() {};

        List<String> anotatedClasses = classScanner.getClassNamesWithAnnotation(AtMarker.class.getName());
        assertThat(anotatedClasses).containsExactlyInAnyOrder(
                InnerClass.class.getName(),
                NestedClass.class.getName(),
                PrivateNestedClass.class.getName(),
                LocalClass.class.getName()
        );
    }


    public interface Marker {}

    @Target({ ElementType.TYPE_USE, ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface AtMarker {}

    @AtMarker
    public class InnerClass implements Marker {}

    @NoScanning
    @AtMarker
    public class IgnoredInnerClass implements Marker {}

    @AtMarker
    public static class NestedClass implements Marker {}

    @AtMarker
    private static class PrivateNestedClass implements Marker {}

}
