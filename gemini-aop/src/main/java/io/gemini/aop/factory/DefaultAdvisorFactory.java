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
package io.gemini.aop.factory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.AdvisorFactory;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AopMetrics.WeaverMetrics;
import io.gemini.aop.factory.support.AdvisorRepository;
import io.gemini.aop.factory.support.AdvisorRepositoryResolver;
import io.gemini.aop.factory.support.AdvisorSpecPostProcessor;
import io.gemini.aop.factory.support.AdvisorSpecScanner;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Pointcut;
import io.gemini.api.classloader.ThreadContext;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
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
class DefaultAdvisorFactory implements AdvisorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdvisorFactory.class);


    private final AopContext aopContext;
    private final FactoryContext factoryContext;

    private final Collection<? extends AdvisorSpec> advisorSpecs;
    private final Collection<? extends AdvisorRepository> advisorRepositories;

    // cache advisors per ClassLoader
    private ConcurrentMap<ClassLoader, List<? extends Advisor>> classLoaderAdvisorMap;


    public DefaultAdvisorFactory(FactoryContext factoryContext) {
        long startedAt = System.nanoTime();

        this.aopContext = factoryContext.getAopContext();
        this.factoryContext = factoryContext;

        String factoryName = factoryContext.getFactoryName();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("^Creating DefaultAdvisorFactory '{}'", factoryName);

        // 1.resolve AdvisorRepository
        this.advisorSpecs = scanAdvisorSpecs(factoryContext);
        this.advisorRepositories = this.resolveAdvisorRepositories(factoryContext, advisorSpecs);

        // 2.initialize properties
        this.classLoaderAdvisorMap = new ConcurrentReferenceHashMap<>();

        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create AdvisorFactory '{}'", 
                    (System.nanoTime() - startedAt) / 1e9, factoryName);
    }

    /**
     * @param factoryContext
     * @return
     */
    private Collection<? extends AdvisorSpec> scanAdvisorSpecs(FactoryContext factoryContext) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        LOGGER.info("^Scanning AdvisorSpec under '{}'.", factoryName);

        Map<String, AdvisorSpec> advisorSpecMap = AdvisorSpecScanner.scanSpecs(factoryContext);

        advisorSpecMap = AdvisorSpecPostProcessor.postProcessSpecs(factoryContext, advisorSpecMap );

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false)
            LOGGER.info("$Took '{}' seconds to scan {} AdvisorSpec under '{}'.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecMap.size(), factoryName);
        else 
            LOGGER.info("$Took '{}' seconds to scan {} AdvisorSpec under '{}'. {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecMap.size(), factoryName,
                    StringUtils.join(advisorSpecMap.values(), AdvisorSpec::getAdvisorName, "\n  ", "\n  ", "\n")
            );

        return advisorSpecMap.values();
    }

    private Collection<? extends AdvisorRepository> resolveAdvisorRepositories(FactoryContext factoryContext, 
            Collection<? extends AdvisorSpec> advisorSpecs) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        LOGGER.info("^Resolving AdvisorSpec under '{}'.", factoryName);

        AdvisorContext validationContext = factoryContext.createAdvisorContext(factoryContext.getClassLoader(), null, true);
        List<? extends AdvisorRepository> advisorRepositories = this.aopContext.getGlobalTaskExecutor().executeTasks(
                advisorSpecs, 
                advisorSpec -> AdvisorRepositoryResolver.resolveRepository(factoryContext, validationContext, advisorSpec)
        )
        .stream()
        .flatMap( e -> e.stream())
        .collect( Collectors.toList() );

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false)
            LOGGER.info("$Took '{}' seconds to resolve {} AdvisorRepository under '{}'.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorRepositories.size(), factoryName);
        else 
            LOGGER.info("$Took '{}' seconds to resolve {} AdvisorRepository under '{}'. {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorRepositories.size(), factoryName,
                    StringUtils.join(advisorRepositories, AdvisorRepository::getAdvisorName, "\n  ", "\n  ", "\n")
            );

        return advisorRepositories;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Integer> getAdvisorSpecNum() {
        return Collections.singletonMap(this.factoryContext.getFactoryName(), this.advisorRepositories.size());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(
            TypeDescription typeDescription, ClassLoader joinpointClassLoader, JavaModule javaModule) {
        long startedAt = System.nanoTime();

        // diagnostic log
        String typeName = typeDescription.getTypeName();
        if(aopContext.isDiagnosticClass(typeName)) {
            LOGGER.info("Getting Advisors for type '{}' in AdvisorFactory '{}'.", typeName, factoryContext.getFactoryName());
        }


        // 1.filter type by classLoaderMatcher and TypeMatcher in Advisor level
        WeaverMetrics weaverMetrics = aopContext.getAopMetrics().getWeaverMetrics(joinpointClassLoader, javaModule);
        try {
            if(this.acceptType(typeDescription, joinpointClassLoader, javaModule) == false)
                return Collections.emptyMap();
        } finally {
            weaverMetrics.incrTypeAcceptingTime(System.nanoTime() - startedAt);
        }


        // 2.create advisors per ClassLoader
        List<? extends Advisor> candidateAdvisors = null;
        try {
            startedAt = System.nanoTime();

            candidateAdvisors = getOrCreateAdvisorPerClassLoader(typeName, joinpointClassLoader, javaModule, weaverMetrics);

            if(CollectionUtils.isEmpty(candidateAdvisors))
                return Collections.emptyMap();
        } finally {
            weaverMetrics.incrAdvisorCreationTime(System.nanoTime() - startedAt);
        }


        try {
            // 3. fast match advisors for given type
            List<Advisor.PointcutAdvisor> pointcutAdvisors = null;
            try {
                startedAt = System.nanoTime();

                pointcutAdvisors = fastMatchAdvisors(typeDescription, 
                        joinpointClassLoader, javaModule, 
                        candidateAdvisors, weaverMetrics);

                // ignore synthetic class
                if(CollectionUtils.isEmpty(pointcutAdvisors) || typeDescription.isSynthetic()) {
                    return Collections.emptyMap();
                }
            } finally {
                weaverMetrics.incrTypeFastMatchingCount();
                weaverMetrics.incrTypeFastMatchingTime(System.nanoTime() - startedAt);
            }


            // 4. match advisors for given type's methods
            try {
                startedAt = System.nanoTime();

                return matchAdvisors(typeDescription, 
                        joinpointClassLoader, pointcutAdvisors, weaverMetrics);
            } finally {
                weaverMetrics.incrTypeMatchingCount();
                weaverMetrics.incrTypeMatchingTime(System.nanoTime() - startedAt);
            }
        } finally {
            AdvisorContext advisorContext = factoryContext.createAdvisorContext(joinpointClassLoader, javaModule);

            TypeWorld typeWorld = advisorContext.getTypeWorld();
            if(typeWorld != null && typeWorld instanceof TypeWorld.CacheResolutionFacade) {
                ((TypeWorld.CacheResolutionFacade) typeWorld).releaseCache(typeDescription);
            }
        }
    }

    private boolean acceptType(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule) {
        // 1.check match switch
        if(this.factoryContext.isMatchJoinpoint() == false)
            return false;

        // 2.filter type by classLoaderMatcher
        if(factoryContext.isAcceptableClassLoader(joinpointClassLoader) == false)
            return false;

        // 3.filter type by typeMatcher
        if(factoryContext.isAcceptableType(typeDescription.getTypeName()) == false)
            return false;

        return true;
    }

    private List<? extends Advisor> getOrCreateAdvisorPerClassLoader(String typeName, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, WeaverMetrics weaverMetrics) {
        AdvisorContext advisorContext = factoryContext.createAdvisorContext(joinpointClassLoader, javaModule);

        ClassLoader cacheKey = ClassLoaderUtils.maskNull(joinpointClassLoader);

        if(this.classLoaderAdvisorMap.containsKey(cacheKey)) {
            return this.classLoaderAdvisorMap.get(cacheKey);
        }


        // create Advisors
        long startedAt = System.nanoTime();
        LOGGER.info("^Creating advisors under '{}' for '{}'.", 
                factoryContext.getFactoryName(), joinpointClassLoader);

        List<Advisor> advisors = factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                advisorRepositories, 
                advisorRepository -> {
                    try {
                        return advisorRepository.create(advisorContext);
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to instantiate advisor with '{}'.", advisorRepository, t);
                        return null;
                    }
                },
                result -> {
                    ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
                    try {
                        ThreadContext.setContextClassLoader(joinpointClassLoader);   // set joinpointClassLoader
                        return result.get();
                    } finally {
                        ThreadContext.setContextClassLoader(existingClassLoader);
                    }
                }
        )
        .stream()
        .filter( e -> e != null)
        .collect( Collectors.toList() );

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false)
            LOGGER.info("$Took '{}' seconds to load {} advisors under '{}' for '{}'.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, 
                    advisors.size(), factoryContext.getFactoryName(), joinpointClassLoader);
        else
            LOGGER.info("$Took '{}' seconds to load {} advisors under '{}' for '{}'. {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, 
                    advisors.size(), factoryContext.getFactoryName(), joinpointClassLoader,
                    StringUtils.join(advisors, Advisor::getAdvisorName, "\n  ", "\n  ", "\n")
            );

        this.classLoaderAdvisorMap.putIfAbsent(cacheKey, advisors);
        weaverMetrics.incrAdvisorCreationCount(advisors.size());

        return advisors;
    }


    private List<Advisor.PointcutAdvisor> fastMatchAdvisors(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            final List<? extends Advisor> advisors, final WeaverMetrics weaverMetrics) {
        List<Advisor.PointcutAdvisor> matchedAdvisors = new ArrayList<>();
        for(Advisor advisor : advisors) {
            try {
                if(advisor instanceof Advisor.PointcutAdvisor == false)
                    continue;

                Advisor.PointcutAdvisor pointcutAdvisor = (Advisor.PointcutAdvisor) advisor;
                Pointcut pointcut = pointcutAdvisor.getPointcut();
                if(pointcut == null || pointcut.getTypeMatcher() == null)
                    continue;

                if(pointcut.getTypeMatcher().matches(typeDescription))
                    matchedAdvisors.add(pointcutAdvisor);
            } catch(Throwable t) {
                LOGGER.error("Failed to filter advisors {}", advisors, t);
            }
        }

        return matchedAdvisors;
    }

    private Map<MethodDescription, List<? extends Advisor>> matchAdvisors(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, List<Advisor.PointcutAdvisor> pointcutAdvisors, WeaverMetrics weaverMetrics) {
        Map<MethodDescription, List<? extends Advisor>> methodAdvisorsMap = new HashMap<>();
        MethodGraph.Linked methodGraph = null;
        for(InDefinedShape methodDescription : MethodUtils.getAllMethodDescriptions(typeDescription)) {
            // ignore synthetic method?
            if(methodDescription.isNative() || methodDescription.isAbstract()
                    || (methodDescription.isSynthetic() && !methodDescription.isBridge()) )
                continue;

            List<Advisor> candidateAdvisors = new LinkedList<>();
            for(Advisor.PointcutAdvisor pointcutAdvisor : pointcutAdvisors) {
                Pointcut pointcut = pointcutAdvisor.getPointcut();
                if(pointcut == null || pointcut.getMethodMatcher() == null) 
                    continue;

                try {
//                    long startedAt = System.nanoTime();

                    if(pointcut.getMethodMatcher().matches(methodDescription))
                        candidateAdvisors.add(pointcutAdvisor);
                } catch(Throwable t) {
                    LOGGER.info("Failed to match joinpoint with pointcut. \n  Joinpoitn: {} \n  Advisor: {} \n  ClassLoader: {} \n  Error reason: {} \n",
                            MethodUtils.getMethodSignature(methodDescription), pointcutAdvisor, joinpointClassLoader, t.getMessage(), t);
                } finally {
//                  weaverMetrics.incrAdvisorTypeMatchingTime(pointcutAdvisor, (System.nanoTime() - startedAt) );
                }
            }

            if(CollectionUtils.isEmpty(candidateAdvisors)) 
                continue;


            // convert matched bridge method to overridden method
            if(methodDescription.isBridge()) {
                if(methodGraph == null)
                    methodGraph = MethodGraph.Compiler.Default.forJavaHierarchy().compile( (TypeDefinition) typeDescription);

                MethodGraph.Node locatedNode = methodGraph.locate(methodDescription.asSignatureToken());
                if(locatedNode != null)
                    methodDescription = locatedNode.getRepresentative().asDefined();
            }

            methodAdvisorsMap.merge(methodDescription, candidateAdvisors, 
                    (oldValue, value) -> CollectionUtils.merge(oldValue, value) 
            );
        }

        return methodAdvisorsMap;
    }


    @Override
    public void close() throws IOException {
        this.factoryContext.close();
    }
}
