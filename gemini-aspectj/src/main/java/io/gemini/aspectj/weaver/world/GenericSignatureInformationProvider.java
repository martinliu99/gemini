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
package io.gemini.aspectj.weaver.world;

import org.aspectj.weaver.UnresolvedType;

import io.gemini.aspectj.weaver.world.internal.InternalResolvedMember;

/**
 * This interface exists to support two different strategies for answering 
 * generic signature related questions on Java 5 and pre-Java 5.
 */
public interface GenericSignatureInformationProvider {

    UnresolvedType[] getGenericParameterTypes(InternalResolvedMember resolvedMember);
    
    UnresolvedType getGenericReturnType(InternalResolvedMember resolvedMember);

    boolean isBridge(InternalResolvedMember resolvedMember);
    
    boolean isVarArgs(InternalResolvedMember resolvedMember);
    
    boolean isSynthetic(InternalResolvedMember resolvedMember);
}
