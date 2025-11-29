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
package io.gemini.aop.activation.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
import io.gemini.aop.matcher.ElementMatcherFactory;
import io.gemini.api.aop.AopException;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.core.object.ClassRenamer;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.IOUtils;
import io.gemini.core.util.SingleEnumeration;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AopClassLoaderConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AopClassLoaderConfigurer.class);

    private final static Set<String /* Class prefix */ > CONDITIONAL_BUILTIN_PARENT_FIRST_CLASS_PREFIXES;


    private final AopContext aopContext;


    static {
        CONDITIONAL_BUILTIN_PARENT_FIRST_CLASS_PREFIXES = new LinkedHashSet<>();
        CONDITIONAL_BUILTIN_PARENT_FIRST_CLASS_PREFIXES.add("org.aspectj.lang.annotation");
    }


    public AopClassLoaderConfigurer(AopContext aopContext) {
        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;
    }


    public void configure(AopClassLoader aopClassLoader, Map<String, String> nameMapping) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("^Configuring AopClassLoader, ");
        }

        // 1.create ParentFirstFilter with parentFirstTypeExpressions and parentFirstResourceExpressions
        Set<String> parentFirstTypeExpressions = new LinkedHashSet<>();
        Set<String> parentFirstResourceExpressions = new LinkedHashSet<>();

        this.configureParentFirstFilter(aopClassLoader,
                parentFirstTypeExpressions, parentFirstResourceExpressions);


        // 2.create BootstrapClassFilter with bootstrap classes.
        this.configureBoostrapClassFilter(aopClassLoader, nameMapping);


        // 3.create BootstrapClassConsumerTypeFilter
        configureBootstrapClassConsumerClassFinder(aopClassLoader, nameMapping);


        long time =  System.nanoTime() - startedAt;
        if (aopContext.getDiagnosticLevel().isDebugEnabled() && LOGGER.isInfoEnabled()) 
            LOGGER.info("$Took '{}' seconds to configure AopClassLoader with settings, \n"
                    + "  parentFirstTypeExpressions: {}"
                    + "  parentFirstResourceExpressions: {}",
                    time / 1e9,
                    StringUtils.join(parentFirstTypeExpressions, "\n    ", "\n    ", "\n"), 
                    StringUtils.join(parentFirstResourceExpressions, "\n    ", "\n    ", "\n")
            );
        else if (aopContext.getDiagnosticLevel().isSimpleEnabled() && LOGGER.isInfoEnabled()) 
            LOGGER.info("$Took '{}' seconds to configure AopClassLoader.", time / 1e9);

        aopContext.getAopMetrics().getBootstraperMetrics().setAopCLConfigTime(time);
    }

    private void configureParentFirstFilter(AopClassLoader aopClassLoader, 
            Set<String> parentFirstTypeExpressions, Set<String> parentFirstResourceExpressions) {
        // 1.collect parent first type expressions
        parentFirstTypeExpressions.addAll(
                aopContext.getConfigView().getAsStringSet("aop.aopClassLoader.builtinParentFirstTypeExpressions", Collections.emptySet()) );
        parentFirstTypeExpressions.addAll(
                aopContext.getConfigView().getAsStringSet("aop.aopClassLoader.parentFirstTypeExpressions", Collections.emptySet()) );

        if (aopContext.isScanClassesFolder())
            parentFirstTypeExpressions.addAll( CONDITIONAL_BUILTIN_PARENT_FIRST_CLASS_PREFIXES );

        ElementMatcher<String> parentFirstClassMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                "ParentFirstClassMatcher", parentFirstTypeExpressions, ElementMatchers.none());


        // 2.collect parent first resource expressions
        parentFirstResourceExpressions.addAll( 
                aopContext.getConfigView().getAsStringSet("aop.aopClassLoader.parentFirstResourceExpressions", Collections.emptySet()) );

        // convert parentFirstTypeExpressions and merge into parentFirstResourceExpressions
        parentFirstResourceExpressions.addAll( parentFirstTypeExpressions );

        ElementMatcher<String> parentFirstResourceMatcher = ElementMatcherFactory.INSTANCE.createResourceNameMatcher(
                "ParentFirstResourceMatcher", parentFirstTypeExpressions, ElementMatchers.none());


        // 3.add ParentFirstFilter
        aopClassLoader.addParentFirstFilter( new AopClassLoader.ParentFirstFilter() {

            @Override
            public boolean isParentFirstClass(String name) {
                return parentFirstClassMatcher.matches(name);
            }

            @Override
            public boolean isParentFirstResource(String name) {
                return parentFirstResourceMatcher.matches(name);
            }
        });
    }


    private void configureBoostrapClassFilter(AopClassLoader aopClassLoader, Map<String, String> nameMapping) {
        ElementMatcher<String> bootstrapClassMatcher = ElementMatcherFactory.INSTANCE.createTypeNameMatcher(
                "BootstrapClassMatcher", nameMapping.keySet(), ElementMatchers.none());

        ElementMatcher<String> bootstrapResourceMatcher = ElementMatcherFactory.INSTANCE.createResourceNameMatcher(
                "BootstrapResourceMatcher", nameMapping.keySet(), ElementMatchers.none());

        aopClassLoader.addTypeFilter( new AopClassLoader.TypeFilter() {

            @Override
            public String filterTypeName(String name) {
                if (bootstrapClassMatcher.matches(name)) {
                    throw new IllegalStateException(name + " should be replaced with corresponding bootstrap class via " 
                            + BootstrapClassConsumer.class.getName() );
                }

                return name;
            }

            @Override
            public String filterResourceName(String name) {
                if (bootstrapResourceMatcher.matches(name)) {
                    throw new IllegalStateException(name + " should be replaced with corresponding bootstrap class via "
                            + BootstrapClassConsumer.class.getName() );
                }

                return name;
            }
        } );
    }

    private void configureBootstrapClassConsumerClassFinder(AopClassLoader aopClassLoader, Map<String, String> nameMapping) {
        // discover consumer classes
        List<String> consumerClassNames = aopContext.getClassScanner().getClassNamesWithAnnotation(BootstrapClassConsumer.class.getName());

        ClassRenamer classRenamer = new ClassRenamer.Default(
                nameMapping, 
                aopContext.isDumpByteCode(),
                aopContext.getByteCodeDumpPath()
        );

        Map<String, byte[]> classesTypeMap = new LinkedHashMap<>();
        Map<String, URL> classResourceMap = new LinkedHashMap<>(consumerClassNames.size());
        try {
            for (String className : consumerClassNames) {
                String path = ClassUtils.convertClassToResource(className, true);
                InputStream inputStream = aopClassLoader.getResourceAsStream(path);

                byte[] byteCode = classRenamer.map(className, IOUtils.toByteArray(inputStream) );

                classesTypeMap.put(className, byteCode);
                classResourceMap.put(path, IOUtils.toURL(path, byteCode));
            }
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Could not load BootstrapClass consumer class", e);

            throw new AopException(e);
        }

        aopClassLoader.addTypeFinder( new AopClassLoader.TypeFinder() {

            @Override
            public byte[] findByteCode(String name) {
                return classesTypeMap.get(name);
            }


            @Override
            public URL findResource(String name) {
                return classResourceMap.get(name);
            }

            @Override
            public Enumeration<URL> findResources(String name) throws IOException {
                 return new SingleEnumeration<URL>( classResourceMap.get(name) );
            }
        } );
    }

}