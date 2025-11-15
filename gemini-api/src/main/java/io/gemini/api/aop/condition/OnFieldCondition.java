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
/**
 * 
 */
package io.gemini.api.aop.condition;

import io.gemini.api.annotation.Initializer;
import io.gemini.api.aop.MatchingContext;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 */
public class OnFieldCondition implements ElementMatcher<MatchingContext> {

    private final String fieldExpression;


    @Initializer
    public OnFieldCondition(String fieldExpression) {
        this.fieldExpression = fieldExpression;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(MatchingContext context) {
        if (context.hasFiled(fieldExpression) == false)
            throw new MissingElementException("Missing field required by expression.", fieldExpression);

        return true;
    }
}
