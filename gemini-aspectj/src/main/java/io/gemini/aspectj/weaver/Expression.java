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

import java.util.Collections;
import java.util.Map;

import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.IHasPosition;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.ast.Literal;
import org.aspectj.weaver.ast.Test;
import org.aspectj.weaver.patterns.ExposedState;
import org.aspectj.weaver.patterns.FastMatchInfo;
import org.aspectj.weaver.patterns.ParserException;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.reflect.ReflectionWorld;

import io.gemini.aspectj.weaver.patterns.PatternParserV2;
import io.gemini.aspectj.weaver.world.PointcutParser;
import net.bytebuddy.description.type.TypeDescription;


public interface Expression {

    interface TypeExpr {

        boolean matches(ResolvedType matchType);


        class Default implements Expression.TypeExpr {

            private final String expression;
            private final TypePattern typePattern;

            public Default(String expression, TypePattern typePattern) {
                this.expression = expression;
                this.typePattern = typePattern;
            }

            @Override
            public boolean matches(ResolvedType type) {
                return typePattern.matchesStatically(type);
            }

            @Override
            public String toString() {
                return expression;
            }
        }
    }

    interface PointcutExpr {

        boolean fastMatch(ResolvedType matchType);

        ShadowMatch matches(ResolvedMember aMethod);


        class Default implements Expression.PointcutExpr {

            private Pointcut pointcut;
            private String expression;

            private Map<String, TypeDescription> parameters;
            private TypeWorld typeWorld;


            public Default(Pointcut pointcut, String expression, Map<String, TypeDescription> parameters, TypeWorld typeWorld) {
                this.pointcut = pointcut;
                this.expression = expression;

                this.typeWorld = typeWorld;
                this.parameters = parameters == null ? Collections.emptyMap() : parameters ;
            }


            public boolean fastMatch(ResolvedType matchType) {
                FastMatchInfo info = new FastMatchInfo(matchType, null, typeWorld.getWorld());
                FuzzyBoolean fastMatch = pointcut.fastMatch(info);
                return fastMatch.maybeTrue();
            }

            public ShadowMatch matches(ResolvedMember member) {
                Shadow shadow = typeWorld.makeExecutionShadow(member);

                return getShadowMatch(shadow, member);
            }

            private ShadowMatch getShadowMatch(Shadow forShadow, ResolvedMember aMember) {
                org.aspectj.util.FuzzyBoolean match = pointcut.match(forShadow);

                Test residueTest = Literal.TRUE;
                ExposedState state = new ExposedState(parameters.size());
                if (match.maybeTrue()) {
                    residueTest = pointcut.findResidue(forShadow, state);
                }

                return new ShadowMatch(match, residueTest, state, parameters);
            }


            /**
             * Return a string representation of this pointcut expression.
             */
            public String getPointcutExpression() {
                return expression;
            }

            @Override
            public String toString() {
                return expression;
            }
        }
    }


    enum Parser {

        INSTACE;


        public TypeExpr parseTypeExpression(TypeWorld typeWorld, String expression) {
            try {
                TypePattern typePattern = new PatternParserV2(expression).parseTypePattern();
                typePattern.resolve(typeWorld.getWorld());

                return new TypeExpr.Default(expression, typePattern);
            } catch (ParserException pEx) {
                throw new IllegalArgumentException(buildUserMessageFromParserException(expression, pEx));
            } catch (ReflectionWorld.ReflectionWorldException rwEx) {
                throw new IllegalArgumentException(rwEx.getMessage());
            }
        }


        public PointcutExpr parsePointcutExpression(TypeWorld typeWorld, String expression) {
            return PointcutParser.createPointcutParser(typeWorld).parsePointcutExpression(expression);
        }

        public PointcutExpr parsePointcutExpression(TypeWorld typeWorld, 
                String expression, TypeDescription inScope, Map<String, TypeDescription> formalParameters) {
            return PointcutParser.createPointcutParser(typeWorld).parsePointcutExpression(expression, inScope, formalParameters);
        }

        private String buildUserMessageFromParserException(String expression, ParserException ex) {
            StringBuffer msg = new StringBuffer();
            msg.append("Expression is not well-formed: expecting '");
            msg.append(ex.getMessage());
            msg.append("'");
            IHasPosition location = ex.getLocation();
            msg.append(" at character position ");
            msg.append(location.getStart());
            msg.append("\n");
            msg.append(expression);
            msg.append("\n");
            for (int i = 0; i < location.getStart(); i++) {
                msg.append(" ");
            }
            for (int j = location.getStart(); j <= location.getEnd(); j++) {
                msg.append("^");
            }
            msg.append("\n");
            return msg.toString();
        }
    }

}
