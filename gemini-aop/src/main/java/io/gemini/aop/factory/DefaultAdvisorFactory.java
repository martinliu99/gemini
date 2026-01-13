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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.Advisor.PointcutAdvisor;
import io.gemini.aop.AdvisorFactory;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.factory.support.AdvisorRepository;
import io.gemini.aop.factory.support.AdvisorRepositoryResolver;
import io.gemini.aop.factory.support.AdvisorSpecScanner;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Pointcut;
import io.gemini.aspectj.weaver.TypeWorld;
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
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
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

    private final Map<String, ? extends AdvisorSpec> advisorSpecMap;
    private final Collection<? extends AdvisorRepository> advisorRepositories;

    // cache advisors per ClassLoader
    private final Map<ClassLoader, ElementMatcher<String>> typeMatcherPerClassLoaderMap;
    private final ConcurrentMap<ClassLoader, List<? extends Advisor>> advisorPerClassLoaderMap;


    public DefaultAdvisorFactory(FactoryContext factoryContext) {
        long startedAt = System.nanoTime();

        this.aopContext = factoryContext.getAopContext();
        this.factoryContext = factoryContext;

        String factoryName = factoryContext.getFactoryName();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating DefaultAdvisorFactory '{}'", factoryName);

        // 1.resolve AdvisorRepository
        this.advisorSpecMap = AdvisorSpecScanner.scanSpecs(factoryContext);

        AdvisorContext validationContext = factoryContext.createAdvisorContext(factoryContext.getClassLoader(), null, false, true);
        this.advisorRepositories = AdvisorRepositoryResolver.resolveRepositories(factoryContext, validationContext, advisorSpecMap.values());


        // 2.initialize properties
        this.typeMatcherPerClassLoaderMap = new ConcurrentReferenceHashMap<>();
        this.advisorPerClassLoaderMap = new ConcurrentReferenceHashMap<>();


        if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create AdvisorFactory '{}'", 
                    (System.nanoTime() - startedAt) / 1e9, factoryName);
    }

    protected AopContext getAopContext() {
        return aopContext;
    }

    protected FactoryContext getFactoryContext() {
        return factoryContext;
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
    public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule) {
        // 1.get advisors per ClassLoader
        ElementMatcher<String> typeMatcher = typeMatcherPerClassLoaderMap.computeIfAbsent(
                ClassLoaderUtils.maskNull(joinpointClassLoader), 
                key -> doCreateTypeMatcher(joinpointClassLoader)
        );

        boolean joinpointClassLoaderAccepted = ElementMatchers.any().equals(typeMatcher);
        List<? extends Advisor> candidateAdvisors = this.advisorPerClassLoaderMap.computeIfAbsent(
                ClassLoaderUtils.maskNull(joinpointClassLoader), 
                key ->  doCreateAdvisors( joinpointClassLoader, javaModule, joinpointClassLoaderAccepted )
        );
        if (CollectionUtils.isEmpty(candidateAdvisors))
            return Collections.emptyMap();


        try {
            // 2.fast match advisors for given type
            List<Advisor.PointcutAdvisor> pointcutAdvisors = doFastMatchAdvisors(
                    typeDescription, 
                    joinpointClassLoader, 
                    javaModule, 
                    typeMatcher,
                    candidateAdvisors
            );
            // ignore synthetic class
            if (CollectionUtils.isEmpty(pointcutAdvisors) || typeDescription.isSynthetic())
                return Collections.emptyMap();


            // 3.match advisors for given type's methods
            return doMatchAdvisors(
                    typeDescription, 
                    joinpointClassLoader, 
                    javaModule,
                    pointcutAdvisors
            );
        } finally {
            TypeWorld typeWorld = factoryContext.getTypeWorld();
            if (typeWorld != null && typeWorld instanceof TypeWorld.CacheResolutionFacade) {
                ((TypeWorld.CacheResolutionFacade) typeWorld).releaseCache(typeDescription);
            }
        }
    }

    protected ElementMatcher<String> doCreateTypeMatcher(ClassLoader joinpointClassLoader) {
        if (this.factoryContext.getFactoryClassLoaderTypeMatchers().size() == 0)
            return ElementMatchers.any();

        List<ElementMatcher<? super String>> typeMatchers = new ArrayList<>();
        ElementMatcher<String> typeMatcher = null;
        for (Entry<ElementMatcher<ClassLoader>, ElementMatcher<String>> entry : this.factoryContext.getFactoryClassLoaderTypeMatchers().entrySet()) {
            if(entry.getKey().matches(joinpointClassLoader) == false)
                continue;

            typeMatcher = entry.getValue();
            typeMatchers.add(typeMatcher);
        }

        return typeMatchers.size() == 0
                ? ElementMatchers.none()
                : typeMatchers.size() == 1
                        ? typeMatcher
                        : new ElementMatcher.Junction.Disjunction<>(typeMatchers);
    }

    protected List<? extends Advisor> doCreateAdvisors(ClassLoader joinpointClassLoader, JavaModule javaModule, 
            boolean joinpointClassLoaderAccepted) {
        return AdvisorRepository.createAdvisors(
                joinpointClassLoader, 
                factoryContext.createAdvisorContext(joinpointClassLoader, javaModule, joinpointClassLoaderAccepted), 
                advisorRepositories
        );
    }


    protected List<Advisor.PointcutAdvisor> doFastMatchAdvisors(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            ElementMatcher<String> typeMatcher, 
            List<? extends Advisor> advisors) {
        // check typeMatcher of AdvisorFactory
        boolean typeAccepted = doAcceptType(typeDescription, typeMatcher);

        // check typeMatcher of Advisors
        List<Advisor.PointcutAdvisor> matchedAdvisors = new ArrayList<>();
        for (Advisor advisor : advisors) {
            PointcutAdvisor pointcutAdvisor = doFastMatchAdvisor(typeDescription, typeAccepted, advisor);
            if (pointcutAdvisor == null)
                continue;

            matchedAdvisors.add(pointcutAdvisor);
        }

        return matchedAdvisors;
    }

    protected boolean doAcceptType(TypeDescription typeDescription, ElementMatcher<String> typeMatcher) {
        try {
            return typeMatcher.matches(typeDescription.getTypeName());
        } catch (Exception e) {}

        return false;
    }


    protected Advisor.PointcutAdvisor doFastMatchAdvisor(TypeDescription typeDescription, 
            boolean typeAccepted, Advisor advisor) {
        try {
            if (advisor instanceof Advisor.PointcutAdvisor == false)
                return null;

            Advisor.PointcutAdvisor pointcutAdvisor = (Advisor.PointcutAdvisor) advisor;
            Pointcut pointcut = pointcutAdvisor.getPointcut();
            if (pointcut == null || pointcut.getTypeMatcher() == null)
                return null;


            // check factory TypeMatcher matching result
            if (typeAccepted == false
                    && advisorSpecMap.get( advisor.getAdvisorName() ).isInheritTypeMatcher() )
                return null;

            if (pointcut.getTypeMatcher().matches(typeDescription) == false)
                return null;

            return pointcutAdvisor;
        } catch (Throwable t) {
            LOGGER.error("Could not filter advisor {}", advisor, t);

            Throwables.throwIfRequired(t);
            return null;
        }
    }

    protected Map<MethodDescription, List<? extends Advisor>> doMatchAdvisors(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            List<Advisor.PointcutAdvisor> pointcutAdvisors) {
        Map<MethodDescription, List<? extends Advisor>> methodAdvisorsMap = new LinkedHashMap<>();
        for (InDefinedShape methodDescription : MethodUtils.getAllMethodDescriptions(typeDescription)) {
            // ignore synthetic method?
            if (methodDescription.isNative() || methodDescription.isAbstract()
                    || (methodDescription.isSynthetic() && !methodDescription.isBridge()) )
                continue;

            List<Advisor> candidateAdvisors = new LinkedList<>();
            for (Advisor.PointcutAdvisor pointcutAdvisor : pointcutAdvisors) {
                if (doMatchAdvisor(typeDescription, joinpointClassLoader, javaModule, methodDescription, pointcutAdvisor) == false)
                    continue;

                candidateAdvisors.add(pointcutAdvisor);
            }

            if (CollectionUtils.isEmpty(candidateAdvisors)) 
                continue;


            // convert matched bridge method to overridden method
            MethodGraph.Linked methodGraph = null;
            if (methodDescription.isBridge()) {
                if (methodGraph == null)
                    methodGraph = MethodGraph.Compiler.Default.forJavaHierarchy().compile( (TypeDefinition) typeDescription);

                MethodGraph.Node locatedNode = methodGraph.locate(methodDescription.asSignatureToken());
                if (locatedNode != null)
                    methodDescription = locatedNode.getRepresentative().asDefined();
            }

            methodAdvisorsMap.merge(methodDescription, candidateAdvisors, 
                    (oldValue, value) -> CollectionUtils.merge(oldValue, value) 
            );
        }

        return methodAdvisorsMap;
    }


    protected boolean doMatchAdvisor(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            InDefinedShape methodDescription, Advisor.PointcutAdvisor pointcutAdvisor) {
        try {
            if (pointcutAdvisor.getPointcut().getMethodMatcher().matches(methodDescription) == false)
                return false;

            return true;
        } catch (Throwable t) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Could not match joinpoint with pointcut. \n"
                        + "  Joinpoitn: {} \n"
                        + "  Advisor: {} \n"
                        + "  ClassLoader: {} \n"
                        + "  Error reason: {} \n",
                        MethodUtils.getMethodSignature(methodDescription), 
                        pointcutAdvisor, 
                        joinpointClassLoader, 
                        t.getMessage(), 
                        t
                );

            Throwables.throwIfRequired(t);
            return false;
        }
    }


    @Override
    public void close() throws IOException {
        this.factoryContext.close();
    }


    static class Diagnostic extends DefaultAdvisorFactory {

        private static final Logger LOGGER = LoggerFactory.getLogger(Diagnostic.class);


        private final AopMetrics aopMetrics;


        public Diagnostic(FactoryContext factoryContext) {
            super(factoryContext);

            this.aopMetrics = getAopContext().getAopMetrics();
        }


        protected AopMetrics getAopMetrics() {
            return aopMetrics;
        }


        @Override
        public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(TypeDescription typeDescription, 
                ClassLoader joinpointClassLoader, JavaModule javaModule) {
            // diagnostic log
            String typeName = typeDescription.getTypeName();
            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(typeName))
                LOGGER.info("Getting Advisors for type '{}' loaded by ClassLoader '{}' from AdvisorFactory '{}'.", 
                        typeName, joinpointClassLoader, getFactoryContext().getFactoryName());

            // get advisors per AdvisorFactory
            Map<? extends MethodDescription, List<? extends Advisor>> advisorMap = 
                    super.getAdvisors(typeDescription, joinpointClassLoader, javaModule);

            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(typeName)) {
                if (advisorMap.size() == 0)
                    LOGGER.info("Did not get Advisors for type '{}' loaded by ClassLoader '{}' from AdvisorFactory '{}'.",
                            typeName, joinpointClassLoader, getFactoryContext().getFactoryName()
                    );
                else
                    LOGGER.info("Got Advisors for type '{}' in AdvisorFactory, \n"
                            + "  AdvisorFactory: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  {} ",
                            typeName, 
                            getFactoryContext().getFactoryName(),
                            joinpointClassLoader, 
                            StringUtils.join(
                                    advisorMap.entrySet(), 
                                    methodAdvisorEntry -> 
                                        new StringBuilder("Method: ")
                                            .append( MethodUtils.getMethodSignature( methodAdvisorEntry.getKey() ) )
                                            .append("\n  Advices: ")
                                            .append( StringUtils.join(methodAdvisorEntry.getValue(), Advisor::getAdvisorName, "\n    ", "\n    ", "\n") ),
                                    "\n  "
                            )
                    );
            }

            return advisorMap;
        }


        @Override
        protected ElementMatcher<String> doCreateTypeMatcher(ClassLoader joinpointClassLoader) {
            long startedAt = System.nanoTime();

            try {
                return super.doCreateTypeMatcher(joinpointClassLoader);
            } finally {
                AopMetrics.currentTypeMetrics().incrTypeAcceptingTime(System.nanoTime() - startedAt);
            }
        }

        @Override
        protected List<? extends Advisor> doCreateAdvisors(ClassLoader joinpointClassLoader, JavaModule javaModule, 
                boolean joinpointClassLoaderAccepted) {
            long startedAt = System.nanoTime();
            List<? extends Advisor> advisors = Collections.emptyList();

            try {
                return (advisors = super.doCreateAdvisors(joinpointClassLoader, javaModule, joinpointClassLoaderAccepted));
            } finally {
                AopMetrics.currentTypeMetrics().incrAdvisorCreationCount(advisors.size());
                AopMetrics.currentTypeMetrics().incrAdvisorCreationTime(System.nanoTime() - startedAt);
            }
        }

        @Override
        protected List<Advisor.PointcutAdvisor> doFastMatchAdvisors(TypeDescription typeDescription, 
                ClassLoader joinpointClassLoader, JavaModule javaModule, 
                ElementMatcher<String> typeMatcher, 
                List<? extends Advisor> advisors) {
            long startedAt = System.nanoTime();

            try {
                return super.doFastMatchAdvisors(
                        typeDescription, 
                        joinpointClassLoader, 
                        javaModule,
                        typeMatcher,
                        advisors
                );
            } finally {
                AopMetrics.currentTypeMetrics().incrTypeFastMatchingTime(System.nanoTime() - startedAt);
            }
        }

        @Override
        protected boolean doAcceptType(TypeDescription typeDescription, ElementMatcher<String> typeMatcher) {
            long startedAt = System.nanoTime();

            try {
                return super.doAcceptType(typeDescription, typeMatcher);
            } finally {
                AopMetrics.currentTypeMetrics().incrTypeAcceptingTime(System.nanoTime() - startedAt);
            }
        }

        @Override
        protected Map<MethodDescription, List<? extends Advisor>> doMatchAdvisors(TypeDescription typeDescription, 
                ClassLoader joinpointClassLoader, JavaModule javaModule, 
                List<Advisor.PointcutAdvisor> pointcutAdvisors) {
            long startedAt = System.nanoTime();

            try {
                return super.doMatchAdvisors(
                        typeDescription, 
                        joinpointClassLoader, 
                        javaModule,
                        pointcutAdvisors
                );
            } finally {
                AopMetrics.currentTypeMetrics().incrTypeMatchingTime(System.nanoTime() - startedAt);
            }
        }
    }


    static class TyepResolutionDetector extends Diagnostic {

        private final ConcurrentMap<String, ConcurrentMap<String, ResolutionLevel>> typeAdvisorTypeResolutionLevels;


        public TyepResolutionDetector(FactoryContext factoryContext) {
            super(factoryContext);

            this.typeAdvisorTypeResolutionLevels = new ConcurrentHashMap<>();
        }


        protected ConcurrentMap<String, ResolutionLevel> getAdvisorTypeResolutionLevels(
                TypeDescription typeDescription) {
            return typeAdvisorTypeResolutionLevels.computeIfAbsent(
                    typeDescription.getTypeName(), 
                    key -> new ConcurrentHashMap<>()
            );
        }

        protected ConcurrentMap<String, ResolutionLevel> removeAdvisorTypeResolutionLevels(
                TypeDescription typeDescription) {
            return typeAdvisorTypeResolutionLevels.remove(typeDescription.getTypeName());
        }


        @Override
        public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(
                TypeDescription typeDescription, ClassLoader joinpointClassLoader, JavaModule javaModule) {
            try {
                return super.getAdvisors(typeDescription, joinpointClassLoader, javaModule);
            } finally {
                AopMetrics.currentTypeMetrics().addAdvisorResolutuonLevelMap(
                        removeAdvisorTypeResolutionLevels(typeDescription) );
            }
        }

        @Override
        protected Advisor.PointcutAdvisor doFastMatchAdvisor(TypeDescription typeDescription, 
                boolean matchAdvisor, Advisor advisor) {
            // match pointcut of advisor and record type resolution info
            TypeResolutionInspector typeResolutionInspector = typeDescription instanceof TypeResolutionInspector
                    ? (TypeResolutionInspector) typeDescription : null;

            try {
                if (typeResolutionInspector != null)
                    typeResolutionInspector.resetInspection();

                return super.doFastMatchAdvisor(typeDescription, matchAdvisor, advisor);
            } finally {
                // record type resolution information
                ResolutionLevel resolutionLevel = null;
                if (typeResolutionInspector != null) {
                    resolutionLevel = typeResolutionInspector.getResolutionLevel();

                    Map<String, ResolutionLevel> advisorTypeResolutionLevels = 
                            getAdvisorTypeResolutionLevels(typeDescription);
                    if (ResolutionLevel.NO_RESOLUTION != resolutionLevel)
                        advisorTypeResolutionLevels.put(advisor.getAdvisorName(), resolutionLevel);
                }
            }

        }

        @Override
        protected boolean doMatchAdvisor(TypeDescription typeDescription, 
                ClassLoader joinpointClassLoader, JavaModule javaModule, 
                InDefinedShape methodDescription, Advisor.PointcutAdvisor pointcutAdvisor) {
            if (super.doMatchAdvisor(typeDescription,
                    joinpointClassLoader, javaModule, 
                    methodDescription, pointcutAdvisor) == false)
                return false;

            // exclude Advisor
            TypeResolutionInspector typeResolutionInspector = typeDescription instanceof TypeResolutionInspector
                    ? (TypeResolutionInspector) typeDescription : null;
            if (typeResolutionInspector != null) {
                Map<String, ResolutionLevel> advisorTypeResolutionLevels = 
                        getAdvisorTypeResolutionLevels(typeDescription);
                advisorTypeResolutionLevels.remove(pointcutAdvisor.getAdvisorName());
            }

            return true;
        }
    }
}
