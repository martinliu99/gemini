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
package io.gemini.aop.aspectapp;

import java.util.List;
import java.util.Map;

import io.gemini.aop.aspect.Aspect;
import io.gemini.aop.aspect.AspectSpec;
import io.gemini.core.object.Closeable;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;

public interface AspectFactory extends Closeable {

    Map<String, List<AspectSpecHolder<AspectSpec>>> getAspectSpecHolders();

    Map<? extends MethodDescription, List<? extends Aspect>> getAspects(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule);

    void close();
}
