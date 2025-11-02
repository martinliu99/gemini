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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.DiagnosticLevel;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface TaskExecutor {

    static final int DEFAULT_BATCH_COUNT = Runtime.getRuntime().availableProcessors() - 1;
    static final int DEFAULT_TIMEOUT_MS = 0;


    /**
     * 
     * @return
     */
    boolean isParallel();

    /**
     * Executes task sequentially or in parallel
     * 
     * @param <T>
     * @param <R>
     * @param tasks
     * @param taskExecutor
     * @param parallel
     * @param batchCount
     * @return
     */
    <T, R> Collection<R> executeTasks(Collection<T> tasks, Function<T, R> taskExecutor, 
            boolean parallel, int batchCount, Function<Supplier<Collection<R>>, Collection<R>> executionWrapper);

    default <T, R> Collection<R> executeTasks(Collection<T> tasks, Function<T, R> taskExecutor) {
        return executeTasks(tasks, taskExecutor, 
                isParallel(), DEFAULT_BATCH_COUNT, null);
    }

    default <T, R> Collection<R> executeTasks(Collection<T> tasks, Function<T, R> taskExecutor, 
            Function<Supplier<Collection<R>>, Collection<R>> executionWrapper) {
        return executeTasks(tasks, taskExecutor, 
                isParallel(), Default.DEFAULT_BATCH_COUNT, executionWrapper);
    }


    /**
     * 
     */
    void shutdown();


    public static TaskExecutor create(DiagnosticLevel diagnosticLevel, String executorName) {
        return new Default(diagnosticLevel, executorName, true, DEFAULT_TIMEOUT_MS);
    }

    public static TaskExecutor create(DiagnosticLevel diagnosticLevel, String executorName, boolean parallel, int taskTimeoutMs) {
        return new Default(diagnosticLevel, executorName, parallel, taskTimeoutMs);
    }


    class Default implements TaskExecutor {

        private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutor.class);

        private final DiagnosticLevel diagnosticLevel;

        private final String executorName;
        private final boolean inParallel;
        private final int taskTimeoutMs;

        private volatile boolean terminated = false;
        private ExecutorService executorService = null;


        protected Default(DiagnosticLevel diagnosticLevel, String executorName, boolean inParallel, int taskTimeoutMs) {
            this.diagnosticLevel = diagnosticLevel == null ? DiagnosticLevel.DISABLED :diagnosticLevel;

            this.executorName = executorName;
            this.inParallel = inParallel;
            this.taskTimeoutMs = taskTimeoutMs;

            if (this.inParallel == true) {
                executorService = Executors.newCachedThreadPool( 
                        new DaemonThreadFactory(executorName) );

                if (this.diagnosticLevel.isSimpleEnabled())
                    LOGGER.info("Initialized TaskExecutor '{}' in parallel mode.", executorName);
            } else {
                if (this.diagnosticLevel.isSimpleEnabled())
                    LOGGER.info("Initialized TaskExecutor '{}' in sequential mode.", executorName);
            }
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isParallel() {
            return inParallel;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public <T, R> Collection<R> executeTasks(Collection<T> tasks, Function<T, R> taskExecutor, 
                boolean parallel, int batchCount, Function<Supplier<Collection<R>>, Collection<R>> executionWrapper) {
            if (tasks.size() == 0 || taskExecutor == null)
                return Collections.emptyList();

            // execute sequentially or in parallel
            if (parallel == false || inParallel == false || terminated == true)
                return executeTaskSequentially(tasks, taskExecutor);

            return executeTasksInParallel(splitTasks(tasks, batchCount), taskExecutor, executionWrapper, tasks.size());
        }

        private <T, R> Collection<R> executeTaskSequentially(Collection<T> tasks, Function<T, R> taskExecutor) {
            List<R> resultList = new ArrayList<R>(tasks.size());
            for (T task : tasks) {
                R result = taskExecutor.apply(task);

                if (result != null) resultList.add(result);
            }
            return resultList;
        }

        private <T> List<List<T>> splitTasks(Collection<T> elements, int batchCount) {
            if (elements.size() == 0)
                return Collections.emptyList();

            int avgEleCount = elements.size() / batchCount;
            int mod = elements.size() % batchCount;

            int slotEleCount = 0;
            int slotIndex = 0;
            ArrayList<List<T>> splitedTaskList = new ArrayList<>(batchCount);
            for (T ele : elements) {
                int curSlotTaskCount = avgEleCount + (slotIndex < mod ? 1 :0);
                List<T> splitedTasks = slotIndex < splitedTaskList.size() ? splitedTaskList.get(slotIndex) : null;
                if (splitedTasks == null) {
                    splitedTasks = new ArrayList<>( curSlotTaskCount );
                    splitedTaskList.add(splitedTasks);
                }

                splitedTasks.add(ele);

                if (++slotEleCount == curSlotTaskCount) {
                    slotEleCount =  0;
                    slotIndex++;
                }
            }
            return splitedTaskList;
        }

        /**
         * @param splitTasks
         * @param taskExecutor
         * @param taskCount
         * @return
         */
        private <T, R> List<R> executeTasksInParallel(List<List<T>> splitedTaskList, Function<T, R> taskExecutor,
                Function<Supplier<Collection<R>>, Collection<R>> executionWrapper, int taskCount) {
            // submit splitTasks
            List<Future<Collection<R>>> futures = new ArrayList<>(splitedTaskList.size());
            for (List<T> splitedTasks : splitedTaskList) {
                Future<Collection<R>> future = executorService.submit( 
                        () -> executionWrapper == null 
                            ? executeTaskSequentially(splitedTasks, taskExecutor)
                            : executionWrapper.apply( () -> executeTaskSequentially(splitedTasks, taskExecutor) )
                );

                futures.add(future);
            }

            // collect result
            List<R> resultList = new ArrayList<R>(taskCount);
            for (Future<Collection<R>> future : futures) {
                try {
                    Collection<R> results = taskTimeoutMs > 0 
                            ? future.get(taskTimeoutMs, TimeUnit.MILLISECONDS)
                            : future.get();

                    for (R result : results) {
                        if (result != null) resultList.add(result);
                    }
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                } catch (ExecutionException e) {
                    LOGGER.warn("Failed to execute task {} with TaskExecutor '{}'.", taskExecutor, executorName, e.getCause());
                }
            }
            return resultList;
        }


        /**
         * {@inheritDoc}
         */
        public void shutdown() {
            if (inParallel == false || terminated == true)
                return;

            terminated = true;

            ExecutorService executor = this.executorService;
            this.executorService = null;
            if (executor == null) return;
            if (executor.isShutdown() == true) return;

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
