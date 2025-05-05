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
import io.gemini.aop.aspectory.support.AspectSpecScanner;
import io.gemini.api.aspect.AspectSpec;
import io.gemini.api.aspect.Pointcut;
import io.gemini.api.classloader.ThreadContext;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
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


    private List<AspectSpecHolder<AspectSpec>> aspectSpecHolders;
    private List<? extends AspectRepository<AspectSpec>> aspectRepositories;


    // cache aspects per ClassLoader
    private ConcurrentMap<ClassLoader, List<? extends Aspect>> classLoaderAspectMap;


    public DefaultAspectFactory(AopContext aopContext, AspectoryContext aspectoryContext) {
        this.aopContext = aopContext;
        this.aspectoryContext = aspectoryContext;

        this.initialize(aopContext, aspectoryContext, aspectoryContext.getAspectoriesContext());
    }

    private void initialize(AopContext aopContext, AspectoryContext aspectoryContext, AspectoriesContext aspectoriesContext) {
        // 1.create AspectRepository
        this.aspectSpecHolders = this.scanAspectSpecs(aopContext, aspectoryContext, aspectoriesContext);
        this.aspectRepositories = this.resolveAspectRepository(aspectoryContext, aspectoriesContext, this.aspectSpecHolders);

        // validate AspectRepository
        AspectContext validationContext = aspectoryContext.createAspectContext(aspectoryContext.getAspectClassLoader(), null, true);
        this.aopContext.getGlobalTaskExecutor().executeTasks(
                aspectRepositories, 
                repositories ->
                    repositories.stream()
                    .map( repository -> 
                        repository.create(validationContext) )    // validate aspect creation
                    .collect( Collectors.toList() )
        );


        // 3.initialize properties
        this.classLoaderAspectMap = new ConcurrentReferenceHashMap<>();
    }

    private List<AspectSpecHolder<AspectSpec>> scanAspectSpecs(AopContext aopContext, AspectoryContext aspectoryContext, 
            AspectoriesContext aspectoriesContext) {
        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("^Scanning AspectSpec.");
        }


        // filter AspectSpecHolders
        final List<AspectSpecHolder<AspectSpec>> aspectSpecHolders = new ArrayList<>();
        for(AspectSpecScanner<AspectSpec> aspectSpecScanner : aspectoriesContext.getAspectSpecScanners()) {
            List<AspectSpecHolder<AspectSpec>> specHolders = aspectSpecScanner.scan(aspectoryContext);
            if(specHolders == null)
                continue;

            for(AspectSpecHolder<AspectSpec> aspectSpecHolder : specHolders) {
                // check aspect name
                String aspectName = aspectSpecHolder.getAspectName();

                if(aspectoryContext.getIncludedAspectsMatcher().matches(aspectName) == false) {
                    continue;
                }

                if(aspectoryContext.getExcludedAspectsMatcher().matches(aspectName) == true)
                    continue;

                aspectSpecHolders.add(aspectSpecHolder);
            }
        }

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false) 
            LOGGER.info("$Took '{}' seconds to load {} AspectSpecs under '{}' Aspectory.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, aspectSpecHolders.size(), aspectoryContext.getAspectoryName());
        else
            LOGGER.info("$Took '{}' seconds to load {} AspectSpecs under '{}' Aspectory. {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, aspectSpecHolders.size(), aspectoryContext.getAspectoryName(),
                    aspectSpecHolders.size() == 0 ? "" : aspectSpecHolders.stream().map( holder -> holder.getAspectName() ).collect( Collectors.joining("\n  ", "\n  ", "\n") ) );

        return aspectSpecHolders;
    }

    private List<? extends AspectRepository<AspectSpec>> resolveAspectRepository(
            AspectoryContext aspectoryContext,
            AspectoriesContext aspectoriesContext,
            List<AspectSpecHolder<AspectSpec>> aspectSpecHolders) {
        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("^Resolving AspectSpec.");
        }

        List<AspectRepositoryResolver<AspectSpec, AspectSpec>> aspectRepositoryResolvers = aspectoriesContext.getAspectRepositoryResolvers();
        List<? extends AspectRepository<AspectSpec>> aspectRepositories = this.aopContext.getGlobalTaskExecutor().executeTasks(
                aspectSpecHolders, 
                specHolders ->
                    specHolders.stream()
                    .flatMap(aspectSpecHolder -> 
                        aspectRepositoryResolvers.stream()
                            .filter( aspectRepositoryResolver -> 
                                aspectRepositoryResolver.support(aspectSpecHolder) )
                            .flatMap( r -> 
                                r.resolve(aspectSpecHolder, aspectoryContext )
                            .stream()
                        )
                    )
                    .filter( aspectRepository -> aspectRepository != null )
                    .collect( Collectors.toList() )
        );

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false)
            LOGGER.info("$Took '{}' seconds to resolve {} AspectRepository under '{}' Aspectory.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, aspectRepositories.size(), aspectoryContext.getAspectoryName());
        else 
            LOGGER.info("$Took '{}' seconds to resolve {} AspectRepository under '{}' Aspectory. {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, aspectRepositories.size(), aspectoryContext.getAspectoryName(),
                    aspectRepositories.size() == 0 ? "" : aspectRepositories.stream().map( repository -> repository.toString() ).collect( Collectors.joining("\n  ", "\n  ", "\n") ) );

        return aspectRepositories;
    }


    Map<String, List<AspectSpecHolder<AspectSpec>>> getAspectSpecHolders() {
        return Collections.singletonMap(this.aspectoryContext.getAspectoryName(), this.aspectSpecHolders);
    }


    /*
     * @see io.gemini.aop.aspectory.AspectFactory#getAspects(net.bytebuddy.description.type.TypeDescription, java.lang.ClassLoader, net.bytebuddy.utility.JavaModule)
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
            for(InDefinedShape methodDescription : MethodUtils.getAllMethodDescriptions(typeDescription)) {
                List<Aspect> aspectChain = this.doMatchAspects(typeDescription, methodDescription, joinpointClassLoader, pointcutAspects, weaverMetrics);

                if(CollectionUtils.isEmpty(aspectChain) == false)
                    methodAspectsMap.put(methodDescription, aspectChain);
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
            for(AspectRepository<AspectSpec> aspectRepository : this.aspectRepositories) {
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
                        aspects.size() == 0 ? "\n" : aspects.stream().map( aspect -> aspect.getAspectName() ).collect( Collectors.joining("\n  ", "\n  ", "\n") ) );

            this.classLoaderAspectMap.putIfAbsent(cacheKey, aspects);
            weaverMetrics.incrAspectCreationCount(aspects.size());

            return aspects;
        } finally {
            weaverMetrics.incrAspectCreationTime(System.nanoTime() - startedAt);
        }
    }

    protected List<Aspect.PointcutAspect> doFastMatchAspects(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            final List<? extends Aspect> aspects, final WeaverMetrics weaverMetrics) {
        final String joinpointClassLoaderName = ClassLoaderUtils.getClassLoaderName(joinpointClassLoader);

        // warmup 
        this.filterAspects(typeDescription, joinpointClassLoaderName, aspects.subList(0, 1), weaverMetrics);

        return this.aopContext.getGlobalTaskExecutor().executeTasks(
                aspects, 
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
        );
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
        //methodDescription.isAbstract() || 
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

        // remove duplicate advice
        for(int i = 0; i < candidates.size(); i++) {
            for(int j=candidates.size()-1; j>i; j--) {
                Aspect aspect = candidates.get(j);
                if(candidates.get(i).getAdviceClass() == aspect.getAdviceClass()) {
                    if(LOGGER.isDebugEnabled())
                        LOGGER.debug("Removed dupplicate advice '{}'.", aspect);

                    candidates.remove(j);
                }
            }
        }

        return candidates;
    }


    @Override
    public void close() throws IOException {
        this.aspectoryContext.close();
    }
}
