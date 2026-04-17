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

import java.util.Arrays;
import java.util.List;

import net.bytebuddy.asm.Advice.BootstrapArgumentResolver;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaConstant;

/**
 * Provides the {@link net.bytebuddy.asm.Advice.BootstrapArgumentResolver} for ByteBuddy
 * {@code @Advice} classes that use the {@link io.gemini.aop.weaver.BootstrapDispatcher} callback.
 * <p>
 * Passes the callback slot index and the advice class name as bootstrap method arguments,
 * allowing the dispatcher to route the call to the correct {@link io.gemini.aop.weaver.AopWeaver.WeavedCodeCallback}.
 * </p>
 *
 * @author   martin.liu
 */
public class DefaultBootstrapArgumentResolverFactory implements BootstrapArgumentResolver.Factory {

    private final Object callbackCode;

    /**
     * Creates a new factory with the given callback slot code.
     *
     * @param callbackCode the slot index or identifier used to route the INDY callback
     */
    public DefaultBootstrapArgumentResolverFactory(Object callbackCode) {
        this.callbackCode = callbackCode;
    }

    /** 
     * {@inheritDoc} 
     */
    @Override
    public BootstrapArgumentResolver resolve(InDefinedShape adviceMethod, boolean exit) {
        return new DefaultBootstrapArgumentResolver(callbackCode, adviceMethod);
    }


    static class DefaultBootstrapArgumentResolver implements BootstrapArgumentResolver {

        private final Object callbackCode;
        private final MethodDescription.InDefinedShape adviceMethod;

        public DefaultBootstrapArgumentResolver(Object callbackCode, MethodDescription.InDefinedShape adviceMethod) {
            this.callbackCode = callbackCode;
            this.adviceMethod = adviceMethod;
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public List<JavaConstant> resolve(TypeDescription instrumentedType, MethodDescription instrumentedMethod) {
            return Arrays.asList(
                    JavaConstant.Simple.wrap(callbackCode),
                    JavaConstant.Simple.ofLoaded(adviceMethod.getDeclaringType().getName())
            );
        }
    }
}
