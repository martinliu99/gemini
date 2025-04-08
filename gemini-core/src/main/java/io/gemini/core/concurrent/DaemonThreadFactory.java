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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author   martin.liu
 * @since    1.0
 *
 */
public class DaemonThreadFactory implements ThreadFactory {

    private static final String nameSuffix = "]";

    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;


    public DaemonThreadFactory(String poolName) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() :
                              Thread.currentThread().getThreadGroup();
        namePrefix = "Gemini-" + poolName + "[Thread-";
    }

    public DaemonThreadFactory(String poolName, ThreadGroup threadGroup) {
        group = threadGroup;
        namePrefix = "Gemini-" + poolName + "[Thread-";
    }

    public ThreadGroup getThreadGroup() {
        return group;
    }

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