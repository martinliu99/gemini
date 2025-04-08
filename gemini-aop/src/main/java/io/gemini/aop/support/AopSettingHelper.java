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
package io.gemini.aop.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import io.gemini.aop.AopException;
import io.gemini.aop.agent.bootstraper.AopBootstraperConfig;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.config.ConfigSource;
import io.gemini.core.util.Assert;
import io.gemini.core.util.OrderedProperties;
import io.gemini.core.util.StringUtils;

public class AopSettingHelper {

    public static final String BUILTIN_SETTING_PREFIX = "_";

    private static final String AOP_BOOTSTRAPER_ACTIVEPROFILE_DEFAULT = ""; 
    private static final String AOP_INTERNAL_PROPERTIES = "META-INF/aop-internal.properties";

    private static final String DIAGNOSTIC_LEVEL_KEY = "aop.agent.diagnosticStrategy";

    private final AopBootstraperConfig aopBootstraperConfig;

    private final String activeProfile;

    private final ConfigSource configSource;

    private final DiagnosticLevel diagnosticLevel;


    public AopSettingHelper(AopBootstraperConfig aopBootstraperConfig) {
        this(aopBootstraperConfig, new LinkedHashMap<>());
    }

    public AopSettingHelper(AopBootstraperConfig aopBootstraperConfig, Map<String, String> builtinSettings) {
        Assert.notNull(aopBootstraperConfig, "'aopBootstraperConfig' must not be null.");
        this.aopBootstraperConfig = aopBootstraperConfig;

        Map<String, String> agentArgs = this.loadAgentArgs(this.aopBootstraperConfig.getAgentArgs());
        this.activeProfile = this.parseActiveProfile(agentArgs);

        // built-in settings, can NOT be overrode
        builtinSettings = this.createBuiltinSettings(builtinSettings, aopBootstraperConfig);

        // default settings, can be overrode by user-defined settings
        Map<String, String> defaultSettings = new LinkedHashMap<>();
        Map<String, String> userDefinedSettings = new LinkedHashMap<>();
        this.loadAopSettings(builtinSettings, userDefinedSettings, defaultSettings);

        this.configSource = new ConfigSource.Builder()
                .configSource("BuiltinSettings", builtinSettings)
                .configSource("AgentArgs", agentArgs)
                .configSource(this.getUserDefinedConfigName(), userDefinedSettings)
                .configSource("DefaultSettings", defaultSettings)
                .build();

        String level = this.configSource.getValue(DIAGNOSTIC_LEVEL_KEY).toString().toUpperCase();
        DiagnosticLevel diagnosticLevel = DiagnosticLevel.DISABLED;
        try {
            diagnosticLevel = DiagnosticLevel.valueOf(level);
        } catch(Exception e) {
            System.err.println("Ignored illegal setting '" + level + "' and disabled diagnostic mode.\n");
        }
        this.diagnosticLevel = diagnosticLevel;
    }

    private Map<String, String> createBuiltinSettings(Map<String, String> builtinSettings, AopBootstraperConfig aopBootstraperConfig) {
        builtinSettings = builtinSettings == null ? new LinkedHashMap<>() : builtinSettings;
        builtinSettings.put("aop.agent.codeLocation", aopBootstraperConfig.getAgentCodeLocation());

        return builtinSettings;
    }


    private Map<String, String> loadAgentArgs(String agentArgStr) {
        if(StringUtils.hasText(agentArgStr) == false)
            return Collections.emptyMap();

        Map<String, String> agentArgs = new LinkedHashMap<>();

        // 1.try to load from JVM -javaagent:jar=key1=value1,key2=value2
        StringTokenizer st = new StringTokenizer(agentArgStr, ",");
        while(st.hasMoreTokens()) {
            String keyValuePair = st.nextToken();

            int pos = keyValuePair.indexOf("=");
            if(pos == -1 || pos >= keyValuePair.length())
                continue;   // ignore

            String key = keyValuePair.substring(0, pos).trim();
            String value = keyValuePair.substring(pos+1).trim();
            agentArgs.put(key, value);
        }

        return agentArgs;
    }

    private String parseActiveProfile(Map<String, String> agentArgs) {
        String activeProfile = agentArgs.get("aop.agent.activeProfile");
        return activeProfile == null ? AOP_BOOTSTRAPER_ACTIVEPROFILE_DEFAULT : activeProfile.trim();
    }

    private void loadAopSettings(Map<String, String> builtinSettings, 
            Map<String, String> userDefinedSettings, 
            Map<String, String> defaultSettings) {
        ClassLoader classLoader = this.getClass().getClassLoader();
        try {
            this.loadInternalSettings(classLoader, builtinSettings, defaultSettings);

            this.loadUserDefinedSettings(this.activeProfile, classLoader, userDefinedSettings);
        } catch (Throwable t) {
            throw new AopException("Failed to load Aop settings.", t);
        }
    }

    private void loadInternalSettings(ClassLoader classLoader, 
            Map<String, String> builtinSettings, Map<String, String> defaultSettings) throws IOException {
        OrderedProperties internalSettings = new OrderedProperties();
        internalSettings.load(
                classLoader.getResourceAsStream(getBuiltinConfigName()) );
        for(String key : internalSettings.stringPropertyNames()) {
            String value = internalSettings.getProperty(key);

            // built-in or default setting
            if(key.startsWith(BUILTIN_SETTING_PREFIX)) {
                builtinSettings.put(key.substring(1), value);
            } else {
                defaultSettings.put(key, value);
            }
        }
    }

    public String getBuiltinConfigName() {
        return AOP_INTERNAL_PROPERTIES;
    }

    private void loadUserDefinedSettings(String activeProfile, ClassLoader classLoader, 
            Map<String, String> userDefinedSettings) throws IOException {
        String userDefinedConfigName = this.getUserDefinedConfigName();
        InputStream inStream = this.getClass().getClassLoader().getResourceAsStream(userDefinedConfigName);
        if(inStream == null) {
            System.out.println("Did NOT find user defined configuration file '" + userDefinedConfigName + "', and ignored it.");
            return ;
        }

        userDefinedSettings.putAll(
                this.loadSettings(inStream) );
    }

    public String getUserDefinedConfigName() {
        return "aop" + (this.isDefaultProfile() ? "" : "-" + this.getActiveProfile()) + ".properties";
    }

    private Map<String, String> loadSettings(InputStream inStream) throws IOException {
        OrderedProperties properties = new OrderedProperties();
        try {
            properties.load(inStream);
        } finally {
            if(null != inStream) {
                try {
                    inStream.close();
                } catch (IOException ignored) { /* ignore */ }
            }
        }

        Map<String, String> settings = new LinkedHashMap<>();
        for(String key : properties.stringPropertyNames()) {
            settings.put(key, properties.getProperty(key));
        }

        return settings;
    }


    public AopBootstraperConfig getAopBootstraperConfig() {
        return aopBootstraperConfig;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public boolean isDefaultProfile() {
        return AOP_BOOTSTRAPER_ACTIVEPROFILE_DEFAULT.equals(activeProfile);
    }

    public ConfigSource getConfigSource() {
        return configSource;
    }

    public DiagnosticLevel getDiagnosticLevel() {
        return diagnosticLevel;
    }

    public String getLogConfigFileName() {
        return "aop-log4j2.xml";
    }
}