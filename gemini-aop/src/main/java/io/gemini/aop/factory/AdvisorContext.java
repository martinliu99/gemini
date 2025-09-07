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
package io.gemini.aop.factory;

import java.io.Closeable;
import java.io.IOException;

import io.gemini.aop.factory.classloader.AspectClassLoader;
import io.gemini.api.aop.condition.ConditionContext;
import io.gemini.api.classloader.ClassLoaders;
import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.aspectj.weaver.TypeWorldFactory;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.pool.TypePoolFactory;
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

    private final String joinpointClassLoaderName;
    private final JavaModule javaModule;

    private final AspectClassLoader classLoader;
    private final ObjectFactory objectFactory;

    private final TypePoolFactory typePoolFactory;
    private final TypePool typePool;

    private final TypeWorldFactory typeWorldFactory;
    private final TypeWorld typeWorld;

    private final PlaceholderHelper placeholderHelper;

    private final boolean validateContext;

    private final ConditionContext conditionContext;

    private final boolean asmAutoCompute;


    protected AdvisorContext(FactoryContext factoryContext, 
            String joinpointClassLoaderName, JavaModule javaModule,
            AspectClassLoader classLoader, ObjectFactory objectFactory, 
            TypePoolFactory typePoolFactory, TypePool typePool, 
            TypeWorldFactory typeWorldFactory, TypeWorld typeWorld,
            boolean validateContext) {
        this.joinpointClassLoaderName = joinpointClassLoaderName;
        this.javaModule = javaModule;

        this.classLoader = classLoader;
        this.objectFactory = objectFactory;

        this.typePoolFactory = typePoolFactory;
        this.typePool = typePool;

        this.typeWorldFactory = typeWorldFactory;
        this.typeWorld = typeWorld;

        this.placeholderHelper = factoryContext.getPlaceholderHelper();

        this.validateContext = validateContext;

        this.conditionContext = new DefultConditionContext();

        this.asmAutoCompute = factoryContext.getFactoriesContext().isAsmAutoCompute();
    }


    public String getJoinpointClassLoaderName() {
        return joinpointClassLoaderName;
    }

    public JavaModule getJavaModule() {
        return javaModule;
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


    public ConditionContext getConditionContext() {
        return conditionContext;
    }

    public boolean isValidateContext() {
        return validateContext;
    }


    public boolean isASMAutoCompute() {
        return asmAutoCompute;
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


    private class DefultConditionContext implements ConditionContext {

        private final TypePool typePool;
        private final TypeWorld typeWorld;

        public DefultConditionContext() {
            this.typePool = typePoolFactory.createTypePool(classLoader.getJoinpointClassLoader(), javaModule);
            this.typeWorld = typeWorldFactory.createTypeWorld(classLoader.getJoinpointClassLoader(), javaModule);
        }

        @Override
        public TypePool getTypePool() {
            return typePool;
        }

        @Override
        public boolean isBootstrapClassLoader() {
            return ClassLoaders.isBootstrapClassLoader(classLoader.getJoinpointClassLoader());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isExtClassLoader() {
            return ClassLoaders.isExtClassLoader(classLoader.getJoinpointClassLoader());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isAppClassLoader() {
            return ClassLoaders.isAppClassLoader(classLoader.getJoinpointClassLoader());

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isClassLoader(String classLoaderExpr) {
            return ExprParser.INSTANCE.parseClassLoaderExpr(classLoaderExpr)
                    .matches(classLoader.getJoinpointClassLoader());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasType(String typeExpr) {
            return ExprParser.INSTANCE.hasType(typeWorld, typeExpr);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasFiled(String fieldExpr) {
            return ExprParser.INSTANCE.hasField(typeWorld, fieldExpr);
        }

        /** {@inheritDoc} 
         */
        @Override
        public boolean hasConstructorExpr(String constructorExpr) {
            return ExprParser.INSTANCE.hasConstructor(typeWorld, constructorExpr);
        }

        /** {@inheritDoc} 
         */
        @Override
        public boolean hasMethodExpr(String methodsExpr) {
            return ExprParser.INSTANCE.hasMethod(typeWorld, methodsExpr);
        }
    }
}