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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class DefaultTransformationListener implements Listener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTransformationListener.class);

    private final AopContext aopContext;


    public DefaultTransformationListener(AopContext aopContext) {
        this.aopContext = aopContext;
    }

    @Override
    public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule javaModule, boolean loaded) {
        if (aopContext.isDiagnosticClass(typeName))
            LOGGER.info("Discovering {} type '{}' loaded by ClassLoader '{}'.", 
                    loaded ? "loaded" : "", typeName, classLoader
            );
    }

    @Override
    public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule,
            boolean loaded, DynamicType dynamicType) {
        if (aopContext.getDiagnosticLevel().isDebugEnabled())
            LOGGER.info("{} type '{}' loaded by ClassLoader '{}'.", 
                    loaded ? "Redefined loaded" : "Transformed", typeDescription.getTypeName(), classLoader
            );
    }

    @Override
    public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule, boolean loaded) {
        String typeName = typeDescription.getTypeName();
        if (aopContext.isDiagnosticClass(typeName))
            LOGGER.info("Ignored {} type '{}' loaded by ClassLoader '{}'.", 
                    loaded ? "loaded" : "", typeName, classLoader
            );
    }

    @Override
    public void onError(String typeName, ClassLoader classLoader, JavaModule javaModule, boolean loaded,
            Throwable throwable) {
        if (LOGGER.isWarnEnabled())
            LOGGER.warn("Failed to {} type '{}' loaded by ClassLoader '{}'. \n"
                    + "  Error reason: {} \n", 
                    loaded ? "redefine loaded" : "transform", typeName, classLoader, 
                    throwable.getMessage(), throwable
            );
    }

    @Override
    public void onComplete(String typeName, ClassLoader classLoader, JavaModule javaModule, boolean loaded) {
        if (aopContext.isDiagnosticClass(typeName))
            LOGGER.info("Finished to {} type '{}' loaded by ClassLoader '{}'.", 
                    loaded ? "redefine loaded" : "transform", typeName, classLoader);
    }

}
