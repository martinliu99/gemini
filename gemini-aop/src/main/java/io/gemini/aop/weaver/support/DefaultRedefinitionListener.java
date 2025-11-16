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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopMetrics;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.agent.builder.AgentBuilder;

public class DefaultRedefinitionListener implements AgentBuilder.RedefinitionStrategy.Listener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRedefinitionListener.class);

    private static final int DEFAULT = -1;

    private long startedAt = DEFAULT;

    private final DiagnosticLevel diagnosticLevel;
    private final AopMetrics.BootstraperMetrics bootstraperMetrics;


    public DefaultRedefinitionListener(DiagnosticLevel diagnosticLevel, AopMetrics aopMetrics) {
        this.diagnosticLevel = diagnosticLevel == null ? DiagnosticLevel.DISABLED : diagnosticLevel;
        bootstraperMetrics = aopMetrics.getBootstraperMetrics();
    }

    @Override
    public void onBatch(int index, List<Class<?>> batch, List<Class<?>> types) {
        if (this.startedAt == DEFAULT) {
            this.startedAt = System.nanoTime();
        }

        /* do nothing */
        if (diagnosticLevel.isSimpleEnabled())
            LOGGER.info("^Redefining {}/{} loaded types in batch {}.", 
                    batch.size(), types.size(), index);
    }

    @Override
    public Iterable<? extends List<Class<?>>> onError(int index, List<Class<?>> batch, Throwable throwable, List<Class<?>> types) {
        if (LOGGER.isWarnEnabled())
            LOGGER.warn("Could not redefine {}/{} loaded types in batch {}. \n"
                    + "  Error reason: {} \n"
                    + "  Types: \n"
                    + "    {} \n", 
                    batch.size(), types.size(), index, 
                    throwable.getMessage(),
                    StringUtils.join(batch, Class::toString, "\n    "),
                    throwable);

        return Collections.emptyList();
    }

    @Override
    public void onComplete(int amount, List<Class<?>> types, Map<List<Class<?>>, Throwable> failures) {
        if (startedAt == DEFAULT)
            return;

        long time = System.nanoTime() - startedAt;

        /* do nothing */
        if (types.size() > 0 && LOGGER.isInfoEnabled())
            LOGGER.info("$Took '{}' seconds to redefine {} loaded types in {} batchs.", 
                    time / AopMetrics.NANO_TIME, types.size(), amount);

        this.bootstraperMetrics.incrTypeRedefiningCount(types.size());
    }
}