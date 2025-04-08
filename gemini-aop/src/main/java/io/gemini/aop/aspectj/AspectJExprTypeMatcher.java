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
package io.gemini.aop.aspectj;

import io.gemini.aop.matcher.TypeMatcher;
import io.gemini.aspectj.weaver.tools.PointcutParser;
import io.gemini.aspectj.weaver.tools.TypePatternExpression;
import io.gemini.aspectj.weaver.world.TypeWorld;
import io.gemini.core.util.Assert;
import net.bytebuddy.description.type.TypeDescription;

public class AspectJExprTypeMatcher extends TypeMatcher {

    private final String expression;
    private final TypePatternExpression typePatternExpression;


    public AspectJExprTypeMatcher(TypeWorld typeWorld, String expression) {
        Assert.notNull(typeWorld, "'typeWorld' must not be null");
        Assert.hasText(expression, "'expression' must not be empty");

        PointcutParser parser = PointcutParser
                .getPointcutParserSupportingAllPrimitivesAndUsingSpecifiedClassloaderForResolution(typeWorld);

        this.typePatternExpression = parser.parseTypePatternExpression(expression);
        this.expression = expression;
    }

    @Override
    protected boolean doMatch(TypeDescription typeDescription) {
        try {
            return this.typePatternExpression.matches(typeDescription);
        } catch(Throwable t) {
            return false;
        }
    }

    @Override
    public String toString() {
        return this.expression;
    }
}
