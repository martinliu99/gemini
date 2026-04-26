/*
 * Copyright © 2023 - present, the original author or authors. All Rights Reserved.
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.Advisor.PointcutAdvisor;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.factory.support.AdvisorCreator;
import io.gemini.aop.factory.support.AdvisorSpec;
import io.gemini.aop.factory.support.AdvisorSpecScanner;
import io.gemini.api.aop.Pointcut;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.pool.TypeResolutionInspector;
import io.gemini.core.pool.TypeResolutionInspector.ResolutionLevel;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.utility.JavaModule;

/**
 * Default implementation of {@link AdvisorFactory} for a single aspect application.
 * <p>
 * Scans and parses all {@link AdvisorSpec} instances from the aspect application, then
 * creates and caches {@link io.gemini.aop.Advisor} instances per target class loader.
 * 
 * Advisor matching is performed in two phases:
 * <ol>
 *   <li>Fast type-level matching via {@link io.gemini.api.aop.Pointcut#getTypeMatcher()}</li>
 *   <li>Method-level matching via {@link io.gemini.api.aop.Pointcut#getMethodMatcher()}</li>
 * </ol>
 * Inner classes {@link Diagnostic} and {@link TyepResolutionDetector} extend this class
 * to add performance metrics collection and type-resolution tracking respectively.
 * </p>
 *
 * @author   martin.liu
 */
class DefaultAdvisorFactory implements AdvisorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdvisorFactory.class);


    private final AopContext aopContext;
    private final FactoryContext factoryContext;

    private final Collection<? extends AdvisorSpec> advisorSpecs;
    private final AdvisorCreator advisorCreator;

    // cache advisors per ClassLoader
    private final ConcurrentMap<ClassLoader, List<? extends Advisor>> advisorPerClassLoaderMap;


    public DefaultAdvisorFactory(FactoryContext factoryContext) {
        long startedAt = System.nanoTime();

        this.aopContext = factoryContext.getAopContext();
        this.factoryContext = factoryContext;

        String factoryName = factoryContext.getFactoryName();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating DefaultAdvisorFactory '{}'", factoryName);


        // 1.resolve AdvisorRepository
        this.advisorSpecs = new AdvisorSpecScanner.Compound(factoryContext).scan(factoryContext);
        this.advisorCreator = new AdvisorCreator.Compound(factoryContext);

        this.validateAdvisorCreation();


        // 2.initialize properties
        this.advisorPerClassLoaderMap = new ConcurrentReferenceHashMap<>();


        if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create AdvisorFactory '{}'", 
                    (System.nanoTime() - startedAt) / 1e9, factoryName);
    }

    /**
     * Validates all advisor specs at startup by attempting to create advisors against the
     * factory's own class loader. Removes any specs that produce {@link Advisor.IllegalAdvisor} due to
     * possible pointcut expression syntax error.
     */
    private void validateAdvisorCreation() {
        this.doCreateAdvisors(factoryContext.getClassLoader(), null, true)
        .stream()
        .filter( a -> {
            if (a instanceof Advisor.IllegalAdvisor == false)
                return true;

            Advisor.IllegalAdvisor illegalAdvisor = (Advisor.IllegalAdvisor) a;
            advisorSpecs.remove( illegalAdvisor.getAdvisorSpec() );

            return false;
        })
        .collect( Collectors.toList() );
    }

    /**
     * Returns the central {@link AopContext} for this factory.
     *
     * @return the AOP context
     */
    protected AopContext getAopContext() {
        return aopContext;
    }

    /**
     * Returns the {@link FactoryContext} for this aspect application.
     *
     * @return the factory context
     */
    protected FactoryContext getFactoryContext() {
        return factoryContext;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Integer> getAdvisorSpecNum() {
        return Collections.singletonMap(this.factoryContext.getFactoryName(), this.advisorSpecs.size());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(
            TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule) {
        // 1.get advisors per ClassLoader
        List<? extends Advisor> candidateAdvisors = this.advisorPerClassLoaderMap.computeIfAbsent(
                ClassLoaderUtils.maskNull(targetClassLoader), 
                key ->  doCreateAdvisors( targetClassLoader, targetModule, false )
        );
        if (CollectionUtils.isEmpty(candidateAdvisors))
            return Collections.emptyMap();


        try {
            // 2.fast match advisors for given type
            List<Advisor.PointcutAdvisor> pointcutAdvisors = doFastMatchAdvisors(
                    targetType, 
                    targetClassLoader, 
                    targetModule, 
                    candidateAdvisors
            );
            // ignore synthetic class
            if (CollectionUtils.isEmpty(pointcutAdvisors) || targetType.isSynthetic())
                return Collections.emptyMap();


            // 3.match advisors for given type's methods
            return doMatchAdvisors(
                    targetType, 
                    targetClassLoader, 
                    targetModule,
                    pointcutAdvisors
            );
        } finally {
            TypeWorld typeWorld = factoryContext.getTypeWorld();
            if (typeWorld != null && typeWorld instanceof TypeWorld.CacheResolutionFacade) {
                ((TypeWorld.CacheResolutionFacade) typeWorld).releaseCache(targetType);
            }
        }
    }

    /**
     * Creates advisors for all specs using the given {@link AdvisorContext}, executing tasks
     * in parallel if the global task executor is configured for parallel mode.
     *
     * @param advisorContext    the per-class-loader advisor context
     * @param targetClassLoader the target class loader (used for thread context)
     * @return list of successfully created advisors
     */
    protected List<? extends Advisor> doCreateAdvisors(ClassLoader targetClassLoader, 
            JavaModule targetJavaModule, boolean validCreation) {
        long startedAt = System.nanoTime();

        String factoryName = factoryContext.getFactoryName();
        if (validCreation == false && LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating Advisors via AdvisorCreator under '{}' for '{}', \n"
                    + "  {} \n", 
                    factoryName, targetClassLoader,
                    advisorCreator
            );


        AdvisorContext advisorContext = validCreation 
                ? factoryContext.createAdvisorContext(targetClassLoader, null, true)
                : factoryContext.createAdvisorContext(targetClassLoader, targetJavaModule);

        AopContext aopContext = factoryContext.getAopContext();
        List<Advisor> advisors = aopContext.getGlobalTaskExecutor().executeTasks(
                advisorSpecs, 
                advisorSpec -> advisorCreator.create(advisorContext, advisorSpec),
                result -> {
                    ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
                    try {
                        ThreadContext.setContextClassLoader(targetClassLoader);   // set targetClassLoader
                        return result.get();
                    } finally {
                        ThreadContext.setContextClassLoader(existingClassLoader);
                    }
                }
        )
        .stream()
        .filter( e -> e != null)
        .collect( Collectors.toList() );


        if (validCreation == false && LOGGER.isInfoEnabled()) {
            if (aopContext.getDiagnosticLevel().isDebugEnabled() && advisors.size() > 0) 
                LOGGER.info("$Took '{}' seconds to create {} Advisors under '{}' for '{}', \n"
                        + "  {} \n", 
                        (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisors.size(), factoryName, targetClassLoader,
                        StringUtils.join(advisors, Advisor::toString, "\n  ")
                );
            else if (aopContext.getDiagnosticLevel().isSimpleEnabled()) 
                LOGGER.info("$Took '{}' seconds to create {} Advisors under '{}' for '{}'. ", 
                        (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, 
                        advisors.size(), factoryName, targetClassLoader
                );
        }

        return advisors;
    }


    /**
     * Performs fast type-level matching: filters the candidate advisor list to those whose
     * {@link Pointcut#getTypeMatcher()} matches the given target type.
     *
     * @param targetType        the type being loaded
     * @param targetClassLoader the class loader loading the type
     * @param targetModule      the Java module of the type
     * @param advisors          the full candidate advisor list
     * @return advisors whose type matcher matched
     */
    protected List<Advisor.PointcutAdvisor> doFastMatchAdvisors(TypeDescription targetType, 
            ClassLoader targetClassLoader, JavaModule targetModule, 
            List<? extends Advisor> advisors) {
        // check typeMatcher of Advisors
        List<Advisor.PointcutAdvisor> matchedAdvisors = new ArrayList<>();
        for (Advisor advisor : advisors) {
            PointcutAdvisor pointcutAdvisor = doFastMatchAdvisor(targetType, advisor);
            if (pointcutAdvisor == null)
                continue;

            matchedAdvisors.add(pointcutAdvisor);
        }

        return matchedAdvisors;
    }

    /**
     * Tests a single advisor's type matcher against the target type.
     *
     * @param targetType the type being loaded
     * @param advisor    the advisor to test
     * @return the advisor cast to {@link Advisor.PointcutAdvisor} if matched, or {@code null}
     */
    protected Advisor.PointcutAdvisor doFastMatchAdvisor(TypeDescription targetType, Advisor advisor) {
        try {
            if (advisor instanceof Advisor.PointcutAdvisor == false)
                return null;

            Advisor.PointcutAdvisor pointcutAdvisor = (Advisor.PointcutAdvisor) advisor;
            Pointcut pointcut = pointcutAdvisor.getPointcut();
            if (pointcut == null || pointcut.getTypeMatcher() == null)
                return null;

            if (pointcut.getTypeMatcher().matches(targetType) == false)
                return null;

            return pointcutAdvisor;
        } catch (Throwable t) {
            LOGGER.error("Could not filter advisor {}", advisor, t);

            Throwables.throwIfRequired(t);
            return null;
        }
    }

    /**
     * Performs method-level matching: for each method in the target type, tests all
     * type-matched advisors and builds the method-to-advisor map.
     *
     * @param targetType        the type being instrumented
     * @param targetClassLoader the class loader loading the type
     * @param targetModule      the Java module of the type
     * @param pointcutAdvisors  advisors that passed the fast type-level match
     * @return map of method to matched advisors
     */
    protected Map<MethodDescription, List<? extends Advisor>> doMatchAdvisors(
            TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule, 
            List<Advisor.PointcutAdvisor> pointcutAdvisors) {
        Map<MethodDescription, List<? extends Advisor>> methodAdvisorsMap = new LinkedHashMap<>();
        for (InDefinedShape targetMethod : MethodUtils.getAllMethodDescriptions(targetType)) {
            // ignore synthetic method?
            if (targetMethod.isAbstract()
                    || (targetMethod.isSynthetic() && !targetMethod.isBridge()) )
                continue;

            List<Advisor> candidateAdvisors = new LinkedList<>();
            for (Advisor.PointcutAdvisor pointcutAdvisor : pointcutAdvisors) {
                if (doMatchAdvisor(targetType, targetClassLoader, targetModule, 
                        targetMethod, pointcutAdvisor) == false)
                    continue;

                candidateAdvisors.add(pointcutAdvisor);
            }

            if (CollectionUtils.isEmpty(candidateAdvisors)) 
                continue;


            // convert matched bridge method to overridden method
            MethodGraph.Linked methodGraph = null;
            if (targetMethod.isBridge()) {
                if (methodGraph == null)
                    methodGraph = MethodGraph.Compiler.Default.forJavaHierarchy().compile( (TypeDefinition) targetType);

                MethodGraph.Node locatedNode = methodGraph.locate(targetMethod.asSignatureToken());
                if (locatedNode != null)
                    targetMethod = locatedNode.getRepresentative().asDefined();
            }

            methodAdvisorsMap.merge(targetMethod, candidateAdvisors, 
                    (oldValue, value) -> CollectionUtils.merge(oldValue, value) 
            );
        }

        return methodAdvisorsMap;
    }


    /**
     * Tests a single advisor's method matcher against the target method.
     *
     * @param targetType        the declaring type
     * @param targetClassLoader the class loader
     * @param targetModule      the Java module
     * @param targetMethod      the method to test
     * @param pointcutAdvisor   the advisor to test
     * @return {@code true} if the advisor's method matcher matched
     */
    protected boolean doMatchAdvisor(TypeDescription targetType, 
            ClassLoader targetClassLoader, JavaModule targetModule, 
            InDefinedShape targetMethod, Advisor.PointcutAdvisor pointcutAdvisor) {
        try {
            if (pointcutAdvisor.getPointcut().getMethodMatcher().matches(targetMethod) == false)
                return false;

            return true;
        } catch (Throwable t) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Could not match joinpoint with pointcut. \n"
                        + "  Joinpoint: {} \n"
                        + "  Advisor: {} \n"
                        + "  ClassLoader: {} \n"
                        + "  Error reason: {} \n",
                        MethodUtils.getMethodSignature(targetMethod), 
                        pointcutAdvisor.getAdvisorName(), 
                        targetClassLoader, 
                        t.getMessage(), 
                        t
                );

            Throwables.throwIfRequired(t);
            return false;
        }
    }


    /**
     * {@inheritDoc}
     * <p>Closes the underlying {@link FactoryContext}.</p>
     */
    @Override
    public void close() throws IOException {
        this.factoryContext.close();
    }


    /**
     * Extends {@link DefaultAdvisorFactory} to collect per-type weaving metrics
     * (advisor creation time, fast-match time, method-match time, transformation time).
     */
    static class Diagnostic extends DefaultAdvisorFactory {

        private static final Logger LOGGER = LoggerFactory.getLogger(Diagnostic.class);


        private final AopMetrics aopMetrics;


        public Diagnostic(FactoryContext factoryContext) {
            super(factoryContext);

            this.aopMetrics = getAopContext().getAopMetrics();
        }


        /**
         * Returns the {@link AopMetrics} instance for recording per-type timing.
         *
         * @return the AOP metrics
         */
        protected AopMetrics getAopMetrics() {
            return aopMetrics;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(
                TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule) {
            // diagnostic log
            String targetTypeName = targetType.getTypeName();
            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                LOGGER.info("Getting Advisors for type '{}' loaded by ClassLoader '{}' from AdvisorFactory '{}'.", 
                        targetTypeName, targetClassLoader, getFactoryContext().getFactoryName());

            // get advisors per AdvisorFactory
            Map<? extends MethodDescription, List<? extends Advisor>> advisorMap = 
                    super.getAdvisors(targetType, targetClassLoader, targetModule);

            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName)) {
                if (advisorMap.size() == 0)
                    LOGGER.info("Did not get Advisors for type '{}' loaded by ClassLoader '{}' from AdvisorFactory '{}'.",
                            targetTypeName, targetClassLoader, getFactoryContext().getFactoryName()
                    );
                else
                    LOGGER.info("Got Advisors for type '{}' in AdvisorFactory, \n"
                            + "  AdvisorFactory: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  {} ",
                            targetTypeName, 
                            getFactoryContext().getFactoryName(),
                            targetClassLoader, 
                            StringUtils.join(
                                    advisorMap.entrySet(), 
                                    methodAdvisorEntry -> 
                                        new StringBuilder("Method: ")
                                            .append( MethodUtils.getMethodSignature( methodAdvisorEntry.getKey() ) )
                                            .append("\n  Advisors: ")
                                            .append( StringUtils.join(methodAdvisorEntry.getValue(), Advisor::toString, "\n    ", "\n    ", "\n") ),
                                    "\n  "
                            )
                    );
            }

            return advisorMap;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected List<? extends Advisor> doCreateAdvisors(ClassLoader targetClassLoader, 
                JavaModule targetJavaModule, boolean validCreation) {
            long startedAt = System.nanoTime();
            List<? extends Advisor> advisors = Collections.emptyList();

            try {
                return (advisors = super.doCreateAdvisors(targetClassLoader, targetJavaModule, validCreation));
            } finally {
                if (validCreation == false) {
                    AopMetrics.currentTypeMetrics().incrAdvisorCreationCount(advisors.size());
                    AopMetrics.currentTypeMetrics().incrAdvisorCreationTime(System.nanoTime() - startedAt);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected List<Advisor.PointcutAdvisor> doFastMatchAdvisors(
                TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule, 
                List<? extends Advisor> advisors) {
            long startedAt = System.nanoTime();

            try {
                return super.doFastMatchAdvisors(
                        targetType, 
                        targetClassLoader, 
                        targetModule,
                        advisors
                );
            } finally {
                AopMetrics.currentTypeMetrics().incrTypeFastMatchingTime(System.nanoTime() - startedAt);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Map<MethodDescription, List<? extends Advisor>> doMatchAdvisors(
                TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule, 
                List<Advisor.PointcutAdvisor> pointcutAdvisors) {
            long startedAt = System.nanoTime();

            try {
                return super.doMatchAdvisors(
                        targetType, 
                        targetClassLoader, 
                        targetModule,
                        pointcutAdvisors
                );
            } finally {
                AopMetrics.currentTypeMetrics().incrTypeMatchingTime(System.nanoTime() - startedAt);
            }
        }
    }


    /**
     * Extends {@link Diagnostic} to track which advisors triggered type resolution
     * (i.e., required loading of additional type information) during fast-match and method-match phases.
     * Resolution levels are recorded per advisor and reported in the startup metrics summary.
     */
    static class TyepResolutionDetector extends Diagnostic {

        private final ConcurrentMap<String, ConcurrentMap<String, ResolutionLevel>> typeAdvisorTypeResolutionLevels;


        public TyepResolutionDetector(FactoryContext factoryContext) {
            super(factoryContext);

            this.typeAdvisorTypeResolutionLevels = new ConcurrentHashMap<>();
        }


        /**
         * Returns the type resolution level map for the given target type,
         * creating it if it doesn't exist yet.
         *
         * @param targetType the type being matched
         * @return the per-advisor resolution level map
         */
        protected ConcurrentMap<String, ResolutionLevel> getAdvisorTypeResolutionLevels(TypeDescription targetType) {
            return typeAdvisorTypeResolutionLevels.computeIfAbsent(
                    targetType.getTypeName(), 
                    key -> new ConcurrentHashMap<>()
            );
        }

        /**
         * Removes and returns the type resolution level map for the given target type.
         * Called after all advisors have been matched to free memory.
         *
         * @param targetType the type that was matched
         * @return the removed resolution level map, or {@code null} if absent
         */
        protected ConcurrentMap<String, ResolutionLevel> removeAdvisorTypeResolutionLevels(TypeDescription targetType) {
            return typeAdvisorTypeResolutionLevels.remove(targetType.getTypeName());
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(
                TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule) {
            try {
                return super.getAdvisors(targetType, 
                        targetClassLoader, targetModule);
            } finally {
                AopMetrics.currentTypeMetrics().addAdvisorResolutuonLevelMap(
                        removeAdvisorTypeResolutionLevels(targetType) );
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Advisor.PointcutAdvisor doFastMatchAdvisor(TypeDescription targetType, Advisor advisor) {
            // match pointcut of advisor and record type resolution info
            TypeResolutionInspector typeResolutionInspector = targetType instanceof TypeResolutionInspector
                    ? (TypeResolutionInspector) targetType : null;

            try {
                if (typeResolutionInspector != null)
                    typeResolutionInspector.resetInspection();

                return super.doFastMatchAdvisor(targetType, advisor);
            } finally {
                // record type resolution information
                ResolutionLevel resolutionLevel = null;
                if (typeResolutionInspector != null) {
                    resolutionLevel = typeResolutionInspector.getResolutionLevel();

                    Map<String, ResolutionLevel> advisorTypeResolutionLevels = 
                            getAdvisorTypeResolutionLevels(targetType);
                    if (ResolutionLevel.NO_RESOLUTION != resolutionLevel)
                        advisorTypeResolutionLevels.put(advisor.getAdvisorName(), resolutionLevel);
                }
            }

        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doMatchAdvisor(TypeDescription targetType, 
                ClassLoader targetClassLoader, JavaModule targetModule, 
                InDefinedShape targetMethod, Advisor.PointcutAdvisor pointcutAdvisor) {
            if (super.doMatchAdvisor(targetType,
                    targetClassLoader, targetModule, targetMethod, pointcutAdvisor) == false)
                return false;

            // exclude Advisor
            TypeResolutionInspector typeResolutionInspector = targetType instanceof TypeResolutionInspector
                    ? (TypeResolutionInspector) targetType : null;
            if (typeResolutionInspector != null) {
                Map<String, ResolutionLevel> advisorTypeResolutionLevels = 
                        getAdvisorTypeResolutionLevels(targetType);
                advisorTypeResolutionLevels.remove(pointcutAdvisor.getAdvisorName());
            }

            return true;
        }
    }
}
