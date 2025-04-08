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

import java.util.HashMap;
import java.util.Map;

import org.aspectj.bridge.ISourceLocation;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.ast.Var;
import org.aspectj.weaver.tools.MatchingContext;

import io.gemini.aspectj.weaver.world.AnnotationFinder;
import io.gemini.aspectj.weaver.world.TypeWorld;
import net.bytebuddy.description.ByteCodeElement.Member;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
/**
 * @author colyer
 * 
 */
@SuppressWarnings("rawtypes")
public class ShadowImpl extends Shadow {

    private final World world;
    private final ResolvedType enclosingType;
    private final ResolvedMember enclosingMember;
    private final MatchingContext matchContext;
    private Var thisVar = null;
    private Var targetVar = null;
    private Var[] argsVars = null;
    private Var atThisVar = null;
    private Var atTargetVar = null;
    private Map atArgsVars = new HashMap();
    private Map withinAnnotationVar = new HashMap();
    private Map withinCodeAnnotationVar = new HashMap();
    private Map annotationVar = new HashMap();
    private AnnotationFinder annotationFinder;

    public static Shadow makeExecutionShadow(TypeWorld typeWorld, Member forMethod, MatchingContext withContext) {
        if(forMethod instanceof MethodDescription == false)
            // TODO
            throw new RuntimeException();

        MethodDescription method = (MethodDescription) forMethod;

        Kind kind = method.isMethod() ? Shadow.MethodExecution : Shadow.ConstructorExecution;
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedMember(method, typeWorld);
        ResolvedType enclosingType = signature.getDeclaringType().resolve(typeWorld);

        return new ShadowImpl(typeWorld, kind, signature, null, enclosingType, null, withContext);
    }

    public static Shadow makeAdviceExecutionShadow(TypeWorld typeWorld, MethodDescription forMethod, MatchingContext withContext) {
        Kind kind = Shadow.AdviceExecution;
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedAdviceMember(forMethod, typeWorld);
        ResolvedType enclosingType = signature.getDeclaringType().resolve(typeWorld);
        return new ShadowImpl(typeWorld, kind, signature, null, enclosingType, null, withContext);
    }

    public static Shadow makeCallShadow(TypeWorld typeWorld, Member aMember, Member withinCode,
            MatchingContext withContext) {
        Shadow enclosingShadow = makeExecutionShadow(typeWorld, withinCode, withContext);
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedMember(aMember, typeWorld);
        ResolvedMember enclosingMember = ReferenceTypeDelegateFactoryImpl.createResolvedMember(withinCode, typeWorld);
        ResolvedType enclosingType = enclosingMember.getDeclaringType().resolve(typeWorld);

        Kind kind = decideKind(aMember);
        return new ShadowImpl(typeWorld, kind, signature, enclosingShadow, enclosingType, enclosingMember, withContext);
    }

    public static Shadow makeCallShadow(TypeWorld typeWorld, Member aMember, TypeDescription thisClass,
            MatchingContext withContext) {
        Shadow enclosingShadow = makeStaticInitializationShadow(typeWorld, thisClass, withContext);
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedMember(aMember, typeWorld);
        ResolvedMember enclosingMember = ReferenceTypeDelegateFactoryImpl.createStaticInitMember(thisClass, typeWorld);
        ResolvedType enclosingType = enclosingMember.getDeclaringType().resolve(typeWorld);

        Kind kind = decideKind(aMember);
        return new ShadowImpl(typeWorld, kind, signature, enclosingShadow, enclosingType, enclosingMember, withContext);
    }


    private static Kind decideKind(Member aMember) {
        if(aMember instanceof MethodDescription == false)
            // TODO
            throw new RuntimeException();
        
        MethodDescription method = (MethodDescription) aMember;

        Kind kind = null;
        if(method.isMethod()) 
            kind = Shadow.MethodCall;
        else if(method.isConstructor())
            kind = Shadow.ConstructorCall;
        else 
            // TODO
            throw new RuntimeException();

        return kind;
    }

    public static Shadow makeStaticInitializationShadow(TypeWorld typeWorld, TypeDescription forType, MatchingContext withContext) {
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createStaticInitMember(forType, typeWorld);
        ResolvedType enclosingType = signature.getDeclaringType().resolve(typeWorld);
        Kind kind = Shadow.StaticInitialization;
        return new ShadowImpl(typeWorld, kind, signature, null, enclosingType, null, withContext);
    }

    public static Shadow makePreInitializationShadow(TypeWorld typeWorld, MethodDescription forConstructor, MatchingContext withContext) {
        // TODO: check constructor
        Kind kind = Shadow.PreInitialization;
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedMember(forConstructor, typeWorld);
        ResolvedType enclosingType = signature.getDeclaringType().resolve(typeWorld);
        return new ShadowImpl(typeWorld, kind, signature, null, enclosingType, null, withContext);
    }

    public static Shadow makeInitializationShadow(TypeWorld typeWorld, MethodDescription forConstructor, MatchingContext withContext) {
        // TODO: check constructor
        Kind kind = Shadow.Initialization;
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedMember(forConstructor, typeWorld);
        ResolvedType enclosingType = signature.getDeclaringType().resolve(typeWorld);
        return new ShadowImpl(typeWorld, kind, signature, null, enclosingType, null, withContext);
    }

    public static Shadow makeHandlerShadow(TypeWorld typeWorld, TypeDescription exceptionType, TypeDescription withinType, MatchingContext withContext) {
        Kind kind = Shadow.ExceptionHandler;
        Shadow enclosingShadow = makeStaticInitializationShadow(typeWorld, withinType, withContext);
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createHandlerMember(exceptionType, withinType, typeWorld);
        ResolvedMember enclosingMember = ReferenceTypeDelegateFactoryImpl.createStaticInitMember(withinType, typeWorld);
        ResolvedType enclosingType = enclosingMember.getDeclaringType().resolve(typeWorld);
        return new ShadowImpl(typeWorld, kind, signature, enclosingShadow, enclosingType, enclosingMember, withContext);
    }

    public static Shadow makeHandlerShadow(TypeWorld typeWorld, TypeDescription exceptionType, Member withinCode,
            MatchingContext withContext) {
        Kind kind = Shadow.ExceptionHandler;
        Shadow enclosingShadow = makeExecutionShadow(typeWorld, withinCode, withContext);
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createHandlerMember(exceptionType,
                withinCode.getDeclaringType(), typeWorld);
        ResolvedMember enclosingMember = ReferenceTypeDelegateFactoryImpl.createResolvedMember(withinCode, typeWorld);
        ResolvedType enclosingType = enclosingMember.getDeclaringType().resolve(typeWorld);
        return new ShadowImpl(typeWorld, kind, signature, enclosingShadow, enclosingType, enclosingMember, withContext);
    }

    public static Shadow makeFieldGetShadow(TypeWorld typeWorld, FieldDescription forField, TypeDescription callerType, MatchingContext withContext) {
        Shadow enclosingShadow = makeStaticInitializationShadow(typeWorld, callerType, withContext);
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedField(forField, typeWorld);
        ResolvedMember enclosingMember = ReferenceTypeDelegateFactoryImpl.createStaticInitMember(callerType, typeWorld);
        ResolvedType enclosingType = enclosingMember.getDeclaringType().resolve(typeWorld);
        Kind kind = Shadow.FieldGet;
        return new ShadowImpl(typeWorld, kind, signature, enclosingShadow, enclosingType, enclosingMember, withContext);
    }

    public static Shadow makeFieldGetShadow(TypeWorld typeWorld, FieldDescription forField, Member inMember,
            MatchingContext withContext) {
        Shadow enclosingShadow = makeExecutionShadow(typeWorld, inMember, withContext);
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedField(forField, typeWorld);
        ResolvedMember enclosingMember = ReferenceTypeDelegateFactoryImpl.createResolvedMember(inMember, typeWorld);
        ResolvedType enclosingType = enclosingMember.getDeclaringType().resolve(typeWorld);
        Kind kind = Shadow.FieldGet;
        return new ShadowImpl(typeWorld, kind, signature, enclosingShadow, enclosingType, enclosingMember, withContext);
    }

    public static Shadow makeFieldSetShadow(TypeWorld typeWorld, FieldDescription forField, TypeDescription callerType, MatchingContext withContext) {
        Shadow enclosingShadow = makeStaticInitializationShadow(typeWorld, callerType, withContext);
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedField(forField, typeWorld);
        ResolvedMember enclosingMember = ReferenceTypeDelegateFactoryImpl.createStaticInitMember(callerType, typeWorld);
        ResolvedType enclosingType = enclosingMember.getDeclaringType().resolve(typeWorld);
        Kind kind = Shadow.FieldSet;
        return new ShadowImpl(typeWorld, kind, signature, enclosingShadow, enclosingType, enclosingMember, withContext);
    }

    public static Shadow makeFieldSetShadow(TypeWorld typeWorld, FieldDescription forField, Member inMember,
            MatchingContext withContext) {
        Shadow enclosingShadow = makeExecutionShadow(typeWorld, inMember, withContext);
        ResolvedMember signature = ReferenceTypeDelegateFactoryImpl.createResolvedField(forField, typeWorld);
        ResolvedMember enclosingMember = ReferenceTypeDelegateFactoryImpl.createResolvedMember(inMember, typeWorld);
        ResolvedType enclosingType = enclosingMember.getDeclaringType().resolve(typeWorld);
        Kind kind = Shadow.FieldSet;
        return new ShadowImpl(typeWorld, kind, signature, enclosingShadow, enclosingType, enclosingMember, withContext);
    }

    public ShadowImpl(World world, Kind kind, org.aspectj.weaver.Member signature, Shadow enclosingShadow, ResolvedType enclosingType,
            ResolvedMember enclosingMember, MatchingContext withContext) {
        super(kind, signature, enclosingShadow);
        this.world = world;
        this.enclosingType = enclosingType;
        this.enclosingMember = enclosingMember;
        this.matchContext = withContext;
        if (world instanceof TypeWorld) {
            this.annotationFinder = ((TypeWorld) world).getAnnotationFinder();
        }
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getIWorld()
     */
    public World getIWorld() {
        return world;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getThisVar()
     */
    public Var getThisVar() {
        if (thisVar == null && hasThis()) {
            thisVar = VarImpl.createThisVar(getThisType().resolve(world), this.annotationFinder);
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
            targetVar = VarImpl.createTargetVar(getThisType().resolve(world), this.annotationFinder);
        }
        return targetVar;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getEnclosingType()
     */
    public UnresolvedType getEnclosingType() {
        return this.enclosingType;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getArgVar(int)
     */
    public Var getArgVar(int i) {
        if (argsVars == null) {
            this.argsVars = new Var[this.getArgCount()];
            for (int j = 0; j < this.argsVars.length; j++) {
                this.argsVars[j] = VarImpl.createArgsVar(getArgType(j).resolve(world), j, this.annotationFinder);
            }
        }
        if (i < argsVars.length) {
            return argsVars[i];
        } else {
            return null;
        }
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
    public Var getKindedAnnotationVar(UnresolvedType forAnnotationType) {
        ResolvedType annType = forAnnotationType.resolve(world);
        if (annotationVar.get(annType) == null) {
            Var v = VarImpl.createAtAnnotationVar(annType, this.annotationFinder);
            annotationVar.put(annType, v);
        }
        return (Var) annotationVar.get(annType);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getWithinAnnotationVar(org.aspectj.weaver.UnresolvedType)
     */
    @SuppressWarnings("unchecked")
    public Var getWithinAnnotationVar(UnresolvedType forAnnotationType) {
        ResolvedType annType = forAnnotationType.resolve(world);
        if (withinAnnotationVar.get(annType) == null) {
            Var v = VarImpl.createWithinAnnotationVar(annType, this.annotationFinder);
            withinAnnotationVar.put(annType, v);
        }
        return (Var) withinAnnotationVar.get(annType);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getWithinCodeAnnotationVar(org.aspectj.weaver.UnresolvedType)
     */
    @SuppressWarnings("unchecked")
    public Var getWithinCodeAnnotationVar(UnresolvedType forAnnotationType) {
        ResolvedType annType = forAnnotationType.resolve(world);
        if (withinCodeAnnotationVar.get(annType) == null) {
            Var v = VarImpl.createWithinCodeAnnotationVar(annType, this.annotationFinder);
            withinCodeAnnotationVar.put(annType, v);
        }
        return (Var) withinCodeAnnotationVar.get(annType);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getThisAnnotationVar(org.aspectj.weaver.UnresolvedType)
     */
    public Var getThisAnnotationVar(UnresolvedType forAnnotationType) {
        if (atThisVar == null) {
            atThisVar = VarImpl.createThisAnnotationVar(forAnnotationType.resolve(world), this.annotationFinder);
        }
        return atThisVar;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getTargetAnnotationVar(org.aspectj.weaver.UnresolvedType)
     */
    public Var getTargetAnnotationVar(UnresolvedType forAnnotationType) {
        if (atTargetVar == null) {
            atTargetVar = VarImpl.createTargetAnnotationVar(forAnnotationType.resolve(world), this.annotationFinder);
        }
        return atTargetVar;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getArgAnnotationVar(int, org.aspectj.weaver.UnresolvedType)
     */
    @SuppressWarnings("unchecked")
    public Var getArgAnnotationVar(int i, UnresolvedType forAnnotationType) {
        ResolvedType annType = forAnnotationType.resolve(world);
        if (atArgsVars.get(annType) == null) {
            Var[] vars = new Var[getArgCount()];
            atArgsVars.put(annType, vars);
        }
        Var[] vars = (Var[]) atArgsVars.get(annType);
        if (i > (vars.length - 1))
            return null;
        if (vars[i] == null) {
            vars[i] = VarImpl.createArgsAnnotationVar(annType, i, this.annotationFinder);
        }
        return vars[i];
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getEnclosingCodeSignature()
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

    /*
     * (non-Javadoc)
     * 
     * @see org.aspectj.weaver.Shadow#getSourceLocation()
     */
    public ISourceLocation getSourceLocation() {
        return null;
    }

    public MatchingContext getMatchingContext() {
        return this.matchContext;
    }
}
