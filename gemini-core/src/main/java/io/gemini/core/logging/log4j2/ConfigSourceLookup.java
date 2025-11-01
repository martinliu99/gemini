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
package io.gemini.core.logging.log4j2;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerContextAware;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.AbstractLookup;
import org.apache.logging.log4j.core.lookup.LookupResult;
import org.apache.logging.log4j.core.lookup.StrLookup;

import io.gemini.core.config.ConfigSource;

/**
 * This class looks up property in configuration file via {@code ConfigSource}.
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
@Plugin(name = "logger", category = StrLookup.CATEGORY)
public class ConfigSourceLookup extends AbstractLookup implements LoggerContextAware {

    public static final String CONFIG_SOURCE_KEY = "configSource";


    private ConfigSource configSource;


    @Override
    public void setLoggerContext(LoggerContext loggerContext) {
        if (loggerContext == null)
            return;

        this.configSource = (ConfigSource) loggerContext.getObject(CONFIG_SOURCE_KEY);
        this.configSource = this.configSource == null ? ConfigSource.Dummy.INSTANCE : this.configSource;
    }

    @Override
    public String lookup(LogEvent event, String key) {
        if (configSource.containsKey(key) == false)
            return null;

        Object value = configSource.getValue(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    public LookupResult evaluate(LogEvent event, String key) {
        final String value = lookup(event, key);

        return value == null ? null : new ConfigSourceLookupResult(value);
    }

    public static class ConfigSourceLookupResult implements LookupResult {

        private final String value;

        public ConfigSourceLookupResult(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public boolean isLookupEvaluationAllowedInValue() {
            // evaluate variables in return value
            return true;
        }
    }
}
