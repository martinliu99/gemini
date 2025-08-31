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
package io.gemini.aop.factory.classloader;

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;

import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.aspectj.weaver.TypeWorldFactory;
import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.description.type.TypeDescription;

/**
 * 
 */
public class AspectTypeWorld extends BytebuddyWorld {

    private final AspectClassLoader aspectClassLoader;
    private final TypeWorldFactory typeWordlFactory;


    public AspectTypeWorld(AspectTypePool typePool, PlaceholderHelper placeholderHelper, AspectClassLoader aspectClassLoader, TypeWorldFactory typeWordlFactory) {
        super(typePool, placeholderHelper);

        this.aspectClassLoader = aspectClassLoader;
        this.typeWordlFactory = typeWordlFactory;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedType resolve(UnresolvedType ty, boolean allowMissing) {
        try {
            ResolvedType resolvedType = super.resolve(ty, allowMissing);
            if(resolvedType != null && resolvedType.isMissing() == false)
                return resolvedType;
        } catch(Exception e) {}

        TypeWorld joinpointTypeWorld = getJoinpointTypeWorld();
        return joinpointTypeWorld == null ? null : joinpointTypeWorld.getWorld().resolve(ty, allowMissing);
    }

    private TypeWorld getJoinpointTypeWorld() {
        ClassLoader joinpointCL = aspectClassLoader.getJoinpointClassLoader();
        if(joinpointCL == null)
            return null;

        return typeWordlFactory.createTypeWorld(joinpointCL, null);
    }

    @Override
    protected TypeDescription describeType(String typeName) {
        if("java.lang.Object".equals(typeName)) return OBJECT_DESCRIPTION;

        return ((AspectTypePool) typePool).describeAspect(typeName).resolve();
    }
}
