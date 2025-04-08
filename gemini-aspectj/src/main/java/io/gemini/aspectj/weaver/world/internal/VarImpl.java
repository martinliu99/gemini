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


import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.ast.Var;

import io.gemini.aspectj.weaver.BindingType;
import io.gemini.aspectj.weaver.world.AnnotationFinder;
import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.type.TypeDescription;


/**
 * A variable at a reflection shadow, used by the residual tests.
 */
public final class VarImpl extends Var {

    private AnnotationFinder annotationFinder = null;

    private BindingType category;
    private int argsIndex = 0;


    public static VarImpl createThisVar(ResolvedType type, AnnotationFinder finder) {
        VarImpl ret = new VarImpl(type,finder);
        ret.category = BindingType.THIS_VAR;
        return ret;
    }

    public static VarImpl createTargetVar(ResolvedType type, AnnotationFinder finder) {
        VarImpl ret = new VarImpl(type,finder);
        ret.category = BindingType.TARGET_VAR;
        return ret; 
    }

    public static VarImpl createArgsVar(ResolvedType type, int index, AnnotationFinder finder) {
        VarImpl ret = new VarImpl(type,finder);
        ret.category = BindingType.ARGS_VAR;
        ret.argsIndex = index;
        return ret; 
    }

    public static VarImpl createThisAnnotationVar(ResolvedType type, AnnotationFinder finder) {
        VarImpl ret = new VarImpl(type,finder);
        ret.category = BindingType.AT_THIS_VAR;
        return ret;
    }

    public static VarImpl createTargetAnnotationVar(ResolvedType type, AnnotationFinder finder) {
        VarImpl ret = new VarImpl(type,finder);
        ret.category = BindingType.AT_TARGET_VAR;
        return ret;
    }

    public static VarImpl createArgsAnnotationVar(ResolvedType type, int index, AnnotationFinder finder) {
        VarImpl ret = new VarImpl(type,finder);
        ret.category = BindingType.AT_ARGS_VAR;
        ret.argsIndex = index;
        return ret;
    }

    public static VarImpl createWithinAnnotationVar(ResolvedType annType, AnnotationFinder finder) {
        VarImpl ret = new VarImpl(annType,finder);
        ret.category = BindingType.AT_WITHIN_VAR;
        return ret;
    }

    public static VarImpl createWithinCodeAnnotationVar(ResolvedType annType, AnnotationFinder finder) {
        VarImpl ret = new VarImpl(annType,finder);
        ret.category = BindingType.AT_WITHINCODE_VAR;
        return ret;
    }

    public static VarImpl createAtAnnotationVar(ResolvedType annType, AnnotationFinder finder) {
        VarImpl ret = new VarImpl(annType,finder);
        ret.category = BindingType.AT_ANNOTATION_VAR;
        return ret;
    }

    private VarImpl(ResolvedType type,AnnotationFinder finder) {
        super(type);
        this.annotationFinder = finder;
    }


    public int getArgsIndex() {
        return argsIndex;
    }

    public BindingType getCategory() {
        return category;
    }

    public Object getBindingAtJoinPoint(Object thisObject, Object targetObject, Object[] args) {
        return getBindingAtJoinPoint(thisObject,targetObject,args,null,null,null);
    }

    /**
     * At a join point with the given this, target, and args, return the object to which this
     * var is bound.
     * @param thisObject
     * @param targetObject
     * @param args
     * @return
     */
    public Object getBindingAtJoinPoint(
            Object thisObject, 
            Object targetObject, 
            Object[] args,
            Member subject,
            Member withinCode,
            TypeDescription withinType) {
        switch( this.category) {
            case THIS_VAR: return thisObject;
            case TARGET_VAR: return targetObject;
            case ARGS_VAR:
                if (this.argsIndex > (args.length - 1)) return null;
                return args[argsIndex];
            case AT_THIS_VAR:
                if (annotationFinder != null) {
                    return annotationFinder.getAnnotation(getType(), thisObject);
                } else return null;
            case AT_TARGET_VAR:
                if (annotationFinder != null) {
                    return annotationFinder.getAnnotation(getType(), targetObject);
                } else return null;
            case AT_ARGS_VAR:
                if (this.argsIndex > (args.length - 1)) return null;
                if (annotationFinder != null) {
                    return annotationFinder.getAnnotation(getType(), args[argsIndex]);
                } else return null;
            case AT_WITHIN_VAR:
                if (annotationFinder != null) {
                    return annotationFinder.getAnnotationFromClass(getType(), withinType);
                } else return null;
            case AT_WITHINCODE_VAR:
                if (annotationFinder != null) {
                    return annotationFinder.getAnnotationFromMember(getType(), withinCode);
                } else return null;
            case AT_ANNOTATION_VAR:
                if (annotationFinder != null) {
                    return annotationFinder.getAnnotationFromMember(getType(), subject);
                } else return null;
            default:
                return null;
        }
    }

}
