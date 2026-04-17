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
 * Per-target-ClassLoader context for advisor creation within a single aspect application.
 * <p>
 * Holds the {@link AspectClassLoader}, {@link ObjectFactory}, {@link TypePool}, and
 * {@link TypeWorld} scoped to a specific target class loader, along with a
 * {@link MatchingContext} for evaluating {@code @Conditional} annotations.
 * </p>
 *
 * @author   martin.liu
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


    /** 
     * Returns the parent {@link FactoryContext} that owns this advisor context. 
     */
    public FactoryContext getFactoryContext() {
        return factoryContext;
    }

    /** 
     * Returns the human-readable name of the target class loader. 
     */
    public String getTargetClassLoaderName() {
        return targetClassLoaderName;
    }

    /** 
     * Returns the Java module of the target class loader, or {@code null} if not applicable. 
     */
    public JavaModule getTargetJavaModule() {
        return targetJavaModule;
    }

    /** 
     * Returns the {@link AspectClassLoader} used to load aspect and advice classes. 
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /** 
     * Returns the object factory for instantiating advice objects within this context. 
     */
    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    /** 
     * Returns the ByteBuddy type pool for resolving aspect-side type descriptions. 
     */
    public TypePool getTypePool() {
        return typePool;
    }

    /** 
     * Returns the AspectJ type world for pointcut expression evaluation. 
     */
    public TypeWorld getTypeWorld() {
        return typeWorld;
    }

    /** 
     * Returns the placeholder helper for resolving {@code ${key}} expressions in pointcut expressions. 
     */
    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }

    /** 
     * Returns the matching context used to evaluate {@code @Conditional} annotations. 
     */
    public MatchingContext getMatchingContext() {
        return matchingContext;
    }

    /**
     * Returns {@code true} if the target class loader is accepted by the factory's class loader filter.
     *
     * @return {@code true} if this context's class loader is a valid target
     */
    public boolean acceptTargetClassloader() {
        return factoryContext.acceptTargetClassLoader(classLoader.getTargetClassLoader());
    }

    /**
     * Returns {@code true} if this context is in validation mode (used during startup to eagerly
     * detect invalid advisor specifications).
     *
     * @return {@code true} if validation mode is active
     */
    public boolean isValidateContext() {
        return validateContext;
    }

    /** 
     * Returns {@code true} if ASM frame computation should be performed automatically. 
     */
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


    /**
     * Default {@link MatchingContext} implementation backed by the aspect class loader's
     * target type pool and type world. Evaluates class loader identity and structural
     * presence checks for {@code @Conditional} annotations.
     */
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

        /** 
         * {@inheritDoc} 
         */
        @Override
        public boolean hasConstructor(String requiredConstructorExpression) {
            return ExprParser.INSTANCE.hasConstructor(targetTypeWorld, requiredConstructorExpression);
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public boolean hasMethod(String requiredMethodExpression) {
            return ExprParser.INSTANCE.hasMethod(targetTypeWorld, requiredMethodExpression);
        }
    }
}