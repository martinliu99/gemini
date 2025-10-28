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
import java.util.HashMap;
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

import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.matcher.AdvisorCondition;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.AdvisorSpec.ExprPointcutSpec;
import io.gemini.api.aop.AdvisorSpec.PojoPointcutSpec;
import io.gemini.api.aop.condition.ConditionContext;
import io.gemini.api.aop.condition.Conditional;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.util.Assert;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
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
        Map<String, AdvisorSpec> advisorSpecMap = new LinkedHashMap<>();

        // scan and load AdvisorSpec instances via AdvisorSpecScanner
        for(AdvisorSpecScanner advisorSpecScanner : factoryContext.getAdvisorSpecScanners()) {
            for(AdvisorSpec advisorSpec : advisorSpecScanner.scan(factoryContext) ) {
                if(advisorSpec == null) 
                    continue;

                String advisorName = advisorSpec.getAdvisorName();
                if(advisorSpecMap.containsKey(advisorName)) {
                    AdvisorSpec existingAdvisorSpec = advisorSpecMap.get(advisorName);
                    LOGGER.warn("Ignored existing AdvisorSpec. \n  AdvisorName : {} \n  ExistingSpec AdviceClassName: {} \n  NewSpec AdviceClassName: {} \n",
                            advisorName, existingAdvisorSpec.getAdviceClassName(), advisorSpec.getAdviceClassName() );
                }

                advisorSpecMap.put(advisorName, advisorSpec);
            }
        }

        // post process loaded AdvisorSpec instances
        AdvisorSpecPostProcessor.postProcessSpecs(factoryContext, advisorSpecMap);

        return advisorSpecMap.values();
    }


    abstract class AbstractBase<S extends AdvisorSpec> extends ClassScanner.InstantiableClassInfoFilter 
            implements AdvisorSpecScanner {

        protected String resolverName = this.getClass().getName();


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(ClassInfo classInfo) {
            if(super.accept(classInfo) == true)
                return true;

            LOGGER.warn("Ignored AdvisorSpec class is NOT top-level or nested, concrete class. \n  {}: {} \n"
                    + "  Use @{} annotation to ignore this illegal AdvisorSpec. \n", 
                    getSpecType(), classInfo.getName(), NoScanning.class.getName());

            return false;
        }


        @Override
        public Collection<? extends AdvisorSpec> scan(FactoryContext factoryContext) {
            Assert.notNull(factoryContext, "'factoryContext' must not be null");

            // scan AdvisorSpec implementation
            try {
                List<S> advisorSpecs = this.doScanSpecs(factoryContext);

                if(advisorSpecs != null) {
                    Iterator<S> it = advisorSpecs.iterator();
                    while(it.hasNext()) {
                        S advisorSpec = it.next();
                        if(advisorSpec == null)
                            it.remove();
                    }
                }

                if(CollectionUtils.isEmpty(advisorSpecs)) {
                    LOGGER.info("Did not find AdvisorSpec.{} via '{}'.", getSpecType(), resolverName);
                } else {
                    LOGGER.info("Found {} AdvisorSpec.{} via '{}'.", advisorSpecs.size(), getSpecType(), resolverName);

                    return advisorSpecs;
                }
            } catch(Throwable t) {
                LOGGER.warn("Failed to scan AdvisorSpec.{} via '{}'.", getSpecType(), resolverName, t);
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
                            className -> loadSpecClass(factoryContext, className) )
            );

            advisorSpecs.addAll(
                    factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                            factoryClassNames, 
                            className -> loadFactoryClass(factoryContext, className) )
            );

            return advisorSpecs;
        }

        private AdvisorSpec.PojoPointcutSpec loadSpecClass(FactoryContext factoryContext, String className) {
            try {
                Class<?> clazz = factoryContext.getClassLoader().loadClass(className);
                AdvisorSpec.PojoPointcutSpec advisorSpec = (AdvisorSpec.PojoPointcutSpec) factoryContext.getObjectFactory().createObject(clazz);

                String advisorName = advisorSpec.getAdvisorName();
                boolean noAdvisorName = !StringUtils.hasText(advisorName) || AdvisorSpec.PojoPointcutSpec.Default.class.getName().equals(advisorName);

                ElementMatcher<ConditionContext> condition = advisorSpec.getCondition();
                boolean noCondition = condition == null;

                if(noAdvisorName || noCondition)
                    advisorSpec = new AdvisorSpec.PojoPointcutSpec.Default(
                            noAdvisorName ? className : advisorName,
                            noCondition ? AdvisorSpec.TRUE : condition,
                            advisorSpec.isPerInstance(), advisorSpec.getAdviceClassName(),
                            advisorSpec.getPointcut(), advisorSpec.getOrder());

                return advisorSpec;
            } catch (Throwable t) {
                LOGGER.warn("Failed to load AdvisorSpec. \n  {}: {}", getSpecType(), className, t);
                return null;
            }
        }

        private AdvisorSpec.PojoPointcutSpec loadFactoryClass(FactoryContext factoryContext, String className) {
            try {
                Class<?> clazz = factoryContext.getClassLoader().loadClass(className);
                AdvisorSpec.PojoPointcutSpec.Factory factory = (AdvisorSpec.PojoPointcutSpec.Factory) factoryContext.getObjectFactory().createObject(clazz);

                PojoPointcutSpec advisorSpec = factory.getAdvisorSpec();
                if(advisorSpec == null) {
                    LOGGER.warn("Ignored AdvisorSpec is null. \n  {}: {} \n", getSpecType(), className);
                    return null;
                }

                String advisorName = advisorSpec.getAdvisorName();
                boolean noAdvisorName = !StringUtils.hasText(advisorName) || AdvisorSpec.PojoPointcutSpec.Default.class.getName().equals(advisorName);

                ElementMatcher<ConditionContext> condition = advisorSpec.getCondition();
                boolean noCondition = condition == null;

                if(noAdvisorName || noCondition) 
                    advisorSpec = new AdvisorSpec.PojoPointcutSpec.Default(
                            noAdvisorName ? className : advisorName,
                            noCondition ? AdvisorSpec.TRUE : condition,
                            advisorSpec.isPerInstance(), advisorSpec.getAdviceClassName(),
                            advisorSpec.getPointcut(), advisorSpec.getOrder());

                return advisorSpec;
            } catch (Throwable t) {
                LOGGER.warn("Failed to load AdvisorSpec. \n  {}: {}", getSpecType(), className, t);
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

            List<AdvisorSpec.ExprPointcutSpec> advisorSpecs = new ArrayList<>(implementorClassNames.size() + factoryClassNames.size());

            advisorSpecs.addAll(
                    factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                            implementorClassNames, 
                            className -> loadSpecClass(factoryContext, className) )
            );

            advisorSpecs.addAll(
                    factoryContext.getAopContext().getGlobalTaskExecutor().executeTasks(
                            factoryClassNames, 
                            className -> loadFactoryClass(factoryContext, className) )
            );

            return advisorSpecs;
        }

        private AdvisorSpec.ExprPointcutSpec loadSpecClass(FactoryContext factoryContext, String className) {
            try {
                Class<?> clazz = factoryContext.getClassLoader().loadClass(className);
                AdvisorSpec.ExprPointcutSpec advisorSpec = (AdvisorSpec.ExprPointcutSpec) factoryContext.getObjectFactory().createObject(clazz);

                String advisorName = advisorSpec.getAdvisorName();
                boolean noAdvisorName = !StringUtils.hasText(advisorName) || AdvisorSpec.ExprPointcutSpec.Default.class.getName().equals(advisorName);

                ElementMatcher<ConditionContext> condition = advisorSpec.getCondition();
                boolean noCondition = condition == null;

                if(noAdvisorName || noCondition) 
                    advisorSpec = new AdvisorSpec.ExprPointcutSpec.Default(
                            noAdvisorName ? className : advisorName,
                            noCondition ? AdvisorSpec.TRUE: condition,
                            advisorSpec.isPerInstance(), advisorSpec.getAdviceClassName(),
                            advisorSpec.getPointcutExpression(), advisorSpec.getOrder());

                return advisorSpec;
            } catch (Throwable t) {
                LOGGER.warn("Failed to load AdvisorSpec. \n  {}: {}", getSpecType(), className, t);
                return null;
            }
        }

        private AdvisorSpec.ExprPointcutSpec loadFactoryClass(FactoryContext factoryContext, String className) {
            try {
                Class<?> clazz = factoryContext.getClassLoader().loadClass(className);
                AdvisorSpec.ExprPointcutSpec.Factory factory = (AdvisorSpec.ExprPointcutSpec.Factory) factoryContext.getObjectFactory().createObject(clazz);

                ExprPointcutSpec advisorSpec = factory.getAdvisorSpec();
                if(advisorSpec == null) {
                    LOGGER.warn("Ignored AdvisorSpec is null. \n  {}: {} \n", getSpecType(), className);
                    return null;
                }

                String advisorName = advisorSpec.getAdvisorName();
                boolean noAdvisorName = !StringUtils.hasText(advisorName) || AdvisorSpec.ExprPointcutSpec.Default.class.getName().equals(advisorName);

                ElementMatcher<ConditionContext> condition = advisorSpec.getCondition();
                boolean noCondition = condition == null;

                if(noAdvisorName || noCondition) 
                    advisorSpec = new AdvisorSpec.ExprPointcutSpec.Default(
                            noAdvisorName ? className : advisorName,
                            noCondition ? AdvisorSpec.TRUE : condition,
                            advisorSpec.isPerInstance(), advisorSpec.getAdviceClassName(),
                            advisorSpec.getPointcutExpression(), advisorSpec.getOrder());

                return advisorSpec;
            } catch (Throwable t) {
                LOGGER.warn("Failed to load AdvisorSpec. \n  {}: {}", getSpecType(), className, t);
                return null;
            }
        }
    }


    public class ForAspectJ extends AbstractBase<AspectJAdvisorSpec> {

        private static final List<Class<? extends Annotation>> ADVICE_ANNOTATIONS = Arrays.asList(
                Before.class, After.class, 
                AfterReturning.class, AfterThrowing.class, 
                Around.class);


        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AspectJAdvisorSpec.class.getSimpleName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected List<AspectJAdvisorSpec> doScanSpecs(FactoryContext factoryContext) {
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

        private Collection<AspectJAdvisorSpec> parseAspectJClass(FactoryContext factoryContext, String aspectJClassName) {
            TypeDescription aspectJType = factoryContext.getTypePool().describe(aspectJClassName).resolve();
            if(aspectJType == null) 
                return Collections.emptyList();

            AdvisorSpec aspectJSpec = parseAspectSpec(factoryContext, aspectJType);
            Collection<AspectJAdvisorSpec> parsedAdvisorSpecs = parseAdvisorSpec(factoryContext, aspectJSpec, aspectJType);

            if(parsedAdvisorSpecs.size() == 0) {
                LOGGER.warn("Ignored AdvisorSpec contains no advice methods. \n  {}: {} \n", 
                        AspectJAdvisorSpec.class.getSimpleName(), aspectJClassName );
                return Collections.emptyList();
            }

            return parsedAdvisorSpecs;
        }

        private AdvisorSpec parseAspectSpec(FactoryContext factoryContext, TypeDescription aspectJType) {
            ElementMatcher<ConditionContext> aspectCondition = parseCondition(factoryContext, aspectJType.getDeclaredAnnotations());
            if(aspectCondition == null)
                aspectCondition = AdvisorSpec.TRUE;

            return new AdvisorSpec.AbstractBase(aspectCondition, false, aspectJType.getTypeName(), 0) {};
        }

        private Collection<AspectJAdvisorSpec> parseAdvisorSpec(FactoryContext factoryContext,
                AdvisorSpec aspectJSpec, TypeDescription aspectJType) {
            String aspectJClassName = aspectJSpec.getAdviceClassName();

            MethodList<InDefinedShape> declaredMethods = aspectJType.getDeclaredMethods();
            Map<String, AspectJAdvisorSpec> advisorSpecMap = new HashMap<>(declaredMethods.size());
            for(MethodDescription.InDefinedShape aspectJMethod : declaredMethods) {
                AnnotationList annotations = aspectJMethod.getDeclaredAnnotations();

                for(Class<? extends Annotation> annotationType : ADVICE_ANNOTATIONS) {
                    AnnotationDescription aspectJAnnotation = annotations.ofType(annotationType);
                    if(aspectJAnnotation == null)
                        continue;

                    if(aspectJMethod.isAbstract()) {
                        LOGGER.warn("Ignored abstract AspectJ advice method. \n  {}: {} \n  AdviceMethod: {} \n",
                                getSpecType(), aspectJClassName, MethodUtils.getMethodSignature(aspectJMethod) );
                        continue;
                    }

                    if(aspectJMethod.isPrivate()) {
                        LOGGER.warn("Ignored private AspectJ advice method. \n  {}: {} \n  AdviceMethod: {} \n",
                                getSpecType(), aspectJClassName, MethodUtils.getMethodSignature(aspectJMethod) );
                        continue;
                    }

                    // create AspectJAdvisorSpec
                    ElementMatcher<ConditionContext> condition = parseCondition(factoryContext, annotations);
                    if(condition == null)
                        condition = AdvisorSpec.TRUE;

                    AspectJAdvisorSpec aspectJAdvisorSpec = AspectJAdvisorSpec.Parser.parse( 
                            new ElementMatcher.Junction.Conjunction<ConditionContext>(aspectJSpec.getCondition(), condition),
                            aspectJSpec.isPerInstance(), aspectJSpec.getOrder() , 
                            aspectJType, aspectJMethod, aspectJAnnotation);
                    if(aspectJAdvisorSpec == null)
                        continue;

                    if(advisorSpecMap.containsKey(aspectJAdvisorSpec.getAdvisorName()))
                        LOGGER.warn("Ignored duplicate name AspectJ advice method. \n  {}: {} \n  AdviceMethod: {} \n",
                                getSpecType(), aspectJClassName, MethodUtils.getMethodSignature(aspectJMethod) );
                    else
                        advisorSpecMap.put(aspectJAdvisorSpec.getAdvisorName(), aspectJAdvisorSpec);
                }
            }

            return advisorSpecMap.values();
        }

        private ElementMatcher<ConditionContext> parseCondition(FactoryContext factoryContext, AnnotationList annotations) {
            AnnotationDescription annotationDescription = annotations.ofType(Conditional.class);
            if(annotationDescription == null)
                return null;

            return AdvisorCondition.create(factoryContext, annotationDescription);
        }
    }
}
