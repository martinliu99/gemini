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
package io.gemini.aspectj.weaver.world.internal;

import java.util.HashSet;
import java.util.Set;

import org.aspectj.weaver.AbstractAnnotationAJ;
import org.aspectj.weaver.ResolvedType;

import net.bytebuddy.description.annotation.AnnotationDescription;

/**
 * This type represents the weavers abstraction of an annotation - it is not tied to any underlying BCI toolkit. The weaver actualy
 * handles these through AnnotationX wrapper objects - until we start transforming the BCEL annotations into this form (expensive)
 * or offer a clever visitor mechanism over the BCEL annotation stuff that builds these annotation types directly.
 * 
 * @author AndyClement
 */
public class AnnotationAJImpl extends AbstractAnnotationAJ {

    private final boolean isRuntimeVisible;
    private final AnnotationDescription annotationDescription;

    public AnnotationAJImpl(ResolvedType type, AnnotationDescription annotationDescription) {
        super(type);
        
        this.annotationDescription = annotationDescription;
//        this.isRuntimeVisible = annotationDescription.getElementTypes();
        isRuntimeVisible = true;
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
        StringBuffer sb = new StringBuffer();
        sb.append("@").append(type.getClassName());
//        if (hasNameValuePairs()) {
//            sb.append("(");
//            for (AnnotationNameValuePair nvPair : nvPairs) {
//                sb.append(nvPair.stringify());
//            }
//            sb.append(")");
//        }
        return sb.toString();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Anno[" + getTypeSignature() + " " + (isRuntimeVisible ? "rVis" : "rInvis"));
//        if (nvPairs != null) {
//            sb.append(" ");
//            for (Iterator<AnnotationNameValuePair> iter = nvPairs.iterator(); iter.hasNext();) {
//                AnnotationNameValuePair element = iter.next();
//                sb.append(element.toString());
//                if (iter.hasNext()) {
//                    sb.append(",");
//                }
//            }
//        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNamedValue(String n) {
//        if (nvPairs == null) {
//            return false;
//        }
//        for (int i = 0; i < nvPairs.size(); i++) {
//            AnnotationNameValuePair pair = nvPairs.get(i);
//            if (pair.getName().equals(n)) {
//                return true;
//            }
//        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNameValuePair(String n, String v) {
//        if (nvPairs == null) {
//            return false;
//        }
//        for (int i = 0; i < nvPairs.size(); i++) {
//            AnnotationNameValuePair pair = nvPairs.get(i);
//            if (pair.getName().equals(n)) {
//                if (pair.getValue().stringify().equals(v)) {
//                    return true;
//                }
//            }
//        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getTargets() {
//        if (!type.equals(UnresolvedType.AT_TARGET)) {
//            return Collections.emptySet();
//        }
//        AnnotationNameValuePair nvp = nvPairs.get(0);
//        ArrayAnnotationValue aav = (ArrayAnnotationValue) nvp.getValue();
//        AnnotationValue[] avs = aav.getValues();
        Set<String> targets = new HashSet<>();
//        for (int i = 0; i < avs.length; i++) {
//            EnumAnnotationValue value = (EnumAnnotationValue)avs[i];
//            targets.add(value.getValue());
//        }
        return targets;
    }


    /**
     * {@inheritDoc}
     */
    public String getStringFormOfValue(String name) {
//        if (hasNameValuePairs()) {
//            for (AnnotationNameValuePair nvPair : nvPairs) {
//                if (nvPair.getName().equals(name)) {
//                    return nvPair.getValue().stringify();
//                }
//            }
//        }
        return null;
    }
}
