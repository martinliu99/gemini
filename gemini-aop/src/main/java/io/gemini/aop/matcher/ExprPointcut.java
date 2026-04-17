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
 * An AspectJ-expression-based {@link io.gemini.api.aop.Pointcut} used by the 
 * {@link io.gemini.aop.factory.AdvisorFactory} to match types and methods.
 * <p>
 * The {@link AspectJExprPointcut} implementation parses the expression via
 * {@link io.gemini.aspectj.weaver.ExprParser} and evaluates it against ByteBuddy
 * {@link net.bytebuddy.description.type.TypeDescription} and
 * {@link net.bytebuddy.description.method.MethodDescription} instances.
 * </p>
 *
 * @author   martin.liu
 */
public interface ExprPointcut extends io.gemini.api.aop.Pointcut, ElementMatcher<MethodDescription> {

    /**
     * Returns the raw AspectJ pointcut expression string that this pointcut was built from.
     *
     * @return the pointcut expression; never {@code null}
     */
    String getPointcutExpression();


    /**
     * Returns an {@link ElementMatcher} that tests whether a given {@link TypeDescription}
     * is a candidate for this pointcut (fast-match / type-level filter).
     *
     * @return a type-level matcher; never {@code null}
     */
    @Override
    ElementMatcher<TypeDescription> getTypeMatcher();

    /**
     * Returns an {@link ElementMatcher} that tests whether a given {@link MethodDescription}
     * is matched by this pointcut.
     *
     * @return a method-level matcher; never {@code null}
     */
    @Override
    ElementMatcher<MethodDescription> getMethodMatcher();


    /**
     * Tests whether the given method is matched by this pointcut using the default
     * {@link PointcutParameterMatcher.True} (no parameter binding validation).
     *
     * @param targetMethod the method to test
     * @return {@code true} if the pointcut matches the method
     */
    boolean matches(MethodDescription targetMethod);

    /**
     * Tests whether the given method is matched by this pointcut, additionally validating
     * pointcut parameter bindings via the supplied {@code pointcutParameterMatcher}.
     *
     * @param targetMethod              the method to test
     * @param pointcutParameterMatcher  strategy for validating parameter bindings after expression matching
     * @return {@code true} if the pointcut matches the method and the parameter matcher approves
     */
    boolean matches(MethodDescription targetMethod, PointcutParameterMatcher pointcutParameterMatcher);


    /**
     * A strategy for matching pointcut parameters against a target method's parameter types.
     * Used by {@link AspectJExprPointcut} to validate parameter binding after expression matching.
     */
    interface PointcutParameterMatcher {

        /**
         * Tests whether the parameter bindings resolved from the pointcut expression are
         * compatible with the parameter types of the given {@code targetMethod}.
         *
         * @param targetMethod        the method whose parameters are to be validated
         * @param pointcutParameters  the named pointcut parameters resolved during expression matching
         * @return {@code true} if the parameter bindings are acceptable
         */
        boolean match(MethodDescription targetMethod, List<NamedPointcutParameter> pointcutParameters);


        /**
         * A no-op {@link PointcutParameterMatcher} that always returns {@code true},
         * used when no parameter binding validation is required.
         */
        enum True implements PointcutParameterMatcher {

            INSTANCE;

            /** 
             * {@inheritDoc}
             */
            @Override
            public boolean match(MethodDescription targetMethod, List<NamedPointcutParameter> pointcutParameters) {
                return true;
            }
        }
    }


    /**
     * AspectJ-expression-based implementation of {@link ExprPointcut}.
     * Parses the expression via {@link io.gemini.aspectj.weaver.ExprParser} and evaluates it
     * against ByteBuddy type and method descriptions using the AspectJ shadow model.
     */
    class AspectJExprPointcut implements ExprPointcut {

        private static final Logger LOGGER = LoggerFactory.getLogger(AspectJExprPointcut.class);

        private static final Set<PointcutPrimitive> SUPPORTED_PRIMITIVES = new LinkedHashSet<>();


        private final TypeWorld typeWorld;

        private final String pointcutExpression;
        private final Pointcut pointcut;

        private final TypeDescription pointcutDeclarationType;
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
         * Creates a new {@link AspectJExprPointcut} with no declaration type and no pointcut parameters.
         *
         * @param typeWorld          the type world used for type resolution and shadow creation
         * @param pointcutExpression the AspectJ pointcut expression to parse
         */
        public AspectJExprPointcut(TypeWorld typeWorld, String pointcutExpression) {
            this(typeWorld, pointcutExpression, null, Collections.emptyMap());
        }

        /**
         * Creates a new {@link AspectJExprPointcut} with an explicit declaration type and parameter map.
         *
         * @param typeWorld               the type world used for type resolution and shadow creation
         * @param pointcutExpression      the AspectJ pointcut expression to parse
         * @param pointcutDeclarationType the type in which the pointcut is declared; may be {@code null}
         * @param pointcutParametes       a map of parameter name to generic type for pointcut parameter binding
         */
        public AspectJExprPointcut(TypeWorld typeWorld, String pointcutExpression,
                TypeDescription pointcutDeclarationType, Map<String, Generic> pointcutParametes) {
            this.typeWorld = typeWorld;

            this.pointcutExpression = pointcutExpression;
            this.pointcutDeclarationType = pointcutDeclarationType;
            this.pointcutParameters = pointcutParametes;

            // Build the underlying pointcut expression.
            this.pointcut = ExprParser.INSTANCE.parsePointcutExpr(typeWorld, SUPPORTED_PRIMITIVES, 
                    pointcutExpression, pointcutDeclarationType, pointcutParameters);
        }


        /**
         * Returns the raw AspectJ pointcut expression string.
         *
         * @return the pointcut expression; never {@code null}
         */
        public String getPointcutExpression() {
            return this.pointcutExpression;
        }

        @Override
        public ElementMatcher<TypeDescription> getTypeMatcher() {
            return new ElementMatcher<TypeDescription>() {
                @Override
                public boolean matches(TypeDescription targetType) {
                    return AspectJExprPointcut.this.matches(targetType);
                }
            };
        }

        @Override
        public ElementMatcher<MethodDescription> getMethodMatcher() {
            return this;
        }

        /**
         * Tests whether the given type is a candidate for this pointcut using AspectJ fast-match.
         *
         * @param targetType the type to test
         * @return {@code true} if the pointcut may match methods on this type
         */
        public boolean matches(TypeDescription targetType) {
            try {
                FastMatchInfo info = new FastMatchInfo(typeWorld.resolve(targetType), null, typeWorld.getWorld());
                FuzzyBoolean fastMatch = pointcut.fastMatch(info);
                return fastMatch.maybeTrue();
            } catch (Exception e) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not match AspectJ ExprPointcut. \n"
                            + "  TargetType: {} \n"
                            + "  PointcutExpression: {} \n"
                            + "  Error reason: {} \n", 
                            targetType.getTypeName(), pointcutExpression, e.getMessage(), e);

                return false;
            }
        }

        /** 
         * {@inheritDoc}
         */
        public boolean matches(MethodDescription targetMethod) {
            return doMatch(targetMethod, false, PointcutParameterMatcher.True.INSTANCE);
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public boolean matches(MethodDescription targetMethod, PointcutParameterMatcher pointcutParameterMatcher) {
            return this.doMatch(targetMethod, false, pointcutParameterMatcher);
        }

        protected boolean doMatch(MethodDescription targetMethod, 
                boolean beanHasIntroductions, PointcutParameterMatcher pointcutParameterMatcher) {
            Shadow shadow = typeWorld.makeShadow(targetMethod);
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
                return pointcutParameterMatcher.match(targetMethod, 
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


        /**
         * {@inheritDoc}
         */
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
                    ObjectUtils.equals(this.pointcutDeclarationType, otherPc.pointcutDeclarationType) &&
                    ObjectUtils.equals(this.pointcutParameters, otherPc.pointcutParameters);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            int hashCode = ObjectUtils.hashCode(this.getPointcutExpression());
            hashCode = 31 * hashCode + ObjectUtils.hashCode(this.pointcutDeclarationType);
            hashCode = 31 * hashCode + ObjectUtils.hashCode(this.pointcutParameters);
            return hashCode;
        }

        /**
         * {@inheritDoc}
         */
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
