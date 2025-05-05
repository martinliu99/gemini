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
package io.gemini.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.status.StatusLogger;

import io.gemini.core.util.IOUtils;
import io.gemini.core.util.OrderedProperties;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class ConfigViews {

    private static final StatusLogger LOGGER = StatusLogger.getLogger();

    private static final String BUILTIN_SETTING_PREFIX = "_";


    public static ConfigSource createConfigSource(Map<String, String> launchArgs, Map<String, Object> builtinSettings,
            ClassLoader classLoader, String internalConfigLocation, Map<String, String> userDefinedConfigLocations) {
        launchArgs = launchArgs == null ? Collections.emptyMap() : launchArgs;

        // 1.load built-in and default settings
        // built-in settings, can NOT be overrode
        builtinSettings = builtinSettings == null ? new LinkedHashMap<>() : builtinSettings;
        // default settings, can be overrode by user-defined settings
        Map<String, Object> defaultSettings = new LinkedHashMap<>();

        String configName = "BuiltinSettings";
        loadSettings(classLoader, 
                internalConfigLocation,configName,  
                builtinSettings, defaultSettings, 
                true);


        // 2.load user-defined settings
        ConfigSource.Builder builder = new ConfigSource.Builder()
                .configSource("BuiltinSettings", builtinSettings)
                .configSource("LaunchArgs", launchArgs);

        for(Entry<String, String> entry : userDefinedConfigLocations.entrySet()) {
            String userDefinedConfigLocation = entry.getKey();

            Map<String, Object> userDefinedSettings = new LinkedHashMap<>();
            loadSettings(classLoader, 
                    userDefinedConfigLocation, entry.getValue(),  
                    null, userDefinedSettings, 
                    true);

            builder.configSource(userDefinedConfigLocation, userDefinedSettings);
        }

        return builder
                .configSource("DefaultSettings", defaultSettings)
                .build();
    }

    public static ConfigView createConfigView(ConfigView parentConfigView,
            ClassLoader classLoader, String internalConfigLocation, Map<String, String> userDefinedConfigLocations) {
        // 1.load built-in and default settings
        // built-in settings, can NOT be overrode
        Map<String, Object> builtinSettings = new LinkedHashMap<>();
        // default settings, can be overrode by user-defined settings
        Map<String, Object> defaultSettings = new LinkedHashMap<>();

        String configName = "BuiltinSettings";
        loadSettings(classLoader, 
                internalConfigLocation, configName, 
                builtinSettings, defaultSettings, 
                true);


        // 2.load user-defined settings
        ConfigView.Builder builder = new ConfigView.Builder()
                .parent(parentConfigView)
                .configSource(configName, builtinSettings);

        for(Entry<String, String> entry : userDefinedConfigLocations.entrySet()) {
            String userDefinedConfigLocation = entry.getKey();

            Map<String, Object> userDefinedSettings = new LinkedHashMap<>();
            loadSettings(classLoader, 
                    userDefinedConfigLocation, entry.getValue(),  
                    null, userDefinedSettings, 
                    false);

            builder.configSource(userDefinedConfigLocation, userDefinedSettings);
        }

        return builder
                .configSource("DefaultSettings", defaultSettings)
                .build();
    }

    private static void loadSettings(ClassLoader classLoader, 
            String propertiesFileLocation, String configName, 
            Map<String, Object> builtinSettings, Map<String, Object> settings, 
            boolean warnException) {
        InputStream inStream = classLoader.getResourceAsStream(propertiesFileLocation);
        if(inStream == null && warnException) {
            LOGGER.error("Did NOT find properties file '{} for '{}'. \n", propertiesFileLocation, configName);
            return;
        }

        try {
            OrderedProperties properties = new OrderedProperties();
            properties.load(inStream);

            for(String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);

                // built-in or regular setting
                if(builtinSettings != null && key.startsWith(BUILTIN_SETTING_PREFIX)) {
                    builtinSettings.put(key.substring(1), value);
                } else {
                    settings.put(key, value);
                }
            }
        } catch(IOException e) {
            if(warnException) {
                LOGGER.error("Failed to load properties file '{} for '{}'. \n", propertiesFileLocation, configName);
                e.printStackTrace(System.out);
            }
        } finally {
            IOUtils.closeQuietly(inStream);
        }
    }
}
