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
import io.gemini.aop.factory.support.AdviceMethodSpec.AspectJMethodSpec;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.condition.ConditionContext;
import io.gemini.api.aop.condition.Conditional;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorRepositoryResolver<T extends AdvisorSpec, R extends AdvisorSpec> {

    boolean support(AdvisorSpec advisorSpec);

    List<? extends AdvisorRepository<R>> resolve(T advisorSpec, FactoryContext factoryContext);


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
                T advisorSpec, FactoryContext factoryContext);
    }


    class ForPojoPointcut extends AbstractBase<AdvisorSpec.PojoPointcutSpec, AdvisorSpec.PojoPointcutSpec> {

        @Override
        public boolean support(AdvisorSpec advisorSpec) {
            return advisorSpec != null && advisorSpec instanceof AdvisorSpec.PojoPointcutSpec;
        }

        @Override
        protected List<? extends AdvisorRepository<AdvisorSpec.PojoPointcutSpec>> doResolve(
                AdvisorSpec.PojoPointcutSpec advisorSpec, FactoryContext factoryContext) {
            return Collections.singletonList(
                    new AdvisorRepository.ForPojoPointcut(advisorSpec) );
        }
    }


    class ForExprPointcut extends AbstractBase<AdvisorSpec.ExprPointcutSpec, AdvisorSpec.ExprPointcutSpec> {

        @Override
        public boolean support(AdvisorSpec advisorSpec) {
            return advisorSpec != null && advisorSpec instanceof AdvisorSpec.ExprPointcutSpec;
        }

        @Override
        protected List<? extends AdvisorRepository<AdvisorSpec.ExprPointcutSpec>> doResolve(
                AdvisorSpec.ExprPointcutSpec advisorSpec, FactoryContext factoryContext) {
            return Collections.singletonList(
                    new AdvisorRepository.ForExprPointcut(advisorSpec) );
        }
    }


    class ForAspectJ extends AbstractBase<AdvisorSpec.AspectJSpec, AdvisorSpec.ExprPointcutSpec> {

        @Override
        public boolean support(AdvisorSpec advisorSpec) {
            return advisorSpec != null && advisorSpec instanceof AdvisorSpec.AspectJSpec;
        }

        @Override
        protected List<? extends AdvisorRepository<AdvisorSpec.ExprPointcutSpec>> doResolve(
                AdvisorSpec.AspectJSpec advisorSpec, FactoryContext factoryContext) {
            TypeDescription adviceTypeDescription = factoryContext.getTypePool().describe(advisorSpec.getAspectJClassName()).resolve();
            if(adviceTypeDescription == null) 
                return Collections.emptyList();

            ElementMatcher<ConditionContext> aspectCondition = createAdviceCondition(factoryContext, adviceTypeDescription.getDeclaredAnnotations());
            if(aspectCondition == null)
                aspectCondition = AdvisorSpec.TRUE;

            List<AdvisorRepository<AdvisorSpec.ExprPointcutSpec>> advisorRepositories = new ArrayList<>();
            AtomicInteger adviceMethodIndex = new AtomicInteger(1);
            for(MethodDescription.InDefinedShape methodDescription : adviceTypeDescription.getDeclaredMethods()) {
                AnnotationList annotations = methodDescription.getDeclaredAnnotations();

                for(Class<? extends Annotation> annotationType : AdviceClassMaker.ADVICE_ANNOTATIONS) {
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
                    String pointcutExpression = annotationValue == null ? null : annotationValue.resolve(String.class).trim();
                    if(StringUtils.hasText(pointcutExpression) == false) {
                        annotationValue = annotationDescription.getValue("pointcut");
                        pointcutExpression = annotationValue == null ? null : annotationValue.resolve(String.class).trim();
                    }
                    if(StringUtils.hasText(pointcutExpression) == false) {
                        LOGGER.warn("Ignored AspectJ advice method with empty pointcut expression. \n  AdvisorSpec: {} \n  AdviceMethod: {} \n", 
                                advisorName, methodDescription.toGenericString());
                        continue;
                    }

                    // 2.create AspectJMethodSpec
                    AspectJMethodSpec aspectJMethodSpec;
                    try {
                        aspectJMethodSpec= new AspectJMethodSpec(advisorName, 
                                adviceTypeDescription, methodDescription, 
                                annotationType, annotationDescription);

                        if(aspectJMethodSpec.isValid() == false)
                            continue;
                    } catch(Throwable t) {
                        LOGGER.warn("Failed to parse AspectJ advice method. \n  AdvisorSpec: {} \n  AdviceMethod: {} \n", 
                                advisorName, MethodUtils.getMethodSignature(methodDescription), t);

                        continue;
                    }

                    String adviceClassName = createAdviceClassName(advisorSpec, adviceMethodIndex.getAndIncrement(), methodDescription);
                    ElementMatcher<ConditionContext> condition = createAdviceCondition(factoryContext, annotations);
                    if(condition == null)
                        condition = factoryContext.getDefaultCondition();

                    AdvisorSpec.ExprPointcutSpec exprAdvisorSpec = new AdvisorSpec.ExprPointcutSpec.Default(
                            adviceClassName, 
                            new ElementMatcher.Junction.Conjunction<ConditionContext>(aspectCondition, condition),
                            advisorSpec.isPerInstance(), 
                            adviceClassName, 
                            pointcutExpression, 
                            advisorSpec.getOrder());

                    // 3.create AdvisorRepository
                    advisorRepositories.add( 
                            new AdvisorRepository.ForAspectJAdvice(exprAdvisorSpec, aspectJMethodSpec) 
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

        private ElementMatcher<ConditionContext> createAdviceCondition(FactoryContext factoryContext, AnnotationList annotations) {
            AnnotationDescription annotationDescription = annotations.ofType(Conditional.class);
            if(annotationDescription == null)
                return null;

            AnnotationValue<?, ?> conditionAnnotation = annotationDescription.getValue("value");
            TypeDescription[] conditionTypeDescritions = (TypeDescription[]) conditionAnnotation.resolve();
            List<String> conditionClassNames = new ArrayList<>(conditionTypeDescritions.length);
            for(TypeDescription conditionTypeDescrition : conditionTypeDescritions)
                conditionClassNames.add(conditionTypeDescrition.getTypeName());

            AnnotationValue<?, ?> annotationValue = annotationDescription.getValue("classLoaderExpr");
            String classLoaderExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

            annotationValue = annotationDescription.getValue("typeExpr");
            String typeExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

            annotationValue = annotationDescription.getValue("fieldExpr");
            String fieldExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

            annotationValue = annotationDescription.getValue("constructorExpr");
            String constructorExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

            annotationValue = annotationDescription.getValue("methodExpr");
            String methodExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

            return new ForAspectJAdvice(factoryContext, conditionClassNames, classLoaderExpr, 
                    typeExpr, fieldExpr, constructorExpr, methodExpr);
        }


        private static class ForAspectJAdvice implements ElementMatcher<ConditionContext> {

            private final FactoryContext factoryContext;
            private final List<String> conditionClassNames;

            private final String classLoaderExpr;

            private final String typeExpr;
            private final String fieldExpr;
            private final String constructorExpr;
            private final String methodExpr;


            public ForAspectJAdvice(FactoryContext factoryContext, List<String> conditionClassNames, 
                    String classLoaderExpr,
                    String typeExpr, String fieldExpr, String constructorExpr, String methodExpr) {
                this.factoryContext = factoryContext;
                this.conditionClassNames = conditionClassNames;

                this.classLoaderExpr = classLoaderExpr;

                this.typeExpr = typeExpr;
                this.fieldExpr = fieldExpr;
                this.constructorExpr = constructorExpr;
                this.methodExpr = methodExpr;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean matches(ConditionContext conditionContext) {
                if(CollectionUtils.isEmpty(conditionClassNames) == false
                        && doMatchConditions(conditionContext) == false)
                    return false;

                if(StringUtils.hasText(classLoaderExpr) 
                        && conditionContext.isClassLoader(classLoaderExpr) == false)
                    return false;

                if(StringUtils.hasText(typeExpr) 
                        && conditionContext.hasType(typeExpr) == false)
                    return false;

                if(StringUtils.hasText(fieldExpr) 
                        && conditionContext.hasFiled(fieldExpr) == false)
                    return false;

                if(StringUtils.hasText(constructorExpr) 
                        && conditionContext.hasConstructorExpr(constructorExpr) == false)
                    return false;

                if(StringUtils.hasText(methodExpr) 
                        && conditionContext.hasMethodExpr(methodExpr) == false)
                    return false;

                return true;
            }

            /**
             * @param conditionContext 
             * @return
             */
            @SuppressWarnings("unchecked")
            protected boolean doMatchConditions(ConditionContext conditionContext) {
                ObjectFactory objectFactory = factoryContext.getObjectFactory();
                for(String conditionClassName : conditionClassNames) {
                    Class<ElementMatcher<ConditionContext>> conditonClass = 
                            (Class<ElementMatcher<ConditionContext>>) objectFactory.loadClass(conditionClassName);
                    ElementMatcher<ConditionContext> condition = objectFactory.createObject(conditonClass);

                    if(condition.matches(conditionContext) == false)
                        return false;
                }
                return true;
            }
        }
    }
}
