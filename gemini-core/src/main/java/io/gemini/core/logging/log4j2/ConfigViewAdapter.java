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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.PropertySource;

import io.gemini.core.config.ConfigSource;
import io.gemini.core.config.ConfigView;
import io.gemini.core.logging.LoggingSystem;
import io.gemini.core.util.Assert;
import io.gemini.core.util.StringUtils;

/**
 * This class adapts {@code ConfigSource} to {@code PropertySource} to load log4j2 settings.
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class ConfigViewAdapter implements PropertySource, ConfigSource {

    public static final String STATUS_LOG_LEVEL_KEY = "aop.logger.statusLogLevel";

    private static final int DEFAULT_PRIORITY = 10000;
    private static final String SETTING_KEY_PREFIX = "aop.logger.";

    private static final Map<String, String> DEBUG_SETTINGS;

    private final ConfigView configView;
    private final boolean debug;

    private final Set<String> keys;

    static{
        DEBUG_SETTINGS = new HashMap<>();

        DEBUG_SETTINGS.put(SETTING_KEY_PREFIX + "log4j2.disableJmx", "false");

        DEBUG_SETTINGS.put(STATUS_LOG_LEVEL_KEY, Level.DEBUG.name());
        DEBUG_SETTINGS.put(LoggingSystem.LOGGER_ALL_LOG_LEVEL_KEY, Level.DEBUG.name());
        DEBUG_SETTINGS.put(LoggingSystem.LOGGER_INCLUDE_LOCATION_KEY, "true");
    }

    public ConfigViewAdapter(ConfigView configView, boolean debug) {
        Assert.notNull(configView, "'configView' must not be null.");
        this.configView = configView;

        this.debug = debug;

        this.keys = new LinkedHashSet<>();

        // filter out null && empty setting value
        for(String key : configView.keys()) {
            String value = configView.getAsString(key, null);
            if(value == null || StringUtils.hasText(value) == false)
                continue;

            this.keys.add(key);
        }

        if(debug)
            this.keys.addAll(DEBUG_SETTINGS.keySet());
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY;
    }


    @Override
    public void forEach(BiConsumer<String, String> action) {}

    @Override
    public Collection<String> getPropertyNames() {
        return keys;
    }

    public Collection<String> getLoggerPropertyNames() {
        return keys.stream()
                .filter( e -> 
                    e.startsWith(SETTING_KEY_PREFIX) )
                .collect(Collectors.toList());
    }

    @Override
    public String getProperty(String key) {
        String value = doGetProperty(SETTING_KEY_PREFIX + key);
        if(value != null)
            return (String) value;

        value = doGetProperty(key);
        if(value == null)
            return null;

        if(value instanceof String)
            return (String) value;

        throw new IllegalStateException("value '" + value + "' is not String.");
    }

    private String doGetProperty(String key) {
        if(debug) {
            String value = DEBUG_SETTINGS.get(key);
            if(value != null)
                return value;
        }

        return this.configView.getAsString(key, null);
    }

    @Override
    public boolean containsProperty(String key) {
        boolean contains = doContainsProperty(SETTING_KEY_PREFIX + key);
        if(contains)
            return true;

        return doContainsProperty(key);
    }

    private boolean doContainsProperty(String key) {
        if(debug && DEBUG_SETTINGS.containsKey(key))
            return true;

        return this.keys.contains(key);
    }

    @Override
    public Collection<String> keys() {
        return keys;
    }

    @Override
    public boolean containsKey(String key) {
        return this.containsProperty(key);
    }

    @Override
    public Object getValue(String key) {
        return this.getProperty(key);
    }

}
