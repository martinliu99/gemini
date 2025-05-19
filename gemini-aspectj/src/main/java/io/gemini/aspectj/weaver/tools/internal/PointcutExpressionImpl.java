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
package io.gemini.aspectj.weaver.tools.internal;


import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.ast.Literal;
import org.aspectj.weaver.ast.Test;
import org.aspectj.weaver.patterns.AbstractPatternNodeVisitor;
import org.aspectj.weaver.patterns.AnnotationPointcut;
import org.aspectj.weaver.patterns.ArgsAnnotationPointcut;
import org.aspectj.weaver.patterns.ArgsPointcut;
import org.aspectj.weaver.patterns.CflowPointcut;
import org.aspectj.weaver.patterns.ExposedState;
import org.aspectj.weaver.patterns.FastMatchInfo;
import org.aspectj.weaver.patterns.IfPointcut;
import org.aspectj.weaver.patterns.NotAnnotationTypePattern;
import org.aspectj.weaver.patterns.NotPointcut;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.ThisOrTargetAnnotationPointcut;
import org.aspectj.weaver.patterns.ThisOrTargetPointcut;
import org.aspectj.weaver.patterns.WithinAnnotationPointcut;
import org.aspectj.weaver.patterns.WithinCodeAnnotationPointcut;
import org.aspectj.weaver.tools.DefaultMatchingContext;
import org.aspectj.weaver.tools.MatchingContext;

import io.gemini.aspectj.weaver.tools.PointcutExpression;
import io.gemini.aspectj.weaver.tools.PointcutParameter;
import io.gemini.aspectj.weaver.tools.ShadowMatch;
import io.gemini.aspectj.weaver.world.TypeWorld;
import io.gemini.aspectj.weaver.world.internal.ShadowImpl;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;

/**
 * Map from weaver.tools interface to internal Pointcut implementation...
 */
public class PointcutExpressionImpl implements PointcutExpression {

//    private static final Logger LOGGER = LoggerFactory.getLogger(PointcutExpressionImpl.class);


    private static boolean MATCH_INFO = false;

    private TypeWorld typeWorld;
    private Pointcut pointcut;
    private String expression;
    private PointcutParameter[] parameters;
    private MatchingContext matchContext = new DefaultMatchingContext();

    public PointcutExpressionImpl(Pointcut pointcut, String expression, PointcutParameter[] params, TypeWorld typeWorld) {
        this.pointcut = pointcut;
        this.expression = expression;
        this.typeWorld = typeWorld;
        this.parameters = params;
        if (this.parameters == null) {
            this.parameters = new PointcutParameter[0];
        }
    }

    public Pointcut getUnderlyingPointcut() {
        return this.pointcut;
    }


    /**
     * {@inheritDoc}
     */
    public void setMatchingContext(MatchingContext aMatchContext) {
        this.matchContext = aMatchContext;
    }

    public boolean couldMatchJoinpointsInType(TypeDescription aClass) {
        ResolvedType matchType = typeWorld.resolve(aClass.getName());

        FastMatchInfo info = new FastMatchInfo(matchType, null, typeWorld);
        FuzzyBoolean fastMatch = pointcut.fastMatch(info);

//        if(fastMatch.alwaysTrue() == false && fastMatch.alwaysFalse() == false)
//            LOGGER.info("type '{}' maybe result for expression: '{}'", aClass.getTypeName(), this.expression);

        boolean couldMatch = fastMatch.maybeTrue();

        if (MATCH_INFO) {
            System.out.println("MATCHINFO: fast match for '" + this.expression + "' against '" + aClass.getName() + "': "
                    + couldMatch);
        }
        return couldMatch;
    }

    public boolean mayNeedDynamicTest() {
        HasPossibleDynamicContentVisitor visitor = new HasPossibleDynamicContentVisitor();
        pointcut.traverse(visitor, null);
        return visitor.hasDynamicContent();
    }

    private ExposedState getExposedState() {
        return new ExposedState(parameters.length);
    }

    public ShadowMatch matchesMethodExecution(MethodDescription aMethod) {
        ShadowMatch match = matchesExecution(aMethod);
        if (MATCH_INFO && match.maybeMatches()) {
            System.out.println("MATCHINFO: method execution match on '" + aMethod + "' for '" + this.expression + "': "
                    + (match.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return match;
    }

    public ShadowMatch matchesConstructorExecution(MethodDescription aConstructor) {
        ShadowMatch match = matchesExecution(aConstructor);
        if (MATCH_INFO && match.maybeMatches()) {
            System.out.println("MATCHINFO: constructor execution match on '" + aConstructor + "' for '" + this.expression + "': "
                    + (match.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return match;
    }

    private ShadowMatch matchesExecution(MethodDescription aMember) {
        Shadow s = ShadowImpl.makeExecutionShadow(typeWorld, aMember, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aMember);
        sm.setWithinCode(null);
        sm.setWithinType(aMember.getDeclaringType().asErasure());
        return sm;
    }

    public ShadowMatch matchesStaticInitialization(TypeDescription aClass) {
        Shadow s = ShadowImpl.makeStaticInitializationShadow(typeWorld, aClass, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(null);
        sm.setWithinCode(null);
        sm.setWithinType(aClass);
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: static initialization match on '" + aClass.getName() + "' for '" + this.expression
                    + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesAdviceExecution(MethodDescription aMethod) {
        Shadow s = ShadowImpl.makeAdviceExecutionShadow(typeWorld, aMethod, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aMethod);
        sm.setWithinCode(null);
        sm.setWithinType(aMethod.getDeclaringType().asErasure());
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: advice execution match on '" + aMethod + "' for '" + this.expression + "': "
                    + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesInitialization(MethodDescription aConstructor) {
        Shadow s = ShadowImpl.makeInitializationShadow(typeWorld, aConstructor, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aConstructor);
        sm.setWithinCode(null);
        sm.setWithinType(aConstructor.getDeclaringType().asErasure());
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: initialization match on '" + aConstructor + "' for '" + this.expression + "': "
                    + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesPreInitialization(MethodDescription aConstructor) {
        Shadow s = ShadowImpl.makePreInitializationShadow(typeWorld, aConstructor, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aConstructor);
        sm.setWithinCode(null);
        sm.setWithinType(aConstructor.getDeclaringType().asErasure());
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: preinitialization match on '" + aConstructor + "' for '" + this.expression + "': "
                    + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesMethodCall(MethodDescription aMethod, MethodDescription withinCode) {
        Shadow s = ShadowImpl.makeCallShadow(typeWorld, aMethod, withinCode, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aMethod);
        sm.setWithinCode(withinCode);
        sm.setWithinType(withinCode.getDeclaringType().asErasure());
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: method call match on '" + aMethod + "' withinCode='" + withinCode + "' for '"
                    + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesMethodCall(MethodDescription aMethod, TypeDescription callerType) {
        Shadow s = ShadowImpl.makeCallShadow(typeWorld, aMethod, callerType, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aMethod);
        sm.setWithinCode(null);
        sm.setWithinType(callerType);
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: method call match on '" + aMethod + "' callerType='" + callerType.getName() + "' for '"
                    + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesConstructorCall(MethodDescription aConstructor, TypeDescription callerType) {
        Shadow s = ShadowImpl.makeCallShadow(typeWorld, aConstructor, callerType, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aConstructor);
        sm.setWithinCode(null);
        sm.setWithinType(callerType);
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: constructor call match on '" + aConstructor + "' callerType='" + callerType.getName()
                    + "' for '" + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesConstructorCall(MethodDescription aConstructor, MethodDescription withinCode) {
        Shadow s = ShadowImpl.makeCallShadow(typeWorld, aConstructor, withinCode, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aConstructor);
        sm.setWithinCode(withinCode);
        sm.setWithinType(withinCode.getDeclaringType().asErasure());
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: constructor call match on '" + aConstructor + "' withinCode='" + withinCode + "' for '"
                    + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesHandler(TypeDescription exceptionType, TypeDescription handlingType) {
        Shadow s = ShadowImpl.makeHandlerShadow(typeWorld, exceptionType, handlingType, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(null);
        sm.setWithinCode(null);
        sm.setWithinType(handlingType);
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: handler match on '" + exceptionType.getName() + "' handlingType='" + handlingType
                    + "' for '" + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesHandler(TypeDescription exceptionType, MethodDescription withinCode) {
        Shadow s = ShadowImpl.makeHandlerShadow(typeWorld, exceptionType, withinCode, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(null);
        sm.setWithinCode(withinCode);
        sm.setWithinType(withinCode.getDeclaringType().asErasure());
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: handler match on '" + exceptionType.getName() + "' withinCode='" + withinCode
                    + "' for '" + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesFieldGet(FieldDescription aField, TypeDescription withinType) {
        Shadow s = ShadowImpl.makeFieldGetShadow(typeWorld, aField, withinType, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aField);
        sm.setWithinCode(null);
        sm.setWithinType(withinType);
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: field get match on '" + aField + "' withinType='" + withinType.getName() + "' for '"
                    + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesFieldGet(FieldDescription aField, MethodDescription withinCode) {
        Shadow s = ShadowImpl.makeFieldGetShadow(typeWorld, aField, withinCode, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aField);
        sm.setWithinCode(withinCode);
        sm.setWithinType(withinCode.getDeclaringType().asErasure());
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: field get match on '" + aField + "' withinCode='" + withinCode + "' for '"
                    + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesFieldSet(FieldDescription aField, TypeDescription withinType) {
        Shadow s = ShadowImpl.makeFieldSetShadow(typeWorld, aField, withinType, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aField);
        sm.setWithinCode(null);
        sm.setWithinType(withinType);
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: field set match on '" + aField + "' withinType='" + withinType.getName() + "' for '"
                    + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    public ShadowMatch matchesFieldSet(FieldDescription aField, MethodDescription withinCode) {
        Shadow s = ShadowImpl.makeFieldSetShadow(typeWorld, aField, withinCode, this.matchContext);
        ShadowMatchImpl sm = getShadowMatch(s);
        sm.setSubject(aField);
        sm.setWithinCode(withinCode);
        sm.setWithinType(withinCode.getDeclaringType().asErasure());
        if (MATCH_INFO && sm.maybeMatches()) {
            System.out.println("MATCHINFO: field set match on '" + aField + "' withinCode='" + withinCode + "' for '"
                    + this.expression + "': " + (sm.alwaysMatches() ? "YES" : "MAYBE"));
        }
        return sm;
    }

    private ShadowMatchImpl getShadowMatch(Shadow forShadow) {
        org.aspectj.util.FuzzyBoolean match = pointcut.match(forShadow);
        Test residueTest = Literal.TRUE;
        ExposedState state = getExposedState();
        if (match.maybeTrue()) {
            residueTest = pointcut.findResidue(forShadow, state);
        }
        ShadowMatchImpl sm = new ShadowMatchImpl(match, residueTest, state, parameters);
        sm.setMatchingContext(this.matchContext);
        return sm;
    }

    /**
     * {@inheritDoc}
     */
    public String getPointcutExpression() {
        return expression;
    }

    private static class HasPossibleDynamicContentVisitor extends AbstractPatternNodeVisitor {
        private boolean hasDynamicContent = false;

        public boolean hasDynamicContent() {
            return hasDynamicContent;
        }

        @Override
        public Object visit(WithinAnnotationPointcut node, Object data) {
            hasDynamicContent = true;
            return null;
        }

        @Override
        public Object visit(WithinCodeAnnotationPointcut node, Object data) {
            hasDynamicContent = true;
            return null;
        }

        @Override
        public Object visit(AnnotationPointcut node, Object data) {
            hasDynamicContent = true;
            return null;
        }

        @Override
        public Object visit(ArgsAnnotationPointcut node, Object data) {
            hasDynamicContent = true;
            return null;
        }

        @Override
        public Object visit(ArgsPointcut node, Object data) {
            hasDynamicContent = true;
            return null;
        }

        @Override
        public Object visit(CflowPointcut node, Object data) {
            hasDynamicContent = true;
            return null;
        }

        @Override
        public Object visit(IfPointcut node, Object data) {
            hasDynamicContent = true;
            return null;
        }

        @Override
        public Object visit(NotAnnotationTypePattern node, Object data) {
            return node.getNegatedPattern().accept(this, data);
        }

        @Override
        public Object visit(NotPointcut node, Object data) {
            return node.getNegatedPointcut().accept(this, data);
        }

        @Override
        public Object visit(ThisOrTargetAnnotationPointcut node, Object data) {
            hasDynamicContent = true;
            return null;
        }

        @Override
        public Object visit(ThisOrTargetPointcut node, Object data) {
            hasDynamicContent = true;
            return null;
        }

    }
}
