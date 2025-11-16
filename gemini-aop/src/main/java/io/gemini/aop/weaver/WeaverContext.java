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
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
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

    private static final String WEAVER_MATCH_JOINPOINT_KEY = "aop.weaver.matchJoinpoint";

    private static final String WEAVER_CLASS_LOADER_EXPRESSIONS_KEY = "aop.weaver.classLoaderExpressions";
    private static final String WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRESSIONS = "aop.weaver.defaultExcludedClassLoaderExpressions";

    private static final String WEAVER_TYPE_EXPRESSIONS_KEY = "aop.weaver.typeExpressions";
    private static final String WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRESSIONS = "aop.weaver.defaultExcludedTypeExpressions";


    private final AopContext aopContext;

    // weaver settings
    private boolean matchJoinpoint;

    private ElementMatcher<ClassLoader> classLoaderMatcher;
    private ElementMatcher<String> typeMatcher;


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


        if (aopContext.getDiagnosticLevel().isSimpleEnabled()) 
            LOGGER.info("$Took '{}' seconds to create WeaverContext. ", 
                    (System.nanoTime() - startedAt) / 1e9);
    }

    private void loadSettings(AopContext aopContext) {
        ConfigView configView = aopContext.getConfigView();

        ClassInfoList noMatchingClassInfoList = aopContext.getClassScanner().getClassesWithAnnotation(NoMatching.class.getName());

        // load joinpoint matcher settings
        {
            this.matchJoinpoint = configView.getAsBoolean(WEAVER_MATCH_JOINPOINT_KEY, true);
            if (matchJoinpoint == false) {
                LOGGER.warn("WARNING! Setting '{}' is false, and switched off aop weaving.\n", WEAVER_MATCH_JOINPOINT_KEY);
            }

            {
                Set<String> classLoaderExpressions = configView.getAsStringSet(WEAVER_CLASS_LOADER_EXPRESSIONS_KEY, new LinkedHashSet<>());
                if (classLoaderExpressions.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n"
                            + "  {} \n", 
                            classLoaderExpressions.size(), WEAVER_CLASS_LOADER_EXPRESSIONS_KEY, 
                            StringUtils.join(classLoaderExpressions, "\n  ")
                    );


                Set<String> defaultExcludedClassLoaderExpressions = new LinkedHashSet<>();
                defaultExcludedClassLoaderExpressions.addAll(
                        configView.getAsStringSet(WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRESSIONS, Collections.emptySet()) );
                defaultExcludedClassLoaderExpressions.addAll(
                        noMatchingClassInfoList.filter( this::isClassLoader ).getNames() );

                LOGGER.info("Loaded {} rules from '{}' setting. \n"
                        + "  {} \n", 
                        defaultExcludedClassLoaderExpressions.size(), WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRESSIONS, 
                        StringUtils.join(defaultExcludedClassLoaderExpressions, "\n  ")
                );


                this.classLoaderMatcher = ElementMatchers.not(
                        ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                                WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRESSIONS, defaultExcludedClassLoaderExpressions, ElementMatchers.none() ) );
                if (classLoaderExpressions.size() > 0)
                    this.classLoaderMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                            WEAVER_CLASS_LOADER_EXPRESSIONS_KEY, classLoaderExpressions, ElementMatchers.any() )
                        .and( this.classLoaderMatcher );
            }


            {
                Set<String> typeExpressions = configView.getAsStringSet(WEAVER_TYPE_EXPRESSIONS_KEY, Collections.emptySet());
                if (typeExpressions.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n"
                            + "  {} \n", 
                            typeExpressions.size(), WEAVER_TYPE_EXPRESSIONS_KEY,
                            StringUtils.join(typeExpressions, "\n  ") 
                    );


                Set<String> defaultExcludedTypeExpressions = new LinkedHashSet<>();
                defaultExcludedTypeExpressions.addAll(
                        configView.getAsStringList(WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRESSIONS, Collections.emptyList()) );
                defaultExcludedTypeExpressions.addAll(
                        noMatchingClassInfoList.filter( this::isClass ).getNames() );
                defaultExcludedTypeExpressions.addAll( aopContext.getBootstrapClassNameMapping().values() );

                LOGGER.info("Loaded {} rules from '{}' setting. \n"
                        + "  {} \n", 
                        defaultExcludedTypeExpressions.size(), WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRESSIONS,
                        StringUtils.join(defaultExcludedTypeExpressions, "\n  ")
                );


                this.typeMatcher = ElementMatchers.not(
                        ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                                WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRESSIONS, defaultExcludedTypeExpressions, ElementMatchers.none()) );
                if (typeExpressions.size() > 0)
                    this.typeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                            WEAVER_TYPE_EXPRESSIONS_KEY, typeExpressions, ElementMatchers.any())
                        .and( typeMatcher );
            }
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
                LOGGER.warn("Ignored illegal setting '{}' and use default RedefinitionStrategy '{}'. \n", strategy, RedefinitionStrategy.RETRANSFORMATION);
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


    public boolean isMatchJoinpoint() {
        return matchJoinpoint;
    }


    public boolean acceptClassLoader(ClassLoader classLoader) {
        return this.classLoaderMatcher.matches(classLoader);
    }

    public boolean acceptType(String typeName) {
        return this.typeMatcher.matches(typeName);
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
