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
package io.gemini.aspectj.weaver.world;


import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.ast.Var;

import io.gemini.aspectj.weaver.PointcutParameter;


/**
 * A variable at a bytebuddy shadow, used by the residual tests.
 */
class InternalVar extends Var implements PointcutParameter {

    private final ParamCategory paramCategory;
    private final int argsIndex;


    public InternalVar(ResolvedType type, ParamCategory paramCategory) {
        this(type, paramCategory, INVALID_ARGS_INDEX);
    }

    public InternalVar(ResolvedType type, ParamCategory paramCategory, int argsIndex) {
        super(type);

        this.paramCategory = paramCategory;
        this.argsIndex = argsIndex;
    }

    /**
     * {@inheritDoc}
     */
    public ParamCategory getParamCategory() {
        return paramCategory;
    }

    /**
     * {@inheritDoc}
     */
    public int getArgsIndex() {
        return argsIndex;
    }
}
