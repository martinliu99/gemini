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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.patterns.ExposedState;
import org.aspectj.weaver.patterns.FastMatchInfo;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.tools.PointcutPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.aspectj.weaver.PointcutParameter;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.core.util.ObjectUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface ExprPointcut extends io.gemini.api.aop.Pointcut, ElementMatcher<MethodDescription> {

    String getPointcutExpression();


    @Override
    ElementMatcher<TypeDescription> getTypeMatcher();

    @Override
    ElementMatcher<MethodDescription> getMethodMatcher();


    boolean matches(MethodDescription methodDescription);

    boolean matches(MethodDescription methodDescription, PointcutParameterMatcher pointcutParameterMatcher);


    interface PointcutParameterMatcher {

        boolean match(MethodDescription methodDescription, List<NamedPointcutParameter> pointcutParameters);

        enum True implements PointcutParameterMatcher {

            INSTANCE;

            /** 
             * {@inheritDoc}
             */
            @Override
            public boolean match(MethodDescription methodDescription, List<NamedPointcutParameter> pointcutParameters) {
                return true;
            }
        }
    }


    class AspectJExprPointcut implements ExprPointcut {

        private static final Logger LOGGER = LoggerFactory.getLogger(AspectJExprPointcut.class);

        private static final Set<PointcutPrimitive> SUPPORTED_PRIMITIVES = new LinkedHashSet<>();


        private final TypeWorld typeWorld;

        private final String pointcutExpression;
        private final Pointcut pointcut;

        private final TypeDescription pointcutDeclarationScope;
        private final Map<String, Generic> pointcutParameters;


        static {
            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.REFERENCE);

            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.STATIC_INITIALIZATION);
            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.EXECUTION);

            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.WITHIN);
            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.ARGS);
            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.THIS);
            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.TARGET);

            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_ANNOTATION);
            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_WITHIN);
            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_ARGS);
            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_THIS);
            SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_TARGET);
        }


        /**
         * Create a new AspectJExprPointcut with the given settings.
         */
        public AspectJExprPointcut(TypeWorld typeWorld, String pointcutExpression) {
            this(typeWorld, pointcutExpression, null, Collections.emptyMap());
        }

        /**
         * Create a new AspectJExprPointcut with the given settings.
         * 
         * @param declarationScope the declaration scope for the pointcut
         * @param pointcutParameterNames the parameter names for the pointcut
         * @param pointcutParameterTypes the parameter types for the pointcut
         */
        public AspectJExprPointcut(TypeWorld typeWorld, String pointcutExpression,
                TypeDescription declarationScope, Map<String, Generic> pointcutParametes) {
            this.typeWorld = typeWorld;

            this.pointcutExpression = pointcutExpression;
            this.pointcutDeclarationScope = declarationScope;
            this.pointcutParameters = pointcutParametes;

            // Build the underlying pointcut expression.
            this.pointcut = ExprParser.INSTANCE.parsePointcutExpr(typeWorld, SUPPORTED_PRIMITIVES, 
                    pointcutExpression, pointcutDeclarationScope, pointcutParameters);
        }


        /**
         * Return this pointcut's expression.
         */
        public String getPointcutExpression() {
            return this.pointcutExpression;
        }

        @Override
        public ElementMatcher<TypeDescription> getTypeMatcher() {
            return new ElementMatcher<TypeDescription>() {
                @Override
                public boolean matches(TypeDescription typeDescription) {
                    return AspectJExprPointcut.this.matches(typeDescription);
                }
            };
        }

        @Override
        public ElementMatcher<MethodDescription> getMethodMatcher() {
            return this;
        }

        public boolean matches(TypeDescription typeDescription) {
            try {
                FastMatchInfo info = new FastMatchInfo(typeWorld.resolve(typeDescription), null, typeWorld.getWorld());
                FuzzyBoolean fastMatch = pointcut.fastMatch(info);
                return fastMatch.maybeTrue();
            } catch (Exception e) {
                LOGGER.warn("Could not match AspectJ ExprPointcut. \n"
                        + "  TargetType: {} \n"
                        + "  PointcutExpression: {} \n"
                        + "  Error reason: {} \n", 
                        typeDescription.getTypeName(), pointcutExpression, e.getMessage(), e);

                return false;
            }
        }

        /** 
         * {@inheritDoc}
         */
        public boolean matches(MethodDescription methodDescription) {
            return doMatch(methodDescription, false, PointcutParameterMatcher.True.INSTANCE);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public boolean matches(MethodDescription methodDescription, PointcutParameterMatcher pointcutParameterMatcher) {
            return this.doMatch(methodDescription, false, pointcutParameterMatcher);
        }

        protected boolean doMatch(MethodDescription methodDescription, boolean beanHasIntroductions, PointcutParameterMatcher pointcutParameterMatcher) {
            Shadow shadow = typeWorld.makeShadow(methodDescription);
            FuzzyBoolean matchResult = pointcut.match(shadow);

//            Test residueTest = Literal.TRUE;
            ExposedState exposedState = new ExposedState(pointcutParameters.size());
            if (matchResult.maybeTrue()) {
                pointcut.findResidue(shadow, exposedState);
            }


            // Special handling for this, target, @this, @target, @annotation
            // in Spring - we can optimize since we know we have exactly this class,
            // and there will never be matching subclass at runtime.
            if (matchResult.alwaysTrue()) {
                return pointcutParameterMatcher.match(methodDescription, 
                        createParamterBindings(pointcutParameters, exposedState));
            }
            else if (matchResult.alwaysFalse()) {
                return false;
            }
            else {
                // the maybe case
                if (beanHasIntroductions) {
                    return true;
                }
                // A match test returned maybe - if there are any subtype sensitive variables
                // involved in the test (this, target, at_this, at_target, at_annotation) then
                // we say this is not a match as in Spring there will never be a different
                // runtime subtype.
//                RuntimeTestWalker walker = getRuntimeTestWalker(shadowMatch);
//                return (!walker.testsSubtypeSensitiveVars() || walker.testTargetInstanceOfResidue(targetClass));
                return false;
            }
        }

        private List<NamedPointcutParameter> createParamterBindings(
                Map<String, Generic> pointcutParameters, ExposedState exposedState) {
            int i = 0;
            List<NamedPointcutParameter> parameterBindings = new ArrayList<>(pointcutParameters.size());
            for (Entry<String, Generic> entry : pointcutParameters.entrySet()) {
                PointcutParameter var = (PointcutParameter) exposedState.vars[i++];
                if (var == null)
                    continue;

                parameterBindings.add( 
                        new PointcutParameter.Default(entry.getKey(), entry.getValue(), var) );
            }

            return parameterBindings;
        }


        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AspectJExprPointcut)) {
                return false;
            }
            AspectJExprPointcut otherPc = (AspectJExprPointcut) other;
            return ObjectUtils.equals(this.getPointcutExpression(), otherPc.getPointcutExpression()) &&
                    ObjectUtils.equals(this.pointcutDeclarationScope, otherPc.pointcutDeclarationScope) &&
                    ObjectUtils.equals(this.pointcutParameters, otherPc.pointcutParameters);
        }

        @Override
        public int hashCode() {
            int hashCode = ObjectUtils.hashCode(this.getPointcutExpression());
            hashCode = 31 * hashCode + ObjectUtils.hashCode(this.pointcutDeclarationScope);
            hashCode = 31 * hashCode + ObjectUtils.hashCode(this.pointcutParameters);
            return hashCode;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("AspectJExprPointcut: ");

            int i = 0;
            if (this.pointcutParameters != null) {
                sb.append("(");
                for (Entry<String, Generic> entry : this.pointcutParameters.entrySet()) {
                    sb.append(entry.getValue().getTypeName());
                    sb.append(" ");
                    sb.append(entry.getKey());
                    if ((i+1) < pointcutParameters.size()) {
                        sb.append(", ");
                    }
                    i++;
                }
                sb.append(")");
            }
            sb.append(" ");
            if (getPointcutExpression() != null) {
                sb.append(getPointcutExpression());
            }
            else {
                sb.append("<pointcut expression not set>");
            }
            return sb.toString();
        }
    }

}
