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
import net.bytebuddy.matcher.ElementMatcher;
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

    private String aopWeavingDetailPerAspect;

    private boolean summarizeMetricsDetail = false;
    private final BootstraperMetrics bootstraperMetrics;

    private final AtomicInteger index = new AtomicInteger(0);
    private volatile ConcurrentMap<ClassLoader, WeaverMetrics> weaverMetricsMap;

    private WeaverSummary bytebuddyWarmupSummary;
    private WeaverSummary launcherStartupSummary;
    private WeaverSummary appStartupSummary;

    private ElementMatcher<String> excludedClassLoaderMatcher;


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


    public void setExcludedClassLoaderMatcher(ElementMatcher<String> excludedClassLoaderMatcher) {
        this.excludedClassLoaderMatcher = excludedClassLoaderMatcher;
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
        if(excludedClassLoaderMatcher != null
                && excludedClassLoaderMatcher.matches(ClassLoaderUtils.getClassLoaderName(classLoader)) == true) {
            return WeaverMetrics.DUMMY;
        }

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

        valueMap.put("loggerCreationTime", bootstraperMetrics.getLoggerCreationTime() / NANO_TIME);

        valueMap.put("aopContextCreationTime", bootstraperMetrics.getAopContextCreationTime() / NANO_TIME);
        valueMap.put("classScannerCreationTime", bootstraperMetrics.getClassScannerCreationTime() / NANO_TIME);

        valueMap.put("bootstrapCLConfigTime", bootstraperMetrics.getBootstrapCLConfigTime() / NANO_TIME);
        valueMap.put("aopCLConfigTime", bootstraperMetrics.getAopCLConfigTime() / NANO_TIME);

        valueMap.put("aspectFactoryCreationTime", bootstraperMetrics.getAspectFactoryCreationTime() / NANO_TIME);
        valueMap.put("aspectWeaverCreationTime", bootstraperMetrics.getAspectWeaverCreationTime() / NANO_TIME);

        valueMap.put("bytebuddyInstallationTime", bootstraperMetrics.getBytebuddyInstallationTime() / NANO_TIME);

        valueMap.put("bytebuddtWarnupTime", bytebuddyWarmupSummary != null ? bytebuddyWarmupSummary.getTypeLoadingTime() : 0);
        valueMap.put("typeRedefiningTime", bootstraperMetrics.getTypeRedefiningTime() / NANO_TIME);
        valueMap.put("typeWeavingTime", launcherStartupSummary != null ? launcherStartupSummary.getTypeLoadingTime() : 0);

        valueMap.put("uncategorizedTime", bootstraperMetrics.getUncategorizedTime() /NANO_TIME );

        valueMap = format(valueMap);

        StringBuilder aspectSepcs = new StringBuilder();
        if(CollectionUtils.isEmpty(bootstraperMetrics.getAspectSpecs()) == false) {
            for(Entry<String, Integer> entry : bootstraperMetrics.getAspectSpecs().entrySet()) {
                aspectSepcs.append(entry.getKey()).append(": ").append(entry.getValue()).append(", ");
            }
            aspectSepcs.delete(aspectSepcs.length()-2, aspectSepcs.length());
        } else
            aspectSepcs.append(0);
        valueMap.put("aspectSpecs", aspectSepcs.toString());

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

            valueMap.put("aspectCreationCount", weaverStats.getAspectCreationCount());
            valueMap.put("aspectCreationTime", weaverStats.getAspectCreationTime());

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

        // 2.render detail metrics per ClassLoader and Aspect
        for(WeaverMetrics weaverMetrics : weaverStats.getWeaverMetricsList()) {
            if(weaverMetrics.getTypeLoadingCount() <= 1 && weaverMetrics.getAspectCreationCount() == 0)
                continue;

            Map<String, Object> valueMap = new HashMap<>();

            valueMap.put("itemName", format(
                    ClassUtils.abbreviate( ClassLoaderUtils.getClassLoaderId(weaverMetrics.getClassLoader()) ) ) );

            valueMap.put("typeLoadingCount", weaverMetrics.getTypeLoadingCount());
            valueMap.put("typeLoadingTime", weaverMetrics.getTypeLoadingTime() / NANO_TIME);

            valueMap.put("typeAcceptingCount", weaverMetrics.getTypeAcceptingCount());
            valueMap.put("typeAcceptingTime", weaverMetrics.getTypeAcceptingTime() / NANO_TIME);

            valueMap.put("aspectCreationCount", weaverMetrics.getAspectCreationCount());
            valueMap.put("aspectCreationTime", weaverMetrics.getAspectCreationTime() / NANO_TIME);

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
                Map<Aspect, AtomicLong> aspectMethodMatchingType = weaverMetrics.getAspectTypeMatchingTimeMap();
                for(Entry<Aspect, AtomicLong> aspectEntry : weaverMetrics.getAspectTypeFastMatchingTimeMap().entrySet()) {
                    // TODO: just top 10
                    if(aspectEntry.getValue().get() != 0) {
                        valueMap = new HashMap<>();
                        Aspect aspectName = aspectEntry.getKey();
                        valueMap.put("aspectName", aspectName);
                        valueMap.put("aspectTypeFastMatchingTime", aspectEntry.getValue().get() / NANO_TIME);
                        valueMap.put("aspectTypeMatchingTime", aspectMethodMatchingType.get(aspectName).get() / NANO_TIME);

                        renderResult.append(
                                new PlaceholderHelper.Builder().build(valueMap).replace(aopWeavingDetailPerAspect) );
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

        private long loggerCreationTime;

        private long aopContextCreationTime;
        private long classScannerCreationTime;

        private long bootstrapCLConfigTime;
        private long aopCLConfigTime;

        private long aspectFactoryCreationTime;
        private Map<String, Integer> aspectSpecs;
        private long aspectWeaverCreationTime;

        private long bytebuddyInstallationTime;

        private long typeRedefiningTime;
        private int typeRedefiningCount;


        protected long getLauncherStartedAt() {
            return launcherStartedAt;
        }

        public void setLauncherStartedAt(long launcherStartedAt) {
            this.launcherStartedAt = launcherStartedAt;
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

        protected long getAspectFactoryCreationTime() {
            return aspectFactoryCreationTime;
        }

        public void setAspectFactoryCreationTime(long aspectFactoryCreationTime) {
            this.aspectFactoryCreationTime = aspectFactoryCreationTime;
        }

        protected Map<String, Integer> getAspectSpecs() {
            return aspectSpecs;
        }

        public void setAspectSpecs(Map<String, Integer> aspectSpecs) {
            this.aspectSpecs = aspectSpecs;
        }

        protected long getAspectWeaverCreationTime() {
            return aspectWeaverCreationTime;
        }

        public void setAspectWeaverCreationTime(long aspectDefinitionLoadingTime) {
            this.aspectWeaverCreationTime = aspectDefinitionLoadingTime;
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
            return this.launcherStartupTime 
                    - loggerCreationTime - aopContextCreationTime
                    - bootstrapCLConfigTime - aopCLConfigTime
                    - aspectFactoryCreationTime - aspectWeaverCreationTime 
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

        private final AtomicInteger aspectCreationCount;
        private final AtomicLong aspectCreationTime;

        private final AtomicInteger typeFastMatchingCount;
        private final AtomicLong typeFastMatchingTime;

        private final AtomicInteger typeMatchingCount;
        private final AtomicLong typeMatchingTime;

        private final AtomicInteger typeTransformationCount;
        private final AtomicLong typeTransformationTime;

        private final ConcurrentMap<Aspect, AtomicLong> aspectTypeFastMatchingTimeMap;
        private final ConcurrentMap<Aspect, AtomicLong> aspectTypeMatchingTimeMap;


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
                int aspectCreationCount, long aspectCreationTime,
                int typeFastMatchingCount, long typeFastMatchingTime,
                int typeMatchingCount, long typeMatchingTime,
                int typeTransformationCount, long typeTransformationTime,
                ConcurrentMap<Aspect, AtomicLong> aspectTypeFastMatchingTimeMap,
                ConcurrentMap<Aspect, AtomicLong> aspectTypeMatchingTimeMap) {
            this.metricsIndex = metrcisIndex;
            this.classLoaderRef = new WeakReference<ClassLoader>(classLoader);

            this.typeLoadingCount =  new AtomicInteger(typeLoadingCount);
            this.typeLoadingTime = new AtomicLong(typeLoadingTime);

            this.typeAcceptingCount = new AtomicInteger(typeAcceptingCount);
            this.typeAcceptingTime = new AtomicLong(typeAcceptingTime);

            this.aspectCreationCount = new AtomicInteger(aspectCreationCount);
            this.aspectCreationTime = new AtomicLong(aspectCreationTime);

            this.typeFastMatchingCount = new AtomicInteger(typeFastMatchingCount);
            this.typeFastMatchingTime = new AtomicLong(typeFastMatchingTime);

            this.typeMatchingCount = new AtomicInteger(typeMatchingCount);
            this.typeMatchingTime = new AtomicLong(typeMatchingTime);

            this.typeTransformationCount = new AtomicInteger(typeTransformationCount);
            this.typeTransformationTime = new AtomicLong(typeTransformationTime);

            this.aspectTypeFastMatchingTimeMap = aspectTypeFastMatchingTimeMap;
            this.aspectTypeMatchingTimeMap= aspectTypeMatchingTimeMap;
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

        protected int getAspectCreationCount() {
            return aspectCreationCount.get();
        }

        public void incrAspectCreationCount(int count) {
            aspectCreationCount.addAndGet(count);
        }

        protected long getAspectCreationTime() {
            return aspectCreationTime.get();
        }

        public void incrAspectCreationTime(long time) {
            this.aspectCreationTime.addAndGet(time);
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

        protected Map<Aspect, AtomicLong> getAspectTypeFastMatchingTimeMap() {
            return this.aspectTypeFastMatchingTimeMap;
        }

        public void incrAspectTypeFastMatchingTime(Aspect aspect, long time) {
            AtomicLong totalTime = this.aspectTypeFastMatchingTimeMap.get(aspect);
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

        protected Map<Aspect, AtomicLong> getAspectTypeMatchingTimeMap() {
            return this.aspectTypeMatchingTimeMap;
        }

        public void incrAspectTypeMatchingTime(Aspect aspect, long time) {
            AtomicLong totalTime = this.aspectTypeMatchingTimeMap.get(aspect);
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
            return typeLoadingTime.get() - typeAcceptingTime.get() - aspectCreationTime.get()
                    - typeFastMatchingTime.get() - typeMatchingTime.get() - typeTransformationTime.get();
        }
    }


    protected static class WeaverSummary {

        private final List<WeaverMetrics> weaverMetricsList;

        private final int typeLoadingCount;
        private final double typeLoadingTime;

        private final int typeAcceptingCount;
        private final double typeAcceptingTime;

        private final int aspectCreationCount;
        private final double aspectCreationTime;

        private final int typeFastMatchingCount;
        private final double typeFastMatchingTime;

        private final int typeMatchingCount;
        private final double typeMatchingTime;

        private final int typeTransformationCount;
        private final double typeTransformationTime;

        private final double uncategorizedTime;

        private final ConcurrentMap<Aspect, AtomicLong> aspectTypeFastMatchingTimeMap;
        private final ConcurrentMap<Aspect, AtomicLong> aspectTypeMatchingTimeMap;

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

            this.aspectCreationCount = weaverMetricsList.stream()
                    .mapToInt( e -> e.getAspectCreationCount() )
                    .sum();
            this.aspectCreationTime = weaverMetricsList.stream()
                    .mapToLong( e -> e.getAspectCreationTime() )
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

            this.aspectTypeFastMatchingTimeMap = new ConcurrentHashMap<>();
            this.aspectTypeMatchingTimeMap = new ConcurrentHashMap<>();
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

        public int getAspectCreationCount() {
            return aspectCreationCount;
        }

        public double getAspectCreationTime() {
            return aspectCreationTime;
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

        public ConcurrentMap<Aspect, AtomicLong> getAspectTypeFastMatchingTimeMap() {
            return aspectTypeFastMatchingTimeMap;
        }

        public ConcurrentMap<Aspect, AtomicLong> getAspectTypeMatchingTimeMap() {
            return aspectTypeMatchingTimeMap;
        }
    }

}
