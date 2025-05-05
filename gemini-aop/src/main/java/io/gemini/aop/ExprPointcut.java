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
package io.gemini.aop;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.reflect.ReflectionWorld.ReflectionWorldException;
import org.aspectj.weaver.tools.ContextBasedMatcher;
import org.aspectj.weaver.tools.FuzzyBoolean;
import org.aspectj.weaver.tools.MatchingContext;
import org.aspectj.weaver.tools.PointcutDesignatorHandler;
import org.aspectj.weaver.tools.PointcutPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.matcher.MethodMatcher;
import io.gemini.aop.matcher.TypeMatcher;
import io.gemini.api.aspect.Pointcut;
import io.gemini.aspectj.weaver.ParameterBinding;
import io.gemini.aspectj.weaver.tools.JoinPointMatch;
import io.gemini.aspectj.weaver.tools.PointcutExpression;
import io.gemini.aspectj.weaver.tools.PointcutParameter;
import io.gemini.aspectj.weaver.tools.PointcutParser;
import io.gemini.aspectj.weaver.tools.ShadowMatch;
import io.gemini.aspectj.weaver.tools.internal.ShadowMatchImpl;
import io.gemini.aspectj.weaver.world.TypeWorld;
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
public interface ExprPointcut extends Pointcut {

    String getPointcutExpr();

    ShadowMatch getShadowMatch(MethodDescription methodDescription);


    class AspectJExprPointcut implements ExprPointcut, Serializable {

        /**
         * 
         */
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

        private String pointcutExpr;
        private transient PointcutExpression pointcutExpression;

        private ElementMatcher<String> classLoaderMatcher;

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
        public String getPointcutExpr() {
            return this.pointcutExpr;
        }

        public void setPointcutExpr(String pointcutExpr) {
            this.pointcutExpr = pointcutExpr;
        }

        /**
         * Return the underlying AspectJ pointcut expression.
         */
        public PointcutExpression getPointcutExpression() {
            checkReadyToMatch();
            return this.pointcutExpression;
        }


        @Override
        public ElementMatcher<String> getClassLoaderMatcher() {
            return this.classLoaderMatcher;
        }

        public void setClassLoaderMatcher(ElementMatcher<String> classLoaderMatcher) {
            this.classLoaderMatcher = classLoaderMatcher;
        }

        @Override
        public ElementMatcher.Junction<TypeDescription> getTypeMatcher() {
            return new TypeMatcher() {
                @Override
                protected boolean doMatch(TypeDescription typeDescription) {
                    return AspectJExprPointcut.this.doMatch(typeDescription);
                }
            };
        }

        @Override
        public ElementMatcher.Junction<MethodDescription> getMethodMatcher() {
            return new MethodMatcher() {
                @Override
                protected boolean doMatch(MethodDescription methodDescription) {
                    return AspectJExprPointcut.this.doMatch(methodDescription);
                }
            };
        }

        /**
         * Check whether this pointcut is ready to match,
         * lazily building the underlying AspectJ pointcut expression.
         */
        public void checkReadyToMatch() {
            if (getPointcutExpr() == null) {
                throw new IllegalStateException("Must set property 'expression' before attempting to match");
            }
            if (this.pointcutExpression == null) {
//                this.pointcutClassLoader = determinePointcutClassLoader();
                this.pointcutExpression = buildPointcutExpression(this.typeWorld);
            }
        }

        /**
         * Determine the ClassLoader to use for pointcut evaluation.
         */
//        private ClassLoader determinePointcutClassLoader() {
//            if (this.pointcutDeclarationScope != null) {
//                return this.pointcutDeclarationScope.getClassLoader();
//            }
//            return ClassUtils.getDefaultClassLoader();
//        }

        /**
         * Build the underlying AspectJ pointcut expression.
         */
        private PointcutExpression buildPointcutExpression(TypeWorld typeWorld) {
            PointcutParser parser = initializePointcutParser(typeWorld);
            PointcutParameter[] pointcutParameters = new PointcutParameter[this.pointcutParameterNames.length];
            for (int i = 0; i < pointcutParameters.length; i++) {
                pointcutParameters[i] = parser.createPointcutParameter(
                        this.pointcutParameterNames[i], this.pointcutParameterTypes[i]);
            }
            return parser.parsePointcutExpression(getPointcutExpr(),
                    this.pointcutDeclarationScope, pointcutParameters);
        }

        /**
         * Initialize the underlying AspectJ pointcut parser.
         */
        private PointcutParser initializePointcutParser(TypeWorld typeWorld) {
            PointcutParser parser = PointcutParser
                    .getPointcutParserSupportingSpecifiedPrimitivesAndUsingSpecifiedClassLoaderForResolution(
                            SUPPORTED_PRIMITIVES, typeWorld);
            parser.registerPointcutDesignatorHandler(new ExistsPointcutDesignatorHandler());
            return parser;
        }

        protected boolean doMatch(TypeDescription targetClass) {
            try {
                checkReadyToMatch();

                try {
                    return this.pointcutExpression.couldMatchJoinpointsInType(targetClass);
                }
                catch (ReflectionWorldException ex) {
                    // TODO: ReflectionWorldException
                    
//                    logger.debug("PointcutExpression matching rejected target class - trying fallback expression", ex);
                    // Actually this is still a "maybe" - treat the pointcut as dynamic if we don't know enough yet
                    PointcutExpression fallbackExpression = getFallbackPointcutExpression(targetClass);
                    if (fallbackExpression != null) {
                        return fallbackExpression.couldMatchJoinpointsInType(targetClass);
                    }
                }
            }
            catch (Throwable ex) {
                logger.warn("PointcutExpression matching rejected target class '{}', expression: '{}'.", targetClass, pointcutExpr, ex);
            }
            return false;
        }


        protected boolean doMatch(MethodDescription  methodDescription, Object... args) {
            return this.doMatch(methodDescription, methodDescription.getDeclaringType().asErasure(), false);
        }


        protected boolean doMatch(MethodDescription method, TypeDescription targetClass, boolean beanHasIntroductions) {
            checkReadyToMatch();
//            Method targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
            ShadowMatch shadowMatch = getShadowMatch(method, method);

            // Special handling for this, target, @this, @target, @annotation
            // in Spring - we can optimize since we know we have exactly this class,
            // and there will never be matching subclass at runtime.
            if (shadowMatch.alwaysMatches()) {
                return true;
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


        protected boolean doMatch(MethodDescription.InDefinedShape method, TypeDescription targetClass) {
            return this.doMatch(method, targetClass, false);
        }


//        public boolean isRuntime() {
//            checkReadyToMatch();
//            return this.pointcutExpression.mayNeedDynamicTest();
//        }
    //


//        public boolean matches(Method method, Class<?> targetClass, Object... args) {
//            checkReadyToMatch();
//            ShadowMatch shadowMatch = getShadowMatch(AopUtils.getMostSpecificMethod(method, targetClass), method);
//            ShadowMatch originalShadowMatch = getShadowMatch(method, method);
    //
//            // Bind Spring AOP proxy to AspectJ "this" and Spring AOP target to AspectJ target,
//            // consistent with return of MethodInvocationProceedingJoinpoint
//            ProxyMethodInvocation pmi = null;
//            Object targetObject = null;
//            Object thisObject = null;
//            try {
//                MethodInvocation mi = ExposeInvocationInterceptor.currentInvocation();
//                targetObject = mi.getThis();
//                if (!(mi instanceof ProxyMethodInvocation)) {
//                    throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
//                }
//                pmi = (ProxyMethodInvocation) mi;
//                thisObject = pmi.getProxy();
//            }
//            catch (IllegalStateException ex) {
//                // No current invocation...
//                if (logger.isInfoEnabled()) {
//                    logger.info("Could not access current invocation - matching with limited context: " + ex);
//                }
//            }
    //
//            try {
//                JoinpointMatch JoinpointMatch = shadowMatch.matchesJoinpoint(thisObject, targetObject, args);
    //
//                /*
//                 * Do a final check to see if any this(TYPE) kind of residue match. For
//                 * this purpose, we use the original method's (proxy method's) shadow to
//                 * ensure that 'this' is correctly checked against. Without this check,
//                 * we get incorrect match on this(TYPE) where TYPE matches the target
//                 * type but not 'this' (as would be the case of JDK dynamic proxies).
//                 * <p>See SPR-2979 for the original bug.
//                 */
//                if (pmi != null) {  // there is a current invocation
//                    RuntimeTestWalker originalMethodResidueTest = getRuntimeTestWalker(originalShadowMatch);
//                    if (!originalMethodResidueTest.testThisInstanceOfResidue(thisObject.getClass())) {
//                        return false;
//                    }
//                    if (JoinpointMatch.matches()) {
//                        bindParameters(pmi, JoinpointMatch);
//                    }
//                }
    //
//                return JoinpointMatch.matches();
//            }
//            catch (Throwable ex) {
//                if (logger.isInfoEnabled()) {
//                    logger.info("Failed to evaluate join point for arguments " + Arrays.asList(args) +
//                            " - falling back to non-match", ex);
//                }
//                return false;
//            }
//        }
    //
//        protected String getCurrentProxiedBeanName() {
//            return ProxyCreationContext.getCurrentProxiedBeanName();
//        }
    //
    //
//        /**
//         * Get a new pointcut expression based on a target class's loader rather than the default.
//         */
        private PointcutExpression getFallbackPointcutExpression(TypeDescription targetClass) {
//            try {
//                ClassLoader classLoader = targetClass.getClassLoader();
//                if (classLoader != null && classLoader != this.pointcutClassLoader) {
//                    return buildPointcutExpression(classLoader);
//                }
//            }
//            catch (Throwable ex) {
//                logger.info("Failed to create fallback PointcutExpression", ex);
//            }
            return null;
        }
    //
//        private RuntimeTestWalker getRuntimeTestWalker(ShadowMatch shadowMatch) {
//            if (shadowMatch instanceof DefensiveShadowMatch) {
//                return new RuntimeTestWalker(((DefensiveShadowMatch) shadowMatch).primary);
//            }
//            return new RuntimeTestWalker(shadowMatch);
//        }
    //
//        private void bindParameters(ProxyMethodInvocation invocation, JoinpointMatch jpm) {
//            // Note: Can't use JoinpointMatch.getClass().getName() as the key, since
//            // Spring AOP does all the matching at a join point, and then all the invocations
//            // under this scenario, if we just use JoinpointMatch as the key, then
//            // 'last man wins' which is not what we want at all.
//            // Using the expression is guaranteed to be safe, since 2 identical expressions
//            // are guaranteed to bind in exactly the same way.
//            invocation.setUserAttribute(getExpression(), jpm);
//        }



        @Override
        public ShadowMatch getShadowMatch(MethodDescription methodDescription) {
            return getShadowMatch(methodDescription, methodDescription);
        }

        private ShadowMatch getShadowMatch(MethodDescription targetMethod, MethodDescription originalMethod) {
            // Avoid lock contention for known Methods through concurrent access...
            ShadowMatch shadowMatch = this.shadowMatchCache.get(targetMethod);
            if (shadowMatch == null) {
                synchronized (this.lock) {
                    // Not found - now check again with full lock...
                    PointcutExpression fallbackExpression = null;
                    MethodDescription methodToMatch = targetMethod;
                    shadowMatch = this.shadowMatchCache.get(targetMethod);
                    if (shadowMatch == null) {
                        try {
                            try {
                                shadowMatch = doGetShadowMatch( this.pointcutExpression, methodToMatch);
                            }
                            catch (ReflectionWorldException ex) {
                                // TODO: ReflectionWorldException
                                
                                // Failed to introspect target method, probably because it has been loaded
                                // in a special ClassLoader. Let's try the declaring ClassLoader instead...
                                try {
                                    fallbackExpression = getFallbackPointcutExpression(methodToMatch.getDeclaringType().asErasure());
                                    if (fallbackExpression != null) {
                                        shadowMatch = fallbackExpression.matchesMethodExecution(methodToMatch);
                                    }
                                }
                                catch (ReflectionWorldException ex2) {
                                    fallbackExpression = null;
                                }
                            }
//                            if (shadowMatch == null && targetMethod != originalMethod) {
//                                methodToMatch = originalMethod;
//                                try {
//                                    shadowMatch = this.pointcutExpression.matchesMethodExecution(methodToMatch);
//                                }
//                                catch (ReflectionWorldException ex3) {
//                                    // Could neither introspect the target class nor the proxy class ->
//                                    // let's try the original method's declaring class before we give up...
//                                    try {
//                                        fallbackExpression = getFallbackPointcutExpression(methodToMatch.getDeclaringClass());
//                                        if (fallbackExpression != null) {
//                                            shadowMatch = fallbackExpression.matchesMethodExecution(methodToMatch);
//                                        }
//                                    }
//                                    catch (ReflectionWorldException ex4) {
//                                        fallbackExpression = null;
//                                    }
//                                }
//                            }
                        }
                        catch (Throwable ex) {
                            // Possibly AspectJ 1.8.10 encountering an invalid signature
                            logger.debug("PointcutExpression matching rejected target method '{}'", targetMethod, ex);
                            fallbackExpression = null;
                        }
                        if (shadowMatch == null) {
                            shadowMatch = new ShadowMatchImpl(org.aspectj.util.FuzzyBoolean.NO, null, null, null);
                        }
                        else if (shadowMatch.maybeMatches() && fallbackExpression != null) {
                            shadowMatch = new DefensiveShadowMatch(shadowMatch,
                                    fallbackExpression.matchesMethodExecution(methodToMatch));
                        }
                        
//                        if(shadowMatch != null)
//                        this.shadowMatchCache.put(targetMethod, shadowMatch);
                    }
                }
            }
            return shadowMatch;
        }

        protected ShadowMatch doGetShadowMatch(PointcutExpression pointcutExpression, MethodDescription methodToMatch) {
            if(methodToMatch.isTypeInitializer())
                return pointcutExpression.matchesStaticInitialization(methodToMatch.getDeclaringType().asErasure());
            else if(methodToMatch.isConstructor())
                return pointcutExpression.matchesConstructorExecution(methodToMatch);
            else if(methodToMatch.isMethod())
                return pointcutExpression.matchesMethodExecution(methodToMatch);
            else// TODO
                throw new RuntimeException();
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
            return ObjectUtils.equals(this.getPointcutExpr(), otherPc.getPointcutExpr()) &&
                    ObjectUtils.equals(this.pointcutDeclarationScope, otherPc.pointcutDeclarationScope) &&
                    ObjectUtils.equals(this.pointcutParameterNames, otherPc.pointcutParameterNames) &&
                    ObjectUtils.equals(this.pointcutParameterTypes, otherPc.pointcutParameterTypes);
        }

        @Override
        public int hashCode() {
            int hashCode = ObjectUtils.hashCode(this.getPointcutExpr());
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
            if (getPointcutExpr() != null) {
                sb.append(getPointcutExpr());
            }
            else {
                sb.append("<pointcut expression not set>");
            }
            return sb.toString();
        }


        /**
         * Handler for the Spring-specific {@code bean()} pointcut designator
         * extension to AspectJ.
         * <p>This handler must be added to each pointcut object that needs to
         * handle the {@code bean()} PCD. Matching context is obtained
         * automatically by examining a thread local variable and therefore a matching
         * context need not be set on the pointcut.
         */
        @Deprecated
        private class ExistsPointcutDesignatorHandler implements PointcutDesignatorHandler {

            private static final String BEAN_DESIGNATOR_NAME = "exists";

            @Override
            public String getDesignatorName() {
                return BEAN_DESIGNATOR_NAME;
            }

            @Override
            public ContextBasedMatcher parse(String expression) {
                return new ExistsContextMatcher(expression);
            }
        }


        /**
         * Matcher class for the BeanNamePointcutDesignatorHandler.
         * <p>Dynamic match tests for this matcher always return true,
         * since the matching decision is made at the proxy creation time.
         * For static match tests, this matcher abstains to allow the overall
         * pointcut to match even when negation is used with the bean() pointcut.
         */
        @Deprecated
        private class ExistsContextMatcher implements ContextBasedMatcher {

//            private final NamePattern expressionPattern;

            public ExistsContextMatcher(String expression) {
//                this.expressionPattern = new NamePattern(expression);
//                InternalUseOnlyPointcutParser parse = new InternalUseOnlyPointcutParser();
//                parse.parsePointcutExpression(expression)
                PatternParser parser = new PatternParser(expression);
                TypePattern s = parser.parseTypePattern();
                s.getExactType().getClassName();
            }

            @Override
            @SuppressWarnings("rawtypes")
            @Deprecated
            public boolean couldMatchJoinPointsInType(Class someClass) {
                return (contextMatch(someClass) == FuzzyBoolean.YES);
            }

            @Override
            @SuppressWarnings("rawtypes")
            @Deprecated
            public boolean couldMatchJoinPointsInType(Class someClass, MatchingContext context) {
                return (contextMatch(someClass) == FuzzyBoolean.YES);
            }

            @Override
            public boolean matchesDynamically(MatchingContext context) {
                return true;
            }


            public FuzzyBoolean matchesStatically(MatchingContext context) {
                return contextMatch(null);
            }

            @Override
            public boolean mayNeedDynamicTest() {
                return false;
            }

            private FuzzyBoolean contextMatch(Class<?> targetType) {
                return FuzzyBoolean.YES;
//                String advisedBeanName = getCurrentProxiedBeanName();
//                if (advisedBeanName == null) {  // no proxy creation in progress
//                    // abstain; can't return YES, since that will make pointcut with negation fail
//                    return FuzzyBoolean.MAYBE;
//                }
//                if (BeanFactoryUtils.isGeneratedBeanName(advisedBeanName)) {
//                    return FuzzyBoolean.NO;
//                }
//                if (targetType != null) {
//                    boolean isFactory = FactoryBean.class.isAssignableFrom(targetType);
//                    return FuzzyBoolean.fromBoolean(
//                            matchesBeanName(isFactory ? BeanFactory.FACTORY_BEAN_PREFIX + advisedBeanName : advisedBeanName));
//                }
//                else {
//                    return FuzzyBoolean.fromBoolean(matchesBeanName(advisedBeanName) ||
//                            matchesBeanName(BeanFactory.FACTORY_BEAN_PREFIX + advisedBeanName));
//                }
            }

//            private boolean matchesBeanName(String advisedBeanName) {
//                if (this.expressionPattern.matches(advisedBeanName)) {
//                    return true;
//                }
//                if (beanFactory != null) {
//                    String[] aliases = beanFactory.getAliases(advisedBeanName);
//                    for (String alias : aliases) {
//                        if (this.expressionPattern.matches(alias)) {
//                            return true;
//                        }
//                    }
//                }
//                return false;
//            }
        }


        //---------------------------------------------------------------------
        // Serialization support
        //---------------------------------------------------------------------

        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
            // Rely on default serialization, just initialize state after deserialization.
            ois.defaultReadObject();

            // Initialize transient fields.
            // pointcutExpression will be initialized lazily by checkReadyToMatch()
            this.shadowMatchCache = new ConcurrentHashMap<>(32);
        }


        private static class DefensiveShadowMatch implements ShadowMatch {

            private final ShadowMatch primary;

            private final ShadowMatch other;

            public DefensiveShadowMatch(ShadowMatch primary, ShadowMatch other) {
                this.primary = primary;
                this.other = other;
            }

            @Override
            public boolean alwaysMatches() {
                return this.primary.alwaysMatches();
            }

            @Override
            public boolean maybeMatches() {
                return this.primary.maybeMatches();
            }

            @Override
            public boolean neverMatches() {
                return this.primary.neverMatches();
            }

            @Override
            public JoinPointMatch matchesJoinPoint(Object thisObject, Object targetObject, Object[] args) {
                try {
                    return this.primary.matchesJoinPoint(thisObject, targetObject, args);
                }
                catch (ReflectionWorldException ex) {
                    return this.other.matchesJoinPoint(thisObject, targetObject, args);
                }
            }

            @Override
            public void setMatchingContext(MatchingContext aMatchContext) {
                this.primary.setMatchingContext(aMatchContext);
                this.other.setMatchingContext(aMatchContext);
            }

            @Override
            public List<ParameterBinding> getParameterBindings() {
                // TODO:
                return null;
            }
        }
    }

}
