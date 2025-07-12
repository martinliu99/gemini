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

    TypeWorld createTypeWorld(ClassLoader classLoader, JavaModule javaModule, 
            TypePool typePool, PlaceholderHelper placeholderHelper);


    enum WorkMode {

        SINGLETON, PROTOTYPE;
    }


    abstract class AbstractBase implements TypeWorldFactory {

        protected BytebuddyWorld doCreateTypeWorld(ClassLoader classLoader, JavaModule javaModule, 
                TypePool typePool, PlaceholderHelper placeholderHelper) {
            return new BytebuddyWorld(typePool, placeholderHelper);
        }
    }


    class Singleton extends AbstractBase {

        private final ConcurrentMap<ClassLoader, BytebuddyWorld> typeWorldCache = new ConcurrentReferenceHashMap<>();


        public Singleton() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public BytebuddyWorld createTypeWorld(ClassLoader classLoader, JavaModule javaModule, 
                TypePool typePool, PlaceholderHelper placeholderHelper) {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

            if(typeWorldCache.containsKey(cacheKey) == false) {
                this.typeWorldCache.computeIfAbsent(
                        cacheKey, 
                        key -> doCreateTypeWorld(classLoader, javaModule, typePool, placeholderHelper)
                );
            }

            return typeWorldCache.get(cacheKey);
        }
    }


    class Prototype extends AbstractBase {

        public Prototype() {
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public BytebuddyWorld createTypeWorld(ClassLoader classLoader, JavaModule javaModule, 
                TypePool typePool, PlaceholderHelper placeholderHelper) {
            return doCreateTypeWorld(classLoader, javaModule, typePool, placeholderHelper);
        }
    }
}