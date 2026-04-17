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
/**
 * 
 */
package io.gemini.api.aop.condition;

import io.gemini.api.aop.MatchingContext;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Condition implementation for {@link io.gemini.api.aop.annotation.ConditionalOnType}.
 * Evaluates whether the specified type expression is present in the target class loader's classpath.
 * Throws {@link MissingElementException} if the type is absent, causing the advisor to be skipped.
 *
 * @author   martin.liu
 */
public class OnTypeCondition implements ElementMatcher<MatchingContext> {

    private final String typeExpression;


    public OnTypeCondition(String typeExpression) {
        this.typeExpression = typeExpression;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(MatchingContext context) {
        if (context.hasMethod(typeExpression) == false)
            throw new MissingElementException("Missing type required by expression.", typeExpression);

        return true;
    }
}
