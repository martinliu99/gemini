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
package io.gemini.aop;

import java.lang.ref.WeakReference;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.DiagnosticLevel;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigView.Converter.ToString;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.PlaceholderHelper;
import io.gemini.core.util.PlaceholderHelper.PlaceholderResolver;
import net.bytebuddy.utility.JavaModule;

public class AopMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(AopMetrics.class);

    private static final int ITEM_NAME_LENGTH = 40;
    public static final double NANO_TIME = 1e9;

    private final ConfigView configView;
    private final DiagnosticLevel diagnosticLevel;

    private String bannerTemplate;;

    private String launcherStartupSummrayTemplate;
    private String appStartupSummrayTemplate;

    private String weaverSummrayHeaderTemplate;
    private String weaverSummrayDetailTemplate;
    private String weaverSummrayPerCLTemplate;

    private String aopWeavingDetailPerAdvisor;

    private boolean summarizeMetricsDetail = false;
    private final BootstraperMetrics bootstraperMetrics;

    private final AtomicInteger index = new AtomicInteger(0);
    private volatile ConcurrentMap<ClassLoader, WeaverMetrics> weaverMetricsMap;

    private WeaverSummary bytebuddyWarmupSummary;
    private WeaverSummary launcherStartupSummary;
    private WeaverSummary appStartupSummary;


    public AopMetrics(ConfigView configView, DiagnosticLevel diagnosticLevel) {
        // 1.check input argument
        Assert.notNull(configView, "'configView' must not be null.");
        this.configView = configView;

        Assert.notNull(diagnosticLevel, "'diagnosticLevel' must not be null.");
        this.diagnosticLevel = diagnosticLevel;

        // 2.initialize properties
        bootstraperMetrics = new BootstraperMetrics();
        weaverMetricsMap = new ConcurrentReferenceHashMap<>();

        // 3.load settings
        loadSettings(this.configView);
    }


    private void loadSettings(ConfigView configView) {
        this.summarizeMetricsDetail = configView.getAsBoolean("aop.metrics.summarizeMetricsDetail", false);

        this.bannerTemplate = configView.<String>getValue("aop.metrics.bannerTemplate", "", ToString.INSTANCE, false);

        this.launcherStartupSummrayTemplate = configView.<String>getValue("aop.metrics.launcherStartupSummrayTemplate", "", ToString.INSTANCE, false);
        this.appStartupSummrayTemplate =   configView.<String>getValue("aop.metrics.appStartupSummrayTemplate", "", ToString.INSTANCE, false);

        this.weaverSummrayHeaderTemplate = configView.<String>getValue("aop.metrics.weaverSummrayHeaderTemplate", "", ToString.INSTANCE, false);
        this.weaverSummrayDetailTemplate =  configView.<String>getValue("aop.metrics.weaverSummrayDetailTemplate", "", ToString.INSTANCE, false);
        this.weaverSummrayPerCLTemplate  = configView.<String>getValue("aop.metrics.weaverSummrayPerCLTemplate", "", ToString.INSTANCE, false);
    }


    public BootstraperMetrics getBootstraperMetrics() {
        return bootstraperMetrics;
    }

    protected Map<ClassLoader, WeaverMetrics> getWeaverMetricsMap() {
        return weaverMetricsMap;
    }

    public WeaverMetrics createWeaverMetrics(ClassLoader classLoader, JavaModule javaModule) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

        // ignore excluded ClassLoader
        return weaverMetricsMap
                .computeIfAbsent(
                        cacheKey, 
                        cl -> new WeaverMetrics(index.getAndIncrement(), classLoader)
                );
    }

    public WeaverMetrics getWeaverMetrics(ClassLoader classLoader, JavaModule javaModule) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

        return weaverMetricsMap.get(cacheKey);
    }

    public void warmupByteBuddy() {
        this.bytebuddyWarmupSummary = this.newWeaverSummary();
    }

    public void startupLauncher() {
        this.launcherStartupSummary = this.newWeaverSummary();

        if(diagnosticLevel.isSimpleEnabled() == false) {
            LOGGER.info("$Took '{}' seconds to activate Gemini. \n{}\n{}\n", 
                    (System.nanoTime() - bootstraperMetrics.getLauncherStartedAt()) / 1e9,
                    bannerTemplate,
                    renderLauncherStartupSummaryTemplate(bootstraperMetrics) );
        } else {
            LOGGER.info("$Took '{}' seconds to activate Gemini. \n{}\n{}\n{}\n{}", 
                    (System.nanoTime() - bootstraperMetrics.getLauncherStartedAt()) / 1e9,
                    bannerTemplate,
                    renderLauncherStartupSummaryTemplate(bootstraperMetrics),
                    bytebuddyWarmupSummary != null ? renderWeaverMetricsTemplate("Warmup ByteBuddy", bytebuddyWarmupSummary) : "",
                    launcherStartupSummary != null ? renderWeaverMetricsTemplate("Redefined Loaded Types", launcherStartupSummary) : "" );
        }
    }

    public void startupApplication() {
        this.appStartupSummary = this.newWeaverSummary();

        if(diagnosticLevel.isSimpleEnabled() == false) {
            LOGGER.info("$Took '{}' seconds to start application. \n{}\n",
                    (System.nanoTime() - bootstraperMetrics.getLauncherStartedAt()) / 1e9,
                    renderAppStartupSummaryTemplate(bootstraperMetrics) );
        } else {
            LOGGER.info("$Took '{}' seconds to start application. \n{}\n{}",
                    (System.nanoTime() - bootstraperMetrics.getLauncherStartedAt()) / 1e9,
                    renderAppStartupSummaryTemplate(bootstraperMetrics),
                    renderWeaverMetricsTemplate("Weaved New Types", appStartupSummary) );
        }
    }


    private WeaverSummary newWeaverSummary() {
        Map<ClassLoader, WeaverMetrics> existingMetricsMap = this.weaverMetricsMap;
        this.weaverMetricsMap = new ConcurrentHashMap<>();
        return new WeaverSummary(existingMetricsMap);
    }

    private String renderLauncherStartupSummaryTemplate(BootstraperMetrics bootstraperMetrics) {
        Map<String, Object> valueMap = new HashMap<>();

        valueMap.put("launcherStartupTime", bootstraperMetrics.getLauncherStartupTime() / NANO_TIME);

        valueMap.put("launcherSetupTime", bootstraperMetrics.getLauncherSetupTime() / NANO_TIME);

        valueMap.put("loggerCreationTime", bootstraperMetrics.getLoggerCreationTime() / NANO_TIME);

        valueMap.put("aopContextCreationTime", bootstraperMetrics.getAopContextCreationTime() / NANO_TIME);
        valueMap.put("classScannerCreationTime", bootstraperMetrics.getClassScannerCreationTime() / NANO_TIME);

        valueMap.put("classLoaderConfigTime", (bootstraperMetrics.getBootstrapCLConfigTime() + bootstraperMetrics.getAopCLConfigTime()) / NANO_TIME);
        valueMap.put("bootstrapCL", bootstraperMetrics.getBootstrapCLConfigTime() / NANO_TIME);
        valueMap.put("aopCL", bootstraperMetrics.getAopCLConfigTime() / NANO_TIME);

        valueMap.put("advisorFactoryCreationTime", bootstraperMetrics.getAdvisorFactoryCreationTime() / NANO_TIME);
        valueMap.put("aopWeaverCreationTime", bootstraperMetrics.getAopWeaverCreationTime() / NANO_TIME);

        valueMap.put("bytebuddyInstallationTime", bootstraperMetrics.getBytebuddyInstallationTime() / NANO_TIME);

        valueMap.put("bytebuddtWarnupTime", bytebuddyWarmupSummary != null ? bytebuddyWarmupSummary.getTypeLoadingTime() : 0);
        valueMap.put("typeRedefiningTime", bootstraperMetrics.getTypeRedefiningTime() / NANO_TIME);
        valueMap.put("typeWeavingTime", launcherStartupSummary != null ? launcherStartupSummary.getTypeLoadingTime() : 0);

        valueMap.put("uncategorizedTime", bootstraperMetrics.getUncategorizedTime() /NANO_TIME );

        valueMap = format(valueMap);

        StringBuilder advisorSepcs = new StringBuilder();
        if(CollectionUtils.isEmpty(bootstraperMetrics.getAdvisorSpecs()) == false) {
            for(Entry<String, Integer> entry : bootstraperMetrics.getAdvisorSpecs().entrySet()) {
                advisorSepcs.append(entry.getKey()).append(": ").append(entry.getValue()).append(", ");
            }
            advisorSepcs.delete(advisorSepcs.length()-2, advisorSepcs.length());
        } else
            advisorSepcs.append(0);
        valueMap.put("advisorSpecs", advisorSepcs.toString());

        valueMap.put("typeRedefiningCount", bootstraperMetrics.getTypeRedefiningCount());

        PlaceholderHelper placeholderHelper = new PlaceholderHelper.Builder().build(valueMap);
        return placeholderHelper.replace(launcherStartupSummrayTemplate);
    }

    private String renderAppStartupSummaryTemplate(BootstraperMetrics bootstraperMetrics) {
        Map<String, Object> valueMap = new HashMap<>();

        double appStartupTime = (System.nanoTime() - this.bootstraperMetrics.getLauncherStartedAt()) / NANO_TIME;
        valueMap.put("appStartupTime", appStartupTime );

        valueMap.put("launcherStartupTime", bootstraperMetrics.getLauncherStartupTime() / NANO_TIME);
        valueMap.put("tyepWeavingTime", appStartupSummary.getTypeLoadingTime() );

        valueMap.put("uncategorizedTime", appStartupTime - bootstraperMetrics.getLauncherStartupTime() / NANO_TIME - appStartupSummary.getTypeLoadingTime() );

        valueMap = format(valueMap);

        valueMap.put("tyepTransformationCount", appStartupSummary.getTypeTransformationCount() );

        PlaceholderHelper placeholderHelper = new PlaceholderHelper.Builder().build(valueMap);
        return placeholderHelper.replace(appStartupSummrayTemplate);
    }

    private String renderWeaverMetricsTemplate(String phaseName, WeaverSummary weaverStats) {
        StringBuilder renderResult = new StringBuilder();
        renderResult.append(this.weaverSummrayHeaderTemplate);

        // 1.render summary metrics
        {
            Map<String, Object> valueMap = new HashMap<>();

            valueMap.put("typeLoadingCount", weaverStats.getTypeLoadingCount());
            valueMap.put("typeLoadingTime", weaverStats.getTypeLoadingTime());

            valueMap.put("typeAcceptingCount", weaverStats.getTypeAcceptingCount());
            valueMap.put("typeAcceptingTime", weaverStats.getTypeAcceptingTime());

            valueMap.put("advisorCreationCount", weaverStats.getAdvisorCreationCount());
            valueMap.put("advisorCreationTime", weaverStats.getAdvisorCreationTime());

            valueMap.put("typeFastMatchingCount", weaverStats.getTypeFastMatchingCount());
            valueMap.put("typeFastMatchingTime", weaverStats.getTypeFastMatchingTime());

            valueMap.put("typeMatchingCount", weaverStats.getTypeMatchingCount());
            valueMap.put("typeMatchingTime", weaverStats.getTypeMatchingTime());

            valueMap.put("typeTransformationCount", weaverStats.getTypeTransformationCount());
            valueMap.put("typeTransformationTime", weaverStats.getTypeTransformationTime());

            valueMap.put("uncategorizedTime", weaverStats.getUncategorizedTime());

            valueMap = format(valueMap);

            valueMap.put("itemName", formatStr(phaseName + " \u2935") );

            renderResult.append( new PlaceholderHelper.Builder().build(valueMap).replace(weaverSummrayDetailTemplate) );
        }

        // 2.render detail metrics per ClassLoader and Advisor
        for(WeaverMetrics weaverMetrics : weaverStats.getWeaverMetricsList()) {
            if(weaverMetrics.getTypeLoadingCount() <= 1 && weaverMetrics.getAdvisorCreationCount() == 0)
                continue;

            Map<String, Object> valueMap = new HashMap<>();

            valueMap.put("itemName", format(
                    ClassUtils.abbreviate( ClassLoaderUtils.getClassLoaderId(weaverMetrics.getClassLoader()) ) ) );

            valueMap.put("typeLoadingCount", weaverMetrics.getTypeLoadingCount());
            valueMap.put("typeLoadingTime", weaverMetrics.getTypeLoadingTime() / NANO_TIME);

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

            valueMap.put("uncategorizedTime", weaverMetrics.getUncategorizedTime() / NANO_TIME );

            valueMap = format(valueMap);
            renderResult.append(
                    new PlaceholderHelper.Builder()
                    .build( new CompositePlaceholderResolver(valueMap, configView) )
                    .replace(weaverSummrayPerCLTemplate) );

            if(this.summarizeMetricsDetail) {
                Map<Advisor, AtomicLong> advisorMethodMatchingType = weaverMetrics.getAdvisorTypeMatchingTimeMap();
                for(Entry<Advisor, AtomicLong> advisorEntry : weaverMetrics.getAdvisorTypeFastMatchingTimeMap().entrySet()) {
                    // TODO: just top 10
                    if(advisorEntry.getValue().get() != 0) {
                        valueMap = new HashMap<>();
                        Advisor advisorName = advisorEntry.getKey();
                        valueMap.put("advisorName", advisorName);
                        valueMap.put("advisorTypeFastMatchingTime", advisorEntry.getValue().get() / NANO_TIME);
                        valueMap.put("advisorTypeMatchingTime", advisorMethodMatchingType.get(advisorName).get() / NANO_TIME);

                        renderResult.append(
                                new PlaceholderHelper.Builder().build(valueMap).replace(aopWeavingDetailPerAdvisor) );
                    }
                }
            }
        }
        int length = renderResult.length();
        if(length > 0) {
            for(int i=0; i<2; i++) {
                length = renderResult.length();
                char lastChar = renderResult.charAt(length-1);

                if('\r' == lastChar || '\n' == lastChar)
                    renderResult.deleteCharAt(length-1);
            }
        }

        return renderResult.toString();
    }

    private Object formatStr(String item) {
        String str = (String) item;
        str = str.length() < ITEM_NAME_LENGTH ? str : str.substring(0, ITEM_NAME_LENGTH);
        return String.format("%-" + ITEM_NAME_LENGTH + "s", str);
    }

    private Object format(Object item) {
        if(item instanceof String) {
            String str = (String) item;
            str = str.length() < ITEM_NAME_LENGTH ? str : str.substring(0, ITEM_NAME_LENGTH);
            return String.format("%" + ITEM_NAME_LENGTH + "s", str);
        }

        if(item instanceof Float || item instanceof Double)
            return String.format("%9.6f", item);

        if(item instanceof Integer || item instanceof Long) 
            return String.format("%6d", item);

        return item;
    }

    private Map<String, Object> format(Map<String, Object> map) {
        return map.entrySet().stream()
                .map( e -> 
                    new SimpleEntry<>(e.getKey(), format(e.getValue())) )
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }


    private static class CompositePlaceholderResolver implements PlaceholderResolver {

        private final Map<String, Object> valueMap;
        private final ConfigView configView;

        public CompositePlaceholderResolver(Map<String, Object> valueMap, ConfigView configView) {
            this.valueMap = valueMap;
            this.configView = configView;
        }

        @Override
        public String getValue(String key) {
            Object value = valueMap.get(key);
            if(value != null)
                return value.toString();

            return configView.getAsString(key);
        }
    } 


    public class BootstraperMetrics {

        private long launcherStartedAt;
        private long launcherStartupTime;

        private long launcherSetupTime;
        private long loggerCreationTime;

        private long aopContextCreationTime;
        private long classScannerCreationTime;

        private long bootstrapCLConfigTime;
        private long aopCLConfigTime;

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

        protected long getBootstrapCLConfigTime() {
            return bootstrapCLConfigTime;
        }

        public void setBootstrapCLConfigTime(long bootstrapCLConfigTime) {
            this.bootstrapCLConfigTime = bootstrapCLConfigTime;
        }

        protected long getAopCLConfigTime() {
            return aopCLConfigTime;
        }

        public void setAopCLConfigTime(long aopCLConfigTime) {
            this.aopCLConfigTime = aopCLConfigTime;
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

        public void setBytebuddyInstallationTime(long bytebuddyInstallationTime) {
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

        public void setLauncherStartupTime(long launcherStartupTime) {
            this.launcherStartupTime = launcherStartupTime;

            AopMetrics.this.startupLauncher();
        }

        protected long getUncategorizedTime() {
            return launcherStartupTime 
                    - launcherSetupTime
                    - aopContextCreationTime
                    - bootstrapCLConfigTime - aopCLConfigTime
                    - advisorFactoryCreationTime - aopWeaverCreationTime 
                    - bytebuddyInstallationTime - typeRedefiningTime;
        }
    }


    public static class WeaverMetrics {

        protected final static WeaverMetrics DUMMY = new WeaverMetrics(0, WeaverMetrics.class.getClassLoader());


        private final int metricsIndex;
        private final WeakReference<ClassLoader> classLoaderRef;

        private final AtomicInteger typeLoadingCount;
        private final AtomicLong typeLoadingTime;

        private final AtomicInteger typeAcceptingCount;
        private final AtomicLong typeAcceptingTime;

        private final AtomicInteger advisorCreationCount;
        private final AtomicLong advisorCreationTime;

        private final AtomicInteger typeFastMatchingCount;
        private final AtomicLong typeFastMatchingTime;

        private final AtomicInteger typeMatchingCount;
        private final AtomicLong typeMatchingTime;

        private final AtomicInteger typeTransformationCount;
        private final AtomicLong typeTransformationTime;

        private final ConcurrentMap<Advisor, AtomicLong> advisorTypeFastMatchingTimeMap;
        private final ConcurrentMap<Advisor, AtomicLong> advisorTypeMatchingTimeMap;


        public WeaverMetrics(int metricsIndex, ClassLoader classLoader) {
            this(
                    metricsIndex, classLoader,
                    0, 0, 
                    0, 0, 
                    0, 0, 
                    0, 0, 
                    0, 0, 
                    0, 0, 
                    new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>()
                    );
        }

        private WeaverMetrics(int metrcisIndex, ClassLoader classLoader, 
                int typeLoadingCount, long typeLoadingTime,
                int typeAcceptingCount, long typeAcceptingTime,
                int advisorCreationCount, long advisorCreationTime,
                int typeFastMatchingCount, long typeFastMatchingTime,
                int typeMatchingCount, long typeMatchingTime,
                int typeTransformationCount, long typeTransformationTime,
                ConcurrentMap<Advisor, AtomicLong> advisorTypeFastMatchingTimeMap,
                ConcurrentMap<Advisor, AtomicLong> advisorTypeMatchingTimeMap) {
            this.metricsIndex = metrcisIndex;
            this.classLoaderRef = new WeakReference<ClassLoader>(classLoader);

            this.typeLoadingCount =  new AtomicInteger(typeLoadingCount);
            this.typeLoadingTime = new AtomicLong(typeLoadingTime);

            this.typeAcceptingCount = new AtomicInteger(typeAcceptingCount);
            this.typeAcceptingTime = new AtomicLong(typeAcceptingTime);

            this.advisorCreationCount = new AtomicInteger(advisorCreationCount);
            this.advisorCreationTime = new AtomicLong(advisorCreationTime);

            this.typeFastMatchingCount = new AtomicInteger(typeFastMatchingCount);
            this.typeFastMatchingTime = new AtomicLong(typeFastMatchingTime);

            this.typeMatchingCount = new AtomicInteger(typeMatchingCount);
            this.typeMatchingTime = new AtomicLong(typeMatchingTime);

            this.typeTransformationCount = new AtomicInteger(typeTransformationCount);
            this.typeTransformationTime = new AtomicLong(typeTransformationTime);

            this.advisorTypeFastMatchingTimeMap = advisorTypeFastMatchingTimeMap;
            this.advisorTypeMatchingTimeMap= advisorTypeMatchingTimeMap;
        }

        protected int getMetricsIndex() {
            return metricsIndex;
        }

        protected ClassLoader getClassLoader() {
            return classLoaderRef.get();
        }

        protected int getTypeLoadingCount() {
            return typeLoadingCount.get(); 
        }

        public void incrTypeLoadingCount() {
            typeLoadingCount.incrementAndGet();
        }

        protected long getTypeLoadingTime() {
            return typeLoadingTime.get();
        }

        public void incrTypeLoadingTime(long time) {
            typeLoadingTime.addAndGet(time);
        }

        protected int getTypeAcceptingCount() {
            return typeAcceptingCount.get();
        }

        public void incrTypeAcceptingCount() {
            typeAcceptingCount.incrementAndGet();
        }

        protected long getTypeAcceptingTime() {
            return typeAcceptingTime.get();
        }

        public void incrTypeAcceptingTime(long time) {
            typeAcceptingTime.addAndGet(time);
        }

        protected int getAdvisorCreationCount() {
            return advisorCreationCount.get();
        }

        public void incrAdvisorCreationCount(int count) {
            advisorCreationCount.addAndGet(count);
        }

        protected long getAdvisorCreationTime() {
            return advisorCreationTime.get();
        }

        public void incrAdvisorCreationTime(long time) {
            this.advisorCreationTime.addAndGet(time);
        }

        protected int getTypeFastMatchingCount() {
            return typeFastMatchingCount.get();
        }

        public void incrTypeFastMatchingCount() {
            typeFastMatchingCount.incrementAndGet();
        }

        protected long getTypeFastMatchingTime() {
            return typeFastMatchingTime.get();
        }

        public void incrTypeFastMatchingTime(long time) {
            typeFastMatchingTime.addAndGet(time);
        }

        protected Map<Advisor, AtomicLong> getAdvisorTypeFastMatchingTimeMap() {
            return this.advisorTypeFastMatchingTimeMap;
        }

        public void incrAdvisorTypeFastMatchingTime(Advisor advisor, long time) {
            AtomicLong totalTime = this.advisorTypeFastMatchingTimeMap.get(advisor);
            if(totalTime == null)
                return;
            totalTime.addAndGet(time);
        }

        protected int getTypeMatchingCount() {
            return typeMatchingCount.get();
        }

        public void incrTypeMatchingCount() {
            typeMatchingCount.incrementAndGet();
        }

        protected Map<Advisor, AtomicLong> getAdvisorTypeMatchingTimeMap() {
            return this.advisorTypeMatchingTimeMap;
        }

        public void incrAdvisorTypeMatchingTime(Advisor advisor, long time) {
            AtomicLong totalTime = this.advisorTypeMatchingTimeMap.get(advisor);
            if(totalTime == null)
                return;
            totalTime.addAndGet(time);
        }

        protected long getTypeMatchingTime() {
            return typeMatchingTime.get();
        }

        public void incrTypeMatchingTime(long time) {
            typeMatchingTime.addAndGet(time);
        }

        protected int getTypeTransformationCount() {
            return typeTransformationCount.get();
        }

        public void incrTypeTransformationCount() {
            typeTransformationCount.incrementAndGet();
        }

        protected long getTypeTransformationTime() {
            return typeTransformationTime.get();
        }

        public void incrTypeTransformationTime(long time) {
            typeTransformationTime.addAndGet(time);
        }

        protected long getUncategorizedTime() {
            return typeLoadingTime.get() - typeAcceptingTime.get() - advisorCreationTime.get()
                    - typeFastMatchingTime.get() - typeMatchingTime.get() - typeTransformationTime.get();
        }
    }


    protected static class WeaverSummary {

        private final List<WeaverMetrics> weaverMetricsList;

        private final int typeLoadingCount;
        private final double typeLoadingTime;

        private final int typeAcceptingCount;
        private final double typeAcceptingTime;

        private final int advisorCreationCount;
        private final double advisorCreationTime;

        private final int typeFastMatchingCount;
        private final double typeFastMatchingTime;

        private final int typeMatchingCount;
        private final double typeMatchingTime;

        private final int typeTransformationCount;
        private final double typeTransformationTime;

        private final double uncategorizedTime;

        private final ConcurrentMap<Advisor, AtomicLong> advisorTypeFastMatchingTimeMap;
        private final ConcurrentMap<Advisor, AtomicLong> advisorTypeMatchingTimeMap;

        public WeaverSummary(Map<ClassLoader, WeaverMetrics> weaverMetricsMap) {
            weaverMetricsList = weaverMetricsMap.values().stream()
                    .sorted( (e1, e2) -> 
                        e1.getMetricsIndex() - e2.getMetricsIndex() )
                    .collect(Collectors.toList());

            this.typeLoadingCount = weaverMetricsList.stream()
                    .mapToInt( e -> e.getTypeLoadingCount() )
                    .sum();
            this.typeLoadingTime = weaverMetricsList.stream()
                    .mapToLong( e -> e.getTypeLoadingTime() )
                    .sum() / NANO_TIME;

            this.typeAcceptingCount = weaverMetricsList.stream()
                    .mapToInt( e -> e.getTypeAcceptingCount() )
                    .sum();
            this.typeAcceptingTime = weaverMetricsList.stream()
                    .mapToLong( e -> e.getTypeAcceptingTime() )
                    .sum() / NANO_TIME;

            this.advisorCreationCount = weaverMetricsList.stream()
                    .mapToInt( e -> e.getAdvisorCreationCount() )
                    .sum();
            this.advisorCreationTime = weaverMetricsList.stream()
                    .mapToLong( e -> e.getAdvisorCreationTime() )
                    .sum() / NANO_TIME;

            this.typeFastMatchingCount = weaverMetricsList.stream()
                    .mapToInt( e -> e.getTypeFastMatchingCount() )
                    .sum();
            this.typeFastMatchingTime = weaverMetricsList.stream()
                    .mapToLong( e -> e.getTypeFastMatchingTime() )
                    .sum() / NANO_TIME;

            this.typeMatchingCount = weaverMetricsList.stream()
                    .mapToInt( e -> e.getTypeMatchingCount() )
                    .sum();
            this.typeMatchingTime = weaverMetricsList.stream()
                    .mapToLong( e -> e.getTypeMatchingTime() )
                    .sum() / NANO_TIME;

            this.typeTransformationCount = weaverMetricsList.stream()
                    .mapToInt( e -> e.getTypeTransformationCount() )
                    .sum();
            this.typeTransformationTime = weaverMetricsList.stream()
                    .mapToLong( e -> e.getTypeTransformationTime() )
                    .sum() / NANO_TIME;

            this.uncategorizedTime = weaverMetricsList.stream()
                    .mapToLong( e -> e.getUncategorizedTime() )
                    .sum() / NANO_TIME;

            this.advisorTypeFastMatchingTimeMap = new ConcurrentHashMap<>();
            this.advisorTypeMatchingTimeMap = new ConcurrentHashMap<>();
        }

        public List<WeaverMetrics> getWeaverMetricsList() {
            return weaverMetricsList;
        }

        public int getTypeLoadingCount() {
            return typeLoadingCount;
        }

        public double getTypeLoadingTime() {
            return typeLoadingTime;
        }

        public int getTypeAcceptingCount() {
            return typeAcceptingCount;
        }

        public double getTypeAcceptingTime() {
            return typeAcceptingTime;
        }

        public int getAdvisorCreationCount() {
            return advisorCreationCount;
        }

        public double getAdvisorCreationTime() {
            return advisorCreationTime;
        }

        public int getTypeFastMatchingCount() {
            return typeFastMatchingCount;
        }

        public double getTypeFastMatchingTime() {
            return typeFastMatchingTime;
        }

        public int getTypeMatchingCount() {
            return typeMatchingCount;
        }

        public double getTypeMatchingTime() {
            return typeMatchingTime;
        }

        public int getTypeTransformationCount() {
            return typeTransformationCount;
        }

        public double getTypeTransformationTime() {
            return typeTransformationTime;
        }

        public double getUncategorizedTime() {
            return uncategorizedTime;
        }

        public ConcurrentMap<Advisor, AtomicLong> getAdvisorTypeFastMatchingTimeMap() {
            return advisorTypeFastMatchingTimeMap;
        }

        public ConcurrentMap<Advisor, AtomicLong> getAdvisorTypeMatchingTimeMap() {
            return advisorTypeMatchingTimeMap;
        }
    }

}
