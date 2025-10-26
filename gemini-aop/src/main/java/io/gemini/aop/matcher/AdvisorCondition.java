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

    private final Collection<String> acceptableClassLoaderExpressions;

    private final Collection<String> requiredTypeExpressions;
    private final Collection<String> requiredFieldExpressions;
    private final Collection<String> requiredConstructorExpressions;
    private final Collection<String> requiredMethodExpressions;


    public static AdvisorCondition create(FactoryContext factoryContext, Collection<String> acceptableClassLoaderExpressions) {
        return new AdvisorCondition(
                factoryContext, 
                null,
                acceptableClassLoaderExpressions, 
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

        List<String> acceptableClassLoaderExpressions = fetchAttribute(annotationDescription.getValue("acceptableClassLoaderExpressions"));
        List<String> requiredTypeExpressions = fetchAttribute(annotationDescription.getValue("requiredTypeExpressions"));
        List<String> requiredFieldExpressions = fetchAttribute(annotationDescription.getValue("requiredFieldExpressions"));
        List<String> requiredConstructorExpressions = fetchAttribute(annotationDescription.getValue("requiredConstructorExpressions"));
        List<String> requiredMethodExpressions = fetchAttribute(annotationDescription.getValue("requiredMethodExpressions"));

        if(conditionClassNames.size() == 0 && acceptableClassLoaderExpressions.size() == 0 
                && requiredTypeExpressions.size() == 0 && requiredFieldExpressions.size() == 0
                && requiredConstructorExpressions.size() == 0 && requiredMethodExpressions.size() == 0)
            return null;

        return new AdvisorCondition(
                factoryContext, 
                conditionClassNames, acceptableClassLoaderExpressions, 
                requiredTypeExpressions, requiredFieldExpressions,
                requiredConstructorExpressions, requiredMethodExpressions
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
        List<String> conditionClassNames = configView.getAsStringList(keyPrefix + "condition.conditionClassNames", Collections.emptyList());
        List<String> acceptableClassLoaderExpressions = configView.getAsStringList(keyPrefix + "condition.acceptableClassLoaderExpressions", Collections.emptyList());
        List<String> requiredTypeExpressions = configView.getAsStringList(keyPrefix + "condition.requiredTypeExpressions", Collections.emptyList());
        List<String> requiredFieldExpressions = configView.getAsStringList(keyPrefix + "condition.requiredFieldExpressions", Collections.emptyList());
        List<String> requiredConstructorExpressions = configView.getAsStringList(keyPrefix + "condition.requiredConstructorExpressions", Collections.emptyList());
        List<String> requiredMethodExpressions = configView.getAsStringList(keyPrefix + "condition.requiredMethodExpressions", Collections.emptyList());

        if(conditionClassNames.size() == 0 && acceptableClassLoaderExpressions.size() == 0 
                && requiredTypeExpressions.size() == 0 && requiredFieldExpressions.size() == 0
                && requiredConstructorExpressions.size() == 0 && requiredMethodExpressions.size() == 0)
            return null;

        return new AdvisorCondition(
                factoryContext, 
                conditionClassNames, acceptableClassLoaderExpressions, 
                requiredTypeExpressions, requiredFieldExpressions,
                requiredConstructorExpressions, requiredMethodExpressions
        );
    }


    private AdvisorCondition(FactoryContext factoryContext, List<String> conditionClassNames, 
            Collection<String> acceptableClassLoaderExpressions, Collection<String> requiredTypeExpressions, 
            Collection<String> requiredFieldExpressions, Collection<String> requiredConstructorExpressions, Collection<String> requiredMethodExpressions) {
        this.factoryContext = factoryContext;

        this.conditionClassNames = conditionClassNames == null ? Collections.emptyList() : conditionClassNames;

        this.acceptableClassLoaderExpressions = acceptableClassLoaderExpressions == null ? Collections.emptyList() : acceptableClassLoaderExpressions;
        this.requiredTypeExpressions = requiredTypeExpressions == null ? Collections.emptyList() : requiredTypeExpressions;

        this.requiredFieldExpressions = requiredFieldExpressions == null ? Collections.emptyList() : requiredFieldExpressions;
        this.requiredConstructorExpressions = requiredConstructorExpressions == null ? Collections.emptyList() : requiredConstructorExpressions;
        this.requiredMethodExpressions = requiredMethodExpressions == null ? Collections.emptyList() : requiredMethodExpressions;
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

        if(acceptableClassLoaderExpressions.size() > 0) {
            boolean matched = false;
            for(String acceptableClassLoaderExpression : acceptableClassLoaderExpressions) {
                if(conditionContext.isAccesptableClassLoader(acceptableClassLoaderExpression) == true) {
                    matched = true;
                    break;
                }
            }
            if(matched == false) return false;
        }

        for(String requiredTypeExpression : requiredTypeExpressions) {
            if(conditionContext.hasRequiredType(requiredTypeExpression) == false) return false;
        }

        for(String requiredFieldExpression : requiredFieldExpressions) {
            if(conditionContext.hasRequiredFiled(requiredFieldExpression) == false) return false;
        }

        for(String requiredConstructorExpression : requiredConstructorExpressions) {
            if(conditionContext.hasRequiredConstructor(requiredConstructorExpression) == false) return false;
        }

        for(String requiredMethodExpression : requiredMethodExpressions) {
            if(conditionContext.hasRequiredMethod(requiredMethodExpression) == false) return false;
        }

        return true;
    }
}
