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
package io.gemini.aop.aspectory;

import java.io.Closeable;
import java.io.IOException;

import io.gemini.aspectj.weaver.world.TypeWorld;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AspectContext implements Closeable {

    private final String joinpointClassLoaderName;
    private final JavaModule javaModule;

    private final ClassLoader classLoader;
    private final ObjectFactory objectFactory;
    private final TypePool typePool;
    private final TypeWorld typeWorld;

    private final PlaceholderHelper placeholderHelper;

    private final ElementMatcher<String> defaultClassLoaderMatcher;

    private final boolean validateContext;

    private final boolean asmAutoCompute;


    protected AspectContext(AspectoryContext aspectoryContext, 
            String joinpointClassLoaderName, JavaModule javaModule,
            ClassLoader classLoader, ObjectFactory objectFactory, 
            TypePool typePool, TypeWorld typeWorld,
            ElementMatcher<String> defaultClassLoaderMatcher,
            boolean validateContext) {
        this.joinpointClassLoaderName = joinpointClassLoaderName;
        this.javaModule = javaModule;

        this.classLoader = classLoader;
        this.objectFactory = objectFactory;
        this.typePool = typePool;
        this.typeWorld = typeWorld;

        this.placeholderHelper = aspectoryContext.getPlaceholderHelper();

        this.defaultClassLoaderMatcher = defaultClassLoaderMatcher;

        this.validateContext = validateContext;

        this.asmAutoCompute = aspectoryContext.getAspectoriesContext().isAsmAutoCompute();
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


    public ElementMatcher<String> getDefaultClassLoaderMatcher() {
        return defaultClassLoaderMatcher;
    }

    public boolean isValidateContext() {
        return validateContext;
    }

    public boolean isASMAutoCompute() {
        return asmAutoCompute;
    }


    /* @see java.io.Closeable#close()
     */
    @Override
    public void close() throws IOException {
        this.typePool.clear();
        this.objectFactory.close();
    }
}