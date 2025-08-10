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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopException;
import io.gemini.aop.matcher.Pattern;
import io.gemini.aop.matcher.StringMatcherFactory;
import io.gemini.aop.matcher.TypeMatcherFactory;
import io.gemini.aop.matcher.Pattern.Parser;
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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;


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

    private static final String WEAVER_INCLUDED_CLASS_LOADERS_KEY = "aop.weaver.includedClassLoaders";
    private static final String WEAVER_EXCLUDED_CLASS_LOADERS_KEY = "aop.weaver.excludedClassLoaders";

    private static final String WEAVER_INCLUDED_TYPE_PATTERNS_KEY = "aop.weaver.includedTypePatterns";
    private static final String WEAVER_EXCLUDED_TYPE_PATTERNS_KEY = "aop.weaver.excludedTypePatterns";


    // weaver settings
    private boolean matchJoinpoint;

    private StringMatcherFactory classLoaderMatcherFactory;

    private ElementMatcher<String> includedClassLoadersMatcher;
    private ElementMatcher<String> excludedClassLoadersMatcher;


    private TypeMatcherFactory typeMatcherFactory;

    private Collection<Pattern> includedTypePatterns;
    private Collection<Pattern> excludedTypePatterns;


    private Class<?> classInitializerAdvice;
    private Class<?> classMethodAdvice;
    private Class<?> instanceConstructorAdvice;
    private Class<?> instanceMethodAdvice;


    // weaver installer settings
    private RedefinitionStrategy redefinitionStrategy;


    public WeaverContext(AopContext aopContext) {
        // 1.load weaver settings
        this.classLoaderMatcherFactory = new StringMatcherFactory();
        this.typeMatcherFactory = new TypeMatcherFactory(aopContext.getTypeWorldFactory());

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
                Set<String> includedClassLoaders = configView.getAsStringSet(WEAVER_INCLUDED_CLASS_LOADERS_KEY, new LinkedHashSet<>());

                if(includedClassLoaders.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            includedClassLoaders.size(), WEAVER_INCLUDED_CLASS_LOADERS_KEY, 
                            StringUtils.join(includedClassLoaders, "\n  ")
                    );

                this.includedClassLoadersMatcher = classLoaderMatcherFactory.createStringMatcher(
                        WEAVER_INCLUDED_CLASS_LOADERS_KEY,
                        Parser.parsePatterns( includedClassLoaders ), 
                        false, false);
            }

            {
                Set<String> excludedClassLoaders = new LinkedHashSet<>();
                excludedClassLoaders.addAll(
                        configView.getAsStringList("aop.weaver.builtinExcludedClassLoaders", Collections.emptyList()) );
                excludedClassLoaders.addAll(
                        noMatchingClassInfoList.filter( this::isClassLoader ).getNames() );

                excludedClassLoaders.addAll(
                        configView.getAsStringList(WEAVER_EXCLUDED_CLASS_LOADERS_KEY, Collections.emptyList()) );

                if(excludedClassLoaders.size() > 0) 
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            excludedClassLoaders.size(), WEAVER_EXCLUDED_CLASS_LOADERS_KEY, 
                            StringUtils.join(excludedClassLoaders, "\n  ")
                    );

                this.excludedClassLoadersMatcher = classLoaderMatcherFactory.createStringMatcher(
                        WEAVER_EXCLUDED_CLASS_LOADERS_KEY,
                        Parser.parsePatterns( excludedClassLoaders ), 
                        true, false );

                aopContext.getAopMetrics().setExcludedClassLoaderMatcher(this.excludedClassLoadersMatcher);
            }

            {
                Set<String> includedTypePatterns = configView.getAsStringSet(WEAVER_INCLUDED_TYPE_PATTERNS_KEY, Collections.emptySet());

                if(includedTypePatterns.size() > 0)
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            includedTypePatterns.size(), WEAVER_INCLUDED_TYPE_PATTERNS_KEY,
                            StringUtils.join(includedTypePatterns, "\n  ") 
                    );

                this.includedTypePatterns = typeMatcherFactory.validateTypePatterns(
                        WeaverContext.WEAVER_INCLUDED_TYPE_PATTERNS_KEY,
                        Parser.parsePatterns( includedTypePatterns ), 
                        false, 
                        aopClassLoader, 
                        null );
            }

            {
                Set<String> excludedTypePatterns = new LinkedHashSet<>();
                excludedTypePatterns.addAll(
                        configView.getAsStringList("aop.weaver.builtinExcludedTypePatterns", Collections.emptyList()) );
                excludedTypePatterns.addAll(
                        noMatchingClassInfoList.filter( this::isClass ).getNames() );
                excludedTypePatterns.addAll( aopContext.getBootstrapClassNameMapping().keySet() );

                excludedTypePatterns.addAll(
                        configView.getAsStringList(WEAVER_EXCLUDED_TYPE_PATTERNS_KEY, Collections.emptyList()) );

                if(excludedTypePatterns.size() > 0) 
                    LOGGER.info("Loaded {} rules from '{}' setting. \n  {} \n", 
                            excludedTypePatterns.size(), WEAVER_EXCLUDED_TYPE_PATTERNS_KEY,
                            StringUtils.join(excludedTypePatterns, "\n  ")
                    );

                this.excludedTypePatterns = typeMatcherFactory.validateTypePatterns(
                        WeaverContext.WEAVER_EXCLUDED_TYPE_PATTERNS_KEY,
                        Parser.parsePatterns( excludedTypePatterns ), 
                        true, 
                        aopClassLoader, 
                        null );
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


    public ElementMatcher<String> getExcludedClassLoadersMatcher() {
        return excludedClassLoadersMatcher;
    }

    public ElementMatcher<String> getIncludedClassLoadersMatcher() {
        return includedClassLoadersMatcher;
    }

    public boolean isExcludedClassLoader(String joinpointClassLoaderName) {
        return this.excludedClassLoadersMatcher.matches(joinpointClassLoaderName) == true;
    }


    public ElementMatcher<TypeDescription> createIncludedTypesMatcher(ClassLoader joinpointClassLoader, JavaModule javaModule) {
        return typeMatcherFactory.createTypeMatcher(
                WEAVER_INCLUDED_TYPE_PATTERNS_KEY, 
                includedTypePatterns, 
                false, 
                joinpointClassLoader, 
                javaModule ); 
    }

    public ElementMatcher<TypeDescription> createExcludedTypesMatcher(ClassLoader joinpointClassLoader, JavaModule javaModule) {
        return typeMatcherFactory.createTypeMatcher(
                WEAVER_EXCLUDED_TYPE_PATTERNS_KEY, 
                excludedTypePatterns, 
                false, 
                joinpointClassLoader, 
                javaModule ); 
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
