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
package io.gemini.aspectj.weaver;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.World;

import io.gemini.api.BaseException;
import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;

/**
 * Bridges ByteBuddy type descriptions and AspectJ's type resolution model.
 * <p>
 * Provides methods to resolve {@link org.aspectj.weaver.ResolvedType} from ByteBuddy
 * {@link TypeDescription} instances, create AspectJ {@link Shadow} objects for method
 * matching, and describe types by name. 
 * Inner classes provide lazy, cached, and local-scoped facades over the core world.
 * </p>
 *
 * @author   martin.liu
 */
public interface TypeWorld {

    TypeWorld EMPTY_WORLD = new TypeWorld.LazyFacade( new BytebuddyWorld(TypePool.Empty.INSTANCE, null) );


    /**
     * Returns the underlying AspectJ {@link World} used for type resolution.
     *
     * @return the AspectJ world; never {@code null}
     */
    World getWorld();

    /**
     * Resolves a type by its fully-qualified name.
     *
     * @param typeName the fully-qualified type name
     * @return the resolved type; never {@code null} (may be {@link ResolvedType#MISSING})
     */
    ResolvedType resolve(String typeName);

    /**
     * Resolves a ByteBuddy {@link TypeDescription} to an AspectJ {@link ResolvedType}.
     *
     * @param typeDescription the type description to resolve
     * @return the resolved type, or {@code null} if {@code typeDescription} is {@code null}
     */
    ResolvedType resolve(TypeDescription typeDescription);

    /**
     * Returns a ByteBuddy {@link TypeDescription} for the given fully-qualified type name.
     *
     * @param typeName the fully-qualified type name
     * @return the type description; never {@code null}
     */
    TypeDescription describeType(String typeName);


    /**
     * Resolves a ByteBuddy {@link Member} (method or field) to an AspectJ {@link ResolvedMember}.
     *
     * @param member the member to resolve
     * @return the resolved member; never {@code null}
     */
    ResolvedMember resolve(Member member);

    /**
     * Creates an AspectJ {@link Shadow} for the given ByteBuddy {@link Member},
     * representing the execution joinpoint for pointcut matching.
     *
     * @param member the method or field member
     * @return the shadow, or {@code null} if {@code member} is {@code null}
     */
    Shadow makeShadow(Member member);


    /**
     * Thrown when the AspectJ world's message handler receives a non-info message,
     * indicating a lint or error condition during type resolution or pointcut parsing.
     */
    class WorldLintException extends BaseException {

        private static final long serialVersionUID = 816600136638029684L;

        /**
         * Creates a new {@code WorldLintException} with the given message.
         *
         * @param message the lint or error message from the AspectJ world
         */
        public WorldLintException(String message) {
            super(message);
        }
    }


    /**
     * Abstract base that forwards all {@link TypeWorld} operations to a delegate instance.
     * Subclasses override individual methods to add caching, lazy resolution, or other behaviour
     * while delegating the rest.
     */
    abstract class WithDelegation implements TypeWorld {

        private final TypeWorld delegate;


        /**
         * Creates a {@code WithDelegation} that forwards calls to the given delegate.
         *
         * @param typeWorld the delegate {@link TypeWorld}; must not be {@code null}
         */
        public WithDelegation(TypeWorld typeWorld) {
            this.delegate = typeWorld;
        }

        /**
         * Returns the delegate {@link TypeWorld} that this instance forwards calls to.
         *
         * @return the delegate; never {@code null}
         */
        protected TypeWorld getDelegate() {
            return this.delegate;
        }


        /** 
         * {@inheritDoc}
         */
        @Override
        public World getWorld() {
            return getDelegate().getWorld();
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public ResolvedType resolve(String typeName) {
            return getDelegate().resolve(typeName);
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public ResolvedType resolve(TypeDescription typeDescription) {
            return getDelegate().resolve(typeDescription);
        }

        /**
         * {@inheritDoc}
         */
        public TypeDescription describeType(String typeName) {
            return getDelegate().describeType(typeName);
        }


        /**
         * {@inheritDoc}
         */
        public ResolvedMember resolve(Member member) {
            return getDelegate().resolve(member);
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        public Shadow makeShadow(Member member) {
            return getDelegate().makeShadow(member);
        }


        @Override
        public String toString() {
            return getClass().getSimpleName() + "-" + getDelegate().toString();
        }
    }


    /**
     * A {@link WithDelegation} that defers type resolution: instead of resolving a
     * {@link TypeDescription} or type name immediately, it first checks the world's
     * type map cache and, on a miss, returns a {@link ReferenceTypes.LazyFacade} that
     * postpones actual class loading until the type's metadata is first accessed.
     */
    public static class LazyFacade extends WithDelegation {

        /**
         * Creates a {@code LazyFacade} wrapping the given {@link TypeWorld}.
         *
         * @param typeWorld the delegate type world
         */
        public LazyFacade(TypeWorld typeWorld) {
            super(typeWorld);
        }

        /**
         * Resolves a {@link TypeDescription} lazily: returns a cached entry from the world's
         * type map if present, otherwise wraps it in a {@link ReferenceTypes.LazyFacade}
         * to defer actual resolution.
         *
         * @param typeDescription the type description to resolve
         * @return the resolved type or a lazy facade; {@code null} if {@code typeDescription} is {@code null}
         */
        public ResolvedType resolve(TypeDescription typeDescription) {
            if (typeDescription == null)
                return null;

            ResolvedType resolvedType = getDelegate().getWorld().getTypeMap().get(typeDescription.getTypeName());
            return resolvedType != null ? resolvedType : new ReferenceTypes.LazyFacade(typeDescription, this.getDelegate());
        }

        /** 
         * {@inheritDoc}
         */
        @Override
        public ResolvedType resolve(String typeName) {
            ResolvedType resolvedType = getDelegate().getWorld().getTypeMap().get(typeName);
            return resolvedType != null ? resolvedType : new ReferenceTypes.LazyFacade(typeName, this.getDelegate());
        }
    }


    /**
     * A {@link WithDelegation} that caches resolved {@link ResolvedType} and {@link Shadow}
     * instances per {@link TypeDescription}, avoiding redundant resolution work during
     * repeated pointcut evaluations against the same type.
     * Use {@link #releaseCache(TypeDefinition)} to evict a stale entry when a type is redefined.
     */
    public static class CacheResolutionFacade extends WithDelegation {

        private final ConcurrentMap<TypeDescription, Resolution> resolutionCache = new ConcurrentHashMap<>();


        /**
         * Creates a {@code CacheResolutionFacade} wrapping the given {@link TypeWorld}.
         *
         * @param typeWorld the delegate type world
         */
        public CacheResolutionFacade(TypeWorld typeWorld) {
            super(typeWorld);
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public ResolvedType resolve(TypeDescription typeDescription) {
            if (typeDescription == null) return null;

            return doResolve(typeDescription).resolvedType;
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        public Shadow makeShadow(Member member) {
            if (member == null) return null;

            return doResolve(member.getDeclaringType().asErasure()).shadowCache.computeIfAbsent(
                    member, 
                    key -> getDelegate().makeShadow(key)
            );
        }

        protected Resolution doResolve(TypeDescription typeDescription) {
            return resolutionCache.computeIfAbsent(
                    typeDescription, 
                    key -> new Resolution(
                            getDelegate().resolve(key) )
            );
        }


        /**
         * Evicts the cached resolution entry for the given type, forcing re-resolution
         * on the next access.
         *
         * @param typeDefinition the type whose cache entry should be removed; ignored if {@code null}
         */
        public void releaseCache(TypeDefinition typeDefinition) {
            if (typeDefinition == null) return;

            resolutionCache.remove(typeDefinition);
        }


        private static class Resolution {

            final ResolvedType resolvedType;
            ConcurrentMap<Member, Shadow> shadowCache;

            public Resolution(ResolvedType resolvedType) {
                this.resolvedType = resolvedType;
                this.shadowCache = new ConcurrentHashMap<>();
            }
        }
    }


    /**
     * A {@link WithDelegation} that pre-describes types through a local {@link TypePool}
     * before delegating resolution to the wrapped {@link TypeWorld}.
     * Useful when a scoped pool (e.g., for a specific class loader or module) must be
     * consulted first to ensure the type is visible before the global world resolves it.
     */
    public static class LocalWorld extends WithDelegation {

        private final TypePool typePool;

        /**
         * Creates a {@code LocalWorld} that resolves types from the given {@link TypePool}
         * before delegating to the wrapped {@link TypeWorld}.
         *
         * @param typeWorld the delegate type world
         * @param typePool  the local type pool used to pre-describe types before resolution
         */
        public LocalWorld(TypeWorld typeWorld, TypePool typePool) {
            super(typeWorld);
            this.typePool = typePool;
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public ResolvedType resolve(String typeName) {
            // TODO: check existence?
            typePool.describe(typeName).resolve();
            return getDelegate().resolve(typeName);
        }
    }
}
