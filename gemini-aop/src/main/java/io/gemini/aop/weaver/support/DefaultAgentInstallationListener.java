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
package io.gemini.aop.weaver.support;

import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.agent.builder.AgentBuilder.InstallationListener;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;

public class DefaultAgentInstallationListener implements InstallationListener {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAgentInstallationListener.class);


    @Override
    public void onBeforeInstall(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer) {

    }

    @Override
    public void onInstall(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer) {
    }

    @Override
    public Throwable onError(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer,
            Throwable throwable) {
        LOGGER.error("Failed to install Gemini Agent!", throwable);
        return throwable;
    }

    @Override
    public void onReset(Instrumentation instrumentation, ResettableClassFileTransformer classFileTransformer) {
    }

    @Override
    public void onBeforeWarmUp(Set<Class<?>> types, ResettableClassFileTransformer classFileTransformer) {
    }

    @Override
    public void onWarmUpError(Class<?> type, ResettableClassFileTransformer classFileTransformer, Throwable throwable) {
    }

    @Override
    public void onAfterWarmUp(Map<Class<?>, byte[]> types, ResettableClassFileTransformer classFileTransformer,
            boolean transformed) {
    }

}
