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
package io.gemini.core.pool;

import io.gemini.core.util.Assert;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

public class AspectTypePool extends TypePool.AbstractBase {

    private final TypePool aspectTypePool;
    private final TypePool joinpointTypePool;

    private ElementMatcher<String> joinpointTypeMatcher = ElementMatchers.none();


    public AspectTypePool(CacheProvider cacheProvider, TypePool aspectTypePool, TypePool joinpointTypePool) {
        super(cacheProvider);

        Assert.notNull(aspectTypePool, "'aspectTypePool' must not be null.");
        this.aspectTypePool = aspectTypePool;

        Assert.notNull(joinpointTypePool, "'joinpointTypePool' must not be null.");
        this.joinpointTypePool = joinpointTypePool;
    }

    public void setJoinpointTypeMatcher(ElementMatcher<String> joinpointTypeMatcher) {
        this.joinpointTypeMatcher = joinpointTypeMatcher;
    }


    @Override
    protected Resolution doDescribe(String name) {
        if(joinpointTypeMatcher.matches(name) == false) {
            Resolution resolution = aspectTypePool.describe(name);
            if(resolution.isResolved()) {
                return resolution;
            }

            return joinpointTypePool.describe(name);
        } else {
            Resolution resolution = joinpointTypePool.describe(name);
            if(resolution.isResolved()) {
                return resolution;
            }

            return aspectTypePool.describe(name);
        }
    }


    public void clear() {
        aspectTypePool.clear();
        joinpointTypePool.clear();
    }
}
