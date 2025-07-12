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
package io.gemini.aspectj.weaver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.ast.Test;
import org.aspectj.weaver.patterns.ExposedState;

import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import net.bytebuddy.description.type.TypeDescription;

/**
 * The result of asking a PointcutExpression to match at a shadow (method execution,
 * handler, constructor call, and so on).
 *
 */
public class ShadowMatch {

    private FuzzyBoolean match;
    private ExposedState state;
    private List<NamedPointcutParameter> parameterBindings;


    public ShadowMatch(FuzzyBoolean match, Test test, ExposedState state, Map<String, TypeDescription> params) {
        this.match = match;
        this.state = state;

        this.parameterBindings = params == null ? Collections.emptyList() : createParamterBindings(params);
    }

    private List<NamedPointcutParameter> createParamterBindings(Map<String, TypeDescription> params) {
        int i = 0;
        List<NamedPointcutParameter> parameterBindings = new ArrayList<>(params.size());
        for(Entry<String, TypeDescription> entry : params.entrySet()) {
            PointcutParameter var = (PointcutParameter) state.vars[i++];
            if(var == null)
                continue;

            parameterBindings.add( new PointcutParameter.Default(entry.getKey(), var) );

        }

        return parameterBindings;
    }

    /**
     * True iff the pointcut expression will match any join point at this
     * shadow (for example, any call to the given method).
     */
    public boolean alwaysMatches() {
        return match.alwaysTrue();
    }

    /**
     * True if the pointcut expression may match some join points at this
     * shadow (for example, some calls to the given method may match, depending
     * on the type of the caller).
     * <p>If alwaysMatches is true, then maybeMatches is always true.</p>
     */
    public boolean maybeMatches() {
        return match.maybeTrue();
    }

    /**
     * True iff the pointcut expression can never match any join point at this
     * shadow (for example, the pointcut will never match a call to the given
     * method).
     */
    public boolean neverMatches() {
        return match.alwaysFalse();
    }

    /**
     * Get pointcut parameters
     * @return
     */
    public List<NamedPointcutParameter> getPointcutParameters() {
        return this.parameterBindings;
    }
}
