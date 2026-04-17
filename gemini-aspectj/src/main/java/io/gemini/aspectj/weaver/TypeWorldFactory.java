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
 * Factory for creating {@link TypeWorld} instances scoped to a specific class loader.
 * <p>
 * The {@link Default} implementation caches one {@link TypeWorld} per class loader.
 * The {@link Default.TyepResolutionDetector} variant wraps the world to track which
 * type properties are accessed during pointcut matching.
 * </p>
 *
 * @author   martin.liu
 */
public interface TypeWorldFactory {

    /**
     * Creates or retrieves a {@link TypeWorld} scoped to the given class loader and Java module.
     *
     * @param classLoader the class loader that defines the type resolution scope
     * @param javaModule  the Java module associated with the class loader, or {@code null}
     * @return a {@link TypeWorld} for the given class loader; never {@code null}
     */
    TypeWorld createTypeWorld(ClassLoader classLoader, JavaModule javaModule);


    /**
     * Abstract base implementation providing a shared {@link #doCreateTypeWorld} factory method
     * that constructs a {@link BytebuddyWorld} from a given {@link TypePool} and optional
     * {@link PlaceholderHelper}. Subclasses override this method to return specialised world variants.
     */
    abstract class AbstractBase implements TypeWorldFactory {

        protected TypeWorld doCreateTypeWorld(TypePool typePool, PlaceholderHelper placeholderHelper) {
            return new BytebuddyWorld(typePool, placeholderHelper);
        }
    }


    /**
     * Default {@link TypeWorldFactory} implementation that caches one {@link TypeWorld} per
     * class loader. The cache uses weak references so entries are evicted when a class loader
     * is garbage-collected.
     */
    class Default extends AbstractBase {

        private final TypePoolFactory typePoolFactory;
        private final ConcurrentMap<ClassLoader, TypeWorld> typeWorldCache = new ConcurrentReferenceHashMap<>();


        /**
         * Creates a {@code Default} factory backed by the given {@link TypePoolFactory}.
         *
         * @param typePoolFactory the factory used to create a {@link TypePool} per class loader
         */
        public Default(TypePoolFactory typePoolFactory) {
            this.typePoolFactory = typePoolFactory;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public TypeWorld createTypeWorld(ClassLoader classLoader, JavaModule javaModule) {
            return this.typeWorldCache.computeIfAbsent(
                    ClassLoaderUtils.maskNull(classLoader), 
                    key -> doCreateTypeWorld(
                            typePoolFactory.createTypePool(classLoader, javaModule), 
                            null
                    )
            );
        }


        /**
         * A {@link Default} variant whose created {@link TypeWorld} instances additionally
         * track which type properties (superclass, interfaces) are accessed during pointcut
         * matching, enabling resolution-level inspection.
         */
        public static class TyepResolutionDetector extends Default  {

            /**
             * Creates a {@code TyepResolutionDetector} factory backed by the given {@link TypePoolFactory}.
             * The worlds it creates will track which type properties (superclass/interfaces) are
             * accessed during pointcut matching.
             *
             * @param typePoolFactory the factory used to create a {@link TypePool} per class loader
             */
            public TyepResolutionDetector(TypePoolFactory typePoolFactory) {
                super(typePoolFactory);
            }


            protected TypeWorld doCreateTypeWorld(TypePool typePool, PlaceholderHelper placeholderHelper) {
                return new BytebuddyWorld.TyepResolutionDetector(typePool, placeholderHelper);
            }
        }
    }
}