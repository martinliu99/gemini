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

import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.NOPLogger;
import org.slf4j.spi.LocationAwareLogger;

/**
 * Generally, this class is used in logging system startup phase to cache {@code DelayMessage}, 
 * and later replay messages after initialized logging system. 
 * 
 * After initialization, all logger instances, generated before and after initialization, 
 * works like normal logger and records message in real-time.
 *
 * with DelayLoggerFactory,
 * <li> in initializing phase, lose location information, e.g., class, method, file, line,
 * <li> might record disordered messages when working with spring boot {@code DeferredLog}.
 * 
 * This class is a enhanced {@code LocationAwareLogger} version of {@code org.slf4j.helpers.SubstituteLoggerFactory}.
 * 
 * @author martin.liu
 */
public class DelayLoggerFactory {

    private static final Class<?> LOGGER_CONTEXT_CLASS;
    private static final Method GET_FRAMEWORK_PACKAGES_METHOD;

    private static final String SPACE = " ";
    private static final Map<Integer, String> LEVEL_MAP;

    private static final DelayLoggerFactory INSTANCE = new DelayLoggerFactory();


    private final Map<String, DelayLogger> loggers = new HashMap<String, DelayLogger>();
    private final LinkedBlockingQueue<DelayMessage> eventQueue = new LinkedBlockingQueue<DelayMessage>();
    private volatile boolean postInitialization = false;


    static {
        Class<?> clazz = null;
        Method method = null;
        try {
            clazz = Class.forName("ch.qos.logback.classic.LoggerContext");
            method = clazz.getDeclaredMethod("getFrameworkPackages");
        } catch (Exception ignored) {}

        LOGGER_CONTEXT_CLASS = clazz;
        GET_FRAMEWORK_PACKAGES_METHOD = method;

        LEVEL_MAP = new HashMap<>(5);
        LEVEL_MAP.put(LocationAwareLogger.TRACE_INT, Level.TRACE.toString());
        LEVEL_MAP.put(LocationAwareLogger.DEBUG_INT, Level.DEBUG.toString());
        LEVEL_MAP.put(LocationAwareLogger.INFO_INT, Level.INFO.toString());
        LEVEL_MAP.put(LocationAwareLogger.WARN_INT, Level.WARN.toString());
        LEVEL_MAP.put(LocationAwareLogger.ERROR_INT, Level.ERROR.toString());
    }


    public static DelayLogger getLogger(String name) {
        return INSTANCE.getOrCreateLogger(name);
    }

    public static DelayLogger getLogger(Class<?> clazz) {
        return INSTANCE.getOrCreateLogger(clazz.getName());
    }

    public static void setLoggerInitialized(Level loggerLevel) {
        INSTANCE.setPostInitialization(loggerLevel);
    }


    synchronized protected DelayLogger getOrCreateLogger(String name) {
        DelayLogger logger = loggers.get(name);
        if (logger == null) {
            logger = new DelayLogger(name, eventQueue, postInitialization);
            loggers.put(name, logger);
        }
        return logger;
    }

    protected void setPostInitialization(Level loggerLevel) {
        if (postInitialization == true)
            return;

        // mark logging system initialized
        postInitialization = true;

        // lazily initialize delegate logger
        fixDelayLoggers();

        // replay log messages
        replayMessages(loggerLevel);

        // clear cached logger and messages
        clear();
    }

    private void fixDelayLoggers() {
        synchronized (this) {
            for (DelayLogger delayLogger : loggers.values()) {
                delayLogger.setPostInitialization();
            }

            // adjust Logback LoggerContext;
            ILoggerFactory iLoggerFactory = LoggerFactory.getILoggerFactory();
            if (LOGGER_CONTEXT_CLASS.isAssignableFrom(iLoggerFactory.getClass())) {
                try {
                    @SuppressWarnings({ "unchecked", "unused" })
                    List<String> frameworkPackages = (List<String>) GET_FRAMEWORK_PACKAGES_METHOD.invoke(iLoggerFactory);
                } catch (Exception ignored) {}
            }
        }
    }

    private void replayMessages(Level loggerLevel) {
        final LinkedBlockingQueue<DelayMessage> queue = eventQueue;
        int queueSize = queue.size();
        if (queueSize == 0) return;

        StringBuilder sBuilder = new StringBuilder()
                .append(") of logging calls during the initialization phase have been intercepted and are now being replayed. "
                        + "These are subject to the filtering rules of the underlying logging system.\n\n");
        loggerLevel = loggerLevel == null ? Level.INFO : loggerLevel;

        final int maxDrain = 128;
        List<DelayMessage> messages = new ArrayList<DelayMessage>(maxDrain);
        int msgCount = 0;
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");

        while (true) {
            int numDrained = queue.drainTo(messages, maxDrain);
            if (numDrained == 0)
                break;

            for (DelayMessage message : messages) {
                if (message.getLevel() < loggerLevel.toInt()) continue;

                msgCount++;
                formatMessage(sBuilder, dateFormatter, message);
            }

            messages.clear();
        }

        sBuilder.append("Replayed delay messages. \n");

        if (msgCount == 0) return;

        sBuilder.insert(0, "A number (" + msgCount);
        LoggerFactory.getLogger(DelayLoggerFactory.class).info(sBuilder.toString());
    }

    private void formatMessage(StringBuilder sBuilder, DateFormat dateFormatter, DelayMessage message) {
        sBuilder.append(dateFormatter.format(message.getTimeStamp())).append(SPACE)
        .append(
                LEVEL_MAP.containsKey(message.getLevel()) ? LEVEL_MAP.get(message.getLevel()) : "N/A" ).append(SPACE)
        .append("[").append(message.getThreadName()).append("]").append(SPACE)
        .append("(").append(message.getLoggerName()).append(")").append(SPACE)
        .append(" - ").append(
                MessageFormatter.arrayFormat(message.getMessage(), message.getArgumentArray(), message.getThrowable()).getMessage() ).append(SPACE)
        .append("\n")
        .toString();
    }

    private void clear() {
        loggers.clear();
        eventQueue.clear();
    }


    static class DelayLogger implements Logger {

        // adjust logging location
        public static final String FQCN = DelayLogger.class.getName();

        private final static boolean RECORD_ALL_EVENTS = true;

        private final String name;
        private volatile Logger delegate;

        private final Queue<DelayMessage> eventQueue;

        private volatile boolean postInitialization = false;


        public DelayLogger(String name, Queue<DelayMessage> eventQueue, boolean postInitialization) {
            this.name = name;
            this.eventQueue = eventQueue;

            if (postInitialization)
                this.setPostInitialization();
        }

        public String getName() {
            return name;
        }

        public void setPostInitialization() {
            if (postInitialization == true)
                return;

            postInitialization = true;

            Logger logger = LoggerFactory.getLogger(name);
            this.delegate = logger == null ? NOPLogger.NOP_LOGGER : logger;
        }


        public boolean isTraceEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isTraceEnabled();
        }

        public boolean isTraceEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isTraceEnabled(marker);
        }

        public boolean isDebugEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isDebugEnabled();
        }

        public boolean isDebugEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isDebugEnabled(marker);
        }

        public boolean isInfoEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isInfoEnabled();
        }

        public boolean isInfoEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isInfoEnabled(marker);
        }

        public boolean isWarnEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isWarnEnabled();
        }

        public boolean isWarnEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isWarnEnabled(marker);
        }

        public boolean isErrorEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isErrorEnabled();
        }

        public boolean isErrorEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isErrorEnabled(marker);
        }


        @Override
        public void trace(String msg) {
            if (isTraceEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.TRACE.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.TRACE.toInt(), msg, null, null);
                else
                    delegate.trace(msg);
            }
        }

        @Override
        public void trace(String format, Object arg) {
            if (isTraceEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.TRACE.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.TRACE.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.trace(format, arg);
            }
        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {
            if (isTraceEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.TRACE.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.TRACE.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.trace(format, arg1, arg2);
            }
        }

        @Override
        public void trace(String format, Object... arguments) {
            if (isTraceEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.TRACE.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.TRACE.toInt(), format, arguments, null);
                else
                    delegate.trace(format, arguments);
            }
        }

        @Override
        public void trace(String msg, Throwable t) {
            if (isTraceEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.TRACE.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.TRACE.toInt(), msg, null, t);
                else
                    delegate.trace(msg, t);
            }
        }

        @Override
        public void trace(Marker marker, String msg) {
            if (isTraceEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.TRACE.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.TRACE.toInt(), msg, null, null);
                else
                    delegate.trace(marker, msg);
            }
        }

        @Override
        public void trace(Marker marker, String format, Object arg) {
            if (isTraceEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.TRACE.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.TRACE.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.trace(marker, format, arg);
            }
        }

        @Override
        public void trace(Marker marker, String format, Object arg1, Object arg2) {
            if (isTraceEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.TRACE.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.TRACE.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.trace(marker, format, arg1, arg2);
            }
        }

        @Override
        public void trace(Marker marker, String format, Object... arguments) {
            if (isTraceEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.TRACE.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.TRACE.toInt(), format, arguments, null);
                else
                    delegate.trace(marker, format, arguments);
            }
        }

        public void trace(Marker marker, String msg, Throwable t) {
            if (isTraceEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.TRACE.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.TRACE.toInt(), msg, null, t);
                else
                    delegate.trace(marker, msg, t);
            }
        }

        public void debug(String msg) {
            if (isDebugEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.DEBUG.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.DEBUG.toInt(), msg, null, null);
                else
                    delegate.debug(msg);
            }
        }

        public void debug(String format, Object arg) {
            if (isDebugEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.DEBUG.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.DEBUG.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.debug(format, arg);
            }
        }

        public void debug(String format, Object arg1, Object arg2) {
            if (isDebugEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.DEBUG.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.DEBUG.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.debug(format, arg1, arg2);
            }
        }

        public void debug(String format, Object... arguments) {
            if (isDebugEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.DEBUG.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.DEBUG.toInt(), format, arguments, null);
                else
                    delegate.debug(format, arguments);
            }
        }

        public void debug(String msg, Throwable t) {
            if (isDebugEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.DEBUG.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.DEBUG.toInt(), msg, null, t);
                else
                    delegate.debug(msg, t);
            }
        }

        public void debug(Marker marker, String msg) {
            if (isDebugEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.DEBUG.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.DEBUG.toInt(), msg, null, null);
                else
                    delegate.debug(marker, msg);
            }
        }

        public void debug(Marker marker, String format, Object arg) {
            if (isDebugEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.DEBUG.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.DEBUG.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.debug(marker, format, arg);
            }
        }

        public void debug(Marker marker, String format, Object arg1, Object arg2) {
            if (isDebugEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.DEBUG.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.DEBUG.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.debug(marker, format, arg1, arg2);
            }
        }

        public void debug(Marker marker, String format, Object... arguments) {
            if (isDebugEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.DEBUG.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.DEBUG.toInt(), format, arguments, null);
                else
                    delegate.debug(marker, format, arguments);
            }
        }

        public void debug(Marker marker, String msg, Throwable t) {
            if (isDebugEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.DEBUG.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.DEBUG.toInt(), msg, null, t);
                else
                    delegate.debug(marker, msg, t);
            }
        }

        public void info(String msg) {
            if (isInfoEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.INFO.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.INFO.toInt(), msg, null, null);
                else
                    delegate.info(msg);
            }
        }

        public void info(String format, Object arg) {
            if (isInfoEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.INFO.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.INFO.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.info(format, arg);
            }
        }

        public void info(String format, Object arg1, Object arg2) {
            if (isInfoEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.INFO.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.INFO.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.info(format, arg1, arg2);
            }
        }

        public void info(String format, Object... arguments) {
            if (isInfoEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.INFO.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.INFO.toInt(), format, arguments, null);
                else
                    delegate.info(format, arguments);
            }
        }

        public void info(String msg, Throwable t) {
            if (isInfoEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.INFO.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.INFO.toInt(), msg, null, t);
                else
                    delegate.info(msg, t);
            }
        }

        public void info(Marker marker, String msg) {
            if (isInfoEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.INFO.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.INFO.toInt(), msg, null, null);
                else
                    delegate.info(marker, msg);
            }
        }

        public void info(Marker marker, String format, Object arg) {
            if (isInfoEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.INFO.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.INFO.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.info(marker, format, arg);
            }
        }

        public void info(Marker marker, String format, Object arg1, Object arg2) {
            if (isInfoEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.INFO.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.INFO.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.info(marker, format, arg1, arg2);
            }
        }

        public void info(Marker marker, String format, Object... arguments) {
            if (isInfoEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.INFO.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.INFO.toInt(), format, arguments, null);
                else
                    delegate.info(marker, format, arguments);
            }
        }

        public void info(Marker marker, String msg, Throwable t) {
            if (isInfoEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.INFO.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.INFO.toInt(), msg, null, t);
                else
                    delegate.info(marker, msg, t);
            }
        }

        public void warn(String msg) {
            if (isWarnEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.WARN.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.WARN.toInt(), msg, null, null);
                else
                    delegate.warn(msg);
            }
        }

        public void warn(String format, Object arg) {
            if (isWarnEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.WARN.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.WARN.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.warn(format, arg);
            }
        }

        public void warn(String format, Object arg1, Object arg2) {
            if (isWarnEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.WARN.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.WARN.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.warn(format, arg1, arg2);
            }
        }

        public void warn(String format, Object... arguments) {
            if (isWarnEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.WARN.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.WARN.toInt(), format, arguments, null);
                else
                    delegate.warn(format, arguments);
            }
        }

        public void warn(String msg, Throwable t) {
            if (isWarnEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.WARN.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.WARN.toInt(), msg, null, t);
                else
                    delegate.warn(msg, t);
            }
        }

        public void warn(Marker marker, String msg) {
            if (isWarnEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.WARN.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.WARN.toInt(), msg, null, null);
                else
                    delegate.warn(marker, msg);
            }
        }

        public void warn(Marker marker, String format, Object arg) {
            if (isWarnEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.WARN.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.WARN.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.warn(marker, format, arg);
            }
        }

        public void warn(Marker marker, String format, Object arg1, Object arg2) {
            if (isWarnEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.WARN.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.WARN.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.warn(marker, format, arg1, arg2);
            }
        }

        public void warn(Marker marker, String format, Object... arguments) {
            if (isWarnEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.WARN.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.WARN.toInt(), format, arguments, null);
                else
                    delegate.warn(marker, format, arguments);
            }
        }

        public void warn(Marker marker, String msg, Throwable t) {
            if (isWarnEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.WARN.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.WARN.toInt(), msg, null, t);
                else
                    delegate.warn(marker, msg, t);
            }
        }

        public void error(String msg) {
            if (isErrorEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.ERROR.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.ERROR.toInt(), msg, null, null);
                else
                    delegate.error(msg);
            }
        }

        public void error(String format, Object arg) {
            if (isErrorEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.ERROR.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.ERROR.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.error(format, arg);
            }
        }

        public void error(String format, Object arg1, Object arg2) {
            if (isErrorEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.ERROR.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.ERROR.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.error(format, arg1, arg2);
            }
        }

        public void error(String format, Object... arguments) {
            if (isErrorEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.ERROR.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.ERROR.toInt(), format, arguments, null);
                else
                    delegate.error(format, arguments);
            }
        }

        public void error(String msg, Throwable t) {
            if (isErrorEnabled()) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(null, Level.ERROR.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(null, FQCN, Level.ERROR.toInt(), msg, null, t);
                else
                    delegate.error(msg, t);
            }
        }

        public void error(Marker marker, String msg) {
            if (isErrorEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.ERROR.toInt(), msg, null, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.ERROR.toInt(), msg, null, null);
                else
                    delegate.error(marker, msg);
            }
        }

        public void error(Marker marker, String format, Object arg) {
            if (isErrorEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.ERROR.toInt(), format, new Object[] {arg}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.ERROR.toInt(), format, new Object[] {arg}, null);
                else
                    delegate.error(marker, format, arg);
            }
        }

        public void error(Marker marker, String format, Object arg1, Object arg2) {
            if (isErrorEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.ERROR.toInt(), format, new Object[] {arg1, arg2}, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.ERROR.toInt(), format, new Object[] {arg1, arg2}, null);
                else
                    delegate.error(marker, format, arg1, arg2);
            }
        }

        public void error(Marker marker, String format, Object... arguments) {
            if (isErrorEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.ERROR.toInt(), format, arguments, null);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.ERROR.toInt(), format, arguments, null);
                else
                    delegate.error(marker, format, arguments);
            }
        }

        public void error(Marker marker, String msg, Throwable t) {
            if (isErrorEnabled(marker)) {
                Logger delegate = getDelegate();

                if (delegate == null)
                    recordMessage(marker, Level.ERROR.toInt(), msg, null, t);
                else if (delegate instanceof LocationAwareLogger)
                    ((LocationAwareLogger) delegate).log(marker, FQCN, Level.ERROR.toInt(), msg, null, t);
                else
                    delegate.error(marker, msg, t);
            }
        }

        private void recordMessage(Marker marker, int level, String msg, Object[] args, Throwable throwable) {
            DelayMessage loggingEvent = new DelayMessage();

            loggingEvent.setLoggerName(name);

            loggingEvent.setThreadName(Thread.currentThread().getName());
            loggingEvent.setTimeStamp(System.currentTimeMillis());

            loggingEvent.setLevel(level);
            loggingEvent.setMarker(marker);
            loggingEvent.setMessage(msg);
            loggingEvent.setArgumentArray(args);
            loggingEvent.setThrowable(throwable);

            eventQueue.add(loggingEvent);
        }


        public Logger getDelegate() {
            return delegate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            DelayLogger that = (DelayLogger) o;

            if (!getName().equals(that.getName()))
                return false;

            return true;
        }
    }


    static class DelayMessage {

        String loggerName;
        long timeStamp;
        String threadName;

        int level;
        Marker marker;
        String message;
        Object[] argArray;
        Throwable throwable;


        public String getLoggerName() {
            return loggerName;
        }

        public void setLoggerName(String loggerName) {
            this.loggerName = loggerName;
        }


        public long getTimeStamp() {
            return timeStamp;
        }

        public void setTimeStamp(long timeStamp) {
            this.timeStamp = timeStamp;
        }

        public String getThreadName() {
            return threadName;
        }

        public void setThreadName(String threadName) {
            this.threadName = threadName;
        }


        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public Marker getMarker() {
            return marker;
        }

        public void setMarker(Marker marker) {
            this.marker = marker;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object[] getArgumentArray() {
            return argArray;
        }

        public void setArgumentArray(Object[] argArray) {
            this.argArray = argArray;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }
    }
}