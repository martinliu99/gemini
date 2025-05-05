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

import io.gemini.aop.aspectory.AspectSpecHolder;
import io.gemini.aop.aspectory.AspectoryContext;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aspect.AspectSpec;
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

    List<AspectSpecHolder<T>> scan(AspectoryContext aspectoryContext);


    abstract class AbstractBase<T extends AspectSpec> extends ClassScanner.AccessibleClassInfoFilter
            implements AspectSpecScanner<T> {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AspectSpecScanner.class);

        protected String scannerName = this.getClass().getName();


        @Override
        public List<AspectSpecHolder<T>> scan(AspectoryContext aspectoryContext) {
            Assert.notNull(aspectoryContext, "'aspectoryContext' must not be null");

            List<AspectSpecHolder<T>> aspectSpecHolders = null;
            try {
                aspectSpecHolders = this.doScanSpecs(aspectoryContext);

                aspectSpecHolders = aspectSpecHolders.stream()
                .filter( aspectSpecHolder -> doValidateSpecInstance(aspectSpecHolder) )
                .collect( Collectors.toList());


                if(CollectionUtils.isEmpty(aspectSpecHolders)) {
                    LOGGER.info("Did not find AspectSpec.{} via '{}'.", getSpecType(), scannerName);
                } else {
                    LOGGER.info("Found {} AspectSpec.{} via '{}'.", aspectSpecHolders.size(), getSpecType(), scannerName);
                }
            } catch(Throwable t) {
                LOGGER.warn("Failed to scan AspectSpec.{} via '{}'.", getSpecType(), scannerName, t);
            }

            return aspectSpecHolders;
        }


        protected abstract String getSpecType();


        protected abstract List<AspectSpecHolder<T>> doScanSpecs(AspectoryContext aspectoryContext);

        /* 
         * @see io.github.classgraph.ClassInfoList.ClassInfoFilter#accept(io.github.classgraph.ClassInfo) 
         */
        @Override
        public boolean accept(ClassInfo classInfo) {
            if(super.accept(classInfo) == true)
                return true;

            LOGGER.warn("Ignored AspectSpec since AspectSpec class must be public top-level class or public static member class (non-interface, non-abstract, non-enum, non-inner, non-local). \n  {}: {} \n"
                    + "  Use @{} annotation to remove this AspectSpec class and this warning. \n", 
                    getSpecType(), classInfo.getName(), NoScanning.class.getName());

            return false;
        }

        protected boolean doValidateSpecInstance(AspectSpecHolder<T> aspectSpecHolder) {
            if(aspectSpecHolder == null)
                return false;

            // check advice definition
            if(StringUtils.hasText(aspectSpecHolder.getAspectSpec().getAdviceClassName()) == false) {
                LOGGER.warn("Ignored AspectSpec since adviceClassName must not be empty. \n  {}: {} \n", 
                        getSpecType(), aspectSpecHolder.getAspectName() );
                return false;
            }

            return true;
        }
    }


    public class ForPojoPointcut extends AbstractBase<AspectSpec.PojoPointcutSpec> {

        /* 
         * @see io.gemini.aop.aspectory.support.AspectSpecScanner.AbstractBase#getSpecType()
         */
        @Override
        protected String getSpecType() {
            return AspectSpec.PojoPointcutSpec.class.getSimpleName();
        }

        @Override
        protected List<AspectSpecHolder<AspectSpec.PojoPointcutSpec>> doScanSpecs(AspectoryContext aspectoryContext) {
            List<AspectSpecHolder<AspectSpec.PojoPointcutSpec>> aspectSpecHolders = new ArrayList<>();
            doScanImplementor(aspectoryContext, aspectSpecHolders);
            doScanFactory(aspectoryContext, aspectSpecHolders);

            return aspectSpecHolders;
        }

        @SuppressWarnings("unchecked")
        protected void doScanImplementor(AspectoryContext aspectoryContext, List<AspectSpecHolder<AspectSpec.PojoPointcutSpec>> aspectSpecHolders) {
            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesImplementing( AspectSpec.PojoPointcutSpec.class.getName() )
                    .filter(this)
                    .getNames();

            ClassLoader classLoader = aspectoryContext.getAspectClassLoader();
            ObjectFactory objectFactory = aspectoryContext.getObjectFactory();

            for(String className : classNames) {
                try {
                    Class<AspectSpec.PojoPointcutSpec> clazz = (Class<AspectSpec.PojoPointcutSpec>) classLoader.loadClass(className);

                    aspectSpecHolders.add( 
                            new AspectSpecHolder<AspectSpec.PojoPointcutSpec>(
                                    className, 
                                    (AspectSpec.PojoPointcutSpec) objectFactory.createObject(clazz)
                            )
                    );
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AspectSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        protected void doScanFactory(AspectoryContext aspectoryContext, List<AspectSpecHolder<AspectSpec.PojoPointcutSpec>> aspectSpecHolders) {
            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesImplementing( AspectSpec.PojoPointcutSpec.Factory.class.getName() )
                    .filter(this)
                    .getNames();
            ClassLoader classLoader = aspectoryContext.getAspectClassLoader();
            ObjectFactory objectFactory = aspectoryContext.getObjectFactory();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    AspectSpec.PojoPointcutSpec.Factory factory = (AspectSpec.PojoPointcutSpec.Factory) objectFactory.createObject(clazz);

                    aspectSpecHolders.add(  
                            new AspectSpecHolder<AspectSpec.PojoPointcutSpec>(
                                    className, factory.getAspectSpec()
                            )
                    );
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AspectSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        @Override
        protected boolean doValidateSpecInstance(AspectSpecHolder<AspectSpec.PojoPointcutSpec> aspectSpecHolder) {
            if(super.doValidateSpecInstance(aspectSpecHolder) == false)
                return false;

            Pointcut pointcut = aspectSpecHolder.getAspectSpec().getPointcut();
            if(pointcut == null ||
                    (pointcut.getTypeMatcher() == null && pointcut.getMethodMatcher() == null)) {
                LOGGER.warn("Ignored AspectSpec since pointuct must not be null. \n  {}: {} \n", 
                        getSpecType(), aspectSpecHolder.getAspectName() );
                return false;
            }

            return true;
        }
    }


    public class ForExprPointcut extends AbstractBase<AspectSpec.ExprPointcutSpec> {

        /* 
         * @see io.gemini.aop.aspectory.support.AspectSpecScanner.AbstractBase#getSpecType()
         */
        @Override
        protected String getSpecType() {
            return AspectSpec.ExprPointcutSpec.class.getSimpleName();
        }

        @Override
        protected List<AspectSpecHolder<AspectSpec.ExprPointcutSpec>> doScanSpecs(AspectoryContext aspectoryContext) {
            List<AspectSpecHolder<AspectSpec.ExprPointcutSpec>> aspectSpecs = new ArrayList<>();
            doScanImplementor(aspectoryContext, aspectSpecs);
            doScanFactory(aspectoryContext, aspectSpecs);

            return aspectSpecs;
        }

        protected void doScanImplementor(AspectoryContext aspectoryContext, List<AspectSpecHolder<AspectSpec.ExprPointcutSpec>> aspectSpecHolders) {
            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesImplementing( AspectSpec.ExprPointcutSpec.class.getName() )
                    .filter(this)
                    .getNames();

            ClassLoader classLoader = aspectoryContext.getAspectClassLoader();
            ObjectFactory objectFactory = aspectoryContext.getObjectFactory();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);

                    aspectSpecHolders.add(
                            new AspectSpecHolder<AspectSpec.ExprPointcutSpec>(
                                    className, (AspectSpec.ExprPointcutSpec) objectFactory.createObject(clazz)
                            ) 
                    );
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AspectSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        protected void doScanFactory(AspectoryContext aspectoryContext, List<AspectSpecHolder<AspectSpec.ExprPointcutSpec>> aspectSpecHolders) {
            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesImplementing( AspectSpec.ExprPointcutSpec.Factory.class.getName() )
                    .filter(this)
                    .getNames();
            ClassLoader classLoader = aspectoryContext.getAspectClassLoader();
            ObjectFactory objectFactory = aspectoryContext.getObjectFactory();

            for(String className : classNames) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);

                    AspectSpec.ExprPointcutSpec.Factory factory = (AspectSpec.ExprPointcutSpec.Factory) objectFactory.createObject(clazz);

                    aspectSpecHolders.add( 
                            new AspectSpecHolder<AspectSpec.ExprPointcutSpec>(
                                    className, 
                                    factory.getAspectSpec()
                            ) 
                    );
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load AspectSpec. \n  {}: {}", getSpecType(), className, t);
                }
            }
        }

        @Override
        protected boolean doValidateSpecInstance(AspectSpecHolder<AspectSpec.ExprPointcutSpec> aspectSpecHolder) {
            if(super.doValidateSpecInstance(aspectSpecHolder) == false)
                return false;

            if(StringUtils.hasText(aspectSpecHolder.getAspectSpec().getPointcutExpression()) == false) {
                LOGGER.warn("Ignored AspectSpec since pointcutExpression must not be empty. \n  {}: {} \n", 
                        getSpecType(), aspectSpecHolder.getAspectName() );
                return false;
            }

            return true;
        }
    }


    public class ForAspectJ extends AbstractBase<AspectSpec.AspectJSpec> {

        /* 
         * @see io.gemini.aop.aspectory.support.AspectSpecScanner.AbstractBase#getSpecType()
         */
        @Override
        protected String getSpecType() {
            return AspectSpec.AspectJSpec.class.getSimpleName();
        }

        @Override
        protected List<AspectSpecHolder<AspectSpec.AspectJSpec>> doScanSpecs(AspectoryContext aspectoryContext) {
            List<AspectSpecHolder<AspectSpec.AspectJSpec>> aspectSpecHolders = new ArrayList<>();

            List<String> classNames = aspectoryContext.getClassScanner()
                    .getClassesWithAnnotation( Aspect.class.getName() )
                    .filter(this)
                    .getNames();
            for(String className : classNames) {
                aspectSpecHolders.add(
                        new AspectSpecHolder<AspectSpec.AspectJSpec>(
                                className, new AspectSpec.AspectJSpec.Default(false, null, className, 0)
                        ) 
                );
            }

            return aspectSpecHolders;
        }

        @Override
        protected boolean doValidateSpecInstance(AspectSpecHolder<AspectSpec.AspectJSpec> aspectSpecHolder) {
            if(StringUtils.hasText(aspectSpecHolder.getAspectSpec().getAspectJClassName()) == false) {
                LOGGER.warn("Ignored AspectSpec since aspectJClassName must not be empty. \n  {}: {} \n", 
                        getSpecType(), aspectSpecHolder.getAspectName() );
                return false;
            }

            return true;
        }
    }
}
