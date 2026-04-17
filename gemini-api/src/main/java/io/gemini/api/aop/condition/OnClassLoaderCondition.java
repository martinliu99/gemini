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
 * Condition implementation for {@link io.gemini.api.aop.annotation.ConditionalOnClassLoader}.
 * Evaluates whether the target class loader matches the specified criteria
 * (bootstrap, ext, app, or a named expression).
 *
 * @author   martin.liu
 */
public class OnClassLoaderCondition implements ElementMatcher<MatchingContext> {

    private final String classLoaderExpression;

    private final boolean isBootstrapClassLoader;
    private final boolean isExtClassLoader;
    private final boolean isAppClassLoader;


    public OnClassLoaderCondition(String classLoaderExpression, 
            boolean isBootstrapClassLoader, boolean isExtClassLoader, boolean isAppClassLoader) {
        this.classLoaderExpression = classLoaderExpression == null 
                ? "" : classLoaderExpression.trim();

        this.isBootstrapClassLoader = isBootstrapClassLoader;
        this.isExtClassLoader = isExtClassLoader;
        this.isAppClassLoader = isAppClassLoader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(MatchingContext context) {
        if (isBootstrapClassLoader) {
            if (context.isBootstrapClassLoader())
                return true;
        } else if (isExtClassLoader) {
            if (context.isExtClassLoader())
                return true;
        } else if (isAppClassLoader) {
            if (context.isAppClassLoader())
                return true;
        } else if (!"".equals(classLoaderExpression)) {
            return context.isClassLoader(classLoaderExpression);
        }

        return false;
    }
}
