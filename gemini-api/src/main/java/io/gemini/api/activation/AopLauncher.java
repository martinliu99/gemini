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
package io.gemini.api.activation;

import java.lang.instrument.Instrumentation;

import io.gemini.api.classloader.AopClassLoader;

/**
 * SPI interface for launching the Gemini AOP framework.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/io.gemini.api.activation.AopLauncher}.
 * The default implementation is {@code io.gemini.aop.activation.DefaultAopLauncher}.
 * </p>
 *
 * @author   martin.liu
 */
public interface AopLauncher {

    /**
     * Starts the AOP framework, builds advisor factories and aop weaver, 
     * matches and begins class transformation.
     *
     * @param instrumentation the JVM instrumentation API instance
     * @param launcherConfig  configuration describing paths, profiles, and classpath URLs
     * @param aopClassLoader  the isolated class loader for AOP framework classes
     */
    void start(Instrumentation instrumentation, 
            LauncherConfig launcherConfig, 
            AopClassLoader aopClassLoader);

    /**
     * Stops the AOP framework and releases all held resources.
     */
    void stop();
}
