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
package io.gemini.aop.factory.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.aspectj.weaver.tools.PointcutPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.aop.Pointcut;
import io.gemini.aspectj.weaver.Expression;
import io.gemini.aspectj.weaver.Expression.PointcutExpr;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import io.gemini.aspectj.weaver.ShadowMatch;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.core.util.ObjectUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
interface ExprPointcut extends Pointcut, ElementMatcher<MethodDescription> {

    String getPointcutExpression();

    boolean matches(MethodDescription targetMethodDescription);

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


    class AspectJExprPointcut implements ExprPointcut, Serializable {

        private static final long serialVersionUID = -4563729814609710694L;


        public static final Set<PointcutPrimitive> SUPPORTED_PRIMITIVES = new HashSet<>();

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


        private static final Logger logger = LoggerFactory.getLogger(AspectJExprPointcut.class);


        private final TypeDescription pointcutDeclarationScope;
        private final String[] pointcutParameterNames;
        private final TypeDescription[] pointcutParameterTypes;

        private transient TypeWorld typeWorld;

        private String pointcutExpression;
        private transient PointcutExpr pointcutExpr;

        private final Object lock = new Object();
        private transient ConcurrentMap<MethodDescription, ShadowMatch> shadowMatchCache = new ConcurrentHashMap<>(32);


        public AspectJExprPointcut() {
            this.pointcutDeclarationScope = null;
            this.pointcutParameterNames = new String[0];
            this.pointcutParameterTypes = new TypeDescription[0];
        }

        /**
         * Create a new AspectJExpressionPointcut with the given settings.
         * @param declarationScope the declaration scope for the pointcut
         * @param paramNames the parameter names for the pointcut
         * @param paramTypes the parameter types for the pointcut
         */
        public AspectJExprPointcut(TypeDescription declarationScope, String[] paramNames, TypeDescription[] paramTypes) {
            this.pointcutDeclarationScope = declarationScope;

            this.pointcutParameterNames = paramNames == null ? new String[0] : paramNames;
            this.pointcutParameterTypes = paramTypes == null ? new TypeDescription[0] : paramTypes;
            if (pointcutParameterNames.length != pointcutParameterTypes.length) {
                throw new IllegalStateException(
                        "Number of pointcut parameter names must match number of pointcut parameter types");
            }
        }

        public void setTypeWorld(TypeWorld typeWorld) {
            this.typeWorld = typeWorld;
        }

        /**
         * Return this pointcut's expression.
         */
        public String getPointcutExpression() {
            return this.pointcutExpression;
        }

        public void setPointcutExpr(String pointcutExpr) {
            this.pointcutExpression = pointcutExpr;
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

        /**
         * Check whether this pointcut is ready to match,
         * lazily building the underlying AspectJ pointcut expression.
         */
        public void checkReadyToMatch() {
            if (getPointcutExpression() == null) {
                throw new IllegalStateException("Must set property 'expression' before attempting to match");
            }
            if (this.pointcutExpr == null) {
//                this.pointcutClassLoader = determinePointcutClassLoader();
                this.pointcutExpr = buildPointcutExpr(this.typeWorld);
            }
        }

        /**
         * Build the underlying AspectJ pointcut expression.
         */
        private PointcutExpr buildPointcutExpr(TypeWorld typeWorld) {
//            PointcutParser parser = initializePointcutParser(typeWorld);
            Map<String, TypeDescription> pointcutParameters = new LinkedHashMap<String, TypeDescription>(this.pointcutParameterNames.length);
            for (int i = 0; i < pointcutParameterNames.length; i++) {
                pointcutParameters.put(this.pointcutParameterNames[i], this.pointcutParameterTypes[i]);
            }
//            return parser.parsePointcutExpression(getPointcutExpression(),
//                    this.pointcutDeclarationScope, pointcutParameters);
            return Expression.Parser.INSTACE.
                    parsePointcutExpression(typeWorld, pointcutExpression, pointcutDeclarationScope, pointcutParameters);
        }

        public boolean matches(TypeDescription typeDescription) {
            try {
                checkReadyToMatch();

                return this.pointcutExpr.fastMatch( typeWorld.resolve(typeDescription, true) );
            } catch (Throwable ex) {
                logger.warn("PointcutExpression matching rejected target class '{}', expression: '{}'.", typeDescription, pointcutExpression, ex);
            }
            return false;
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

        protected boolean doMatch(MethodDescription method, boolean beanHasIntroductions, PointcutParameterMatcher pointcutParameterMatcher) {
            checkReadyToMatch();
//            Method targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
            ShadowMatch shadowMatch = getShadowMatch(method, method);

            // Special handling for this, target, @this, @target, @annotation
            // in Spring - we can optimize since we know we have exactly this class,
            // and there will never be matching subclass at runtime.
            if (shadowMatch.alwaysMatches()) {
                return pointcutParameterMatcher.match(method, shadowMatch.getPointcutParameters());
            }
            else if (shadowMatch.neverMatches()) {
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


        private ShadowMatch getShadowMatch(MethodDescription targetMethod, MethodDescription originalMethod) {
            // Avoid lock contention for known Methods through concurrent access...
            ShadowMatch shadowMatch = this.shadowMatchCache.get(targetMethod);
            if (shadowMatch == null) {
                synchronized (this.lock) {
                    // Not found - now check again with full lock...
                    MethodDescription methodToMatch = targetMethod;
                    shadowMatch = this.shadowMatchCache.get(targetMethod);
                    if (shadowMatch == null) {
                        try {
                            shadowMatch = doGetShadowMatch( this.pointcutExpr, methodToMatch);
                        } catch (Throwable ex) {
                            // Possibly AspectJ 1.8.10 encountering an invalid signature
                            logger.info("PointcutExpression matching rejected target method '{}'", targetMethod, ex);
                        }
                        if (shadowMatch == null) {
                            shadowMatch = new ShadowMatch(org.aspectj.util.FuzzyBoolean.NO, null, null, null);
                        }
                    }
                }
            }
            return shadowMatch;
        }

        protected ShadowMatch doGetShadowMatch(PointcutExpr pointcutExpr, MethodDescription methodToMatch) {
//            ResolvedMember resolvedMember = typeWorld.resolvedMember(methodToMatch.getMethodDescription());
//            return PointcutExpr.matchesMemberExecution(resolvedMember);

            return pointcutExpr.matches( typeWorld.resolved(methodToMatch, true) );

            //            if(methodToMatch.getMethodDescription().isTypeInitializer())
//                return PointcutExpr.matchesStaticInitialization(methodToMatch.getResolvedType());
//            else 
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
                    ObjectUtils.equals(this.pointcutParameterNames, otherPc.pointcutParameterNames) &&
                    ObjectUtils.equals(this.pointcutParameterTypes, otherPc.pointcutParameterTypes);
        }

        @Override
        public int hashCode() {
            int hashCode = ObjectUtils.hashCode(this.getPointcutExpression());
            hashCode = 31 * hashCode + ObjectUtils.hashCode(this.pointcutDeclarationScope);
            hashCode = 31 * hashCode + ObjectUtils.hashCode(this.pointcutParameterNames);
            hashCode = 31 * hashCode + ObjectUtils.hashCode(this.pointcutParameterTypes);
            return hashCode;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("AspectJExpressionPointcut: ");
            if (this.pointcutParameterNames != null && this.pointcutParameterTypes != null) {
                sb.append("(");
                for (int i = 0; i < this.pointcutParameterTypes.length; i++) {
                    sb.append(this.pointcutParameterTypes[i].getName());
                    sb.append(" ");
                    sb.append(this.pointcutParameterNames[i]);
                    if ((i+1) < this.pointcutParameterTypes.length) {
                        sb.append(", ");
                    }
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

        //---------------------------------------------------------------------
        // Serialization support
        //---------------------------------------------------------------------

        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
            // Rely on default serialization, just initialize state after deserialization.
            ois.defaultReadObject();

            // Initialize transient fields.
            // PointcutExpr will be initialized lazily by checkReadyToMatch()
            this.shadowMatchCache = new ConcurrentHashMap<>(32);
        }
    }

}
