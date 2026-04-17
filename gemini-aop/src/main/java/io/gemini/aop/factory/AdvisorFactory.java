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
package io.gemini.aop.factory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.gemini.aop.Advisor;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.core.util.Assert;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;

/**
 * Factory interface for creating {@link Advisor} instances for a given target type.
 * <p>
 * Implementations scan aspect applications, parse advisor specifications, and return
 * a map of {@link MethodDescription} to matched {@link Advisor} lists for a given type.
 * The inner {@link Creator} enum constructs a {@link CompoundAdvisorFactory} that aggregates
 * all discovered aspect applications.
 * </p>
 *
 * @author   martin.liu
 */
public interface AdvisorFactory extends Closeable {

    /**
     * Returns a map of aspect-app name to the number of advisor specifications it contributed.
     *
     * @return map of factory name to advisor spec count
     */
    Map<String, Integer> getAdvisorSpecNum();

    /**
     * Returns a map of target method to matched advisors for the given type.
     *
     * @param targetType        the type being loaded
     * @param targetClassLoader the class loader loading the type
     * @param targetModule      the Java module of the type
     * @return map of method to matched advisor list, or empty map if no advisors matched
     */
    Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(
            TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule);

    /**
     * Closes this factory and releases all held resources.
     *
     * @throws IOException if an I/O error occurs during close
     */
    void close() throws IOException ;


    /**
     * Singleton factory that creates a {@link CompoundAdvisorFactory} from the given
     * {@link AopContext} and records creation timing in {@link io.gemini.aop.AopMetrics.LauncherMetrics}.
     */
    enum Creator {

        INSTANCE;


        /**
         * Creates a {@link CompoundAdvisorFactory} from the given {@link AopContext},
         * records creation timing in {@link io.gemini.aop.AopMetrics.LauncherMetrics},
         * and returns it as an {@link AdvisorFactory}.
         *
         * @param aopContext the central AOP context
         * @return the created {@link AdvisorFactory}
         */
        public AdvisorFactory create(AopContext aopContext) {
            Assert.notNull(aopContext, "'aopContext' must not be null.");

            long startedAt = System.nanoTime();
            AopMetrics.LauncherMetrics launcherMetrics = aopContext.getAopMetrics().getLauncherMetrics();
            CompoundAdvisorFactory advisorFactory = null;
            try {
                advisorFactory = new CompoundAdvisorFactory(aopContext);
                return advisorFactory;
            } finally {
                if (advisorFactory != null)
                    launcherMetrics.setAdvisorSpecs(advisorFactory.getAdvisorSpecNum());

                launcherMetrics.setAdvisorFactoryCreationTime(System.nanoTime() - startedAt);
            }
        }
    }
}
