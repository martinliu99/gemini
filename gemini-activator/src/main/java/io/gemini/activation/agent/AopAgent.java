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
package io.gemini.activation.agent;

import java.lang.instrument.Instrumentation;

import io.gemini.activation.AopActivator;

/**
 * Java agent entry point for the Gemini AOP framework.
 * This class is specified as the {@code Premain-Class} and {@code Agent-Class} in the agent JAR manifest.
 * It delegates all activation work to {@link AopActivator}.
 *
 * @author   martin.liu
 */
public class AopAgent {

    /**
     * Called by the JVM before the application's {@code main} method when the agent is loaded
     * via the {@code -javaagent} command-line option.
     *
     * @param agentArgs       comma-separated key=value pairs passed after the agent jar path
     * @param instrumentation the JVM instrumentation API instance
     * @throws Exception if activation fails
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) throws Exception {
        AopActivator.activateAop(agentArgs, instrumentation);
    }

    /**
     * Called when the agent is dynamically attached to a running JVM at runtime.
     * TODO: not supported
     *
     * @param agentArgs       comma-separated key=value pairs
     * @param instrumentation the JVM instrumentation API instance
     * @throws Exception if activation fails
     */
    public static void attach(String agentArgs, Instrumentation instrumentation) throws Exception {
        AopActivator.activateAop(agentArgs, instrumentation);
    }

}
