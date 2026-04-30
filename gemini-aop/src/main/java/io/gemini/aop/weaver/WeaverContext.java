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
package io.gemini.aop.weaver;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.aop.weaver.advice.ClassInitializerAdvice;
import io.gemini.aop.weaver.advice.ClassMethodAdvice;
import io.gemini.aop.weaver.advice.InstanceConstructorAdvice;
import io.gemini.aop.weaver.advice.InstanceMethodAdvice;
import io.gemini.api.annotation.NoMatching;
import io.gemini.api.classloader.BaseClassLoader;
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.StringUtils;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;


/**
 * Holds weaver-specific configuration derived from {@link AopContext} settings.
 * <p>
 * Manages:
 * <ul>
 *   <li>Whether weaving is enabled ({@code aop.weaver.enableWeaver})</li>
 *   <li>ClassLoader acceptance rules (which class loaders are eligible for instrumentation)</li>
 *   <li>Framework Byte Buddy advice classes for each joinpoint type (class initializer, static method,
 *       constructor, instance method)</li>
 *   <li>ByteBuddy redefinition strategy and native method prefix</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public class WeaverContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeaverContext.class);

    private static final String ENABLE_WEAVER_KEY = "aop.weaver.enableWeaver";

    private static final String WEAVER_CLASSLOADER_EXPRESSIONS_KEY = "aop.weaver.classLoaderExpressions";
    private static final String WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRESSIONS = "aop.weaver.defaultExcludedClassLoaderExpressions";


    private final AopContext aopContext;

    // weaver settings
    private boolean enableWeaver;

    private ElementMatcher<ClassLoader> classLoaderMatcher;

    private Class<?> classInitializerAdvice;
    private Class<?> classMethodAdvice;
    private Class<?> instanceConstructorAdvice;
    private Class<?> instanceMethodAdvice;

    // weaver installer settings
    private RedefinitionStrategy redefinitionStrategy;

    private String nativeMethodPrefix;


    public WeaverContext(AopContext aopContext) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating WeaverContext, ");


        this.aopContext = aopContext;

        // 1.load weaver settings
        this.loadSettings(aopContext);


        if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled()) 
            LOGGER.info("$Took '{}' seconds to create WeaverContext. ", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME);
    }

    /**
     * Loads all weaver settings from the {@link AopContext} configuration view,
     * including class loader matchers, advice class overrides, and redefinition strategy.
     *
     * @param aopContext the central AOP context providing configuration
     */
    private void loadSettings(AopContext aopContext) {
        ConfigView configView = aopContext.getConfigView();

        ClassInfoList noMatchingClassInfoList = aopContext.getClassScanner().getClassesWithAnnotation(NoMatching.class.getName());

        // load joinpoint matcher settings
        {
            this.enableWeaver = configView.getAsBoolean(ENABLE_WEAVER_KEY, true);
            if (LOGGER.isWarnEnabled() && enableWeaver == false)
                LOGGER.warn("WARNING! Setting '{}' is false, and switched off aop weaving.\n", ENABLE_WEAVER_KEY);
        }

        {
            Set<String> classLoaderExpressions = 
                    configView.getAsStringSet(WEAVER_CLASSLOADER_EXPRESSIONS_KEY, Collections.emptySet());

            if (LOGGER.isWarnEnabled() && classLoaderExpressions.size() > 0)
                LOGGER.warn("Loaded {} rules from '{}' setting. \n"
                        + "  {} \n", 
                        classLoaderExpressions.size(), WEAVER_CLASSLOADER_EXPRESSIONS_KEY, 
                        StringUtils.join(classLoaderExpressions, "\n  ")
                );


            Set<String> defaultExcludedClassLoaderExpressions = new LinkedHashSet<>();
            defaultExcludedClassLoaderExpressions.addAll(
                    configView.getAsStringSet(WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRESSIONS, Collections.emptySet()) );
            defaultExcludedClassLoaderExpressions.addAll(
                    noMatchingClassInfoList.filter( this::isClassLoader ).getNames() );

            if (LOGGER.isInfoEnabled())
                LOGGER.info("Loaded {} rules from '{}' setting. \n"
                        + "  {} \n", 
                        defaultExcludedClassLoaderExpressions.size(), WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRESSIONS, 
                        StringUtils.join(defaultExcludedClassLoaderExpressions, "\n  ")
                );

            ElementMatcher<ClassLoader> classLoaderMatcher = ElementMatchers.not(
                    ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                            WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRESSIONS, 
                            defaultExcludedClassLoaderExpressions, 
                            ElementMatchers.none() 
                    ) 
            );
            if (classLoaderExpressions.size() > 0)
                classLoaderMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                        WEAVER_CLASSLOADER_EXPRESSIONS_KEY, 
                        classLoaderExpressions, 
                        ElementMatchers.none() 
                )
                .and(classLoaderMatcher);

            this.classLoaderMatcher = classLoaderMatcher;
        }

        // load joinpoint transformer settings
        {
            this.classInitializerAdvice = configView.getAsClass(
                    "aop.weaver.classInitializerAdvice", ClassInitializerAdvice.class);
            this.classMethodAdvice = configView.getAsClass(
                    "aop.weaver.classMethodAdvice", ClassMethodAdvice.class);
            this.instanceConstructorAdvice = configView.getAsClass(
                    "aop.weaver.instanceConstructorAdvice", InstanceConstructorAdvice.class);
            this.instanceMethodAdvice = configView.getAsClass(
                    "aop.weaver.instanceMethodAdvice", InstanceMethodAdvice.class);
        }

        // load weaver installer settings
        {
            String strategy = configView.getAsString("aop.weaver.redefinitionStrategy", "").toUpperCase(Locale.ENGLISH);

            try {
                this.redefinitionStrategy = RedefinitionStrategy.valueOf(strategy);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored illegal setting '{}' and use default RedefinitionStrategy '{}'. \n", 
                            strategy, RedefinitionStrategy.RETRANSFORMATION);

                this.redefinitionStrategy = RedefinitionStrategy.RETRANSFORMATION;
            }

            this.nativeMethodPrefix = configView.getAsString("aop.weaver.nativeMethodPrefix", "$$original$$_");
        }
    }

    private boolean isClassLoader(ClassInfo classInfo) {
        return Boolean.TRUE.equals(
                classInfo.getAnnotationInfo(NoMatching.class).getParameterValues().get("classLoader").getValue() );
    }


    /**
     * Returns the central {@link AopContext}.
     *
     * @return the AOP context
     */
    public AopContext getAopContext() {
        return aopContext;
    }


    /**
     * Returns whether the weaver is enabled. When {@code false}, no types will be instrumented.
     *
     * @return {@code true} if weaving is enabled
     */
    public boolean isEnableWeaver() {
        return enableWeaver;
    }

    /**
     * Returns {@code true} if the given target class loader is eligible for instrumentation.
     * {@link BaseClassLoader} instances are always excluded.
     *
     * @param targetClassLoader the class loader to test
     * @return {@code true} if this class loader should be instrumented
     */
    public boolean acceptTargetClassLoader(ClassLoader targetClassLoader) {
        return targetClassLoader instanceof BaseClassLoader 
                ? false : classLoaderMatcher.matches(targetClassLoader);
    }

    /**
     * Returns the framework {@code @Advice} class to use for the given target method,
     * selecting among class initializer, static method, constructor, and instance method advice.
     *
     * @param targetMethod the method being instrumented
     * @return the framework advice class to apply
     */
    public Class<?> getFrameworkAdviceClass(MethodDescription targetMethod) {
        if (targetMethod.isStatic()) {
            if (targetMethod.isTypeInitializer()) {
                return classInitializerAdvice;
            } else { 
                return classMethodAdvice;
            }
        } else {
            if (targetMethod.isConstructor()) {
                return instanceConstructorAdvice;
            } else {
                return instanceMethodAdvice;
            }
        }
    }

    /**
     * Returns the ByteBuddy {@link RedefinitionStrategy} used when retransforming already-loaded types.
     *
     * @return the redefinition strategy
     */
    public RedefinitionStrategy getRedefinitionStrategy() {
        return redefinitionStrategy;
    }

    /**
     * Returns the prefix prepended to native method names when they are renamed to allow interception.
     *
     * @return the native method prefix string
     */
    public String getNativeMethodPrefix() {
        return nativeMethodPrefix;
    }
}
