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
 * Log4j2 {@link org.apache.logging.log4j.core.lookup.StrLookup} that resolves property
 * values from a {@link ConfigSource} stored in the {@link LoggerContext}.
 * <p>
 * Registered as a Log4j2 plugin under the {@code "logger"} category, allowing
 * {@code ${logger:key}} expressions in Log4j2 configuration files to be resolved
 * from the AOP framework's configuration.
 * </p>
 *
 * @author   martin.liu
 */
@Plugin(name = "logger", category = StrLookup.CATEGORY)
public class ConfigSourceLookup extends AbstractLookup implements LoggerContextAware {

    public static final String CONFIG_SOURCE_KEY = "configSource";


    private ConfigSource configSource;


    /**
     * Sets the {@link LoggerContext} and retrieves the {@link ConfigSource} stored under
     * {@code CONFIG_SOURCE_KEY}.
     *
     * @param loggerContext the Log4j2 logger context
     */
    @Override
    public void setLoggerContext(LoggerContext loggerContext) {
        if (loggerContext == null)
            return;

        this.configSource = (ConfigSource) loggerContext.getObject(CONFIG_SOURCE_KEY);
        this.configSource = this.configSource == null ? ConfigSource.Dummy.INSTANCE : this.configSource;
    }

    /**
     * Looks up the value for the given key in the {@link ConfigSource}.
     *
     * @param event the log event (unused)
     * @param key   the configuration key
     * @return the value as a string, or {@code null} if absent
     */
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

    /**
     * Evaluates the lookup for the given key and returns a {@link LookupResult}.
     *
     * @param event the log event (unused)
     * @param key   the configuration key
     * @return a {@link ConfigSourceLookupResult} if found, or {@code null}
     */
    @Override
    public LookupResult evaluate(LogEvent event, String key) {
        final String value = lookup(event, key);

        return value == null ? null : new ConfigSourceLookupResult(value);
    }

    /**
     * A {@link LookupResult} that wraps a string value from the {@link ConfigSource}.
     */
    public static class ConfigSourceLookupResult implements LookupResult {

        private final String value;

        /**
         * Constructs a {@code ConfigSourceLookupResult} with the given value.
         *
         * @param value the string value
         */
        public ConfigSourceLookupResult(String value) {
            this.value = value;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String value() {
            return value;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isLookupEvaluationAllowedInValue() {
            // evaluate variables in return value
            return true;
        }
    }
}
