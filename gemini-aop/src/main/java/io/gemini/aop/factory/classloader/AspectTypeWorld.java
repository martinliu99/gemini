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
package io.gemini.aop.factory.classloader;

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;

import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.aspectj.weaver.TypeWorldFactory;
import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.description.type.TypeDescription;


/**
 * An AspectJ {@link TypeWorld} scoped to an aspect application.
 * <p>
 * Resolves types by first consulting the target class loader's type world,
 * then falling back to the aspect class loader's own classpath. Primitive types
 * and the aspect class itself are always resolved from the aspect classpath.
 * </p>
 *
 * @author   martin.liu
 */
public class AspectTypeWorld extends BytebuddyWorld {

    private final AspectClassLoader aspectClassLoader;
    private final TypeWorldFactory typeWorldFactory;


    public AspectTypeWorld(AspectTypePool typePool, PlaceholderHelper placeholderHelper, 
            AspectClassLoader aspectClassLoader, TypeWorldFactory typeWordlFactory) {
        super(typePool, placeholderHelper);

        this.aspectClassLoader = aspectClassLoader;
        this.typeWorldFactory = typeWordlFactory;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedType resolve(UnresolvedType unresolvedType, boolean allowMissing) {
        String aspectType = ThreadContext.getContextAspectType();
        boolean isAspectType = (aspectType != null && aspectType.equals(unresolvedType.getName()))
                || unresolvedType.isPrimitiveType();

        // 1.resolve target type firstly
        if (!isAspectType) {
            ClassLoader targetCL = aspectClassLoader != null 
                    ? aspectClassLoader.getTargetClassLoader() : null;

            try {
                TypeWorld targetTypeWorld = typeWorldFactory.createTypeWorld(targetCL, null);
                ResolvedType resolvedType = targetTypeWorld == null ? null : targetTypeWorld.getWorld().resolve(unresolvedType, allowMissing);
                if (resolvedType != null && resolvedType.isMissing() == false)
                    return resolvedType;
            } catch (Exception ignored) { /* do nothing */ }
        }

        // 2.resolve aspect type
        return super.resolve(unresolvedType, allowMissing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeDescription describeType(String typeName) {
        if (OBJECT_DESCRIPTION.getTypeName().equals(typeName)) 
            return OBJECT_DESCRIPTION;

        return ((AspectTypePool) typePool).describeAspectType(typeName).resolve();
    }
}
