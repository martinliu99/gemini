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
package io.gemini.aop.weaver.support;

import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.agent.builder.AgentBuilder.InstallationListener;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;

/**
 * ByteBuddy {@link InstallationListener} that logs errors during agent installation.
 *
 * @author   martin.liu
 */
public class DefaultTransformerInstallationListener implements InstallationListener {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTransformerInstallationListener.class);


    /**
     * Called before the ByteBuddy agent is installed. No-op in this implementation.
     *
     * @param instrumentation       the JVM instrumentation API
     * @param classFileTransformer  the class file transformer being installed
     */
    @Override
    public void onBeforeInstall(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer) {

    }

    /**
     * Called after the ByteBuddy agent is successfully installed. No-op in this implementation.
     *
     * @param instrumentation       the JVM instrumentation API
     * @param classFileTransformer  the installed class file transformer
     */
    @Override
    public void onInstall(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer) {
    }

    /**
     * Called when the ByteBuddy agent installation fails. Logs the error at ERROR level.
     *
     * @param instrumentation       the JVM instrumentation API
     * @param classFileTransformer  the class file transformer that failed to install
     * @param throwable             the installation error
     * @return the original throwable, allowing ByteBuddy to propagate it
     */
    @Override
    public Throwable onError(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer,
            Throwable throwable) {
        LOGGER.error("Could not install Aop Launcher!", throwable);
        return throwable;
    }

    /**
     * Called when the ByteBuddy agent is reset. No-op in this implementation.
     *
     * @param instrumentation       the JVM instrumentation API
     * @param classFileTransformer  the class file transformer being reset
     */
    @Override
    public void onReset(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer) {
    }

    /**
     * Called before warm-up types are processed. No-op in this implementation.
     *
     * @param types                the set of types to warm up
     * @param classFileTransformer the class file transformer
     */
    @Override
    public void onBeforeWarmUp(Set<Class<?>> types, ResettableClassFileTransformer classFileTransformer) {
    }

    /**
     * Called when a warm-up type fails to process. No-op in this implementation.
     *
     * @param type                 the type that failed warm-up
     * @param classFileTransformer the class file transformer
     * @param throwable            the error that occurred
     */
    @Override
    public void onWarmUpError(Class<?> type, ResettableClassFileTransformer classFileTransformer, Throwable throwable) {
    }

    /**
     * Called after all warm-up types have been processed. No-op in this implementation.
     *
     * @param types                map of warm-up types to their bytecode
     * @param classFileTransformer the class file transformer
     * @param transformed          whether any types were actually transformed
     */
    @Override
    public void onAfterWarmUp(Map<Class<?>, byte[]> types, ResettableClassFileTransformer classFileTransformer,
            boolean transformed) {
    }

}
