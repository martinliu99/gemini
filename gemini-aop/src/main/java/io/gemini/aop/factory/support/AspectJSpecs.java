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
import io.gemini.api.annotation.NoScanning;
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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 */
interface AspectJSpecs {


    class AspectAspectJSpec extends AdvisorSpec.AbstractBase {

        private final String aspectJClassName;


        public AspectAspectJSpec(boolean perInstance, String aspectJClassName, int order) {
            this(null, null, perInstance, aspectJClassName, order);
        }

        public AspectAspectJSpec(ElementMatcher<ConditionContext> condition, boolean perInstance, 
                String aspectJClassName, int order) {
            this(null, condition, perInstance, aspectJClassName, order);
        }

        public AspectAspectJSpec(String advisorName, ElementMatcher<ConditionContext> condition, boolean perInstance, 
                String aspectJClassName, int order) {
            super(advisorName == null ? aspectJClassName : advisorName, condition, 
                    perInstance, null, order);

            this.aspectJClassName = aspectJClassName;
        }

        public String getAspectJClassName() {
            return aspectJClassName;
        }
    }


    @NoScanning
    class AspectJAdvisorSpec extends AdvisorSpec.AbstractBase {

        private static final Logger LOGGER = LoggerFactory.getLogger(AspectJAdvisorSpec.class);

        private final TypeDescription aspectJType;
        private final MethodDescription aspectJMethod;
        private final AnnotationDescription aspectJAnnotation;

        private final String pointcutExpression;


        public AspectJAdvisorSpec(String advisorName, ElementMatcher<ConditionContext> condition, boolean perInstance, 
                String adviceClassName, TypeDescription aspectJType, 
                MethodDescription adviceMethod, AnnotationDescription aspectJAnnotation, int order) {
            super(advisorName == null ? aspectJType.getTypeName() : advisorName, condition, 
                    perInstance, adviceClassName, order);

            this.aspectJType = aspectJType;
            this.aspectJMethod = adviceMethod;
            this.aspectJAnnotation = aspectJAnnotation;

            this.pointcutExpression = parsePointcutExpression(advisorName, adviceMethod, aspectJAnnotation);
        }

        private static String parsePointcutExpression(String advisorName, MethodDescription adviceMethod, 
                AnnotationDescription aspectJAnnotation) {
            AnnotationValue<?, ?> annotationValue = aspectJAnnotation.getValue("value");
            String pointcutExpression = annotationValue == null ? null : annotationValue.resolve(String.class).trim();
            if(StringUtils.hasText(pointcutExpression) == false) {
                annotationValue = aspectJAnnotation.getValue("pointcut");
                pointcutExpression = annotationValue == null ? null : annotationValue.resolve(String.class).trim();
            }
            if(StringUtils.hasText(pointcutExpression) == false) {
                LOGGER.warn("Ignored AspectJ advice method with empty pointcut expression. \n  AdvisorSpec: {} \n  AdviceMethod: {} \n", 
                        advisorName, MethodUtils.getMethodSignature(adviceMethod));
            }
            return pointcutExpression;
        }

        public TypeDescription getAspectJType() {
            return aspectJType;
        }

        public String getAspectJClassName() {
            return aspectJType.getTypeName();
        }

        public MethodDescription getAspectJMethod() {
            return aspectJMethod;
        }

        public AnnotationDescription getAspectJAnnotation() {
            return aspectJAnnotation;
        }

        public String getPointcutExpression() {
            return pointcutExpression;
        }
    }


    class AspectJAdvisorCondition implements ElementMatcher<ConditionContext> {

        private final FactoryContext factoryContext;
        private final List<String> conditionClassNames;

        private final String classLoaderExpr;

        private final String typeExpr;
        private final String fieldExpr;
        private final String constructorExpr;
        private final String methodExpr;


        public AspectJAdvisorCondition(FactoryContext factoryContext, AnnotationDescription annotationDescription) {
            this.factoryContext = factoryContext;

            // resolve condition attributes
            AnnotationValue<?, ?> conditionAnnotation = annotationDescription.getValue("value");
            TypeDescription[] conditionTypeDescritions = (TypeDescription[]) conditionAnnotation.resolve();
            this.conditionClassNames = new ArrayList<>(conditionTypeDescritions.length);
            for(TypeDescription conditionTypeDescrition : conditionTypeDescritions)
                conditionClassNames.add(conditionTypeDescrition.getTypeName());

            AnnotationValue<?, ?> annotationValue = annotationDescription.getValue("classLoaderExpr");
            this.classLoaderExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

            annotationValue = annotationDescription.getValue("typeExpr");
            this.typeExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

            annotationValue = annotationDescription.getValue("fieldExpr");
            this.fieldExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

            annotationValue = annotationDescription.getValue("constructorExpr");
            this.constructorExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();

            annotationValue = annotationDescription.getValue("methodExpr");
            this.methodExpr = annotationValue == null ? null : annotationValue.resolve(String.class).trim();
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


    enum Parser {

        INSTANCE;

        private static final Logger LOGGER = LoggerFactory.getLogger(Parser.class);


        public AspectAspectJSpec parseSpec(FactoryContext factoryContext, String aspectJClassName) {
            TypeDescription adviceTypeDescription = factoryContext.getTypePool().describe(aspectJClassName).resolve();
            if(adviceTypeDescription == null) 
                return null;

            ElementMatcher<ConditionContext> aspectCondition = parseCondition(factoryContext, adviceTypeDescription.getDeclaredAnnotations());
            if(aspectCondition == null)
                aspectCondition = AdvisorSpec.TRUE;

            return new AspectAspectJSpec(aspectCondition, false, aspectJClassName, 0);
        }

        public List<AspectJAdvisorSpec> parseSpec(FactoryContext factoryContext,
                AspectAspectJSpec aspectJSpec, TypeDescription aspectJType,
                MethodDescription aspectJMethod, AtomicInteger aspectJMethodIndex) {
            String aspectJClassName = aspectJSpec.getAspectJClassName();

            if(aspectJMethod.isAbstract()) {
                LOGGER.warn("Ignored abstract AspectJ advice method. \n  {}: {} \n  AdviceMethod: {} \n",
                        getSpecType(), aspectJClassName, MethodUtils.getMethodSignature(aspectJMethod) );
                return Collections.emptyList();
            }
            if(aspectJMethod.isPrivate()) {
                LOGGER.warn("Ignored private AspectJ advice method. \n  {}: {} \n  AdviceMethod: {} \n",
                        getSpecType(), aspectJClassName, MethodUtils.getMethodSignature(aspectJMethod) );
                return Collections.emptyList();
            }


            List<AspectJAdvisorSpec> advisorSpecs = new ArrayList<>();
            AnnotationList annotations = aspectJMethod.getDeclaredAnnotations();
            for(Class<? extends Annotation> annotationType : AdviceClassMaker.ADVICE_ANNOTATIONS) {
                AnnotationDescription aspectJAnnotation = annotations.ofType(annotationType);
                if(aspectJAnnotation == null)
                    continue;

                String adviceClassName = aspectJClassName + "_" + aspectJMethod.getName() + "_Advice" + aspectJMethodIndex.getAndIncrement();
                ElementMatcher<ConditionContext> condition = parseCondition(factoryContext, annotations);
                if(condition == null)
                    condition = factoryContext.getDefaultCondition();

                advisorSpecs.add(
                        new AspectJAdvisorSpec(adviceClassName, 
                                new ElementMatcher.Junction.Conjunction<ConditionContext>(aspectJSpec.getCondition(), condition),
                                aspectJSpec.isPerInstance(), 
                                adviceClassName,
                                aspectJType,
                                aspectJMethod, 
                                aspectJAnnotation, 
                                aspectJSpec.getOrder() ) );

            }

            return advisorSpecs;
        }

        private String getSpecType() {
            return AspectJAdvisorSpec.class.getSimpleName();
        }


        public ElementMatcher<ConditionContext> parseCondition(FactoryContext factoryContext, AnnotationList annotations) {
            AnnotationDescription annotationDescription = annotations.ofType(Conditional.class);
            if(annotationDescription == null)
                return null;

            return new AspectJAdvisorCondition(factoryContext, annotationDescription);
        }
    }
}
