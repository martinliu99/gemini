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
package io.gemini.aop;

import java.lang.ref.WeakReference;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.DiagnosticLevel;
import io.gemini.core.config.ConfigView;
import io.gemini.core.pool.TypeResolutionInspector.ResolutionLevel;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.PlaceholderHelper;
import io.gemini.core.util.Throwables;

/**
 * Collects and reports performance metrics for the Gemini AOP framework lifecycle.
 * <p>
 * Metrics are collected asynchronously via a background thread and a blocking queue.
 * Key inner classes:
 * <ul>
 *   <li>{@link LauncherMetrics} – startup phase timings (logging, context creation, weaver installation, etc.)</li>
 *   <li>{@link TypeMetrics} – per-type weaving timings (accepting, matching, transformation)</li>
 *   <li>{@link ClassLoaderMetrics} – aggregated metrics per class loader</li>
 *   <li>{@link WeaverMetrics} – snapshot of all class loader metrics at a point in time</li>
 *   <li>{@link WeaverMetricDumper} – renders metrics to human-readable log output using configurable templates</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public class AopMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(AopMetrics.class);

    private static final int POLLING_TIMEOUT = 100;
    private static final int ITEM_NAME_LENGTH = 40;

    public static final ClassLoader REJECTED_CLASS_LOADER = new RejectedClassLoader();
    public static final double NANO_TIME = 1e9;

    private static ThreadLocal<TypeMetrics> TYPE_METRICS_HOLDER = new ThreadLocal<>();


    private final ConfigView configView;
    private final DiagnosticLevel diagnosticLevel;


    private volatile AtomicBoolean running;
    private final BlockingQueue<TypeMetrics> queue;

    private final Thread workThread;
    private int batchSize;

    private final LauncherMetrics launcherMetrics;

    private volatile Map<ClassLoader, ClassLoaderMetrics> classLoaderMetricsMap;
    private WeaverMetrics bytebuddyWarmupMetrics;

    private final WeaverMetricDumper weaverMetricDumper;


    /**
     * Creates a new {@code AopMetrics} instance, initializing the launcher metrics,
     * background processing thread, and metric dumper.
     *
     * @param configView        the configuration view used to load template settings
     * @param diagnosticLevel   controls the verbosity of the metrics output
     */
    public AopMetrics(ConfigView configView, DiagnosticLevel diagnosticLevel) {
        // 1.check input argument
        Assert.notNull(configView, "'configView' must not be null.");
        this.configView = configView;

        Assert.notNull(diagnosticLevel, "'diagnosticLevel' must not be null.");
        this.diagnosticLevel = diagnosticLevel;


        // 2.initialize properties
        launcherMetrics = new LauncherMetrics();


        this.running = new AtomicBoolean(true);
        this.queue = new LinkedBlockingQueue<>();

        this.workThread = new Thread(this::processMetrics, "Gemini-" + this.getClass().getSimpleName());
        workThread.setDaemon(true);
        workThread.start();

        this.batchSize = 20;

        classLoaderMetricsMap = new LinkedHashMap<>();
        weaverMetricDumper = new WeaverMetricDumper();
    }


    /**
     * Returns the {@link LauncherMetrics} that tracks startup phase timings.
     *
     * @return the launcher metrics instance
     */
    public LauncherMetrics getLauncherMetrics() {
        return launcherMetrics;
    }


    /**
     * Creates a new {@link TypeMetrics} for the given type and stores it in the thread-local holder.
     *
     * @param targetClassLoader the class loader loading the type
     * @param targetTypeName    the fully-qualified type name
     * @param startedAt         the nanosecond timestamp when processing started
     * @return the newly created {@link TypeMetrics}
     */
    public TypeMetrics createTypeMetrics(ClassLoader targetClassLoader, String targetTypeName, long startedAt) {
        TYPE_METRICS_HOLDER.set(
                new TypeMetrics(targetClassLoader, targetTypeName, startedAt) );

        return TYPE_METRICS_HOLDER.get();
    }

    /**
     * Returns the {@link TypeMetrics} stored in the current thread's local holder.
     *
     * @return the current thread's {@link TypeMetrics}, or {@code null} if none
     */
    public static TypeMetrics currentTypeMetrics() {
        return TYPE_METRICS_HOLDER.get();
    }

    /**
     * Enqueues the given {@link TypeMetrics} for asynchronous processing and removes it
     * from the thread-local holder.
     *
     * @param typeMetrics the metrics to collect
     */
    public void collect(TypeMetrics typeMetrics) {
        this.queue.offer(typeMetrics);
        TYPE_METRICS_HOLDER.remove();
    }

    /**
     * Signals the background metrics processing thread to stop.
     */
    public void stop() {
        this.running.compareAndSet(true, false);

        this.workThread.interrupt();
    }

    /**
     * Background loop that drains the metrics queue in batches and delegates to
     * {@link #doProcessMetrics(List)}.
     */
    private void processMetrics() {
        List<TypeMetrics> typeMetricsList = new ArrayList<>(batchSize);

        while (running.get()) {
            try {
                TypeMetrics typeMetrics = this.queue.poll(POLLING_TIMEOUT, TimeUnit.MILLISECONDS);
                if (typeMetrics == null)
                    continue;

                typeMetricsList.clear();
                typeMetricsList.add(typeMetrics);

                queue.drainTo(typeMetricsList, batchSize - 1);

                this.doProcessMetrics(typeMetricsList);
            } catch (Throwable t) {
                Throwables.throwIfRequired(t);
            }
        }
    }

    /**
     * Processes a batch of {@link TypeMetrics} by aggregating them into the per-class-loader
     * {@link ClassLoaderMetrics} map.
     *
     * @param typeMetricsList the batch of type metrics to process
     */
    protected void doProcessMetrics(List<TypeMetrics> typeMetricsList) {
        for (TypeMetrics typeMetrics : typeMetricsList) {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(typeMetrics.getTargetClassLoader());
            ClassLoaderMetrics classLoaderMetrics = this.classLoaderMetricsMap.computeIfAbsent(
                    cacheKey, 
                    key -> new ClassLoaderMetrics(cacheKey)
            );

            // compute weaving metrics
            boolean isRejectedClassLoader = cacheKey == REJECTED_CLASS_LOADER;
            boolean isTransformingType = typeMetrics.getTypeTransformationTime() > 0;

            classLoaderMetrics.incrTypeWeavingCount( isTransformingType ? 0 : 1 );
            classLoaderMetrics.incrTypeWeavingTime( typeMetrics.getTypeWeavingTime() );

            classLoaderMetrics.incrTypeAcceptingCount( isTransformingType ? 0 : 1 );
            classLoaderMetrics.incrTypeAcceptingTime( 
                    isRejectedClassLoader ? typeMetrics.getTypeWeavingTime() : typeMetrics.getTypeAcceptingTime() );

            classLoaderMetrics.incrAdvisorCreationCount( typeMetrics.getAdvisorCreationCount() );
            classLoaderMetrics.incrAdvisorCreationTime( typeMetrics.getAdvisorCreationTime() );

            long typeFastMatchingTime = typeMetrics.getTypeFastMatchingTime();
            classLoaderMetrics.incrTypeFastMatchingCount( typeFastMatchingTime > 0 ? 1 : 0 );
            classLoaderMetrics.incrTypeFastMatchingTime( typeFastMatchingTime );

            long typeMatchingTime = typeMetrics.getTypeMatchingTime();
            classLoaderMetrics.incrTypeMatchingCount( typeMatchingTime > 0 ? 1 : 0 );
            classLoaderMetrics.incrTypeMatchingTime( typeMatchingTime );

            long typeTransformationTime = typeMetrics.getTypeTransformationTime();
            classLoaderMetrics.incrTypeTransformationCount( typeTransformationTime > 0 ? 1 : 0 );
            classLoaderMetrics.incrTypeTransformationTime( typeTransformationTime );


            // collect type resolution info
            for (Map<String, ResolutionLevel> advisorResolutuonLevelMap : typeMetrics.getAdvisorResolutuonLevelMaps()) {
                for (Entry<String, ResolutionLevel> entry : advisorResolutuonLevelMap.entrySet()) {
                    Map<String, Integer> advisorTypeCountMap = classLoaderMetrics.getTypeResolutuonLevelAdvisorMap()
                    .computeIfAbsent(
                            entry.getValue(), 
                            key -> new LinkedHashMap<>()
                    );

                    String advisorName = entry.getKey();
                    int count = advisorTypeCountMap.containsKey(advisorName)
                            ? advisorTypeCountMap.get(advisorName)
                            : 0;
                    advisorTypeCountMap.put(advisorName, ++count);
                }
            }
        }
    }


    /**
     * Records the ByteBuddy warmup metrics snapshot and stores it for later reporting.
     * Called internally by {@link LauncherMetrics#warmupByteBuddy(long)}.
     */
    protected void warmupByteBuddy() {
        this.bytebuddyWarmupMetrics = this.newWeaverMetricsSummary();
    }

    /**
     * Captures the launcher startup metrics snapshot and logs the startup summary.
     * Output verbosity is controlled by {@link DiagnosticLevel}.
     * Called internally by {@link LauncherMetrics#startupAopLauncher()}.
     */
    protected void startupAopLauncher() {
        WeaverMetrics launcherStartupMetrics = this.newWeaverMetricsSummary();

        if (LOGGER.isInfoEnabled()) {
            if (diagnosticLevel.isSimpleEnabled() == false) 
                LOGGER.info("$Took '{}' seconds to activate Gemini. \n"
                        + "{} \n"
                        + "{} \n", 
                        this.launcherMetrics.getLauncherStartupTime() / NANO_TIME,
                        weaverMetricDumper.getBannerTemplate(),
                        weaverMetricDumper.renderLauncherStartupSummaryTemplate(launcherMetrics, launcherStartupMetrics) );
            else 
                LOGGER.info("$Took '{}' seconds to activate Gemini. \n"
                        + "{} \n"
                        + "{} \n"
                        + "{}{}{} \n", 
                        this.launcherMetrics.getLauncherStartupTime() / NANO_TIME,
                        weaverMetricDumper.getBannerTemplate(),
                        weaverMetricDumper.renderLauncherStartupSummaryTemplate(launcherMetrics, launcherStartupMetrics),
                        bytebuddyWarmupMetrics != null ? weaverMetricDumper.renderWeaverMetricsTemplate("Warmup ByteBuddy", bytebuddyWarmupMetrics, true) : "",
                                launcherStartupMetrics != null ? weaverMetricDumper.renderWeaverMetricsTemplate("Redefined Loaded Types", launcherStartupMetrics, false) : "",
                        weaverMetricDumper.renderTypeResolutionTemplate(launcherStartupMetrics)
                );
        }
    }

    /**
     * Captures the application startup metrics snapshot and logs the application startup summary.
     * Output verbosity is controlled by {@link DiagnosticLevel}.
     */
    public void startupApplication() {
        WeaverMetrics appStartupMetrics = this.newWeaverMetricsSummary();

        if (LOGGER.isInfoEnabled()) {
            if (diagnosticLevel.isSimpleEnabled() == false) 
                LOGGER.info("$Took '{}' seconds to start application. \n"
                        + "{} \n",
                        (System.nanoTime() - launcherMetrics.getLauncherStartedAt()) / NANO_TIME,
                        weaverMetricDumper.renderAppStartupSummaryTemplate(launcherMetrics, appStartupMetrics) 
                );
            else 
                LOGGER.info("$Took '{}' seconds to start application. \n"
                        + "{} \n"
                        + "{}{} \n",
                        (System.nanoTime() - launcherMetrics.getLauncherStartedAt()) / NANO_TIME,
                        weaverMetricDumper.renderAppStartupSummaryTemplate(launcherMetrics, appStartupMetrics),
                        weaverMetricDumper.renderWeaverMetricsTemplate("Weaved New Types", appStartupMetrics, true),
                        weaverMetricDumper.renderTypeResolutionTemplate(appStartupMetrics)
                );
        }
    }


    /**
     * Takes a snapshot of the current class loader metrics map, replaces it with a fresh
     * empty map, and wraps the snapshot in a {@link WeaverMetrics} aggregate.
     *
     * @return a {@link WeaverMetrics} summarising all metrics collected since the last snapshot
     */
    private WeaverMetrics newWeaverMetricsSummary() {
        Map<ClassLoader, ClassLoaderMetrics> existingMetricsMap = this.classLoaderMetricsMap;
        this.classLoaderMetricsMap = new LinkedHashMap<>();

        return new WeaverMetrics(existingMetricsMap.values());
    }


    /**
     * Renders weaving metrics to human-readable log output using configurable
     * template strings loaded from the {@link io.gemini.core.config.ConfigView}.
     * Produces startup summary, per-phase weaver summary, and type resolution reports.
     */
    class WeaverMetricDumper {

        private String bannerTemplate;;

        private String launcherStartupSummrayTemplate;
        private String appStartupSummrayTemplate;

        private String weaverSummrayHeaderTemplate;
        private String weaverSummrayDetailTemplate;
        private String weaverSummrayPerCLTemplate;

        private String typeResolutionHeaderTemplate;
        private String typeResolutionCLTemplate;
        private String typeResolutionLevelTemplate;
        private String typeResolutionDetailTemplate;

        private String reportLineSeparatorTemplate;


        public WeaverMetricDumper() {
            loadSettings(configView);
        }

        /**
         * Loads all metric template strings from the given {@link ConfigView}.
         * Templates use placeholder syntax and are resolved at render time.
         *
         * @param configView the configuration view to read templates from
         */
        private void loadSettings(ConfigView configView) {
            this.bannerTemplate = configView.<String>getValue(
                    "aop.metrics.bannerTemplate", "", false, String.class);

            this.launcherStartupSummrayTemplate = configView.<String>getValue(
                    "aop.metrics.launcherStartupSummrayTemplate", "", false, String.class);
            this.appStartupSummrayTemplate =   configView.<String>getValue(
                    "aop.metrics.appStartupSummrayTemplate", "", false, String.class);

            this.weaverSummrayHeaderTemplate = configView.<String>getValue(
                    "aop.metrics.weaverSummrayHeaderTemplate", "", false, String.class);
            this.weaverSummrayDetailTemplate =  configView.<String>getValue(
                    "aop.metrics.weaverSummrayDetailTemplate", "", false, String.class);
            this.weaverSummrayPerCLTemplate  = configView.<String>getValue(
                    "aop.metrics.weaverSummrayPerCLTemplate", "", false, String.class);

            this.typeResolutionHeaderTemplate  = configView.<String>getValue(
                    "aop.metrics.typeResolutionHeaderTemplate", "", false, String.class);
            this.typeResolutionCLTemplate  = configView.<String>getValue(
                    "aop.metrics.typeResolutionCLTemplate", "", false, String.class);
            this.typeResolutionLevelTemplate  = configView.<String>getValue(
                    "aop.metrics.typeResolutionLevelTemplate", "", false, String.class);
            this.typeResolutionDetailTemplate  = configView.<String>getValue(
                    "aop.metrics.typeResolutionDetailTemplate", "", false, String.class);

            this.reportLineSeparatorTemplate  = configView.<String>getValue(
                    "aop.metrics.reportLineSeparatorTemplate", "", false, String.class);
        }


        /**
         * Renders the AOP launcher startup summary using timing data from {@link LauncherMetrics}
         * and the weaver metrics collected during the AOP framework launching phase.
         *
         * @param launcherMetrics        the launcher phase timing data
         * @param launcherStartupMetrics the weaver metrics snapshot for the launcher phase
         * @return the rendered summary string
         */
        public String renderLauncherStartupSummaryTemplate(LauncherMetrics launcherMetrics, WeaverMetrics launcherStartupMetrics) {
            Map<String, Object> valueMap = new HashMap<>();

            // collect metrics to be formated
            valueMap.put("launcherStartupTime", launcherMetrics.getLauncherStartupTime() / NANO_TIME);

            valueMap.put("launcherSetupTime", launcherMetrics.getLauncherSetupTime() / NANO_TIME);
            valueMap.put("loggerCreationTime", launcherMetrics.getLoggerCreationTime() / NANO_TIME);

            valueMap.put("aopContextCreationTime", launcherMetrics.getAopContextCreationTime() / NANO_TIME);
            valueMap.put("classScannerCreationTime", launcherMetrics.getClassScannerCreationTime() / NANO_TIME);

            valueMap.put("classLoaderConfigTime", (launcherMetrics.getBootstrapClassConfigTime() + launcherMetrics.getAopCLConfigTime()) / NANO_TIME);
            valueMap.put("bootstrapClass", launcherMetrics.getBootstrapClassConfigTime() / NANO_TIME);
            valueMap.put("aopClassLoader", launcherMetrics.getAopCLConfigTime() / NANO_TIME);

            valueMap.put("advisorFactoryCreationTime", launcherMetrics.getAdvisorFactoryCreationTime() / NANO_TIME);

            valueMap.put("aopWeaverCreationTime", launcherMetrics.getAopWeaverCreationTime() / NANO_TIME);

            valueMap.put("bytebuddyInstallationTime", launcherMetrics.getBytebuddyInstallationTime() / NANO_TIME);
            valueMap.put("bytebuddtWarnupTime", bytebuddyWarmupMetrics != null ? bytebuddyWarmupMetrics.getTypeWeavingTime() / NANO_TIME : 0);

            valueMap.put("typeRedefiningTime", launcherMetrics.getTypeRedefiningTime() / NANO_TIME);
            valueMap.put("typeWeavingTime", launcherStartupMetrics != null ? launcherStartupMetrics.getTypeWeavingTime() / NANO_TIME : 0);

            valueMap.put("uncategorizedTime", launcherMetrics.getUncategorizedTime() /NANO_TIME );

            valueMap = format(valueMap);


            // collect raw metrics
            StringBuilder advisorSepcs = new StringBuilder();
            if (CollectionUtils.isEmpty(launcherMetrics.getAdvisorSpecs()) == false) {
                for (Entry<String, Integer> entry : launcherMetrics.getAdvisorSpecs().entrySet()) {
                    advisorSepcs.append(entry.getKey()).append(": ").append(entry.getValue()).append(" specs, ");
                }
                advisorSepcs.delete(advisorSepcs.length()-2, advisorSepcs.length());
            } else
                advisorSepcs.append(0);
            valueMap.put("advisorSpecs", advisorSepcs.toString());

            valueMap.put("typeRedefiningCount", launcherMetrics.getTypeRedefiningCount());
          valueMap.put("typeWeavingCount", launcherStartupMetrics.getTypeWeavingCount());


            PlaceholderHelper placeholderHelper = PlaceholderHelper.create(valueMap);
            return placeholderHelper.replace(launcherStartupSummrayTemplate);
        }


        /**
         * Renders the application startup summary using timing data from {@link LauncherMetrics}
         * and the weaver metrics collected during the application launching phase.
         * 
         * @param   launcherMetrics     metrics of launcher
         * @param   appStartupMetrics   metrics of application startup
         * @return  rendered application startup summary
         */
        public String renderAppStartupSummaryTemplate(LauncherMetrics launcherMetrics, WeaverMetrics appStartupMetrics) {
            Map<String, Object> valueMap = new HashMap<>();

            // collect metrics to be formated
            double appStartupTime = (System.nanoTime() - launcherMetrics.getLauncherStartedAt()) / NANO_TIME;
            valueMap.put("appStartupTime", appStartupTime );

            valueMap.put("launcherStartupTime", launcherMetrics.getLauncherStartupTime() / NANO_TIME);
            valueMap.put("tyepWeavingTime", appStartupMetrics.getTypeWeavingTime() / NANO_TIME );

            valueMap.put("uncategorizedTime", appStartupTime - launcherMetrics.getLauncherStartupTime() / NANO_TIME - appStartupMetrics.getTypeWeavingTime() / NANO_TIME );

            valueMap = format(valueMap);


            // collect raw metrics
            valueMap.put("tyepTransformationCount", appStartupMetrics.getTypeTransformationCount() );
            valueMap.put("tyepWeavingCount", appStartupMetrics.getTypeWeavingCount() );


            PlaceholderHelper placeholderHelper = PlaceholderHelper.create(valueMap);
            return placeholderHelper.replace(appStartupSummrayTemplate);
        }


        /**
         * Renders a weaver metrics table for the given phase, including an optional header row
         * and one detail row per class loader.
         *
         * @param phaseName     the label for this metrics phase (e.g. "Warmup ByteBuddy")
         * @param weaverMetrics the metrics snapshot to render
         * @param withHead      whether to prepend the column header row
         * @return the rendered metrics table string
         */
        public String renderWeaverMetricsTemplate(String phaseName, WeaverMetrics weaverMetrics, boolean withHead) {
            StringBuilder renderResult = new StringBuilder();

            if (withHead)
                renderResult
                .append(reportLineSeparatorTemplate)
                .append(this.weaverSummrayHeaderTemplate)
                .append(reportLineSeparatorTemplate);

            // 1.render summary metrics
            {
                Map<String, Object> valueMap = new HashMap<>();

                valueMap.put("typeWeavingCount", weaverMetrics.getTypeWeavingCount());
                valueMap.put("typeWeavingTime", weaverMetrics.getTypeWeavingTime() / NANO_TIME);

                valueMap.put("typeAcceptingCount", weaverMetrics.getTypeAcceptingCount());
                valueMap.put("typeAcceptingTime", weaverMetrics.getTypeAcceptingTime() / NANO_TIME);

                valueMap.put("advisorCreationCount", weaverMetrics.getAdvisorCreationCount());
                valueMap.put("advisorCreationTime", weaverMetrics.getAdvisorCreationTime() / NANO_TIME);

                valueMap.put("typeFastMatchingCount", weaverMetrics.getTypeFastMatchingCount());
                valueMap.put("typeFastMatchingTime", weaverMetrics.getTypeFastMatchingTime() / NANO_TIME);

                valueMap.put("typeMatchingCount", weaverMetrics.getTypeMatchingCount());
                valueMap.put("typeMatchingTime", weaverMetrics.getTypeMatchingTime() / NANO_TIME);

                valueMap.put("typeTransformationCount", weaverMetrics.getTypeTransformationCount());
                valueMap.put("typeTransformationTime", weaverMetrics.getTypeTransformationTime() / NANO_TIME);

                valueMap.put("uncategorizedTime", weaverMetrics.getUncategorizedTime() / NANO_TIME);

                valueMap = format(valueMap);

                valueMap.put("itemName", formatStr(phaseName + " \u2935", ITEM_NAME_LENGTH, true) );

                renderResult.append( PlaceholderHelper.create(valueMap).replace(weaverSummrayDetailTemplate) )
                .append(reportLineSeparatorTemplate);
            }

            // 2.render detail metrics per ClassLoader and Advisor
            for (ClassLoaderMetrics classLoaderMetrics : weaverMetrics.getClassLoaderMetricsList()) {
                if (classLoaderMetrics.getTypeWeavingTime() == 0.0 && classLoaderMetrics.getAdvisorCreationTime() == 0.0)
                    continue;

                Map<String, Object> valueMap = new HashMap<>();

                ClassLoader classLoader = classLoaderMetrics.getTargetClassLoader();
                boolean isRejectedClassLoader = classLoader == REJECTED_CLASS_LOADER;
                String classLoaderId = isRejectedClassLoader 
                        ? "RejectedClassLoader" : ClassLoaderUtils.getClassLoaderId(classLoader);

                valueMap.put("itemName", format(
                        ClassUtils.abbreviate( classLoaderId, ITEM_NAME_LENGTH ) ) );

                valueMap.put("typeWeavingCount", classLoaderMetrics.getTypeWeavingCount());
                valueMap.put("typeWeavingTime", classLoaderMetrics.getTypeWeavingTime() / NANO_TIME);

                valueMap.put("typeAcceptingCount", classLoaderMetrics.getTypeAcceptingCount());
                valueMap.put("typeAcceptingTime", classLoaderMetrics.getTypeAcceptingTime() / NANO_TIME);

                valueMap.put("advisorCreationCount", classLoaderMetrics.getAdvisorCreationCount());
                valueMap.put("advisorCreationTime", classLoaderMetrics.getAdvisorCreationTime() / NANO_TIME);

                valueMap.put("typeFastMatchingCount", classLoaderMetrics.getTypeFastMatchingCount());
                valueMap.put("typeFastMatchingTime", classLoaderMetrics.getTypeFastMatchingTime() / NANO_TIME);

                valueMap.put("typeMatchingCount", classLoaderMetrics.getTypeMatchingCount());
                valueMap.put("typeMatchingTime", classLoaderMetrics.getTypeMatchingTime() / NANO_TIME);

                valueMap.put("typeTransformationCount", classLoaderMetrics.getTypeTransformationCount());
                valueMap.put("typeTransformationTime", classLoaderMetrics.getTypeTransformationTime() / NANO_TIME);

                valueMap.put("uncategorizedTime", classLoaderMetrics.getUncategorizedTime() / NANO_TIME );

                valueMap = format(valueMap);
                renderResult.append(
                        PlaceholderHelper.create( 
                                new ConfigView.Builder().parent(configView).configSource("valueMap", valueMap).build() )
                        .replace(weaverSummrayPerCLTemplate) )
                .append(reportLineSeparatorTemplate);
            }

            int length = renderResult.length();
            if (length > 0) {
                for (int i=0; i<2; i++) {
                    length = renderResult.length();
                    char lastChar = renderResult.charAt(length-1);

                    if ('\r' == lastChar || '\n' == lastChar)
                        renderResult.deleteCharAt(length-1);
                }
            }

            return renderResult.toString();
        }


        /**
         * Renders the type resolution report, showing per-class-loader advisor resolution level
         * statistics. Returns an empty string if no resolution data is available.
         *
         * @param weaverMetrics the metrics snapshot containing type resolution data
         * @return the rendered type resolution report string, or {@code ""} if none
         */
        public String renderTypeResolutionTemplate(WeaverMetrics weaverMetrics) {
            if (weaverMetrics.hasTypeResolution == false)
                return "";

            StringBuilder renderResult = new StringBuilder();

            // 1.render type resolution metrics
            renderResult.append("\n")
            .append(reportLineSeparatorTemplate)
            .append(this.typeResolutionHeaderTemplate)
            .append(reportLineSeparatorTemplate);

            for (ClassLoaderMetrics classLoaderMetrics : weaverMetrics.getClassLoaderMetricsList()) {
                if (classLoaderMetrics.getTypeWeavingCount() <= 1 && classLoaderMetrics.getAdvisorCreationCount() == 0)
                    continue;

                Map<ResolutionLevel, Map<String, Integer>> typeResolutuonLevelAdvisorMap = classLoaderMetrics.getTypeResolutuonLevelAdvisorMap();
                if (typeResolutuonLevelAdvisorMap.size() == 0)
                    continue;

                Map<String, Object> valueMap = new HashMap<>();
                valueMap.put("classLoader", formatStr(
                        ClassUtils.abbreviate( ClassLoaderUtils.getClassLoaderId(classLoaderMetrics.getTargetClassLoader()), ITEM_NAME_LENGTH), ITEM_NAME_LENGTH, true ) );

                renderResult.append(
                        PlaceholderHelper.create( 
                                new ConfigView.Builder().parent(configView).configSource("valueMap", valueMap).build() )
                        .replace(typeResolutionCLTemplate) );


                valueMap = new HashMap<>();
                for (Entry<ResolutionLevel, Map<String, Integer>> entry : typeResolutuonLevelAdvisorMap.entrySet()) {
                    valueMap = new HashMap<>();
                    valueMap.put("typeResolutionLevel", formatStr(
                            entry.getKey().toString() + " Advisor", 30, true ) );

                    renderResult.append(
                            PlaceholderHelper.create( 
                                    new ConfigView.Builder().parent(configView).configSource("valueMap", valueMap).build() )
                            .replace(typeResolutionLevelTemplate) );


                    for (Entry<String, Integer> advisorEntry : entry.getValue().entrySet()) {
                        String typeResolution = ClassUtils.abbreviate( advisorEntry.getKey(), 120 ) 
                                + ": " + advisorEntry.getValue() 
                                + "/" + classLoaderMetrics.getTypeFastMatchingCount() 
                                + " = " + format(advisorEntry.getValue() * 100.0 / classLoaderMetrics.getTypeFastMatchingCount())
                                + "%";
                        valueMap.put("typeResolutionDetails", formatStr(typeResolution, 152, true) );

                        renderResult.append(
                                PlaceholderHelper.create( 
                                        new ConfigView.Builder().parent(configView).configSource("valueMap", valueMap).build() )
                                .replace(typeResolutionDetailTemplate) );
                    }
                }

                renderResult.append(reportLineSeparatorTemplate);
            }

            int length = renderResult.length();
            if (length > 0) {
                for (int i=0; i<2; i++) {
                    length = renderResult.length();
                    char lastChar = renderResult.charAt(length-1);

                    if ('\r' == lastChar || '\n' == lastChar)
                        renderResult.deleteCharAt(length-1);
                }
            }

            return renderResult.toString();
        }

        /**
         * Formats a string to a fixed width, truncating or padding as needed.
         *
         * @param item       the string to format
         * @param itemLength the target column width; if 0, uses the string's own length
         * @param leftAlign  {@code true} for left-aligned, {@code false} for right-aligned
         * @return the formatted string
         */
        private String formatStr(String item, int itemLength, boolean leftAlign) {
            String str = (String) item;

            if (itemLength == 0)
                itemLength = str.length();

            str = str.length() <= itemLength ? str : str.substring(0, itemLength);
            return String.format("%" + (leftAlign ? "-" : "") + itemLength + "s", str);
        }

        /**
         * Formats a single metric value for display:
         * strings are padded to {@link AopMetrics#ITEM_NAME_LENGTH}, doubles to 9.6f,
         * and integers/longs to 6d.
         *
         * @param item the value to format
         * @return the formatted value
         */
        private Object format(Object item) {
            if (item instanceof String) {
                return formatStr( (String) item, ITEM_NAME_LENGTH, false);
            }

            if (item instanceof Float)
                return String.format("%9.6f", (Float) item);
            if (item instanceof Double)
                return String.format("%9.6f", (Double) item);

            if (item instanceof Integer) 
                return String.format("%6d", (Integer) item);
            if (item instanceof Long) 
                return String.format("%6d", (Long) item);

            return item;
        }

        /**
         * Applies {@link #format(Object)} to every value in the given map and returns
         * a new map with the formatted values.
         *
         * @param map the map of metric key-value pairs to format
         * @return a new map with all values formatted
         */
        private Map<String, Object> format(Map<String, Object> map) {
            return map.entrySet().stream()
                    .map( e -> 
                        new SimpleEntry<>(e.getKey(), format(e.getValue())) )
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        }


        /**
         * Returns the banner template string used as a header in startup log output.
         *
         * @return the banner template
         */
        public String getBannerTemplate() {
            return bannerTemplate;
        }
    }


    /**
     * Collects timing data for each phase of the AOP framework launcher startup sequence:
     * logging initialization, context creation, class loader configuration, advisor factory
     * creation, ByteBuddy installation, and type redefinition.
     */
    public class LauncherMetrics {

        private long launcherStartedAt;
        private long launcherStartupTime;

        private long launcherSetupTime;
        private long loggerCreationTime;

        private long aopContextCreationTime;
        private long classScannerCreationTime;

        private long bootstrapClassConfigTime;
        private long aopClassLoaderConfigTime;

        private long advisorFactoryCreationTime;
        private Map<String, Integer> advisorSpecs;
        private long aopWeaverCreationTime;

        private long bytebuddyInstallationTime;

        private long typeRedefiningTime;
        private int typeRedefiningCount;


        /**
         * Returns the nanosecond timestamp recorded when the launcher started.
         *
         * @return launcher start timestamp in nanoseconds
         */
        protected long getLauncherStartedAt() {
            return launcherStartedAt;
        }

        /**
         * Records the nanosecond timestamp when the launcher started.
         *
         * @param launcherStartedAt the start timestamp in nanoseconds
         */
        public void setLauncherStartedAt(long launcherStartedAt) {
            this.launcherStartedAt = launcherStartedAt;
        }

        /**
         * Returns the time spent on initial launcher setup in nanoseconds.
         *
         * @return launcher setup time in nanoseconds
         */
        protected long getLauncherSetupTime() {
            return launcherSetupTime;
        }

        /**
         * Records the time spent on initial launcher setup.
         *
         * @param launcherSetupTime setup time in nanoseconds
         */
        public void setLauncherSetupTime(long launcherSetupTime) {
            this.launcherSetupTime = launcherSetupTime;
        }

        /**
         * Returns the time spent creating the logging infrastructure in nanoseconds.
         *
         * @return logger creation time in nanoseconds
         */
        protected long getLoggerCreationTime() {
            return loggerCreationTime;
        }

        /**
         * Records the time spent creating the logging infrastructure.
         *
         * @param loggerCreationTime logger creation time in nanoseconds
         */
        public void setLoggerCreationTime(long loggerCreationTime) {
            this.loggerCreationTime = loggerCreationTime;
        }


        /**
         * Returns the time spent creating the {@link io.gemini.aop.AopContext} in nanoseconds.
         *
         * @return AOP context creation time in nanoseconds
         */
        protected long getAopContextCreationTime() {
            return aopContextCreationTime;
        }

        /**
         * Records the time spent creating the {@link io.gemini.aop.AopContext}.
         *
         * @param aopContextCreationTime AOP context creation time in nanoseconds
         */
        public void setAopContextCreationTime(long aopContextCreationTime) {
            this.aopContextCreationTime = aopContextCreationTime;
        }

        /**
         * Returns the time spent creating the class scanner in nanoseconds.
         *
         * @return class scanner creation time in nanoseconds
         */
        protected long getClassScannerCreationTime() {
            return classScannerCreationTime;
        }

        /**
         * Records the time spent creating the class scanner.
         *
         * @param classScannerCreationTime class scanner creation time in nanoseconds
         */
        public void setClassScannerCreationTime(long classScannerCreationTime) {
            this.classScannerCreationTime = classScannerCreationTime;
        }

        /**
         * Returns the time spent configuring bootstrap classes in nanoseconds.
         *
         * @return bootstrap class configuration time in nanoseconds
         */
        protected long getBootstrapClassConfigTime() {
            return bootstrapClassConfigTime;
        }

        /**
         * Records the time spent configuring bootstrap classes.
         *
         * @param bootstrapClassConfigTime bootstrap class configuration time in nanoseconds
         */
        public void setBootstrapClassConfigTime(long bootstrapClassConfigTime) {
            this.bootstrapClassConfigTime = bootstrapClassConfigTime;
        }

        /**
         * Returns the time spent configuring the AOP class loader in nanoseconds.
         *
         * @return AOP class loader configuration time in nanoseconds
         */
        protected long getAopCLConfigTime() {
            return aopClassLoaderConfigTime;
        }

        /**
         * Records the time spent configuring the AOP class loader.
         *
         * @param aopClassLoaderConfigTime AOP class loader configuration time in nanoseconds
         */
        public void setAopClassLoaderConfigTime(long aopClassLoaderConfigTime) {
            this.aopClassLoaderConfigTime = aopClassLoaderConfigTime;
        }

        /**
         * Returns the time spent creating the advisor factory in nanoseconds.
         *
         * @return advisor factory creation time in nanoseconds
         */
        protected long getAdvisorFactoryCreationTime() {
            return advisorFactoryCreationTime;
        }

        /**
         * Records the time spent creating the advisor factory.
         *
         * @param advisorFactoryCreationTime advisor factory creation time in nanoseconds
         */
        public void setAdvisorFactoryCreationTime(long advisorFactoryCreationTime) {
            this.advisorFactoryCreationTime = advisorFactoryCreationTime;
        }

        /**
         * Returns a map of advisor spec source names to their loaded spec counts.
         *
         * @return advisor spec counts keyed by source name
         */
        protected Map<String, Integer> getAdvisorSpecs() {
            return advisorSpecs;
        }

        /**
         * Records the advisor spec counts per source.
         *
         * @param advisorSpecs map of source name to spec count
         */
        public void setAdvisorSpecs(Map<String, Integer> advisorSpecs) {
            this.advisorSpecs = advisorSpecs;
        }

        /**
         * Returns the time spent creating the AOP weaver in nanoseconds.
         *
         * @return AOP weaver creation time in nanoseconds
         */
        protected long getAopWeaverCreationTime() {
            return aopWeaverCreationTime;
        }

        /**
         * Records the time spent creating the AOP weaver.
         *
         * @param aopWeaverCreationTime AOP weaver creation time in nanoseconds
         */
        public void setAopWeaverCreationTime(long aopWeaverCreationTime) {
            this.aopWeaverCreationTime = aopWeaverCreationTime;
        }

        /**
         * Returns the time spent installing ByteBuddy in nanoseconds.
         *
         * @return ByteBuddy installation time in nanoseconds
         */
        protected long getBytebuddyInstallationTime() {
            return bytebuddyInstallationTime;
        }

        /**
         * Records the ByteBuddy installation time and triggers the warmup metrics snapshot.
         *
         * @param bytebuddyInstallationTime ByteBuddy installation time in nanoseconds
         */
        public void warmupByteBuddy(long bytebuddyInstallationTime) {
            this.bytebuddyInstallationTime = bytebuddyInstallationTime;

            AopMetrics.this.warmupByteBuddy();
        }

        /**
         * Returns the time spent redefining already-loaded types in nanoseconds.
         *
         * @return type redefining time in nanoseconds
         */
        protected long getTypeRedefiningTime() {
            return typeRedefiningTime;
        }

        /**
         * Records the time spent redefining already-loaded types.
         *
         * @param typeRedefiningTime type redefining time in nanoseconds
         */
        public void setTypeRedefiningTime(long typeRedefiningTime) {
            this.typeRedefiningTime = typeRedefiningTime;
        }

        /**
         * Returns the number of types redefined during the launcher startup phase.
         *
         * @return type redefining count
         */
        protected int getTypeRedefiningCount() {
            return typeRedefiningCount;
        }

        /**
         * Increments the count of types redefined during the launcher startup phase.
         *
         * @param typeRedefiningCount the number of additional types redefined
         */
        public void incrTypeRedefiningCount(int typeRedefiningCount) {
            this.typeRedefiningCount += typeRedefiningCount;
        }

        /**
         * Returns the total launcher startup time in nanoseconds.
         *
         * @return launcher startup time in nanoseconds
         */
        protected long getLauncherStartupTime() {
            return this.launcherStartupTime;
        }

        /**
         * Computes and stores the total launcher startup time, then triggers the startup log output.
         * Should be called once the launcher has fully initialised.
         */
        public void startupAopLauncher() {
            this.launcherStartupTime = System.nanoTime() - launcherMetrics.getLauncherStartedAt();

            AopMetrics.this.startupAopLauncher();
        }

        /**
         * Returns the time not accounted for by any specific startup phase (i.e. overhead).
         * Computed as total startup time minus the sum of all tracked phase times.
         *
         * @return uncategorized time in nanoseconds
         */
        protected long getUncategorizedTime() {
            return launcherStartupTime 
                    - launcherSetupTime
                    - aopContextCreationTime
                    - bootstrapClassConfigTime - aopClassLoaderConfigTime
                    - advisorFactoryCreationTime - aopWeaverCreationTime 
                    - bytebuddyInstallationTime - typeRedefiningTime;
        }
    }


    /**
     * Base metrics holder for weaving timing and count data, keyed by a target class loader.
     */
    static class BaseMetrics {

        private final WeakReference<ClassLoader> targetClassLoaderRef;

        private long typeWeavingTime = 0;

        private long typeAcceptingTime = 0;

        private int advisorCreationCount = 0;
        private long advisorCreationTime = 0;

        private long typeFastMatchingTime = 0;

        private long typeMatchingTime = 0;

        private long typeTransformationTime = 0;


        /**
         * Creates a new {@code BaseMetrics} instance associated with the given class loader.
         * The class loader is held via a {@link WeakReference} to avoid preventing GC.
         *
         * @param targetClassLoader the class loader this metrics instance is associated with
         */
        public BaseMetrics(ClassLoader targetClassLoader) {
            this.targetClassLoaderRef = new WeakReference<>(targetClassLoader);
        }


        /**
         * Returns the target class loader, or {@code null} if it has been garbage collected.
         *
         * @return the target class loader
         */
        public ClassLoader getTargetClassLoader() {
            return targetClassLoaderRef.get();
        }

        /**
         * Returns the total time spent in the type weaving pipeline in nanoseconds.
         *
         * @return type weaving time in nanoseconds
         */
        public long getTypeWeavingTime() {
            return typeWeavingTime;
        }

        /**
         * Adds the given duration to the cumulative type weaving time.
         *
         * @param typeWeavingTime additional weaving time in nanoseconds
         */
        public void incrTypeWeavingTime(long typeWeavingTime) {
            this.typeWeavingTime += typeWeavingTime;
        }

        /**
         * Returns the total time spent in the type accepting phase in nanoseconds.
         *
         * @return type accepting time in nanoseconds
         */
        public long getTypeAcceptingTime() {
            return typeAcceptingTime;
        }

        /**
         * Adds the given duration to the cumulative type accepting time.
         *
         * @param typeAcceptingTime additional accepting time in nanoseconds
         */
        public void incrTypeAcceptingTime(long typeAcceptingTime) {
            this.typeAcceptingTime += typeAcceptingTime;
        }

        /**
         * Returns the number of advisors created during weaving.
         *
         * @return advisor creation count
         */
        public int getAdvisorCreationCount() {
            return advisorCreationCount;
        }

        /**
         * Sets the advisor creation count to the given value.
         *
         * @param advisorCreationCount the advisor creation count to set
         */
        public void setAdvisorCreationCount(int advisorCreationCount) {
            this.advisorCreationCount = advisorCreationCount;
        }

        /**
         * Sets the advisor creation count only if it has not been set yet (i.e. is still zero).
         *
         * @param advisorCreationCount the advisor creation count to apply
         */
        public void incrAdvisorCreationCount(int advisorCreationCount) {
            if (this.advisorCreationCount == 0)
                this.advisorCreationCount = advisorCreationCount;
        }

        /**
         * Returns the total time spent creating advisors in nanoseconds.
         *
         * @return advisor creation time in nanoseconds
         */
        public long getAdvisorCreationTime() {
            return advisorCreationTime;
        }

        /**
         * Adds the given duration to the cumulative advisor creation time.
         *
         * @param advisorCreationTime additional advisor creation time in nanoseconds
         */
        public void incrAdvisorCreationTime(long advisorCreationTime) {
            this.advisorCreationTime += advisorCreationTime;
        }

        /**
         * Returns the total time spent in the fast type matching phase in nanoseconds.
         *
         * @return type fast matching time in nanoseconds
         */
        public long getTypeFastMatchingTime() {
            return typeFastMatchingTime;
        }

        /**
         * Adds the given duration to the cumulative fast type matching time.
         *
         * @param typeFastMatchingTime additional fast matching time in nanoseconds
         */
        public void incrTypeFastMatchingTime(long typeFastMatchingTime) {
            this.typeFastMatchingTime += typeFastMatchingTime;
        }

        /**
         * Returns the total time spent in the full type matching phase in nanoseconds.
         *
         * @return type matching time in nanoseconds
         */
        public long getTypeMatchingTime() {
            return typeMatchingTime;
        }

        /**
         * Adds the given duration to the cumulative full type matching time.
         *
         * @param typeMatchingTime additional matching time in nanoseconds
         */
        public void incrTypeMatchingTime(long typeMatchingTime) {
            this.typeMatchingTime += typeMatchingTime;
        }

        /**
         * Returns the total time spent transforming (instrumenting) types in nanoseconds.
         *
         * @return type transformation time in nanoseconds
         */
        public long getTypeTransformationTime() {
            return typeTransformationTime;
        }

        /**
         * Adds the given duration to the cumulative type transformation time.
         *
         * @param typeTransformationTime additional transformation time in nanoseconds
         */
        public void incrTypeTransformationTime(long typeTransformationTime) {
            this.typeTransformationTime += typeTransformationTime;
        }


        /**
         * Returns the time not accounted for by any specific weaving sub-phase.
         * Computed as total weaving time minus the sum of all tracked sub-phase times.
         *
         * @return uncategorized weaving time in nanoseconds
         */
        public long getUncategorizedTime() {
            return getTypeWeavingTime() - getTypeAcceptingTime() - getAdvisorCreationTime()
                    - getTypeFastMatchingTime() - getTypeMatchingTime() - getTypeTransformationTime();
        }
    }


    /**
     * Per-type weaving metrics collected during a single class transformation.
     * Includes timing for accepting, advisor creation, fast-matching, method-matching,
     * and transformation phases, plus type resolution level data per advisor.
     */
    public static class TypeMetrics extends BaseMetrics {

        private long startedAt;
        private List<Map<String /* AdvisorName */, ResolutionLevel>> advisorResolutuonLevelMaps;


        /**
         * Creates a new {@code TypeMetrics} for the given class loader, type name, and start timestamp.
         *
         * @param targetClassLoader the class loader loading the type
         * @param targetTypeName    the fully-qualified name of the type being processed
         * @param startedAt         the nanosecond timestamp when processing started
         */
        public TypeMetrics(ClassLoader targetClassLoader, String targetTypeName, long startedAt) {
            super(targetClassLoader);

            this.startedAt = startedAt;
            this.advisorResolutuonLevelMaps = new ArrayList<>();
        }


        /**
         * Returns the nanosecond timestamp when processing of this type started.
         *
         * @return start timestamp in nanoseconds
         */
        public long getStartedAt() {
            return startedAt;
        }

        /**
         * Returns the list of advisor-to-resolution-level maps collected during type processing.
         * Each entry in the list corresponds to one advisor evaluation pass.
         *
         * @return list of advisor resolution level maps
         */
        public List<Map<String, ResolutionLevel>> getAdvisorResolutuonLevelMaps() {
            return advisorResolutuonLevelMaps;
        }

        /**
         * Appends an advisor resolution level map to the list. Null values are silently ignored.
         *
         * @param advisorResolutuonLevelMap the map of advisor name to resolution level to add
         */
        public void addAdvisorResolutuonLevelMap(Map<String, ResolutionLevel> advisorResolutuonLevelMap) {
            if (advisorResolutuonLevelMap != null)
                this.advisorResolutuonLevelMaps.add(advisorResolutuonLevelMap);
        }
    }


    /**
     * Aggregated metrics for all types processed under a single class loader.
     * Extends {@link BaseMetrics} with per-phase type counts and type resolution level tracking.
     */
    static class ClassLoaderMetrics extends BaseMetrics {

        private int typeWeavingCount = 0;

        private int typeAcceptingCount = 0;

        private int typeFastMatchingCount = 0;
        private int typeMatchingCount = 0;

        private int typeTransformationCount = 0;

        private final Map<ResolutionLevel, Map<String, Integer>> typeResolutuonLevelAdvisorMap = new LinkedHashMap<>();


        /**
         * Creates a new {@code ClassLoaderMetrics} associated with the given class loader.
         *
         * @param classLoader the class loader these metrics are associated with
         */
        public ClassLoaderMetrics(ClassLoader classLoader) {
            super(classLoader);
        }

        /**
         * Returns the number of types that passed through the weaving pipeline.
         *
         * @return type weaving count
         */
        public int getTypeWeavingCount() {
            return typeWeavingCount;
        }

        /**
         * Increments the type weaving count by the given amount.
         *
         * @param typeWeavingCount the number of additional types woven
         */
        public void incrTypeWeavingCount(int typeWeavingCount) {
            this.typeWeavingCount += typeWeavingCount;
        }

        /**
         * Returns the number of types that were accepted (not rejected) by the weaver.
         *
         * @return type accepting count
         */
        public int getTypeAcceptingCount() {
            return typeAcceptingCount;
        }

        /**
         * Increments the type accepting count by the given amount.
         *
         * @param typeAcceptingCount the number of additional types accepted
         */
        public void incrTypeAcceptingCount(int typeAcceptingCount) {
            this.typeAcceptingCount += typeAcceptingCount;
        }

        /**
         * Returns the number of types that were evaluated by the fast matcher.
         *
         * @return type fast matching count
         */
        public int getTypeFastMatchingCount() {
            return typeFastMatchingCount;
        }

        /**
         * Increments the fast matching count by the given amount.
         *
         * @param typeFastMatchingCount the number of additional types fast-matched
         */
        public void incrTypeFastMatchingCount(int typeFastMatchingCount) {
            this.typeFastMatchingCount += typeFastMatchingCount;
        }

        /**
         * Returns the number of types that were evaluated by the full matcher.
         *
         * @return type matching count
         */
        public int getTypeMatchingCount() {
            return typeMatchingCount;
        }

        /**
         * Increments the full matching count by the given amount.
         *
         * @param typeMatchingCount the number of additional types fully matched
         */
        public void incrTypeMatchingCount(int typeMatchingCount) {
            this.typeMatchingCount += typeMatchingCount;
        }

        /**
         * Returns the number of types that were actually transformed (instrumented).
         *
         * @return type transformation count
         */
        public int getTypeTransformationCount() {
            return typeTransformationCount;
        }

        /**
         * Increments the type transformation count by the given amount.
         *
         * @param typeTransformationCount the number of additional types transformed
         */
        public void incrTypeTransformationCount(int typeTransformationCount) {
            this.typeTransformationCount += typeTransformationCount;
        }

        /**
         * Returns the map of resolution level to advisor name/count pairs, used for
         * type resolution reporting.
         *
         * @return map of {@link ResolutionLevel} to advisor name-to-count map
         */
        public Map<ResolutionLevel, Map<String, Integer>> getTypeResolutuonLevelAdvisorMap() {
            return typeResolutuonLevelAdvisorMap;
        }
    }


    /**
     * Snapshot of aggregated weaving metrics across all class loaders at a point in time.
     * Created by {@link AopMetrics#newWeaverMetricsSummary()} to capture either the
     * launcher startup phase or the application startup phase metrics.
     */
    static class WeaverMetrics extends ClassLoaderMetrics {

        private final Collection<ClassLoaderMetrics> classLoaderMetricsList;

        private final boolean hasTypeResolution;


        /**
         * Creates a new {@code WeaverMetrics} by aggregating all metrics from the given
         * collection of {@link ClassLoaderMetrics}. All counters and timings are summed,
         * and the type resolution flag is set if any class loader has resolution data.
         *
         * @param classLoaderMetricsList the per-class-loader metrics to aggregate
         */
        public WeaverMetrics(Collection<ClassLoaderMetrics> classLoaderMetricsList) {
            super(null);

            this.classLoaderMetricsList = classLoaderMetricsList;

            for (ClassLoaderMetrics classLoaderMetrics : classLoaderMetricsList) {
                this.incrTypeWeavingCount( classLoaderMetrics.getTypeWeavingCount() );
                this.incrTypeWeavingTime( classLoaderMetrics.getTypeWeavingTime() );

                this.incrTypeAcceptingCount( classLoaderMetrics.getTypeAcceptingCount() );
                this.incrTypeAcceptingTime( classLoaderMetrics.getTypeAcceptingTime() );

                this.incrAdvisorCreationCount( classLoaderMetrics.getAdvisorCreationCount() );
                this.incrAdvisorCreationTime( classLoaderMetrics.getAdvisorCreationTime() );

                this.incrTypeFastMatchingCount( classLoaderMetrics.getTypeFastMatchingCount() );
                this.incrTypeFastMatchingTime( classLoaderMetrics.getTypeFastMatchingTime() );

                this.incrTypeMatchingCount( classLoaderMetrics.getTypeMatchingCount() );
                this.incrTypeMatchingTime( classLoaderMetrics.getTypeMatchingTime() );

                this.incrTypeTransformationCount( classLoaderMetrics.getTypeTransformationCount() );
                this.incrTypeTransformationTime( classLoaderMetrics.getTypeTransformationTime() );
            }

            this.hasTypeResolution = classLoaderMetricsList.stream()
            .map( metrcis -> metrcis.getTypeResolutuonLevelAdvisorMap().size() )
            .collect( Collectors.summingInt(Integer::intValue) ) > 0;
        }

        /**
         * Adds the given count to the cumulative advisor creation count.
         * Overrides the base implementation to always accumulate rather than set-once.
         *
         * @param advisorCreationCount the number of additional advisors created
         */
        @Override
        public void incrAdvisorCreationCount(int advisorCreationCount) {
            this.setAdvisorCreationCount(this.getAdvisorCreationCount() + advisorCreationCount);
        }

        /**
         * Returns the list of per-class-loader metrics that were aggregated into this snapshot.
         *
         * @return the collection of {@link ClassLoaderMetrics}
         */
        public Collection<ClassLoaderMetrics> getClassLoaderMetricsList() {
            return classLoaderMetricsList;
        }
    }


    /** Sentinel class loader used as a map key for types rejected before class loader acceptance. */
    static class RejectedClassLoader extends ClassLoader {}
}
