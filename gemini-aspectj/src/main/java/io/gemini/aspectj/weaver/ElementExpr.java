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

import org.aspectj.weaver.IHasPosition;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.patterns.Bindings;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.ParserException;
import org.aspectj.weaver.patterns.SimpleScope;
import org.aspectj.weaver.patterns.TypePattern;

import io.gemini.aspectj.weaver.patterns.PatternParserV2;
import io.gemini.aspectj.weaver.patterns.TypeNamePatternParser;
import io.gemini.core.pool.TypePools.HybridResolutionTypePool;
import io.gemini.core.pool.TypePools.ResolutionType;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.ClassUtils;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface ElementExpr<T> extends ElementMatcher<T> {

    /**
     * {@inheritDoc}
     */
    boolean matches(T target);


    abstract class AbstractBase<T> implements ElementExpr<T> {

        private final String expression;
        private final TypeWorld typeWorld;
        private final TypePattern typePattern;


        public AbstractBase(String expression, TypePattern typePattern) {
            this(expression, TypeWorld.EMPTY_WORLD, typePattern);
        }

        public AbstractBase(String expression, TypeWorld typeWorld, TypePattern typePattern) {
            this.expression = expression;
            this.typeWorld = typeWorld;

            this.typePattern = typePattern;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(T target) {
            ResolvedType resolvedType = doResolveType(typeWorld, target);

            return typePattern.matchesStatically(resolvedType);
        }

        protected abstract ResolvedType doResolveType(TypeWorld typeWorld, T target);


        @Override
        public String toString() {
            return expression;
        }
    }


    class ClassLoaderExpr extends AbstractBase<ClassLoader> {

        /**
         * @param expression
         * @param typePattern
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
            if(ClassLoaderUtils.isBootstrapClassLoader(classLoader))
                return ClassLoaderUtils.BOOTSTRAP_CLASSLOADER_NAME;
            else if(ClassLoaderUtils.isExtClassLoader(classLoader))
                return ClassLoaderUtils.EXT_CLASSLOADER_NAME;
            else if(ClassLoaderUtils.isAppClassLoader(classLoader))
                return ClassLoaderUtils.APP_CLASSLOADER_NAME;
            else
                return classLoader.getClass().getName();
        }
    }


    class TypeNameExpr extends AbstractBase<String> {

        /**
         * @param expression
         * @param typePattern
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


    class ResourceNameExpr extends AbstractBase<String> {

        /**
         * @param expression
         * @param typePattern
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
            if(resourceName != null) {
                resourceName = resourceName.replace(ClassUtils.RESOURCE_SPERATOR, ClassUtils.PACKAGE_SEPARATOR);

                if(resourceName.endsWith(ClassUtils.CLASS_FILE_EXTENSION)) 
                    resourceName = resourceName.replace(ClassUtils.CLASS_FILE_EXTENSION, "");
            }

            return resourceName;
        }
    }


    class TypeExpr extends AbstractBase<TypeDescription> {

        /**
         * @param expression
         * @param typePattern
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


    enum Parser {

        INSTANCE;


        public ElementMatcher<ClassLoader> parseClassLoaderExpr(String expression) {
            try {
                TypePattern typePattern = new TypeNamePatternParser(expression).parseTypePattern();

                return new ClassLoaderExpr(expression, typePattern);
            } catch (ParserException e) {
                throw new IllegalArgumentException(buildUserMessageFromParserException(expression, e));
            } catch (TypeWorld.TypeWorldException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }


        public ElementMatcher<String> parseTypeNameExpr(String expression) {
            try {
                TypePattern typePattern = new TypeNamePatternParser(expression).parseTypePattern();

                return new TypeNameExpr(expression, typePattern);
            } catch (ParserException e) {
                throw new IllegalArgumentException(buildUserMessageFromParserException(expression, e));
            } catch (TypeWorld.TypeWorldException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }


        public ElementMatcher<String> parseResourceNameExpr(String expression) {
            try {
                expression = formatExpression(expression);
                TypePattern typePattern = new TypeNamePatternParser(expression).parseTypePattern();

                return new ResourceNameExpr(expression, typePattern);
            } catch (ParserException e) {
                throw new IllegalArgumentException(buildUserMessageFromParserException(expression, e));
            } catch (TypeWorld.TypeWorldException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }

        private String formatExpression(String expression) {
            return expression == null ? "" : expression.replace("/", ".");
        }


        public ElementMatcher<TypeDescription> parseTypeExpr(String expression, TypeWorld typeWorld) {
            ResolutionType currentResolutionType = HybridResolutionTypePool.getResolutionType();
            try {
                HybridResolutionTypePool.setResolutionType(ResolutionType.EAGER);

                TypePattern typePattern = new PatternParserV2(expression).parseTypePattern();

                IScope scope = new SimpleScope(typeWorld.getWorld(), new FormalBinding[0]);
                typePattern = typePattern.resolveBindings(scope, Bindings.NONE, false, false);

                return new TypeExpr(expression, typeWorld, typePattern);
            } catch (ParserException e) {
                throw new IllegalArgumentException(buildUserMessageFromParserException(expression, e));
            } catch (TypeWorld.TypeWorldException e) {
                throw new IllegalArgumentException(e.getMessage());
            } finally {
                HybridResolutionTypePool.setResolutionType(currentResolutionType);
            }
        }


        private String buildUserMessageFromParserException(String expression, ParserException ex) {
            StringBuffer msg = new StringBuffer();
            msg.append("Expression is not well-formed: '");
            msg.append(ex.getMessage());
            msg.append("'");
            IHasPosition location = ex.getLocation();
            msg.append(" at character position ");
            msg.append(location.getStart());
            msg.append("\n");
            msg.append(expression);
            msg.append("\n");
            for (int i = 0; i < location.getStart(); i++) {
                msg.append(" ");
            }
            for (int j = location.getStart(); j <= location.getEnd(); j++) {
                msg.append("^");
            }
            msg.append("\n");
            return msg.toString();
        }

    }
}
