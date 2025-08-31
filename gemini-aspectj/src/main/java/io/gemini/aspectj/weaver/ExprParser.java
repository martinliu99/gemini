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
/**
 * 
 */
package io.gemini.aspectj.weaver;

import static io.gemini.aspectj.weaver.world.PointcutParser.buildUserMessageFromParserException;
import static io.gemini.aspectj.weaver.world.PointcutParser.replaceBooleanOperators;

import java.util.Map;
import java.util.Set;

import org.aspectj.weaver.patterns.Bindings;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.ParserException;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.SimpleScope;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.tools.PointcutPrimitive;

import io.gemini.aspectj.weaver.patterns.PatternParserV2;
import io.gemini.aspectj.weaver.patterns.TypeNamePatternParser;
import io.gemini.aspectj.weaver.world.ElementExpr.ClassLoaderExpr;
import io.gemini.aspectj.weaver.world.ElementExpr.ResourceNameExpr;
import io.gemini.aspectj.weaver.world.ElementExpr.TypeExpr;
import io.gemini.aspectj.weaver.world.ElementExpr.TypeNameExpr;
import io.gemini.aspectj.weaver.world.PointcutParser;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 */
public enum ExprParser {

    INSTANCE;


    public ElementMatcher<ClassLoader> parseClassLoaderExpr(String expression) {
        try {
            expression = replaceBooleanOperators(expression);
            TypePattern typePattern = new TypeNamePatternParser(expression).parseTypePattern();

            return new ClassLoaderExpr(expression, typePattern);
        } catch (ParserException e) {
            throw new IllegalArgumentException(buildUserMessageFromParserException(expression, e));
        } catch (TypeWorld.TypeWorldException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }


    public ElementMatcher<String> parseTypeNameExpr(String expression) {
        try {
            expression = replaceBooleanOperators(expression);
            TypePattern typePattern = new TypeNamePatternParser(expression).parseTypePattern();

            return new TypeNameExpr(expression, typePattern);
        } catch (ParserException e) {
            throw new IllegalArgumentException(buildUserMessageFromParserException(expression, e));
        } catch (TypeWorld.TypeWorldException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }


    public ElementMatcher<String> parseResourceNameExpr(String expression) {
        try {
            expression = formatExpression(expression);
            expression = replaceBooleanOperators(expression);
            TypePattern typePattern = new TypeNamePatternParser(expression).parseTypePattern();

            return new ResourceNameExpr(expression, typePattern);
        } catch (ParserException e) {
            throw new IllegalArgumentException(buildUserMessageFromParserException(expression, e));
        } catch (TypeWorld.TypeWorldException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private String formatExpression(String expression) {
        return expression == null ? "" : expression.replace("/", ".");
    }


    public ElementMatcher<TypeDescription> parseTypeExpr(TypeWorld typeWorld, String expression) {
        try {
            expression = replaceBooleanOperators(expression);
            TypePattern typePattern = new PatternParserV2(expression).parseTypePattern();

            IScope scope = new SimpleScope(typeWorld.getWorld(), new FormalBinding[0]);
            typePattern = typePattern.resolveBindings(scope, Bindings.NONE, false, false);

            return new TypeExpr(expression, typeWorld, typePattern);
        } catch (ParserException e) {
            throw new IllegalArgumentException(buildUserMessageFromParserException(expression, e));
        } catch (TypeWorld.TypeWorldException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }


    public Pointcut parsePointcutExpr(TypeWorld typeWorld, Set<PointcutPrimitive> supportedPointcutKinds, 
            String pointcutExpression, TypeDescription pointcutDeclarationScope, Map<String, TypeDescription> pointcutParameters) {
        return new PointcutParser(typeWorld, supportedPointcutKinds).parsePointcut(pointcutExpression, pointcutDeclarationScope, pointcutParameters);
    }
}
