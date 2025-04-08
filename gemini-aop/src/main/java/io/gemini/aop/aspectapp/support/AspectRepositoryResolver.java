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
package io.gemini.aop.aspectapp.support;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.aspect.AspectSpec;
import io.gemini.aop.aspectapp.AspectContext;
import io.gemini.aop.aspectapp.AspectSpecHolder;
import io.gemini.aop.aspectapp.support.AspectJAdvice.MethodSpec;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AspectRepositoryResolver<T extends AspectSpec, R extends AspectSpec> {

    boolean support(AspectSpecHolder<T> apsectSpecHolder);

    List<? extends AspectRepository<R>> resolve(
            AspectSpecHolder<T> apsectSpecHolder, AspectContext aspectContext);


    abstract class AbstractBase<T extends AspectSpec, R extends AspectSpec> 
            implements AspectRepositoryResolver<T, R> {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AspectRepositoryResolver.class);

        protected String resolverName = this.getClass().getName();


        @Override
        public List<? extends AspectRepository<R>> resolve(
                AspectSpecHolder<T> apsectSpecHolder, AspectContext aspectContext) {
            List<? extends AspectRepository<R>> aspectRepositories = Collections.emptyList();

            try {
                aspectRepositories = doResolve(apsectSpecHolder, aspectContext);
            } catch(Throwable t) {
                LOGGER.warn("Failed to resolve AspectSepc '{}' via '{}'.", apsectSpecHolder, resolverName, t);
            }

            return aspectRepositories;
        }

        protected abstract List<? extends AspectRepository<R>> doResolve(
                AspectSpecHolder<T> apsectSpecHolder, AspectContext aspectContext);
    }


    class ForPojoPointcut extends AbstractBase<AspectSpec.PojoPointcutSpec, AspectSpec.PojoPointcutSpec> {

        @Override
        public boolean support(AspectSpecHolder<AspectSpec.PojoPointcutSpec> apsectSpecHolder) {
            return apsectSpecHolder != null && apsectSpecHolder.getAspectSpec() instanceof AspectSpec.PojoPointcutSpec;
        }

        @Override
        protected List<? extends AspectRepository<AspectSpec.PojoPointcutSpec>> doResolve(
                AspectSpecHolder<AspectSpec.PojoPointcutSpec> apsectSpecHolder, AspectContext aspectContext) {
            return Collections.singletonList(
                    new AspectRepository.ForPojoPointcut(apsectSpecHolder) );
        }
    }


    class ForExprPointcut extends AbstractBase<AspectSpec.ExprPointcutSpec, AspectSpec.ExprPointcutSpec> {

        @Override
        public boolean support(AspectSpecHolder<AspectSpec.ExprPointcutSpec> apsectSpecHolder) {
            return apsectSpecHolder != null && apsectSpecHolder.getAspectSpec() instanceof AspectSpec.ExprPointcutSpec;
        }

        @Override
        protected List<? extends AspectRepository<AspectSpec.ExprPointcutSpec>> doResolve(
                AspectSpecHolder<AspectSpec.ExprPointcutSpec> apsectSpecHolder, AspectContext aspectContext) {
            return Collections.singletonList(
                    new AspectRepository.ForExprPointcut(apsectSpecHolder) );
        }
    }


    class ForAspectJ extends AbstractBase<AspectSpec.AspectJSpec, AspectSpec.ExprPointcutSpec> {

        @Override
        public boolean support(AspectSpecHolder<AspectSpec.AspectJSpec> apsectSpecHolder) {
            return apsectSpecHolder != null && apsectSpecHolder.getAspectSpec() instanceof AspectSpec.AspectJSpec;
        }

        @Override
        protected List<? extends AspectRepository<AspectSpec.ExprPointcutSpec>> doResolve(
                AspectSpecHolder<AspectSpec.AspectJSpec> apsectSpecHolder, AspectContext aspectContext) {
            AspectSpec.AspectJSpec aspectJSpec = apsectSpecHolder.getAspectSpec();

            TypeDescription aspectJTypeDescription = aspectContext.getAspectTypePool().describe(aspectJSpec.getAspectJClassName()).resolve();
            if(aspectJTypeDescription == null) 
                return Collections.emptyList();

            List<AspectRepository<AspectSpec.ExprPointcutSpec>> aspectRepositories = new ArrayList<>();
            AtomicInteger adviceMethodIndex = new AtomicInteger(1);
            for(MethodDescription.InDefinedShape methodDescription : aspectJTypeDescription.getDeclaredMethods()) {
                AnnotationList annotations = methodDescription.getDeclaredAnnotations();

                for(Class<? extends Annotation> annotationType : AspectJAdvice.ADVICE_ANNOTATIONS) {
                    AnnotationDescription annotationDescription = annotations.ofType(annotationType);
                    if(annotationDescription == null)
                        continue;

                    if(methodDescription.isAbstract()) {
                        LOGGER.warn("Ignored abstract AspectJ advice method. \n  advice method: {} \n",
                                methodDescription.toGenericString());
                        continue;
                    }
                    if(methodDescription.isPrivate()) {
                        LOGGER.warn("Ignored private AspectJ advice method. \n  advice method: {} \n",
                                methodDescription.toGenericString());
                        continue;
                    }

                    // 1.parse pointcut expression
                    AnnotationValue<?, ?> annotationValue = annotationDescription.getValue("value");
                    String pointcutExpression = annotationValue.resolve(String.class).trim();
                    if(StringUtils.hasText(pointcutExpression) == false) {
                        annotationValue = annotationDescription.getValue("pointcut");
                        pointcutExpression = annotationValue.resolve(String.class).trim();
                    }
                    if(StringUtils.hasText(pointcutExpression) == false) {
                        LOGGER.warn("Ignored empty pointcut expression AspectJ advice method. \n  advice method: {} \n", 
                                methodDescription.toGenericString());
                        continue;
                    }

                    // 2.create MethodSpec
                    MethodSpec methodSpec;
                    try {
                        methodSpec= new MethodSpec(pointcutExpression, methodDescription, 
                                annotationType, annotationDescription);

                        if(methodSpec.isValid() == false)
                            continue;
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to parse AspectJ advice method. \n  advice method: {} \n", 
                                MethodUtils.getMethodSignature(methodDescription), t);

                        continue;
                    }

                    String adviceClassName = createAdviceClassName(aspectJSpec, adviceMethodIndex.getAndIncrement(), methodDescription);
                    AspectSpec.ExprPointcutSpec asepctSpec = new AspectSpec.ExprPointcutSpec.Default(
                            aspectJSpec.isPerInstance(), adviceClassName, pointcutExpression, aspectJSpec.getOrder());
                    AspectJAdvice.ClassMaker classMaker = new AspectJAdvice.ClassMaker(adviceClassName, aspectJTypeDescription, methodSpec);

                    // 3.create AspectRepository
                    aspectRepositories.add( 
                            new AspectRepository.ForAspectJAdvice(
                                    new AspectSpecHolder<AspectSpec.ExprPointcutSpec>(adviceClassName, asepctSpec), 
                                    classMaker
                            ) 
                    );
                }
            }

            if(adviceMethodIndex.get() == 1) {
                LOGGER.warn("Ignored AspectSpec since advice method must exist. \n  {}: {} \n", 
                        AspectSpec.AspectJSpec.class.getSimpleName(), aspectJSpec.getAspectJClassName() );
            }

            return aspectRepositories;
        }

        private String createAdviceClassName(AspectSpec.AspectJSpec aspectJSpec, int index, InDefinedShape methodDescription) {
            return aspectJSpec.getAspectJClassName() + "_" + methodDescription.getName() + "_Advice" + index ;
        }
    }
}
