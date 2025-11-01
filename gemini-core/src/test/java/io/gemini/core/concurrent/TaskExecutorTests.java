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
/**
 * 
 */
package io.gemini.core.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.DiagnosticLevel;

/**
 * 
 */
public class TaskExecutorTests {

    private final static Logger LOGGER = LoggerFactory.getLogger(TaskExecutorTests.class);

    @Test
    public void testTaskExeution() {
        TaskExecutor taskExecutor = TaskExecutor.create(DiagnosticLevel.SIMPLE, "test", true);

        int count = 140;
        List<Function<Integer, Integer>> tasks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tasks.add( index -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                return index;
            });
        }

        long startedAt = System.nanoTime();
        taskExecutor.executeTasks(
                tasks, 
                task -> task.apply(1));
        LOGGER.info("Took {} seconds to execute task {} times.", (System.nanoTime() - startedAt)/1e9, count);
    }
}
