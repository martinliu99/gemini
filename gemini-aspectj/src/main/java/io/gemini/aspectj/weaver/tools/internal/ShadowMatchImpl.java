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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.ast.And;
import org.aspectj.weaver.ast.Call;
import org.aspectj.weaver.ast.FieldGetCall;
import org.aspectj.weaver.ast.HasAnnotation;
import org.aspectj.weaver.ast.ITestVisitor;
import org.aspectj.weaver.ast.Instanceof;
import org.aspectj.weaver.ast.Literal;
import org.aspectj.weaver.ast.Not;
import org.aspectj.weaver.ast.Or;
import org.aspectj.weaver.ast.Test;
import org.aspectj.weaver.ast.Var;
import org.aspectj.weaver.internal.tools.MatchingContextBasedTest;
import org.aspectj.weaver.patterns.ExposedState;
import org.aspectj.weaver.tools.DefaultMatchingContext;
import org.aspectj.weaver.tools.MatchingContext;

import io.gemini.aspectj.weaver.ParameterBinding;
import io.gemini.aspectj.weaver.tools.JoinPointMatch;
import io.gemini.aspectj.weaver.tools.PointcutParameter;
import io.gemini.aspectj.weaver.tools.ShadowMatch;
import io.gemini.aspectj.weaver.world.internal.VarImpl;
import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.type.TypeDescription;



/**
 * @author colyer Implementation of ShadowMatch for reflection based worlds.
 */
public class ShadowMatchImpl implements ShadowMatch {

    private FuzzyBoolean match;
    private ExposedState state;
    private Test residualTest;
    private PointcutParameter[] params;
    private Member withinCode;
    private Member subject;
    private TypeDescription withinType;
    private MatchingContext matchContext = new DefaultMatchingContext();

    private List<ParameterBinding> parameterBindings;


    public ShadowMatchImpl(FuzzyBoolean match, Test test, ExposedState state, PointcutParameter[] params) {
        this.match = match;
        this.residualTest = test;
        this.state = state;
        this.params = params;

        this.parameterBindings = params == null ? Collections.emptyList() : createParamterBindings(params);
    }

    private List<ParameterBinding> createParamterBindings(PointcutParameter[] params) {
        List<ParameterBinding> parameterBindings = new ArrayList<>(params.length);

        for(int i = 0; i < params.length; i++) {
            PointcutParameter pointcutParameter = params[i];
            VarImpl var = (VarImpl) state.vars[i];
            if(var == null)
                continue;

            parameterBindings.add(
                    new ParameterBinding(pointcutParameter.getName(), pointcutParameter.getType(), var.getCategory(), var.getArgsIndex()) );
        }

        return parameterBindings;
    }

    public void setWithinCode(Member aMember) {
        this.withinCode = aMember;
    }

    public void setSubject(Member aMember) {
        this.subject = aMember;
    }

    public void setWithinType(TypeDescription aClass) {
        this.withinType = aClass;
    }

    public boolean alwaysMatches() {
        return match.alwaysTrue();
    }

    public boolean maybeMatches() {
        return match.maybeTrue();
    }

    public boolean neverMatches() {
        return match.alwaysFalse();
    }

    public JoinPointMatch matchesJoinPoint(Object thisObject, Object targetObject, Object[] args) {
        if (neverMatches()) {
            return JoinPointMatchImpl.NO_MATCH;
        }
        if (new RuntimeTestEvaluator(residualTest, thisObject, targetObject, args, this.matchContext).matches()) {
            return new JoinPointMatchImpl(getPointcutParameters(thisObject, targetObject, args));
        } else {
            return JoinPointMatchImpl.NO_MATCH;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.tools.ShadowMatch#setMatchingContext(org.aspectj.weaver.tools.MatchingContext)
     */
    public void setMatchingContext(MatchingContext aMatchContext) {
        this.matchContext = aMatchContext;
    }

    private PointcutParameter[] getPointcutParameters(Object thisObject, Object targetObject, Object[] args) {
        Var[] vars = state.vars;
        PointcutParameterImpl[] bindings = new PointcutParameterImpl[params.length];
        for (int i = 0; i < bindings.length; i++) {
            bindings[i] = new PointcutParameterImpl(params[i].getName(), params[i].getType());
            bindings[i].setBinding(((VarImpl) vars[i]).getBindingAtJoinPoint(thisObject, targetObject, args, subject,
                    withinCode, withinType));
        }
        return bindings;
    }


    @Override
    public List<ParameterBinding> getParameterBindings() {
        return this.parameterBindings;
    }


    private static class RuntimeTestEvaluator implements ITestVisitor {

        private boolean matches = true;
        private final Test test;
        private final Object thisObject;
        private final Object targetObject;
        private final Object[] args;
        private final MatchingContext matchContext;

        public RuntimeTestEvaluator(Test aTest, Object thisObject, Object targetObject, Object[] args, MatchingContext context) {
            this.test = aTest;
            this.thisObject = thisObject;
            this.targetObject = targetObject;
            this.args = args;
            this.matchContext = context;
        }

        public boolean matches() {
            test.accept(this);
            return matches;
        }

        public void visit(And e) {
            boolean leftMatches = new RuntimeTestEvaluator(e.getLeft(), thisObject, targetObject, args, matchContext).matches();
            if (!leftMatches) {
                matches = false;
            } else {
                matches = new RuntimeTestEvaluator(e.getRight(), thisObject, targetObject, args, matchContext).matches();
            }
        }

        public void visit(Instanceof instanceofTest) {
            VarImpl v = (VarImpl) instanceofTest.getVar();
            Object value = v.getBindingAtJoinPoint(thisObject, targetObject, args);
            World world = v.getType().getWorld();
            ResolvedType desiredType = instanceofTest.getType().resolve(world);
            if (value == null) {
                matches = false;
            } else {
                ResolvedType actualType = world.resolve(value.getClass().getName());
                matches = desiredType.isAssignableFrom(actualType);
            }
        }

        public void visit(MatchingContextBasedTest matchingContextTest) {
            matches = matchingContextTest.matches(this.matchContext);
        }

        public void visit(Not not) {
            matches = !new RuntimeTestEvaluator(not.getBody(), thisObject, targetObject, args, matchContext).matches();
        }

        public void visit(Or or) {
            boolean leftMatches = new RuntimeTestEvaluator(or.getLeft(), thisObject, targetObject, args, matchContext).matches();
            if (leftMatches) {
                matches = true;
            } else {
                matches = new RuntimeTestEvaluator(or.getRight(), thisObject, targetObject, args, matchContext).matches();
            }
        }

        public void visit(Literal literal) {
            if (literal == Literal.FALSE) {
                matches = false;
            } else {
                matches = true;
            }
        }

        public void visit(Call call) {
            throw new UnsupportedOperationException("Can't evaluate call test at runtime");
        }

        public void visit(FieldGetCall fieldGetCall) {
            throw new UnsupportedOperationException("Can't evaluate fieldGetCall test at runtime");
        }

        public void visit(HasAnnotation hasAnnotation) {
            VarImpl v = (VarImpl) hasAnnotation.getVar();
            Object value = v.getBindingAtJoinPoint(thisObject, targetObject, args);
            World world = v.getType().getWorld();
            ResolvedType actualVarType = world.resolve(value.getClass().getName());
            ResolvedType requiredAnnotationType = hasAnnotation.getAnnotationType().resolve(world);
            matches = actualVarType.hasAnnotation(requiredAnnotationType);
        }

    }
}
