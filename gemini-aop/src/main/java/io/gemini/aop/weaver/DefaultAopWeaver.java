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
import java.util.Collections;
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
import io.gemini.aop.java.lang.BootstrapAdvice;
import io.gemini.aop.java.lang.BootstrapAdvice.Dispatcher;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
import io.gemini.aop.weaver.Joinpoints.Descriptor;
import io.gemini.aop.weaver.WeaverCache.TypeCache;
import io.gemini.aop.weaver.advice.DescriptorOffset;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.ClassFileVersion;
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
class DefaultAopWeaver implements AopWeaver, BootstrapAdvice.Factory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAopWeaver.class);


    private final AopContext aopContext;
    private final AdvisorFactory advisorFactory;
    private final WeaverContext weaverContext;

    private WeaverCache weaverCache;


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

        this.initialize(weaverContext);


        if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled())
            LOGGER.info("$Took '{}' seconds to create AopWeaver.", (System.nanoTime() - startedAt) / 1e9);
    }

    private void initialize(WeaverContext weaverContext) {
        // 1.initialize properties
        this.weaverCache = new WeaverCache(weaverContext);
    }


    @Override
    public boolean matches(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain) {
        long startedAt = System.nanoTime();
        TypeMetrics typeMetric = aopContext.getAopMetrics().createTypeMetric(joinpointClassLoader, typeDescription.getTypeName());

        try {
            return doMatch(typeDescription, 
                    joinpointClassLoader, javaModule,
                    classBeingRedefined, protectionDomain);
        } finally {
            typeMetric.incrTypeWeavingTime(System.nanoTime() - startedAt);
            getAopContext().getAopMetrics().collect(typeMetric);
        }
    }

    protected boolean doMatch(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain) {
        String typeName = typeDescription.getTypeName();
        // 1.check cached result since bytebuddy will enter this method twice when class redefinition, or retransmission
        TypeCache typeCache = weaverCache.getTypeCache(joinpointClassLoader, typeName);
        if (typeCache != null && typeCache.isMatched() == true)
            return true;


        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
        try {
            ThreadContext.setContextClassLoader(joinpointClassLoader);   // set joinpointClassLoader

            // 2.filter type by TypeMatcher
            if (doAcceptType(typeDescription, joinpointClassLoader, javaModule) == false)
                return false;


            // 3.get or create/cache advisors
            return getAdvisors(typeDescription, joinpointClassLoader, javaModule).size() > 0;
        } catch (Throwable t) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Could not match type '{}' loaded by ClassLoader '{}' in AopWeaver.", 
                        typeName, joinpointClassLoader, t);

            Throwables.throwIfRequired(t);
            return false;
        } finally {
            ThreadContext.setContextClassLoader(existingClassLoader);
        }
    }

    protected boolean doAcceptType(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule) {
        if (weaverContext.acceptClassLoader(joinpointClassLoader) == false)
            return false;

        // filter type by typeMatcher
        return weaverContext.acceptType(typeDescription.getTypeName());
    }

    private Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule) {
        Map<? extends MethodDescription, List<? extends Advisor>> methodDescriptionAdvisors = 
                this.advisorFactory.getAdvisors(typeDescription, joinpointClassLoader, javaModule);
        if (CollectionUtils.isEmpty(methodDescriptionAdvisors) == true) 
            return Collections.emptyMap();


        TypeCache typeCache = weaverCache.createTypeCache(typeDescription.getTypeName());
        weaverCache.putTypeCache(joinpointClassLoader, typeCache);
        typeCache.setMethodDescriptionAdvisors(methodDescriptionAdvisors);
        return methodDescriptionAdvisors;
    }


    @Override
    public Builder<?> transform(Builder<?> builder, TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            ProtectionDomain protectionDomain) {
        // transform type
        long startedAt = System.nanoTime();
        try {
            return doTransform(builder, typeDescription, 
                    joinpointClassLoader, javaModule,
                    protectionDomain);
        } finally {
            AopMetrics.currentTypeMetrics().incrTypeWeavingTime(System.nanoTime() - startedAt);
        }
    }

    protected Builder<?> doTransform(Builder<?> builder, TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule, 
            ProtectionDomain protectionDomain) {
        String typeName = typeDescription.getTypeName();
        // 1.check if cached advisorChain exists
        TypeCache typeCache = weaverCache.getTypeCache(joinpointClassLoader, typeName);
        if (typeCache.isMatched() == false)
            return builder;


        // 2.transform type
        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
        try {
            ThreadContext.setContextClassLoader(joinpointClassLoader);   // set joinpointClassLoader

            for (Entry<String, MethodDescription> entry : typeCache.getMethodSignatureMap().entrySet()) {
                builder = this.transformMatchedMethods(builder, typeDescription, entry.getKey(), entry.getValue());
            }

            if (Boolean.TRUE == typeCache.setTransformed(true)) {
                LOGGER.error("Reweaved type '{}' loaded by ClassLoader '{}' since it was already transformed!\n", typeName, joinpointClassLoader);
            }

            return builder;
        } finally {
            ThreadContext.setContextClassLoader(existingClassLoader);
        }
    }

    private Builder<?> transformMatchedMethods(Builder<?> builder, TypeDescription typeDescription, 
            String methodSignature, MethodDescription methodDescription) {
        WithCustomMapping withCustomMapping = net.bytebuddy.asm.Advice.withCustomMapping()
                .bind(
                        ClassFileVersion.JAVA_V7.isGreaterThan(typeDescription.getClassFileVersion())
                            ? new DescriptorOffset.ForRegularInvocation(methodSignature, methodDescription) 
                            : new DescriptorOffset.ForDynamicInvocation(methodSignature, methodDescription)
                );

        if (methodDescription.isStatic()) {
            if (methodDescription.isTypeInitializer()) {
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
            if (methodDescription.isConstructor()) {
                builder = builder.visit(
                        withCustomMapping
                            .to(this.weaverContext.getInstanceConstructorAdvice())
                            .on(ElementMatchers.is(methodDescription) ) );  
            } else if (methodDescription.isMethod()) {
                builder = builder.visit(
                        withCustomMapping
                            .to(this.weaverContext.getInstanceMethodAdvice())
                            .on(ElementMatchers.is(methodDescription) ) );
            }
        }
        // type initializer, native, etc

        return builder;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Object createDescriptor(Lookup lookup, Object... arguments) {
        String methodSignature = (String) arguments[0];
        return weaverCache.getJoinpointDescriptor(lookup, methodSignature);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public CallSite createDescriptorCallSite(Lookup lookup, String bsmMethodName, MethodType bsmMethodType, Object... arguments) {
        String methodSignature = (String) arguments[0];
        Joinpoints.Descriptor descriptor = weaverCache.getJoinpointDescriptor(lookup, methodSignature);

        MethodHandle constant = MethodHandles.constant(Object.class, descriptor);
        return new ConstantCallSite( constant );
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public <T, E extends Throwable> Dispatcher<T, E> dispacther(Object descriptor, Object thisObject, Object[] arguments) {
        return descriptor == null
                ? null
                : new Joinpoints.MutableJoinpointDispatcher<>( (Descriptor) descriptor, thisObject, arguments, aopContext );
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
        public boolean matches(TypeDescription typeDescription, 
                ClassLoader joinpointClassLoader, JavaModule javaModule,
                Class<?> classBeingRedefined, ProtectionDomain protectionDomain) {
            // match type
            long startedAt = System.nanoTime();
            TypeMetrics typeMetrics = getAopContext().getAopMetrics().createTypeMetric(joinpointClassLoader, typeDescription.getTypeName());

            boolean matched = false;
            try {
                String typeName = typeDescription.getTypeName();

                // diagnostic log
                if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(typeName))
                    LOGGER.info("Matching type '{}' loaded by ClassLoader '{}' in AopWeaver.", typeName, joinpointClassLoader);

                matched = super.doMatch(typeDescription, 
                        joinpointClassLoader, javaModule, 
                        classBeingRedefined, protectionDomain);
                return matched;
            } finally {
                typeMetrics.incrTypeWeavingTime(System.nanoTime() - startedAt);

                getAopContext().getAopMetrics().collect(typeMetrics);
            }
        }

        @Override
        protected boolean doAcceptType(TypeDescription typeDescription, 
                ClassLoader joinpointClassLoader, JavaModule javaModule) {
            long startedAt = System.nanoTime();
            try {
                return super.doAcceptType(typeDescription, joinpointClassLoader, javaModule);
            } finally {
                AopMetrics.currentTypeMetrics().incrTypeAcceptingTime(System.nanoTime() - startedAt);
            }
        }


        @Override
        public Builder<?> transform(Builder<?> builder, TypeDescription typeDescription, 
                ClassLoader joinpointClassLoader, JavaModule javaModule, ProtectionDomain protectionDomain) {
            long startedAt = System.nanoTime();
            TypeMetrics typeMetrics = getAopContext().getAopMetrics().createTypeMetric(joinpointClassLoader, typeDescription.getTypeName());

            try {
                String typeName = typeDescription.getTypeName();

                // diagnostic log
                if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(typeName))
                    LOGGER.info("Transforming type '{}' loaded by ClassLoader '{}' in AopWeaver.", typeName, joinpointClassLoader);

                return super.doTransform(builder, typeDescription, 
                        joinpointClassLoader, javaModule, protectionDomain);

                // log transformation in DefaultTransformationListener
            } finally {
                long time = System.nanoTime() - startedAt;
                typeMetrics.incrTypeTransformationTime(time);
                typeMetrics.incrTypeWeavingTime(time);

                getAopContext().getAopMetrics().collect(typeMetrics);
            }
        }


        @Override
        public <T, E extends Throwable> Dispatcher<T, E> dispacther(Object descriptor, Object thisObject, Object[] arguments) {
            return descriptor == null
                    ? null
                    : new Joinpoints.MutableJoinpointDispatcher.Diagnostic<>( (Descriptor) descriptor, thisObject, arguments, getAopContext() );
        }
    }
}