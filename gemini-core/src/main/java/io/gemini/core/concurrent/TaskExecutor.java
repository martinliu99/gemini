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
import java.util.stream.Stream;

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


    <T> List<List<T>> splitTasks(List<T> elements, int batchSize);

    default <T> List<List<T>> splitTasks(List<T> elements) {
        return splitTasks(elements, Default.BATCH_SIZE);
    }


    /**
     * Executes task sequentially or in parallel
     * 
     * @param <T>
     * @param <M>
     * @param elements
     * @param elementMapper
     * @param parallel
     * @return
     */
    <T, M> Stream<M> executeTasks(List<T> elements, Function<T, M> elementMapper, boolean parallel);

    default <T, M> Stream<M> executeTasks(List<T> elements, Function<T, M> elementMapper) {
        return executeTasks(elements, elementMapper, isParallel());
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


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isParallel() {
            return parallel;
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        public <T> List<List<T>> splitTasks(List<T> elements, int batchSize) {
            if(elements.size() == 0)
                return Collections.emptyList();

            int batchCount = elements.size() / batchSize;
            if(elements.size() % batchSize != 0)  batchCount++;

            List<List<T>> splitedTasks = new ArrayList<>(batchCount);
            for(int batch = 0; batch < batchSize; batch++) {
                int endIndex = Math.min((batch + 1) * batchCount, elements.size());

                splitedTasks.add( elements.subList(batch * (batchCount-1), endIndex) );
            }
            return splitedTasks;
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public <T, M> Stream<M> executeTasks(List<T> elements, Function<T, M> elementMapper, boolean parallel) {
            if(elements.size() == 0 || elementMapper == null)
                return Stream.empty();

            // execute sequentially or in parallel
            return parallel == false || terminated == true || parallel == false
                    ? elements.stream().map(elementMapper).filter( e -> e != null)
                    : executeInParallel(elements, elementMapper);
        }


        protected <T, M> Stream<M> executeInParallel(List<T> elements, Function<T, M> elementMapper) {
            // submit tasks
            List<Future<M>> futures = new ArrayList<>(elements.size());
            for(T element : elements) {
                futures.add( 
                        executorService.submit( 
                                () -> elementMapper.apply(element) ) );
            }

            // collect result
            return futures.stream().map( future -> {
                        try {
                            return future.get();
                        } catch(InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch(ExecutionException e) {
                            LOGGER.warn("Failed to execute task {} with TaskExecutor '{}'.", elementMapper, executorName, e);
                        }
                        return null;
                } )
                .filter( e -> e != null)
                ;
        }


        /**
         * {@inheritDoc}
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
