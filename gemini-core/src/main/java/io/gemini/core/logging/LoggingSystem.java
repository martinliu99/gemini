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

import io.gemini.core.DiagnosticLevel;
import io.gemini.core.config.ConfigView;
import io.gemini.core.logging.logback.LogbackLoggingSystem;
import io.gemini.core.util.Assert;

public interface LoggingSystem {

    static final String CONTEXT_NAME = "GeminiLogger";

    static final String LOGGER_CONFIG_LOCATION_KEY = "aop.logger.configLocation";
    static final String LOGGER_ALL_LOG_LEVEL_KEY = "aop.logger.allLogLevel";
    static final String LOGGER_INCLUDE_LOCATION_KEY = "aop.logger.includeLocation";


    void initialize(ClassLoader currentClassLoader);


    class Builder {

        private String configLocation;
        private ConfigView configView;
        private DiagnosticLevel diagnosticLevel;


        public Builder configLocation(String configLocation) {
            Assert.hasText(configLocation, "'configLocation' must not be empty.");
            this.configLocation = configLocation;

            return this;
        }

        public Builder configView(ConfigView configView) {
            Assert.notNull(configView, "'configView' must not be null.");
            this.configView = configView;

            return this;
        }

        public Builder diagnosticLevel(DiagnosticLevel diagnosticLevel) {
            this.diagnosticLevel = diagnosticLevel;

            return this;
        }

        public LoggingSystem build() {
//            return new Log4j2(configLocation, configView, diagnosticLevel);
            return new LogbackLoggingSystem(configLocation, configView, diagnosticLevel);
        }
    }
}
