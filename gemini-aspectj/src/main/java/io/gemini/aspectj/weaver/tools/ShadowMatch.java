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
package io.gemini.aspectj.weaver.tools;

import java.util.List;

import org.aspectj.weaver.tools.MatchingContext;

import io.gemini.aspectj.weaver.ParameterBinding;

/**
 * The result of asking a PointcutExpression to match at a shadow (method execution,
 * handler, constructor call, and so on).
 *
 */
public interface ShadowMatch {

    /**
     * True iff the pointcut expression will match any join point at this
     * shadow (for example, any call to the given method).
     */
    boolean alwaysMatches();

    /**
     * True if the pointcut expression may match some join points at this
     * shadow (for example, some calls to the given method may match, depending
     * on the type of the caller).
     * <p>If alwaysMatches is true, then maybeMatches is always true.</p>
     */
    boolean maybeMatches();

    /**
     * True iff the pointcut expression can never match any join point at this
     * shadow (for example, the pointcut will never match a call to the given
     * method).
     */
    boolean neverMatches();

    /**
     * Return the result of matching a join point at this shadow with the given
     * this, target, and args.
     * @param thisObject  the object bound to this at the join point
     * @param targetObject the object bound to target at the join point
     * @param args the arguments at the join point
     * @return
     */
    JoinPointMatch matchesJoinPoint(Object thisObject, Object targetObject, Object[] args);

    /**
     * Set a matching context to be used when matching
     * join points.
     * @see MatchingContext
     */
    void setMatchingContext(MatchingContext aMatchContext);

    /**
     * Get advice parameter binding 
     * @return
     */
    List<ParameterBinding> getParameterBindings();
}
