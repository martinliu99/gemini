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
package io.gemini.core.pool;

import java.util.concurrent.ConcurrentMap;

import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.ClassFileBufferStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.LocationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

public interface TypePoolFactory {

    AgentBuilder customizeAgentBuilder(AgentBuilder agentBuilder);

    ExplicitTypePool createExplicitTypePool(ClassLoader classLoader, JavaModule javaModule);

    ExplicitTypePool getExplicitTypePool(ClassLoader classLoader);


    class Default implements TypePoolFactory {

        private ClassFileLocator classFileLocator;
        private LocationStrategy locationStrategy;
        private PoolStrategy poolStrategy;
        private ClassFileBufferStrategy classFileBufferStrategy;

        private final ConcurrentMap<ClassLoader, ExplicitTypePool> explicitTypePoolCache = new ConcurrentReferenceHashMap<>();


        public Default() {
            this(null, null, null, null);
        }

        public Default(ClassFileLocator classFileLocator, LocationStrategy locationStrategy, 
                PoolStrategy poolStrategy, ClassFileBufferStrategy classFileBufferStrategy) {
            this.classFileLocator = classFileLocator == null ? ClassFileLocator.NoOp.INSTANCE : classFileLocator;
            this.locationStrategy = locationStrategy == null ? LocationStrategy.ForClassLoader.WEAK : locationStrategy;
            this.poolStrategy = poolStrategy == null ? PoolStrategy.Default.FAST : poolStrategy;
            this.classFileBufferStrategy = classFileBufferStrategy == null ? ClassFileBufferStrategy.Default.RETAINING : classFileBufferStrategy;
        }


        /*
         * @see io.gemini.core.pool.TypePoolFactory#customizeAgentBuilder(net.bytebuddy.agent.builder.AgentBuilder)
         */
        @Override
        public AgentBuilder customizeAgentBuilder(AgentBuilder agentBuilder) {
            Assert.notNull(agentBuilder, "'agentBuilder' must not be null.");

            return agentBuilder
                    .with(poolStrategy)
                    .with(locationStrategy)
                    .with(classFileLocator)
                    .with(classFileBufferStrategy)
                    ;
        }

        /*
         * @see io.gemini.core.pool.TypePoolFactory#createExplicitTypePool(java.lang.ClassLoader, net.bytebuddy.utility.JavaModule)
         */
        @Override
        public ExplicitTypePool createExplicitTypePool(ClassLoader classLoader, JavaModule javaModule) {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

            if(this.explicitTypePoolCache.containsKey(cacheKey) == false) {
                this.explicitTypePoolCache.computeIfAbsent(
                        cacheKey, 
                        key -> new ExplicitTypePool(this.doCreateTypePool(classLoader, javaModule))
                );
            }

            return this.explicitTypePoolCache.get(cacheKey);
        }

        protected TypePool doCreateTypePool(ClassLoader classLoader, JavaModule javaModule) {
            ClassFileLocator[] classFileLocators = new ClassFileLocator[2];
            classFileLocators[0] = this.classFileLocator;
            classFileLocators[1] = this.locationStrategy.classFileLocator(classLoader, javaModule);
            ClassFileLocator classFileLocator = new ClassFileLocator.Compound(classFileLocators);

            return classFileBufferStrategy.typePool(poolStrategy, classFileLocator, null, ClassLoaderUtils.getClassLoaderId(classLoader));
        }


        /*
         * @see io.gemini.core.pool.TypePoolFactory#getExplicitTypePool(java.lang.ClassLoader)
         */
        @Override
        public ExplicitTypePool getExplicitTypePool(ClassLoader classLoader) {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);
            return this.explicitTypePoolCache.get(cacheKey);
        }
    }
}
