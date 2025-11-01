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
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.core.util.CollectionUtils;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public enum ElementMatcherFactory {

    INSTANCE;


    private static final Logger LOGGER = LoggerFactory.getLogger(ElementMatcherFactory.class);

    private static final String STAR = "*";


    public ElementMatcher.Junction<ClassLoader> createClassLoaderMatcher(String ruleName, 
            Collection<String> classLoaderExpressions, ElementMatcher.Junction<ClassLoader> defaultMatcher) {
        return doCreateElementMatcher(ruleName, classLoaderExpressions, 
                ExprParser.INSTANCE::parseClassLoaderExpr, defaultMatcher);
    }

    public ElementMatcher.Junction<String> createTypeNameMatcher(String ruleName, 
            Collection<String> typeNameExpressions, ElementMatcher.Junction<String> defaultMatcher) {
        return doCreateElementMatcher(ruleName, typeNameExpressions, 
                ExprParser.INSTANCE::parseTypeNameExpr, defaultMatcher);
    }

    public ElementMatcher.Junction<String> createResourceNameMatcher(String ruleName, 
            Collection<String> resourceNameExpressions, ElementMatcher.Junction<String> defaultMatcher) {
        return doCreateElementMatcher(ruleName, resourceNameExpressions, 
                ExprParser.INSTANCE::parseResourceNameExpr, defaultMatcher);
    }


    private <T> ElementMatcher.Junction<T> doCreateElementMatcher(String ruleName, Collection<String> expressions, 
            Function<String, ElementMatcher<T>> parser, ElementMatcher.Junction<T> defaultMatcher) {
        if (CollectionUtils.isEmpty(expressions))
            return ElementMatchers.none();

        List<ElementMatcher<? super T>> elementMatchers = new ArrayList<>(expressions.size());
        for (String expression : expressions) {
            expression = expression.trim();
            if (STAR.equals(expression))
                return ElementMatchers.any();

            try {
                elementMatchers.add(
                        parser.apply(expression) );
            } catch (ExprParser.ExprParseException e) {
                LOGGER.warn("Ignored unparsable expression. \n  Rule: {} \n  Expression: {} \n  Syntax Error: {} \n", 
                        ruleName, expression, e.getMessage());
            } catch (ExprParser.ExprLintException e) {
                LOGGER.warn("Ignored lint expression. \n  Rule: {} \n  Expression: {} \n  Lint message: {} \n", 
                        ruleName, expression, e.getMessage());
            } catch (ExprParser.ExprUnknownException e) {
                Throwable cause = e.getCause();
                LOGGER.warn("Ignored illegal expression. \n  Rule: {} \n  Expression: {} \n  Error reason: {} \n", 
                        ruleName, expression, cause.getMessage(), cause);
            } catch (Exception e) {
                LOGGER.warn("Ignored illegal expression. \n  Rule: {} \n  Expression: {} \n  Error reason: {} \n", 
                        ruleName, expression, e.getMessage());
            }
        }

        return elementMatchers.size() == 0 ? defaultMatcher : new ElementMatcher.Junction.Disjunction<>(elementMatchers);
    }
}
