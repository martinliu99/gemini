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

    private static final String WEAVER_INCLUDED_CLASS_LOADER_EXPRS_KEY = "aop.weaver.includedClassLoaderExprs";
    private static final String WEAVER_EXCLUDED_CLASS_LOADER_EXPRS_KEY = "aop.weaver.excludedClassLoaderExprs";

    private static final String WEAVER_INCLUDED_TYPE_EXPRS_KEY = "aop.weaver.includedTypeExprs";
    private static final String WEAVER_EXCLUDED_TYPE_EXPRS_KEY = "aop.weaver.excludedTypeExprs";


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
                Set<String> includedClassLoaderExprs = configView.getAsStringSet(WEAVER_INCLUDED_CLASS_LOADER_EXPRS_KEY, new LinkedHashSet<>());

                if(includedClassLoaderExprs.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            includedClassLoaderExprs.size(), WEAVER_INCLUDED_CLASS_LOADER_EXPRS_KEY, 
                            StringUtils.join(includedClassLoaderExprs, "\n  ")
                    );

                ElementMatcher.Junction<ClassLoader> includedClassLoadersMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                        WEAVER_INCLUDED_CLASS_LOADER_EXPRS_KEY, includedClassLoaderExprs );


                Set<String> excludedClassLoaderExprs = new LinkedHashSet<>();
                excludedClassLoaderExprs.addAll(
                        configView.getAsStringList("aop.weaver.builtinExcludedClassLoaderExprs", Collections.emptyList()) );
                excludedClassLoaderExprs.addAll(
                        noMatchingClassInfoList.filter( this::isClassLoader ).getNames() );

                excludedClassLoaderExprs.addAll(
                        configView.getAsStringList(WEAVER_EXCLUDED_CLASS_LOADER_EXPRS_KEY, Collections.emptyList()) );

                if(excludedClassLoaderExprs.size() > 0) 
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            excludedClassLoaderExprs.size(), WEAVER_EXCLUDED_CLASS_LOADER_EXPRS_KEY, 
                            StringUtils.join(excludedClassLoaderExprs, "\n  ")
                    );

                ElementMatcher.Junction<ClassLoader> excludedClassLoadersMatcher = ElementMatcherFactory.INSTANCE.createClassLoaderMatcher(
                        WEAVER_EXCLUDED_CLASS_LOADER_EXPRS_KEY, excludedClassLoaderExprs );


                this.classLoaderMatcher = includedClassLoadersMatcher.or( ElementMatchers.not(excludedClassLoadersMatcher) );
            }


            {
                Set<String> includedTypeExprs = configView.getAsStringSet(WEAVER_INCLUDED_TYPE_EXPRS_KEY, Collections.emptySet());

                if(includedTypeExprs.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            includedTypeExprs.size(), WEAVER_INCLUDED_TYPE_EXPRS_KEY,
                            StringUtils.join(includedTypeExprs, "\n  ") 
                    );

                ElementMatcher.Junction<String> includedTypeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                        WeaverContext.WEAVER_INCLUDED_TYPE_EXPRS_KEY, includedTypeExprs);


                Set<String> excludedTypeExprs = new LinkedHashSet<>();
                excludedTypeExprs.addAll(
                        configView.getAsStringList("aop.weaver.builtinExcludedTypeExprs", Collections.emptyList()) );
                excludedTypeExprs.addAll(
                        noMatchingClassInfoList.filter( this::isClass ).getNames() );
                excludedTypeExprs.addAll( aopContext.getBootstrapClassNameMapping().keySet() );

                excludedTypeExprs.addAll(
                        configView.getAsStringList(WEAVER_EXCLUDED_TYPE_EXPRS_KEY, Collections.emptyList()) );

                if(excludedTypeExprs.size() > 0) 
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            excludedTypeExprs.size(), WEAVER_EXCLUDED_TYPE_EXPRS_KEY,
                            StringUtils.join(excludedTypeExprs, "\n  ")
                    );

                ElementMatcher.Junction<String> excludedTypeMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                        WeaverContext.WEAVER_EXCLUDED_TYPE_EXPRS_KEY, excludedTypeExprs);


                this.typeMatcher = includedTypeMatcher.or( ElementMatchers.not(excludedTypeMatcher) );
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
