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
package io.gemini.core.logging;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.gemini.core.DiagnosticLevel;
import io.gemini.core.config.ConfigSource;
import io.gemini.core.logging.log4j2.Log4j2LoggingSystem;

public class LoggingSystemTests {

    @Test
    public void simpleInitialization_withConfigFile() {
        LoggingSystem loggingSystem = new LoggingSystem.Builder()
                .configLocation("log4j2.xml")
                .configSource(getConfigSource(true))
                .diagnosticLevel(DiagnosticLevel.DEBUG)
                .build()
                ;
        loggingSystem.initialize(this.getClass().getClassLoader());
    }

    @Test
    public void simpleInitialization_withoutConfigFile() {
        LoggingSystem loggingSystem = new LoggingSystem.Builder()
                .configLocation("log4j2-wrong.xml")
                .configSource(getConfigSource(true))
                .diagnosticLevel(DiagnosticLevel.DEBUG)
                .build()
                ;
        loggingSystem.initialize(this.getClass().getClassLoader());
    }

    @Test
    public void customInitialization_withConfigFile() {
        LoggingSystem loggingSystem = new LoggingSystem.Builder()
                .configLocation("log4j2.xml")
                .configSource(getConfigSource(false))
                .diagnosticLevel(DiagnosticLevel.DEBUG)
                .build()
                ;
        loggingSystem.initialize(this.getClass().getClassLoader());
    }

    @Test
    public void customInitialization_withoutConfigFile() {
        LoggingSystem loggingSystem = new LoggingSystem.Builder()
                .configLocation("log4j2-wrong.xml")
                .configSource(getConfigSource(false))
                .diagnosticLevel(DiagnosticLevel.DEBUG)
                .build()
                ;
        loggingSystem.initialize(this.getClass().getClassLoader());
    }

    private ConfigSource getConfigSource(boolean simpleInitialization) {
        Map<String, Object> map = new HashMap<>();
        map.put(Log4j2LoggingSystem.SIMPLE_INITIALIZATION_KEY, simpleInitialization ? "true" : "false");

        ConfigSource configSource = new ConfigSource.Builder().configSource("", map).build();
        return configSource;
    }
}
