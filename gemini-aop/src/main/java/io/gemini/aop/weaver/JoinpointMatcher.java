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

import java.security.ProtectionDomain;
import java.util.List;

import io.gemini.aop.aspect.Aspect;
import net.bytebuddy.agent.builder.AgentBuilder.RawMatcher;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;

public interface JoinpointMatcher extends RawMatcher {

    RawMatcher getIgnoreMatcher();

    boolean matches(TypeDescription typeDescription,
            ClassLoader joinpointClassLoader, JavaModule javaModule,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain);


    List<? extends Aspect> getAspectChain(ClassLoader joinpointClassLoader, String typeName, String method);

}
