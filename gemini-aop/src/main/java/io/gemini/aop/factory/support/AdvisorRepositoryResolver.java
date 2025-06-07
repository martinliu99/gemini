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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.support.AspectJAdvice.MethodSpec;
import io.gemini.api.aop.AdvisorSpec;
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
public interface AdvisorRepositoryResolver<T extends AdvisorSpec, R extends AdvisorSpec> {

    boolean support(AdvisorSpec apsectSpec);

    List<? extends AdvisorRepository<R>> resolve(T apsectSpec, FactoryContext factoryContext);


    abstract class AbstractBase<T extends AdvisorSpec, R extends AdvisorSpec> 
            implements AdvisorRepositoryResolver<T, R> {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AdvisorRepositoryResolver.class);

        protected String resolverName = this.getClass().getName();


        @Override
        public List<? extends AdvisorRepository<R>> resolve(
                T advisorSpec, FactoryContext factoryContext) {
            List<? extends AdvisorRepository<R>> advisorRepositories = Collections.emptyList();

            try {
                advisorRepositories = doResolve(advisorSpec, factoryContext);
            } catch(Throwable t) {
                LOGGER.warn("Failed to resolve AdvisorSpec '{}' via '{}'.", advisorSpec, resolverName, t);
            }

            return advisorRepositories;
        }

        protected abstract List<? extends AdvisorRepository<R>> doResolve(
                T apsectSpec, FactoryContext factoryContext);
    }


    class ForPojoPointcut extends AbstractBase<AdvisorSpec.PojoPointcutSpec, AdvisorSpec.PojoPointcutSpec> {

        @Override
        public boolean support(AdvisorSpec apsectSpec) {
            return apsectSpec != null && apsectSpec instanceof AdvisorSpec.PojoPointcutSpec;
        }

        @Override
        protected List<? extends AdvisorRepository<AdvisorSpec.PojoPointcutSpec>> doResolve(
                AdvisorSpec.PojoPointcutSpec apsectSpec, FactoryContext factoryContext) {
            return Collections.singletonList(
                    new AdvisorRepository.ForPojoPointcut(apsectSpec) );
        }
    }


    class ForExprPointcut extends AbstractBase<AdvisorSpec.ExprPointcutSpec, AdvisorSpec.ExprPointcutSpec> {

        @Override
        public boolean support(AdvisorSpec apsectSpec) {
            return apsectSpec != null && apsectSpec instanceof AdvisorSpec.ExprPointcutSpec;
        }

        @Override
        protected List<? extends AdvisorRepository<AdvisorSpec.ExprPointcutSpec>> doResolve(
                AdvisorSpec.ExprPointcutSpec apsectSpec, FactoryContext factoryContext) {
            return Collections.singletonList(
                    new AdvisorRepository.ForExprPointcut(apsectSpec) );
        }
    }


    class ForAspectJ extends AbstractBase<AdvisorSpec.AspectJSpec, AdvisorSpec.ExprPointcutSpec> {

        @Override
        public boolean support(AdvisorSpec apsectSpec) {
            return apsectSpec != null && apsectSpec instanceof AdvisorSpec.AspectJSpec;
        }

        @Override
        protected List<? extends AdvisorRepository<AdvisorSpec.ExprPointcutSpec>> doResolve(
                AdvisorSpec.AspectJSpec advisorSpec, FactoryContext factoryContext) {
            TypeDescription aspectJTypeDescription = factoryContext.getTypePool().describe(advisorSpec.getAspectJClassName()).resolve();
            if(aspectJTypeDescription == null) 
                return Collections.emptyList();

            List<AdvisorRepository<AdvisorSpec.ExprPointcutSpec>> advisorRepositories = new ArrayList<>();
            AtomicInteger adviceMethodIndex = new AtomicInteger(1);
            for(MethodDescription.InDefinedShape methodDescription : aspectJTypeDescription.getDeclaredMethods()) {
                AnnotationList annotations = methodDescription.getDeclaredAnnotations();

                for(Class<? extends Annotation> annotationType : AspectJAdvice.ADVICE_ANNOTATIONS) {
                    AnnotationDescription annotationDescription = annotations.ofType(annotationType);
                    if(annotationDescription == null)
                        continue;

                    String advisorName = advisorSpec.getAdvisorName();
                    if(methodDescription.isAbstract()) {
                        LOGGER.warn("Ignored abstract AspectJ advice method. \n  AdvisorSpec: {} \n  AdviceMethod: {} \n",
                                advisorName, methodDescription.toGenericString());
                        continue;
                    }
                    if(methodDescription.isPrivate()) {
                        LOGGER.warn("Ignored private AspectJ advice method. \n  AdvisorSpec: {} \n  AdviceMethod: {} \n",
                                advisorName, methodDescription.toGenericString());
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
                        LOGGER.warn("Ignored AspectJ advice method with empty pointcut expression. \n  AdvisorSpec: {} \n  AdviceMethod: {} \n", 
                                advisorName, methodDescription.toGenericString());
                        continue;
                    }

                    // 2.create MethodSpec
                    MethodSpec methodSpec;
                    try {
                        methodSpec= new MethodSpec(advisorName,
                                pointcutExpression, methodDescription, 
                                annotationType, annotationDescription);

                        if(methodSpec.isValid() == false)
                            continue;
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to parse AspectJ advice method. \n  AdvisorSpec: {} \n  AdviceMethod: {} \n", 
                                advisorName, MethodUtils.getMethodSignature(methodDescription), t);

                        continue;
                    }

                    String adviceClassName = createAdviceClassName(advisorSpec, adviceMethodIndex.getAndIncrement(), methodDescription);
                    AdvisorSpec.ExprPointcutSpec exprAdvisorSpec = new AdvisorSpec.ExprPointcutSpec.Default(
                            adviceClassName, 
                            factoryContext.getDefaultCondition(),
                            advisorSpec.isPerInstance(), 
                            adviceClassName, 
                            pointcutExpression, 
                            advisorSpec.getOrder());

                    AspectJAdvice.ClassMaker classMaker = new AspectJAdvice.ClassMaker(adviceClassName, aspectJTypeDescription, methodSpec);

                    // 3.create AdvisorRepository
                    advisorRepositories.add( 
                            new AdvisorRepository.ForAspectJAdvice(exprAdvisorSpec, classMaker) 
                    );
                }
            }

            if(adviceMethodIndex.get() == 1) {
                LOGGER.warn("Ignored AdvisorSpec contains no advice methods. \n  {}: {} \n", 
                        AdvisorSpec.AspectJSpec.class.getSimpleName(), advisorSpec.getAspectJClassName() );
            }

            return advisorRepositories;
        }

        private String createAdviceClassName(AdvisorSpec.AspectJSpec aspectJSpec, int index, InDefinedShape methodDescription) {
            return aspectJSpec.getAspectJClassName() + "_" + methodDescription.getName() + "_Advice" + index ;
        }
    }
}
