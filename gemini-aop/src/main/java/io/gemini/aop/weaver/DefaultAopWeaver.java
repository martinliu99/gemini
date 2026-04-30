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
package io.gemini.aop.weaver;

import java.io.IOException;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AopMetrics.TypeMetrics;
import io.gemini.aop.factory.AdvisorFactory;
import io.gemini.aop.weaver.BootstrapDispatcher.Dispatcher;
import io.gemini.aop.weaver.Joinpoints.Descriptor;
import io.gemini.aop.weaver.support.PointcutAdvisorWeaver;
import io.gemini.api.aop.AopException;
import io.gemini.core.bootstrap.BootstrapClassConsumer;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.inline.MethodNameTransformer;
import net.bytebuddy.utility.JavaModule;

/**
 * Default implementation of {@link AopWeaver} that integrates with the ByteBuddy agent builder.
 * <p>
 * Implements {@link net.bytebuddy.agent.builder.AgentBuilder.RawMatcher} to filter which types
 * should be instrumented, and {@link net.bytebuddy.agent.builder.AgentBuilder.Transformer} to
 * apply {@link io.gemini.aop.weaver.support.PointcutAdvisorWeaver} instances to matched types.
 * Results are cached per class loader and type name in {@link TargetTypeCache} for future advisor 
 * lookups on re-transformation.
 * 
 * The inner {@link Diagnostic} subclass adds per-type diagnostic logging.
 * </p>
 *
 * @author   martin.liu
 */
@BootstrapClassConsumer
class DefaultAopWeaver implements AopWeaver, AgentBuilder.TypeStrategy, BootstrapDispatcher.Delegator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAopWeaver.class);


    private final AopContext aopContext;
    private final AdvisorFactory advisorFactory;
    private final WeaverContext weaverContext;

    private final ConcurrentMap<ClassLoader, ConcurrentMap<String /* typeName */, TargetTypeCache>> classLoaderTypeCache;
    private final List<WeavedCodeCallback> weavedCodeCallbacks;


    public DefaultAopWeaver(AopContext aopContext, AdvisorFactory advisorFactory, WeaverContext weaverContext) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating AopWeaver with '{}'.", weaverContext);


        this.aopContext = aopContext;
        this.advisorFactory = advisorFactory;
        this.weaverContext = weaverContext;


        // initialize properties
        this.classLoaderTypeCache = new ConcurrentReferenceHashMap<>();
        this.weavedCodeCallbacks = new CopyOnWriteArrayList<>();


        if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create AopWeaver.", (System.nanoTime() - startedAt) / 1e9);
    }


    protected AopContext getAopContext() {
        return aopContext;
    }

    /**
     * Creates and caches a {@link TargetTypeCache} for the given class loader and type name.
     *
     * @param targetClassLoader   the class loader loading the type
     * @param targetTypeName      the fully-qualified type name
     * @param loaded              {@code true} if the type is already loaded (retransformation)
     * @param targetMethodAdvisors map of method to matched advisors
     * @return the created or existing cache entry
     */
    protected TargetTypeCache createTargetTypeCache(ClassLoader targetClassLoader, 
            String targetTypeName, boolean loaded,
            Map<? extends MethodDescription, List<? extends Advisor>> targetMethodAdvisors) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(targetClassLoader);
        return this.classLoaderTypeCache
                .computeIfAbsent(
                        cacheKey, 
                        key -> new ConcurrentHashMap<>()
                )
                .computeIfAbsent( 
                        targetTypeName, 
                        key -> new TargetTypeCache(weaverContext.getAopContext(), targetTypeName, loaded, targetMethodAdvisors, weaverContext)
                )
        ;
    }

    /**
     * Retrieves the cached {@link TargetTypeCache} for the given class loader and type name.
     *
     * @param targetClassLoader the class loader loading the type
     * @param targetTypeName    the fully-qualified type name
     * @return the cached entry, or {@code null} if not yet cached
     */
    protected TargetTypeCache getTargetTypeCache(ClassLoader targetClassLoader, String targetTypeName) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(targetClassLoader);
        ConcurrentMap<String /* typeName */, TargetTypeCache> targetTypeCaches = this.classLoaderTypeCache.get(cacheKey);
        return targetTypeCaches == null ? null : targetTypeCaches.get(targetTypeName);
    }


    /** 
     * {@inheritDoc}
     */
    @Override
    public boolean matches(TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule, 
            Class<?> classBeingRedefined, ProtectionDomain targetProtectionDomain) {
        // 1.check cached result since bytebuddy will enter this method twice when class redefinition, or retransmission
        String targetTypeName = targetType.getTypeName();
        TargetTypeCache targetTypeCache = getTargetTypeCache(targetClassLoader, targetTypeName);
        if (targetTypeCache != null && targetTypeCache.isMatched() == true)
            return true;


        // 2.filter type by ClassLoaderMatcher
        long startedAt = System.nanoTime();

        boolean targetClassLoaderAccepted = weaverContext.acceptTargetClassLoader(targetClassLoader);
        TypeMetrics typeMetrics = aopContext.getAopMetrics().createTypeMetrics(
                targetClassLoaderAccepted ? targetClassLoader : AopMetrics.REJECTED_CLASS_LOADER, targetTypeName, startedAt);

        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
        try {
            if (targetClassLoaderAccepted == false)
                return false;

            ThreadContext.setContextClassLoader(targetClassLoader);   // set targetClassLoader

            // 3.get advisors
            Map<? extends MethodDescription, List<? extends Advisor>> targetMethodAdvisors = 
                    this.advisorFactory.getAdvisors(targetType, targetClassLoader, targetModule);
            if (CollectionUtils.isEmpty(targetMethodAdvisors)) 
                return false;

            createTargetTypeCache(targetClassLoader, 
                    targetType.getTypeName(), classBeingRedefined != null, targetMethodAdvisors);
            return true;
        } catch (Throwable t) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Could not match type '{}' loaded by ClassLoader '{}' in AopWeaver.", 
                        targetTypeName, targetClassLoader, t);

            Throwables.throwIfRequired(t);
            return false;
        } finally {
            typeMetrics.incrTypeWeavingTime(System.nanoTime() - startedAt);
            getAopContext().getAopMetrics().collect(typeMetrics);

            ThreadContext.setContextClassLoader(existingClassLoader);
        }
    }


    /** 
     * {@inheritDoc}
     */
    @Override
    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription targetType, ClassLoader targetClassLoader, 
            JavaModule targetModule, ProtectionDomain targetProtectionDomain) {
        // 1.check if cached advisors exists
        String targetTypeName = targetType.getTypeName();
        TargetTypeCache targetTypeCache = getTargetTypeCache(targetClassLoader, targetTypeName);
        if (targetTypeCache != null && targetTypeCache.isMatched() == false)
            return builder;


        // 2.transform type
        long startedAt = System.nanoTime();
        TypeMetrics typeMetrics = aopContext.getAopMetrics().createTypeMetrics(
                targetClassLoader, targetTypeName, startedAt);

        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
        try {
            ThreadContext.setContextClassLoader(targetClassLoader);   // set targetClassLoader

            for (Entry<String /* methodSignature */, PointcutAdvisorWeaver> entry : targetTypeCache.getAdvisorWeavers().entrySet()) {
                builder = entry.getValue().weave(builder, targetTypeCache.isLoaded());
            }

            if (true == targetTypeCache.setTransformed(true)) {
                LOGGER.error("Reweaved type '{}' loaded by ClassLoader '{}' since it was already transformed!\n", targetTypeName, targetClassLoader);
            }

            return builder;
        } finally {
            long time = System.nanoTime() - startedAt;
            typeMetrics.incrTypeTransformationTime(time);
            typeMetrics.incrTypeWeavingTime(time);

            getAopContext().getAopMetrics().collect(typeMetrics);

            ThreadContext.setContextClassLoader(existingClassLoader);
        }
    }


    /**
     * {@inheritDoc}
     * Selects proper TypeStrategy based on class file format change.
     */
    @Override
    public DynamicType.Builder<?> builder(TypeDescription targetType, ByteBuddy byteBuddy, ClassFileLocator classFileLocator,
            MethodNameTransformer methodNameTransformer, ClassLoader targetClassLoader, JavaModule module,
            ProtectionDomain protectionDomain) {
        String targetTypeName = targetType.getTypeName();
        TargetTypeCache targetTypeCache = getTargetTypeCache(targetClassLoader, targetTypeName);

        // better performance than REDEFINE or REBASE
        TypeStrategy typeStrategy = TypeStrategy.Default.DECORATE;
        if (targetTypeCache != null && targetTypeCache.shouldChangeClassFile())
            typeStrategy = TypeStrategy.Default.REBASE;

        return typeStrategy.builder(targetType, byteBuddy, classFileLocator, 
                methodNameTransformer, targetClassLoader, module, protectionDomain);
    }


    /** 
     * {@inheritDoc}
     */
    @Override
    public WeaverContext getWeaverContext() {
        return weaverContext;
    }

    /** 
     * {@inheritDoc}
     */
    @Override
    public int registerCallback(WeavedCodeCallback weavedCodeCallback) {
        int startPos = this.weavedCodeCallbacks.size();
        this.weavedCodeCallbacks.add(weavedCodeCallback);
        for (int i = startPos; i< this.weavedCodeCallbacks.size(); i++) {
            if (this.weavedCodeCallbacks.get(i) == weavedCodeCallback)
                return i;
        }

        // throw exception
        throw new AopException("Could not register WeavedCodeCallback.");
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public <T, E extends Throwable> Dispatcher<T, E> createDispacther(Object descriptor, 
            Object targetObject, Object[] arguments) {
        return descriptor == null
                ? null
                : new Joinpoints.MutableJoinpointDispatcher<>( (Descriptor) descriptor, targetObject, arguments, aopContext );
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Object callback(Lookup targetLookup, String methodName, MethodType methodType, Object... arguments) {
        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
        try {
            Class<?> targetType = targetLookup.lookupClass();
            ClassLoader targetClassLoader = targetType.getClassLoader();
            ThreadContext.setContextClassLoader(targetClassLoader);   // set targetClassLoader

            try {
                int callbaclSlot = (int) arguments[0];
                return weavedCodeCallbacks.get(callbaclSlot).callback(targetLookup, methodName, methodType, arguments);
            } catch(Exception e) {
                if (LOGGER.isWarnEnabled()) 
                    LOGGER.warn("Could not weave target type. \n"
                            + "  ClassLoader: {}"
                            + "  TargetType: {}"
                            + "  CallbaclArgument: {}"
                            + "  Error reason: {} \n",
                            targetClassLoader,
                            targetType,
                            arguments,
                            e.getMessage(),
                            e
                    );

                return null;
            }
        } finally {
            ThreadContext.setContextClassLoader(existingClassLoader);
        }
    }


    /** 
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        this.classLoaderTypeCache.clear();
    }


    /**
     * Caches per-type weaving state for a given class loader and type name.
     * Holds the map of method signatures to {@link PointcutAdvisorWeaver} instances
     * and tracks whether the type has already been transformed.
     */
    class TargetTypeCache {

        private final AopContext aopContext;

        private final String typeName;
        private final boolean loaded;

        // cache transformation result per ClassLoader and Type in local storage
        private final AtomicBoolean transformed;

        private final boolean shouldChangeClassFile;
        private final Map<String /* methodSignature */, PointcutAdvisorWeaver> advisorWeavers;


        public TargetTypeCache(AopContext aopContext, String typeName, boolean loaded,
                Map<? extends MethodDescription, List<? extends Advisor>> targetMethodAdvisors,
                        WeaverContext weaverContext) {
            this.aopContext = aopContext;

            this.typeName = typeName;
            this.loaded = loaded;

            this.transformed = new AtomicBoolean(false);

            // create advisorWeavers
            this.advisorWeavers = new LinkedHashMap<>(targetMethodAdvisors.size());
            boolean shouldChangeClassFile = false;
            for (Entry<? extends MethodDescription, List<? extends Advisor>> entry : targetMethodAdvisors.entrySet()) {
                MethodDescription targetMethod = entry.getKey();
                if (targetMethod.isNative())
                    shouldChangeClassFile = true;

                this.advisorWeavers.put(targetMethod.toGenericString(), 
                        new PointcutAdvisorWeaver.Compound(DefaultAopWeaver.this, loaded, entry.getKey(), entry.getValue()) );
            }
            this.shouldChangeClassFile = shouldChangeClassFile;
        }

        protected AopContext getAopContext() {
            return aopContext;
        }


        public String getTypeName() {
            return typeName;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public Map<String /* methodSignature */, PointcutAdvisorWeaver> getAdvisorWeavers() {
            return this.advisorWeavers;
        }

        public boolean shouldChangeClassFile() {
            return shouldChangeClassFile;
        }

        public PointcutAdvisorWeaver getAdvisorWeaver(String targetMethodSignature) {
            return this.advisorWeavers.get(targetMethodSignature);
        }

        public boolean isMatched() {
            return advisorWeavers.size() > 0;
        }

        public boolean isTransformed() {
            return transformed.get();
        }

        public boolean setTransformed(boolean transformed) {
            return this.transformed.getAndSet(transformed);
        }

        @Override
        public String toString() {
            return typeName;
        }
    }


    /**
     * Extends {@link DefaultAopWeaver} to add per-type diagnostic logging for
     * {@link #matches} and {@link #transform} calls, and uses
     * {@link io.gemini.aop.weaver.Joinpoints.MutableJoinpointDispatcher.Diagnostic}
     * for enhanced joinpoint dispatch logging.
     */
    @BootstrapClassConsumer
    static class Diagnostic extends DefaultAopWeaver {

        private static final Logger LOGGER = LoggerFactory.getLogger(Diagnostic.class);


        public Diagnostic(AopContext aopContext, AdvisorFactory advisorFactory, WeaverContext weaverContext) {
            super(aopContext, advisorFactory, weaverContext);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule,
                Class<?> classBeingRedefined, ProtectionDomain targetProtectionDomain) {
            // diagnostic log
            String targetTypeName = targetType.getTypeName();
            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                LOGGER.info("Matching type '{}' loaded by ClassLoader '{}' in AopWeaver.", targetTypeName, targetClassLoader);

            return super.matches(targetType, 
                    targetClassLoader, targetModule, 
                    classBeingRedefined, targetProtectionDomain);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription targetType, ClassLoader targetClassLoader, 
                JavaModule targetModule, ProtectionDomain targetProtectionDomain) {
            String targetTypeName = targetType.getTypeName();
            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                LOGGER.info("Transforming type '{}' loaded by ClassLoader '{}' in AopWeaver.", targetTypeName, targetClassLoader);

            return super.transform(builder, targetType, 
                    targetClassLoader, targetModule, targetProtectionDomain);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public <T, E extends Throwable> Dispatcher<T, E> createDispacther(Object descriptor, 
                Object targetObject, Object[] arguments) {
            return descriptor == null
                    ? null
                    : new Joinpoints.MutableJoinpointDispatcher.Diagnostic<>(
                            (Descriptor) descriptor, targetObject, arguments, getAopContext() );
        }
    }
}