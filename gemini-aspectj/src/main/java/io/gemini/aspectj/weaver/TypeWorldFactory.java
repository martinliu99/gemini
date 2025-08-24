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

import java.util.concurrent.ConcurrentMap;

import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.pool.TypePoolFactory;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface TypeWorldFactory {

    TypeWorld createTypeWorld(ClassLoader classLoader, JavaModule javaModule);

    TypeWorld createTypeWorld(TypePool typePool, PlaceholderHelper placeholderHelper);


    abstract class AbstractBase implements TypeWorldFactory {

        protected TypeWorld doCreateTypeWorld(TypePool typePool, PlaceholderHelper placeholderHelper) {
            return new BytebuddyWorld(typePool, placeholderHelper);
        }
    }


    class Default extends AbstractBase {

        private final TypePoolFactory typePoolFactory;
        private final ConcurrentMap<ClassLoader, TypeWorld> typeWorldCache = new ConcurrentReferenceHashMap<>();


        public Default(TypePoolFactory typePoolFactory) {
            this.typePoolFactory = typePoolFactory;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public TypeWorld createTypeWorld(ClassLoader classLoader, JavaModule javaModule) {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

            TypePool typePool = typePoolFactory.createTypePool(classLoader, javaModule);
            this.typeWorldCache.computeIfAbsent(
                    cacheKey, 
                    key -> new TypeWorld.CacheResolutionFacade(
                            new TypeWorld.LazyFacade(
                                    doCreateTypeWorld(typePool, null) ) )
            );

            return typeWorldCache.get(cacheKey);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public TypeWorld createTypeWorld(TypePool typePool, PlaceholderHelper placeholderHelper) {
            return doCreateTypeWorld(typePool, placeholderHelper);
        }
    }
}