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
import io.gemini.aop.AopException;
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.aop.weaver.advice.ClassInitializerAdvice;
import io.gemini.aop.weaver.advice.ClassMethodAdvice;
import io.gemini.aop.weaver.advice.InstanceConstructorAdvice;
import io.gemini.aop.weaver.advice.InstanceMethodAdvice;
import io.gemini.api.annotation.NoMatching;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigView.Converter;
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

    private static final String WEAVER_CLASS_LOADER_EXPRS_KEY = "aop.weaver.classLoaderExprs";
    private static final String WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRS = "aop.weaver.defaultExcludedClassLoaderExprs";

    private static final String WEAVER_TYPE_EXPRS_KEY = "aop.weaver.typeExprs";
    private static final String WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRS = "aop.weaver.defaultExcludedTypeExprs";


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
        // 1.load weaver settings
        this.loadSettings(aopContext);
    }

    private void loadSettings(AopContext aopContext) {
        ConfigView configView = aopContext.getConfigView();
        AopClassLoader aopClassLoader = aopContext.getAopClassLoader();

        ClassInfoList noMatchingClassInfoList = aopContext.getClassScanner().getClassesWithAnnotation(NoMatching.class.getName());

        // load joinpoint matcher settings
        {
            this.matchJoinpoint = configView.getAsBoolean(WEAVER_MATCH_JOINPOINT_KEY, true);
            if(matchJoinpoint == false) {
                LOGGER.warn("WARNING! Setting '{}' is false, and switched off aop weaving.\n", WEAVER_MATCH_JOINPOINT_KEY);
            }

            {
                Set<String> classLoaderExprs = configView.getAsStringSet(WEAVER_CLASS_LOADER_EXPRS_KEY, new LinkedHashSet<>());
                if(classLoaderExprs.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            classLoaderExprs.size(), WEAVER_CLASS_LOADER_EXPRS_KEY, 
                            StringUtils.join(classLoaderExprs, "\n  ")
                    );


                Set<String> defaultExcludedClassLoaderExprs = new LinkedHashSet<>();
                defaultExcludedClassLoaderExprs.addAll(
                        configView.getAsStringSet(WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRS, Collections.emptySet()) );
                defaultExcludedClassLoaderExprs.addAll(
                        noMatchingClassInfoList.filter( this::isClassLoader ).getNames() );

                LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                        defaultExcludedClassLoaderExprs.size(), WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRS, 
                        StringUtils.join(defaultExcludedClassLoaderExprs, "\n  ")
                );


                this.classLoaderMatcher = ElementMatchers.not(
                        ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                                WEAVER_DEFAULT_EXCLUDED_CLASS_LOADER_EXPRS, defaultExcludedClassLoaderExprs ) );
                if(classLoaderExprs.size() > 0)
                    this.classLoaderMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                            WEAVER_CLASS_LOADER_EXPRS_KEY, classLoaderExprs )
                        .and( this.classLoaderMatcher );
            }


            {
                Set<String> typeExprs = configView.getAsStringSet(WEAVER_TYPE_EXPRS_KEY, Collections.emptySet());
                if(typeExprs.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            typeExprs.size(), WEAVER_TYPE_EXPRS_KEY,
                            StringUtils.join(typeExprs, "\n  ") 
                    );


                Set<String> defaultExcludedTypeExprs = new LinkedHashSet<>();
                defaultExcludedTypeExprs.addAll(
                        configView.getAsStringList(WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRS, Collections.emptyList()) );
                defaultExcludedTypeExprs.addAll(
                        noMatchingClassInfoList.filter( this::isClass ).getNames() );
                defaultExcludedTypeExprs.addAll( aopContext.getBootstrapClassNameMapping().values() );

                LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                        defaultExcludedTypeExprs.size(), WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRS,
                        StringUtils.join(defaultExcludedTypeExprs, "\n  ")
                );


                this.typeMatcher = ElementMatchers.not(
                        ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                                WEAVER_DEFAULT_EXCLUDED_TYPE_EXPRS, defaultExcludedTypeExprs) );
                if(typeExprs.size() > 0)
                    this.typeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                            WEAVER_TYPE_EXPRS_KEY, typeExprs)
                        .and( typeMatcher );
            }
        }

        // load joinpoint transformer settings
        {
            String settingkey = "aop.weaver.classInitializerAdvice";
            this.classInitializerAdvice = configView.<Class<?>>getValue(settingkey, ClassInitializerAdvice.class, 
                    new ToClass(settingkey, aopClassLoader));

            settingkey = "aop.weaver.classMethodAdvice";
            this.classMethodAdvice = configView.<Class<?>>getValue(settingkey, ClassMethodAdvice.class,
                    new ToClass(settingkey, aopClassLoader));

            settingkey = "aop.weaver.instanceConstructorAdvice";
            this.instanceConstructorAdvice = configView.<Class<?>>getValue(settingkey, InstanceConstructorAdvice.class,
                    new ToClass(settingkey, aopClassLoader));

            settingkey = "aop.weaver.instanceMethodAdvice";
            this.instanceMethodAdvice = configView.<Class<?>>getValue(settingkey, InstanceMethodAdvice.class,
                    new ToClass(settingkey, aopClassLoader));
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

    private boolean isClassLoader(ClassInfo classInfo) {
        return Boolean.TRUE.equals(
                classInfo.getAnnotationInfo(NoMatching.class).getParameterValues().get("classLoader").getValue() );
    }

    private boolean isClass(ClassInfo classInfo) {
        return !isClassLoader(classInfo);
    }


    public boolean isMatchJoinpoint() {
        return matchJoinpoint;
    }


    public boolean matchClassLoader(ClassLoader classLoader) {
        return this.classLoaderMatcher.matches(classLoader);
    }

    public boolean matchType(String typeName) {
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
