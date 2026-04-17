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
package io.gemini.aspectj.weaver.world;


import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.ast.Var;

import io.gemini.aspectj.weaver.PointcutParameter;


/**
 * A variable at a ByteBuddy shadow, used by the AspectJ residual tests during pointcut matching.
 * <p>
 * Implements {@link PointcutParameter} to carry the parameter category and optional
 * argument index needed for parameter binding in AspectJ advice.
 * </p>
 *
 * @author   martin.liu
 */
class InternalVar extends Var implements PointcutParameter {

    private final ParamCategory paramCategory;
    private final int argsIndex;


    /**
     * Creates an {@code InternalVar} with no argument index ({@link PointcutParameter#INVALID_ARGS_INDEX}).
     *
     * @param type          the resolved type of this variable
     * @param paramCategory the parameter category indicating the variable's role
     */
    public InternalVar(ResolvedType type, ParamCategory paramCategory) {
        this(type, paramCategory, INVALID_ARGS_INDEX);
    }

    /**
     * Creates an {@code InternalVar} with an explicit argument index.
     *
     * @param type          the resolved type of this variable
     * @param paramCategory the parameter category indicating the variable's role
     * @param argsIndex     the index of this variable in the method's argument list
     */
    public InternalVar(ResolvedType type, ParamCategory paramCategory, int argsIndex) {
        super(type);

        this.paramCategory = paramCategory;
        this.argsIndex = argsIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParamCategory getParamCategory() {
        return paramCategory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getArgsIndex() {
        return argsIndex;
    }
}
