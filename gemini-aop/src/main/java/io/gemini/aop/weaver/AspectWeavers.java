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

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AopMetrics.BootstraperMetrics;
import io.gemini.aop.AspectFactory;
import io.gemini.aop.AspectWeaver;
import io.gemini.aop.java.lang.BootstrapAdvice;
import io.gemini.aop.java.lang.BootstrapClassConsumer;
import io.gemini.aop.weaver.support.DefaultRedefinitionListener;
import io.gemini.aop.weaver.support.DefaultTransformationListener;
import io.gemini.aop.weaver.support.DefaultTransformerInstallationListener;
import io.gemini.aop.weaver.support.DiscoveryStrategyAdapter;
import io.gemini.core.util.Assert;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.DescriptionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.FallbackStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.InjectionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.dynamic.scaffold.MethodGraph;


/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
@BootstrapClassConsumer
public class AspectWeavers {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectWeavers.class);


    public static AspectWeaver createAspectWeaver(Instrumentation instrumentation, 
            AopContext aopContext, AspectFactory aspectFactory) {
        Assert.notNull(instrumentation, "'instrumentation' must not be null.");
        Assert.notNull(aopContext, "'aopContext' must not be null.");
        Assert.notNull(aspectFactory, "'aspectFactory' must not be null.");

        AopMetrics.BootstraperMetrics bootstraperMetrics = aopContext.getAopMetrics().getBootstraperMetrics();

        // 1.create AspectWeaver
        WeaverContext weaverContext = new WeaverContext(aopContext);

        final AspectWeaver aspectWeaver = createAspectWeaver(aopContext, aspectFactory, bootstraperMetrics, weaverContext);


        // 2.install bytebuddy
        installByteBuddy(instrumentation, aopContext, bootstraperMetrics, weaverContext, aspectWeaver);

        return aspectWeaver;
    }

    protected static AspectWeaver createAspectWeaver(AopContext aopContext, 
            AspectFactory aspectFactory, 
            BootstraperMetrics bootstraperMetrics,
            WeaverContext weaverContext) {
        long startedAt = System.nanoTime();

        // 1.create AspectWeaver
        DefaultAspectWeaver aspectWeaver = new DefaultAspectWeaver(aopContext, aspectFactory, weaverContext);

        // 2.initialize BootstrapAdvice.Bridger
        BootstrapAdvice.Bridger.setFactory(aspectWeaver);
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("$Initialized BootstrapAdvice.Bridger with '{}' loaded by classLoader '{}'.", 
                    aspectWeaver, AspectWeavers.class.getClassLoader());
        }

        bootstraperMetrics.setAspectWeaverCreationTime(System.nanoTime() - startedAt);

        return aspectWeaver;
    }

    protected static void installByteBuddy(Instrumentation instrumentation, 
            AopContext aopContext, 
            BootstraperMetrics bootstraperMetrics,
            WeaverContext weaverContext,
            AspectWeaver aspectWeaver) {
        long startedAt = System.nanoTime();
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("^Installing ByteBuddy.");
        }

        AtomicLong typeRetransformationStartedAt = new AtomicLong(0);
        DiscoveryStrategyAdapter.Listener discoveryStrategyListern = new DiscoveryStrategyAdapter.Listener( ) {

            @Override
            public void onStart() {
                long time = System.nanoTime() - startedAt;
                bootstraperMetrics.setBytebuddyInstallationTime(time);
                if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
                    LOGGER.info("$Took '{}' seconds to install ByteBuddy.", time / 1e9);
                }

                typeRetransformationStartedAt.set( System.nanoTime() );
                if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
                    LOGGER.info("^Redefining loaded types.");
                }
            }

        };

        //        ResettableClassFileTransformer resettableClassFileTransformer = 
        aopContext.getTypePoolFactory().customizeAgentBuilder( new AgentBuilder.Default() )
            .with( new ByteBuddy()
                    .with( MethodGraph.Compiler.ForDeclaredMethods.INSTANCE )
            )
            .ignore( aspectWeaver.getIgnoreMatcher() )
            // better performance than REDEFINE or REDEFINE_FROZEN
            .with( TypeStrategy.Default.DECORATE )
            .with( InjectionStrategy.UsingUnsafe.INSTANCE )
            // support lambda, for debug only
//              .with( AgentBuilder.LambdaInstrumentationStrategy.ENABLED )
            .with( DescriptionStrategy.Default.HYBRID )
            .with( InitializationStrategy.NoOp.INSTANCE )
            // re-transform loaded classes, and only work with Advice and disableClassFormatChanges
            .with( RedefinitionStrategy.DISABLED != weaverContext.getRedefinitionStrategy()
                    ? weaverContext.getRedefinitionStrategy() 
                    : RedefinitionStrategy.RETRANSFORMATION )
            .with( new DiscoveryStrategyAdapter(
                    RedefinitionStrategy.DiscoveryStrategy.SinglePass.INSTANCE, 
                    discoveryStrategyListern,
                    RedefinitionStrategy.DISABLED == weaverContext.getRedefinitionStrategy() ) ) 
            .with( new DefaultRedefinitionListener(aopContext.getAopMetrics()) )
            .with( FallbackStrategy.ByThrowableType.ofOptionalTypes() )
            // warn up bootstrap ClassLoader
            .warmUp( System.class )
            .with( new DefaultTransformerInstallationListener() )
            .with( new DefaultTransformationListener(aopContext) )
            .disableClassFormatChanges()
            .type( aspectWeaver )
            .transform( aspectWeaver )
            .installOn( instrumentation )
            ;

        long time = System.nanoTime() - typeRetransformationStartedAt.get();
        bootstraperMetrics.setTypeRedefiningTime(time);
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("$Took '{}' seconds to redefine loaded types.", time / 1e9);
        }
    }
}
