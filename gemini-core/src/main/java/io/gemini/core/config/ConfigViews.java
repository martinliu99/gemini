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
package io.gemini.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;

import io.gemini.core.DiagnosticLevel;
import io.gemini.core.converter.ConversionService;
import io.gemini.core.logging.DeferredLoggerFactory;
import io.gemini.core.util.IOUtils;
import io.gemini.core.util.OrderedProperties;

/**
 * Factory utility for creating and loading {@link ConfigView} instances from properties files
 * and launch arguments.
 * <p>
 * Supports two creation modes:
 * <ul>
 *   <li>Standalone: from launch args, built-in settings, and properties files</li>
 *   <li>Hierarchical: from a parent {@link ConfigView} with additional properties files</li>
 * </ul>
 * Also provides {@link #getDiagnosticLevel(ConfigView)} to read the configured
 * {@link io.gemini.core.DiagnosticLevel} from a config view.
 * </p>
 *
 * @author   martin.liu
 */
public class ConfigViews {

    private static final Logger LOGGER = DeferredLoggerFactory.getLogger(ConfigViews.class);

    private static final String BUILTIN_SETTING_PREFIX = "_";

    private static final String DIAGNOSTIC_LEVEL_KEY = "aop.launcher.diagnosticStrategy";


    /**
     * Creates a standalone {@link ConfigView} from launch arguments, built-in settings,
     * and properties files loaded from the given class loader.
     *
     * @param launchArgs                   key-value pairs from the AOP launcher command line
     * @param builtinSettings              immutable built-in settings (cannot be overridden)
     * @param classLoader                  the class loader used to load properties files
     * @param internalConfigLocation       classpath location of the internal properties file, or {@code null}
     * @param userDefinedConfigLocations   map of user-defined properties file locations to source names
     * @return the constructed config view
     */
    public static ConfigView createConfigView(Map<String, String> launchArgs, Map<String, Object> builtinSettings,
            ClassLoader classLoader, String internalConfigLocation, Map<String, String> userDefinedConfigLocations) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating ConfigView, ");


        launchArgs = launchArgs == null ? Collections.emptyMap() : launchArgs;

        // built-in settings, can NOT be overrode
        builtinSettings = builtinSettings == null ? new LinkedHashMap<>() : builtinSettings;

        ConfigView.Builder builder = new ConfigView.Builder()
                .configSource("BuiltinSettings", builtinSettings)
                .configSource("LaunchArgs", launchArgs);


        // 1.load built-in and default settings
        // default settings, can be overrode by user-defined settings
        Map<String, Object> defaultSettings = new LinkedHashMap<>();

        String configName = "BuiltinSettings";
        if (internalConfigLocation != null)
            loadSettings(classLoader, 
                    internalConfigLocation, configName,  
                    builtinSettings, defaultSettings);


        // 2.load user-defined settings
        for (Entry<String, String> entry : userDefinedConfigLocations.entrySet()) {
            String userDefinedConfigLocation = entry.getKey();

            Map<String, Object> userDefinedSettings = new LinkedHashMap<>();
            loadSettings(classLoader, 
                    userDefinedConfigLocation, entry.getValue(),  
                    null, userDefinedSettings);

            builder.configSource(userDefinedConfigLocation, userDefinedSettings);
        }

        ConfigView configView = builder
                .configSource("DefaultSettings", defaultSettings)
                .build();


        DiagnosticLevel diagnosticLevel = getDiagnosticLevel(configView);
        if (LOGGER.isInfoEnabled()) {
            if (diagnosticLevel.isDebugEnabled()) 
                LOGGER.info("Created ConfigView with settings, \n"
                        + "  LaunchArgs: {} \n"
                        + "  InternalConfigLocation: {} \n"
                        + "  UserDefinedConfigLocation: {} \n",
                        launchArgs, internalConfigLocation, userDefinedConfigLocations.keySet() 
                );
            else if (diagnosticLevel.isSimpleEnabled()) 
                LOGGER.info("Created ConfigView. ");
        }

        return configView;
    }

    /**
     * Creates a hierarchical {@link ConfigView} with the given parent and additional properties files.
     *
     * @param parentConfigView             the parent config view to fall back to
     * @param conversionService            the conversion service to use
     * @param classLoader                  the class loader used to load properties files
     * @param internalConfigLocation       classpath location of the internal properties file, or {@code null}
     * @param userDefinedConfigLocations   map of user-defined properties file locations to source names
     * @return the constructed config view
     */
    public static ConfigView createConfigView(ConfigView parentConfigView, ConversionService conversionService,
            ClassLoader classLoader, String internalConfigLocation, Map<String, String> userDefinedConfigLocations) {
        ConfigView.Builder builder = new ConfigView.Builder();
        if (parentConfigView != null)
            builder = builder.parent(parentConfigView);

        builder = builder.conversionService(conversionService);

        // built-in settings, can NOT be overrode
        Map<String, Object> builtinSettings = new LinkedHashMap<>();

        String configName = "BuiltinSettings";
        builder.configSource(configName, builtinSettings);


        // 1.load built-in and default settings
        // default settings, can be overrode by user-defined settings
        Map<String, Object> defaultSettings = new LinkedHashMap<>();
        if (internalConfigLocation != null)
            loadSettings(classLoader, 
                    internalConfigLocation, configName, 
                    builtinSettings, defaultSettings);


        // 2.load user-defined settings
        for (Entry<String, String> entry : userDefinedConfigLocations.entrySet()) {
            String userDefinedConfigLocation = entry.getKey();

            Map<String, Object> userDefinedSettings = new LinkedHashMap<>();
            loadSettings(classLoader, 
                    userDefinedConfigLocation, entry.getValue(),  
                    null, userDefinedSettings);

            builder.configSource(userDefinedConfigLocation, userDefinedSettings);
        }

        return builder
                .configSource("DefaultSettings", defaultSettings)
                .build();
    }

    private static void loadSettings(ClassLoader classLoader, 
            String propertiesFileLocation, String configName, 
            Map<String, Object> builtinSettings, Map<String, Object> settings) {
        InputStream inStream = classLoader.getResourceAsStream(propertiesFileLocation);
        if (inStream == null) {
            LOGGER.error("Did NOT find properties file '{} for '{}'. \n", propertiesFileLocation, configName);
            return;
        }

        try {
            OrderedProperties properties = new OrderedProperties();
            properties.load(inStream);

            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);

                // built-in or regular setting
                if (builtinSettings != null && key.startsWith(BUILTIN_SETTING_PREFIX)) {
                    builtinSettings.put(key.substring(1), value);
                } else {
                    settings.put(key, value);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not load properties file '{} for '{}'. \n", propertiesFileLocation, configName, e);
        } finally {
            IOUtils.closeQuietly(inStream);
        }
    }


    /**
     * Reads the {@code aop.launcher.diagnosticStrategy} key from the given config view
     * and returns the corresponding {@link DiagnosticLevel}.
     * Returns {@link DiagnosticLevel#DISABLED} if the key is absent or the value is invalid.
     *
     * @param configView the config view to read from
     * @return the configured diagnostic level
     */
    public static DiagnosticLevel getDiagnosticLevel(ConfigView configView) {
        if (configView.containsKey(DIAGNOSTIC_LEVEL_KEY)) {
            String level = configView.getAsString(DIAGNOSTIC_LEVEL_KEY).toUpperCase();
            try {
                return DiagnosticLevel.valueOf(level);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored illegal setting '{}' and disabled diagnostic mode.\n",
                            level);
            }
        }

        return DiagnosticLevel.DISABLED;
    }
}
