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
package io.gemini.aop.factory;

import java.io.Closeable;
import java.io.IOException;

import io.gemini.aop.factory.classloader.AspectClassLoader;
import io.gemini.api.aop.MatchingContext;
import io.gemini.api.classloader.ClassLoaders;
import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AdvisorContext implements Closeable {

    private final FactoryContext factoryContext;

    private final String targetClassLoaderName;
    private final JavaModule targetJavaModule;

    private final AspectClassLoader classLoader;
    private final ObjectFactory objectFactory;

    private final TypePool typePool;
    private final TypeWorld typeWorld;

    private final PlaceholderHelper placeholderHelper;

    private final boolean validateContext;

    private final MatchingContext matchingContext;

    private final boolean autoComputeAsm;


    protected AdvisorContext(FactoryContext factoryContext, 
            String targetClassLoaderName, JavaModule targetJavaModule,
            AspectClassLoader classLoader, ObjectFactory objectFactory, 
            TypePool typePool, TypePool targetTypePool,
            TypeWorld typeWorld, TypeWorld targetTypeWorld,
            boolean validateContext) {
        this.factoryContext = factoryContext;

        this.targetClassLoaderName = targetClassLoaderName;
        this.targetJavaModule = targetJavaModule;

        this.classLoader = classLoader;
        this.objectFactory = objectFactory;

        this.typePool = typePool;
        this.typeWorld = typeWorld;

        this.placeholderHelper = factoryContext.getPlaceholderHelper();

        this.validateContext = validateContext;

        this.matchingContext = new DefultMatchingContext(classLoader, targetTypePool, targetTypeWorld);

        this.autoComputeAsm = factoryContext.getFactoriesContext().isAutoComputeAsm();
    }


    public FactoryContext getFactoryContext() {
        return factoryContext;
    }

    public String getTargetClassLoaderName() {
        return targetClassLoaderName;
    }

    public JavaModule getTargetJavaModule() {
        return targetJavaModule;
    }


    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public TypePool getTypePool() {
        return typePool;
    }

    public TypeWorld getTypeWorld() {
        return typeWorld;
    }


    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }


    public MatchingContext getMatchingContext() {
        return matchingContext;
    }

    public boolean acceptTargetClassloader() {
        return factoryContext.acceptTargetClassLoader(classLoader.getTargetClassLoader());
    }

    public boolean isValidateContext() {
        return validateContext;
    }


    public boolean isAutoComputeAsm() {
        return autoComputeAsm;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        this.typePool.clear();
        this.objectFactory.close();

        this.classLoader.close();
    }


    static class DefultMatchingContext implements MatchingContext {

        private final AspectClassLoader classLoader;

        private final TypePool targetTypePool;
        private final TypeWorld targetTypeWorld;


        public DefultMatchingContext(AspectClassLoader classLoader, 
                TypePool targetTypePool, TypeWorld targetTypeWorld) {
            this.classLoader = classLoader;

            this.targetTypePool = targetTypePool;
            this.targetTypeWorld = targetTypeWorld;
        }

        @Override
        public TypePool getTargetTypePool() {
            return targetTypePool;
        }

        @Override
        public boolean isBootstrapClassLoader() {
            return ClassLoaders.isBootstrapClassLoader(classLoader.getTargetClassLoader());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isExtClassLoader() {
            return ClassLoaders.isExtClassLoader(classLoader.getTargetClassLoader());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isAppClassLoader() {
            return ClassLoaders.isAppClassLoader(classLoader.getTargetClassLoader());

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isClassLoader(String classLoaderExpression) {
            return ExprParser.INSTANCE.parseClassLoaderExpr(classLoaderExpression)
                    .matches(classLoader.getTargetClassLoader());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasType(String requiredTypeExpression) {
            return ExprParser.INSTANCE.hasType(targetTypeWorld, requiredTypeExpression);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasFiled(String requiredFieldExpression) {
            return ExprParser.INSTANCE.hasField(targetTypeWorld, requiredFieldExpression);
        }

        /** {@inheritDoc} 
         */
        @Override
        public boolean hasConstructor(String requiredConstructorExpression) {
            return ExprParser.INSTANCE.hasConstructor(targetTypeWorld, requiredConstructorExpression);
        }

        /** {@inheritDoc} 
         */
        @Override
        public boolean hasMethod(String requiredMethodExpression) {
            return ExprParser.INSTANCE.hasMethod(targetTypeWorld, requiredMethodExpression);
        }
    }
}