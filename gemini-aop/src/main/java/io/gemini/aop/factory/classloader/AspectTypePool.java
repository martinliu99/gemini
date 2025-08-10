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

import io.gemini.core.pool.TypePoolFactory;
import net.bytebuddy.pool.TypePool;

public class AspectTypePool implements TypePool {

    private final AspectClassLoader aspectClassLoader;
    private final TypePool aspectTypePool;
    private final TypePoolFactory typePoolFactory;


    public AspectTypePool(AspectClassLoader aspectClassLoader, TypePoolFactory typePoolFactory) {
        this.aspectClassLoader = aspectClassLoader;

        this.typePoolFactory = typePoolFactory;
        this.aspectTypePool = typePoolFactory.createTypePool(aspectClassLoader, null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Resolution describe(String name) {
        Resolution resolution = doResolveViaJoinpointTypePool(name);
        if(resolution != null && resolution.isResolved())
            return resolution;

        return aspectTypePool.describe(name);
    }

    private Resolution doResolveViaJoinpointTypePool(String name) {
        ClassLoader joinpointCL = aspectClassLoader.doFindJoinpointCL();
        if(joinpointCL == null)
            return new Resolution.Illegal(name);

        TypePool typePool = this.typePoolFactory.createTypePool(joinpointCL, null);
        return typePool.describe(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
    }
}
