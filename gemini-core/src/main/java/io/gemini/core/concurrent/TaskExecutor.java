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
 * Executes tasks sequentially or in parallel using a cached thread pool.
 * <p>
 * When parallel mode is enabled, tasks are split into batches and submitted to an
 * {@link java.util.concurrent.ExecutorService}. An optional {@code executionWrapper}
 * function can be used to set thread-local state (e.g., context class loader) around
 * each batch execution.
 * </p>
 *
 * @author   martin.liu
 */
public interface TaskExecutor {

    static final int DEFAULT_BATCH_COUNT = Runtime.getRuntime().availableProcessors() - 1;
    static final int DEFAULT_TIMEOUT_MS = 0;


    /**
     * Returns {@code true} if this executor is configured to run tasks in parallel.
     *
     * @return {@code true} for parallel mode, {@code false} for sequential mode
     */
    boolean isParallel();

    /**
     * Executes the given tasks either sequentially or in parallel, depending on the
     * {@code parallel} flag and the executor's own parallel setting.
     * <p>
     * Tasks are split into at most {@code batchCount} batches. Each batch is submitted
     * to the internal thread pool. The optional {@code executionWrapper} can be used to
     * set thread-local state (e.g., context class loader) around each batch.
     * </p>
     *
     * @param <T>              the input task element type
     * @param <R>              the result type produced by {@code taskExecutor}
     * @param tasks            the collection of task inputs to process
     * @param taskExecutor     function applied to each task element to produce a result
     * @param parallel         whether to attempt parallel execution
     * @param batchCount       maximum number of parallel batches to split tasks into
     * @param executionWrapper optional wrapper applied around each batch's sequential
     *                         execution; may be {@code null}
     * @return a list of non-null results in encounter order; never {@code null}
     */
    <T, R> List<R> executeTasks(Collection<T> tasks, Function<T, R> taskExecutor, 
            boolean parallel, int batchCount, Function<Supplier<Collection<R>>, Collection<R>> executionWrapper);

    /**
     * Executes the given tasks using this executor's default parallel setting and
     * batch count, with no execution wrapper.
     *
     * @param <T>          the input task element type
     * @param <R>          the result type
     * @param tasks        the collection of task inputs to process
     * @param taskExecutor function applied to each task element
     * @return a list of non-null results; never {@code null}
     */
    default <T, R> List<R> executeTasks(Collection<T> tasks, Function<T, R> taskExecutor) {
        return executeTasks(tasks, taskExecutor, 
                isParallel(), DEFAULT_BATCH_COUNT, null);
    }

    /**
     * Executes the given tasks using this executor's default parallel setting and
     * batch count, wrapping each batch with the supplied {@code executionWrapper}.
     *
     * @param <T>              the input task element type
     * @param <R>              the result type
     * @param tasks            the collection of task inputs to process
     * @param taskExecutor     function applied to each task element
     * @param executionWrapper wrapper applied around each batch's sequential execution
     * @return a list of non-null results; never {@code null}
     */
    default <T, R> List<R> executeTasks(Collection<T> tasks, Function<T, R> taskExecutor, 
            Function<Supplier<Collection<R>>, Collection<R>> executionWrapper) {
        return executeTasks(tasks, taskExecutor, 
                isParallel(), Default.DEFAULT_BATCH_COUNT, executionWrapper);
    }


    /**
     * Shuts down the underlying thread pool, waiting up to 5 seconds for in-flight
     * tasks to complete. Has no effect if the executor is in sequential mode or has
     * already been shut down.
     */
    void shutdown();


    /**
     * Creates a parallel {@link TaskExecutor} with no task timeout.
     *
     * @param diagnosticLevel controls diagnostic logging verbosity
     * @param executorName    a human-readable name used in log messages and thread names
     * @return a new {@link TaskExecutor} in parallel mode
     */
    public static TaskExecutor create(DiagnosticLevel diagnosticLevel, String executorName) {
        return new Default(diagnosticLevel, executorName, true, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Creates a {@link TaskExecutor} with explicit parallel and timeout settings.
     *
     * @param diagnosticLevel controls diagnostic logging verbosity
     * @param executorName    a human-readable name used in log messages and thread names
     * @param parallel        {@code true} to enable parallel execution
     * @param taskTimeoutMs   per-task timeout in milliseconds; {@code 0} means no timeout
     * @return a new {@link TaskExecutor}
     */
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

                if (LOGGER.isInfoEnabled() && this.diagnosticLevel.isSimpleEnabled())
                    LOGGER.info("Initialized TaskExecutor '{}' in parallel mode.", executorName);
            } else {
                if (LOGGER.isInfoEnabled() && this.diagnosticLevel.isSimpleEnabled())
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
        public <T, R> List<R> executeTasks(Collection<T> tasks, Function<T, R> taskExecutor, 
                boolean parallel, int batchCount, Function<Supplier<Collection<R>>, Collection<R>> executionWrapper) {
            if (tasks.size() == 0 || taskExecutor == null)
                return Collections.emptyList();

            // execute sequentially or in parallel
            if (parallel == false || inParallel == false || terminated == true)
                return executeTaskSequentially(tasks, taskExecutor);

            return executeTasksInParallel(splitTasks(tasks, batchCount), taskExecutor, executionWrapper, tasks.size());
        }

        private <T, R> List<R> executeTaskSequentially(Collection<T> tasks, Function<T, R> taskExecutor) {
            List<R> resultList = new ArrayList<R>(tasks.size());
            for (T task : tasks) {
                R result = taskExecutor.apply(task);

                if (result != null) resultList.add(result);
            }
            return resultList;
        }

        private <T> List<List<T>> splitTasks(Collection<T> elements, int batchCount) {
            if (elements.size() == 0)
                return new ArrayList<>();

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
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not execute task {} with TaskExecutor '{}'.", 
                                taskExecutor, executorName, e.getCause());
                }
            }
            return resultList;
        }


        /**
         * {@inheritDoc}
         */
        @Override
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
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Interrupted while waiting for taskExecutor '{}' to terminate.", 
                            executorName);

                Thread.currentThread().interrupt();

                if (LOGGER.isInfoEnabled())
                    LOGGER.info("Shut down TaskExecutor '{}'.", executorName);
            }
        }
    }
}
