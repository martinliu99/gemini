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

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aspectj.weaver.AbstractAnnotationAJ;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;


class InternalAnnotationAJ extends AbstractAnnotationAJ {

    private final AnnotationDescription annotationDescription;
    private final boolean isRuntimeVisible;
    private final Map<String, AnnotationValue<?, ?>> nameValuePair;


    public InternalAnnotationAJ(ResolvedType resolvedType, AnnotationDescription annotationDescription) {
        super(resolvedType);

        this.annotationDescription = annotationDescription;
        this.isRuntimeVisible = RetentionPolicy.RUNTIME == annotationDescription.getRetention();

        MethodList<MethodDescription.InDefinedShape> properties = annotationDescription.getAnnotationType().getDeclaredMethods().filter(
                takesArguments(0).and( isPublic() ).and( not( isStatic() ) ) );

        this.nameValuePair = new HashMap<>(properties.size());
        for(MethodDescription.InDefinedShape methodDescription : properties) {
            nameValuePair.put(methodDescription.getName(), annotationDescription.getValue(methodDescription));
        }
    }


    /**
     * {@inheritDoc}
     */
    public boolean isRuntimeVisible() {
        return isRuntimeVisible;
    }

    /**
     * {@inheritDoc}
     */
    public String stringify() {
        StringBuilder builder = new StringBuilder()
                .append("@").append(type.getClassName());

        if (nameValuePair.size() > 0) {
            builder.append("(");
            int i = 0;
            for(Entry<String, AnnotationValue<?, ?>> entry : nameValuePair.entrySet()) {
                if(i > 0)
                    builder.append(", ");

                builder.append(entry.getKey()).append("=").append(entry.getValue().resolve().toString());
            }
            builder.append(")");
        }
        return builder.toString();
    }

    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append("Annot[").append(getTypeSignature()).append(" ")
                .append(isRuntimeVisible ? "runtimeVisible" : "runtimeInvisible");

        if (nameValuePair.size() > 0) {
            builder.append(" ");
            int i = 0;
            for(Entry<String, AnnotationValue<?, ?>> entry : nameValuePair.entrySet()) {
                if(i > 0)
                    builder.append(", ");

                builder.append(entry.getKey()).append("=").append(entry.getValue().resolve().toString());
            }
        }
        builder.append("]");
        return builder.toString();
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNamedValue(String name) {
        return nameValuePair.containsKey(name);
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNameValuePair(String name, String value) {
        AnnotationValue<?, ?> annotationValue = nameValuePair.get(name);
        return annotationValue != null && annotationValue.resolve().toString().equals(value);
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getTargets() {
        if (!type.equals(UnresolvedType.AT_TARGET))
            return Collections.emptySet();

        Set<String> targets = new HashSet<>(annotationDescription.getElementTypes().size());
        for(ElementType elementType : annotationDescription.getElementTypes()) {
            targets.add(elementType.name());
        }
        return targets;
    }


    /**
     * {@inheritDoc}
     */
    public String getStringFormOfValue(String name) {
        AnnotationValue<?, ?> annotationValue = nameValuePair.get(name);
        return annotationValue == null ? null : annotationValue.resolve().toString();
    }
}
