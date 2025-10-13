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
import java.util.List;
import java.util.Map;

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
import io.gemini.api.aop.Pointcut;
import io.gemini.api.aop.condition.ConditionContext;
import io.gemini.api.aop.condition.Conditional;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.ObjectFactory;
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

    List<? extends AdvisorSpec> scan(FactoryContext factoryContext);


    abstract class AbstractBase<S extends AdvisorSpec> extends ClassScanner.InstantiableClassInfoFilter 
            implements AdvisorSpecScanner {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AdvisorSpecScanner.class);

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
        public List<? extends AdvisorSpec> scan(FactoryContext factoryContext) {
            Assert.notNull(factoryContext, "'factoryContext' must not be null");

            // scan AdvisorSpec implementation
            try {
                List<S> advisorSpecs = this.doScanSpecs(factoryContext);

                if(advisorSpecs != null) {
                    Iterator<S> it = advisorSpecs.iterator();
                    while(it.hasNext()) {
                        S advisorSpec = it.next();
                        if(advisorSpec == null || doValidateSpec(advisorSpec) == false)
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


        protected boolean doValidateSpec(S advisorSpec) {
            if(advisorSpec == null)
                return false;

            // check advice definition
            if(StringUtils.hasText(advisorSpec.getAdviceClassName()) == false) {
                LOGGER.warn("Ignored AdvisorSpec with empty adviceClassName. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return true;
        }
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
            List<AdvisorSpec.PojoPointcutSpec> advisorSpecss = new ArrayList<>();
            doScanImplementor(factoryContext, advisorSpecss);
            doScanFactory(factoryContext, advisorSpecss);

            return advisorSpecss;
        }

        protected void doScanImplementor(FactoryContext factoryContext, List<AdvisorSpec.PojoPointcutSpec> advisorSpecs) {
            List<String> classNames = factoryContext.getClassScanner()
                    .getClassesImplementing( AdvisorSpec.PojoPointcutSpec.class.getName() )
                    .filter(this)
                    .getNames();

            ClassLoader classLoader = factoryContext.getClassLoader();
            ObjectFactory objectFactory = factoryContext.getObjectFactory();
            String defaultSpecClassName = AdvisorSpec.PojoPointcutSpec.Default.class.getName();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AdvisorSpec.PojoPointcutSpec advisorSpec = (AdvisorSpec.PojoPointcutSpec) objectFactory.createObject(clazz);

                    String advisorName = advisorSpec.getAdvisorName();
                    boolean noAdvisorName = !StringUtils.hasText(advisorName) || defaultSpecClassName.equals(advisorName);

                    ElementMatcher<ConditionContext> condition = advisorSpec.getCondition();
                    boolean noCondition = condition == null || AdvisorSpec.TRUE == condition;

                    if(noAdvisorName || noCondition)
                        advisorSpec = new AdvisorSpec.PojoPointcutSpec.Default(
                                noAdvisorName ? className : advisorName,
                                noCondition ? factoryContext.getDefaultCondition() : condition,
                                advisorSpec.isPerInstance(), advisorSpec.getAdviceClassName(),
                                advisorSpec.getPointcut(), advisorSpec.getOrder());

                    advisorSpecs.add(advisorSpec);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AdvisorSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        protected void doScanFactory(FactoryContext factoryContext, List<AdvisorSpec.PojoPointcutSpec> advisorSpecs) {
            List<String> classNames = factoryContext.getClassScanner()
                    .getClassesImplementing( AdvisorSpec.PojoPointcutSpec.Factory.class.getName() )
                    .filter(this)
                    .getNames();
            ClassLoader classLoader = factoryContext.getClassLoader();
            ObjectFactory objectFactory = factoryContext.getObjectFactory();
            String defaultSpecClassName = AdvisorSpec.PojoPointcutSpec.Default.class.getName();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AdvisorSpec.PojoPointcutSpec.Factory factory = (AdvisorSpec.PojoPointcutSpec.Factory) objectFactory.createObject(clazz);

                    PojoPointcutSpec advisorSpec = factory.getAdvisorSpec();
                    if(advisorSpec == null) {
                        LOGGER.warn("Ignored AdvisorSpec is null. \n  {}: {} \n", getSpecType(), className);
                        continue;
                    }

                    String advisorName = advisorSpec.getAdvisorName();
                    boolean noAdvisorName = !StringUtils.hasText(advisorName) || defaultSpecClassName.equals(advisorName);

                    ElementMatcher<ConditionContext> condition = advisorSpec.getCondition();
                    boolean noCondition = condition == null || AdvisorSpec.TRUE == condition;

                    if(noAdvisorName || noCondition) 
                        advisorSpec = new AdvisorSpec.PojoPointcutSpec.Default(
                                noAdvisorName ? className : advisorName,
                                noCondition ? factoryContext.getDefaultCondition() : condition,
                                advisorSpec.isPerInstance(), advisorSpec.getAdviceClassName(),
                                advisorSpec.getPointcut(), advisorSpec.getOrder());

                    advisorSpecs.add(advisorSpec);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AdvisorSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        @Override
        protected boolean doValidateSpec(AdvisorSpec.PojoPointcutSpec advisorSpec) {
            if(super.doValidateSpec(advisorSpec) == false)
                return false;

            Pointcut pointcut = advisorSpec.getPointcut();
            if(pointcut == null ||
                    (pointcut.getTypeMatcher() == null && pointcut.getMethodMatcher() == null)) {
                LOGGER.warn("Ignored AdvisorSpec with null pointuct. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return true;
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
            List<AdvisorSpec.ExprPointcutSpec> advisorSpecs = new ArrayList<>();
            doScanImplementor(factoryContext, advisorSpecs);
            doScanFactory(factoryContext, advisorSpecs);

            return advisorSpecs;
        }

        protected void doScanImplementor(FactoryContext factoryContext, List<AdvisorSpec.ExprPointcutSpec> advisorSpecs) {
            List<String> classNames = factoryContext.getClassScanner()
                    .getClassesImplementing( AdvisorSpec.ExprPointcutSpec.class.getName() )
                    .filter(this)
                    .getNames();

            ClassLoader classLoader = factoryContext.getClassLoader();
            ObjectFactory objectFactory = factoryContext.getObjectFactory();
            String defaultSpecClassName = AdvisorSpec.ExprPointcutSpec.Default.class.getName();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AdvisorSpec.ExprPointcutSpec advisorSpec = (AdvisorSpec.ExprPointcutSpec) objectFactory.createObject(clazz);

                    String advisorName = advisorSpec.getAdvisorName();
                    boolean noAdvisorName = !StringUtils.hasText(advisorName) || defaultSpecClassName.equals(advisorName);

                    ElementMatcher<ConditionContext> condition = advisorSpec.getCondition();
                    boolean noCondition = condition == null || AdvisorSpec.TRUE == condition;

                    if(noAdvisorName || noCondition) 
                        advisorSpec = new AdvisorSpec.ExprPointcutSpec.Default(
                                noAdvisorName ? className : advisorName,
                                noCondition ? factoryContext.getDefaultCondition() : condition,
                                advisorSpec.isPerInstance(), advisorSpec.getAdviceClassName(),
                                advisorSpec.getPointcutExpression(), advisorSpec.getOrder());

                    advisorSpecs.add(advisorSpec);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AdvisorSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        protected void doScanFactory(FactoryContext factoryContext, List<AdvisorSpec.ExprPointcutSpec> advisorSpecs) {
            List<String> classNames = factoryContext.getClassScanner()
                    .getClassesImplementing( AdvisorSpec.ExprPointcutSpec.Factory.class.getName() )
                    .filter(this)
                    .getNames();
            ClassLoader classLoader = factoryContext.getClassLoader();
            ObjectFactory objectFactory = factoryContext.getObjectFactory();
            String defaultSpecClassName = AdvisorSpec.ExprPointcutSpec.Default.class.getName();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AdvisorSpec.ExprPointcutSpec.Factory factory = (AdvisorSpec.ExprPointcutSpec.Factory) objectFactory.createObject(clazz);

                    ExprPointcutSpec advisorSpec = factory.getAdvisorSpec();
                    if(advisorSpec == null) {
                        LOGGER.warn("Ignored AdvisorSpec is null. \n  {}: {} \n", getSpecType(), className);
                        continue;
                    }

                    String advisorName = advisorSpec.getAdvisorName();
                    boolean noAdvisorName = !StringUtils.hasText(advisorName) || defaultSpecClassName.equals(advisorName);

                    ElementMatcher<ConditionContext> condition = advisorSpec.getCondition();
                    boolean noCondition = condition == null || AdvisorSpec.TRUE == condition;

                    if(noAdvisorName || noCondition) 
                        advisorSpec = new AdvisorSpec.ExprPointcutSpec.Default(
                                noAdvisorName ? className : advisorName,
                                noCondition ? factoryContext.getDefaultCondition() : condition,
                                advisorSpec.isPerInstance(), advisorSpec.getAdviceClassName(),
                                advisorSpec.getPointcutExpression(), advisorSpec.getOrder());

                    advisorSpecs.add(advisorSpec);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AdvisorSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doValidateSpec(AdvisorSpec.ExprPointcutSpec advisorSpec) {
            if(super.doValidateSpec(advisorSpec) == false)
                return false;

            if(StringUtils.hasText(advisorSpec.getPointcutExpression()) == false) {
                LOGGER.warn("Ignored AdvisorSpec with empty pointcutExpression. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return true;
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
            List<AspectJAdvisorSpec> advisorSpecs = new ArrayList<>();

            List<String> classNames = factoryContext.getClassScanner()
                    .getClassesWithAnnotation( Aspect.class.getName() )
                    .filter(this)
                    .getNames();

            for(String className : classNames) {
                doParseAspectJClass(factoryContext, className, advisorSpecs);
            }

            return advisorSpecs;
        }

        protected void doParseAspectJClass(FactoryContext factoryContext, String aspectJClassName,
                List<AspectJAdvisorSpec> advisorSpecs) {
            TypeDescription aspectJType = factoryContext.getTypePool().describe(aspectJClassName).resolve();
            if(aspectJType == null) 
                return;

            AdvisorSpec aspectJSpec = parseAspectSpec(factoryContext, aspectJType);
            Collection<AspectJAdvisorSpec> parsedAdvisorSpecs = parseAdvisorSpec(factoryContext, aspectJSpec, aspectJType);

            if(parsedAdvisorSpecs.size() == 0) {
                LOGGER.warn("Ignored AdvisorSpec contains no advice methods. \n  {}: {} \n", 
                        AspectJAdvisorSpec.class.getSimpleName(), aspectJClassName );
                return;
            }

            advisorSpecs.addAll(parsedAdvisorSpecs);
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

                    String adviceClassName = aspectJClassName + "_" + aspectJMethod.getName() + "_" + annotationType.getSimpleName();
                    if(advisorSpecMap.containsKey(adviceClassName)) {
                        LOGGER.warn("Ignored duplicate name AspectJ advice method. \n  {}: {} \n  AdviceMethod: {} \n",
                                getSpecType(), aspectJClassName, MethodUtils.getMethodSignature(aspectJMethod) );
                        continue;
                    }

                    ElementMatcher<ConditionContext> condition = parseCondition(factoryContext, annotations);
                    if(condition == null)
                        condition = factoryContext.getDefaultCondition();

                    advisorSpecMap.put(
                            adviceClassName,
                            AspectJAdvisorSpec.create(adviceClassName, 
                                    new ElementMatcher.Junction.Conjunction<ConditionContext>(aspectJSpec.getCondition(), condition),
                                    aspectJSpec.isPerInstance(), 
                                    adviceClassName,
                                    aspectJType,
                                    aspectJMethod, 
                                    aspectJAnnotation, 
                                    aspectJSpec.getOrder() ) );
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

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doValidateSpec(AspectJAdvisorSpec advisorSpec) {
            if(super.doValidateSpec(advisorSpec) == false)
                return false;

            if(advisorSpec.getAspectJType() == null) {
                LOGGER.warn("Ignored AdvisorSpec with empty aspectJClassName. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            if(StringUtils.hasText(advisorSpec.getPointcutExpression()) == false) {
                LOGGER.warn("Ignored AdvisorSpec with empty pointcutExpression. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return true;
        }
    }
}
