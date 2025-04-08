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

import java.lang.instrument.Instrumentation;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopMetrics.BootstraperMetrics;
import io.gemini.aop.agent.classloader.AgentClassLoader;
import io.gemini.aop.aspectapp.AspectContext;
import io.gemini.aop.aspectapp.support.AspectContextHelper;
import io.gemini.aop.classloader.AspectClassLoader;
import io.gemini.aop.matcher.Pattern.Parser;
import io.gemini.aop.matcher.StringMatcherFactory;
import io.gemini.aop.support.AopSettingHelper;
import io.gemini.aop.weaver.advice.ClassInitializerAdvice;
import io.gemini.aop.weaver.advice.ClassMethodAdvice;
import io.gemini.aop.weaver.advice.InstanceConstructorAdvice;
import io.gemini.aop.weaver.advice.InstanceMethodAdvice;
import io.gemini.aspectj.weaver.world.TypeWorldFactory;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.concurrent.TaskExecutor;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigView.Converter;
import io.gemini.core.logging.LoggingSystem;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.Closeable;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.object.Resources;
import io.gemini.core.pool.TypePoolFactory;
import io.gemini.core.pool.TypePoolFactory.Default;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.PlaceholderHelper;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.agent.builder.AgentBuilder.ClassFileBufferStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.LocationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;


/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AopContext implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AopContext.class);

    public static final String JOINPOINT_MATCHER_MATCH_JOINPOINT_KEY = "aop.joinpointMatcher.matchJoinpoint";

    public static final String ASPECT_WEAVER_INCLUDED_CLASS_LOADERS_KEY = "aop.joinpointMatcher.includedClassLoaders";
    public static final String ASPECT_WEAVER_EXCLUDED_CLASS_LOADERS_KEY = "aop.joinpointMatcher.excludedClassLoaders";

    public static final String ASPECT_WEAVER_INCLUDED_TYPE_PATTERNS_KEY = "aop.joinpointMatcher.includedTypePatterns";
    public static final String ASPECT_WEAVER_EXCLUDED_TYPE_PATTERNS_KEY = "aop.joinpointMatcher.excludedTypePatterns";

    public static final String ASPECT_WEAVER_INCLUDED_ASPECT_APPS_KEY = "aop.joinpointMatcher.includedAspectApps";
    public static final String ASPECT_WEAVER_EXCLUDED_ASPECT_APPS_KEY = "aop.joinpointMatcher.excludedAspectApps";

    public static final String ASPECT_WEAVER_INCLUDED_ASPECTS_KEY = "aop.joinpointMatcher.includedAspects";
    public static final String ASPECT_WEAVER_EXCLUDED_ASPECTS_KEY = "aop.joinpointMatcher.excludedAspects";

    public static final String ASPECT_WEAVER_GLOBAL_MATCHING_CLASS_LOADERS_KEY = "aop.joinpointMatcher.globalMatchingClassLoader";


    private final static Set<String /* Class prefix */ > CONDITIONAL_BUILTIN_PARENT_FIRST_CLASS_PREFIXES;

    static {
        CONDITIONAL_BUILTIN_PARENT_FIRST_CLASS_PREFIXES = new LinkedHashSet<>();
        CONDITIONAL_BUILTIN_PARENT_FIRST_CLASS_PREFIXES.add("org.aspectj.lang.annotation");
    }


    private final Instrumentation instrumentation;

    private final Map<String, String> builtinSettings;
    private final LoggingSystem loggingSystem;

    private final AgentClassLoader agentClassLoader;
    private final ConfigView configView;
    private final PlaceholderHelper placeholderHelper;


    // diagnostic settings
    private final DiagnosticLevel diagnoticLevel;
    private Set<String> diagnosticClasses;

    // profile settings
    private final boolean defaultProfile;
    private final String activeProfile;


    // AgentClassLoader settings
    private Set<String> bootstrapClasses;

    private Set<String> parentFirstClassPrefixes;
    private Set<String> parentFirstResourcePrefixes;


    // JoinpointMatcher settings
    private boolean matchJoinpoint;

    private Set<String> includedClassLoaders;
    private Set<String> excludedClassLoaders;

    private Set<String> includedTypePatterns;
    private Set<String> excludedTypePatterns;

    private Set<String> includedAspectApps;
    private Set<String> excludedAspectApps;

    private Set<String> includedAspects;
    private Set<String> excludedAspects;

    private Set<String> globalMatchingClassLoader;


    // JoinpointTransformer settings
    private Class<?> classInitializerAdvice;
    private Class<?> classMethodAdvice;
    private Class<?> instanceConstructorAdvice;
    private Class<?> instanceMethodAdvice;

    private boolean asmAutoCompute = false;

    private boolean dumpByteCode;
    private String byteCodeDumpPath;


    // aspectapp settings
    private String aspectAppsLocation;
    private boolean scanGeminiLibs;
    private boolean scanClassesFolder;
    private Set<String> scannedClassFolders;

    private boolean shareAspectClassLoader;
    private List<Set<String>> conflictJoinpointClassLoaders;


    // weaver installer settings
    private RedefinitionStrategy redefinitionStrategy;


    private final AopMetrics aopMetrics;

    private TypePoolFactory typePoolFactory;
    private TypeWorldFactory typeWorldFactory;


    private boolean processInParallel;
    private TaskExecutor globalTaskExecutor;

    private AspectContextHelper aspectContextHelper;
    private ClassScanner classScanner;
    private ObjectFactory objectFactory;

    private Map<String /* AspectAppName */, AspectContext> aspectContextMap;

    private final ElementMatcher<String> excludedClassLoaderMatcher;


    public AopContext(Instrumentation instrumentation, 
            AgentClassLoader agentClassLoader,
            AopSettingHelper aopSettingHelper, 
            Map<String, String> builtinSettings, 
            LoggingSystem loggingSystem) {
        long startedAt = System.nanoTime();

        Assert.notNull(aopSettingHelper, "'aopSettingHelper' must not be null.");
        this.diagnoticLevel = aopSettingHelper.getDiagnosticLevel();

        if(diagnoticLevel.isSimpleEnabled()) 
            LOGGER.info("^Creating AopContext with settings, \n"
                    + "  classLoader: {} \n  isDefaultProfile: {} \n  activeProfile: {} \n"
                    + "  builtinConfigName: {} \n  userDefinedConfigName: {} \n  diagnosticStrategy: {} \n"
                    + "  logConfigFileName: {} \n",
                    agentClassLoader, aopSettingHelper.isDefaultProfile(), aopSettingHelper.getActiveProfile(),
                    aopSettingHelper.getBuiltinConfigName(), aopSettingHelper.getUserDefinedConfigName(), aopSettingHelper.getDiagnosticLevel(),
                    aopSettingHelper.getLogConfigFileName()
            );


        // 1.check input arguments and initialize properties
        Assert.notNull(instrumentation, "'instrumentation' must not be null.");
        this.instrumentation = instrumentation;

        Assert.notNull(builtinSettings, "'builtinSettings' must not be null.");
        this.builtinSettings = builtinSettings;

        Assert.notNull(loggingSystem, "'loggingSystem' must not be null.");
        this.loggingSystem = loggingSystem;

        Assert.notNull(agentClassLoader, "'AgentClassLoader' must not be null.");
        this.agentClassLoader = agentClassLoader;

        this.configView = new ConfigView.Builder()
                .configSource(aopSettingHelper.getConfigSource())
                .build();

        this.placeholderHelper = new PlaceholderHelper.Builder().build(configView);


        // 2.load aop settings
        this.defaultProfile = aopSettingHelper.isDefaultProfile();
        this.activeProfile = aopSettingHelper.getActiveProfile();

        this.loadSettings(configView);


        // 3.create properties
        this.aopMetrics = new AopMetrics(configView, this.diagnoticLevel);

        this.typePoolFactory = createTypePoolFactory();
        this.typeWorldFactory = createTypeWorldFactory(typePoolFactory, configView);

        this.globalTaskExecutor = TaskExecutor.create("globalTaskExecutor", this.processInParallel);

        this.aspectContextHelper = new AspectContextHelper(this.diagnoticLevel, 
                this.aspectAppsLocation, 
                this.scanGeminiLibs, aopSettingHelper.getAopBootstraperConfig().getAgentResources(),
                this.scanClassesFolder, this.scannedClassFolders);

        Map<String, Resources> appResourcesMap = this.aspectContextHelper.findAppResources();
        Map<String, AspectClassLoader> aspectClassLoaderMap = this.aspectContextHelper
                .createAspectClassLoaders(appResourcesMap, agentClassLoader);

        this.classScanner = createClassScanner(agentClassLoader, aspectClassLoaderMap,
                configView, aopMetrics.getBootstraperMetrics());
        this.objectFactory = createObjectFactory();

        this.aspectContextMap = createAspectContextMap(appResourcesMap, aspectClassLoaderMap);


        StringMatcherFactory classLoaderMatcherFactory = new StringMatcherFactory();
        this.excludedClassLoaderMatcher = classLoaderMatcherFactory.createStringMatcher(
                AopContext.ASPECT_WEAVER_EXCLUDED_CLASS_LOADERS_KEY,
                Parser.parsePatterns( getExcludedClassLoaders() ), 
                true, false );

        this.aopMetrics.setExcludedClassLoaderMatcher(excludedClassLoaderMatcher);


        if(diagnoticLevel.isSimpleEnabled()) {
            LOGGER.info("$Took '{}' seconds to create AopContext.", (System.nanoTime() - startedAt) / 1e9);
        }
    }

    private void loadSettings(ConfigView configView) {
        // load diagnostic settings
        {
            this.diagnosticClasses = configView.getAsStringSet("aop.agent.diagnosticClasses", Collections.emptySet());
        }

        // load bootstrap ClassLoader settings
        {
            this.bootstrapClasses = configView.getAsStringSet("aop.bootstrapClassLoader.bootstrapClasses", Collections.emptySet());
        }

        // load AgentClassLoader settings
        {
            {
                Set<String> parentFirstClassPrefixes = new LinkedHashSet<>();
                parentFirstClassPrefixes.addAll(
                        configView.getAsStringList("aop.agentClassLoader.builtinParentFirstClassPrefixes", Collections.emptyList()) );
                parentFirstClassPrefixes.addAll(
                        configView.getAsStringList("aop.agentClassLoader.parentFirstClassPrefixes", Collections.emptyList()) );
                this.parentFirstClassPrefixes = parentFirstClassPrefixes;
            }

            {
                this.parentFirstResourcePrefixes = configView.getAsStringSet("aop.agentClassLoader.parentFirstResourcePrefixes", new LinkedHashSet<>());
                for(String classPrefix : this.parentFirstClassPrefixes) {
                    this.parentFirstResourcePrefixes.add( ClassUtils.convertClassToResource(classPrefix) );
                }
            }
        }

        // load joinpoint matcher settings
        {
            this.matchJoinpoint = configView.getAsBoolean(JOINPOINT_MATCHER_MATCH_JOINPOINT_KEY, true);
            if(matchJoinpoint == false) {
                LOGGER.warn("WARNING! Setting '{}' is false, and switched off aspect weaving.\n", JOINPOINT_MATCHER_MATCH_JOINPOINT_KEY);
            }

            {
                this.includedClassLoaders = configView.getAsStringSet(ASPECT_WEAVER_INCLUDED_CLASS_LOADERS_KEY, Collections.emptySet());

                if(this.includedClassLoaders.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            includedClassLoaders.size(), ASPECT_WEAVER_INCLUDED_CLASS_LOADERS_KEY, 
                            includedClassLoaders.stream().collect( Collectors.joining("\n  ") ) );
            }

            {
                Set<String> excludedClassLoaders = new LinkedHashSet<>();
                excludedClassLoaders.addAll(
                        configView.getAsStringList("aop.joinpointMatcher.builtinExcludedClassLoaders", Collections.emptyList()) );
                excludedClassLoaders.addAll(
                        configView.getAsStringList(ASPECT_WEAVER_EXCLUDED_CLASS_LOADERS_KEY, Collections.emptyList()) );
                this.excludedClassLoaders = excludedClassLoaders;

                if(this.excludedClassLoaders.size() > 0) 
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            excludedClassLoaders.size(), ASPECT_WEAVER_EXCLUDED_CLASS_LOADERS_KEY, 
                            excludedClassLoaders.stream().collect( Collectors.joining("\n  ") ) );
            }

            {
                this.includedTypePatterns = configView.getAsStringSet(ASPECT_WEAVER_INCLUDED_TYPE_PATTERNS_KEY, Collections.emptySet());

                if(includedTypePatterns.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            includedTypePatterns.size(), ASPECT_WEAVER_INCLUDED_TYPE_PATTERNS_KEY,
                            includedTypePatterns.stream().collect( Collectors.joining("\n  ") ) );
            }

            {
                Set<String> excludedTypePatterns = new LinkedHashSet<>();
                excludedTypePatterns.addAll(
                        configView.getAsStringList("aop.joinpointMatcher.builtinExcludedTypePatterns", Collections.emptyList()) );
                excludedTypePatterns.addAll(
                        configView.getAsStringList(ASPECT_WEAVER_EXCLUDED_TYPE_PATTERNS_KEY, Collections.emptyList()) );
                this.excludedTypePatterns = excludedTypePatterns;

                if(this.excludedTypePatterns.size() > 0) 
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            excludedTypePatterns.size(), ASPECT_WEAVER_EXCLUDED_TYPE_PATTERNS_KEY,
                            excludedTypePatterns.stream().collect( Collectors.joining("\n  ") ) );
            }

            {
                this.includedAspectApps = configView.getAsStringSet(ASPECT_WEAVER_INCLUDED_ASPECT_APPS_KEY, Collections.emptySet());

                if(includedAspectApps.size() > 0) 
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n  {} \n", 
                            includedAspectApps.size(), ASPECT_WEAVER_INCLUDED_ASPECT_APPS_KEY,
                            includedAspectApps.stream().collect( Collectors.joining("\n  ") ) );

                this.excludedAspectApps = configView.getAsStringSet(ASPECT_WEAVER_EXCLUDED_ASPECT_APPS_KEY, Collections.emptySet());

                if(excludedAspectApps.size() > 0) 
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n  {} \n", 
                            excludedAspectApps.size(), ASPECT_WEAVER_EXCLUDED_ASPECT_APPS_KEY,
                            excludedAspectApps.stream().collect( Collectors.joining("\n  ") ) );
            }

            {
                this.includedAspects = configView.getAsStringSet(ASPECT_WEAVER_INCLUDED_ASPECTS_KEY, Collections.emptySet());

                if(includedAspects.size() > 0)
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n  {} \n", 
                            includedAspects.size(), ASPECT_WEAVER_INCLUDED_ASPECTS_KEY,
                            includedAspects.stream().collect( Collectors.joining("\n  ") ) );

                this.excludedAspects = configView.getAsStringSet(ASPECT_WEAVER_EXCLUDED_ASPECTS_KEY, Collections.emptySet());

                if(excludedAspects.size() > 0) 
                    LOGGER.warn("WARNING! Loaded {} rules from '{}' setting. \n  {} \n", 
                            excludedAspects.size(), ASPECT_WEAVER_EXCLUDED_ASPECTS_KEY, 
                            excludedAspects.stream().collect( Collectors.joining("\n  ") ) );
            }

            {
                Set<String> classLoaders = new HashSet<>();
                classLoaders.addAll( 
                        configView.getAsStringSet(ASPECT_WEAVER_GLOBAL_MATCHING_CLASS_LOADERS_KEY, Collections.emptySet()) );
                this.globalMatchingClassLoader = classLoaders;
            }
        }

        // load joinpoint transformer settings
        {
            String settingkey = "aop.joinpointTransformer.classInitializerAdvice";
            this.classInitializerAdvice = configView.<Class<?>>getValue(settingkey, ClassInitializerAdvice.class, 
                    new ToClass(settingkey, this.getAgentClassLoader()));

            settingkey = "aop.joinpointTransformer.classMethodAdvice";
            this.classMethodAdvice = configView.<Class<?>>getValue(settingkey, ClassMethodAdvice.class,
                    new ToClass(settingkey, this.getAgentClassLoader()));

            settingkey = "aop.joinpointTransformer.instanceConstructorAdvice";
            this.instanceConstructorAdvice = configView.<Class<?>>getValue(settingkey, InstanceConstructorAdvice.class,
                    new ToClass(settingkey, this.getAgentClassLoader()));

            settingkey = "aop.joinpointTransformer.instanceMethodAdvice";
            this.instanceMethodAdvice = configView.<Class<?>>getValue(settingkey, InstanceMethodAdvice.class,
                    new ToClass(settingkey, this.getAgentClassLoader()));

            this.asmAutoCompute = configView.getAsBoolean("aop.joinpointTransformer.asmAutoCompute", false);

            String dumpByteCodeKey = "aop.joinpointTransformer.dumpByteCode";
            if(diagnoticLevel.isDebugEnabled()) {
                builtinSettings.put(dumpByteCodeKey, "true");
            }

            this.dumpByteCode = configView.getAsBoolean(dumpByteCodeKey, false);
            this.byteCodeDumpPath = configView.getAsString("aop.joinpointTransformer.byteCodeDumpPath");
        }


        {
            this.processInParallel = configView.getAsBoolean("aop.globalTaskExecutor.parallel", false);
        }

        // load aspectapp settings
        {
            this.aspectAppsLocation = configView.getAsString("aop.agent.aspectAppsLocation");

            this.scanGeminiLibs = configView.getAsBoolean("aop.aspectapp.scanGeminiLibs", false);

            this.scanClassesFolder = configView.getAsBoolean("aop.aspectapp.scanClassesFolder", false);
            if(scanClassesFolder == true) {
                this.parentFirstClassPrefixes.addAll(CONDITIONAL_BUILTIN_PARENT_FIRST_CLASS_PREFIXES);
            }

            this.scannedClassFolders = configView.getAsStringSet("aop.aspectapp.scannedclassesFolders", Collections.emptySet());
        }

        {
            this.shareAspectClassLoader = configView.getAsBoolean("aop.aspectapp.shareAspectClassLoader", false);
            this.conflictJoinpointClassLoaders = parseConflictJoinpointClassLoaders(
                    configView.getAsString("aop.aspectapp.conflictJoinpointClassLoaders", "") );
        }

        // load weaver installer settings
        {
            String strategy = configView.getAsString("aop.weaver.redefinitionStrategy", "").toUpperCase();

            try {
                this.redefinitionStrategy = RedefinitionStrategy.valueOf(strategy);
            } catch(Exception e) {
                LOGGER.warn("Ignored illegal setting '{}' and use default RedefinitionStrategy '{}'. \n", strategy, RedefinitionStrategy.RETRANSFORMATION);
                this.redefinitionStrategy = RedefinitionStrategy.RETRANSFORMATION;
            }
        }
    }


    public List<Set<String>> parseConflictJoinpointClassLoaders(String conflictJoinpointClassLoadersStr) {
        if(StringUtils.hasText(conflictJoinpointClassLoadersStr) == false)
            return Collections.emptyList();

        StringTokenizer groupSt = new StringTokenizer(conflictJoinpointClassLoadersStr, ";");

        List<Set<String>> groupList = new ArrayList<>(groupSt.countTokens());
        while(groupSt.hasMoreTokens()) {
            String classLoadersStr = groupSt.nextToken().trim();
            StringTokenizer classLoaderNameSt = new StringTokenizer(classLoadersStr, ",");

            Set<String> classLoaderNames = new LinkedHashSet<>(classLoaderNameSt.countTokens());
            groupList.add(classLoaderNames);
            while(classLoaderNameSt.hasMoreTokens()) {
                String classLoaderName = classLoaderNameSt.nextToken().trim();

                if(StringUtils.hasText(classLoadersStr))
                    classLoaderNames.add(classLoaderName);
            }
        }

        return groupList;
    }

    private Default createTypePoolFactory() {
        return new TypePoolFactory.Default(
                ClassFileLocator.NoOp.INSTANCE, 
                LocationStrategy.ForClassLoader.WEAK, 
                PoolStrategy.Default.FAST, 
                ClassFileBufferStrategy.Default.RETAINING);
    }

    private TypeWorldFactory createTypeWorldFactory(TypePoolFactory typePoolFactory, ConfigView configView) {
        String mode = configView.getAsString("aop.typeWorldFactory.workMode").toUpperCase();
        TypeWorldFactory.WorkMode workMode = TypeWorldFactory.WorkMode.PROTOTYPE;
        try {
            workMode = TypeWorldFactory.WorkMode.valueOf(mode);
        } catch(Exception e) {
            LOGGER.warn("Ignored illegal setting '{}', and useTypeWorldFactory.WorkMode.PROTOTYPE. \n", mode);
        }

        if(workMode == TypeWorldFactory.WorkMode.PROTOTYPE)
            return new TypeWorldFactory.Prototype();
        else if(workMode == TypeWorldFactory.WorkMode.SINGLETON)
            return new TypeWorldFactory.Singleton();
        else 
            // impossible
            return null;
    }

    private ClassScanner createClassScanner(AgentClassLoader agentClassLoader, Map<String, AspectClassLoader> aspectClassLoaderMap, 
            ConfigView configView, BootstraperMetrics bootstraperMetrics) {
        long startedAt = System.nanoTime();

        Set<String> acceptJarPatterns= new HashSet<>();
        {
            acceptJarPatterns.addAll(
                    configView.getAsStringList("aop.classScanner.builtinAcceptJarPatterns", Collections.emptyList()) );
            acceptJarPatterns.addAll(
                    configView.getAsStringList("aop.classScanner.acceptJarPatterns", Collections.emptyList()) );
        }

        Set<String> acceptPackages= new HashSet<>();
        {
            acceptPackages.addAll(
                    configView.getAsStringList("aop.classScanner.builtinAcceptPackages", Collections.emptyList()) );
            acceptPackages.addAll(
                    configView.getAsStringList("aop.classScanner.acceptPackages", Collections.emptyList()) );
        }

        String enableVerboseKey = "aop.classScanner.enableVerbose";
        if(diagnoticLevel.isDebugEnabled()) {
            builtinSettings.put(enableVerboseKey, "true");
        }

        ClassScanner classScanner = new ClassScanner.Builder()
                .scannedClassLoaders( aspectClassLoaderMap.values().toArray(new ClassLoader[] {}) )
                .enableVerbose( configView.getAsBoolean(enableVerboseKey, false) )
                .acceptJarPatterns( acceptJarPatterns )
                .acceptPackages( acceptPackages )
                .workThreads( configView.getAsInteger("aop.classScanner.workThreads", ClassScanner.NO_WORK_THREAD) )
                .classpathElementUrls( getAgentClassLoader().getURLs() )
                .diagnosticLevel(diagnoticLevel)
                .build();

        bootstraperMetrics.setClassScannerCreationTime(System.nanoTime() - startedAt);
        return classScanner;
    }

    private ObjectFactory createObjectFactory() {
        return new ObjectFactory.Builder()
                .classLoader(agentClassLoader)
                .classScanner(classScanner)
                .build(true);
    }

    private Map<String, AspectContext> createAspectContextMap(Map<String, Resources> appResourcesMap, 
            Map<String, AspectClassLoader> aspectClassLoaderMap) {
        long startedAt = System.nanoTime();
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating AspectContexts.");
        }
        ConcurrentMap<String, Double> creationTime = new ConcurrentHashMap<>();

        try {
            return this.getGlobalTaskExecutor().executeTasks(
                    appResourcesMap.entrySet().stream()
                        .collect( Collectors.toList() ),
                    appResources -> {
                        List<Entry<String, AspectContext>> appAspectContexts = new ArrayList<>(1);
                        for(Entry<String, Resources> entry : appResources) {
                            long startedAt2 = System.nanoTime();

                            String appName = entry.getKey();
                            if(LOGGER.isDebugEnabled())
                                LOGGER.debug("Creating AspectContext '{}'", appName);

                            try {
                                Entry<String, AspectContext> aspectContext = new SimpleEntry<>(appName, 
                                        new AspectContext(AopContext.this, appName, entry.getValue(), aspectClassLoaderMap.get(appName)) );
                                appAspectContexts.add(aspectContext);
                            } finally {
                                double time = (System.nanoTime() - startedAt2) / 1e9;
    
                                if(LOGGER.isDebugEnabled())
                                    LOGGER.debug("Took '{}' seconds to create AspectContext '{}'", time, appName);
                                creationTime.put(appName, time);
                            }
                        }
                        return appAspectContexts;
                    },
                    1
            ).stream()
           .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );
        } finally {
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug("Took '{}' seconds to create AspectContextsm, including", (System.nanoTime() - startedAt) / 1e9);
                for(Entry<String, Double> entry : creationTime.entrySet()) {
                    LOGGER.debug("  '{}': '{}'", entry.getKey(), entry.getValue());
                }
            }
        }
    }


    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public AgentClassLoader getAgentClassLoader() {
        return this.agentClassLoader;
    }

    public LoggingSystem getLoggingSystem() {
        return loggingSystem;
    }


    public ConfigView getConfigView() {
        return configView;
    }

    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }

    public AopMetrics getAopMetrics() {
        return aopMetrics;
    }


    public DiagnosticLevel getDiagnosticLevel() {
        return diagnoticLevel;
    }

    public boolean isDiagnosticClass(String typeName) {
        return diagnosticClasses.contains(typeName);
    }

    public boolean isDiagnosticClass(List<Class<?>> types) {
        if(CollectionUtils.isEmpty(types) == true)
            return false;

        for(Class<?> clazz : types) {
            if(diagnosticClasses.contains(clazz.getName()))
                return true;
        }
        return false;
    }

    public boolean isDefaultProfile() {
        return defaultProfile;
    }

    public String getActiveProfile() {
        return activeProfile;
    }


    public Set<String> getBootstrapClasses() {
        return Collections.unmodifiableSet( bootstrapClasses );
    }

    public Set<String> getParentFirstClassPrefixes() {
        return Collections.unmodifiableSet( parentFirstClassPrefixes );
    }

    public Set<String> getParentFirstResourcePrefixes() {
        return Collections.unmodifiableSet( parentFirstResourcePrefixes );
    }


    public boolean isMatchJoinpoint() {
        return matchJoinpoint;
    }

    public Set<String> getIncludedClassLoaders() {
        return Collections.unmodifiableSet( includedClassLoaders );
    }

    public Set<String> getExcludedClassLoaders() {
        return Collections.unmodifiableSet( excludedClassLoaders );
    }

    public Set<String> getIncludedTypePatterns() {
        return Collections.unmodifiableSet( includedTypePatterns );
    }

    public Set<String> getExcludedTypePatterns() {
        return Collections.unmodifiableSet( excludedTypePatterns );
    }

    public Set<String> getIncludedAspectApps() {
        return Collections.unmodifiableSet( includedAspectApps );
    }

    public Set<String> getExcludedAspectApps() {
        return Collections.unmodifiableSet( excludedAspectApps );
    }

    public Set<String> getIncludedAspects() {
        return Collections.unmodifiableSet( includedAspects );
    }

    public Set<String> getExcludedAspects() {
        return Collections.unmodifiableSet( excludedAspects );
    }

    public Set<String> getGlobalMacthingClassLoaders() {
        return Collections.unmodifiableSet( globalMatchingClassLoader );
    }


    public Class<?> getClassInitializerAdvice() {
        return classInitializerAdvice;
    }

    public Class<?> getClassMethodAdvice() {
        return classMethodAdvice;
    }

    public Class<?> getInstanceConstructorAdvice() {
        return instanceConstructorAdvice;
    }

    public Class<?> getInstanceMethodAdvice() {
        return instanceMethodAdvice;
    }

    public boolean isASMAutoCompute() {
        return asmAutoCompute;
    }

    public boolean isDumpByteCode() {
        return dumpByteCode;
    }

    public String getByteCodeDumpPath() {
        return byteCodeDumpPath;
    }


    public String getAspectAppsLocation() {
        return aspectAppsLocation;
    }

    public boolean isScanGeminiLibs() {
        return scanGeminiLibs;
    }

    public boolean isScanClassesFolder() {
        return scanClassesFolder;
    }

    public Set<String> getScannedClassFolders() {
        return Collections.unmodifiableSet( scannedClassFolders );
    }


    public boolean isShareAspectClassLoader() {
        return shareAspectClassLoader;
    }

    public List<Set<String>> getConflictJoinpointClassLoaders() {
        return Collections.unmodifiableList( conflictJoinpointClassLoaders );
    }


    public RedefinitionStrategy getRedefinitionStrategy() {
        return redefinitionStrategy;
    }


    public TypePoolFactory getTypePoolFactory() {
        return typePoolFactory;
    }

    public TypeWorldFactory getTypeWorldFactory() {
        return typeWorldFactory;
    }

    public TaskExecutor getGlobalTaskExecutor() {
        return globalTaskExecutor;
    }

    public AspectContextHelper getAspectContextHelper() {
        return aspectContextHelper;
    }

    public ClassScanner getClassScanner() {
        return classScanner;
    }

    public ObjectFactory getObjectFactory() {
        return this.objectFactory;
    }


    public Map<String, AspectContext> getAspectContextMap() {
        return this.aspectContextMap;
    }


    public ElementMatcher<String> getExcludedClassLoaderMatcher() {
        return excludedClassLoaderMatcher;
    }

    public boolean isExcludedClassLoader(String joinpointClassLoaderName) {
        return this.excludedClassLoaderMatcher.matches(joinpointClassLoaderName) == true;
    }


    @Override
    public void close() {
        this.globalTaskExecutor.shutdown();

        for(Closeable closeable : this.aspectContextMap.values()) {
            closeable.close();
        }

        this.objectFactory.close();
    }

    private static class ToClass implements Converter<Class<?>> {

        private final String settingKey;
        private final ClassLoader classLoader;


        public ToClass(String settingKey, ClassLoader classLoader) {
            this.settingKey = settingKey;
            this.classLoader = classLoader;
        }

        @Override
        public Class<?> convert(Object object) {
            String className = object.toString();
            try {
                return Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException e) {
                throw new AopException("Setting '" + settingKey + "' referring to unexisting class " + className, e);
            }
        }
    }
}
