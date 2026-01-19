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


    public LauncherMetrics getLauncherMetrics() {
        return launcherMetrics;
    }


    public TypeMetrics createTypeMetrics(ClassLoader targetClassLoader, String targetTypeName, long startedAt) {
        TYPE_METRICS_HOLDER.set(
                new TypeMetrics(targetClassLoader, targetTypeName, startedAt) );

        return TYPE_METRICS_HOLDER.get();
    }

    public static TypeMetrics currentTypeMetrics() {
        return TYPE_METRICS_HOLDER.get();
    }

    public void collect(TypeMetrics typeMetrics) {
        this.queue.offer(typeMetrics);
        TYPE_METRICS_HOLDER.remove();
    }

    public void stop() {
        this.running.compareAndSet(true, false);

        this.workThread.interrupt();
    }

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


    protected void warmupByteBuddy() {
        this.bytebuddyWarmupMetrics = this.newWeaverMetricsSummary();
    }

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


    private WeaverMetrics newWeaverMetricsSummary() {
        Map<ClassLoader, ClassLoaderMetrics> existingMetricsMap = this.classLoaderMetricsMap;
        this.classLoaderMetricsMap = new LinkedHashMap<>();

        return new WeaverMetrics(existingMetricsMap.values());
    }


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
                if (classLoaderMetrics.getTypeWeavingCount() <= 1 && classLoaderMetrics.getAdvisorCreationCount() == 0)
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

        private String formatStr(String item, int itemLength, boolean leftAlign) {
            String str = (String) item;

            if (itemLength == 0)
                itemLength = str.length();

            str = str.length() <= itemLength ? str : str.substring(0, itemLength);
            return String.format("%" + (leftAlign ? "-" : "") + itemLength + "s", str);
        }

        private Object format(Object item) {
            if (item instanceof String) {
                return formatStr( (String) item, ITEM_NAME_LENGTH, false);
            }

            if (item instanceof Float || item instanceof Double)
                return String.format("%9.6f", item);

            if (item instanceof Integer || item instanceof Long) 
                return String.format("%6d", item);

            return item;
        }

        private Map<String, Object> format(Map<String, Object> map) {
            return map.entrySet().stream()
                    .map( e -> 
                        new SimpleEntry<>(e.getKey(), format(e.getValue())) )
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        }


        public String getBannerTemplate() {
            return bannerTemplate;
        }
    }


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


        protected long getLauncherStartedAt() {
            return launcherStartedAt;
        }

        public void setLauncherStartedAt(long launcherStartedAt) {
            this.launcherStartedAt = launcherStartedAt;
        }

        protected long getLauncherSetupTime() {
            return launcherSetupTime;
        }

        public void setLauncherSetupTime(long launcherSetupTime) {
            this.launcherSetupTime = launcherSetupTime;
        }

        protected long getLoggerCreationTime() {
            return loggerCreationTime;
        }

        public void setLoggerCreationTime(long loggerCreationTime) {
            this.loggerCreationTime = loggerCreationTime;
        }


        protected long getAopContextCreationTime() {
            return aopContextCreationTime;
        }

        public void setAopContextCreationTime(long aopContextCreationTime) {
            this.aopContextCreationTime = aopContextCreationTime;
        }

        protected long getClassScannerCreationTime() {
            return classScannerCreationTime;
        }

        public void setClassScannerCreationTime(long classScannerCreationTime) {
            this.classScannerCreationTime = classScannerCreationTime;
        }

        protected long getBootstrapClassConfigTime() {
            return bootstrapClassConfigTime;
        }

        public void setBootstrapClassConfigTime(long bootstrapClassConfigTime) {
            this.bootstrapClassConfigTime = bootstrapClassConfigTime;
        }

        protected long getAopCLConfigTime() {
            return aopClassLoaderConfigTime;
        }

        public void setAopClassLoaderConfigTime(long aopClassLoaderConfigTime) {
            this.aopClassLoaderConfigTime = aopClassLoaderConfigTime;
        }

        protected long getAdvisorFactoryCreationTime() {
            return advisorFactoryCreationTime;
        }

        public void setAdvisorFactoryCreationTime(long advisorFactoryCreationTime) {
            this.advisorFactoryCreationTime = advisorFactoryCreationTime;
        }

        protected Map<String, Integer> getAdvisorSpecs() {
            return advisorSpecs;
        }

        public void setAdvisorSpecs(Map<String, Integer> advisorSpecs) {
            this.advisorSpecs = advisorSpecs;
        }

        protected long getAopWeaverCreationTime() {
            return aopWeaverCreationTime;
        }

        public void setAopWeaverCreationTime(long aopWeaverCreationTime) {
            this.aopWeaverCreationTime = aopWeaverCreationTime;
        }

        protected long getBytebuddyInstallationTime() {
            return bytebuddyInstallationTime;
        }

        public void warmupByteBuddy(long bytebuddyInstallationTime) {
            this.bytebuddyInstallationTime = bytebuddyInstallationTime;

            AopMetrics.this.warmupByteBuddy();
        }

        protected long getTypeRedefiningTime() {
            return typeRedefiningTime;
        }

        public void setTypeRedefiningTime(long typeRedefiningTime) {
            this.typeRedefiningTime = typeRedefiningTime;
        }

        protected int getTypeRedefiningCount() {
            return typeRedefiningCount;
        }

        public void incrTypeRedefiningCount(int typeRedefiningCount) {
            this.typeRedefiningCount += typeRedefiningCount;
        }

        protected long getLauncherStartupTime() {
            return this.launcherStartupTime;
        }

        public void startupAopLauncher() {
            this.launcherStartupTime = System.nanoTime() - launcherMetrics.getLauncherStartedAt();

            AopMetrics.this.startupAopLauncher();
        }

        protected long getUncategorizedTime() {
            return launcherStartupTime 
                    - launcherSetupTime
                    - aopContextCreationTime
                    - bootstrapClassConfigTime - aopClassLoaderConfigTime
                    - advisorFactoryCreationTime - aopWeaverCreationTime 
                    - bytebuddyInstallationTime - typeRedefiningTime;
        }
    }


    static class BaseMetrics {

        private final WeakReference<ClassLoader> targetClassLoaderRef;

        private long typeWeavingTime = 0;

        private long typeAcceptingTime = 0;

        private int advisorCreationCount = 0;
        private long advisorCreationTime = 0;

        private long typeFastMatchingTime = 0;

        private long typeMatchingTime = 0;

        private long typeTransformationTime = 0;


        public BaseMetrics(ClassLoader targetClassLoader) {
            this.targetClassLoaderRef = new WeakReference<>(targetClassLoader);
        }


        public ClassLoader getTargetClassLoader() {
            return targetClassLoaderRef.get();
        }

        public long getTypeWeavingTime() {
            return typeWeavingTime;
        }

        public void incrTypeWeavingTime(long typeWeavingTime) {
            this.typeWeavingTime += typeWeavingTime;
        }

        public long getTypeAcceptingTime() {
            return typeAcceptingTime;
        }

        public void incrTypeAcceptingTime(long typeAcceptingTime) {
            this.typeAcceptingTime += typeAcceptingTime;
        }

        public int getAdvisorCreationCount() {
            return advisorCreationCount;
        }

        public void setAdvisorCreationCount(int advisorCreationCount) {
            this.advisorCreationCount = advisorCreationCount;
        }

        public void incrAdvisorCreationCount(int advisorCreationCount) {
            if (this.advisorCreationCount == 0)
                this.advisorCreationCount = advisorCreationCount;
        }

        public long getAdvisorCreationTime() {
            return advisorCreationTime;
        }

        public void incrAdvisorCreationTime(long advisorCreationTime) {
            this.advisorCreationTime += advisorCreationTime;
        }

        public long getTypeFastMatchingTime() {
            return typeFastMatchingTime;
        }

        public void incrTypeFastMatchingTime(long typeFastMatchingTime) {
            this.typeFastMatchingTime += typeFastMatchingTime;
        }

        public long getTypeMatchingTime() {
            return typeMatchingTime;
        }

        public void incrTypeMatchingTime(long typeMatchingTime) {
            this.typeMatchingTime += typeMatchingTime;
        }

        public long getTypeTransformationTime() {
            return typeTransformationTime;
        }

        public void incrTypeTransformationTime(long typeTransformationTime) {
            this.typeTransformationTime += typeTransformationTime;
        }


        public long getUncategorizedTime() {
            return getTypeWeavingTime() - getTypeAcceptingTime() - getAdvisorCreationTime()
                    - getTypeFastMatchingTime() - getTypeMatchingTime() - getTypeTransformationTime();
        }
    }


    public static class TypeMetrics extends BaseMetrics {

        private long startedAt;
        private List<Map<String /* AdvisorName */, ResolutionLevel>> advisorResolutuonLevelMaps;


        public TypeMetrics(ClassLoader targetClassLoader, String targetTypeName, long startedAt) {
            super(targetClassLoader);

            this.startedAt = startedAt;
            this.advisorResolutuonLevelMaps = new ArrayList<>();
        }


        public long getStartedAt() {
            return startedAt;
        }

        public List<Map<String, ResolutionLevel>> getAdvisorResolutuonLevelMaps() {
            return advisorResolutuonLevelMaps;
        }

        public void addAdvisorResolutuonLevelMap(Map<String, ResolutionLevel> advisorResolutuonLevelMap) {
            if (advisorResolutuonLevelMap != null)
                this.advisorResolutuonLevelMaps.add(advisorResolutuonLevelMap);
        }
    }


    static class ClassLoaderMetrics extends BaseMetrics {

        private int typeWeavingCount = 0;

        private int typeAcceptingCount = 0;

        private int typeFastMatchingCount = 0;
        private int typeMatchingCount = 0;

        private int typeTransformationCount = 0;

        private final Map<ResolutionLevel, Map<String, Integer>> typeResolutuonLevelAdvisorMap = new LinkedHashMap<>();


        public ClassLoaderMetrics(ClassLoader classLoader) {
            super(classLoader);
        }

        public int getTypeWeavingCount() {
            return typeWeavingCount;
        }

        public void incrTypeWeavingCount(int typeWeavingCount) {
            this.typeWeavingCount += typeWeavingCount;
        }

        public int getTypeAcceptingCount() {
            return typeAcceptingCount;
        }

        public void incrTypeAcceptingCount(int typeAcceptingCount) {
            this.typeAcceptingCount += typeAcceptingCount;
        }

        public int getTypeFastMatchingCount() {
            return typeFastMatchingCount;
        }

        public void incrTypeFastMatchingCount(int typeFastMatchingCount) {
            this.typeFastMatchingCount += typeFastMatchingCount;
        }

        public int getTypeMatchingCount() {
            return typeMatchingCount;
        }

        public void incrTypeMatchingCount(int typeMatchingCount) {
            this.typeMatchingCount += typeMatchingCount;
        }

        public int getTypeTransformationCount() {
            return typeTransformationCount;
        }

        public void incrTypeTransformationCount(int typeTransformationCount) {
            this.typeTransformationCount += typeTransformationCount;
        }

        public Map<ResolutionLevel, Map<String, Integer>> getTypeResolutuonLevelAdvisorMap() {
            return typeResolutuonLevelAdvisorMap;
        }
    }


    static class WeaverMetrics extends ClassLoaderMetrics {

        private final Collection<ClassLoaderMetrics> classLoaderMetricsList;

        private final boolean hasTypeResolution;


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

        public void incrAdvisorCreationCount(int advisorCreationCount) {
            this.setAdvisorCreationCount(this.getAdvisorCreationCount() + advisorCreationCount);
        }

        public Collection<ClassLoaderMetrics> getClassLoaderMetricsList() {
            return classLoaderMetricsList;
        }
    }


    static class RejectedClassLoader extends ClassLoader {}
}
