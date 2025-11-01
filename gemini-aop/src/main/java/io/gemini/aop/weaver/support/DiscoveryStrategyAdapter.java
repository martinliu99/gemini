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
package io.gemini.aop.weaver.support;

import java.lang.instrument.Instrumentation;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.util.Assert;
import io.gemini.core.util.Throwables;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.DiscoveryStrategy;

public class DiscoveryStrategyAdapter implements DiscoveryStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryStrategyAdapter.class);

    private final DiscoveryStrategy delegatee;
    private final Listener listern;
    private final boolean disabled;

    public DiscoveryStrategyAdapter(DiscoveryStrategy delegatee, Listener listern, boolean disabled) {
        Assert.notNull(delegatee, "'delegatee' must not be null.");
        this.delegatee = delegatee;

        this.listern = listern;
        this.disabled = disabled;
    }

    @Override
    public Iterable<Iterable<Class<?>>> resolve(Instrumentation instrumentation) {
        if (listern != null) {
            try {
                listern.onStart();
            } catch (Throwable t) {
                LOGGER.warn("Failed to invoke onStart() method of '{}", listern.getClass());

                Throwables.throwIfRequired(t);
            }
        }

        // collector garbage before instrumenting loaded classes 
        // avoid exception such as 'IllegalStateException zip file closed'
        if (disabled == false)
            System.gc();

        return disabled 
                ? Collections.<Iterable<Class<?>>>emptyList()
                : delegatee.resolve(instrumentation);
    }

    public interface Listener {

        void onStart();

    }
}
