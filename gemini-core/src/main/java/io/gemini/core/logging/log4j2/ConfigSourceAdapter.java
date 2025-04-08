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
import io.gemini.core.util.Assert;
import io.gemini.core.util.StringUtils;

/**
 * This class adapts {@code ConfigSource} to {@code PropertySource} to load log4j2 settings.
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class ConfigSourceAdapter implements PropertySource, ConfigSource {

    public static final String ALL_LOG_LEVEL_KEY = "aop.logger.allLogLevel";

    private static final int DEFAULT_PRIORITY = 10000;
    private static final String SETTING_KEY_PREFIX = "aop.logger.";

    private static final Map<String, String> DEBUG_SETTINGS;

    private final ConfigSource configSource;
    private final boolean debug;

    private final Set<String> keys;

    static{
        DEBUG_SETTINGS = new HashMap<>();

        DEBUG_SETTINGS.put(SETTING_KEY_PREFIX + "log4j2.disableJmx", "false");

        DEBUG_SETTINGS.put(ALL_LOG_LEVEL_KEY, Level.DEBUG.name());
        DEBUG_SETTINGS.put("aop.logger.includeLocation", "true");
    }

    public ConfigSourceAdapter(ConfigSource configSource, boolean debug) {
        Assert.notNull(configSource, "'configSource' must not be null.");
        this.configSource = configSource;

        this.debug = debug;

        this.keys = new LinkedHashSet<>();

        // filter out null && empty setting value
        for(String key : configSource.keys()) {
            Object value = configSource.getValue(key);
            if(value == null || value instanceof String == false)
                continue;

            String strValue = (String) value;
            if(StringUtils.hasText(strValue) == false)
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
        Object value = doGetProperty(SETTING_KEY_PREFIX + key);
        if(value != null && value instanceof String)
            return (String) value;

        value = doGetProperty(key);
        if(value == null)
            return null;

        if(value instanceof String)
            return (String) value;

        throw new IllegalStateException("value '" + value + "' is not String.");
    }

    private Object doGetProperty(String key) {
        if(debug) {
            String value = DEBUG_SETTINGS.get(key);
            if(value != null)
                return value;
        }

        return this.configSource.getValue(key);
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
