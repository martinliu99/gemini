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

import java.net.URL;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.status.StatusConsoleListener;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.DiagnosticLevel;
import io.gemini.core.config.ConfigSource;
import io.gemini.core.logging.log4j2.ConfigSourceAdapter;
import io.gemini.core.logging.log4j2.ConfigSourceLookup;
import io.gemini.core.util.Assert;
import io.gemini.core.util.StringUtils;

public interface LoggingSystem {

    void initialize(ClassLoader currentClassLoader);


    class Log4j2 implements LoggingSystem {

        private static final String LOG_CONFIG_LOCATION_KEY = "aop.logger.configLocation";
        static final String SIMPLE_INITIALIZATION_KEY = "aop.logger.simpleInitialization";

        private static final String INTERNAL_CONFIGURATION_FILE = "META-INF/aop-log4j2.xml";
        private static final String CONTEXT_NAME = "GeminiLogger";
        private static final StatusLogger LOGGER = StatusLogger.getLogger();

        private final String configLocation;
        private final ConfigSourceAdapter configSource;
        private final DiagnosticLevel diagnosticLevel;

        private final boolean simpleInitialization;

        private final Level defaultStatusLevel = LOGGER.getLevel();
        private final Level statusLevel;

        private final Level allLogLevel;


        Log4j2(String configLocation, ConfigSource configSource,
                DiagnosticLevel diagnosticLevel) {
            this.diagnosticLevel = diagnosticLevel;

            this.configSource =  new ConfigSourceAdapter(configSource, diagnosticLevel.isDebugEnabled());


            if(StringUtils.hasText(configLocation))
                this.configLocation = configLocation;
            else if(this.configSource.containsKey(LOG_CONFIG_LOCATION_KEY))
                this.configLocation = this.configSource.getProperty(LOG_CONFIG_LOCATION_KEY).trim();
            else
                this.configLocation = INTERNAL_CONFIGURATION_FILE;


            if(this.configSource.containsProperty(SIMPLE_INITIALIZATION_KEY)) {
                String value = this.configSource.getProperty(SIMPLE_INITIALIZATION_KEY);
                this.simpleInitialization = value == null || Boolean.valueOf(value) == true ? true : false;
            } else {
                this.simpleInitialization = true;
            }

            if(this.configSource.containsProperty(ConfigSourceAdapter.STATUS_LOG_LEVEL_KEY)) {
                String logLevel = this.configSource.getProperty(ConfigSourceAdapter.STATUS_LOG_LEVEL_KEY).toString().trim();
                this.statusLevel = StringUtils.hasText(logLevel) ? Level.toLevel(logLevel) : null;
            } else
                this.statusLevel = null;

            if(this.configSource.containsProperty(ConfigSourceAdapter.ALL_LOG_LEVEL_KEY)) {
                String logLevel = this.configSource.getProperty(ConfigSourceAdapter.ALL_LOG_LEVEL_KEY).toString().trim();
                this.allLogLevel = StringUtils.hasText(logLevel) ? Level.toLevel(logLevel) : null;
            } else
                this.allLogLevel = null;
        }

        @Override
        public void initialize(ClassLoader currentClassLoader) {
            long startedAt = System.nanoTime();

            StatusConsoleListener fallbackListener = LOGGER.getFallbackListener();
            try {
                // 1.set status log level
                if(diagnosticLevel.isSimpleEnabled()) {
                    fallbackListener.setLevel(Level.INFO);

                    Collection<String> loggerPropertyNames = configSource.getLoggerPropertyNames();
                    LOGGER.info("^Initializing LoggingSystem with settings, \n"
                            + "  defaultStatusLevel: {} \n  statusLevel: {} \n"
                            + "  allLogLevel: {} \n  configLocation: {} "
                            + "{} ",
                            defaultStatusLevel, statusLevel,
                            allLogLevel, configLocation,
                            loggerPropertyNames.size() == 0 ? "" : loggerPropertyNames.stream().map( name -> name + ": " + configSource.getProperty(name) ).collect( Collectors.joining("\n  ", "\n  ", "\n") ) );
                }

                if(statusLevel != null && defaultStatusLevel.compareTo(statusLevel) < 0) {
                    fallbackListener.setLevel(statusLevel);
                }

                // 2.load log4j2 settings from configSource
                PropertiesUtil.getProperties().addPropertySource(configSource);

                // 3.initialize LoggerContext with given configuration file
                Configuration configuration = this.simpleInitialization 
                        ? doInitialize(currentClassLoader) : doInitialize2(currentClassLoader);

                this.customizeConfiguration(configuration);

            } catch(Throwable t) {
                LOGGER.warn("$Failed to initialize LoggingSystem.", t);
            } finally {
                fallbackListener.setLevel(defaultStatusLevel);

                Logger logger = LoggerFactory.getLogger(LoggingSystem.class);
                if(diagnosticLevel.isSimpleEnabled()) {
                    logger.info("$Took '{}' seconds to initialize LoggingSystem.", (System.nanoTime() - startedAt) / 1e9);
                }
            }
        }

        protected Configuration doInitialize(ClassLoader currentClassLoader) throws Exception {
            long startedAt = System.nanoTime();

            URL configFile = loadConfigurationFile(currentClassLoader);

            LoggerContext loggerContext = Configurator.initialize(CONTEXT_NAME, currentClassLoader, 
                    configFile == null ? null : configFile.toURI(), 
                    new SimpleEntry<>(ConfigSourceLookup.CONFIG_SOURCE_KEY, configSource));
            loggerContext.start();
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug("Took '{}' seconds to start loggerContext '{}'.", 
                        (System.nanoTime() - startedAt) / 1e9, CONTEXT_NAME);
            }

            return loggerContext.getConfiguration();
        }

        protected Configuration doInitialize2(ClassLoader currentClassLoader) throws Exception {
            long startedAt = System.nanoTime();

            // 1.create LoggerContext
            Log4jContextFactory log4jContextFactory = (Log4jContextFactory) LogManager.getFactory();
            LoggerContext loggerContext = log4jContextFactory.getSelector().getContext(
                    null, currentClassLoader, false);
            loggerContext.setName(CONTEXT_NAME);
            loggerContext.putObject(ConfigSourceLookup.CONFIG_SOURCE_KEY, configSource);

            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug("Took '{}' seconds to create LoggerContext '{}'.", 
                        (System.nanoTime() - startedAt) / 1e9, CONTEXT_NAME);
            }


            // 2.load configuration, and customize with specific LogLevel
            startedAt = System.nanoTime();

            URL configFile = loadConfigurationFile(currentClassLoader);

            Configuration configuration = null;
            if(configFile != null) {
                ConfigurationSource configurationSource = new ConfigurationSource(configFile.openStream());
                configuration = new XmlConfiguration(loggerContext, configurationSource);
            }

            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug("Took '{}' seconds to create Configuration '{}'.", 
                        (System.nanoTime() - startedAt) / 1e9, configFile);
            }


            // 3.start LoggerContext
            startedAt = System.nanoTime();
            if(configuration != null)
                loggerContext.start(configuration);
            else
                loggerContext.start();
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug("Took '{}' seconds to start loggerContext '{}'.", 
                        (System.nanoTime() - startedAt) / 1e9, CONTEXT_NAME);
            }

            return configuration;
        }

        private URL loadConfigurationFile(ClassLoader currentClassLoader) {
            // load user-defined configuration file, or built-in configuration file
            URL configFile = currentClassLoader.getResource(configLocation);
            if(configFile != null) {
                if(LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loaded config file '{}'.", configLocation);
                }

                return configFile;
            } else {
                LOGGER.warn("Did not find config file '{}'.\n", configLocation);

                return null;
            }
        }

        private void customizeConfiguration(Configuration configuration) {
            if(allLogLevel == null)
                return;

            for(LoggerConfig loggerConfig : configuration.getLoggers().values()) {
                Level currentLevel = loggerConfig.getLevel();
                if(currentLevel.compareTo(allLogLevel) >= 0)
                    continue;

                loggerConfig.setLevel(allLogLevel);
            }
        }
    }


    class Builder {

        private String configLocation;
        private ConfigSource configSource;
        private DiagnosticLevel diagnosticLevel;


        public Builder configLocation(String configLocation) {
            Assert.hasText(configLocation, "'configLocation' must not be empty.");
            this.configLocation = configLocation;

            return this;
        }

        public Builder configSource(ConfigSource configSource) {
            Assert.notNull(configSource, "'configSource' must not be null.");
            this.configSource = configSource;

            return this;
        }

        public Builder diagnosticLevel(DiagnosticLevel diagnosticLevel) {
            this.diagnosticLevel = diagnosticLevel;

            return this;
        }

        public LoggingSystem build() {
            return new Log4j2(configLocation, configSource, diagnosticLevel);
        }
    }
}
