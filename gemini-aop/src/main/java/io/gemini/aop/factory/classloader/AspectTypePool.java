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
/**
 * 
 */
package io.gemini.aop.factory.classloader;

import io.gemini.core.pool.TypePoolFactory;
import net.bytebuddy.agent.builder.AgentBuilder.LocationStrategy;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

/**
 * 
 */
public class AspectTypePool extends TypePool.Default.WithLazyResolution {

    private final AspectClassLoader aspectClassLoader;
    private final TypePoolFactory typePoolFactory;
    private final ElementMatcher<String> joinpointTypeMatcher;


    /**
     * @param cacheProvider
     * @param classFileLocator
     * @param readerMode
     * @param parentPool
     */
    public AspectTypePool(AspectClassLoader aspectClassLoader, TypePoolFactory typePoolFactory, ElementMatcher<String> joinpointTypeMatcher) {
        super(CacheProvider.Simple.withObjectType(), 
                LocationStrategy.ForClassLoader.WEAK.classFileLocator(aspectClassLoader, null), 
                ReaderMode.FAST, 
                typePoolFactory.createTypePool(aspectClassLoader.getParent(), null));

        this.aspectClassLoader = aspectClassLoader;
        this.typePoolFactory = typePoolFactory;
        this.joinpointTypeMatcher = joinpointTypeMatcher;
    }

    /**
     * {@inheritDoc}
     */
//    public Resolution doDescribe(String name) {
//        if(this.joinpointTypeMatcher.matches(name) == true) {
//            return describeViaJoinpointTypePool(name);
//        }
//
//        Resolution resolution = super.doDescribe(name);
//        return resolution.isResolved() ? resolution : describeViaJoinpointTypePool(name);
//    }
//
//    /**
//     * @param name
//     */
//    private Resolution describeViaJoinpointTypePool(String name) {
//        ClassLoader joinpointCL = aspectClassLoader.doFindJoinpointCL();
//        if(joinpointCL == null)
//            return new Resolution.Illegal(name);
//
//        TypePool typePool = this.typePoolFactory.createTypePool(joinpointCL, null);
//        return typePool.describe(name);
//    }

    protected Resolution doResolve(String name) {
        if(this.joinpointTypeMatcher.matches(name) == true) {
            return doResolveViaJoinpointTypePool(name);
        }

        return super.doResolve(name);
    }

    private Resolution doResolveViaJoinpointTypePool(String name) {
        ClassLoader joinpointCL = aspectClassLoader.doFindJoinpointCL();
        if(joinpointCL == null)
            return new Resolution.Illegal(name);

        TypePool typePool = this.typePoolFactory.createTypePool(joinpointCL, null);
        return typePool.describe(name);
    }

}
