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

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.patterns.TypePattern;

import io.gemini.aspectj.weaver.tools.TypePatternExpression;
import io.gemini.aspectj.weaver.world.TypeWorld;
import io.gemini.aspectj.weaver.world.internal.ReferenceTypeDelegateFactoryImpl;
import net.bytebuddy.description.type.TypeDescription;

public class TypePatternExpressionImpl implements TypePatternExpression {

    private final TypePattern pattern;
    private final TypeWorld typeWorld;

    public TypePatternExpressionImpl(TypePattern pattern, TypeWorld typeWorld) {
        this.pattern = pattern;
        this.typeWorld = typeWorld;
    }

    public boolean matches(TypeDescription typeDescription) {
        ResolvedType rt = 
                ReferenceTypeDelegateFactoryImpl.resolveTypeInWorld(typeDescription, typeWorld);
        return pattern.matchesStatically(rt);
    }
}
