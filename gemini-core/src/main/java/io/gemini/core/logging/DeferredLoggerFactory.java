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
package io.gemini.core.logging;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
 * A logger factory that defers log messages during the Gemini AOP framework initialization phase
 * and replays them once the logging system is fully configured.
 * <p>
 * During the deferred phase, all log messages are queued in memory. After
 * {@link #replayDeferredMessages(Level)} is called, the queue is drained and each
 * message is forwarded to the real SLF4J logger at the appropriate level.
 * After replay, all loggers switch to real-time logging.
 * </p>
 *
 * @author   martin.liu
 */
public class DeferredLoggerFactory {

    private static final Class<?> LOGGER_CONTEXT_CLASS;
    private static final Method GET_FRAMEWORK_PACKAGES_METHOD;

    private static final String SPACE = " ";
    private static final Map<Integer, String> LEVEL_MAP;

    private static final DeferredLoggerFactory INSTANCE = new DeferredLoggerFactory();


    private final Map<String, WeakReference<DeferredLogger>> loggers = new HashMap<String, WeakReference<DeferredLogger>>();
    private final LinkedBlockingQueue<DeferredMessage> eventQueue = new LinkedBlockingQueue<DeferredMessage>();

    private volatile boolean enableDeferMode = false;


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


        // adjust Logback LoggerContext;
        ILoggerFactory iLoggerFactory = LoggerFactory.getILoggerFactory();
        if (LOGGER_CONTEXT_CLASS.isAssignableFrom(iLoggerFactory.getClass())) {
            try {
                @SuppressWarnings({ "unchecked", "unused" })
                List<String> frameworkPackages = (List<String>) GET_FRAMEWORK_PACKAGES_METHOD.invoke(iLoggerFactory);
            } catch (Exception ignored) {}
        }
    }


    /**
     * Returns a {@link DeferredLogger} for the given name, creating one if it doesn't exist.
     *
     * @param name the logger name
     * @return the deferred logger instance
     */
    public static DeferredLogger getLogger(String name) {
        return INSTANCE.getOrCreateLogger(name);
    }

    /**
     * Returns a {@link DeferredLogger} for the given class.
     *
     * @param clazz the class whose name is used as the logger name
     * @return the deferred logger instance
     */
    public static DeferredLogger getLogger(Class<?> clazz) {
        return INSTANCE.getOrCreateLogger(clazz.getName());
    }

    /**
     * Enables defer mode, and caches log messages
     * 
     */
    public static void enableDeferMode() {
        INSTANCE.enableDeferModeInternal();
    }

    /**
     * Exits defer mode, and replays cache log messages with given log level.
     * 
     * @param loggingLevel
     */
    public static void replayDeferredMessages(Level loggingLevel) {
        INSTANCE.replayDeferredMessagesInternal(loggingLevel);
    }


    /**
     * Returns or creates a {@link DeferredLogger} for the given name, using a weak reference cache.
     *
     * @param name the logger name
     * @return the deferred logger
     */
    private synchronized DeferredLogger getOrCreateLogger(String name) {
        WeakReference<DeferredLogger> loggerRef = loggers.get(name);
        if (loggerRef == null || loggerRef.get() == null) {
            loggerRef = new WeakReference<DeferredLogger>(
                    new DeferredLogger(name, eventQueue, enableDeferMode) );
            loggers.put(name, loggerRef);
        }

        return loggerRef.get();
    }

    /**
     * Enables deferred mode on all existing loggers and sets the factory flag.
     */
    private void enableDeferModeInternal() {
        if (enableDeferMode == true)
            return;

        // enable defer mode, and cache log messages
        enableDeferMode = true;

        adjustDelayLoggers(enableDeferMode);
    }

    /**
     * Disables deferred mode, replays all queued messages at the given level, then clears state.
     *
     * @param loggingLevel the minimum level at which queued messages are replayed
     */
    private void replayDeferredMessagesInternal(Level loggingLevel) {
        if (enableDeferMode == false)
            return;

        // disable defer mode, and log message in-time
        enableDeferMode = false;

        adjustDelayLoggers(enableDeferMode);

        // replay log messages
        replayMessages(loggingLevel);

        // clear cached messages
        clear();
    }

    /**
     * Switches all cached loggers between deferred and real-time mode.
     *
     * @param deferMode {@code true} to enable deferred mode, {@code false} to disable
     */
    private void adjustDelayLoggers(boolean deferMode) {
        synchronized (this) {
            try {
                for (Iterator<WeakReference<DeferredLogger>> it = loggers.values().iterator(); it.hasNext(); ) {
                    WeakReference<DeferredLogger> loggerRef = it.next();

                    DeferredLogger DeferredLogger = loggerRef.get();
                    if (DeferredLogger == null) {
                        it.remove();
                        continue;
                    }

                    if (deferMode == true)
                        DeferredLogger.enableDeferMode();
                    else
                        DeferredLogger.disableDeferMode();
                }
            } catch (Exception e) {}
        }
    }

    /**
     * Drains the event queue and forwards each message to the real SLF4J logger.
     *
     * @param loggingLevel the minimum level to replay
     */
    private void replayMessages(Level loggingLevel) {
        final LinkedBlockingQueue<DeferredMessage> queue = eventQueue;
        int queueSize = queue.size();
        if (queueSize == 0) return;

        StringBuilder sBuilder = new StringBuilder()
                .append(") of logging calls during the initialization phase have been intercepted and are now being replayed. "
                        + "These are subject to the filtering rules of the underlying logging system.\n\n");
        loggingLevel = loggingLevel == null ? Level.INFO : loggingLevel;

        final int maxDrain = 128;
        List<DeferredMessage> messages = new ArrayList<DeferredMessage>(maxDrain);
        int msgCount = 0;
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");

        while (true) {
            int numDrained = queue.drainTo(messages, maxDrain);
            if (numDrained == 0)
                break;

            for (DeferredMessage message : messages) {
                if (message.getLevel() < loggingLevel.toInt()) continue;

                msgCount++;
                formatMessage(sBuilder, dateFormatter, message);
            }

            messages.clear();
        }

        sBuilder.append("\nReplayed deferred messages. \n");

        if (msgCount == 0) return;

        sBuilder.insert(0, "A number (" + msgCount);
        LoggerFactory.getLogger(DeferredLoggerFactory.class).info(sBuilder.toString());
    }

    /**
     * Formats a single {@link DeferredMessage} into the replay string builder.
     *
     * @param sBuilder      the string builder to append to
     * @param dateFormatter the date formatter for timestamps
     * @param message       the deferred message to format
     */
    private void formatMessage(StringBuilder sBuilder, DateFormat dateFormatter, DeferredMessage message) {
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
//        eventQueue.clear();
    }


    /**
     * SLF4J {@link Logger} implementation that queues log messages during the deferred phase
     * and forwards them to the real logger once deferred mode is disabled.
     */
    static class DeferredLogger implements Logger {

        // adjust logging location
        public static final String FQCN = DeferredLogger.class.getName();

        private final static boolean RECORD_ALL_EVENTS = true;

        private final String name;
        private volatile Logger delegate;

        private final Queue<DeferredMessage> eventQueue;


        public DeferredLogger(String name, Queue<DeferredMessage> eventQueue, boolean enableDeferMode) {
            this.name = name;
            this.eventQueue = eventQueue;

            if (enableDeferMode == true)
                this.enableDeferMode();
            else
                this.disableDeferMode();
        }

        public String getName() {
            return name;
        }

        /**
         * Switches this logger to deferred mode — log messages are queued instead of forwarded.
         */
        void enableDeferMode() {
            this.delegate = null;
        }

        /**
         * Switches this logger to real-time mode — log messages are forwarded to the real SLF4J logger.
         */
        void disableDeferMode() {
            Logger logger = LoggerFactory.getLogger(name);
            this.delegate = logger == null ? NOPLogger.NOP_LOGGER : logger;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isTraceEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isTraceEnabled();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isTraceEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isTraceEnabled(marker);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDebugEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isDebugEnabled();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDebugEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isDebugEnabled(marker);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isInfoEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isInfoEnabled();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isInfoEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isInfoEnabled(marker);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isWarnEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isWarnEnabled();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isWarnEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isWarnEnabled(marker);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isErrorEnabled() {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isErrorEnabled();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isErrorEnabled(Marker marker) {
            return getDelegate() == null ? RECORD_ALL_EVENTS : getDelegate().isErrorEnabled(marker);
        }


        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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

        /**
         * {@inheritDoc}
         */
        @Override
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
            DeferredMessage loggingEvent = new DeferredMessage();

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

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            DeferredLogger that = (DeferredLogger) o;

            if (!getName().equals(that.getName()))
                return false;

            return true;
        }
    }


    /**
     * Holds log message replayed in future.
     */
    static class DeferredMessage {

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