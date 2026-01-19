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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.AopContext;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class DefaultTransformationListener extends Listener.Adapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTransformationListener.class);


    private final AopContext aopContext;


    public DefaultTransformationListener(AopContext aopContext) {
        this.aopContext = aopContext;
    }


    protected AopContext getAopContext() {
        return aopContext;
    }


    @Override
    public void onTransformation(TypeDescription targetType, ClassLoader targetClassLoader, 
            JavaModule targetModule, boolean loaded, DynamicType dynamicType) {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("{} type '{}' loaded by ClassLoader '{}'.", 
                    loaded ? "Redefined loaded" : "Transformed", targetType.getTypeName(), targetClassLoader
            );
    }

    @Override
    public void onError(String targetTypeName,  ClassLoader targetClassLoader, 
            JavaModule targetModule, boolean loaded, Throwable throwable) {
        if (LOGGER.isWarnEnabled())
            LOGGER.warn("Could not {} type '{}' loaded by ClassLoader '{}'. \n"
                    + "  Error reason: {} \n", 
                    loaded ? "redefine loaded" : "transform", targetTypeName, targetClassLoader, 
                    throwable.getMessage(), throwable
            );
    }

    @Override
    public void onComplete(String targetTypeName, ClassLoader targetClassLoader, 
            JavaModule targetJavaModule, boolean loaded) {
        // release cached data
        this.aopContext.getTypePoolFactory().removeTypeResolution(targetTypeName);
    }


    public static class Diagnostic extends DefaultTransformationListener {

        private static final Logger LOGGER = LoggerFactory.getLogger(Diagnostic.class);


        public Diagnostic(AopContext aopContext) {
            super(aopContext);
        }

        @Override
        public void onDiscovery(String targetTypeName,  ClassLoader targetClassLoader, 
                JavaModule targetModule, boolean loaded) {
            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                LOGGER.info("Discovering {} type '{}' loaded by ClassLoader '{}'.", 
                        loaded ? "loaded" : "", targetTypeName, targetClassLoader
                );
        }

        @Override
        public void onIgnored(TypeDescription targetType, ClassLoader targetClassLoader, 
                JavaModule targetModule, boolean loaded) {
            String typeName = targetType.getTypeName();
            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(typeName))
                LOGGER.info("Ignored {} type '{}' loaded by ClassLoader '{}'.", 
                        loaded ? "loaded" : "", typeName, targetClassLoader
                );
        }

        @Override
        public void onComplete(String targetTypeName, ClassLoader targetClassLoader, 
                JavaModule targetModule, boolean loaded) {
            super.onComplete(targetTypeName, targetClassLoader, targetModule, loaded);

            // release cached data
            getAopContext().removeCachedDiagnosticType(targetTypeName);

            if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(targetTypeName))
                LOGGER.info("Finished to {} type '{}' loaded by ClassLoader '{}'.", 
                        loaded ? "redefine loaded" : "transform", targetTypeName, targetClassLoader);
        }
    }
}
