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
package io.gemini.aop.aspectory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AopMetrics.WeaverMetrics;
import io.gemini.aop.Aspect;
import io.gemini.aop.AspectFactory;
import io.gemini.aop.aspectory.support.AspectRepository;
import io.gemini.aop.aspectory.support.AspectRepositoryResolver;
import io.gemini.api.aspect.AspectSpec;
import io.gemini.api.aspect.Pointcut;
import io.gemini.api.classloader.ThreadContext;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.concurrent.TaskExecutor;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.utility.JavaModule;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
class DefaultAspectFactory implements AspectFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAspectFactory.class);


    private final AopContext aopContext;
    private final AspectoryContext aspectoryContext;


    private List<? extends AspectSpec> aspectSpecs;
    private List<? extends AspectRepository<? extends AspectSpec>> aspectRepositories;


    // cache aspects per ClassLoader
    private ConcurrentMap<ClassLoader, List<? extends Aspect>> classLoaderAspectMap;


    public DefaultAspectFactory(AopContext aopContext, AspectoryContext aspectoryContext) {
        long startedAt = System.nanoTime();
        String aspectoryName = aspectoryContext.getAspectoryName();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("^Creating DefaultAspectFactory '{}'", aspectoryName);

        this.aopContext = aopContext;
        this.aspectoryContext = aspectoryContext;

        // 1.create AspectRepository
        AspectoriesContext aspectoriesContext = aspectoryContext.getAspectoriesContext();

        this.aspectSpecs = this.scanAspectSpecs(aopContext, aspectoryContext, aspectoriesContext);
        this.aspectRepositories = this.resolveAspectRepositories(aspectoryContext, aspectoriesContext, this.aspectSpecs);

        // validate AspectRepository
        AspectContext validationContext = aspectoryContext.createAspectContext(aspectoryContext.getAspectClassLoader(), null, true);
        this.aopContext.getGlobalTaskExecutor().executeTasks(
                aspectRepositories, 
                repository -> 
                    repository.create(validationContext)    // validate aspect creation
        )
        .count();


        // 2.initialize properties
        this.classLoaderAspectMap = new ConcurrentReferenceHashMap<>();

        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create AspectFactory '{}'", 
                    (System.nanoTime() - startedAt) / 1e9, aspectoryName);
    }


    private List<? extends AspectSpec> scanAspectSpecs(AopContext aopContext, AspectoryContext aspectoryContext, 
            AspectoriesContext aspectoriesContext) {
        long startedAt = System.nanoTime();
        LOGGER.info("^Scanning AspectSpecs under '{}' Aspectory.", aspectoryContext.getAspectoryName());

        final List<AspectSpec> aspectSpecs = this.aopContext.getGlobalTaskExecutor().executeTasks(
                aspectoriesContext.getAspectSpecScanners(), 
                specScanner -> specScanner.scan(aspectoryContext)
        )
        .flatMap( e -> e.stream() )
        .collect( Collectors.toList() )
        ;

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false) 
            LOGGER.info("$Took '{}' seconds to load {} AspectSpecs under '{}' Aspectory.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, 
                    aspectSpecs.size(), 
                    aspectoryContext.getAspectoryName());
        else
            LOGGER.info("$Took '{}' seconds to load {} AspectSpecs under '{}' Aspectory. {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, 
                    aspectSpecs.size(), 
                    aspectoryContext.getAspectoryName(),
                    StringUtils.join(aspectSpecs, spec -> spec.getAspectName(), "\n  ", "\n  ", "\n")
            );

        return aspectSpecs;
    }

    private List<? extends AspectRepository<? extends AspectSpec>> resolveAspectRepositories(
            AspectoryContext aspectoryContext, AspectoriesContext aspectoriesContext,
            List<? extends AspectSpec> aspectSpecs) {
        long startedAt = System.nanoTime();
        String aspectoryName = aspectoryContext.getAspectoryName();
        LOGGER.info("^Resolving AspectSpec under '{}' Aspectory.", aspectoryName);

        TaskExecutor taskExecutor = this.aopContext.getGlobalTaskExecutor();
        List<? extends AspectRepository<? extends AspectSpec>> aspectRepositories = taskExecutor.executeTasks(
                taskExecutor.splitTasks(aspectSpecs), 
                specs -> resolveAspectRepositoriesSequentially(aspectoryContext, aspectoriesContext, specs)
        )
        .flatMap( e -> e.stream())
        .collect( Collectors.toList() );

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false)
            LOGGER.info("$Took '{}' seconds to resolve {} AspectRepository under '{}' Aspectory.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, aspectRepositories.size(), aspectoryName);
        else 
            LOGGER.info("$Took '{}' seconds to resolve {} AspectRepository under '{}' Aspectory. {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, aspectRepositories.size(), aspectoryName,
                    StringUtils.join(aspectRepositories, repository -> repository.getAspectName(), "\n  ", "\n  ", "\n")
            );

        return aspectRepositories;
    }

    private List<? extends AspectRepository<? extends AspectSpec>> resolveAspectRepositoriesSequentially(
            AspectoryContext aspectoryContext, AspectoriesContext aspectoriesContext,
            List<? extends AspectSpec> aspectSpecs) {
        List<AspectRepository<AspectSpec>> repositories = new ArrayList<>();
        List<AspectRepositoryResolver<AspectSpec, AspectSpec>> aspectRepositoryResolvers = aspectoriesContext.getAspectRepositoryResolvers();
        for(AspectSpec aspectSpec : aspectSpecs) {
            for(AspectRepositoryResolver<AspectSpec, AspectSpec> resolver : aspectRepositoryResolvers) {
                if(resolver.support(aspectSpec) == false)
                    continue;

                List<? extends AspectRepository<AspectSpec>> resolvedRepositories =  resolver.resolve(aspectSpec, aspectoryContext);
                if(resolvedRepositories == null) continue;

                for(AspectRepository<AspectSpec> repository : resolvedRepositories) {
                    if(repository == null) continue;

                    // filter aspectRepositry via aspectMatcher
                    if(aspectoryContext.getIncludedAspectsMatcher().matches(repository.getAspectName()) == false)
                        continue;
                    if(aspectoryContext.getExcludedAspectsMatcher().matches(repository.getAspectName()) == true)
                        continue;

                    repositories.add(repository);
                }
            }
        }

        return repositories;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<? extends AspectSpec>> getAspectSpecs() {
        return Collections.singletonMap(this.aspectoryContext.getAspectoryName(), this.aspectSpecs);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<? extends MethodDescription, List<? extends Aspect>> getAspects(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule) {
        long startedAt = System.nanoTime();
        WeaverMetrics weaverMetrics = aopContext.getAopMetrics().getWeaverMetrics(joinpointClassLoader, javaModule);
        String joinpointClassLoaderId = ClassLoaderUtils.getClassLoaderId(joinpointClassLoader);

        // diagnostic log
        String typeName = typeDescription.getTypeName();
        if(aopContext.isDiagnosticClass(typeName)) {
            LOGGER.info("Getting Aspects for type '{}' in AspectFactory '{}'.", typeName, aspectoryContext.getAspectoryName());
        }

        // 1.filter type by excluding and including rules in Aspectory level
        try {
            if(this.acceptType(typeDescription, joinpointClassLoader, javaModule, weaverMetrics) == false)
                return Collections.emptyMap();
        } finally {
            weaverMetrics.incrTypeAcceptingTime(System.nanoTime() - startedAt);
        }


        // 2.create aspects
        List<? extends Aspect> candidateAspects = getOrCreateAspectPerClassLoader(typeName, typeDescription, 
                joinpointClassLoaderId, joinpointClassLoader, javaModule, weaverMetrics);
        if(CollectionUtils.isEmpty(candidateAspects))
            return Collections.emptyMap();


        // 3. fast match aspects for given type
        List<Aspect.PointcutAspect> pointcutAspects = null;
        try {
            startedAt = System.nanoTime();
            weaverMetrics.incrTypeFastMatchingCount();

            pointcutAspects = this.doFastMatchAspects(typeDescription, joinpointClassLoader, javaModule, candidateAspects, weaverMetrics);
        } finally {
            weaverMetrics.incrTypeFastMatchingTime(System.nanoTime() - startedAt);
        }

        if(CollectionUtils.isEmpty(pointcutAspects)) {
            return Collections.emptyMap();
        }


        // 4. match aspects for given type's methods
        try {
            startedAt = System.nanoTime();
            weaverMetrics.incrTypeMatchingCount();

            Map<MethodDescription, List<? extends Aspect>> methodAspectsMap = new HashMap<>();
            MethodGraph.Linked methodGraph = null;
            for(InDefinedShape methodDescription : MethodUtils.getAllMethodDescriptions(typeDescription)) {
                List<Aspect> aspectChain = this.doMatchAspects(typeDescription, methodDescription, joinpointClassLoader, pointcutAspects, weaverMetrics);
                if(CollectionUtils.isEmpty(aspectChain)) continue;

                // convert matched bridge method to overridden method
                if(methodDescription.isBridge()) {
                    if(methodGraph == null)
                        methodGraph = MethodGraph.Compiler.Default.forJavaHierarchy().compile( (TypeDefinition) typeDescription);

                    MethodGraph.Node locatedNode = methodGraph.locate(methodDescription.asSignatureToken());
                    if(locatedNode != null)
                        methodDescription = locatedNode.getRepresentative().asDefined();
                }

                methodAspectsMap.merge(methodDescription, aspectChain, 
                        (oldValue, value) -> CollectionUtils.merge(oldValue, value) 
                );
            }

            return methodAspectsMap;
        } finally {
            weaverMetrics.incrTypeMatchingTime(System.nanoTime() - startedAt);
        }
    }

    private boolean acceptType(TypeDescription typeDescription, ClassLoader joinpointClassLoader, JavaModule javaModule,
            WeaverMetrics weaverMetrics) {
        // 1.check match switch
        if(this.aspectoryContext.isMatchJoinpoint() == false)
            return false;

        String joinpointClassLoaderName = ClassLoaderUtils.getClassLoaderName(joinpointClassLoader);

        // 2.check included joinpointClassLoader
        if(aspectoryContext.getIncludedClassLoadersMatcher().matches(joinpointClassLoaderName) == true) {
            return true;
        }

        // 3.check excluded joinpointClassLoader
        if(aspectoryContext.getExcludedClassLoadersMatcher().matches(joinpointClassLoaderName) == true) {
            return false;
        }

        // 4.check included type
        if(aspectoryContext.createIncludedTypesMatcher(joinpointClassLoader, javaModule).matches(typeDescription) == true) {
            return true;
        }

        // 5.check excluded type
        if(aspectoryContext.createExcludedTypesMatcher(joinpointClassLoader, javaModule).matches(typeDescription) == true) {
            return false;
        }

        return true;
    }

    private List<? extends Aspect> getOrCreateAspectPerClassLoader(String typeName, TypeDescription typeDescription, 
            String joinpointClassLoaderId, ClassLoader joinpointClassLoader, JavaModule javaModule, 
            WeaverMetrics weaverMetrics) {
        long startedAt = System.nanoTime();

        try {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(joinpointClassLoader);

            if(this.classLoaderAspectMap.containsKey(cacheKey)) {
                return this.classLoaderAspectMap.get(cacheKey);
            }

            // create aspects per ClassLoader
            AspectContext aspectContext = aspectoryContext.createAspectContext(joinpointClassLoader, javaModule);

            List<Aspect> aspects = new ArrayList<>();
            for(AspectRepository<? extends AspectSpec> aspectRepository : this.aspectRepositories) {
                try {
                    Aspect aspect = aspectRepository.create(aspectContext);
                    if(aspect != null)
                        aspects.add(aspect);
                } catch(Throwable t) {
                    LOGGER.warn("Failed to instatiate Aspect with definition '{}'.", aspectRepository, t);
                }
            }

            if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false)
                LOGGER.info("Loaded {} aspects under '{}' Aspectory for '{}'.", 
                        aspects.size(), aspectoryContext.getAspectoryName(), joinpointClassLoaderId);
            else
                LOGGER.info("Loaded {} aspects under '{}' Aspectory for '{}'. {}", 
                        aspects.size(), aspectoryContext.getAspectoryName(), joinpointClassLoaderId,
                        StringUtils.join(aspects, aspect -> aspect.getAspectName(), "\n  ", "\n  ", "\n")
                );

            this.classLoaderAspectMap.putIfAbsent(cacheKey, aspects);
            weaverMetrics.incrAspectCreationCount(aspects.size());

            return aspects;
        } finally {
            weaverMetrics.incrAspectCreationTime(System.nanoTime() - startedAt);
        }
    }

    @SuppressWarnings("unchecked")
    protected List<Aspect.PointcutAspect> doFastMatchAspects(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            final List<? extends Aspect> aspects, final WeaverMetrics weaverMetrics) {
        final String joinpointClassLoaderName = ClassLoaderUtils.getClassLoaderName(joinpointClassLoader);

        // warmup 
        this.filterAspects(typeDescription, joinpointClassLoaderName, aspects.subList(0, 1), weaverMetrics);

        TaskExecutor taskExecutor = this.aopContext.getGlobalTaskExecutor();
        return (List<Aspect.PointcutAspect>) taskExecutor.executeTasks(
                taskExecutor.splitTasks(aspects), 
                subAspects -> {
                    ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

                    try {
                        ThreadContext.setContextClassLoader(joinpointClassLoader);   // set joinpointClassLoader

                        return filterAspects(typeDescription, joinpointClassLoaderName, subAspects, weaverMetrics);
                    } catch(Throwable t) {
                        LOGGER.error("Failed to filter aspects {}", subAspects, t);
                        return Collections.emptyList();
                    } finally {
                        ThreadContext.setContextClassLoader(existingClassLoader);   // set joinpointClassLoader
                    }
                }
        )
        .flatMap( e -> e.stream() ).collect( Collectors.toList() );
    }

    private List<Aspect.PointcutAspect> filterAspects(TypeDescription typeDescription, String joinpointClassLoaderName, 
            List<? extends Aspect> aspects, WeaverMetrics weaverMetrics) {
        List<Aspect.PointcutAspect> pointcutAspects = new ArrayList<>(aspects.size());
        for(int i = 0; i < aspects.size(); i++) {
            long startedAt = System.nanoTime();
            Aspect aspect = aspects.get(i);
            try {
                if(aspect instanceof Aspect.PointcutAspect == false)
                    continue;

                Aspect.PointcutAspect pointcutAspect = (Aspect.PointcutAspect) aspect;
                Pointcut pointcut = pointcutAspect.getPointcut();
                if(pointcut == null)
                    continue;

                if(pointcut.getTypeMatcher() == null || pointcut.getTypeMatcher().matches(typeDescription) == false)
                    continue;

                pointcutAspects.add(pointcutAspect);
            } finally {
                long endedAt = System.nanoTime();
                weaverMetrics.incrAspectTypeFastMatchingTime(aspect, (endedAt - startedAt) );
            }
        }
        return pointcutAspects;
    }

    protected List<Aspect> doMatchAspects(TypeDescription typeDescription, InDefinedShape methodDescription, 
            ClassLoader joinpointClassLoader, List<Aspect.PointcutAspect> aspects, WeaverMetrics weaverMetrics) {
        // TODO: methodDescription.isNative() || 
        if(methodDescription.isNative() || methodDescription.isAbstract())
            return null;

        List<Aspect> candidates = new LinkedList<>();

        for(Aspect.PointcutAspect pointcutAspect : aspects) {
            Pointcut pointcut = pointcutAspect.getPointcut();
            if(pointcut.getMethodMatcher() != null) {
                long startedAt = System.nanoTime();

                boolean matches = false;
                try {
                    matches = pointcut.getMethodMatcher().matches(methodDescription);
                } catch(Throwable t) {
                    LOGGER.info("Failed to match joinpoint '{}.{}(...)' with pointcut '{}'.",
                            typeDescription.getTypeName(), methodDescription.getName(), pointcut, t);
                }

                long endedAt = System.nanoTime();
                weaverMetrics.incrAspectTypeMatchingTime(pointcutAspect, (endedAt - startedAt) );

                if(matches == false) {
                    continue;
                }
            }

            candidates.add(pointcutAspect);
        }

        return candidates;
    }


    @Override
    public void close() throws IOException {
        this.aspectoryContext.close();
    }
}
