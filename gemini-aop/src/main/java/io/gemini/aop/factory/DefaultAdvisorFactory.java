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
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Pointcut;
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

    private final List<? extends AdvisorSpec> advisorSpecs;
    private final List<? extends AdvisorRepository> advisorRepositories;

    // cache advisors per ClassLoader
    private ConcurrentMap<ClassLoader, List<? extends Advisor>> classLoaderAdvisorMap;


    public DefaultAdvisorFactory(AopContext aopContext, FactoryContext factoryContext) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("^Creating DefaultAdvisorFactory '{}'", factoryName);

        this.aopContext = aopContext;
        this.factoryContext = factoryContext;

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
     * @param factoryContext2
     * @return
     */
    private List<? extends AdvisorSpec> scanAdvisorSpecs(FactoryContext factoryContext2) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        LOGGER.info("^Scanning AdvisorSpec under '{}'.", factoryName);

        List<? extends AdvisorSpec> advisorSpecs = this.aopContext.getGlobalTaskExecutor().executeTasks(
                factoryContext.getFactoriesContext().getAdvisorSpecScanners(), 
                advisorSpecScanner -> advisorSpecScanner.scan(factoryContext)
        )
        .stream()
        .flatMap( e -> e.stream())
        .collect( Collectors.toList() );

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false)
            LOGGER.info("$Took '{}' seconds to scan {} AdvisorSpec under '{}'.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecs.size(), factoryName);
        else 
            LOGGER.info("$Took '{}' seconds to scan {} AdvisorSpec under '{}'. {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecs.size(), factoryName,
                    StringUtils.join(advisorSpecs, repository -> repository.getAdvisorName(), "\n  ", "\n  ", "\n")
            );

        return advisorSpecs;
    }

    private List<? extends AdvisorRepository> resolveAdvisorRepositories(FactoryContext factoryContext, 
            List<? extends AdvisorSpec> advisorSpecs) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        LOGGER.info("^Resolving AdvisorSpec under '{}'.", factoryName);

        List<AdvisorRepositoryResolver> advisorRepositoryResolvers = factoryContext.getFactoriesContext().getAdvisorRepositoryResolvers();
        AdvisorContext validationContext = factoryContext.createAdvisorContext(factoryContext.getClassLoader(), null, true);
        List<? extends AdvisorRepository> advisorRepositories = this.aopContext.getGlobalTaskExecutor().executeTasks(
                advisorSpecs, 
                advisorSpec -> {
                    List<AdvisorRepository> repositories = new ArrayList<>(advisorRepositoryResolvers.size());
                    for(AdvisorRepositoryResolver advisorRepositoryResolver : advisorRepositoryResolvers) {
                        AdvisorRepository advisorRepository  = advisorRepositoryResolver.resolve(factoryContext, advisorSpec);
                        if(advisorRepository == null) continue;

                        // filter advisorRepositry via advisorMatcher
                        if(factoryContext.matchAdvisor(advisorRepository.getAdvisorName()) == false)
                            continue;

                        // validate advisor creation
                        try {
                            advisorRepository.create(validationContext);
                        } catch(Exception e) {
                            continue;
                        }

                        repositories.add(advisorRepository);
                    }

                    return repositories;
                }
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
                    StringUtils.join(advisorRepositories, repository -> repository.getAdvisorName(), "\n  ", "\n  ", "\n")
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
        WeaverMetrics weaverMetrics = aopContext.getAopMetrics().getWeaverMetrics(joinpointClassLoader, javaModule);
        String joinpointClassLoaderId = ClassLoaderUtils.getClassLoaderId(joinpointClassLoader);

        // diagnostic log
        String typeName = typeDescription.getTypeName();
        if(aopContext.isDiagnosticClass(typeName)) {
            LOGGER.info("Getting Advisors for type '{}' in AdvisorFactory '{}'.", typeName, factoryContext.getFactoryName());
        }


        // 1.filter type by classLoaderMatcher and TypeMatcher in Advisor level
        try {
            if(this.acceptType(typeDescription, joinpointClassLoader, javaModule, weaverMetrics) == false)
                return Collections.emptyMap();
        } finally {
            weaverMetrics.incrTypeAcceptingTime(System.nanoTime() - startedAt);
        }


        // 2.create advisors per ClassLoader
        List<? extends Advisor> candidateAdvisors = null;
        try {
            startedAt = System.nanoTime();

            candidateAdvisors = getOrCreateAdvisorPerClassLoader(typeName,  
                    joinpointClassLoaderId, joinpointClassLoader, javaModule, weaverMetrics);

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

    private boolean acceptType(TypeDescription typeDescription, ClassLoader joinpointClassLoader, JavaModule javaModule,
            WeaverMetrics weaverMetrics) {
        // 1.check match switch
        if(this.factoryContext.isMatchJoinpoint() == false)
            return false;

        // 2.filter type by classLoaderMatcher
        if(factoryContext.matchClassLoaders(joinpointClassLoader) == false)
            return false;

        // 3.filter type by typeMatcher
        if(factoryContext.matchType(typeDescription.getTypeName()) == false)
            return false;

        return true;
    }

    private List<? extends Advisor> getOrCreateAdvisorPerClassLoader(String typeName, 
            String joinpointClassLoaderId, ClassLoader joinpointClassLoader, JavaModule javaModule, 
            WeaverMetrics weaverMetrics) {
        AdvisorContext advisorContext = factoryContext.createAdvisorContext(joinpointClassLoader, javaModule);

        ClassLoader cacheKey = ClassLoaderUtils.maskNull(joinpointClassLoader);

        if(this.classLoaderAdvisorMap.containsKey(cacheKey)) {
            return this.classLoaderAdvisorMap.get(cacheKey);
        }


        // create Advisors
        List<Advisor> advisors = new ArrayList<>();
        for(AdvisorRepository advisorRepository : this.advisorRepositories) {
            try {
                Advisor advisor = advisorRepository.create(advisorContext);
                if(advisor != null)
                    advisors.add(advisor);
            } catch(Throwable t) {
                LOGGER.warn("Failed to instatiate advisor with '{}'.", advisorRepository, t);
            }
        }

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false)
            LOGGER.info("Loaded {} advisors under '{}' for '{}'.", 
                    advisors.size(), factoryContext.getFactoryName(), joinpointClassLoaderId);
        else
            LOGGER.info("Loaded {} advisors under '{}' for '{}'. {}", 
                    advisors.size(), factoryContext.getFactoryName(), joinpointClassLoaderId,
                    StringUtils.join(advisors, advisor -> advisor.getAdvisorName(), "\n  ", "\n  ", "\n")
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
