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
package io.gemini.core.logging.logback;

import ch.qos.logback.classic.pattern.Abbreviator;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import io.gemini.core.logging.LoggingSystem;

/**
 * Logback {@link ClassicConverter} that renders the caller source location
 * (file name and line number) or an abbreviated logger name, depending on the
 * {@code aop.logger.includeLocation} configuration property.
 *
 * @author   martin.liu
 */
public class CallerSoucreConverter extends ClassicConverter {

    private boolean includeLocation = true;
    private final Abbreviator nameAbbreviator = new TargetLengthBasedClassNameAbbreviator(10);


    /**
     * Reads the {@code aop.logger.includeLocation} property from the Logback context
     * and sets the {@code includeLocation} flag accordingly.
     */
    @Override
    public void start() {
        Context context = getContext();
        if (context != null) {
            String includeLocationStr = context.getProperty(LoggingSystem.LOGGER_INCLUDE_LOCATION_KEY);
            includeLocation = Boolean.parseBoolean(includeLocationStr);
        }

        super.start();
    }

    /**
     * Returns the caller source string for the given logging event.
     * If {@code includeLocation} is {@code true}, returns {@code "FileName:lineNumber"};
     * otherwise returns an abbreviated logger name.
     *
     * @param event the logging event
     * @return the formatted caller source string
     */
    @Override
    public String convert(ILoggingEvent event) {
        if (this.includeLocation) {
            StackTraceElement[] cda = event.getCallerData();
            if (cda != null && cda.length > 0) {
                return cda[0].getFileName() + ":" + Integer.toString(cda[0].getLineNumber());
            } else {
                return nameAbbreviator.abbreviate(event.getLoggerName());
            }
        } else
            return nameAbbreviator.abbreviate(event.getLoggerName());
    }
}