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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.matcher.Pattern.Type;
import io.gemini.aspectj.weaver.Expression;
import io.gemini.aspectj.weaver.Expression.TypeExpr;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.aspectj.weaver.TypeWorldFactory;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.pool.TypePoolFactory;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class TypeMatcherFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypeMatcherFactory.class);

    private final TypePoolFactory typePoolFactory;
    private final TypeWorldFactory typeWorldFactory;

    private final ConcurrentMap<ClassLoader, ConcurrentMap<Collection<Pattern>, ElementMatcher<TypeDescription>>> 
            typePatternMatcherCache = new ConcurrentReferenceHashMap<>();


    public TypeMatcherFactory(TypePoolFactory typePoolFactory, TypeWorldFactory typeWorldFactory) {
        Assert.notNull(typePoolFactory, "'typePoolFactory' must not be null");
        this.typePoolFactory = typePoolFactory;

        Assert.notNull(typeWorldFactory, "'typeWorldFactory' must not be null");
        this.typeWorldFactory = typeWorldFactory;
    }

    public Collection<Pattern> validateTypePatterns(
            String ruleName, Collection<Pattern> typePatterns, boolean acceptMatchAllPattern, 
            ClassLoader classLoader, JavaModule javaModule, PlaceholderHelper placeholderHelper) {
        TypePool typePool = this.typePoolFactory.createTypePool(classLoader, javaModule);
        TypeWorld typeWorld = this.typeWorldFactory.createTypeWorld(classLoader, javaModule, typePool, placeholderHelper);

        List<Pattern> patterns = new ArrayList<>(typePatterns.size());
        for(Pattern pattern : typePatterns) {
            Type type = pattern.getType();

            if(Pattern.Type.MATCH_ALL_PATTERN == type) {
                if(createMatchAllMatcher(ruleName, typePatterns, acceptMatchAllPattern) != null)
                    return Collections.singleton(pattern);
            } else if(Pattern.Type.COMPLEX_PATTERN == type) {
                if(createTypeMatcher(ruleName, typePatterns, typeWorld, pattern.getExpression()) != null)
                    patterns.add(pattern);
            } else {
                patterns.add(pattern);
            }
        }

        return patterns;
    }

    private ElementMatcher.Junction<TypeDescription> createMatchAllMatcher(
            String ruleName, Collection<Pattern> typePatterns, boolean acceptMatchAllPattern) {
        if(acceptMatchAllPattern == true) {
            return TypeMatchers.TRUE;
        } else {
            LOGGER.warn("Ignored {} '{}' in rule '{}', patterns: {}.\n", 
                    Pattern.Type.MATCH_ALL_PATTERN, Pattern.STAR, ruleName, typePatterns);
            return null;
        }
    }

    private ElementMatcher.Junction<TypeDescription> createTypeMatcher(
            String ruleName, Collection<Pattern> typePatterns, TypeWorld typeWorld, 
            String expression) {
        try {
            // validate type pattern
            return new TypeExprMatcher(typeWorld, expression);
        } catch(Throwable t) {
            LOGGER.warn("Ignored illegal typePattern '{}' in rule '{}', patterns: {}.", 
                    expression, ruleName, typePatterns, t);
        }
        return null;
    }


    public ElementMatcher<TypeDescription> createTypeMatcher(
            String ruleName, Collection<Pattern> typePatterns, boolean acceptMatchAllPattern,
            ClassLoader classLoader, JavaModule javaModule, PlaceholderHelper placeholderHelper) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

        return this.typePatternMatcherCache
                .computeIfAbsent(
                        cacheKey, 
                        cl -> new ConcurrentHashMap<>() 
                )
                .computeIfAbsent(
                        typePatterns, 
                        key -> doCreateTypeMatcher(ruleName, typePatterns, acceptMatchAllPattern, classLoader, javaModule, placeholderHelper)
                );
    }

    protected ElementMatcher.Junction<TypeDescription> doCreateTypeMatcher(
            String ruleName, Collection<Pattern> typePatterns, boolean acceptMatchAllPattern,
            ClassLoader classLoader, JavaModule javaModule, PlaceholderHelper placeholderHelper) {
        if(CollectionUtils.isEmpty(typePatterns)) {
            return ElementMatchers.none();
        }

        TypePool typePool = this.typePoolFactory.createTypePool(classLoader, javaModule);
        TypeWorld typeWorld = this.typeWorldFactory.createTypeWorld(classLoader, javaModule, typePool, placeholderHelper);

        List<ElementMatcher<? super TypeDescription>> typeMatchers = new ArrayList<>();
        for(Pattern pattern : typePatterns) {
            Type type = pattern.getType();
            String expression = pattern.getExpression();

            if(Pattern.Type.MATCH_ALL_PATTERN == pattern.getType()) {
                if(createMatchAllMatcher(ruleName, typePatterns, acceptMatchAllPattern) != null)
                    return ElementMatchers.any();
            } else if(Pattern.Type.STARTS_WITH_PATTERN == type) {
                typeMatchers.add( 
                        TypeMatchers.nameStartsWith(expression) );
            } else if(Pattern.Type.EQUALS_PATTERN == type) {
                typeMatchers.add( 
                        TypeMatchers.nameEquals(expression) );
            } else if(Pattern.Type.ENDS_WITH_PATTERN == type) {
                typeMatchers.add( 
                        TypeMatchers.nameEndsWith(expression) );
            } else if(Pattern.Type.COMPLEX_PATTERN == type) {
                ElementMatcher<? super TypeDescription> typeMatcher = createTypeMatcher(ruleName, typePatterns, typeWorld, expression);
                if(typeMatcher != null)
                    typeMatchers.add(typeMatcher);
            }
        }

        return new ElementMatcher.Junction.Disjunction<>(typeMatchers);
    }


    public static class TypeExprMatcher extends ElementMatcher.Junction.ForNonNullValues<TypeDescription> {

        private final TypeWorld typeWorld;
        private final String typeExpression;
        private final TypeExpr typeExpr;

        public TypeExprMatcher(TypeWorld typeWorld, String typeExpression) {
            this.typeWorld = typeWorld;
            this.typeExpression = typeExpression;
            this.typeExpr = Expression.Parser.INSTACE.parseTypeExpression(typeWorld, typeExpression);
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        protected boolean doMatch(TypeDescription typeDescription) {
            try {
                return this.typeExpr.matches( typeWorld.resolve(typeDescription, true) );
            } catch(Throwable t) {
                return false;
            }
        }


        @Override
        public String toString() {
            return typeExpression;
        }
    }
}
