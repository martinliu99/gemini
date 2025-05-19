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

import io.gemini.aspectj.weaver.tools.JoinPointMatch;
import io.gemini.aspectj.weaver.tools.PointcutParameter;

/**
 * @author colyer
 * Implementation of JoinPointMatch for description based worlds.
 */
public class JoinPointMatchImpl implements JoinPointMatch {

    public static final JoinPointMatch NO_MATCH = new JoinPointMatchImpl();
    private static final PointcutParameter[] NO_BINDINGS = new PointcutParameter[0];

    private boolean match;
    private PointcutParameter[] bindings;


    public JoinPointMatchImpl(PointcutParameter[] bindings) {
        this.match = true;
        this.bindings = bindings;
    }

    private JoinPointMatchImpl() {
        this.match = false;
        this.bindings = NO_BINDINGS;
    }

    /**
     * {@inheritDoc}
     */
    public boolean matches() {
        return match;
    }

    /**
     * {@inheritDoc}
     */
    public PointcutParameter[] getParameterBindings() {
        return bindings;
    }

}
