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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.AbstractBase;


public class ExplicitTypePool extends AbstractBase {

    private final TypePool parent;

    private final ConcurrentMap<String /* Type Name */, Resolution> typeResolutions = new ConcurrentHashMap<>();


    public ExplicitTypePool(TypePool parent) {
        super(CacheProvider.NoOp.INSTANCE);

        this.parent = parent == null ? TypePool.Empty.INSTANCE : parent;
    }

    public void addTypeDescription(TypeDescription typeDescription) {
        if(typeDescription == null) return;

        this.typeResolutions.put(typeDescription.getTypeName(), new Resolution.Simple(typeDescription));
    }

    public void removeTypeDescription(String typeName) {
        this.typeResolutions.remove(typeName);
    }

    @Override
    public Resolution describe(String name) {
        Resolution resolution = super.describe(name);
        return resolution != null && resolution.isResolved()
                ? resolution
                : parent.describe(name);
    }

    @Override
    protected Resolution doDescribe(String name) {
        return this.typeResolutions.get(name);
    }

    @Override
    public void clear() {
        try {
            parent.clear();
        } finally {
            super.clear();
            this.typeResolutions.clear();
        }
    }
}
