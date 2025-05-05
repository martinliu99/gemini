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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopException;
import io.gemini.aop.AopContext;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
import io.gemini.aop.matcher.Pattern;
import io.gemini.aop.matcher.Pattern.Parser;
import io.gemini.api.classloader.AopClassLoader;
import io.gemini.aop.matcher.StringMatcherFactory;
import io.gemini.core.object.ClassRenamer;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.IOUtils;
import io.gemini.core.util.SingleEnumeration;
import net.bytebuddy.matcher.ElementMatcher;

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
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("^Configuring AopClassLoader with settings,");
        }

        StringMatcherFactory classMatcherFactory = new StringMatcherFactory();

        // 1.create ParentFirstFilter with parentFirstTypePatterns and parentFirstResourcePatterns
        Set<String> parentFirstTypePatternExprs = new LinkedHashSet<>();
        Set<Pattern> parentFirstResourcePatterns = new LinkedHashSet<>();

        this.configureParentFirstFilter(aopClassLoader, nameMapping,
                classMatcherFactory, parentFirstTypePatternExprs, parentFirstResourcePatterns);


        // 2.create BootstrapClassFilter with bootstrap classes.
        this.configureBoostrapClassFilter(aopClassLoader, nameMapping, classMatcherFactory);


        // 3.create BootstrapClassConsumerTypeFilter
        configureBootstrapClassConsumerClassFinder(aopClassLoader, nameMapping);


        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) 
            LOGGER.info("$Took '{}' seconds to configure AopClassLoader. \n"
                    + "  parentFirstTypePatterns: {}  parentFirstResourcePatterns: {}", 
                    (System.nanoTime() - startedAt) / 1e9,
                    parentFirstTypePatternExprs.stream().collect( Collectors.joining("\n    ", "\n    ", "\n") ), 
                    parentFirstResourcePatterns.stream().map( p -> p.toString() ).collect( Collectors.joining("\n    ", "\n    ", "\n") )
            );
    }

    private void configureParentFirstFilter(AopClassLoader aopClassLoader, Map<String, String> nameMapping,
            StringMatcherFactory classMatcherFactory, 
            Set<String> parentFirstTypePatternExprs, Set<Pattern> parentFirstResourcePatterns) {
        // 1.collect parent first type patterns
        parentFirstTypePatternExprs.addAll(
                aopContext.getConfigView().getAsStringSet("aop.aopClassLoader.builtinParentFirstTypePatterns", Collections.emptySet()) );
        parentFirstTypePatternExprs.addAll(
                aopContext.getConfigView().getAsStringSet("aop.aopClassLoader.parentFirstTypePatterns", Collections.emptySet()) );

        if(aopContext.isScanClassesFolder())
            parentFirstTypePatternExprs.addAll( CONDITIONAL_BUILTIN_PARENT_FIRST_CLASS_PREFIXES );

        parentFirstTypePatternExprs.addAll( nameMapping.values() );

        ElementMatcher<String> parentFirstClassMatcher = classMatcherFactory.createStringMatcher(
                "ParentFirstClassMatcher",
                Parser.parsePatterns( parentFirstTypePatternExprs, false ), 
                false, false);


        // 2.collect parent first resource patterns
        parentFirstResourcePatterns.addAll( 
                Parser.parsePatterns( 
                        aopContext.getConfigView().getAsStringSet("aop.aopClassLoader.parentFirstResourcePatterns", Collections.emptySet()), true ) );

        // convert parentFirstTypePatterns and merge into parentFirstResourcePatterns
        parentFirstResourcePatterns.addAll(
                Parser.parsePatterns( parentFirstTypePatternExprs, true) );

        ElementMatcher<String> parentFirstResourceMatcher = classMatcherFactory.createStringMatcher(
                "ParentFirstResourceMatcher",
                parentFirstResourcePatterns,
                false, false);


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

    private void configureBoostrapClassFilter(AopClassLoader aopClassLoader, Map<String, String> nameMapping,
            StringMatcherFactory classMatcherFactory) {
        ElementMatcher<String> bootstrapClassMatcher = classMatcherFactory.createStringMatcher(
                "BootstrapClassMatcher",
                Parser.parsePatterns( nameMapping.keySet(), false ), 
                false, false);

        ElementMatcher<String> bootstrapResourceMatcher = classMatcherFactory.createStringMatcher(
                "BootstrapResourceMatcher",
                Parser.parsePatterns( nameMapping.keySet(), true ), 
                false, false);

        aopClassLoader.addTypeFilter( new AopClassLoader.TypeFilter() {

            @Override
            public String filterTypeName(String name) {
                if(bootstrapClassMatcher.matches(name)) {
                    throw new IllegalStateException(name + " should be replaced with corresponding bootstrap class via " 
                            + BootstrapClassConsumer.class.getName() );
                }

                return name;
            }

            @Override
            public String filterResourceName(String name) {
                if(bootstrapResourceMatcher.matches(name)) {
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
            for(String className : consumerClassNames) {
                String path = ClassUtils.convertClassToResource(className, true);
                InputStream inputStream = aopClassLoader.getResourceAsStream(path);

                byte[] byteCode = classRenamer.map(className, IOUtils.toByteArray(inputStream) );

                classesTypeMap.put(className, byteCode);
                classResourceMap.put(path, IOUtils.toURL(path, byteCode));
            }
        } catch(Exception e) {
            LOGGER.warn("Failed to load BootstrapClass consumer class", e);
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