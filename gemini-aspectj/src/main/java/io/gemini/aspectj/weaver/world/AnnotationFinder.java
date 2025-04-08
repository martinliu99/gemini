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

import java.util.Set;

import org.aspectj.weaver.AnnotationAJ;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;

import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.type.TypeDescription;

/**
 * @author colyer Used in 1.4 code to access annotations safely
 */
public interface AnnotationFinder {

    void setTypeWorld(TypeWorld aWorld);

    AnnotationDescription getAnnotation(ResolvedType annotationType, Object onObject);

    AnnotationDescription getAnnotationFromClass(ResolvedType annotationType, TypeDescription typeDescription);

    AnnotationDescription getAnnotationFromMember(ResolvedType annotationType, Member aMember);

    public AnnotationAJ getAnnotationOfType(UnresolvedType ofType,
            Member onMember);

    public String getAnnotationDefaultValue(Member onMember);

    Set<ResolvedType>/* ResolvedType */getAnnotations(Member onMember);

    ResolvedType[] getAnnotations(TypeDescription typeDescription, World inWorld);
    
    ResolvedType[][] getParameterAnnotationTypes(Member onMember);
}
