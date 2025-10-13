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
package io.gemini.aop.matcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.gemini.aop.factory.FactoryContext;
import io.gemini.api.aop.condition.ConditionContext;
import io.gemini.core.config.ConfigView;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;


public class AdvisorCondition implements ElementMatcher<ConditionContext> {

    private final FactoryContext factoryContext;
    private final Collection<String> conditionClassNames;

    private final Collection<String> classLoaderExpressions;

    private final Collection<String> typeExpressions;
    private final Collection<String> fieldExpressions;
    private final Collection<String> constructorExpressions;
    private final Collection<String> methodExpressions;


    public static AdvisorCondition create(FactoryContext factoryContext, Collection<String> classLoaderExpressions) {
        return new AdvisorCondition(
                factoryContext, 
                null,
                classLoaderExpressions, 
                null, 
                null, 
                null, 
                null
        );
    }

    public static AdvisorCondition create(FactoryContext factoryContext, AnnotationDescription annotationDescription) {
        if(annotationDescription == null) return null;

        // resolve condition attributes
        AnnotationValue<?, ?> conditionAnnotation = annotationDescription.getValue("value");
        TypeDescription[] conditionTypeDescritions = (TypeDescription[]) conditionAnnotation.resolve();
        List<String> conditionClassNames = new ArrayList<>(conditionTypeDescritions.length);
        for(TypeDescription conditionTypeDescrition : conditionTypeDescritions)
            conditionClassNames.add(conditionTypeDescrition.getTypeName());

        return new AdvisorCondition(
                factoryContext, conditionClassNames,
                fetchAttribute(annotationDescription.getValue("classLoaderExpressions")), 
                fetchAttribute(annotationDescription.getValue("typeExpressions")),
                fetchAttribute(annotationDescription.getValue("fieldExpressions")),
                fetchAttribute(annotationDescription.getValue("constructorExpressions")),
                fetchAttribute(annotationDescription.getValue("methodExpressions"))
        );
    }


    private static List<String> fetchAttribute(AnnotationValue<?, ?> annotationValue) {
        if(annotationValue == null) return Collections.emptyList();

        String[] attributeValue = (String[])annotationValue.resolve();
        List<String> expressions = new ArrayList<>(attributeValue.length);
        for(String expression : attributeValue) {
            expression = expression.trim();
            if(StringUtils.hasText(expression)) expressions.add(expression);
        }

        return expressions;
    }

    public static AdvisorCondition create(FactoryContext factoryContext, ConfigView configView, String keyPrefix) {
        return new AdvisorCondition(
                factoryContext, 
                configView.getAsStringList(keyPrefix + "condition.conditionClassNames", Collections.emptyList()),
                configView.getAsStringList(keyPrefix + "condition.classLoaderExpressions", Collections.emptyList()),
                configView.getAsStringList(keyPrefix + "condition.typeExpressions", Collections.emptyList()),
                configView.getAsStringList(keyPrefix + "condition.fieldExpressions", Collections.emptyList()),
                configView.getAsStringList(keyPrefix + "condition.constructorExpressions", Collections.emptyList()),
                configView.getAsStringList(keyPrefix + "condition.methodExpressions", Collections.emptyList())
        );
    }


    private AdvisorCondition(FactoryContext factoryContext, List<String> conditionClassNames, 
            Collection<String> classLoaderExpressions, Collection<String> typeExpressions, 
            Collection<String> fieldExpressions, Collection<String> constructorExpressions, Collection<String> methodExpressions) {
        this.factoryContext = factoryContext;

        this.conditionClassNames = conditionClassNames == null ? Collections.emptyList() : conditionClassNames;

        this.classLoaderExpressions = classLoaderExpressions == null ? Collections.emptyList() : classLoaderExpressions;
        this.typeExpressions = typeExpressions == null ? Collections.emptyList() : typeExpressions;

        this.fieldExpressions = fieldExpressions == null ? Collections.emptyList() : fieldExpressions;
        this.constructorExpressions = constructorExpressions == null ? Collections.emptyList() : constructorExpressions;
        this.methodExpressions = methodExpressions == null ? Collections.emptyList() : methodExpressions;
    }


    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean matches(ConditionContext conditionContext) {
        ObjectFactory objectFactory = factoryContext.getObjectFactory();
        for(String conditionClassName : conditionClassNames) {
            Class<ElementMatcher<ConditionContext>> conditonClass = 
                    (Class<ElementMatcher<ConditionContext>>) objectFactory.loadClass(conditionClassName);
            ElementMatcher<ConditionContext> condition = objectFactory.createObject(conditonClass);

            if(condition.matches(conditionContext) == false) return false;
        }

        if(classLoaderExpressions.size() > 0) {
            boolean matched = false;
            for(String classLoaderExpression : classLoaderExpressions) {
                if(conditionContext.isClassLoader(classLoaderExpression) == true) {
                    matched = true;
                    break;
                }
            }
            if(matched == false) return false;
        }

        for(String typeExpression : typeExpressions) {
            if(conditionContext.hasType(typeExpression) == false) return false;
        }

        for(String fieldExpression : fieldExpressions) {
            if(conditionContext.hasFiled(fieldExpression) == false) return false;
        }

        for(String constructorExpression : constructorExpressions) {
            if(conditionContext.hasConstructor(constructorExpression) == false) return false;
        }

        for(String methodExpression : methodExpressions) {
            if(conditionContext.hasMethod(methodExpression) == false) return false;
        }

        return true;
    }
}
