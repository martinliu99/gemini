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

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aspectj.bridge.IMessageHandler;
import org.aspectj.bridge.ISourceLocation;
import org.aspectj.bridge.SourceLocation;
import org.aspectj.weaver.BindingScope;
import org.aspectj.weaver.IHasPosition;
import org.aspectj.weaver.ISourceContext;
import org.aspectj.weaver.IntMap;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.patterns.AndPointcut;
import org.aspectj.weaver.patterns.CflowPointcut;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.KindedPointcut;
import org.aspectj.weaver.patterns.NotPointcut;
import org.aspectj.weaver.patterns.OrPointcut;
import org.aspectj.weaver.patterns.ParserException;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.SimpleScope;
import org.aspectj.weaver.patterns.ThisOrTargetAnnotationPointcut;
import org.aspectj.weaver.patterns.ThisOrTargetPointcut;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.aspectj.weaver.tools.PointcutPrimitive;
import org.aspectj.weaver.tools.UnsupportedPointcutPrimitiveException;

import io.gemini.aspectj.weaver.Expression.PointcutExpr;
import io.gemini.aspectj.weaver.TypeWorld;
import io.gemini.aspectj.weaver.patterns.PatternParserV2;
import io.gemini.core.pool.TypePools.HybridResolutionTypePool;
import io.gemini.core.pool.TypePools.ResolutionType;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.type.TypeDescription;


public class PointcutParser {

    private TypeWorld typeWorld;
    private final Set<PointcutPrimitive> supportedPrimitives;


    protected static Set<PointcutPrimitive> getAllSupportedPointcutPrimitives() {
        Set<PointcutPrimitive> primitives = new HashSet<>();
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


    /**
     * Returns a pointcut parser that can parse the full AspectJ pointcut language with the following exceptions:
     * <ul>
     * <li>The <code>if, cflow, and cflowbelow</code> pointcut designators are not supported
     * <li>Pointcut expressions must be self-contained :- they cannot contain references to other named pointcuts
     * <li>The pointcut expression must be anonymous with no formals allowed.
     * </ul>
     * <p>
     * When resolving types in pointcut expressions, the given classloader is used to find types.
     * </p>
     */
    public static PointcutParser createPointcutParser(TypeWorld typeWorld) {
        return new PointcutParser(getAllSupportedPointcutPrimitives(), typeWorld);
    }

    /**
     * Returns a pointcut parser that can parse pointcut expressions built from a user-defined subset of AspectJ's supported
     * pointcut primitives. The following restrictions apply:
     * <ul>
     * <li>The <code>if, cflow, and cflowbelow</code> pointcut designators are not supported
     * <li>Pointcut expressions must be self-contained :- they cannot contain references to other named pointcuts
     * <li>The pointcut expression must be anonymous with no formals allowed.
     * </ul>
     * <p>
     * When resolving types in pointcut expressions, the given classloader is used to find types.
     * </p>
     * 
     * @param supportedPointcutKinds a set of PointcutPrimitives this parser should support
     * @throws UnsupportedOperationException if the set contains if, cflow, or cflow below
     */
    public static PointcutParser createPointcutParser(Set<PointcutPrimitive> supportedPointcutKinds, TypeWorld typeWorld) {
        return new PointcutParser(supportedPointcutKinds, typeWorld);
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
    protected PointcutParser(Set<PointcutPrimitive> supportedPointcutKinds, TypeWorld typeWorld) {
        supportedPrimitives = supportedPointcutKinds;
        for (PointcutPrimitive pointcutPrimitive : supportedPointcutKinds) {
            if ((pointcutPrimitive == PointcutPrimitive.IF) || (pointcutPrimitive == PointcutPrimitive.CFLOW)
                    || (pointcutPrimitive == PointcutPrimitive.CFLOW_BELOW)) {
                throw new UnsupportedOperationException("Cannot handle if, cflow, and cflowbelow primitives");
            }
        }

        this.typeWorld = typeWorld;
    }

    /**
     * Parse the given pointcut expression. A global scope is assumed for resolving any type references, and the pointcut must
     * contain no formals (variables to be bound).
     * 
     * @throws UnsupportedPointcutPrimitiveException if the parser encounters a primitive pointcut expression of a kind not
     *         supported by this PointcutParser.
     * @throws IllegalArgumentException if the expression is not a well-formed pointcut expression
     */
    public PointcutExpr parsePointcutExpression(String expression) throws UnsupportedPointcutPrimitiveException,
            IllegalArgumentException {
        return parsePointcutExpression(expression, null, Collections.emptyMap());
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
    public PointcutExpr parsePointcutExpression(String expression, TypeDescription inScope, Map<String, TypeDescription> formalParameters)
            throws UnsupportedPointcutPrimitiveException, IllegalArgumentException {
        PointcutExpr pcExpr = null;
        try {
            Pointcut pc = resolvePointcutExpression(expression, inScope, formalParameters);

            pc = concretizePointcutExpression(pc, inScope, formalParameters);
            validateAgainstSupportedPrimitives(pc, expression); // again, because we have now followed any ref'd pcuts

            pcExpr = new PointcutExpr.Default(pc, expression, formalParameters, getTypeWorld());
        } catch (ParserException pEx) {
            throw new IllegalArgumentException(buildUserMessageFromParserException(expression, pEx));
        } catch (ReflectionWorld.ReflectionWorldException rwEx) {
            throw new IllegalArgumentException(rwEx.getMessage());
        }
        return pcExpr;
    }

    public Pointcut parsePointcut(String expression, TypeDescription inScope, Map<String, TypeDescription> formalParameters) {
        ResolutionType currentResolutionType = HybridResolutionTypePool.getResolutionType();
        try {
            HybridResolutionTypePool.setResolutionType(ResolutionType.EAGER);

            Pointcut pc = resolvePointcutExpression(expression, inScope, formalParameters);

            pc = concretizePointcutExpression(pc, inScope, formalParameters);
            validateAgainstSupportedPrimitives(pc, expression); // again, because we have now followed any ref'd pcuts

            return pc;
        } catch (ParserException pEx) {
            throw new IllegalArgumentException(buildUserMessageFromParserException(expression, pEx));
        } catch (ReflectionWorld.ReflectionWorldException rwEx) {
            throw new IllegalArgumentException(rwEx.getMessage());
        } finally {
            HybridResolutionTypePool.setResolutionType(currentResolutionType);
        }
    }

    protected Pointcut resolvePointcutExpression(String expression, TypeDescription inScope, Map<String, TypeDescription> formalParameters) {
        try {
            expression = replaceBooleanOperators(expression);
            PatternParser parser = new PatternParserV2(expression);

            Pointcut pc = parser.parsePointcut(true);
            validateAgainstSupportedPrimitives(pc, expression);

            IScope resolutionScope = buildResolutionScope(inScope, formalParameters);
            pc = pc.resolve(resolutionScope);
            return pc;
        } catch (ParserException pEx) {
            throw new IllegalArgumentException(buildUserMessageFromParserException(expression, pEx), pEx);
        }
    }

    /**
     * If a pointcut expression has been specified in XML, the user cannot
     * write {@code and} as "&&" (though &amp;&amp; will work).
     * We also allow {@code and} between two pointcut sub-expressions.
     * <p>This method converts back to {@code &&} for the AspectJ pointcut parser.
     */
    private String replaceBooleanOperators(String pcExpr) {
        String result = StringUtils.replace(pcExpr, " and ", " && ");
        result = StringUtils.replace(result, " or ", " || ");
        result = StringUtils.replace(result, " not ", " ! ");
        return result;
    }

    protected Pointcut concretizePointcutExpression(Pointcut pc, TypeDescription inScope, Map<String, TypeDescription> formalParameters) {
        ResolvedType declaringTypeForResolution = null;
        if (inScope != null) {
            declaringTypeForResolution = getTypeWorld().resolve(inScope.getName());
        } else {
            declaringTypeForResolution = getTypeWorld().getWorld().resolve(ResolvedType.OBJECT);
        }
        IntMap arity = new IntMap(formalParameters.size());
        for (int i = 0; i < formalParameters.size(); i++) {
            arity.put(i, i);
        }
        return pc.concretize(declaringTypeForResolution, declaringTypeForResolution, arity);
    }

    private TypeWorld getTypeWorld() {
        return typeWorld;
    }

    /* for testing */
    Set<PointcutPrimitive> getSupportedPrimitives() {
        return supportedPrimitives;
    }

    /* for testing */
    IMessageHandler setCustomMessageHandler(IMessageHandler aHandler) {
        IMessageHandler current = getTypeWorld().getWorld().getMessageHandler();
        getTypeWorld().getWorld().setMessageHandler(aHandler);
        return current;
    }

    private IScope buildResolutionScope(TypeDescription inScope, Map<String, TypeDescription> formalParameters) {
        if (formalParameters == null) {
            formalParameters = Collections.emptyMap();
        }

        FormalBinding[] formalBindings = new FormalBinding[formalParameters.size()];
        int i = 0;
        for(Entry<String, TypeDescription> entry : formalParameters.entrySet()) {
            formalBindings[i] = new FormalBinding.ImplicitFormalBinding(toUnresolvedType(entry.getValue()), entry.getKey(), i++);
        }

        if (inScope == null) {
            return new SimpleScope(getTypeWorld().getWorld(), formalBindings);
        } else {
            ResolvedType inType = getTypeWorld().resolve(inScope);
            ISourceContext sourceContext = new ISourceContext() {
                public ISourceLocation makeSourceLocation(IHasPosition position) {
                    return new SourceLocation(new File(""), 0);
                }

                public ISourceLocation makeSourceLocation(int line, int offset) {
                    return new SourceLocation(new File(""), line);
                }

                public int getOffset() {
                    return 0;
                }

                public void tidy() {
                }
            };
            return new BindingScope(inType, sourceContext, formalBindings);
        }
    }

    private UnresolvedType toUnresolvedType(TypeDescription clazz) {
        if (clazz.isArray()) {
            return UnresolvedType.forSignature(clazz.getName().replace('.', '/'));
        } else {
            return UnresolvedType.forName(clazz.getName());
        }
    }

    private void validateAgainstSupportedPrimitives(Pointcut pc, String expression) {
        switch (pc.getPointcutKind()) {
        case Pointcut.AND:
            validateAgainstSupportedPrimitives(((AndPointcut) pc).getLeft(), expression);
            validateAgainstSupportedPrimitives(((AndPointcut) pc).getRight(), expression);
            break;
        case Pointcut.ARGS:
            if (!supportedPrimitives.contains(PointcutPrimitive.ARGS)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.ARGS);
            }
            break;
        case Pointcut.CFLOW:
            CflowPointcut cfp = (CflowPointcut) pc;
            if (cfp.isCflowBelow()) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.CFLOW_BELOW);
            } else {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.CFLOW);
            }
        case Pointcut.HANDLER:
            if (!supportedPrimitives.contains(PointcutPrimitive.HANDLER)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.HANDLER);
            }
            break;
        case Pointcut.IF:
        case Pointcut.IF_FALSE:
        case Pointcut.IF_TRUE:
            throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.IF);
        case Pointcut.KINDED:
            validateKindedPointcut(((KindedPointcut) pc), expression);
            break;
        case Pointcut.NOT:
            validateAgainstSupportedPrimitives(((NotPointcut) pc).getNegatedPointcut(), expression);
            break;
        case Pointcut.OR:
            validateAgainstSupportedPrimitives(((OrPointcut) pc).getLeft(), expression);
            validateAgainstSupportedPrimitives(((OrPointcut) pc).getRight(), expression);
            break;
        case Pointcut.THIS_OR_TARGET:
            boolean isThis = ((ThisOrTargetPointcut) pc).isThis();
            if (isThis && !supportedPrimitives.contains(PointcutPrimitive.THIS)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.THIS);
            } else if (!supportedPrimitives.contains(PointcutPrimitive.TARGET)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.TARGET);
            }
            break;
        case Pointcut.WITHIN:
            if (!supportedPrimitives.contains(PointcutPrimitive.WITHIN)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.WITHIN);
            }
            break;
        case Pointcut.WITHINCODE:
            if (!supportedPrimitives.contains(PointcutPrimitive.WITHIN_CODE)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.WITHIN_CODE);
            }
            break;
        case Pointcut.ATTHIS_OR_TARGET:
            isThis = ((ThisOrTargetAnnotationPointcut) pc).isThis();
            if (isThis && !supportedPrimitives.contains(PointcutPrimitive.AT_THIS)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.AT_THIS);
            } else if (!supportedPrimitives.contains(PointcutPrimitive.AT_TARGET)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.AT_TARGET);
            }
            break;
        case Pointcut.ATARGS:
            if (!supportedPrimitives.contains(PointcutPrimitive.AT_ARGS)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.AT_ARGS);
            }
            break;
        case Pointcut.ANNOTATION:
            if (!supportedPrimitives.contains(PointcutPrimitive.AT_ANNOTATION)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.AT_ANNOTATION);
            }
            break;
        case Pointcut.ATWITHIN:
            if (!supportedPrimitives.contains(PointcutPrimitive.AT_WITHIN)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.AT_WITHIN);
            }
            break;
        case Pointcut.ATWITHINCODE:
            if (!supportedPrimitives.contains(PointcutPrimitive.AT_WITHINCODE)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.AT_WITHINCODE);
            }
            break;
        case Pointcut.REFERENCE:
            if (!supportedPrimitives.contains(PointcutPrimitive.REFERENCE)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.REFERENCE);
            }
            break;
        case Pointcut.USER_EXTENSION:
            // always ok...
            break;
        case Pointcut.NONE: // deliberate fall-through
        default:
            throw new IllegalArgumentException("Unknown pointcut kind: " + pc.getPointcutKind());
        }
    }

    private void validateKindedPointcut(KindedPointcut pc, String expression) {
        Shadow.Kind kind = pc.getKind();
        if ((kind == Shadow.MethodCall) || (kind == Shadow.ConstructorCall)) {
            if (!supportedPrimitives.contains(PointcutPrimitive.CALL)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.CALL);
            }
        } else if ((kind == Shadow.MethodExecution) || (kind == Shadow.ConstructorExecution)) {
            if (!supportedPrimitives.contains(PointcutPrimitive.EXECUTION)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.EXECUTION);
            }
        } else if (kind == Shadow.AdviceExecution) {
            if (!supportedPrimitives.contains(PointcutPrimitive.ADVICE_EXECUTION)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.ADVICE_EXECUTION);
            }
        } else if (kind == Shadow.FieldGet) {
            if (!supportedPrimitives.contains(PointcutPrimitive.GET)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.GET);
            }
        } else if (kind == Shadow.FieldSet) {
            if (!supportedPrimitives.contains(PointcutPrimitive.SET)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.SET);
            }
        } else if (kind == Shadow.Initialization) {
            if (!supportedPrimitives.contains(PointcutPrimitive.INITIALIZATION)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.INITIALIZATION);
            }
        } else if (kind == Shadow.PreInitialization) {
            if (!supportedPrimitives.contains(PointcutPrimitive.PRE_INITIALIZATION)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.PRE_INITIALIZATION);
            }
        } else if (kind == Shadow.StaticInitialization) {
            if (!supportedPrimitives.contains(PointcutPrimitive.STATIC_INITIALIZATION)) {
                throw new UnsupportedPointcutPrimitiveException(expression, PointcutPrimitive.STATIC_INITIALIZATION);
            }
        }
    }

    private String buildUserMessageFromParserException(String pc, ParserException ex) {
        StringBuffer msg = new StringBuffer();
        msg.append("Expression is not well-formed: expecting '");
        msg.append(ex.getMessage());
        msg.append("'");
        IHasPosition location = ex.getLocation();
        msg.append(" at character position ");
        msg.append(location.getStart());
        msg.append("\n");
        msg.append(pc);
        msg.append("\n");
        for (int i = 0; i < location.getStart(); i++) {
            msg.append(" ");
        }
        for (int j = location.getStart(); j <= location.getEnd(); j++) {
            msg.append("^");
        }
        msg.append("\n");
        return msg.toString();
    }
}
