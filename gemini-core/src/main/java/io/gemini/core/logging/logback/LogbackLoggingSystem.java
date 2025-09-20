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
package io.gemini.core.logging.logback;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.SubstituteLoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusUtil;
import ch.qos.logback.core.util.StatusListenerConfigHelper;
import ch.qos.logback.core.util.StatusPrinter;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.config.ConfigSource;
import io.gemini.core.logging.LoggingSystem;
import io.gemini.core.util.StringUtils;


public class LogbackLoggingSystem implements LoggingSystem {

    private static final String INTERNAL_CONFIGURATION_FILE = "META-INF/aop-logback.xml";

    private final String configLocation;
    private final Map<String, String> loggerSettings;
    private final DiagnosticLevel diagnosticLevel;

    private final Level allLogLevel;
    private final boolean debugLogback;


    public LogbackLoggingSystem(String configLocation, ConfigSource configSource,
            DiagnosticLevel diagnosticLevel) {
        this.diagnosticLevel = diagnosticLevel;

        this.loggerSettings= fetchLoggerSettings(diagnosticLevel, configSource);

        if(StringUtils.hasText(configLocation))
            this.configLocation = configLocation;
        else if(this.loggerSettings.containsKey(LOGGER_CONFIG_LOCATION_KEY))
            this.configLocation = this.loggerSettings.get(LOGGER_CONFIG_LOCATION_KEY);
        else
            this.configLocation = INTERNAL_CONFIGURATION_FILE;

        if(this.loggerSettings.containsKey(LOGGER_ALL_LOG_LEVEL_KEY)) {
            String logLevel = this.loggerSettings.get(LOGGER_ALL_LOG_LEVEL_KEY).toUpperCase();
            this.allLogLevel = StringUtils.hasText(logLevel) ? Level.toLevel(logLevel) : null;
        } else
        this.allLogLevel = null;

        // set aop.logger.debugLogback flag
        debugLogback = this.diagnosticLevel.isDebugEnabled() || allLogLevel == Level.DEBUG;
        this.loggerSettings.put("aop.logger.debugLogback", debugLogback ? "true" : "false");
    }

    private Map<String, String> fetchLoggerSettings(DiagnosticLevel diagnosticLevel, ConfigSource configSource) {
        Map<String, String> loggerSettings = new LinkedHashMap<>();

        // fetch logger settings
        for(String key : configSource.keys()) {
            if(key.startsWith("aop.logger.") == false) continue;

            Object value = configSource.getValue(key);
            if(value == null) continue;

            loggerSettings.put(key, String.valueOf(value).trim());
        }

        // reset debug setting
        if(diagnosticLevel.isDebugEnabled()) {
            loggerSettings.put(LOGGER_ALL_LOG_LEVEL_KEY, Level.DEBUG.toString());
            loggerSettings.put(LOGGER_INCLUDE_LOCATION_KEY, "true");
        }

        return loggerSettings;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(ClassLoader currentClassLoader) {
        long startedAt = System.nanoTime();

        Logger logger = null;
        try {
            // 1.get LoggerContext
            LoggerContext loggerContext = getLoggerContext();
            logger = loggerContext.getLogger(getClass());

            if(diagnosticLevel.isSimpleEnabled()) {
                logger.info("^Initializing LoggingSystem with settings, {} ",
                        StringUtils.join(loggerSettings.keySet(), key -> key + ": " + loggerSettings.get(key), "\n  ", "\n  ", "\n")
                );
            }

            // 2.configure LoggerContext
            configureLoggerContext(currentClassLoader, loggerContext, logger);

            // 3.customize LoggerContext
            customizeLoggerContext(loggerContext);

            // 4.log initialization
            reportConfigurationErrorsIfNecessary(loggerContext, logger);
        } catch(Throwable t) {
            if(logger != null)
                logger.warn("$Failed to initialize LoggingSystem.", t);
        } finally {
            if(diagnosticLevel.isSimpleEnabled()) {
                LoggerFactory.getLogger(LoggingSystem.class).info("$Took '{}' seconds to initialize LoggingSystem.", (System.nanoTime() - startedAt) / 1e9);
            }
        }
    }

    private LoggerContext getLoggerContext() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        while (factory instanceof SubstituteLoggerFactory) {
            try {
                Thread.sleep(50);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for non-substitute logger factory", ex);
            }
            factory = LoggerFactory.getILoggerFactory();
        }

        if(factory instanceof LoggerContext == false) {
            String codeLocation = "Unknown location";
            ProtectionDomain protectionDomain = factory.getClass().getProtectionDomain();
            CodeSource codeSource = protectionDomain.getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                codeLocation = codeSource.getLocation().getFile();
            }

            throw new IllegalStateException( 
                    String.format(
                        "LoggerFactory is not a Logback LoggerContext(%s loaded from %s).",
                        factory.getClass(), codeLocation) );
        }

        return (LoggerContext) factory;
    }

    /**
     * @param loggerContext
     * @param currentClassLoader 
     * @throws JoranException 
     */
    private void configureLoggerContext(ClassLoader currentClassLoader, LoggerContext loggerContext, Logger logger) throws JoranException {
        // reset LoggerContext
        if(debugLogback)
            StatusListenerConfigHelper.addOnConsoleListenerInstance(loggerContext, new OnConsoleStatusListener());

        loggerContext.reset();
        loggerContext.getStatusManager().clear();
        loggerContext.setName(CONTEXT_NAME);


        // configure logger settings
        for(Entry<String, String> entry : loggerSettings.entrySet())
            loggerContext.putProperty(entry.getKey(), entry.getValue());


        // configure LoggerContext
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);
        configurator.doConfigure( loadConfigurationFile(currentClassLoader, logger) );
    }

    private URL loadConfigurationFile(ClassLoader currentClassLoader, Logger logger) {
        // load user-defined configuration file, or built-in configuration file
        URL configFile = currentClassLoader.getResource(configLocation);
        if(configFile != null) {
            if(logger.isDebugEnabled()) {
                logger.debug("Loaded config file '{}'.", configLocation);
            }

            return configFile;
        } else {
            logger.warn("Did not find config file '{}'.\n", configLocation);

            return null;
        }
    }

    private void customizeLoggerContext(LoggerContext loggerContext) {
        for(Logger logger : loggerContext.getLoggerList()) {
            Level currentLevel = logger.getEffectiveLevel();
            if(currentLevel.isGreaterOrEqual(allLogLevel))
                continue;

            logger.setLevel(allLogLevel);
        }
    }

    private void reportConfigurationErrorsIfNecessary(LoggerContext loggerContext, Logger logger) {
        List<Status> statuses = loggerContext.getStatusManager().getCopyOfStatusList();
        StringBuilder errors = new StringBuilder();
        for (Status status : statuses) {
            if (status.getLevel() == Status.ERROR) {
                errors.append((errors.length() > 0) ? String.format("%n") : "");
                errors.append(status.toString());
            }
        }
        if (errors.length() > 0) {
            throw new IllegalStateException(String.format("Logback configuration error detected: %n%s", errors));
        }

        if(debugLogback == false) {
            if (!StatusUtil.contextHasStatusListener(loggerContext)) {
                StatusPrinter.printIfErrorsOccured(loggerContext);
            }
        }
    }
}
