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
package io.gemini.aop.weaver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
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
import io.gemini.core.config.ConfigView;
import io.gemini.core.util.StringUtils;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;


/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
class WeaverContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeaverContext.class);

    private static final String WEAVER_JOINPOINT_MATCHED_KEY = "aop.weaver.joinpointMatched";

    private static final String WEAVER_CLASSLOADER_TYPE_EXPRESSIONS_KEY = "aop.weaver.classLoaderTypeExpressions";
    private static final String WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRESSIONS = "aop.weaver.defaultExcludedClassLoaderExpressions";
    private static final String WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRESSIONS = "aop.weaver.defaultExcludedTypeExpressions";


    private final AopContext aopContext;

    // weaver settings
    private boolean joinpointMatched;

    private Map<ElementMatcher<ClassLoader>, ElementMatcher<String>> classLoaderTypeMatchers;


    private Class<?> classInitializerAdvice;
    private Class<?> classMethodAdvice;
    private Class<?> instanceConstructorAdvice;
    private Class<?> instanceMethodAdvice;


    // weaver installer settings
    private RedefinitionStrategy redefinitionStrategy;


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

    private void loadSettings(AopContext aopContext) {
        ConfigView configView = aopContext.getConfigView();

        ClassInfoList noMatchingClassInfoList = aopContext.getClassScanner().getClassesWithAnnotation(NoMatching.class.getName());

        // load joinpoint matcher settings
        {
            this.joinpointMatched = configView.getAsBoolean(WEAVER_JOINPOINT_MATCHED_KEY, true);
            if (LOGGER.isWarnEnabled() && joinpointMatched == false)
                LOGGER.warn("WARNING! Setting '{}' is false, and switched off aop weaving.\n", WEAVER_JOINPOINT_MATCHED_KEY);
        }

        {
            Set<String> classLoaderTypeExpressions = 
                    configView.getAsStringSet(WEAVER_CLASSLOADER_TYPE_EXPRESSIONS_KEY, Collections.emptySet());
            Map<String, String> classLoaderTypeExpressionPairs = new LinkedHashMap<>();
            for (String classLoaderTypeExpression : classLoaderTypeExpressions) {
                int pos = classLoaderTypeExpression.indexOf(":");
                String classLoaderExpression = pos != -1 ? classLoaderTypeExpression.substring(0, pos) : classLoaderTypeExpression;
                String typeExpression = pos != -1 ? classLoaderTypeExpression.substring(pos+1) : "*";

                classLoaderTypeExpressionPairs.put(classLoaderExpression.trim(), typeExpression.trim());
            }
            if (LOGGER.isWarnEnabled() && classLoaderTypeExpressionPairs.size() > 0)
                LOGGER.warn("Loaded {} rules from '{}' setting. \n"
                        + "  {} \n", 
                        classLoaderTypeExpressionPairs.size(), WEAVER_CLASSLOADER_TYPE_EXPRESSIONS_KEY, 
                        StringUtils.join(classLoaderTypeExpressionPairs.entrySet(), entry -> entry.getKey() + ": " + entry.getValue(), "\n  ")
                );

            Map<ElementMatcher<ClassLoader>, ElementMatcher<String>> classLoaderTypeMatchers = 
                    new LinkedHashMap<>(classLoaderTypeExpressions.size() + 1);
            for (Entry<String, String> entry : classLoaderTypeExpressionPairs.entrySet()) {
                ElementMatcher<ClassLoader> classLoaderMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                        WEAVER_CLASSLOADER_TYPE_EXPRESSIONS_KEY, 
                        Collections.singleton(entry.getKey()), 
                        ElementMatchers.none()
                );

                ElementMatcher<String> typeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                        WEAVER_CLASSLOADER_TYPE_EXPRESSIONS_KEY, 
                        Collections.singleton(entry.getValue().trim()), 
                        ElementMatchers.none()
                );

                classLoaderTypeMatchers.put(classLoaderMatcher, typeMatcher);
            }

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

            Set<String> defaultExcludedTypeExpressions = new LinkedHashSet<>();
            defaultExcludedTypeExpressions.addAll(
                    configView.getAsStringList(WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRESSIONS, Collections.emptyList()) );
            defaultExcludedTypeExpressions.addAll(
                    noMatchingClassInfoList.filter( this::isClass ).getNames() );

            if (LOGGER.isInfoEnabled())
                LOGGER.info("Loaded {} rules from '{}' setting. \n"
                        + "  {} \n", 
                        defaultExcludedTypeExpressions.size(), WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRESSIONS, 
                        StringUtils.join(defaultExcludedTypeExpressions, "\n  ")
                );

            ElementMatcher<String> typeMatcher = ElementMatchers.not(
                    ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                            WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRESSIONS, 
                            defaultExcludedTypeExpressions, 
                            ElementMatchers.none()
                    ) 
            );

            classLoaderTypeMatchers.put(classLoaderMatcher, typeMatcher);

            this.classLoaderTypeMatchers = classLoaderTypeMatchers;
        }

        // load joinpoint transformer settings
        {
            String settingkey = "aop.weaver.classInitializerAdvice";
            this.classInitializerAdvice = configView.getAsClass(settingkey, ClassInitializerAdvice.class);

            settingkey = "aop.weaver.classMethodAdvice";
            this.classMethodAdvice = configView.getAsClass(settingkey, ClassMethodAdvice.class);

            settingkey = "aop.weaver.instanceConstructorAdvice";
            this.instanceConstructorAdvice = configView.getAsClass(settingkey, InstanceConstructorAdvice.class);

            settingkey = "aop.weaver.instanceMethodAdvice";
            this.instanceMethodAdvice = configView.getAsClass(settingkey, InstanceMethodAdvice.class);
        }

        // load weaver installer settings
        {
            String strategy = configView.getAsString("aop.weaver.redefinitionStrategy", "").toUpperCase();

            try {
                this.redefinitionStrategy = RedefinitionStrategy.valueOf(strategy);
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored illegal setting '{}' and use default RedefinitionStrategy '{}'. \n", 
                            strategy, RedefinitionStrategy.RETRANSFORMATION);

                this.redefinitionStrategy = RedefinitionStrategy.RETRANSFORMATION;
            }
        }
    }

    private boolean isClassLoader(ClassInfo classInfo) {
        return Boolean.TRUE.equals(
                classInfo.getAnnotationInfo(NoMatching.class).getParameterValues().get("classLoader").getValue() );
    }

    private boolean isClass(ClassInfo classInfo) {
        return !isClassLoader(classInfo);
    }


    public AopContext getAopContext() {
        return aopContext;
    }


    public boolean isJoinpointMatched() {
        return joinpointMatched;
    }

    public Map<ElementMatcher<ClassLoader>, ElementMatcher<String>> getClassLoaderTypeMatchers() {
        return classLoaderTypeMatchers;
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


    public RedefinitionStrategy getRedefinitionStrategy() {
        return redefinitionStrategy;
    }
}
