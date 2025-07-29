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
import io.gemini.api.classloader.ThreadContext;
import io.gemini.aspectj.weaver.TypeWorld;
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
class DefaultAdvisorFactory implements AdvisorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdvisorFactory.class);


    private final AopContext aopContext;
    private final FactoryContext factoryContext;


    private List<? extends AdvisorSpec> advisorSpecs;
    private List<? extends AdvisorRepository<? extends AdvisorSpec>> advisorRepositories;


    // cache advisors per ClassLoader
    private ConcurrentMap<ClassLoader, List<? extends Advisor>> classLoaderAdvisorMap;


    public DefaultAdvisorFactory(AopContext aopContext, FactoryContext factoryContext) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("^Creating DefaultAdvisorFactory '{}'", factoryName);

        this.aopContext = aopContext;
        this.factoryContext = factoryContext;

        // 1.create AdvisorRepository
        FactoriesContext factoriesContext = factoryContext.getFactoriesContext();

        this.advisorSpecs = this.scanAdvisorSpecs(aopContext, factoryContext, factoriesContext);
        this.advisorRepositories = this.resolveAdvisorRepositories(factoryContext, factoriesContext, this.advisorSpecs);

        // validate AdvisorRepository
        AdvisorContext validationContext = factoryContext.createAdvisorContext(factoryContext.getClassLoader(), null, true);
        this.aopContext.getGlobalTaskExecutor().executeTasks(
                advisorRepositories, 
                repository -> 
                    repository.create(validationContext)    // validate advisor creation
        )
        .count();


        // 2.initialize properties
        this.classLoaderAdvisorMap = new ConcurrentReferenceHashMap<>();

        if(aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create AdvisorFactory '{}'", 
                    (System.nanoTime() - startedAt) / 1e9, factoryName);
    }


    private List<? extends AdvisorSpec> scanAdvisorSpecs(AopContext aopContext, FactoryContext factoryContext, 
            FactoriesContext factoriesContext) {
        long startedAt = System.nanoTime();
        LOGGER.info("^Scanning AdvisorSpecs under '{}'.", factoryContext.getFactoryName());

        final List<AdvisorSpec> advisorSpecs = this.aopContext.getGlobalTaskExecutor().executeTasks(
                factoriesContext.getAdvisorSpecScanners(), 
                specScanner -> specScanner.scan(factoryContext)
        )
        .flatMap( e -> e.stream() )
        .collect( Collectors.toList() )
        ;

        if(aopContext.getDiagnosticLevel().isSimpleEnabled() == false) 
            LOGGER.info("$Took '{}' seconds to load {} AdvisorSpecs under '{}'.", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, 
                    advisorSpecs.size(), 
                    factoryContext.getFactoryName());
        else
            LOGGER.info("$Took '{}' seconds to load {} AdvisorSpecs under '{}'. {}", 
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, 
                    advisorSpecs.size(), 
                    factoryContext.getFactoryName(),
                    StringUtils.join(advisorSpecs, spec -> spec.getAdvisorName(), "\n  ", "\n  ", "\n")
            );

        return advisorSpecs;
    }

    private List<? extends AdvisorRepository<? extends AdvisorSpec>> resolveAdvisorRepositories(
            FactoryContext factoryContext, FactoriesContext factoriesContext,
            List<? extends AdvisorSpec> advisorSpecs) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        LOGGER.info("^Resolving AdvisorSpec under '{}'.", factoryName);

        TaskExecutor taskExecutor = this.aopContext.getGlobalTaskExecutor();
        List<? extends AdvisorRepository<? extends AdvisorSpec>> advisorRepositories = taskExecutor.executeTasks(
                taskExecutor.splitTasks(advisorSpecs), 
                specs -> resolveAdvisorRepositoriesSequentially(factoryContext, factoriesContext, specs)
        )
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

    private List<? extends AdvisorRepository<? extends AdvisorSpec>> resolveAdvisorRepositoriesSequentially(
            FactoryContext factoryContext, FactoriesContext factoriesContext,
            List<? extends AdvisorSpec> advisorSpecs) {
        List<AdvisorRepository<AdvisorSpec>> repositories = new ArrayList<>();
        List<AdvisorRepositoryResolver<AdvisorSpec, AdvisorSpec>> advisorRepositoryResolvers = factoriesContext.getAdvisorRepositoryResolvers();
        for(AdvisorSpec advisorSpec : advisorSpecs) {
            for(AdvisorRepositoryResolver<AdvisorSpec, AdvisorSpec> resolver : advisorRepositoryResolvers) {
                if(resolver.support(advisorSpec) == false)
                    continue;

                List<? extends AdvisorRepository<AdvisorSpec>> resolvedRepositories =  resolver.resolve(advisorSpec, factoryContext);
                if(resolvedRepositories == null) continue;

                for(AdvisorRepository<AdvisorSpec> repository : resolvedRepositories) {
                    if(repository == null) continue;

                    // filter advisorRepositry via advisorMatcher
                    if(factoryContext.getIncludedAdvisorsMatcher().matches(repository.getAdvisorName()) == false)
                        continue;
                    if(factoryContext.getExcludedAdvisorsMatcher().matches(repository.getAdvisorName()) == true)
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
    public Map<String, List<? extends AdvisorSpec>> getAdvisorSpecs() {
        return Collections.singletonMap(this.factoryContext.getFactoryName(), this.advisorSpecs);
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


        // 1.filter type by excluding and including rules in Advisor level
        try {
            if(this.acceptType(typeDescription, joinpointClassLoader, javaModule, weaverMetrics) == false)
                return Collections.emptyMap();
        } finally {
            weaverMetrics.incrTypeAcceptingTime(System.nanoTime() - startedAt);
        }


        // 2.create advisors
        // create advisors per ClassLoader
        AdvisorContext advisorContext = factoryContext.createAdvisorContext(joinpointClassLoader, javaModule);

        List<? extends Advisor> candidateAdvisors = getOrCreateAdvisorPerClassLoader(typeName,  
                joinpointClassLoaderId, joinpointClassLoader, javaModule, advisorContext, weaverMetrics);
        if(CollectionUtils.isEmpty(candidateAdvisors))
            return Collections.emptyMap();


        // 3.match advisors
        try {
            return matchAdvisors(typeDescription, joinpointClassLoader, javaModule, candidateAdvisors, weaverMetrics);
        } finally {
            TypeWorld typeWorld = advisorContext.getTypeWorld();

            typeWorld.releaseCache(typeDescription);
            for(InDefinedShape methodDescription : MethodUtils.getAllMethodDescriptions(typeDescription)) {
                typeWorld.releaseCache(methodDescription);
            }

        }
    }

    private boolean acceptType(TypeDescription typeDescription, ClassLoader joinpointClassLoader, JavaModule javaModule,
            WeaverMetrics weaverMetrics) {
        // 1.check match switch
        if(this.factoryContext.isMatchJoinpoint() == false)
            return false;

        String joinpointClassLoaderName = ClassLoaderUtils.getClassLoaderName(joinpointClassLoader);

        // 2.check included joinpointClassLoaders
        if(factoryContext.getIncludedClassLoadersMatcher().matches(joinpointClassLoaderName) == true) {
            return true;
        }

        // 3.check excluded joinpointClassLoaders
        if(factoryContext.getExcludedClassLoadersMatcher().matches(joinpointClassLoaderName) == true) {
            return false;
        }

        // 4.check included types
        if(factoryContext.createIncludedTypesMatcher(joinpointClassLoader, javaModule).matches(typeDescription) == true) {
            return true;
        }

        // 5.check excluded types
        if(factoryContext.createExcludedTypesMatcher(joinpointClassLoader, javaModule).matches(typeDescription) == true) {
            return false;
        }

        return true;
    }

    private List<? extends Advisor> getOrCreateAdvisorPerClassLoader(String typeName, 
            String joinpointClassLoaderId, ClassLoader joinpointClassLoader, JavaModule javaModule, 
            AdvisorContext advisorContext, WeaverMetrics weaverMetrics) {
        long startedAt = System.nanoTime();

        try {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(joinpointClassLoader);

            if(this.classLoaderAdvisorMap.containsKey(cacheKey)) {
                return this.classLoaderAdvisorMap.get(cacheKey);
            }

            List<Advisor> advisors = new ArrayList<>();
            for(AdvisorRepository<? extends AdvisorSpec> advisorRepository : this.advisorRepositories) {
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
        } finally {
            weaverMetrics.incrAdvisorCreationTime(System.nanoTime() - startedAt);
        }
    }

    private Map<? extends MethodDescription, List<? extends Advisor>> matchAdvisors(
            TypeDescription typeDescription, ClassLoader joinpointClassLoader, JavaModule javaModule, 
            List<? extends Advisor> candidateAdvisors, WeaverMetrics weaverMetrics) {
        long startedAt = System.nanoTime();

        // 1. fast match advisors for given type
        List<Advisor.PointcutAdvisor> pointcutAdvisors = null;
        try {
            startedAt = System.nanoTime();
            weaverMetrics.incrTypeFastMatchingCount();

            pointcutAdvisors = this.doFastMatchAdvisors(typeDescription, joinpointClassLoader, javaModule, 
                    candidateAdvisors, weaverMetrics);
        } finally {
            weaverMetrics.incrTypeFastMatchingTime(System.nanoTime() - startedAt);
        }

        if(CollectionUtils.isEmpty(pointcutAdvisors)) {
            return Collections.emptyMap();
        }


        // 2. match advisors for given type's methods
        try {
            startedAt = System.nanoTime();
            weaverMetrics.incrTypeMatchingCount();

            Map<MethodDescription, List<? extends Advisor>> methodAdvisorsMap = new HashMap<>();
            MethodGraph.Linked methodGraph = null;
            for(InDefinedShape methodDescription : MethodUtils.getAllMethodDescriptions(typeDescription)) {
                List<Advisor> advisorChain = this.doMatchAdvisors(typeDescription, methodDescription, joinpointClassLoader, 
                        pointcutAdvisors, weaverMetrics);
                if(CollectionUtils.isEmpty(advisorChain)) continue;

                // convert matched bridge method to overridden method
                if(methodDescription.isBridge()) {
                    if(methodGraph == null)
                        methodGraph = MethodGraph.Compiler.Default.forJavaHierarchy().compile( (TypeDefinition) typeDescription);

                    MethodGraph.Node locatedNode = methodGraph.locate(methodDescription.asSignatureToken());
                    if(locatedNode != null)
                        methodDescription = locatedNode.getRepresentative().asDefined();
                }

                methodAdvisorsMap.merge(methodDescription, advisorChain, 
                        (oldValue, value) -> CollectionUtils.merge(oldValue, value) 
                );
            }

            return methodAdvisorsMap;
        } finally {
            weaverMetrics.incrTypeMatchingTime(System.nanoTime() - startedAt);
        }
    }

    protected List<Advisor.PointcutAdvisor> doFastMatchAdvisors(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            final List<? extends Advisor> advisors, final WeaverMetrics weaverMetrics) {
        final String joinpointClassLoaderName = ClassLoaderUtils.getClassLoaderName(joinpointClassLoader);

        // warm up aspectj pointcut parsing
        this.filterAdvisorsSequentially(typeDescription, joinpointClassLoaderName, joinpointClassLoader, advisors.subList(0, 1), weaverMetrics);

        TaskExecutor taskExecutor = this.aopContext.getGlobalTaskExecutor();
        return taskExecutor.executeTasks(
                taskExecutor.splitTasks(advisors), 
                subAdvisors -> filterAdvisorsSequentially(typeDescription, joinpointClassLoaderName, joinpointClassLoader, subAdvisors, weaverMetrics)
        )
        .flatMap( e -> e.stream() )
        .collect( Collectors.toList() );
    }

    private List<Advisor.PointcutAdvisor> filterAdvisorsSequentially(TypeDescription typeDescription, String joinpointClassLoaderName, 
            ClassLoader joinpointClassLoader, List<? extends Advisor> advisors, WeaverMetrics weaverMetrics) {
        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

        try {
            ThreadContext.setContextClassLoader(joinpointClassLoader);   // set joinpointClassLoader

            List<Advisor.PointcutAdvisor> pointcutAdvisors = new ArrayList<>(advisors.size());
            for(int i = 0; i < advisors.size(); i++) {
                long startedAt = System.nanoTime();
                Advisor advisor = advisors.get(i);
                try {
                    if(advisor instanceof Advisor.PointcutAdvisor == false)
                        continue;

                    Advisor.PointcutAdvisor pointcutAdvisor = (Advisor.PointcutAdvisor) advisor;
                    Pointcut pointcut = pointcutAdvisor.getPointcut();
                    if(pointcut == null)
                        continue;

                    if(pointcut.getTypeMatcher() == null || pointcut.getTypeMatcher().matches(typeDescription) == false)
                        continue;

                    pointcutAdvisors.add(pointcutAdvisor);
                } finally {
                    long endedAt = System.nanoTime();
                    weaverMetrics.incrAdvisorTypeFastMatchingTime(advisor, (endedAt - startedAt) );
                }
            }
            return pointcutAdvisors;
        } catch(Throwable t) {
            LOGGER.error("Failed to filter advisors {}", advisors, t);
            return Collections.emptyList();
        } finally {
            ThreadContext.setContextClassLoader(existingClassLoader);   // set joinpointClassLoader
        }
    }

    protected List<Advisor> doMatchAdvisors(TypeDescription typeDescription, InDefinedShape methodDescription, 
            ClassLoader joinpointClassLoader, List<Advisor.PointcutAdvisor> advisors, WeaverMetrics weaverMetrics) {
        // TODO: methodDescription.isNative() || 
        if(methodDescription.isNative() || methodDescription.isAbstract())
            return null;

        List<Advisor> candidates = new LinkedList<>();

        for(Advisor.PointcutAdvisor pointcutAdvisor : advisors) {
            Pointcut pointcut = pointcutAdvisor.getPointcut();
            if(pointcut.getMethodMatcher() != null) {
                long startedAt = System.nanoTime();

                boolean matches = false;
                try {
                    matches = pointcut.getMethodMatcher().matches(methodDescription);
                } catch(Throwable t) {
                    LOGGER.info("Failed to match joinpoint with pointcut. \n  Joinpoitn: {} \n  Advisor: {} \n  ClassLoader: {} \n  Error reason: {} \n",
                            MethodUtils.getMethodSignature(methodDescription), pointcutAdvisor, joinpointClassLoader, t.getMessage(), t);
                }

                long endedAt = System.nanoTime();
                weaverMetrics.incrAdvisorTypeMatchingTime(pointcutAdvisor, (endedAt - startedAt) );

                if(matches == false) {
                    continue;
                }
            }

            candidates.add(pointcutAdvisor);
        }

        return candidates;
    }


    @Override
    public void close() throws IOException {
        this.factoryContext.close();
    }
}
