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
package io.gemini.aop.factory.classloader;

import java.io.IOException;
import java.io.InputStream;

import io.gemini.core.pool.TypePoolFactory;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.StreamDrainer;


/**
 * A ByteBuddy {@link TypePool} scoped to an aspect application.
 * <p>
 * Resolves type descriptions by first consulting the target class loader's type pool,
 * then falling back to the aspect class loader's own classpath. The
 * {@link #describeAspectType(String)} method bypasses the target class loader lookup
 * and resolves only from the aspect class loader's classpath.
 * </p>
 *
 * @author   martin.liu
 */
public class AspectTypePool extends TypePool.Default {

    private final AspectClassLoader aspectClassLoader;
    private final TypePoolFactory typePoolFactory;


    public AspectTypePool(AspectClassLoader aspectClassLoader, TypePoolFactory typePoolFactory) {
        super(new CacheProvider.Simple.UsingSoftReference(), new AspectClassFileLocator(aspectClassLoader), ReaderMode.FAST);

        this.aspectClassLoader = aspectClassLoader;
        this.typePoolFactory = typePoolFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypePool.Resolution describe(String name) {
        // 1.look up cache
        TypePool.Resolution resolution = this.cacheProvider.find(name);
        if (resolution != null && resolution.isResolved())
            return resolution;


        // 2.look up TargetClassLoader
        ClassLoader targetCL = aspectClassLoader.getTargetClassLoader();
        try {
            resolution = doResolveViaTargetTypePool(name, targetCL);
            if (resolution != null && resolution.isResolved())
                return resolution;
        } catch (Exception ignored) { /* do nothing */ }


        // 3.look up AspectClassLoader
        return super.describe(name);
    }

    private TypePool.Resolution doResolveViaTargetTypePool(String name, ClassLoader targetCL) {
        if (targetCL == null)
            return new TypePool.Resolution.Illegal(name);

        TypePool typePool = this.typePoolFactory.createTypePool(targetCL, null);
        return typePool.describe(name);
    }


    /**
     * Only resolve aspect relevant types to avoid target ClassLoader resource lookup.
     * @param name  type name
     * @return      resolved type
     */
    public TypePool.Resolution describeAspectType(String name) {
        return super.describe(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
    }


    /**
     * {@link ClassFileLocator} that reads class bytecode from the aspect class loader's
     * own classpath via {@link AspectClassLoader#getAspectResourceAsStream(String)}.
     */
    static class AspectClassFileLocator implements ClassFileLocator {

        private final AspectClassLoader aspectClassLoader;

        public AspectClassFileLocator(AspectClassLoader aspectClassLoader) {
            this.aspectClassLoader = aspectClassLoader;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ClassFileLocator.Resolution locate(String name) throws IOException {
            InputStream inputStream = aspectClassLoader.getAspectResourceAsStream(name.replace('.', '/') + CLASS_FILE_EXTENSION);
            if (inputStream != null) {
                try {
                    return new ClassFileLocator.Resolution.Explicit(StreamDrainer.DEFAULT.drain(inputStream));
                } finally {
                    inputStream.close();
                }
            } else {
                return new ClassFileLocator.Resolution.Illegal(name);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() throws IOException {
        }
    }
}
