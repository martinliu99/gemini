/*
 * Copyright © 2023 - present, the original author or authors. All Rights Reserved.
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.factory.classloader.AspectClassLoader;
import io.gemini.api.aop.AopException;
import io.gemini.api.aop.MatchingContext;
import io.gemini.api.aop.annotation.Conditional;
import io.gemini.core.OrderComparator;
import io.gemini.core.config.ConfigView;
import io.gemini.core.object.ClassScanner;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.util.Assert;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.ReflectionUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Parses {@link io.gemini.api.aop.annotation.Conditional} annotations from advisor class/method
 * annotations or from configuration properties, and assembles them into a composite
 * {@link net.bytebuddy.matcher.ElementMatcher}&lt;{@link io.gemini.api.aop.MatchingContext}&gt;.
 * <p>
 * Also provides {@link #loadConditionalAndConditionClasses} to discover all
 * {@code @Conditional}-annotated annotation types in the aspect application classpath.
 * </p>
 *
 * @author   martin.liu
 */
public abstract class AdvisorConditionParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorConditionParser.class);

    private static final String CONDITIONAL_CLASSNAME_KEY_SUFFIX = "conditionalClassName";


    /**
     * Scans the classpath for all annotation types annotated with {@link Conditional},
     * loads them via the given {@code classLoader}, and returns a map from each
     * conditional annotation class to its associated {@link io.gemini.api.aop.Condition} class.
     * <p>
     * The returned map is ordered by {@link io.gemini.core.OrderComparator} priority.
     * </p>
     *
     * @param classScanner the scanner used to discover {@code @Conditional}-annotated annotation types
     * @param classLoader  the class loader used to load discovered annotation classes
     * @return an ordered map of conditional annotation class → condition class pairs;
     *         never {@code null}, may be empty
     */
    @SuppressWarnings("unchecked")
    public static Map<Class<? extends Annotation>, Class<?>> loadConditionalAndConditionClasses(
            ClassScanner classScanner, ClassLoader classLoader) {
        Assert.notNull(classScanner, "'classScanner' must not be null.");
        Assert.notNull(classLoader, "'classLoader' must not be null.");

        // find all @Conditional annotated annotation classes
        List<String> conditionalAnnotationClassNames = classScanner
                .getClassesWithAnnotation(Conditional.class.getName())
                .filter( classInfo -> classInfo.isAnnotation() )
                .getNames();

        // create and sort @Conditional annotation class
        List<Class<? extends Annotation>> conditionalClasses = new ArrayList<>(conditionalAnnotationClassNames.size());
        for (String annotationClassName : conditionalAnnotationClassNames) {
            try {
                Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) classLoader.loadClass(annotationClassName);
                conditionalClasses.add(annotationClass);
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not load conditional annotation class '{}', error reason: {}", 
                            conditionalClasses, t.getMessage() );

                Throwables.throwIfRequired(t);
            }
        }
        OrderComparator.sort(conditionalClasses);


        // create & @Conditional and Condition class map
        Map<Class<? extends Annotation>, Class<?>> conditionalAndConditions = new LinkedHashMap<>(conditionalClasses.size());
        for (Class<? extends Annotation> conditionalClass : conditionalClasses) {
            try {
                Class<?> conditionClass = conditionalClass.getAnnotation(Conditional.class).value();

                conditionalAndConditions.put(conditionalClass, conditionClass);
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not load condition class decalred by '{}', error reason: {}", 
                            conditionalClasses, t.getMessage() );

                Throwables.throwIfRequired(t);
            }
        }

        return conditionalAndConditions;
    }


    /**
     * Parses {@link Conditional}-related annotations from the given {@code annotationList}
     * and assembles them into a composite {@link ElementMatcher} over {@link MatchingContext}.
     * <p>
     * For each conditional annotation found on the element, the corresponding
     * {@link io.gemini.api.aop.Condition} instance is created via the {@link ObjectFactory}
     * and combined using a logical AND conjunction.
     * </p>
     *
     * @param factoryContext the factory context providing class loader, object factory,
     *                       and the pre-loaded conditional/condition class map
     * @param annotationList the annotations present on the advisor class or method
     * @return a conjunctive {@link ElementMatcher} representing all applicable conditions,
     *         or {@code null} if no conditional annotations are present
     * @throws AopException if a condition class cannot be instantiated or configured
     */
    @SuppressWarnings("unchecked")
    public static ElementMatcher<MatchingContext> parseAdvisorCondition(FactoryContext factoryContext, 
            AnnotationList annotationList) throws AopException {
        // find annotated @Conditional from current element
        Map<Class<? extends Annotation>, Class<?>> conditionalAndConditionClasses = getConditionalAndConditionClasses(factoryContext, annotationList);

        // instantiate condition class and initialize condition instance with annotation attributes
        ObjectFactory objectFactory = factoryContext.getObjectFactory();
        AspectClassLoader classLoader = factoryContext.getClassLoader();

        List<ElementMatcher<? super MatchingContext>> conditions = new ArrayList<>(conditionalAndConditionClasses.size() );
        for (Entry<Class<? extends Annotation>, Class<?>> entry : conditionalAndConditionClasses.entrySet()) {
            Class<? extends Annotation> conditionalClass = entry.getKey();
            Class<?> conditionClass = entry.getValue();

            try {
                // is annotated with given annotation?
                if (annotationList.isAnnotationPresent(conditionalClass) == false)
                    continue;

                // fetch annotation instance, and all attributes
                Annotation annotation = annotationList.ofType(conditionalClass).prepare(
                        (Class<? extends Annotation>) classLoader.loadClass(conditionalClass.getTypeName()) ).load();
                Map<String, Object> attributeValues = conditionalClass.getTypeName().equals(Conditional.class.getName())
                        ? Collections.emptyMap() : ReflectionUtils.getAttributeValues(annotation);

                // create condition class instance with annotation attributes
                conditions.add( 
                        (ElementMatcher<? super MatchingContext>) objectFactory.createObject(conditionClass, attributeValues));
            } catch (Throwable t) {
                Throwables.throwIfRequired(t);

                throw new AopException("Cannot parse Condition class [" + conditionalClass.getName() + "]", t);
            }
        }

        return CollectionUtils.isEmpty(conditions) ? null : new ElementMatcher.Junction.Conjunction<>(conditions);
    }

    private static Map<Class<? extends Annotation>, Class<?>> getConditionalAndConditionClasses(
            FactoryContext factoryContext, AnnotationList annotationList) {
        // find annotated @Conditional from current element
        AnnotationDescription conditionalType = annotationList.ofType(TypeDescription.ForLoadedType.of(Conditional.class) );
        if (conditionalType == null) 
            return factoryContext.getConditionalAndConditionClasses();


        Class<?> conditionClass = conditionalType.getValue("value")
                .load(factoryContext.getClassLoader())
                .resolve(Class.class);

        Map<Class<? extends Annotation>, Class<?>> conditionalAndConditions = new LinkedHashMap<>(factoryContext.getConditionalAndConditionClasses());
        conditionalAndConditions.put(Conditional.class, conditionClass);

        return conditionalAndConditions;
    }

    /**
     * Parses condition configuration from the given config key prefix and assembles
     * the matching conditions into a composite {@link ElementMatcher} over {@link MatchingContext}.
     * <p>
     * Looks up all condition entries under {@code configKeyPrefix + "conditions."}, resolves
     * the conditional annotation class name from each entry, and instantiates the corresponding
     * {@link io.gemini.api.aop.Condition} with attribute values read from the config.
     * Supports both the generic {@link Conditional} and any registered {@code ConditionalOnXxx}
     * annotation types. All resolved conditions are combined using a logical AND conjunction.
     * </p>
     *
     * @param factoryContext the factory context providing class loader, object factory,
     *                       config view, and the pre-loaded conditional/condition class map
     * @param configKeyPrefix the configuration key prefix under which condition entries are defined
     * @return a conjunctive {@link ElementMatcher} representing all configured conditions,
     *         or {@code null} if no condition entries are found under the given prefix
     * @throws AopException if a condition class cannot be loaded, instantiated, or configured
     */
    @SuppressWarnings("unchecked")
    public static ElementMatcher<MatchingContext> parseAdvisorCondition(FactoryContext factoryContext, 
            String configKeyPrefix) throws AopException  {
        ObjectFactory objectFactory = factoryContext.getObjectFactory();
        ConfigView configView = factoryContext.getConfigView();

        Map<Class<? extends Annotation>, Class<?>> conditionalAndConditions = factoryContext.getConditionalAndConditionClasses();
        AspectClassLoader classLoader = factoryContext.getClassLoader();

        Set<String> configuredConditionPrefixes = findConfiguredConditionPrefix(configView, configKeyPrefix);
        List<ElementMatcher<? super MatchingContext>> conditions = new ArrayList<>(configuredConditionPrefixes.size());
        for (String configuredConditionPrefix : configuredConditionPrefixes) {
            String conditionalClassNameKey = configuredConditionPrefix + CONDITIONAL_CLASSNAME_KEY_SUFFIX;
            String conditionalClassName = configView.getAsString(conditionalClassNameKey, null);

            try {
                Class<? extends Annotation> conditionalClass = (Class<? extends Annotation>) classLoader.loadClass(conditionalClassName);

                Class<?> conditionClass;
                Map<String, Object> attributeValues;
                if (Conditional.class == conditionalClass) {
                    // generic @Conditional
                    String conditionClassName = configView.getAsString(configuredConditionPrefix + "value", null);
                    conditionClass = classLoader.loadClass(conditionClassName);

                    attributeValues = Collections.emptyMap();
                } else {
                    // lookup defined ConditionalOnXxx annotation class
                    conditionClass = conditionalAndConditions.get(conditionalClass);

                    // lookup annotation attributes
                    attributeValues = new HashMap<>();
                    for (Method attributeMethod : ReflectionUtils.getAttributeMethods(conditionalClass)) {
                        String attributeName = attributeMethod.getName();

                        String configKey = configuredConditionPrefix + attributeName;
                        Object attributeValue;
                        if (configView.containsKey(configKey))
                            attributeValue = configView.getValue(configKey, attributeMethod.getReturnType());
                        else
                            attributeValue = attributeMethod.getDefaultValue();

                        attributeValues.put(attributeName, attributeValue) ;
                    }
                }

                // instantiate condition class with attributes of ConditionalOnxxx annotation
                Object condition = objectFactory.createObject(conditionClass, attributeValues);
                conditions.add( 
                        (ElementMatcher<? super MatchingContext>) condition);
            } catch (Throwable t) {
                Throwables.throwIfRequired(t);

                throw new AopException("Cannot parse Condition class [" + conditionalClassName + "] with configuration setting [" + configuredConditionPrefix + "]", t);
            }
        }

        return CollectionUtils.isEmpty(conditions) ? null : new ElementMatcher.Junction.Conjunction<>(conditions);
    }

    private static Set<String> findConfiguredConditionPrefix(ConfigView configView, String configKeyPrefix) {
        Set<String> configuredConditionPrefixes = new LinkedHashSet<>();
        for (String key : configView.keys(configKeyPrefix + "conditions.")) {
            // end with CONDITIONAL_CLASSNAME_KEY_SUFFIX
            int endPos = key.lastIndexOf(CONDITIONAL_CLASSNAME_KEY_SUFFIX);
            if (endPos > -1) {
                configuredConditionPrefixes.add( key.substring(0, endPos).trim() );
            }
        }

        return configuredConditionPrefixes;
    }
}
