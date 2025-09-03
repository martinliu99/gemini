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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.FactoryContext;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.AdvisorSpec.ExprPointcutSpec;
import io.gemini.api.aop.AdvisorSpec.PojoPointcutSpec;
import io.gemini.api.aop.condition.ConditionContext;
import io.gemini.api.aop.Pointcut;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.Assert;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;
import io.github.classgraph.ClassInfo;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorSpecScanner<T extends AdvisorSpec> {

    List<T> scan(FactoryContext factoryContext);


    abstract class AbstractBase<T extends AdvisorSpec> extends ClassScanner.InstantiableClassInfoFilter
            implements AdvisorSpecScanner<T> {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AdvisorSpecScanner.class);

        protected String scannerName = this.getClass().getName();


        @Override
        public List<T> scan(FactoryContext factoryContext) {
            Assert.notNull(factoryContext, "'factoryContext' must not be null");

            List<T> advisorSpecs = null;
            try {
                advisorSpecs = this.doScanSpecs(factoryContext);

                advisorSpecs = advisorSpecs.stream()
                .filter( advisorSpec -> doValidateSpecInstance(advisorSpec) )
                .collect( Collectors.toList());


                if(CollectionUtils.isEmpty(advisorSpecs)) {
                    LOGGER.info("Did not find AdvisorSpec.{} via '{}'.", getSpecType(), scannerName);
                } else {
                    LOGGER.info("Found {} AdvisorSpec.{} via '{}'.", advisorSpecs.size(), getSpecType(), scannerName);
                }
            } catch(Throwable t) {
                LOGGER.warn("Failed to scan AdvisorSpec.{} via '{}'.", getSpecType(), scannerName, t);
            }

            return advisorSpecs;
        }

        protected abstract String getSpecType();

        protected abstract List<T> doScanSpecs(FactoryContext factoryContext);

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

        protected boolean doValidateSpecInstance(T advisorSpecs) {
            if(advisorSpecs == null)
                return false;

            // check advice definition
            if(StringUtils.hasText(advisorSpecs.getAdviceClassName()) == false) {
                LOGGER.warn("Ignored AdvisorSpec with empty adviceClassName. \n  {}: {} \n", 
                        getSpecType(), advisorSpecs.getAdvisorName() );
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

        @SuppressWarnings("unchecked")
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
                    Class<AdvisorSpec.PojoPointcutSpec> clazz = (Class<AdvisorSpec.PojoPointcutSpec>) classLoader.loadClass(className);

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
        protected boolean doValidateSpecInstance(AdvisorSpec.PojoPointcutSpec advisorSpec) {
            if(super.doValidateSpecInstance(advisorSpec) == false)
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
        protected boolean doValidateSpecInstance(AdvisorSpec.ExprPointcutSpec advisorSpec) {
            if(super.doValidateSpecInstance(advisorSpec) == false)
                return false;

            if(StringUtils.hasText(advisorSpec.getPointcutExpression()) == false) {
                LOGGER.warn("Ignored AdvisorSpec with empty pointcutExpression. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return true;
        }
    }


    public class ForAspectJ extends AbstractBase<AdvisorSpec.AspectJSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AdvisorSpec.AspectJSpec.class.getSimpleName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected List<AdvisorSpec.AspectJSpec> doScanSpecs(FactoryContext factoryContext) {
            List<AdvisorSpec.AspectJSpec> advisorSpecs = new ArrayList<>();

            List<String> classNames = factoryContext.getClassScanner()
                    .getClassesWithAnnotation( Aspect.class.getName() )
                    .filter(this)
                    .getNames();

            for(String className : classNames) {
                advisorSpecs.add(
                        new AdvisorSpec.AspectJSpec.Default(
                                className, 
                                factoryContext.getDefaultCondition(), 
                                false, className, 0) );
            }

            return advisorSpecs;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doValidateSpecInstance(AdvisorSpec.AspectJSpec advisorSpec) {
            if(StringUtils.hasText(advisorSpec.getAspectJClassName()) == false) {
                LOGGER.warn("Ignored AdvisorSpec with empty aspectJClassName. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return true;
        }
    }
}
