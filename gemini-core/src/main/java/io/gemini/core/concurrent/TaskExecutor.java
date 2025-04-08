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
package io.gemini.core.concurrent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface TaskExecutor {

    /**
     * 
     * @return
     */
    boolean isParallel();

    /**
     * Executes task sequentially or in parallel.
     * @param <T>
     * @param <R>
     * @param mapper
     * @param elements
     * @param parallel
     * @param batchSize
     * @return
     */
    <T, R> List<R> executeTasks(List<T> elements, Function<List<T>, List<R>> mapper, boolean parallel, int batchSize);

    default <T, R> List<R> executeTasks(List<T> elements, Function<List<T>, List<R>> mapper) {
        return executeTasks(elements, mapper, isParallel(), Default.BATCH_SIZE);
    }

    default <T, R> List<R> executeTasks(List<T> elements, Function<List<T>, List<R>> mapper, boolean parallel) {
        return executeTasks(elements, mapper, parallel, Default.BATCH_SIZE);
    }

    default <T, R> List<R> executeTasks(List<T> elements, Function<List<T>, List<R>> mapper, int batchSize) {
        return executeTasks(elements, mapper, isParallel(), batchSize);
    }

    /**
     * 
     */
    void shutdown();


    public static TaskExecutor create(String executorName) {
        return new Default(executorName, true);
    }

    public static TaskExecutor create(String executorName, boolean parallel) {
        return new Default(executorName, parallel);
    }


    class Default implements TaskExecutor {

        private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutor.class);

        private static final int BATCH_SIZE = Runtime.getRuntime().availableProcessors() - 1;

        private final String executorName;
        private final boolean parallel;

        private volatile boolean terminated = false;
        private ExecutorService executorService = null;


        protected Default(String executorName, boolean parallel) {
            this.executorName = executorName;
            this.parallel = parallel;

            if(this.parallel == true) {
                executorService = Executors.newCachedThreadPool( 
                        new DaemonThreadFactory(executorName) );
                LOGGER.info("TaskExecutor '{}' works in parallel mode.", executorName);
            } else {
                LOGGER.info("TaskExecutor '{}' works in sequential mode.", executorName);
            }
        }


        /*
         * @see io.gemini.core.concurrent.TaskExecutor#isParallel()
         */
        @Override
        public boolean isParallel() {
            return parallel;
        }

        /*
         * @see io.gemini.core.concurrent.TaskExecutor#executeTasks(java.util.List, java.util.function.Function, boolean, int)
         */
        public <T, R> List<R> executeTasks(List<T> elements, Function<List<T>, List<R>> mapper, boolean parallel, int batchSize) {
            if(elements.size() == 0 || mapper == null)
                return Collections.emptyList();

            // 1.execute sequentially
            if(parallel == false || terminated == true || elements.size() < batchSize || parallel == false)
                return mapper.apply(elements);

            // 2.execute in parallel
            int batchCount = elements.size() / batchSize;
            if(elements.size() % batchSize != 0)  batchCount++;

            // submit tasks
            List<Future<List<R>>> futures = new ArrayList<>(batchCount);
            for(int batch = 0; batch < batchCount; batch++) {
                int endIndex = Math.min((batch + 1) * batchSize, elements.size());
                List<T> subElements = elements.subList(batch * batchSize, endIndex );
                Future<List<R>> future = executorService.submit( () -> mapper.apply(subElements) );

                futures.add(future);
            }

            // collect result
            List<R> results = new ArrayList<>(elements.size());
            for(Future<List<R>> future : futures) {
                try {
                    List<R> result = future.get();
                    results.addAll(result);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch(ExecutionException e) {
                    LOGGER.warn("Failed to execute task {} with TaskExecutor '{}'.", mapper, executorName, e);
                }
            }
            return results;
        }

        /*
         * @see io.gemini.core.concurrent.TaskExecutor#shutdown()
         */
        public void shutdown() {
            if(parallel == false || terminated == true)
                return;

            terminated = true;

            ExecutorService executor = this.executorService;
            this.executorService = null;
            if(executor == null) return;
            if(executor.isShutdown() == true) return;

            // shut down execute
            executor.shutdown();

            // wait until timeout
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while waiting for taskExecutor '{}' to terminate.", executorName);

                Thread.currentThread().interrupt();

                LOGGER.info("Shut down TaskExecutor '{}'.", executorName);
            }
        }
    }
}
