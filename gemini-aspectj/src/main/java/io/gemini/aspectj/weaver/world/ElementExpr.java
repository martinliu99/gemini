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
package io.gemini.aspectj.weaver.world;

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.patterns.TypePattern;

import io.gemini.api.classloader.ClassLoaders;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Provides ByteBuddy {@link ElementMatcher} implementations that match against
 * AspectJ {@link TypePattern} instances for class loaders, type names, resource names,
 * and type descriptions.
 * <p>
 * Used by {@link io.gemini.aspectj.weaver.ExprParser} to wrap parsed patterns into
 * ByteBuddy-compatible matchers.
 * </p>
 *
 * @param <T> the target type to match
 *
 * @author   martin.liu
 */
public interface ElementExpr<T> extends ElementMatcher<T> {

    /**
     * {@inheritDoc}
     */
    @Override
    boolean matches(T target);


    /**
     * Abstract base implementation of {@link ElementExpr} that delegates type resolution
     * to a {@link TypeWorld} and evaluates the resolved type against an AspectJ
     * {@link TypePattern} via {@link TypePattern#matchesStatically}.
     * <p>
     * Subclasses implement {@link #doResolveType} to convert the concrete target value
     * (e.g., a {@link ClassLoader}, a type name string, or a {@link TypeDescription})
     * into an AspectJ {@link ResolvedType} suitable for pattern matching.
     * </p>
     *
     * @param <T> the target type to match
     */
    abstract class AbstractBase<T> implements ElementExpr<T> {

        private final String expression;
        private final TypeWorld typeWorld;
        private final TypePattern typePattern;


        /**
         * Creates an {@code AbstractBase} using {@link TypeWorld#EMPTY_WORLD} for type resolution.
         * Suitable for patterns that do not require a real type world (e.g., name-only patterns).
         *
         * @param expression  the original expression string, returned by {@link #toString()}
         * @param typePattern the parsed AspectJ type pattern used for static matching
         */
        public AbstractBase(String expression, TypePattern typePattern) {
            this(expression, TypeWorld.EMPTY_WORLD, typePattern);
        }

        /**
         * Creates an {@code AbstractBase} with an explicit {@link TypeWorld}.
         *
         * @param expression  the original expression string, returned by {@link #toString()}
         * @param typeWorld   the type world used to resolve the target to a {@link ResolvedType}
         * @param typePattern the parsed AspectJ type pattern used for static matching
         */
        public AbstractBase(String expression, TypeWorld typeWorld, TypePattern typePattern) {
            this.expression = expression;
            this.typeWorld = typeWorld;

            this.typePattern = typePattern;
        }

        /**
         * Resolves the target to a {@link ResolvedType} via {@link #doResolveType} and
         * delegates to {@link TypePattern#matchesStatically}. Returns {@code false}
         * immediately for blank string targets.
         *
         * {@inheritDoc}
         */
        @Override
        public boolean matches(T target) {
            if (target instanceof String && StringUtils.hasText( (String) target) == false)
                return false;

            ResolvedType resolvedType = doResolveType(typeWorld, target);

            return typePattern.matchesStatically(resolvedType);
        }

        /**
         * Converts the given target value into an AspectJ {@link ResolvedType} using the
         * provided {@link TypeWorld}.
         *
         * @param typeWorld the type world for resolution
         * @param target    the value to resolve
         * @return the resolved type; must not be {@code null}
         */
        protected abstract ResolvedType doResolveType(TypeWorld typeWorld, T target);


        /**
         * Returns the original expression string this matcher was built from.
         *
         * @return the expression string; never {@code null}
         */
        @Override
        public String toString() {
            return expression;
        }
    }


    /**
     * An {@link AbstractBase} implementation that matches {@link ClassLoader} instances by resolving
     * the class loader to a canonical name (bootstrap, ext, app, or the class loader's
     * own class name) and evaluating it against the type pattern.
     */
    class ClassLoaderExpr extends AbstractBase<ClassLoader> {

        /**
         * Creates a {@code ClassLoaderExpr} that matches class loaders by name
         * using the given type pattern.
         *
         * @param expression  the original expression string (used in {@link #toString()})
         * @param typePattern the parsed type pattern to match against the class loader name
         */
        public ClassLoaderExpr(String expression, TypePattern typePattern) {
            super(expression, typePattern);
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        protected ResolvedType doResolveType(TypeWorld typeWorld, ClassLoader target) {
            return typeWorld.resolve( getName(target) );
        }

        private String getName(ClassLoader classLoader) {
            if (ClassLoaders.isBootstrapClassLoader(classLoader))
                return ClassLoaders.BOOTSTRAP_CLASSLOADER_NAME;
            else if (ClassLoaders.isExtClassLoader(classLoader))
                return ClassLoaders.EXT_CLASSLOADER_NAME;
            else if (ClassLoaders.isAppClassLoader(classLoader))
                return ClassLoaders.APP_CLASSLOADER_NAME;
            else
                return classLoader.getClass().getName();
        }
    }


    /**
     * An {@link AbstractBase} implementation that matches fully-qualified type name strings by resolving
     * the name directly through the {@link TypeWorld} and evaluating it against the type pattern.
     */
    class TypeNameExpr extends AbstractBase<String> {

        /**
         * Creates a {@code TypeNameExpr} that matches fully-qualified type names
         * using the given type pattern.
         *
         * @param expression  the original expression string (used in {@link #toString()})
         * @param typePattern the parsed type pattern to match against the type name
         */
        public TypeNameExpr(String expression, TypePattern typePattern) {
            super(expression, typePattern);
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        protected ResolvedType doResolveType(TypeWorld typeWorld, String target) {
            return typeWorld.resolve(target);
        }
    }


    /**
     * An {@link AbstractBase} implementation that matches resource path strings (e.g., {@code com/example/Foo.class})
     * by normalising the path to a dot-separated type name (stripping the {@code .class} suffix
     * and replacing {@code /} with {@code .}) before resolving and pattern-matching.
     */
    class ResourceNameExpr extends AbstractBase<String> {

        /**
         * Creates a {@code ResourceNameExpr} that matches resource path strings
         * using the given type pattern. Slashes in resource names are converted to
         * dots before matching.
         *
         * @param expression  the original expression string (used in {@link #toString()})
         * @param typePattern the parsed type pattern to match against the normalized resource name
         */
        public ResourceNameExpr(String expression, TypePattern typePattern) {
            super(expression, typePattern);
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        protected ResolvedType doResolveType(TypeWorld typeWorld, String target) {
            return typeWorld.resolve( getName(target) );
        }

        private String getName(String resourceName) {
            if (resourceName != null) {
                resourceName = resourceName.replace(ClassUtils.RESOURCE_SPERATOR, ClassUtils.PACKAGE_SEPARATOR);

                if (resourceName.endsWith(ClassUtils.CLASS_FILE_EXTENSION)) 
                    resourceName = resourceName.replace(ClassUtils.CLASS_FILE_EXTENSION, "");
            }

            return resourceName;
        }
    }


    /**
     * An {@link AbstractBase} implementation that matches type by resolving it through 
     * the {@link TypeWorld} and evaluating it against the type pattern.
     */
    class TypeExpr extends AbstractBase<TypeDescription> {

        /**
         * Creates a {@code TypeExpr} that matches {@link TypeDescription} instances
         * using the given type world and type pattern.
         *
         * @param expression  the original expression string (used in {@link #toString()})
         * @param typeWorld   the type world used to resolve the type description
         * @param typePattern the parsed type pattern to match against the resolved type
         */
        public TypeExpr(String expression, TypeWorld typeWorld, TypePattern typePattern) {
            super(expression, typeWorld, typePattern);
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        protected ResolvedType doResolveType(TypeWorld typeWorld, TypeDescription target) {
            return typeWorld.resolve(target);
        }
    }
}
