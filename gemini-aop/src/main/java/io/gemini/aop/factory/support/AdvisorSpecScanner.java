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
package io.gemini.aop.factory.support;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopMetrics;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AdvisorSpecParser.IgnoredSpecException;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.AdvisorSpec.ExprPointcutSpec;
import io.gemini.api.aop.AdvisorSpec.PojoPointcutSpec;
import io.gemini.api.aop.MatchingContext;
import io.gemini.api.aop.annotation.Advisor;
import io.gemini.api.aop.annotation.ExprPointcut;
import io.gemini.core.concurrent.TaskExecutor;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.util.Assert;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
import io.github.classgraph.ClassInfo;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorSpecScanner {

    static final Logger LOGGER = LoggerFactory.getLogger(AdvisorSpecScanner.class);


    Collection<? extends AdvisorSpec> scan(FactoryContext factoryContext);


    static Collection<? extends AdvisorSpec> scanSpecs(FactoryContext factoryContext) {
        long startedAt = System.nanoTime();
        String factoryName = factoryContext.getFactoryName();
        List<AdvisorSpecScanner> advisorSpecScanners = factoryContext.getAdvisorSpecScanners();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Scanning AdvisorSpec under '{}' via AdvisorSpecScanners, \n"
                    + "  {} \n", 
                    factoryName,
                    StringUtils.join(advisorSpecScanners, AdvisorSpecScanner::toString, "\n  ")
            );


        // 1.scan and load AdvisorSpec instances via AdvisorSpecScanner
        Map<String, AdvisorSpec> advisorSpecMap = new LinkedHashMap<>();
        for (AdvisorSpecScanner advisorSpecScanner : advisorSpecScanners) {
            Collection<? extends AdvisorSpec> scannedSpecs = null;
            try {
                scannedSpecs = advisorSpecScanner.scan(factoryContext);
            } catch (Exception e) {
                LOGGER.warn("Could not scan AdvisorSpec instances via '{}'. \n"
                        + "  Error reason: {} \n", 
                        advisorSpecScanner, 
                        e.getMessage(), e
                );

                continue;
            }

            for (AdvisorSpec advisorSpec : scannedSpecs ) {
                if (advisorSpec == null) 
                    continue;

                if (StringUtils.hasText(advisorSpec.getAdvisorName()) == false) {
                    LOGGER.warn("Ignored empty AdvisorName AdvisorSpec. \n"
                            + "    AdviceClassName: {} \n",
                            advisorSpec.getAdviceClassName()
                    );

                    continue;
                }

                String advisorName = advisorSpec.getAdvisorName();
                if (advisorSpecMap.containsKey(advisorName)) {
                    AdvisorSpec existingAdvisorSpec = advisorSpecMap.get(advisorName);
                    LOGGER.warn("Overwrote existing same AdvisorName AdvisorSpec. \n"
                            + "  AdvisorName : {} \n"
                            + "    ExistingSpec AdviceClassName: {} \n"
                            + "    NewSpec AdviceClassName: {} \n",
                            advisorName, 
                            existingAdvisorSpec.getAdviceClassName(), 
                            advisorSpec.getAdviceClassName() 
                    );
                }

                advisorSpecMap.put(advisorName, advisorSpec);
            }
        }

        // 2.post process loaded AdvisorSpec instances
        AdvisorSpecPostProcessor.postProcessSpecs(factoryContext, advisorSpecMap);


        // 3.sort loaded AdvisorSpec instances
        List<AdvisorSpec> advisorSpecs = new ArrayList<>( advisorSpecMap.values() );
        Collections.sort(advisorSpecs, 
                new Comparator<AdvisorSpec>() {
                    @Override
                    public int compare(AdvisorSpec o1, AdvisorSpec o2) {
                        return o1.getAdvisorName().compareTo(o2.getAdvisorName());
                    }
        } );


        if (factoryContext.getAopContext().getDiagnosticLevel().isDebugEnabled() && advisorSpecMap.size() > 0) 
            LOGGER.info("$Took '{}' seconds to scan {} AdvisorSpec instances under '{}', \n"
                    + "  {} \n",
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecMap.size(), factoryName,
                    StringUtils.join(advisorSpecs, AdvisorSpec::getAdvisorName, "\n  ")
            );
        else if (factoryContext.getAopContext().getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to scan {} AdvisorSpec instances under '{}'. ",
                    (System.nanoTime() - startedAt) / AopMetrics.NANO_TIME, advisorSpecMap.size(), factoryName
            );

        return advisorSpecs;
    }


    abstract class AbstractBase<S extends AdvisorSpec> extends ClassScanner.InstantiableClassInfoFilter 
            implements AdvisorSpecScanner {

        protected String resolverName = this.getClass().getName();


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(ClassInfo classInfo) {
            if (super.accept(classInfo) == true)
                return true;

            if (classInfo.isAnonymousInnerClass() == false)
                LOGGER.warn("Ignored AdvisorSpec class is NOT top-level or nested, concrete class. \n"
                        + "  {}: {} \n"
                        + "  Use @{} annotation to ignore this illegal AdvisorSpec. \n", 
                        getSpecType(), classInfo.getName(), 
                        NoScanning.class.getName()
                );

            return false;
        }


        @Override
        public Collection<? extends AdvisorSpec> scan(FactoryContext factoryContext) {
            Assert.notNull(factoryContext, "'factoryContext' must not be null");

            // scan AdvisorSpec implementation
            try {
                List<S> advisorSpecs = this.doScanSpecs(factoryContext);

                if (advisorSpecs != null) {
                    Iterator<S> it = advisorSpecs.iterator();
                    while (it.hasNext()) {
                        S advisorSpec = it.next();
                        if (advisorSpec == null)
                            it.remove();
                    }
                }

                if (factoryContext.getAopContext().getDiagnosticLevel().isDebugEnabled()) {
                    if (CollectionUtils.isEmpty(advisorSpecs)) {
                        LOGGER.info("Did not find AdvisorSpec.{} via '{}'.", getSpecType(), resolverName);
                    } else {
                        LOGGER.info("Found {} AdvisorSpec.{} via '{}'. ", advisorSpecs.size(), getSpecType(), resolverName);
                    }
                }

                return advisorSpecs;
            } catch (Throwable t) {
                LOGGER.warn("Could not scan {} via '{}'.", getSpecType(), resolverName, t);

                Throwables.throwIfRequired(t);
            }

            return Collections.emptyList();
        }

        protected abstract String getSpecType();

        protected abstract List<S> doScanSpecs(FactoryContext factoryContext);
    }


    public class ForPojoPointcut extends AbstractBase<AdvisorSpec.PojoPointcutSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AdvisorSpec.PojoPointcutSpec.class.getSimpleName();
        }

        @Override
        protected List<AdvisorSpec.PojoPointcutSpec> doScanSpecs(FactoryContext factoryContext) {
            List<String> implementorClassNames = factoryContext.getClassScanner()
                    .getClassesImplementing( AdvisorSpec.PojoPointcutSpec.class.getName() )
                    .filter(this)
                    .getNames();

            List<String> factoryClassNames = factoryContext.getClassScanner()
                    .getClassesImplementing( AdvisorSpec.PojoPointcutSpec.Factory.class.getName() )
                    .filter(this)
                    .getNames();

            List<AdvisorSpec.PojoPointcutSpec> advisorSpecs = new ArrayList<>(implementorClassNames.size() + factoryClassNames.size());

            advisorSpecs.addAll(
                    factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                            implementorClassNames, 
                            className -> loadSpecClass(factoryContext, className) 
                    )
            );

            advisorSpecs.addAll(
                    factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                            factoryClassNames, 
                            className -> loadFactoryClass(factoryContext, className) 
                    )
            );

            return advisorSpecs;
        }

        private AdvisorSpec.PojoPointcutSpec loadSpecClass(FactoryContext factoryContext, String className) {
            try {
                Class<?> clazz = factoryContext.getClassLoader().loadClass(className);
                AdvisorSpec.PojoPointcutSpec advisorSpec = (AdvisorSpec.PojoPointcutSpec) factoryContext.getObjectFactory().createObject(clazz);

                if (StringUtils.hasText( advisorSpec.getAdvisorName() ) == false) {
                    advisorSpec = new AdvisorSpec.PojoPointcutSpec.Default(
                            className,
                            advisorSpec.getCondition(),
                            advisorSpec.isInheritClassLoaderMatcher(),
                            advisorSpec.isInheritTypeMatcher(),
                            advisorSpec.getPointcut(), 
                            advisorSpec.getAdviceClassName(),
                            advisorSpec.isPerInstance(),
                            advisorSpec.getOrder()
                    );
                }

                return advisorSpec;
            } catch (IgnoredSpecException e) {
                return null;
            } catch (Throwable t) {
                LOGGER.warn("Could not load {} '{}'. \n", getSpecType(), className, t);

                Throwables.throwIfRequired(t);
                return null;
            }
        }

        private AdvisorSpec.PojoPointcutSpec loadFactoryClass(FactoryContext factoryContext, String className) {
            try {
                Class<?> clazz = factoryContext.getClassLoader().loadClass(className);
                AdvisorSpec.PojoPointcutSpec.Factory factory = (AdvisorSpec.PojoPointcutSpec.Factory) factoryContext.getObjectFactory().createObject(clazz);

                PojoPointcutSpec advisorSpec = factory.getAdvisorSpec();
                if (advisorSpec == null) {
                    LOGGER.warn("Ignored null AdvisorSpec. \n"
                            + "  {}: {} \n", getSpecType(), className);
                    return null;
                }

                if (StringUtils.hasText( advisorSpec.getAdvisorName() ) == false) {
                    advisorSpec = new AdvisorSpec.PojoPointcutSpec.Default(
                            className,
                            advisorSpec.getCondition(),
                            advisorSpec.isInheritClassLoaderMatcher(),
                            advisorSpec.isInheritTypeMatcher(),
                            advisorSpec.getPointcut(), 
                            advisorSpec.getAdviceClassName(),
                            advisorSpec.isPerInstance(), 
                            advisorSpec.getOrder()
                    );
                }

                return advisorSpec;
            } catch (IgnoredSpecException e) {
                return null;
            } catch (Throwable t) {
                LOGGER.warn("Could not load {} '{}'. \n", getSpecType(), className, t);

                Throwables.throwIfRequired(t);
                return null;
            }
        }
    }


    public class ForExprPointcut extends AbstractBase<AdvisorSpec.ExprPointcutSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AdvisorSpec.ExprPointcutSpec.class.getSimpleName();
        }

        @Override
        protected List<AdvisorSpec.ExprPointcutSpec> doScanSpecs(FactoryContext factoryContext) {
            List<String> implementorClassNames = factoryContext.getClassScanner()
                    .getClassesImplementing( AdvisorSpec.ExprPointcutSpec.class.getName() )
                    .filter(this)
                    .getNames();

            List<String> factoryClassNames = factoryContext.getClassScanner()
                    .getClassesImplementing( AdvisorSpec.ExprPointcutSpec.Factory.class.getName() )
                    .filter(this)
                    .getNames();

            List<String> annotatedClassNames = factoryContext.getClassScanner()
                    .getClassesWithAnnotation( ExprPointcutSpec.class.getName() )
                    .filter(this)
                    .filter( classInfo -> classInfo.implementsInterface(AdvisorSpec.class) )
                    .getNames();

            List<AdvisorSpec.ExprPointcutSpec> advisorSpecs = new ArrayList<>(
                    implementorClassNames.size() 
                    + factoryClassNames.size()
                    + annotatedClassNames.size());


            TaskExecutor globalTaskExecutor = factoryContext.getAopContext().getGlobalTaskExecutor();

            advisorSpecs.addAll(
                    globalTaskExecutor.executeTasks(
                            implementorClassNames, 
                            className -> loadSpecClass(factoryContext, className) 
                    )
            );

            advisorSpecs.addAll(
                    globalTaskExecutor.executeTasks(
                            factoryClassNames, 
                            className -> loadFactoryClass(factoryContext, className) 
                    )
            );

            advisorSpecs.addAll(
                    globalTaskExecutor.executeTasks(
                            annotatedClassNames, 
                            className -> parseExprPointcutSpec(factoryContext, className)
                    )

            );

            return advisorSpecs;
        }

        private AdvisorSpec.ExprPointcutSpec loadSpecClass(FactoryContext factoryContext, String className) {
            try {
                Class<?> clazz = factoryContext.getClassLoader().loadClass(className);
                AdvisorSpec.ExprPointcutSpec advisorSpec = (AdvisorSpec.ExprPointcutSpec) factoryContext.getObjectFactory().createObject(clazz);

                if (StringUtils.hasText( advisorSpec.getAdvisorName() ) == false) {
                    advisorSpec = new AdvisorSpec.ExprPointcutSpec.Default(
                            className,
                            advisorSpec.getCondition(),
                            advisorSpec.isInheritClassLoaderMatcher(),
                            advisorSpec.isInheritTypeMatcher(),
                            advisorSpec.getClassLoaderExpression(),
                            advisorSpec.getPointcutExpression(), 
                            advisorSpec.getAdviceClassName(),
                            advisorSpec.isPerInstance(), 
                            advisorSpec.getOrder()
                    );
                }

                return advisorSpec;
            } catch (IgnoredSpecException e) {
                return null;
            } catch (Throwable t) {
                LOGGER.warn("Could not load {} '{}'. \n", getSpecType(), className, t);

                Throwables.throwIfRequired(t);
                return null;
            }
        }

        private AdvisorSpec.ExprPointcutSpec loadFactoryClass(FactoryContext factoryContext, String className) {
            try {
                Class<?> clazz = factoryContext.getClassLoader().loadClass(className);
                AdvisorSpec.ExprPointcutSpec.Factory factory = (AdvisorSpec.ExprPointcutSpec.Factory) factoryContext.getObjectFactory().createObject(clazz);

                ExprPointcutSpec advisorSpec = factory.getAdvisorSpec();
                if (advisorSpec == null) {
                    LOGGER.warn("Ignored null AdvisorSpec. \n"
                            + "  {}: {} \n", 
                            getSpecType(), className
                    );

                    return null;
                }

                if (StringUtils.hasText( advisorSpec.getAdvisorName() ) == false) {
                    advisorSpec = new AdvisorSpec.ExprPointcutSpec.Default(
                            className,
                            advisorSpec.getCondition(),
                            advisorSpec.isInheritClassLoaderMatcher(),
                            advisorSpec.isInheritTypeMatcher(),
                            advisorSpec.getClassLoaderExpression(),
                            advisorSpec.getPointcutExpression(), 
                            advisorSpec.getAdviceClassName(),
                            advisorSpec.isPerInstance(), 
                            advisorSpec.getOrder()
                    );
                }

                return advisorSpec;
            } catch (IgnoredSpecException e) {
                return null;
            } catch (Throwable t) {
                LOGGER.warn("Could not load {} '{}'. \n", getSpecType(), className, t);

                Throwables.throwIfRequired(t);
                return null;
            }
        }

        private AdvisorSpec.ExprPointcutSpec parseExprPointcutSpec(FactoryContext factoryContext, String className) {
            try {
                TypeDescription adviceType = factoryContext.getTypePool().describe(className).resolve();
                if (adviceType == null) 
                    return null;

                AnnotationList annotations = adviceType.getDeclaredAnnotations();

                ElementMatcher<MatchingContext> condition = AdvisorConditionParser.parseAdvisorCondition(
                        factoryContext, annotations);

                AnnotationDescription advisorAnnotation = annotations.ofType(Advisor.class);
                AnnotationDescription exprPointcutAnnotation = annotations.ofType(
                        io.gemini.api.aop.annotation.ExprPointcut.class);

                return AdvisorSpecParser.parseExprPointcutAdvisorSpec(
                        factoryContext,
                        adviceType.getTypeName(),
                        condition,
                        advisorAnnotation, 
                        exprPointcutAnnotation
                );
            } catch (IgnoredSpecException e) {
                return null;
            } catch (Throwable t) {
                LOGGER.warn("Could not load {} '{}'. \n", getSpecType(), className, t);

                Throwables.throwIfRequired(t);
                return null;
            }
        }
    }


    public class ForAspectJPointcut extends AbstractBase<AspectJPointcutAdvisorSpec> {

        private static final List<Class<? extends Annotation>> ADVICE_ANNOTATIONS = Arrays.asList(
                Before.class, After.class, 
                AfterReturning.class, AfterThrowing.class, 
                Around.class);


        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AspectJPointcutAdvisorSpec.class.getSimpleName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected List<AspectJPointcutAdvisorSpec> doScanSpecs(FactoryContext factoryContext) {
            List<String> classNames = factoryContext.getClassScanner()
                    .getClassesWithAnnotation( Aspect.class.getName() )
                    .filter(this)
                    .getNames();

            return factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                    classNames, 
                    className -> parseAspectJClass(factoryContext, className))
            .stream()
            .flatMap( e -> e.stream() )
            .collect( Collectors.toList() );
        }

        private Collection<AspectJPointcutAdvisorSpec> parseAspectJClass(FactoryContext factoryContext, String aspectJClassName) {
            TypeDescription aspectJType = factoryContext.getTypePool().describe(aspectJClassName).resolve();
            if (aspectJType == null) 
                return Collections.emptyList();

            try {
                AdvisorSpec advisorSpec = parseAdvisorSpec(factoryContext, aspectJType);

                MethodList<InDefinedShape> declaredMethods = aspectJType.getDeclaredMethods();
                Map<String, AspectJPointcutAdvisorSpec> advisorSpecMap = new LinkedHashMap<>(declaredMethods.size());
                for (MethodDescription.InDefinedShape aspectJMethod : declaredMethods) {
                    AnnotationList annotations = aspectJMethod.getDeclaredAnnotations();

                    ElementMatcher<MatchingContext> condition = AdvisorConditionParser.parseAdvisorCondition(
                            factoryContext, annotations);
                    if (condition == null)
                        condition = advisorSpec.getCondition();
                    else
                        condition = new ElementMatcher.Junction.Conjunction<>(
                                advisorSpec.getCondition(), condition);

                    AnnotationDescription advisorAnnotation = annotations.ofType(Advisor.class);

                    for (Class<? extends Annotation> annotationType : ADVICE_ANNOTATIONS) {
                        AnnotationDescription aspectJAnnotation = annotations.ofType(annotationType);
                        if (aspectJAnnotation == null)
                            continue;

                        AspectJPointcutAdvisorSpec aspectJAdvisorSpec = parseAspectJAdvisorSpec(
                                factoryContext,
                                aspectJType, 
                                aspectJMethod,
                                condition,
                                advisorAnnotation, 
                                aspectJAnnotation,
                                advisorSpec
                        );
                        if (aspectJAdvisorSpec == null)
                            continue;

                        if (advisorSpecMap.containsKey(aspectJAdvisorSpec.getAdvisorName()))
                            LOGGER.warn("Ignored duplicate name AspectJ advice method. \n"
                                    + "  {}: {} \n"
                                    + "  AdviceMethod: {} \n",
                                    getSpecType(), aspectJAdvisorSpec.getAdvisorName(), 
                                    MethodUtils.getMethodSignature(aspectJMethod) 
                            );
                        else
                            advisorSpecMap.put(aspectJAdvisorSpec.getAdvisorName(), aspectJAdvisorSpec);

                    }
                }

                if (advisorSpecMap.size() == 0) {
                    LOGGER.warn("Ignored AdvisorSpec contains no advice methods. \n"
                            + "  {}: {} \n", 
                            getSpecType(), aspectJClassName );
                    return Collections.emptyList();
                }

                return advisorSpecMap.values();
            } catch (IgnoredSpecException e) {
                return null;
            } catch (Throwable t) {
                LOGGER.warn("Could not load {} '{}'. \n", getSpecType(), aspectJClassName, t);

                Throwables.throwIfRequired(t);
                return Collections.emptyList();
            }
        }

        private AdvisorSpec parseAdvisorSpec(FactoryContext factoryContext, TypeDescription aspectJType) {
            AnnotationList annotations = aspectJType.getDeclaredAnnotations();

            ElementMatcher<MatchingContext> condition = AdvisorConditionParser.parseAdvisorCondition(
                    factoryContext, annotations);

            AnnotationDescription advisorAnnotation = annotations.ofType(Advisor.class);

            return AdvisorSpecParser.parseAdvisorSpec(
                    factoryContext,
                    aspectJType.getTypeName(),
                    condition,
                    advisorAnnotation
            );
        }

        private AspectJPointcutAdvisorSpec parseAspectJAdvisorSpec(
                FactoryContext factoryContext, 
                TypeDescription aspectJType, 
                MethodDescription.InDefinedShape aspectJMethod, 
                ElementMatcher<MatchingContext> condition,
                AnnotationDescription advisorAnnotation, 
                AnnotationDescription adviceAnnotation,
                AdvisorSpec advisorSpec) {
            try {
                if (aspectJMethod.isAbstract()) {
                    LOGGER.warn("Ignored abstract AspectJ advice method. \n"
                            + "  {}: {} \n"
                            + "  AdviceMethod: {} \n",
                            getSpecType(), aspectJType.getTypeName(), 
                            MethodUtils.getMethodSignature(aspectJMethod) 
                    );

                    return null;
                }

                if (aspectJMethod.isPrivate()) {
                    LOGGER.warn("Ignored private AspectJ advice method. \n"
                            + "  {}: {} \n"
                            + "  AdviceMethod: {} \n",
                            getSpecType(), aspectJType.getTypeName(), 
                            MethodUtils.getMethodSignature(aspectJMethod) 
                    );

                    return null;
                }

                // create AspectJAdvisorSpec
                AnnotationDescription exprPointcutAnnotation = aspectJMethod.getDeclaredAnnotations()
                        .ofType(ExprPointcut.class);

                return AdvisorSpecParser.parseAspectJPointcutAdvisorSpec(
                        factoryContext,
                        aspectJType, 
                        aspectJMethod, 
                        condition,
                        advisorAnnotation, 
                        exprPointcutAnnotation,
                        adviceAnnotation 
                );
            } catch (IgnoredSpecException e) {
                return null;
            } catch (Throwable t) {
                LOGGER.warn("Could not load AdvisorSpec. \n"
                        + "  {}: {} \n"
                        + "  AdviceMethod: {} \n",
                        getSpecType(), aspectJType.getTypeName(), 
                        MethodUtils.getMethodSignature(aspectJMethod),
                        t
                );

                Throwables.throwIfRequired(t);
                return null;
            }
        }
    }
}
