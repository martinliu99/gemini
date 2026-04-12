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

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AopMetrics.LauncherMetrics;
import io.gemini.aop.factory.AdvisorFactory;
import io.gemini.aop.weaver.support.DefaultRedefinitionListener;
import io.gemini.aop.weaver.support.DefaultTransformationListener;
import io.gemini.aop.weaver.support.DefaultTransformerInstallationListener;
import io.gemini.aop.weaver.support.DiscoveryStrategyAdapter;
import io.gemini.core.bootstrap.BootstrapClassConsumer;
import io.gemini.core.util.Assert;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.ClassFileBufferStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.FallbackStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.InjectionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RawMatcher;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.Nexus;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.dynamic.scaffold.TypeWriter;
import net.bytebuddy.matcher.BooleanMatcher;
import net.bytebuddy.utility.AsmClassReader;
import net.bytebuddy.utility.JavaModule;
import net.bytebuddy.utility.OpenedClassReader;

@BootstrapClassConsumer
public interface AopWeaver extends RawMatcher, Transformer, Closeable {

    MethodDescription.InDefinedShape BOOTSTRAP_DISPATCHER_CALLBACK_METHOD =
            TypeDescription.ForLoadedType.of(BootstrapDispatcher.class).getDeclaredMethods()
            .filter( 
                    isPublic()
                    .and( named("callback") )
                    .and( takesArguments( MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class) )
            )
            .getOnly();


    @Override
    boolean matches(TypeDescription targetType, ClassLoader targetClassLoader, JavaModule targetModule,
            Class<?> classBeingRedefined, ProtectionDomain targetProtectionDomain);


    @Override
    Builder<?> transform(Builder<?> builder, TypeDescription targetType, ClassLoader targetClassLoader, 
            JavaModule targetModule, ProtectionDomain targetProtectionDomain);


    WeaverContext getWeaverContext();


    int registerCallback(WeavedCodeCallback weavedCodeCallback);


    @Override
    void close() throws IOException;


    @BootstrapClassConsumer
    enum Creator {

        INSTANCE;


        private static final Logger LOGGER = LoggerFactory.getLogger(AopWeaver.class);


        public AopWeaver create(Instrumentation instrumentation, 
                AopContext aopContext, AdvisorFactory advisorFactory) {
            Assert.notNull(instrumentation, "'instrumentation' must not be null.");
            Assert.notNull(aopContext, "'aopContext' must not be null.");
            Assert.notNull(advisorFactory, "'advisorFactory' must not be null.");

            AopMetrics.LauncherMetrics launcherMetrics = aopContext.getAopMetrics().getLauncherMetrics();

            // 1.create AopWeaver
            final DefaultAopWeaver aopWeaver = createAopWeaver(aopContext, advisorFactory, launcherMetrics);


            // 2.install bytebuddy
            installByteBuddy(instrumentation, aopContext, launcherMetrics, aopWeaver);

            return aopWeaver;
        }

        protected DefaultAopWeaver createAopWeaver(AopContext aopContext, 
                AdvisorFactory advisorFactory, 
                LauncherMetrics launcherMetrics) {
            long startedAt = System.nanoTime();

            // 1.create WeaverContext
            WeaverContext weaverContext = new WeaverContext(aopContext);

            // 2.create AopWeaver
            DefaultAopWeaver aopWeaver = aopContext.getDiagnosticLevel().isSimpleEnabled() == false
                    ? new DefaultAopWeaver(aopContext, advisorFactory, weaverContext)
                    : new DefaultAopWeaver.Diagnostic(aopContext, advisorFactory, weaverContext);

            // 2.initialize BootstrapDispatcher
            BootstrapDispatcher.setDelegator(aopWeaver);
            if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled()) 
                LOGGER.info("$Initialized BootstrapDispatcher.Delegator with '{}' loaded by classLoader '{}'.", 
                        aopWeaver, AopWeaver.class.getClassLoader());

            launcherMetrics.setAopWeaverCreationTime(System.nanoTime() - startedAt);

            return aopWeaver;
        }

        protected void installByteBuddy(Instrumentation instrumentation, 
                AopContext aopContext, 
                LauncherMetrics launcherMetrics,
                DefaultAopWeaver aopWeaver) {
            long startedAt = System.nanoTime();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("^Installing ByteBuddy, ");
            }

            AtomicLong typeRetransformationStartedAt = new AtomicLong(0);
            DiscoveryStrategyAdapter.Listener discoveryStrategyListern = new DiscoveryStrategyAdapter.Listener( ) {

                @Override
                public void onStart() {
                    long time = System.nanoTime() - startedAt;
                    launcherMetrics.warmupByteBuddy(time);
                    if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled()) 
                        LOGGER.info("$Took '{}' seconds to install ByteBuddy. \n", time / AopMetrics.NANO_TIME);

                    typeRetransformationStartedAt.set( System.nanoTime() );
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("^Matching and redefining loaded types, ");
                    }
                }

            };

            System.getProperties().setProperty(OpenedClassReader.PROCESSOR_PROPERTY, 
                    AsmClassReader.Factory.Default.CLASS_FILE_API_FIRST.toString());
            System.getProperties().setProperty(Nexus.PROPERTY, Boolean.TRUE.toString());

            // set bytebuddy setting to dump byte code
            if (aopContext.isDumpByteCode()) {
                String byteCodeDumpPath = aopContext.getByteCodeDumpPath();
                File path = new File(byteCodeDumpPath + File.separator + "byte-buddy");
                path.mkdirs();

                System.getProperties().setProperty(TypeWriter.DUMP_PROPERTY, path.getAbsolutePath());
            }


            WeaverContext weaverContext = aopWeaver.getWeaverContext();
            new AgentBuilder.Default()
                .with( new ByteBuddy()
                        .with( MethodGraph.Compiler.ForDeclaredMethods.INSTANCE )
                )
                .ignore( BooleanMatcher.of(weaverContext.isEnableWeaver() == false) )
                .with( aopWeaver )
                .with( InjectionStrategy.Disabled.INSTANCE )
                // support native method
                .enableNativeMethodPrefix( weaverContext.getNativeMethodPrefix() )
                // support lambda, for debug only
//                  .with( AgentBuilder.LambdaInstrumentationStrategy.ENABLED )
                .with( aopContext.getTypePoolFactory().getPoolStrategy() )
                .with( aopContext.getTypePoolFactory().getDescriptionStrategy() )
                .with( aopContext.getTypePoolFactory().getLocationStrategy() )
                .with( ClassFileBufferStrategy.Default.RETAINING )
                // re-transform loaded classes, and only work with Advice and disableClassFormatChanges
                .with( RedefinitionStrategy.DISABLED != weaverContext.getRedefinitionStrategy()
                        ? weaverContext.getRedefinitionStrategy() 
                        : RedefinitionStrategy.RETRANSFORMATION )
                .with( RedefinitionStrategy.BatchAllocator.ForFixedSize.ofSize(20) )
                .with( new DiscoveryStrategyAdapter(
                        RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE, 
                        discoveryStrategyListern,
                        RedefinitionStrategy.DISABLED == weaverContext.getRedefinitionStrategy() ) ) 
                .with( new DefaultRedefinitionListener(aopContext.getDiagnosticLevel(), aopContext.getAopMetrics()) )
                .with( FallbackStrategy.ByThrowableType.ofOptionalTypes() )
                // warn up bootstrap ClassLoader
                .warmUp( System.class )
                .with( new DefaultTransformerInstallationListener() )
                .with( aopContext.getDiagnosticLevel().isSimpleEnabled() == false
                        ? new DefaultTransformationListener(aopContext)
                        : new DefaultTransformationListener.Diagnostic(aopContext) )
                .with( InitializationStrategy.NoOp.INSTANCE )
                .type( aopWeaver )
                .transform( aopWeaver )
                .installOn( instrumentation )
                ;

            long time = System.nanoTime() - typeRetransformationStartedAt.get();
            launcherMetrics.setTypeRedefiningTime(time);
            if (LOGGER.isInfoEnabled() && aopContext.getDiagnosticLevel().isSimpleEnabled()) 
                LOGGER.info("$Took '{}' seconds to match and redefine loaded types.", time / 1e9);
        }
    }


    interface WeavedCodeCallback {

        Object callback(MethodHandles.Lookup targetLookup, 
                String methodName, MethodType methodType, Object... arguments);

    }
}
