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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.aspectj.weaver.IntMap;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.patterns.AndPointcut;
import org.aspectj.weaver.patterns.CflowPointcut;
import org.aspectj.weaver.patterns.KindedPointcut;
import org.aspectj.weaver.patterns.NotPointcut;
import org.aspectj.weaver.patterns.OrPointcut;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.ThisOrTargetAnnotationPointcut;
import org.aspectj.weaver.patterns.ThisOrTargetPointcut;
import org.aspectj.weaver.tools.PointcutPrimitive;
import org.aspectj.weaver.tools.UnsupportedPointcutPrimitiveException;

import io.gemini.aspectj.weaver.ExprParser;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.aspectj.weaver.patterns.PatternParserV2;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;


public class PointcutParser {

    private TypeWorld typeWorld;
    private final Set<PointcutPrimitive> supportedPrimitives;


    protected static Set<PointcutPrimitive> getAllSupportedPointcutPrimitives() {
        Set<PointcutPrimitive> primitives = new LinkedHashSet<>();
//        primitives.add(PointcutPrimitive.ADVICE_EXECUTION);
        primitives.add(PointcutPrimitive.ARGS);
//        primitives.add(PointcutPrimitive.CALL);
        primitives.add(PointcutPrimitive.EXECUTION);
//        primitives.add(PointcutPrimitive.GET);
//        primitives.add(PointcutPrimitive.HANDLER);
//        primitives.add(PointcutPrimitive.INITIALIZATION);
//        primitives.add(PointcutPrimitive.PRE_INITIALIZATION);
//        primitives.add(PointcutPrimitive.SET);
        primitives.add(PointcutPrimitive.STATIC_INITIALIZATION);
        primitives.add(PointcutPrimitive.TARGET);
        primitives.add(PointcutPrimitive.THIS);
        primitives.add(PointcutPrimitive.WITHIN);
//        primitives.add(PointcutPrimitive.WITHIN_CODE);
        primitives.add(PointcutPrimitive.AT_ANNOTATION);
        primitives.add(PointcutPrimitive.AT_THIS);
        primitives.add(PointcutPrimitive.AT_TARGET);
        primitives.add(PointcutPrimitive.AT_ARGS);
        primitives.add(PointcutPrimitive.AT_WITHIN);
//        primitives.add(PointcutPrimitive.AT_WITHINCODE);
        primitives.add(PointcutPrimitive.REFERENCE);

        return primitives;
    }


    public PointcutParser(TypeWorld typeWorld) {
        this(typeWorld, getAllSupportedPointcutPrimitives());
    }

    /**
     * Create a pointcut parser that can parse pointcut expressions built from a user-defined subset of AspectJ's supported pointcut
     * primitives. The following restrictions apply:
     * <ul>
     * <li>The <code>if, cflow, and cflowbelow</code> pointcut designators are not supported
     * <li>Pointcut expressions must be self-contained :- they cannot contain references to other named pointcuts
     * <li>The pointcut expression must be anonymous with no formals allowed.
     * </ul>
     * 
     * @param supportedPointcutKinds a set of PointcutPrimitives this parser should support
     * @throws UnsupportedOperationException if the set contains if, cflow, or cflow below
     */
    public PointcutParser(TypeWorld typeWorld, Set<PointcutPrimitive> supportedPointcutKinds) {
        this.typeWorld = typeWorld;

        supportedPrimitives = supportedPointcutKinds;
        for (PointcutPrimitive pointcutPrimitive : supportedPointcutKinds) {
            if ((pointcutPrimitive == PointcutPrimitive.IF) || (pointcutPrimitive == PointcutPrimitive.CFLOW)
                    || (pointcutPrimitive == PointcutPrimitive.CFLOW_BELOW)) {
                throw new UnsupportedOperationException("Cannot handle if, cflow, and cflowbelow primitives");
            }
        }
    }


    public Pointcut parsePointcut(String pointcutExpression) {
        return parsePointcut(pointcutExpression, null, Collections.emptyMap());
    }

    /**
     * Parse the given pointcut expression. The pointcut is resolved as if it had been declared inside the inScope class (this
     * allows the pointcut to contain unqualified references to other pointcuts declared in the same type for example). The pointcut
     * may contain zero or more formal parameters to be bound at matched join points.
     * 
     * @throws UnsupportedPointcutPrimitiveException if the parser encounters a primitive pointcut expression of a kind not
     *         supported by this PointcutParser.
     * @throws IllegalArgumentException if the expression is not a well-formed pointcut expression
     */
    public Pointcut parsePointcut(String pointcutExpression, 
            TypeDescription pointcutDeclarationScope, Map<String, ? extends TypeDefinition> pointcutParameters) {
        try {
             Pointcut pointcut = resolvePointcutExpression(pointcutExpression, pointcutDeclarationScope, pointcutParameters);

             pointcut = concretizePointcutExpression(pointcut, pointcutDeclarationScope, pointcutParameters);

             validateAgainstSupportedPrimitives(pointcut, pointcutExpression); // again, because we have now followed any ref'd pcuts

             return pointcut;
        } catch (Exception e) {
            ExprParser.handleException(pointcutExpression, e);
            return null;
        }
    }

    protected Pointcut resolvePointcutExpression(String pointcutExpression, 
            TypeDescription pointcutDeclarationScope, Map<String, ? extends TypeDefinition> pointcutParameters) {
        try {
            pointcutExpression = ExprParser.replaceBooleanOperators(pointcutExpression);
            PatternParser parser = new PatternParserV2(pointcutExpression);

            Pointcut pointcut = parser.parsePointcut(true);
            validateAgainstSupportedPrimitives(pointcut, pointcutExpression);

            pointcut = pointcut.resolve(
                    ExprParser.buildResolutionScope(typeWorld, pointcutDeclarationScope, pointcutParameters) );

            return pointcut;
        } catch (Exception e) {
            ExprParser.handleException(pointcutExpression, e);
            return null;
        }
    }

    protected Pointcut concretizePointcutExpression(Pointcut pointcut, 
            TypeDefinition pointcutDeclarationScope, Map<String, ? extends TypeDefinition> pointcutParameters) {
        ResolvedType declaringTypeForResolution = null;
        if (pointcutDeclarationScope != null) {
            declaringTypeForResolution = typeWorld.resolve(pointcutDeclarationScope.getTypeName());
        } else {
            declaringTypeForResolution = typeWorld.getWorld().resolve(ResolvedType.OBJECT);
        }
        IntMap arity = new IntMap(pointcutParameters.size());
        for (int i = 0; i < pointcutParameters.size(); i++) {
            arity.put(i, i);
        }
        return pointcut.concretize(declaringTypeForResolution, declaringTypeForResolution, arity);
    }

    private void validateAgainstSupportedPrimitives(Pointcut pointcut, String pointcutExpression) {
        switch (pointcut.getPointcutKind()) {
        case Pointcut.AND:
            validateAgainstSupportedPrimitives(((AndPointcut) pointcut).getLeft(), pointcutExpression);
            validateAgainstSupportedPrimitives(((AndPointcut) pointcut).getRight(), pointcutExpression);
            break;
        case Pointcut.ARGS:
            if (!supportedPrimitives.contains(PointcutPrimitive.ARGS)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.ARGS);
            }
            break;
        case Pointcut.CFLOW:
            CflowPointcut cfp = (CflowPointcut) pointcut;
            if (cfp.isCflowBelow()) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.CFLOW_BELOW);
            } else {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.CFLOW);
            }
        case Pointcut.HANDLER:
            if (!supportedPrimitives.contains(PointcutPrimitive.HANDLER)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.HANDLER);
            }
            break;
        case Pointcut.IF:
        case Pointcut.IF_FALSE:
        case Pointcut.IF_TRUE:
            throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.IF);
        case Pointcut.KINDED:
            validateKindedPointcut(((KindedPointcut) pointcut), pointcutExpression);
            break;
        case Pointcut.NOT:
            validateAgainstSupportedPrimitives(((NotPointcut) pointcut).getNegatedPointcut(), pointcutExpression);
            break;
        case Pointcut.OR:
            validateAgainstSupportedPrimitives(((OrPointcut) pointcut).getLeft(), pointcutExpression);
            validateAgainstSupportedPrimitives(((OrPointcut) pointcut).getRight(), pointcutExpression);
            break;
        case Pointcut.THIS_OR_TARGET:
            boolean isThis = ((ThisOrTargetPointcut) pointcut).isThis();
            if (isThis && !supportedPrimitives.contains(PointcutPrimitive.THIS)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.THIS);
            } else if (!supportedPrimitives.contains(PointcutPrimitive.TARGET)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.TARGET);
            }
            break;
        case Pointcut.WITHIN:
            if (!supportedPrimitives.contains(PointcutPrimitive.WITHIN)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.WITHIN);
            }
            break;
        case Pointcut.WITHINCODE:
            if (!supportedPrimitives.contains(PointcutPrimitive.WITHIN_CODE)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.WITHIN_CODE);
            }
            break;
        case Pointcut.ATTHIS_OR_TARGET:
            isThis = ((ThisOrTargetAnnotationPointcut) pointcut).isThis();
            if (isThis && !supportedPrimitives.contains(PointcutPrimitive.AT_THIS)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.AT_THIS);
            } else if (!supportedPrimitives.contains(PointcutPrimitive.AT_TARGET)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.AT_TARGET);
            }
            break;
        case Pointcut.ATARGS:
            if (!supportedPrimitives.contains(PointcutPrimitive.AT_ARGS)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.AT_ARGS);
            }
            break;
        case Pointcut.ANNOTATION:
            if (!supportedPrimitives.contains(PointcutPrimitive.AT_ANNOTATION)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.AT_ANNOTATION);
            }
            break;
        case Pointcut.ATWITHIN:
            if (!supportedPrimitives.contains(PointcutPrimitive.AT_WITHIN)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.AT_WITHIN);
            }
            break;
        case Pointcut.ATWITHINCODE:
            if (!supportedPrimitives.contains(PointcutPrimitive.AT_WITHINCODE)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.AT_WITHINCODE);
            }
            break;
        case Pointcut.REFERENCE:
            if (!supportedPrimitives.contains(PointcutPrimitive.REFERENCE)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.REFERENCE);
            }
            break;
        case Pointcut.USER_EXTENSION:
            // always ok...
            break;
        case Pointcut.NONE: // deliberate fall-through
        default:
            throw new IllegalArgumentException("Unknown pointcut kind: " + pointcut.getPointcutKind());
        }
    }

    private void validateKindedPointcut(KindedPointcut pointcut, String pointcutExpression) {
        Shadow.Kind kind = pointcut.getKind();
        if ((kind == Shadow.MethodCall) || (kind == Shadow.ConstructorCall)) {
            if (!supportedPrimitives.contains(PointcutPrimitive.CALL)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.CALL);
            }
        } else if ((kind == Shadow.MethodExecution) || (kind == Shadow.ConstructorExecution)) {
            if (!supportedPrimitives.contains(PointcutPrimitive.EXECUTION)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.EXECUTION);
            }
        } else if (kind == Shadow.AdviceExecution) {
            if (!supportedPrimitives.contains(PointcutPrimitive.ADVICE_EXECUTION)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.ADVICE_EXECUTION);
            }
        } else if (kind == Shadow.FieldGet) {
            if (!supportedPrimitives.contains(PointcutPrimitive.GET)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.GET);
            }
        } else if (kind == Shadow.FieldSet) {
            if (!supportedPrimitives.contains(PointcutPrimitive.SET)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.SET);
            }
        } else if (kind == Shadow.Initialization) {
            if (!supportedPrimitives.contains(PointcutPrimitive.INITIALIZATION)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.INITIALIZATION);
            }
        } else if (kind == Shadow.PreInitialization) {
            if (!supportedPrimitives.contains(PointcutPrimitive.PRE_INITIALIZATION)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.PRE_INITIALIZATION);
            }
        } else if (kind == Shadow.StaticInitialization) {
            if (!supportedPrimitives.contains(PointcutPrimitive.STATIC_INITIALIZATION)) {
                throw new UnsupportedPointcutPrimitiveException(pointcutExpression, PointcutPrimitive.STATIC_INITIALIZATION);
            }
        }
    }

}
