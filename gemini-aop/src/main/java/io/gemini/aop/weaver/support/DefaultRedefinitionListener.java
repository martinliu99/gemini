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

/**
 * ByteBuddy {@link AgentBuilder.RedefinitionStrategy.Listener} that logs batch progress
 * and records the total count of redefined types in {@link io.gemini.aop.AopMetrics.LauncherMetrics}.
 *
 * @author   martin.liu
 */
public class DefaultRedefinitionListener implements AgentBuilder.RedefinitionStrategy.Listener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRedefinitionListener.class);

    private static final int DEFAULT = -1;

    private long startedAt = DEFAULT;

    private final DiagnosticLevel diagnosticLevel;
    private final AopMetrics.LauncherMetrics launcherMetrics;


    public DefaultRedefinitionListener(DiagnosticLevel diagnosticLevel, AopMetrics aopMetrics) {
        this.diagnosticLevel = diagnosticLevel == null ? DiagnosticLevel.DISABLED : diagnosticLevel;
        launcherMetrics = aopMetrics.getLauncherMetrics();
    }

    /**
     * Called when a redefinition batch starts. Logs progress at INFO level when diagnostic mode is enabled.
     *
     * @param index the batch index
     * @param batch the types in this batch
     * @param types all types being redefined
     */
    @Override
    public void onBatch(int index, List<Class<?>> batch, List<Class<?>> types) {
        if (this.startedAt == DEFAULT) {
            this.startedAt = System.nanoTime();
        }

        /* do nothing */
        if (LOGGER.isInfoEnabled() && diagnosticLevel.isSimpleEnabled())
            LOGGER.info("^Redefining {}/{} loaded types in batch {}.", 
                    batch.size(), types.size(), index);
    }

    /**
     * Called when a redefinition batch fails. Logs the error and the affected types at WARN level.
     *
     * @param index     the batch index
     * @param batch     the types in the failed batch
     * @param throwable the error that occurred
     * @param types     all types being redefined
     * @return an empty iterable (no retry)
     */
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

    /**
     * Called when all redefinition batches have completed. Logs the total count and time,
     * and records the count in {@link io.gemini.aop.AopMetrics.LauncherMetrics}.
     *
     * @param amount   the number of batches processed
     * @param types    all types that were redefined
     * @param failures any per-batch failures
     */
    @Override
    public void onComplete(int amount, List<Class<?>> types, Map<List<Class<?>>, Throwable> failures) {
        if (startedAt == DEFAULT)
            return;

        /* do nothing */
        if (LOGGER.isInfoEnabled() && types.size() > 0)
            LOGGER.info("$Took '{}' seconds to redefine {} loaded types in {} batchs.", 
                    System.nanoTime() - startedAt / AopMetrics.NANO_TIME, types.size(), amount);

        this.launcherMetrics.incrTypeRedefiningCount(types.size());
    }
}