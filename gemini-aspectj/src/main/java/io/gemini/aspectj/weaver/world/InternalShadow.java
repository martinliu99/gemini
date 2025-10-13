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

import java.util.HashMap;
import java.util.Map;

import org.aspectj.bridge.ISourceLocation;
import org.aspectj.weaver.Member;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.ast.Var;

import io.gemini.aspectj.weaver.PointcutParameter.ParamCategory;


@SuppressWarnings("rawtypes")
class InternalShadow extends Shadow {

    private final BytebuddyWorld world;

    private final ResolvedType enclosingType;
    private final ResolvedMember enclosingMember;

    private Var thisVar = null;
    private Var targetVar = null;
    private Var[] argsVars = null;
    private Var atThisVar = null;
    private Var atTargetVar = null;
    private Map atArgsVars = new HashMap();
    private Map withinAnnotationVar = new HashMap();
    private Map withinCodeAnnotationVar = new HashMap();
    private Map annotationVar = new HashMap();


    public InternalShadow(BytebuddyWorld world, Kind kind, 
            Member signature, Shadow enclosingShadow, 
            ResolvedType enclosingType, ResolvedMember enclosingMember) {
        super(kind, signature, enclosingShadow);
        this.world = world;
        this.enclosingType = enclosingType;
        this.enclosingMember = enclosingMember;
    }

    /**
     * {@inheritDoc}
     */
    public World getIWorld() {
        return world;
    }

    /**
     * {@inheritDoc}
     */
    public Var getThisVar() {
        if (thisVar == null && hasThis()) {
            thisVar = new InternalVar(getThisType().resolve(world), ParamCategory.THIS_VAR);
        }
        return thisVar;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getTargetVar()
     */
    public Var getTargetVar() {
        if (targetVar == null && hasTarget()) {
            targetVar = new InternalVar(getThisType().resolve(world), ParamCategory.TARGET_VAR);
        }
        return targetVar;
    }

    /**
     * {@inheritDoc}
     */
    public UnresolvedType getEnclosingType() {
        return this.enclosingType;
    }

    /**
     * {@inheritDoc}
     */
    public Var getArgVar(int i) {
        if (argsVars == null) {
            this.argsVars = new Var[this.getArgCount()];
            for (int j = 0; j < this.argsVars.length; j++) {
                this.argsVars[j] = new InternalVar(getArgType(j).resolve(world), ParamCategory.ARGS_VAR, j);
            }
        }

        return i < argsVars.length ? argsVars[i] : null;
    }

    public Var getThisJoinPointVar() {
        return null;
    }

    public Var getThisJoinPointStaticPartVar() {
        return null;
    }

    public Var getThisEnclosingJoinPointStaticPartVar() {
        return null;
    }

    public Var getThisAspectInstanceVar(ResolvedType aspectType) {
        return null;
    }

    @SuppressWarnings("unchecked")
    public Var getKindedAnnotationVar(UnresolvedType annotationType) {
        ResolvedType annType = annotationType.resolve(world);
        if (annotationVar.get(annType) == null) {
            Var v = new InternalVar(annType, ParamCategory.AT_ANNOTATION_VAR);
            annotationVar.put(annType, v);
        }
        return (Var) annotationVar.get(annType);
    }

    /**
     * 
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public Var getWithinAnnotationVar(UnresolvedType annotationType) {
        ResolvedType annType = annotationType.resolve(world);
        if (withinAnnotationVar.get(annType) == null) {
            Var v = new InternalVar(annType, ParamCategory.AT_WITHIN_VAR);
            withinAnnotationVar.put(annType, v);
        }
        return (Var) withinAnnotationVar.get(annType);
    }

    /**
     * 
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public Var getWithinCodeAnnotationVar(UnresolvedType annotationType) {
        ResolvedType annType = annotationType.resolve(world);
        if (withinCodeAnnotationVar.get(annType) == null) {
            Var v = new InternalVar(annType, ParamCategory.AT_WITHINCODE_VAR);
            withinCodeAnnotationVar.put(annType, v);
        }
        return (Var) withinCodeAnnotationVar.get(annType);
    }

    /**
     * 
     * {@inheritDoc}
     */
    public Var getThisAnnotationVar(UnresolvedType annotationType) {
        if (atThisVar == null) {
            atThisVar = new InternalVar(annotationType.resolve(world), ParamCategory.AT_THIS_VAR);
        }
        return atThisVar;
    }

    /**
     * 
     * {@inheritDoc}
     */
    public Var getTargetAnnotationVar(UnresolvedType annotationType) {
        if (atTargetVar == null) {
            atTargetVar = new InternalVar(annotationType.resolve(world), ParamCategory.AT_TARGET_VAR);
        }
        return atTargetVar;
    }

    /**
     * 
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public Var getArgAnnotationVar(int i, UnresolvedType annotationType) {
        ResolvedType annType = annotationType.resolve(world);
        if (atArgsVars.get(annType) == null) {
            Var[] vars = new Var[getArgCount()];
            atArgsVars.put(annType, vars);
        }

        Var[] vars = (Var[]) atArgsVars.get(annType);
        if (i > (vars.length - 1))
            return null;

        if (vars[i] == null) {
            vars[i] = new InternalVar(annType, ParamCategory.AT_ARGS_VAR, i);
        }
        return vars[i];
    }

    /**
     * 
     * {@inheritDoc}
     */
    public org.aspectj.weaver.Member getEnclosingCodeSignature() {
        // XXX this code is copied from BcelShadow with one minor change...
        if (getKind().isEnclosingKind()) {
            return getSignature();
        } else if (getKind() == Shadow.PreInitialization) {
            // PreInit doesn't enclose code but its signature
            // is correctly the signature of the ctor.
            return getSignature();
        } else if (enclosingShadow == null) {
            return this.enclosingMember;
        } else {
            return enclosingShadow.getSignature();
        }
    }

    /**
     * 
     * {@inheritDoc}
     */
    public ISourceLocation getSourceLocation() {
        return null;
    }

}
