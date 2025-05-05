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
package io.gemini.aop.weaver;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics.WeaverMetrics;
import io.gemini.aop.Aspect;
import io.gemini.aop.AspectFactory;
import io.gemini.aop.AspectWeaver;
import io.gemini.aop.java.lang.BootstrapAdvice;
import io.gemini.aop.java.lang.BootstrapAdvice.Dispatcher;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
import io.gemini.aop.weaver.Joinpoints.Descriptor;
import io.gemini.aop.weaver.WeaverCache.TypeCache;
import io.gemini.aop.weaver.advice.DescriptorOffset;
import io.gemini.api.classloader.ThreadContext;
import io.gemini.core.pool.ExplicitTypePool;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CollectionUtils;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.builder.AgentBuilder.RawMatcher;
import net.bytebuddy.asm.Advice.WithCustomMapping;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
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
class DefaultAspectWeaver implements AspectWeaver, BootstrapAdvice.Factory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAspectWeaver.class);


    private final AopContext aopContext;
    private final AspectFactory aspectFactory;
    private final WeaverContext weaverContext;

    private RawMatcher ignoreMatcher;

    private WeaverCache weaverCache;


    public DefaultAspectWeaver(AopContext aopContext, AspectFactory aspectFactory, WeaverContext weaverContext) {
        this.aopContext = aopContext;
        this.aspectFactory = aspectFactory;
        this.weaverContext = weaverContext;

        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("^Creating AspectWeaver with '{}'.", weaverContext);
        }

        // set bytebuddy setting to dump byte code
        if(aopContext.isDumpByteCode()) {
            String byteCodeDumpPath = aopContext.getByteCodeDumpPath();
            File path = new File(byteCodeDumpPath + File.separator + "byte-buddy");
            path.mkdirs();

            System.getProperties().setProperty("net.bytebuddy.dump", path.getAbsolutePath());
        }

        this.initialize(weaverContext);

        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("$Took '{}' seconds to create AspectWeaver.", (System.nanoTime() - startedAt) / 1e9);
        }
    }

    private void initialize(WeaverContext weaverContext) {
        // 1.create ignoreMatcher
        this.ignoreMatcher = new RawMatcher() {
            @Override
            public boolean matches(TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule,
                    Class<?> classBeingRedefined, ProtectionDomain protectionDomain) {
                if(weaverContext.isMatchJoinpoint() == false)
                    return true;

                if(ElementMatchers.isSynthetic().matches(typeDescription) == true)
                    return true;

                return false;
            }
        };

        // 3.initialize properties
        this.weaverCache = new WeaverCache(weaverContext);
    }


    @Override
    public RawMatcher getIgnoreMatcher() {
        return this.ignoreMatcher;
    }


    @Override
    public boolean matches(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain) {
        long startedAt = System.nanoTime();

        String typeName = typeDescription.getTypeName();
        WeaverMetrics weaverMetrics = null;
        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

        TypeCache typeCache = null;
        try {
            ThreadContext.setContextClassLoader(joinpointClassLoader);   // set joinpointClassLoader

            // diagnostic log
            if(aopContext.isDiagnosticClass(typeName)) {
                LOGGER.info("Matching type '{}' loaded by ClassLoader '{}' in AspectWeaver.", typeName, joinpointClassLoader);
            }

            // check cached result since bytebuddy will enter this method twice when class redefinition, or retransmission
            typeCache = weaverCache.getTypeCache(joinpointClassLoader, typeName);
            if(typeCache.isMatched() == true) {
                return true;
            }

            weaverMetrics = aopContext.getAopMetrics().createWeaverMetrics(joinpointClassLoader, javaModule);
            weaverMetrics.incrTypeLoadingCount();


            // 1.filter type by excluding and including rules in Weaver level
            try {
                weaverMetrics.incrTypeAcceptingCount();
                if(this.acceptType(typeDescription, joinpointClassLoader, javaModule, 
                        classBeingRedefined, protectionDomain, weaverMetrics) == false) {
                    return false;
                }
            } finally {
                weaverMetrics.incrTypeAcceptingTime(System.nanoTime() - startedAt);
            }


            // 2.get or create/cache aspects
            Map<? extends MethodDescription, List<? extends Aspect>> methodDescriptionAspects = 
                    this.aspectFactory.getAspects(typeDescription, joinpointClassLoader, javaModule);

            if(CollectionUtils.isEmpty(methodDescriptionAspects) == false) {
                typeCache.setMethodDescriptionAspects(methodDescriptionAspects);
                Map<String, List<? extends Aspect>> aspectChain = typeCache.getMethodSignatureAspectsMap();

                LOGGER.info("Matched type '{}' loaded by ClassLoader '{}' with below methods and advices. \n{}\n", 
                        typeName, joinpointClassLoader,
                        aspectChain.entrySet().stream()
                            .map( 
                                e -> "  method: " + e.getKey() + "\n" 
                                    + e.getValue().stream().map( a -> "    advice: " + a.getAspectName() ).collect( Collectors.joining("\n")) )
                            .collect( Collectors.joining("\n") ) 
                );

                return true;
            } else {
                if(aopContext.isDiagnosticClass(typeName)) {
                    LOGGER.info("Did not match type '{}' loaded by ClassLoader '{}' in AspectWeaver.", typeName, joinpointClassLoader);
                }

                return false;
            }
        } catch(Throwable t) {
            LOGGER.warn("Failed to match type '{}' loaded by ClassLoader '{}' in AspectWeaver.", typeName, joinpointClassLoader, t);
            return false;
        } finally {
            ThreadContext.setContextClassLoader(existingClassLoader);

            if(null != typeCache && typeCache.isMatched() == false) {
                weaverCache.removeTypeCache(joinpointClassLoader, typeName);        // remove cache since not matched
            }

            if(weaverMetrics != null)
                weaverMetrics.incrTypeLoadingTime(System.nanoTime() - startedAt);
        }
    }

    private boolean acceptType(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, WeaverMetrics weaverMetrics) {
        String joinpointClassLoaderName = ClassLoaderUtils.getClassLoaderName(joinpointClassLoader);

        // 1.check included joinpointClassLoader
        if(weaverContext.getIncludedClassLoadersMatcher().matches(joinpointClassLoaderName) == true) {
            return true;
        }

        // 2.check excluded joinpointClassLoader
        if(weaverContext.getExcludedClassLoadersMatcher().matches(joinpointClassLoaderName) == true) {
            return false;
        }

        ExplicitTypePool explicitTypePool = aopContext.getTypePoolFactory().createExplicitTypePool(joinpointClassLoader, javaModule);
        explicitTypePool.addTypeDescription(typeDescription);

        // 3.check included type
        if(weaverContext.createIncludedTypesMatcher(joinpointClassLoader, javaModule).matches(typeDescription) == true) {
            return true;
        }

        // 4.check excluded type
        if(weaverContext.createExcludedTypesMatcher(joinpointClassLoader, javaModule).matches(typeDescription) == true) {
            return false;
        }

        return true;
    }


    @Override
    public List<? extends Aspect> getAspectChain(ClassLoader joinpointClassLoader, String typeName, String methodSignature) {
        Assert.hasText(typeName, "'typeName' must not be empty.");
        Assert.hasText(methodSignature, "'methodSignature' must not be empty.");

        return weaverCache
                .getTypeCache(joinpointClassLoader, typeName)
                .getMethodSignatureAspectsMap()
                .get(methodSignature);
    }


    @Override
    public Builder<?> transform(Builder<?> builder, TypeDescription typeDescription, ClassLoader joinpointClassLoader,
            JavaModule javaModule, ProtectionDomain protectionDomain) {
        long startedAt = System.nanoTime();
        WeaverMetrics weaverMetrics = aopContext.getAopMetrics().getWeaverMetrics(joinpointClassLoader, javaModule);

        String typeName = typeDescription.getTypeName();
        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

        try {
            ThreadContext.setContextClassLoader(joinpointClassLoader);   // set joinpointClassLoader

            // diagnostic log
            if(aopContext.isDiagnosticClass(typeName)) {
                LOGGER.info("Transforming type '{}' loaded by ClassLoader '{}' in AspectWeaver.", typeName, joinpointClassLoader);
            }

            TypeCache typeCache = weaverCache.getTypeCache(joinpointClassLoader, typeName);

            weaverMetrics.incrTypeTransformationCount();

            // 1.check if cached aspectChain exists
            if(typeCache.isMatched() == false)
                return builder;


            // 2.transform type
            for(Entry<String, MethodDescription> entry : typeCache.getMethodSignatureMap().entrySet()) {
                builder = this.transformMatchedMethods(builder, typeDescription, entry.getKey(), entry.getValue());
            }

            if(Boolean.TRUE == typeCache.setTransformed(true)) {
                LOGGER.error("Reweaved type '{}' loaded by ClassLoader '{}' since it was already transformed!\n", typeName, joinpointClassLoader);
            }

            return builder;
        } finally {
            ThreadContext.setContextClassLoader(existingClassLoader);

            long time = System.nanoTime() - startedAt;
            weaverMetrics.incrTypeTransformationTime(time);
            weaverMetrics.incrTypeLoadingTime(time);
        }
    }

    private Builder<?> transformMatchedMethods(Builder<?> builder, TypeDescription typeDescription, 
            String methodSignature, MethodDescription methodDescription) {
        WithCustomMapping withCustomMapping = net.bytebuddy.asm.Advice.withCustomMapping()
                .bind(
                        ClassFileVersion.JAVA_V7.isGreaterThan(typeDescription.getClassFileVersion())
                            ? new DescriptorOffset.ForRegularInvocation(methodSignature, methodDescription) 
                            : new DescriptorOffset.ForDynamicInvocation(methodSignature, methodDescription)
                )
                ;

        if(methodDescription.isStatic()) {
            if(methodDescription.isTypeInitializer()) {
                builder = builder.visit(
                        withCustomMapping
                        .to(this.weaverContext.getClassInitializerAdvice())
                        .on(ElementMatchers.is(methodDescription) ) );
            } else {
                builder = builder.visit(
                        withCustomMapping
                            .to(this.weaverContext.getClassMethodAdvice())
                            .on(ElementMatchers.is(methodDescription) ) );
            }
        } else {
            if(methodDescription.isConstructor()) {
                builder = builder.visit(
                        withCustomMapping
                            .to(this.weaverContext.getInstanceConstructorAdvice())
                            .on(ElementMatchers.is(methodDescription) ) );  
            } else if(methodDescription.isMethod()) {
                builder = builder.visit(
                        withCustomMapping
                            .to(this.weaverContext.getInstanceMethodAdvice())
                            .on(ElementMatchers.is(methodDescription) ) );
            }
        }
        // type initializer, native, etc

        return builder;
    }


    /* 
     * @see java.lang.BootstrapAdvice.Factory#createDescriptor(java.lang.invoke.MethodHandles.Lookup, java.lang.Object[])
     */
    @Override
    public Object createDescriptor(Lookup lookup, Object... arguments) {
        String methodSignature = (String) arguments[0];
        return weaverCache.getJoinpointDescriptor(lookup, methodSignature);
    }


    /*
     * @see java.lang.BootstrapAdvice.Factory#getDescriptorCallSite(java.lang.invoke.MethodHandles.Lookup, java.lang.String, java.lang.invoke.MethodType, java.lang.Object[])
     */
    @Override
    public CallSite createDescriptorCallSite(Lookup lookup, String bsmMethodName, MethodType bsmMethodType, Object... arguments) {
        String methodSignature = (String) arguments[0];
        Joinpoints.Descriptor descriptor = weaverCache.getJoinpointDescriptor(lookup, methodSignature);

        MethodHandle constant = MethodHandles.constant(Object.class, descriptor);
        return new ConstantCallSite( constant );
    }

    /*
     * @see java.lang.BootstrapAdvice.Factory#dispacther(java.lang.Object, java.lang.Object, java.lang.Object[])
     */
    @Override
    public <T, E extends Throwable> Dispatcher<T, E> dispacther(Object descriptor, Object thisObject, Object[] arguments) {
        return new Joinpoints.MutableJoinpointDispatcher<>( (Descriptor) descriptor, thisObject, arguments, aopContext );
    }


    @Override
    public void close() throws IOException {
        this.weaverCache.close();
    }
}