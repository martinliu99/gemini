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
package io.gemini.aspectj.weaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

import io.gemini.core.pool.TypePoolFactory;
import io.gemini.core.util.ClassLoaderUtils;
import jdk.proxy2.$Proxy27;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;


public class ElementExprTests {

    private Runnable anonymousClass = new Runnable() {

        @Override
        public void run() {
        }
    };

    private Runnable lambdaClass = () -> {};


    @Test
    public void testName() {
        System.out.println("runnable name: " + anonymousClass.getClass().getName());
        System.out.println("runnable canonicalName: " + anonymousClass.getClass().getCanonicalName());
        System.out.println("runnable simpleName: " + anonymousClass.getClass().getSimpleName());
        System.out.println("runnable typeName: " + anonymousClass.getClass().getTypeName());
        System.out.println("");

        System.out.println("runnable2 name: " + lambdaClass.getClass().getName());
        System.out.println("runnable2 canonicalName: " + lambdaClass.getClass().getCanonicalName());
        System.out.println("runnable2 simpleName: " + lambdaClass.getClass().getSimpleName());
        System.out.println("runnable2 typeName: " + lambdaClass.getClass().getTypeName());
    }

    @Test
    public void testClassLoader() {
        {
            ElementMatcher<ClassLoader> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseClassLoaderExpr(
                            "BootstrapClassLoader || AppClassLoader");

            assertThat( elementExpr.matches(null) ).isTrue();
            assertThat( elementExpr.matches( ClassLoaderUtils.getExtClassLoader() ) ).isFalse();
            assertThat( elementExpr.matches( ClassLoaderUtils.getAppClassLoader() ) ).isTrue();
        }

        {
            ElementMatcher<ClassLoader> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseClassLoaderExpr(
                            "io.gemini.aspectj.weaver.*ExprTest*$NestedClassLoader");

            assertThat( elementExpr.matches( new NestedClassLoader(new URL[0], getClass().getClassLoader()) ) ).isTrue();
        }


        {
            ElementMatcher<ClassLoader> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseClassLoaderExpr(
                            "io.gemini.aspectj.weaver.*ExprTest*.NestedClassLoader");

            assertThat( elementExpr.matches( new NestedClassLoader(new URL[0], getClass().getClassLoader()) ) ).isFalse();
        }
    }


    public static class NestedClassLoader extends URLClassLoader {

        /**
         * @param urls
         * @param parent
         */
        public NestedClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }
    }


    @Test
    public void testTypeName() {
        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeNameExpr(
                            "io.gemini..weaver.*ExprTest*");

            assertThat( elementExpr.matches( getClass().getName() ) ).isTrue();
            assertThat( elementExpr.matches( ElementExprTests.InnerClass.class.getName() ) ).isTrue();
        }

        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeNameExpr(
                            "io.gemini..weaver.*ExprTest*$1");

            assertThat( elementExpr.matches( anonymousClass.getClass().getName() ) ).isTrue();
        }

        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeNameExpr(
                            "io.gemini..weaver.*ExprTest*$InnerClass");

            assertThat( elementExpr.matches( getClass().getName() ) ).isFalse();
            assertThat( elementExpr.matches( ElementExprTests.InnerClass.class.getName() ) ).isTrue();
        }

        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeNameExpr(
                            "io.gemini..weaver.*ExprTest* && !io.gemini..weaver.*ExprTest*$NestClass");

            assertThat( elementExpr.matches( getClass().getName() ) ).isTrue();
            assertThat( elementExpr.matches( ElementExprTests.InnerClass.class.getName() ) ).isTrue();
            assertThat( elementExpr.matches( ElementExprTests.NestClass.class.getName() ) ).isFalse();
        }

        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeNameExpr(
                            " !*..$Proxy*");

            assertThat( elementExpr.matches( getClass().getName() ) ).isTrue();
            assertThat( elementExpr.matches( $Proxy27.class.getName() ) ).isFalse();
        }
    }


    @Test
    public void testResourceName() {
        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseResourceNameExpr(
                            "io/gemini//weaver/*ExprTest*");

            assertThat( elementExpr.matches( getClass().getName() ) ).isTrue();
            assertThat( elementExpr.matches( ElementExprTests.InnerClass.class.getName().replace(".", "/") + ".class" ) ).isTrue();
        }

        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseResourceNameExpr(
                            "io.gemini..weaver.*ExprTest*$1");

            assertThat( elementExpr.matches( anonymousClass.getClass().getName().replace(".", "/") + ".class" ) ).isTrue();
        }

        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseResourceNameExpr(
                            "io.gemini..weaver.*ExprTest*$InnerClass");

            assertThat( elementExpr.matches( getClass().getName() ) ).isFalse();
            assertThat( elementExpr.matches( ElementExprTests.InnerClass.class.getName().replace(".", "/") + ".class" ) ).isTrue();
        }

        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseResourceNameExpr(
                            "io.gemini..weaver.resource.*roperties");

            assertThat( elementExpr.matches( ElementExprTests.InnerClass.class.getPackage().getName() + ".resource.properties" ) ).isTrue();
            assertThat( elementExpr.matches( "io/gemini/aspectj/weaver/resource.properties" ) ).isTrue();
        }

        {
            ElementMatcher<String> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseResourceNameExpr(
                            "io/gemini/aspectj/weaver/resource.*roperties");

            assertThat( elementExpr.matches( ElementExprTests.InnerClass.class.getPackage().getName() + ".resource.properties" ) ).isTrue();
            assertThat( elementExpr.matches( "io/gemini/aspectj/weaver/resource.properties" ) ).isTrue();
        }
    }


    @Test
    public void testType() {
        TypeWorld typeWorld = new TypeWorldFactory.Default( new TypePoolFactory.Default() )
                .createTypeWorld(getClass().getClassLoader(), null);

        {
            ElementMatcher<TypeDescription> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeExpr(
                            "io.gemini..weaver.*ExprTest*", typeWorld);

            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(getClass()) ) ).isTrue();
            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(ElementExprTests.InnerClass.class) ) ).isTrue();
        }

        {
            ElementMatcher<TypeDescription> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeExpr(
                            "io.gemini..weaver.*ExprTest*$1", typeWorld);

            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(anonymousClass.getClass()) ) ).isTrue();
        }

        {
            ElementMatcher<TypeDescription> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeExpr(
                            "io.gemini..weaver.*ExprTest*$InnerClass", typeWorld);

            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(getClass()) ) ).isFalse();
            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(ElementExprTests.InnerClass.class) ) ).isTrue();
        }

        {
            ElementMatcher<TypeDescription> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeExpr(
                            "io.gemini..weaver.*ExprTest* && !io.gemini..weaver.*ExprTest*$NestClass", typeWorld);

            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(getClass()) ) ).isTrue();
            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(ElementExprTests.InnerClass.class) ) ).isTrue();
            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(ElementExprTests.NestClass.class) ) ).isFalse();
        }


        {
            ElementMatcher<TypeDescription> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeExpr(
                            "io.gemini.aspectj.weaver.ElementExprTests$Generic[]", typeWorld);

            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(ElementExprTests.Generic[].class) ) ).isTrue();
        }

        {
            ElementMatcher<TypeDescription> elementExpr = ElementExpr.Parser.INSTANCE
                    .parseTypeExpr(
                            "@io.gemini.aspectj.weaver.ElementExprTests.Mark io.gemini.aspectj.weaver.ElementExprTests.Generic", typeWorld);

            assertThat( elementExpr.matches( TypeDescription.ForLoadedType.of(ElementExprTests.Generic.class) ) ).isTrue();
        }
    }


    public class InnerClass {}

    public static class NestClass {}


    public @interface Mark {}

    @Mark
    public class Generic<T> {}

    @Mark
    public class Parameterized extends Generic<String> {}
}
