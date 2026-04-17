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
package io.gemini.aop.matcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Factory for creating ByteBuddy {@link net.bytebuddy.matcher.ElementMatcher} instances
 * from string expressions for class loaders, type names, and resource names.
 * <p>
 * Delegates expression parsing to {@link io.gemini.aspectj.weaver.ExprParser} and
 * combines multiple expressions into a disjunction. Invalid expressions are logged
 * and skipped rather than causing a hard failure.
 * </p>
 *
 * @author   martin.liu
 */
public enum ElementMatcherFactory {

    INSTANCE;


    private static final Logger LOGGER = LoggerFactory.getLogger(ElementMatcherFactory.class);

    private static final String STAR = "*";


    /**
     * Creates a conjunctive/disjunctive {@link ElementMatcher.Junction} for {@link ClassLoader}
     * by parsing each expression in the given collection.
     * <p>
     * Expressions are combined with a logical OR. If the collection is empty or all expressions
     * fail to parse, {@code defaultMatcher} is returned.
     * </p>
     *
     * @param ruleName               the rule name used in warning log messages for unparsable expressions
     * @param classLoaderExpressions the collection of class loader match expressions
     * @param defaultMatcher         the fallback matcher when no valid expressions are found
     * @return a disjunctive matcher over the parsed expressions, or {@code defaultMatcher}
     */
    public ElementMatcher.Junction<ClassLoader> createClassLoaderMatcher(String ruleName, 
            Collection<String> classLoaderExpressions, ElementMatcher.Junction<ClassLoader> defaultMatcher) {
        return createElementMatcher(ruleName, classLoaderExpressions, 
                ExprParser.INSTANCE::parseClassLoaderExpr, defaultMatcher);
    }

    /**
     * Creates an {@link ElementMatcher} for {@link ClassLoader} by parsing a single expression.
     * <p>
     * Returns {@code defaultMatcher} if the expression is blank.
     * </p>
     *
     * @param ruleName              the rule name used in warning log messages for unparsable expressions
     * @param classLoaderExpression the class loader match expression; may be blank
     * @param defaultMatcher        the fallback matcher when the expression is blank
     * @return a matcher for the parsed expression, or {@code defaultMatcher} if the expression is blank
     */
    public ElementMatcher<ClassLoader> createClassLoaderMatcher(String ruleName, 
            String classLoaderExpression, ElementMatcher.Junction<ClassLoader> defaultMatcher) {
        return StringUtils.hasText(classLoaderExpression)
                ? createElementMatcher(ruleName, classLoaderExpression, 
                        ExprParser.INSTANCE::parseClassLoaderExpr)
                : defaultMatcher;
    }

    /**
     * Creates a disjunctive {@link ElementMatcher.Junction} for type names
     * by parsing each expression in the given collection.
     * <p>
     * Expressions are combined with a logical OR. If the collection is empty or all expressions
     * fail to parse, {@code defaultMatcher} is returned.
     * </p>
     *
     * @param ruleName            the rule name used in warning log messages for unparsable expressions
     * @param typeNameExpressions the collection of type name match expressions
     * @param defaultMatcher      the fallback matcher when no valid expressions are found
     * @return a disjunctive matcher over the parsed expressions, or {@code defaultMatcher}
     */
    public ElementMatcher.Junction<String> createTypeNameMatcher(String ruleName, 
            Collection<String> typeNameExpressions, ElementMatcher.Junction<String> defaultMatcher) {
        return createElementMatcher(ruleName, typeNameExpressions, 
                ExprParser.INSTANCE::parseTypeNameExpr, defaultMatcher);
    }

    /**
     * Creates an {@link ElementMatcher} for type names by parsing a single expression.
     * <p>
     * Returns {@code defaultMatcher} if the expression is blank.
     * </p>
     *
     * @param ruleName          the rule name used in warning log messages for unparsable expressions
     * @param typeNameExpression the type name match expression; may be blank
     * @param defaultMatcher    the fallback matcher when the expression is blank
     * @return a matcher for the parsed expression, or {@code defaultMatcher} if the expression is blank
     */
    public ElementMatcher<String> createTypeNameMatcher(String ruleName, 
            String typeNameExpression, ElementMatcher.Junction<String> defaultMatcher) {
        return StringUtils.hasText(typeNameExpression) 
                ? createElementMatcher(ruleName, typeNameExpression, 
                        ExprParser.INSTANCE::parseTypeNameExpr)
                : defaultMatcher;
    }

    /**
     * Creates a disjunctive {@link ElementMatcher.Junction} for resource names
     * by parsing each expression in the given collection.
     * <p>
     * Expressions are combined with a logical OR. If the collection is empty or all expressions
     * fail to parse, {@code defaultMatcher} is returned.
     * </p>
     *
     * @param ruleName                the rule name used in warning log messages for unparsable expressions
     * @param resourceNameExpressions the collection of resource name match expressions
     * @param defaultMatcher          the fallback matcher when no valid expressions are found
     * @return a disjunctive matcher over the parsed expressions, or {@code defaultMatcher}
     */
    public ElementMatcher.Junction<String> createResourceNameMatcher(String ruleName, 
            Collection<String> resourceNameExpressions, ElementMatcher.Junction<String> defaultMatcher) {
        return createElementMatcher(ruleName, resourceNameExpressions, 
                ExprParser.INSTANCE::parseResourceNameExpr, defaultMatcher);
    }

    /**
     * Creates an {@link ElementMatcher} for resource names by parsing a single expression.
     * <p>
     * Returns {@code defaultMatcher} if the expression is blank.
     * </p>
     *
     * @param ruleName              the rule name used in warning log messages for unparsable expressions
     * @param resourceNameExpression the resource name match expression; may be blank
     * @param defaultMatcher        the fallback matcher when the expression is blank
     * @return a matcher for the parsed expression, or {@code defaultMatcher} if the expression is blank
     */
    public ElementMatcher<String> createResourceNameMatcher(String ruleName, 
            String resourceNameExpression, ElementMatcher.Junction<String> defaultMatcher) {
        return StringUtils.hasText(resourceNameExpression) 
                ? createElementMatcher(ruleName, resourceNameExpression, 
                        ExprParser.INSTANCE::parseResourceNameExpr)
                : defaultMatcher;
    }

    private <T> ElementMatcher.Junction<T> createElementMatcher(String ruleName, Collection<String> expressions, 
            Function<String, ElementMatcher<T>> parser, ElementMatcher.Junction<T> defaultMatcher) {
        if (CollectionUtils.isEmpty(expressions))
            return defaultMatcher;

        List<ElementMatcher<? super T>> elementMatchers = new ArrayList<>(expressions.size());
        for (String expression : expressions) {
            expression = expression.trim();
            ElementMatcher<T> elementMatcher = createElementMatcher(ruleName, expression, parser);

            if (elementMatcher != null)
                elementMatchers.add(elementMatcher);
        }

        return elementMatchers.size() == 0 ? defaultMatcher : new ElementMatcher.Junction.Disjunction<>(elementMatchers);
    }

    private <T> ElementMatcher<T> createElementMatcher(String ruleName, String expression,
            Function<String, ElementMatcher<T>> parser) {
        expression = expression.trim();
        if (STAR.equals(expression))
            return ElementMatchers.any();

        try {
            return parser.apply(expression);
        } catch (ExprParser.ExprParseException e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Ignored unparsable expression. \n"
                        + "  Rule: {} \n"
                        + "  Expression: {} \n"
                        + "  Syntax Error: {} \n", 
                        ruleName, 
                        expression, 
                        e.getMessage()
                );
        } catch (ExprParser.ExprLintException e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Ignored lint expression. \n"
                        + "  Rule: {} \n"
                        + "  Expression: {} \n"
                        + "  Lint message: {} \n", 
                        ruleName, 
                        expression, 
                        e.getMessage()
                );
        } catch (ExprParser.ExprUnknownException e) {
            if (LOGGER.isWarnEnabled()) {
                Throwable cause = e.getCause();
                LOGGER.warn("Ignored illegal expression. \n"
                        + "  Rule: {} \n"
                        + "  Expression: {} \n"
                        + "  Error reason: {} \n", 
                        ruleName, 
                        expression, 
                        cause.getMessage(), 
                        cause
                );
            }
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Ignored illegal expression. \n"
                        + "  Rule: {} \n"
                        + "  Expression: {} \n"
                        + "  Error reason: {} \n", 
                        ruleName, 
                        expression, 
                        e.getMessage(),
                        e
                );
        }

        return null;
    }
}
