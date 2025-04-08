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

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AopMetrics.BootstraperMetrics;
import io.gemini.aop.weaver.support.DefaultAgentInstallationListener;
import io.gemini.aop.weaver.support.DefaultAgentListener;
import io.gemini.aop.weaver.support.DefaultAspectWeaver;
import io.gemini.aop.weaver.support.DefaultRedefinitionListener;
import io.gemini.aop.weaver.support.DiscoveryStrategyAdapter;
import io.gemini.core.concurrent.DaemonThreadFactory;
import io.gemini.core.util.Assert;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.DescriptionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.FallbackStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.InjectionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;


public class AspectWeaverInstaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectWeaverInstaller.class);


    private AopContext aopContext;


    public AspectWeaverInstaller() {
    }

    public AspectWeaverInstaller aopContext(AopContext aopContext) {
        Assert.notNull(aopContext, "'aopContext' must not be null.");
        this.aopContext = aopContext;
        return this;
    }

    public AspectWeaver build() {
        AopMetrics.BootstraperMetrics bootstraperMetrics = this.aopContext.getAopMetrics().getBootstraperMetrics();

        // 1.create AspectWeaver
        final AspectWeaver aspectWeaver = this.doCreateAspectWeaver(aopContext, bootstraperMetrics);


        // 2.install bytebuddy
        this.doInstallByteBuddy(aspectWeaver, aopContext, bootstraperMetrics);


        // 3.register shutdown hook
        Thread shutdownHook = new DaemonThreadFactory("ShutdownTask")
                .newThread( () -> {
                    try {
                        aspectWeaver.close();

                        LOGGER.info("Stopped Gemini agent.");
                    } catch (Exception e) {
                        // TODO
                    }
                });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        return aspectWeaver;
    }

    protected AspectWeaver doCreateAspectWeaver(AopContext aopContext, BootstraperMetrics bootstraperMetrics) {
        long startedAt = System.nanoTime();

        // 1.create AspectWeaver
        DefaultAspectWeaver aspectWeaver = new DefaultAspectWeaver(aopContext);

        // 2.initialize BootstrapAdvice.Bridger
        BootstrapAdvice.Bridger.setFactory(aspectWeaver);
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("$Initialized BootstrapAdvice.Bridger with '{}' loaded by classLoader '{}'", 
                    aspectWeaver, AspectWeaverInstaller.class.getClassLoader());
        }

        bootstraperMetrics.setAspectWeaverCreationTime(System.nanoTime() - startedAt);

        return aspectWeaver;
    }

    protected void doInstallByteBuddy(AspectWeaver aspectWeaver, AopContext aopContext, 
            BootstraperMetrics bootstraperMetrics) {
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
            .ignore( aspectWeaver.getIgnoreMatcher() )
            // better performance than REDEFINE or REDEFINE_FROZEN
            .with( TypeStrategy.Default.DECORATE )
            .with( InjectionStrategy.UsingUnsafe.INSTANCE )
            // support lambda, for debug only
//              .with( AgentBuilder.LambdaInstrumentationStrategy.ENABLED )
            .with( DescriptionStrategy.Default.HYBRID )
            .with( InitializationStrategy.NoOp.INSTANCE )
            // re-transform loaded classes, and only work with Advice and disableClassFormatChanges
            .with( RedefinitionStrategy.DISABLED != aopContext.getRedefinitionStrategy()
                    ? aopContext.getRedefinitionStrategy() 
                    : RedefinitionStrategy.RETRANSFORMATION )
            .with( new DiscoveryStrategyAdapter(
                    RedefinitionStrategy.DiscoveryStrategy.SinglePass.INSTANCE, 
                    discoveryStrategyListern,
                    RedefinitionStrategy.DISABLED == aopContext.getRedefinitionStrategy() ) ) 
            .with( new DefaultRedefinitionListener(aopContext) )
            .with( FallbackStrategy.ByThrowableType.ofOptionalTypes() )
            // warn up bootstrap ClassLoader
            .warmUp( System.class )
            .with( new DefaultAgentInstallationListener() )
            .with( new DefaultAgentListener(aopContext) )
            .disableClassFormatChanges()
            .type( aspectWeaver )
            .transform( aspectWeaver )
            .installOn( aopContext.getInstrumentation()  )
            ;

        long time = System.nanoTime() - typeRetransformationStartedAt.get();
        bootstraperMetrics.setTypeRedefiningTime(time);
        if(aopContext.getDiagnosticLevel().isSimpleEnabled()) {
            LOGGER.info("$Took '{}' seconds to redefine loaded types.", time / 1e9);
        }
    }
}
