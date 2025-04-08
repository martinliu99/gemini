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


import org.aspectj.weaver.patterns.Pointcut;

import io.gemini.aspectj.weaver.tools.PointcutParameter;
import io.gemini.aspectj.weaver.tools.PointcutParser;
import net.bytebuddy.description.type.TypeDescription;

public class InternalUseOnlyPointcutParser extends PointcutParser {

    public InternalUseOnlyPointcutParser(TypeWorld typeWorld) {
        super(typeWorld);
    }

    public Pointcut resolvePointcutExpression(
                String expression, 
                TypeDescription inScope,
                PointcutParameter[] formalParameters) {
        return super.resolvePointcutExpression(expression, inScope, formalParameters);
    }
    
    public Pointcut concretizePointcutExpression(Pointcut pc, TypeDescription inScope, PointcutParameter[] formalParameters) {
        return super.concretizePointcutExpression(pc, inScope, formalParameters);
    }
}