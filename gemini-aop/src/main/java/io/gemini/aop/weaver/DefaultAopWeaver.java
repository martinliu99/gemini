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

import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.AdvisorFactory;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AopMetrics.TypeMetrics;
import io.gemini.aop.AopWeaver;
import io.gemini.aop.weaver.BootstrapDispatcher.Dispatcher;
import io.gemini.aop.weaver.Joinpoints.Descriptor;
import io.gemini.aop.weaver.WeaverCache.TargetTypeCache;
import io.gemini.aop.weaver.advice.DescriptorOffset;
import io.gemini.api.classloader.BaseClassLoader;
import io.gemini.core.bootstrap.BootstrapClassConsumer;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.WithCustomMapping;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
@BootstrapClassConsumer
class DefaultAopWeaver implements AopWeaver, BootstrapDispatcher.Creator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAopWeaver.class);


    private final AopContext aopContext;
    private final AdvisorFactory advisorFactory;
    private final WeaverContext weaverContext;

    private final Map<ClassLoader, ElementMatcher<String>> typeMatcherPerClassLoaderMap;
    private final WeaverCache weaverCache;


    public DefaultAopWeaver(AopContext aopContext, AdvisorFactory advisorFactory, WeaverContext weaverContext) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating AopWeaver with '{}'.", weaverContext);


        this.aopContext = aopContext;
        this.advisorFactory = advisorFactory;
        this.weaverContext = weaverContext;

        // set bytebuddy setting to dump byte code
        if (aopContext.isByteCodeDumped()) {
            String byteCodeDumpPath = aopContext.getByteCodeDumpPath();
            File path = new File(byteCodeDumpPath + File.separator + "byte-buddy");
            path.mkdirs();

            System.getProperties().setProperty("net.bytebuddy.dump", path.getAbsolutePath());
        }


        // initialize properties
        this.typeMatcherPerClassLoaderMap = new ConcurrentReferenceHashMap<>();
        this.weaverCache = new WeaverCache(weaverContext);


        if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create AopWeaver.", (System.nanoTime() - startedAt) / 1e9);
    }


    @Override
    public boolean matches(TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule, 
            Class<?> classBeingRedefined, ProtectionDomain targetProtectionDomain) {
        // 1.check cached result since bytebuddy will enter this method twice when class redefinition, or retransmission
        String targetTypeName = targetType.getTypeName();
        TargetTypeCache targetTypeCache = weaverCache.getTargetTypeCache(targetClassLoader, targetTypeName);
        if (targetTypeCache != null && targetTypeCache.isMatched() == true)
            return true;


        // 2.filter type by ClassLoaderMatcher
        long startedAt = System.nanoTime();
        ElementMatcher<String> typeMatcher = this.typeMatcherPerClassLoaderMap.computeIfAbsent(
                ClassLoaderUtils.maskNull(targetClassLoader), 
                key -> doCreateTypeMatcher(targetClassLoader, targetTypeName)
        );

        boolean isRejectedClassLoader = ElementMatchers.none().equals(typeMatcher);
        TypeMetrics typeMetrics = aopContext.getAopMetrics().createTypeMetrics(
                isRejectedClassLoader ? AopMetrics.REJECTED_CLASS_LOADER : targetClassLoader, targetTypeName, startedAt);

        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
        try {
            if (isRejectedClassLoader)
                return false;

            ThreadContext.setContextClassLoader(targetClassLoader);   // set targetClassLoader

            // 3.filter type by TypeMatcher
            if (ElementMatchers.any().equals(typeMatcher) == false && doAcceptType(targetTypeName, typeMatcher) == false)
                return false;


            // 4.get advisors
            Map<? extends MethodDescription, List<? extends Advisor>> targetMethodAdvisors = 
                    this.advisorFactory.getAdvisors(targetType, targetClassLoader, targetModule);
            if (CollectionUtils.isEmpty(targetMethodAdvisors)) 
                return false;

            weaverCache.createTargetTypeCache(targetClassLoader, 
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

    protected ElementMatcher<String> doCreateTypeMatcher(ClassLoader targetClassLoader, String targetTypeName) {
        if (targetClassLoader instanceof BaseClassLoader)
            return ElementMatchers.none();

        if (this.weaverContext.getClassLoaderTypeMatchers().size() == 0)
            return ElementMatchers.any();

        List<ElementMatcher<? super String>> typeMatchers = new ArrayList<>();
        ElementMatcher<String> typeMatcher = null;
        for (Entry<ElementMatcher<ClassLoader>, ElementMatcher<String>> entry : this.weaverContext.getClassLoaderTypeMatchers().entrySet()) {
            if (entry.getKey().matches(targetClassLoader) == false)
                continue;

            typeMatcher = entry.getValue();
            typeMatchers.add(typeMatcher);
        }

        return typeMatchers.size() == 0
                ? ElementMatchers.none() 
                : typeMatchers.size() == 1
                        ? typeMatcher
                        : new ElementMatcher.Junction.Disjunction<>(typeMatchers);
    }

    protected boolean doAcceptType(String targetTypeName, ElementMatcher<String> typeMatcher) {
        return typeMatcher.matches(targetTypeName);
    }


    @Override
    public Builder<?> transform(Builder<?> builder, TypeDescription targetType, ClassLoader targetClassLoader, 
            JavaModule targetModule, ProtectionDomain targetProtectionDomain) {
        // 1.check if cached advisorChain exists
        String targetTypeName = targetType.getTypeName();
        TargetTypeCache targetTypeCache = weaverCache.getTargetTypeCache(targetClassLoader, targetTypeName);
        if (targetTypeCache.isMatched() == false)
            return builder;


        // 2.transform type
        long startedAt = System.nanoTime();
        TypeMetrics typeMetrics = aopContext.getAopMetrics().createTypeMetrics(
                targetClassLoader, targetTypeName, startedAt);

        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
        try {
            ThreadContext.setContextClassLoader(targetClassLoader);   // set targetClassLoader

            for (Entry<String, MethodDescription> entry : targetTypeCache.getMethodSignatureMap().entrySet()) {
                builder = this.transformMatchedMethods(builder, targetTypeCache, targetType, entry.getValue(), entry.getKey());
            }

            if (Boolean.TRUE == targetTypeCache.setTransformed(true)) {
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

    private Builder<?> transformMatchedMethods(Builder<?> builder, 
            TargetTypeCache targetTypeCache, TypeDescription targetType, 
            MethodDescription targetMethod, String targetMethodSignature) {
        WithCustomMapping withCustomMapping = Advice.withCustomMapping().bind( 
                DescriptorOffset.createDescriptorOffset(targetMethod, targetMethodSignature) );

        if (targetMethod.isStatic()) {
            if (targetMethod.isTypeInitializer()) {
                builder = builder.visit(
                        withCustomMapping
                        .to( this.weaverContext.getClassInitializerAdvice(targetType) )
                        .on( is(targetMethod) ) 
                );
            } else if (targetMethod.isNative()){
                if (targetTypeCache.isLoaded()) {
                    LOGGER.warn("Could not retransform type '{}' loaded by ClassLoader '{}' in AopWeaver, \n"
                            + "  NativeTargetMethod: {}",
                            targetType.getTypeName(),
                            targetMethodSignature);
                    return builder;
                }

                String renamedMethodName = weaverContext.getNativeMethodPrefix() + targetMethod.getName();
                // define renamed native method
                builder = builder.defineMethod( 
                        renamedMethodName, targetMethod.getReturnType(), targetMethod.getActualModifiers() )
                .withParameters(
                        targetMethod.getParameters().asTypeList())
                .withoutCode()
                .method( is(targetMethod) )
                // wrap original native method with Advice, and call renamed native method
                .intercept(
                        withCustomMapping
                        .to( this.weaverContext.getClassMethodAdvice(targetType) )
                        .wrap( MethodCall.invoke( named(renamedMethodName) ).withAllArguments() )
                );
            } else { 
                builder = builder.visit(
                        withCustomMapping
                        .to( this.weaverContext.getClassMethodAdvice(targetType) )
                        .on( is(targetMethod) ) 
                );
            }
        } else {
            if (targetMethod.isConstructor()) {
                builder = builder.visit(
                        withCustomMapping
                        .to( this.weaverContext.getInstanceConstructorAdvice(targetType) )
                        .on( is(targetMethod) ) 
                );  
            } else if (targetMethod.isMethod()) {
                builder = builder.visit(
                        withCustomMapping
                        .to( this.weaverContext.getInstanceMethodAdvice(targetType) )
                        .on( is(targetMethod) ) 
                );
            }
        }

        return builder;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Object createDescriptor(Lookup targetLookup, Object... arguments) {
        String targetMethodSignature = (String) arguments[0];
        return weaverCache.getJoinpointDescriptor(targetLookup, targetMethodSignature);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public CallSite createDescriptorCallSite(Lookup targetLookup, String bsmMethodName, 
            MethodType bsmMethodType, Object... arguments) {
        String targetMethodSignature = (String) arguments[0];
        Joinpoints.Descriptor descriptor = weaverCache.getJoinpointDescriptor(targetLookup, targetMethodSignature);

        MethodHandle constant = MethodHandles.constant(Object.class, descriptor);
        return new ConstantCallSite( constant );
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


    @Override
    public void close() throws IOException {
        this.weaverCache.close();
    }


    protected AopContext getAopContext() {
        return aopContext;
    }

    public WeaverContext getWeaverContext() {
        return weaverContext;
    }


    @BootstrapClassConsumer
    static class Diagnostic extends DefaultAopWeaver {

        private static final Logger LOGGER = LoggerFactory.getLogger(Diagnostic.class);


        public Diagnostic(AopContext aopContext, AdvisorFactory advisorFactory, WeaverContext weaverContext) {
            super(aopContext, advisorFactory, weaverContext);
        }


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

        @Override
        protected boolean doAcceptType(String targetTypeName, ElementMatcher<String> typeMatcher) {
            try {
                return super.doAcceptType(targetTypeName, typeMatcher);
            } finally {
                TypeMetrics typeMetrics = AopMetrics.currentTypeMetrics();
                typeMetrics.incrTypeAcceptingTime(System.nanoTime() - typeMetrics.getStartedAt());
            }
        }


        @Override
        public Builder<?> transform(Builder<?> builder, TypeDescription targetType, ClassLoader targetClassLoader, 
                JavaModule targetModule, ProtectionDomain targetProtectionDomain) {
            String targetTypeName = targetType.getTypeName();
            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                LOGGER.info("Transforming type '{}' loaded by ClassLoader '{}' in AopWeaver.", targetTypeName, targetClassLoader);

            return super.transform(builder, targetType, 
                    targetClassLoader, targetModule, targetProtectionDomain);
        }


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