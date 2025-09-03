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
package io.gemini.aop.factory.classloader;

import java.io.IOException;
import java.io.InputStream;

import io.gemini.core.pool.TypePoolFactory;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.StreamDrainer;

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
    public Resolution describe(String name) {
        // check AspectClassLoader
        try {
            Resolution resolution = super.describe(name);
            if(resolution != null && resolution.isResolved())
                return resolution;
        } catch(Exception e) { }

        // check JoinpointClassLoader
        return doResolveViaJoinpointTypePool(name);
    }

    private Resolution doResolveViaJoinpointTypePool(String name) {
        ClassLoader joinpointCL = aspectClassLoader.getJoinpointClassLoader();
        if(joinpointCL == null)
            return new Resolution.Illegal(name);

        TypePool typePool = this.typePoolFactory.createTypePool(joinpointCL, null);
        return typePool.describe(name);
    }

    public Resolution describeAspect(String name) {
        return super.describe(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
    }


    static class AspectClassFileLocator implements ClassFileLocator {

        private final AspectClassLoader aspectClassLoader;

        public AspectClassFileLocator(AspectClassLoader aspectClassLoader) {
            this.aspectClassLoader = aspectClassLoader;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Resolution locate(String name) throws IOException {
            InputStream inputStream = aspectClassLoader.getAspectResourceAsStream(name.replace('.', '/') + CLASS_FILE_EXTENSION);
            if (inputStream != null) {
                try {
                    return new Resolution.Explicit(StreamDrainer.DEFAULT.drain(inputStream));
                } finally {
                    inputStream.close();
                }
            } else {
                return new Resolution.Illegal(name);
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
