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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.gemini.core.util.Assert;
import net.bytebuddy.pool.TypePool;

public class CompoundTypePool implements TypePool {

    private List<TypePool> typePools;

    public CompoundTypePool(TypePool... typePools) {
        Assert.isTrue(typePools != null && typePools.length > 0, "'typePools' must not be empty.");

        this.typePools = Arrays.asList(typePools);
    }

    public CompoundTypePool(TypePool typePool, Collection<TypePool> typePools) {
        Assert.notNull(typePool, "'typePool' must not be null.");
        Assert.notEmpty(typePools, "'typePools' must not be empty.");

        List<TypePool> pools = new ArrayList<>(1 + typePools.size());
        pools.add(typePool);
        pools.addAll(typePools);
        this.typePools = pools;
    }

    @Override
    public Resolution describe(String name) {
        for(TypePool typePool : this.typePools) {
            Resolution resolution = typePool.describe(name);
            if(resolution != null && resolution.isResolved() == true)
                return resolution;
        }

        return new Resolution.Illegal(name);
    }

    @Override
    public void clear() {
        for(TypePool typePool : this.typePools) {
            typePool.clear();
        }
    }
}
