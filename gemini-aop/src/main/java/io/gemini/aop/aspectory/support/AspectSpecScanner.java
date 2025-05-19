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
package io.gemini.aop.aspectory.support;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.aspectory.AspectoryContext;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aspect.AspectSpec;
import io.gemini.api.aspect.AspectSpec.ExprPointcutSpec;
import io.gemini.api.aspect.AspectSpec.PojoPointcutSpec;
import io.gemini.api.aspect.Pointcut;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.Assert;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;
import io.github.classgraph.ClassInfo;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AspectSpecScanner<T extends AspectSpec> {

    List<T> scan(AspectoryContext aspectoryContext);


    abstract class AbstractBase<T extends AspectSpec> extends ClassScanner.InstantiableClassInfoFilter
            implements AspectSpecScanner<T> {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AspectSpecScanner.class);

        protected String scannerName = this.getClass().getName();


        @Override
        public List<T> scan(AspectoryContext aspectoryContext) {
            Assert.notNull(aspectoryContext, "'aspectoryContext' must not be null");

            List<T> aspectSpecs = null;
            try {
                aspectSpecs = this.doScanSpecs(aspectoryContext);

                aspectSpecs = aspectSpecs.stream()
                .filter( aspectSpec -> doValidateSpecInstance(aspectSpec) )
                .collect( Collectors.toList());


                if(CollectionUtils.isEmpty(aspectSpecs)) {
                    LOGGER.info("Did not find AspectSpec.{} via '{}'.", getSpecType(), scannerName);
                } else {
                    LOGGER.info("Found {} AspectSpec.{} via '{}'.", aspectSpecs.size(), getSpecType(), scannerName);
                }
            } catch(Throwable t) {
                LOGGER.warn("Failed to scan AspectSpec.{} via '{}'.", getSpecType(), scannerName, t);
            }

            return aspectSpecs;
        }

        protected abstract String getSpecType();

        protected abstract List<T> doScanSpecs(AspectoryContext aspectoryContext);

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(ClassInfo classInfo) {
            if(super.accept(classInfo) == true)
                return true;

            LOGGER.warn("Ignored AspectSpec class is NOT top-level or nested, concrete class. \n  {}: {} \n"
                    + "  Use @{} annotation to ignore this illegal AspectSpec. \n", 
                    getSpecType(), classInfo.getName(), NoScanning.class.getName());

            return false;
        }

        protected boolean doValidateSpecInstance(T aspectSpec) {
            if(aspectSpec == null)
                return false;

            // check advice definition
            if(StringUtils.hasText(aspectSpec.getAdviceClassName()) == false) {
                LOGGER.warn("Ignored AspectSpec with empty adviceClassName. \n  {}: {} \n", 
                        getSpecType(), aspectSpec.getAspectName() );
                return false;
            }

            return true;
        }
    }


    public class ForPojoPointcut extends AbstractBase<AspectSpec.PojoPointcutSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AspectSpec.PojoPointcutSpec.class.getSimpleName();
        }

        @Override
        protected List<AspectSpec.PojoPointcutSpec> doScanSpecs(AspectoryContext aspectoryContext) {
            List<AspectSpec.PojoPointcutSpec> aspectSpecs = new ArrayList<>();
            doScanImplementor(aspectoryContext, aspectSpecs);
            doScanFactory(aspectoryContext, aspectSpecs);

            return aspectSpecs;
        }

        @SuppressWarnings("unchecked")
        protected void doScanImplementor(AspectoryContext aspectoryContext, List<AspectSpec.PojoPointcutSpec> aspectSpecs) {
            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesImplementing( AspectSpec.PojoPointcutSpec.class.getName() )
                    .filter(this)
                    .getNames();

            ClassLoader classLoader = aspectoryContext.getAspectClassLoader();
            ObjectFactory objectFactory = aspectoryContext.getObjectFactory();
            String pojoPointcutSpecClassName = AspectSpec.PojoPointcutSpec.Default.class.getName();

            for(String className : classNames) {
                try {
                    Class<AspectSpec.PojoPointcutSpec> clazz = (Class<AspectSpec.PojoPointcutSpec>) classLoader.loadClass(className);

                    AspectSpec.PojoPointcutSpec aspectSpec = (AspectSpec.PojoPointcutSpec) objectFactory.createObject(clazz);

                    String aspectName = aspectSpec.getAspectName();
                    if( !StringUtils.hasText(aspectName) || pojoPointcutSpecClassName.equals(aspectName) ) 
                        aspectSpec = new AspectSpec.PojoPointcutSpec.Default(
                                className, 
                                aspectSpec.isPerInstance(), aspectSpec.getAdviceClassName(),
                                aspectSpec.getPointcut(), aspectSpec.getOrder());

                    aspectSpecs.add(aspectSpec);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AspectSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        protected void doScanFactory(AspectoryContext aspectoryContext, List<AspectSpec.PojoPointcutSpec> aspectSpecs) {
            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesImplementing( AspectSpec.PojoPointcutSpec.Factory.class.getName() )
                    .filter(this)
                    .getNames();
            ClassLoader classLoader = aspectoryContext.getAspectClassLoader();
            ObjectFactory objectFactory = aspectoryContext.getObjectFactory();
            String pojoPointcutSpecClassName = AspectSpec.PojoPointcutSpec.Default.class.getName();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AspectSpec.PojoPointcutSpec.Factory factory = (AspectSpec.PojoPointcutSpec.Factory) objectFactory.createObject(clazz);

                    PojoPointcutSpec aspectSpec = factory.getAspectSpec();
                    if(aspectSpec == null) {
                        LOGGER.warn("Ignored AspectSpec is null. \n  {}: {} \n", getSpecType(), className);
                        continue;
                    }

                    String aspectName = aspectSpec.getAspectName();
                    if( !StringUtils.hasText(aspectName) || pojoPointcutSpecClassName.equals(aspectName) ) 
                        aspectSpec = new AspectSpec.PojoPointcutSpec.Default(
                                className, 
                                aspectSpec.isPerInstance(), aspectSpec.getAdviceClassName(),
                                aspectSpec.getPointcut(), aspectSpec.getOrder());

                    aspectSpecs.add(aspectSpec);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AspectSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        @Override
        protected boolean doValidateSpecInstance(AspectSpec.PojoPointcutSpec aspectSpec) {
            if(super.doValidateSpecInstance(aspectSpec) == false)
                return false;

            Pointcut pointcut = aspectSpec.getPointcut();
            if(pointcut == null ||
                    (pointcut.getTypeMatcher() == null && pointcut.getMethodMatcher() == null)) {
                LOGGER.warn("Ignored AspectSpec with null pointuct. \n  {}: {} \n", 
                        getSpecType(), aspectSpec.getAspectName() );
                return false;
            }

            return true;
        }
    }


    public class ForExprPointcut extends AbstractBase<AspectSpec.ExprPointcutSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AspectSpec.ExprPointcutSpec.class.getSimpleName();
        }

        @Override
        protected List<AspectSpec.ExprPointcutSpec> doScanSpecs(AspectoryContext aspectoryContext) {
            List<AspectSpec.ExprPointcutSpec> aspectSpecs = new ArrayList<>();
            doScanImplementor(aspectoryContext, aspectSpecs);
            doScanFactory(aspectoryContext, aspectSpecs);

            return aspectSpecs;
        }

        protected void doScanImplementor(AspectoryContext aspectoryContext, List<AspectSpec.ExprPointcutSpec> aspectSpecs) {
            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesImplementing( AspectSpec.ExprPointcutSpec.class.getName() )
                    .filter(this)
                    .getNames();

            ClassLoader classLoader = aspectoryContext.getAspectClassLoader();
            ObjectFactory objectFactory = aspectoryContext.getObjectFactory();
            String exprPointcutSpecClassName = AspectSpec.ExprPointcutSpec.Default.class.getName();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);

                    AspectSpec.ExprPointcutSpec aspectSpec = (AspectSpec.ExprPointcutSpec) objectFactory.createObject(clazz);

                    String aspectName = aspectSpec.getAspectName();
                    if( !StringUtils.hasText(aspectName) || exprPointcutSpecClassName.equals(aspectName) ) 
                        aspectSpec = new AspectSpec.ExprPointcutSpec.Default(
                                className, 
                                aspectSpec.isPerInstance(), aspectSpec.getAdviceClassName(),
                                aspectSpec.getPointcutExpression(), aspectSpec.getOrder());

                    aspectSpecs.add(aspectSpec);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AspectSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        protected void doScanFactory(AspectoryContext aspectoryContext, List<AspectSpec.ExprPointcutSpec> aspectSpecs) {
            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesImplementing( AspectSpec.ExprPointcutSpec.Factory.class.getName() )
                    .filter(this)
                    .getNames();
            ClassLoader classLoader = aspectoryContext.getAspectClassLoader();
            ObjectFactory objectFactory = aspectoryContext.getObjectFactory();
            String exprPointcutSpecClassName = AspectSpec.ExprPointcutSpec.Default.class.getName();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AspectSpec.ExprPointcutSpec.Factory factory = (AspectSpec.ExprPointcutSpec.Factory) objectFactory.createObject(clazz);

                    ExprPointcutSpec aspectSpec = factory.getAspectSpec();
                    if(aspectSpec == null) {
                        LOGGER.warn("Ignored AspectSpec is null. \n  {}: {} \n", getSpecType(), className);
                        continue;
                    }

                    String aspectName = aspectSpec.getAspectName();
                    if( !StringUtils.hasText(aspectName) || exprPointcutSpecClassName.equals(aspectName) ) 
                        aspectSpec = new AspectSpec.ExprPointcutSpec.Default(
                                className, 
                                aspectSpec.isPerInstance(), aspectSpec.getAdviceClassName(),
                                aspectSpec.getPointcutExpression(), aspectSpec.getOrder());

                    aspectSpecs.add(aspectSpec);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AspectSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doValidateSpecInstance(AspectSpec.ExprPointcutSpec aspectSpec) {
            if(super.doValidateSpecInstance(aspectSpec) == false)
                return false;

            if(StringUtils.hasText(aspectSpec.getPointcutExpression()) == false) {
                LOGGER.warn("Ignored AspectSpec with empty pointcutExpression. \n  {}: {} \n", 
                        getSpecType(), aspectSpec.getAspectName() );
                return false;
            }

            return true;
        }
    }


    public class ForAspectJ extends AbstractBase<AspectSpec.AspectJSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AspectSpec.AspectJSpec.class.getSimpleName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected List<AspectSpec.AspectJSpec> doScanSpecs(AspectoryContext aspectoryContext) {
            List<AspectSpec.AspectJSpec> aspectSpecs = new ArrayList<>();

            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesWithAnnotation( Aspect.class.getName() )
                    .filter(this)
                    .getNames();

            for(String className : classNames) {
                aspectSpecs.add(
                        new AspectSpec.AspectJSpec.Default(className, false, className, 0));
            }

            return aspectSpecs;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doValidateSpecInstance(AspectSpec.AspectJSpec aspectSpec) {
            if(StringUtils.hasText(aspectSpec.getAspectJClassName()) == false) {
                LOGGER.warn("Ignored AspectSpec with empty aspectJClassName. \n  {}: {} \n", 
                        getSpecType(), aspectSpec.getAspectName() );
                return false;
            }

            return true;
        }
    }
}
