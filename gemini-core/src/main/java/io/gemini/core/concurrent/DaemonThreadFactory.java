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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ThreadFactory} that creates daemon threads named with a consistent prefix.
 * <p>
 * All threads created by this factory are daemon threads with normal priority,
 * so they will not prevent JVM shutdown.
 * </p>
 *
 * @author   martin.liu
 */
public class DaemonThreadFactory implements ThreadFactory {

    private static final String nameSuffix = "]";

    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;


    /**
     * Creates a factory whose threads belong to the current thread group (or the
     * security manager's thread group) and are named
     * {@code Gemini-<poolName>[Thread-N]}.
     *
     * @param poolName the logical name of the pool, embedded in every thread name
     */
    public DaemonThreadFactory(String poolName) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() :
                              Thread.currentThread().getThreadGroup();
        namePrefix = "Gemini-" + poolName + "[Thread-";
    }

    /**
     * Creates a factory whose threads belong to the given {@link ThreadGroup} and
     * are named {@code Gemini-<poolName>[Thread-N]}.
     *
     * @param poolName    the logical name of the pool, embedded in every thread name
     * @param threadGroup the thread group to assign to every created thread
     */
    public DaemonThreadFactory(String poolName, ThreadGroup threadGroup) {
        group = threadGroup;
        namePrefix = "Gemini-" + poolName + "[Thread-";
    }

    /**
     * Returns the {@link ThreadGroup} that all threads created by this factory
     * will belong to.
     *
     * @return the thread group; never {@code null}
     */
    public ThreadGroup getThreadGroup() {
        return group;
    }

    /**
     * Creates a new daemon thread with normal priority that executes the given
     * {@link Runnable}. The thread is named {@code Gemini-<poolName>[Thread-N]}
     * where {@code N} is a monotonically increasing counter.
     *
     * @param r the runnable to execute in the new thread
     * @return a new daemon thread ready to be started; never {@code null}
     */
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group,
                              r,
                              namePrefix +
                              threadNumber.getAndIncrement() +
                              nameSuffix,
                              0);

        t.setDaemon(true);

        if (t.getPriority() != Thread.NORM_PRIORITY)
            t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
}