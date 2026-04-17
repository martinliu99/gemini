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
package io.gemini.aop.factory.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopMetrics;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AdviceSpec.AspectJAdviceSpec;
import io.gemini.aop.factory.support.AdvisorSpec.PointcutAdvisorSpec;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.annotation.Order;
import io.gemini.api.aop.MatchingContext;
import io.gemini.api.aop.annotation.AdvisorName;
import io.gemini.api.aop.annotation.EnablePerInstance;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.util.Assert;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
import io.github.classgraph.ClassInfo;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Scans aspect application classpaths for {@link AdvisorSpec} instances.
 * <p>
 * The {@link Compound} implementation delegates to all registered scanners in order,
 * deduplicates by advisor name, and then runs all {@link AdvisorSpecPostProcessor} instances.
 * Concrete scanners ({@link ForAtPojoPointcut}, {@link ForAtExprPointcut}, {@link ForAspectJPointcutAdvisor})
 * discover advice classes annotated with {@code @PojoPointcut}, {@code @ExprPointcut}, or {@code @Aspect}.
 * </p>
 *
 * @author   martin.liu
 */
public interface AdvisorSpecScanner {

    Logger LOGGER = LoggerFactory.getLogger(AdvisorSpecScanner.class);


    /**
     * Scans the aspect application classpath and returns all discovered {@link AdvisorSpec} instances.
     *
     * @param factoryContext the factory context providing class scanner, type pool, and config
     * @return a collection of discovered {@link AdvisorSpec} instances, never {@code null}
     */
    Collection<? extends AdvisorSpec> scan(FactoryContext factoryContext);


    /**
     * Delegates to all registered {@link AdvisorSpecScanner} instances in order,
     * deduplicates by advisor name, and runs all {@link AdvisorSpecPostProcessor} instances.
     */
    @NoScanning
    class Compound implements AdvisorSpecScanner {

        private final List<? extends AdvisorSpecScanner> advisorSpecScanners;

        public Compound(FactoryContext factoryContext) {
            this.advisorSpecScanners = factoryContext.getObjectFactory().createObjectsImplementing(
                    AdvisorSpecScanner.class, true, "factoryContext", factoryContext);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<? extends AdvisorSpec> scan(FactoryContext factoryContext) {
            long startedAt = System.nanoTime();

            String factoryName = factoryContext.getFactoryName();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("^Scanning AdvisorSpec under '{}' via AdvisorSpecScanners, \n"
                        + "  {} \n", 
                        factoryName,
                        StringUtils.join(advisorSpecScanners, AdvisorSpecScanner::toString, "\n  ")
                );


            // 1.scan and load AdvisorSpec instances via AdvisorSpecScanner
            Map<String, AdvisorSpec> advisorSpecMap = new LinkedHashMap<>();
            for (AdvisorSpecScanner advisorSpecScanner : advisorSpecScanners) {
                Collection<? extends AdvisorSpec> scannedSpecs = Collections.emptyList();

                // try to scan AdvisorSpec
                try {
                    scannedSpecs = advisorSpecScanner.scan(factoryContext);
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not scan AdvisorSpec via '{}'. \n"
                                + "  Error reason: {} \n", 
                                advisorSpecScanner, 
                                t.getMessage(), 
                                t
                        );

                    Throwables.throwIfRequired(t);
                    continue;
                }

                // validate and collect AdvisorSpec
                for (AdvisorSpec advisorSpec : scannedSpecs) {
                    if (advisorSpec == null) 
                        continue;
    
                    if (StringUtils.hasText(advisorSpec.getAdvisorName()) == false) {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Ignored empty AdvisorName AdvisorSpec. \n"
                                    + "    AdviceClassName: {} \n",
                                    advisorSpec.getAdviceSpec().getAdviceClassName()
                            );

                        continue;
                    }

                    String advisorName = advisorSpec.getAdvisorName();
                    if (advisorSpecMap.containsKey(advisorName)) {
                        AdvisorSpec existingAdvisorSpec = advisorSpecMap.get(advisorName);

                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Overwrote existing same name AdvisorSpec. \n"
                                    + "  AdvisorName : {} \n"
                                    + "    ExistingSpec AdviceClassName: {} \n"
                                    + "    NewSpec AdviceClassName: {} \n",
                                    advisorName, 
                                    existingAdvisorSpec.getAdviceSpec().getAdviceClassName(), 
                                    advisorSpec.getAdviceSpec().getAdviceClassName() 
                            );
                    }

                    advisorSpecMap.put(advisorName, advisorSpec);
                }
            }

            // 2.post process loaded AdvisorSpec instances
            new AdvisorSpecPostProcessor.Compound(factoryContext).postProcess(factoryContext, advisorSpecMap);


            if (LOGGER.isInfoEnabled()) {
                if (factoryContext.getAopContext().getDiagnosticLevel().isDebugEnabled() && advisorSpecMap.size() > 0) 
                    LOGGER.info("$Took '{}' seconds to scan {} AdvisorSpecs under '{}', \n"
                            + "  {} \n",
                            (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecMap.size(), factoryName,
                            StringUtils.join(advisorSpecMap.values(), AdvisorSpec::getAdvisorName, "\n  ")
                    );
                else if (factoryContext.getAopContext().getDiagnosticLevel().isSimpleEnabled())
                    LOGGER.info("$Took '{}' seconds to scan {} AdvisorSpecs under '{}'. ",
                            (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecMap.size(), factoryName
                    );
            }
    
            return advisorSpecMap.values();
        }
    }


    /**
     * Abstract base providing common scanning logic: class info filtering, spec parsing,
     * condition parsing, and advice/pointcut spec parsing delegation.
     */
    abstract class AbstractBase extends ClassScanner.InstantiableClassInfoFilter 
            implements AdvisorSpecScanner {

        private final FactoryContext factoryContext;
        private String resolverName;


        public AbstractBase(FactoryContext factoryContext) {
            this.factoryContext = factoryContext;
            this.resolverName = this.getClass().getName();
        }


        public FactoryContext getFactoryContext() {
            return factoryContext;
        }

        public String getResolverName() {
            return resolverName;
        }

        protected AdviceSpecParser createAdviceSpecParser() {
            return new AdviceSpecParser.Compound(factoryContext);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(ClassInfo classInfo) {
            if (super.accept(classInfo) == true)
                return true;

            if (classInfo.isAnonymousInnerClass() == false)
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Ignored AdvisorSpec class is NOT top-level or nested, concrete class. \n"
                            + "  AdvisorSpec: {} \n"
                            + "  Use @{} annotation to ignore this illegal AdvisorSpec. \n", 
                            classInfo.getName(), 
                            NoScanning.class.getName()
                    );

            return false;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<AdvisorSpec> scan(FactoryContext factoryContext) {
            Assert.notNull(factoryContext, "'factoryContext' must not be null");

            // scan AdvisorSpec implementation
            try {
                List<AdvisorSpec> advisorSpecs = this.doScanAdvisorSpecs(factoryContext);

                if (advisorSpecs != null) {
                    Iterator<AdvisorSpec> it = advisorSpecs.iterator();
                    while (it.hasNext()) {
                        AdvisorSpec advisorSpec = it.next();
                        if (advisorSpec == null)
                            it.remove();
                    }
                }

                if (LOGGER.isInfoEnabled() && factoryContext.getAopContext().getDiagnosticLevel().isDebugEnabled()) {
                    if (CollectionUtils.isEmpty(advisorSpecs)) {
                        LOGGER.info("Did not find AdvisorSpec via '{}'.", resolverName);
                    } else {
                        LOGGER.info("Found {} AdvisorSpecs via '{}'. ", advisorSpecs.size(), resolverName);
                    }
                }

                return advisorSpecs;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not scan AdvisorSpec via '{}'."
                            + "  Error reason: {} \n", 
                            resolverName, 
                            e.getMessage(),
                            e
                    );
            }

            return Collections.emptyList();
        }

        protected abstract List<AdvisorSpec> doScanAdvisorSpecs(FactoryContext factoryContext);


        protected PointcutAdvisorSpec parsePointcutAdvisorSpec(FactoryContext factoryContext, ClassInfo classInfo) {
            try {
                String adviceClassName = classInfo.getName();
                TypeDescription decalringType = factoryContext.getTypePool().describeAspectType(adviceClassName).resolve();
                if (decalringType == null) 
                    return null;

                AnnotationList annotations = decalringType.getDeclaredAnnotations();

                String advisorName = null;
                AnnotationDescription advisorNameAnnotation = annotations.ofType(AdvisorName.class);
                if (advisorNameAnnotation != null)
                    advisorName = advisorNameAnnotation.getValue("value").resolve(String.class).trim();
                if (StringUtils.hasText(advisorName) == false)
                    advisorName = adviceClassName;

                ElementMatcher<MatchingContext> condition = doParseCondition(factoryContext, annotations);

                AdviceSpec adviceSpec = doParseAdviceSpec(factoryContext, advisorName, decalringType);
                if (adviceSpec == null)
                    return null;

                PointcutSpec pointcutSpec = doParsePointcutSpecs(factoryContext, adviceSpec);
                if (pointcutSpec == null)
                    return null;

                boolean perInstance = annotations.isAnnotationPresent(EnablePerInstance.class);

                int order = Order.LOWEST_PRECEDENCE;
                AnnotationDescription orderAnnotation = annotations.ofType(Order.class);
                if (orderAnnotation != null)
                    order = orderAnnotation.getValue("value").resolve(Integer.class);

                return new PointcutAdvisorSpec.Default(
                        advisorName, condition, 
                        adviceSpec, perInstance, order, 
                        pointcutSpec);
            } catch (IllegalSpecException e) {
                return null;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not parse AdvisorSpec. \n"
                            + "  DeclaringType: {} \n"
                            + "  Error reason: {} \n", 
                            classInfo.getName(), 
                            e.getMessage(),
                            e
                    );
                return null;
            }
        }

        protected ElementMatcher<MatchingContext> doParseCondition(FactoryContext factoryContext, AnnotationList annotationList) {
            return AdvisorConditionParser.parseAdvisorCondition(
                    factoryContext, annotationList);
        }

        protected AdviceSpec doParseAdviceSpec(FactoryContext factoryContext, 
                String advisorName, TypeDescription adviceType) {
            Collection<? extends AdviceSpec> adviceSpecs = doGetAdviceSpecParser().parse(factoryContext, adviceType);
            if (CollectionUtils.isEmpty(adviceSpecs))
                return null;
            return adviceSpecs.iterator().next();
        }

        protected abstract AdviceSpecParser doGetAdviceSpecParser();

        protected PointcutSpec doParsePointcutSpecs(FactoryContext factoryContext, AdviceSpec adviceSpec) {
            return null;
        }
    }


    /**
     * Scans for advice classes annotated with {@link io.gemini.api.aop.annotation.PojoPointcut}
     * and builds {@link AdvisorSpec.PointcutAdvisorSpec} instances with POJO-style pointcuts.
     */
    public class ForAtPojoPointcut extends AbstractBase {

        private final AdviceSpecParser adviceSpecParser;
        private final PointcutSpecParser.ForPojoPointcut pojoPointcutParser;


        public ForAtPojoPointcut(FactoryContext factoryContext) {
            super(factoryContext);

            this.adviceSpecParser = createAdviceSpecParser();
            this.pojoPointcutParser = new PointcutSpecParser.ForPojoPointcut();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected List<AdvisorSpec> doScanAdvisorSpecs(FactoryContext factoryContext) {
            List<ClassInfo> atPojoPointcutClasses = factoryContext.getClassScanner()
                    .getClassesWithAnnotation( io.gemini.api.aop.annotation.PojoPointcut.class.getName() )
                    .filter(this)
                    ;

            return factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                    atPojoPointcutClasses, 
                    classInfo -> parsePointcutAdvisorSpec(factoryContext, classInfo)
            );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected AdviceSpecParser doGetAdviceSpecParser() {
            return adviceSpecParser;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected PointcutSpec doParsePointcutSpecs(FactoryContext factoryContext, AdviceSpec adviceSpec) {
            return pojoPointcutParser.parse(factoryContext, adviceSpec);
        }
    }


    /**
     * Scans for advice classes annotated with {@link io.gemini.api.aop.annotation.ExprPointcut}
     * and builds {@link AdvisorSpec.PointcutAdvisorSpec} instances with expression-based pointcuts.
     */
    public class ForAtExprPointcut extends AbstractBase {

        private final AdviceSpecParser adviceSpecParser;
        private final PointcutSpecParser.ForExprPointcut exprPointcutParser;


        public ForAtExprPointcut(FactoryContext factoryContext) {
            super(factoryContext);

            this.adviceSpecParser = createAdviceSpecParser();
            this.exprPointcutParser = new PointcutSpecParser.ForExprPointcut();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected List<AdvisorSpec> doScanAdvisorSpecs(FactoryContext factoryContext) {
            List<ClassInfo> atExprPointcutClasses = factoryContext.getClassScanner()
                    .getClassesWithAnnotation( io.gemini.api.aop.annotation.ExprPointcut.class.getName() )
                    .filter(this)
                    ;

            return factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                    atExprPointcutClasses, 
                    classInfo -> parsePointcutAdvisorSpec(factoryContext, classInfo)
            );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected AdviceSpecParser doGetAdviceSpecParser() {
            return adviceSpecParser;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected PointcutSpec doParsePointcutSpecs(FactoryContext factoryContext, AdviceSpec adviceSpec) {
            return exprPointcutParser.parse(factoryContext, adviceSpec);
        }
    }


    /**
     * Scans for {@code @Aspect}-annotated classes and builds one
     * {@link AdvisorSpec.PointcutAdvisorSpec} per advice method found.
     */
    public class ForAspectJPointcutAdvisor extends AbstractBase {

        private final AdviceSpecParser adviceSpecParser;
        private final PointcutSpecParser.ForAspectJPointcut aspectJPointcut;


        public ForAspectJPointcutAdvisor(FactoryContext factoryContext) {
            super(factoryContext);

            this.adviceSpecParser = createAdviceSpecParser();
            this.aspectJPointcut = new PointcutSpecParser.ForAspectJPointcut();
        }


        /**
         * {@inheritDoc}
         */
        @Override
        protected List<AdvisorSpec> doScanAdvisorSpecs(FactoryContext factoryContext) {
            List<String> classNames = factoryContext.getClassScanner()
                    .getClassesWithAnnotation( Aspect.class.getName() )
                    .filter(this)
                    .getNames();

            return factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                    classNames, 
                    className -> parsePointcutAdvisorSpec(factoryContext, className))
            .stream()
            .flatMap( e -> e.stream() )
            .collect( Collectors.toList() );
        }

        private Collection<PointcutAdvisorSpec> parsePointcutAdvisorSpec(FactoryContext factoryContext, String adviceClassName) {
            TypeDescription aspectType = factoryContext.getTypePool().describeAspectType(adviceClassName).resolve();
            if (aspectType == null) 
                return Collections.emptyList();

            try {
                TypeDescription adviceType = factoryContext.getTypePool().describeAspectType(adviceClassName).resolve();
                if (adviceType == null) 
                    return null;

                Collection<? extends AdviceSpec> adviceSpecs = doGetAdviceSpecParser().parse(factoryContext, adviceType);
                if (CollectionUtils.isEmpty(adviceSpecs))
                    return null;

                AnnotationList adviceTypeAnnotations = adviceType.getDeclaredAnnotations();
                AnnotationDescription typeAdvisorNameAnnotation = adviceTypeAnnotations.ofType(AdvisorName.class);
                AnnotationDescription typePerInstanceAnnotation = adviceTypeAnnotations.ofType(EnablePerInstance.class);
                AnnotationDescription typeOrderAnnotation = adviceTypeAnnotations.ofType(Order.class);

                ElementMatcher<MatchingContext> adviceTypeCondition = doParseCondition(factoryContext, adviceTypeAnnotations);

                List<PointcutAdvisorSpec> pointcutAdvisorSpecs = new ArrayList<>(adviceSpecs.size());
                for (AdviceSpec adviceSpec : adviceSpecs) {
                    try {
                        AspectJAdviceSpec aspectJAdviceSpec = (AspectJAdviceSpec) adviceSpec;

                        AnnotationList adviceMethodAnnotations = aspectJAdviceSpec.getAdviceMethod().getDeclaredAnnotations();

                        AnnotationDescription advisorNameAnnotation = adviceMethodAnnotations.ofType(AdvisorName.class);
                        if (advisorNameAnnotation == null)
                            advisorNameAnnotation = typeAdvisorNameAnnotation;

                        String advisorName = aspectJAdviceSpec.getAdviceClassName();
                        if (advisorNameAnnotation != null)
                            advisorName = advisorNameAnnotation.getValue("advisorName").resolve(String.class).trim();

                        // merge method level and class level condition definition
                        ElementMatcher<MatchingContext> condition = AdvisorConditionParser.parseAdvisorCondition(
                                factoryContext, adviceMethodAnnotations);
                        if (condition == null)
                            condition = adviceTypeCondition;
                        else if (adviceTypeCondition != null)
                            condition = new ElementMatcher.Junction.Conjunction<>(
                                    adviceTypeCondition, condition);

                        // merge method level and class level order definition
                        AnnotationDescription perInstanceAnnotation = adviceMethodAnnotations.ofType(EnablePerInstance.class);
                        if (perInstanceAnnotation == null)
                            perInstanceAnnotation = typePerInstanceAnnotation;

                        boolean perInstance = perInstanceAnnotation != null;

                        AnnotationDescription orderAnnotation = adviceMethodAnnotations.ofType(Order.class);
                        if (orderAnnotation == null)
                            orderAnnotation = typeOrderAnnotation;

                        int order = Order.LOWEST_PRECEDENCE;
                        if (orderAnnotation != null)
                            order = orderAnnotation.getValue("value").resolve(Integer.class);

                        PointcutSpec pointcutSpec = aspectJPointcut.parse(factoryContext, aspectJAdviceSpec);

                        PointcutAdvisorSpec pointcutAdvisorSpec = new PointcutAdvisorSpec.Default(
                                advisorName, condition, 
                                adviceSpec, perInstance, order, 
                                pointcutSpec);

                        pointcutAdvisorSpecs.add(pointcutAdvisorSpec);
                    } catch (Exception e) {
                        if (LOGGER.isWarnEnabled())
                            LOGGER.warn("Could not parse AdvisorSpec. \n"
                                    + "  DeclaringType: {} \n"
                                    + "  AdviceMethod: {} \n" 
                                    + "  Error reason: {} \n", 
                                    adviceSpec.getDeclaringType().getTypeName(), 
                                    MethodUtils.getMethodSignature(adviceSpec.getAdviceMethod()),
                                    e.getMessage(),
                                    e
                            );
                    }
                }

                return pointcutAdvisorSpecs;
            } catch (IllegalSpecException e) {
                return null;
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not parse AdvisorSpec. \n"
                            + "  DeclaringType: {} \n"
                            + "  Error reason: {} \n", 
                            adviceClassName, 
                            e.getMessage(),
                            e
                    );

                return null;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected AdviceSpecParser doGetAdviceSpecParser() {
            return adviceSpecParser;
        }
    }
}
