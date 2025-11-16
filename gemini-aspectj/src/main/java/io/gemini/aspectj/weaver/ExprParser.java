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
/**
 * 
 */
package io.gemini.aspectj.weaver;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aspectj.bridge.ISourceLocation;
import org.aspectj.bridge.SourceLocation;
import org.aspectj.weaver.BindingScope;
import org.aspectj.weaver.IHasPosition;
import org.aspectj.weaver.ISourceContext;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.patterns.Bindings;
import org.aspectj.weaver.patterns.ExactTypePattern;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.ISignaturePattern;
import org.aspectj.weaver.patterns.ParserException;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.SimpleScope;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.tools.PointcutPrimitive;

import io.gemini.api.FrameworkException;
import io.gemini.aspectj.weaver.TypeWorld.WorldLintException;
import io.gemini.aspectj.weaver.patterns.HasPatternParser;
import io.gemini.aspectj.weaver.patterns.PatternParserV2;
import io.gemini.aspectj.weaver.patterns.TypeNamePatternParser;
import io.gemini.aspectj.weaver.world.ElementExpr.ClassLoaderExpr;
import io.gemini.aspectj.weaver.world.ElementExpr.ResourceNameExpr;
import io.gemini.aspectj.weaver.world.ElementExpr.TypeExpr;
import io.gemini.aspectj.weaver.world.ElementExpr.TypeNameExpr;
import io.gemini.aspectj.weaver.world.PointcutParser;
import io.gemini.core.util.Assert;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * 
 */
public enum ExprParser {

    INSTANCE;


    public ElementMatcher<ClassLoader> parseClassLoaderExpr(String classLoaderExpression)
            throws ExprParseException, ExprLintException, ExprUnknownException {
        classLoaderExpression = validateExpression(classLoaderExpression);

        try {
            classLoaderExpression = replaceBooleanOperators(classLoaderExpression);
            TypePattern typePattern = new TypeNamePatternParser(classLoaderExpression).parseTypePattern();

            return new ClassLoaderExpr(classLoaderExpression, typePattern);
        } catch (Exception e) {
            ExprParser.handleException(classLoaderExpression, e);
            return null;
        }
    }


    /**
     * @param expression
     * @return
     */
    private String validateExpression(String expression) {
        Assert.hasText(expression, "'expression' must not be empty");
        return expression.trim();
    }


    public ElementMatcher<String> parseTypeNameExpr(String typeNameExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        typeNameExpression = validateExpression(typeNameExpression);

        try {
            typeNameExpression = replaceBooleanOperators(typeNameExpression);
            TypePattern typePattern = new TypeNamePatternParser(typeNameExpression).parseTypePattern();

            return new TypeNameExpr(typeNameExpression, typePattern);
        } catch (Exception e) {
            ExprParser.handleException(typeNameExpression, e);
            return null;
        }
    }


    public ElementMatcher<String> parseResourceNameExpr(String resourceNameExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        resourceNameExpression = validateExpression(resourceNameExpression);

        try {
            resourceNameExpression = formatExpression(resourceNameExpression);
            resourceNameExpression = replaceBooleanOperators(resourceNameExpression);
            TypePattern typePattern = new TypeNamePatternParser(resourceNameExpression).parseTypePattern();

            return new ResourceNameExpr(resourceNameExpression, typePattern);
        } catch (Exception e) {
            ExprParser.handleException(resourceNameExpression, e);
            return null;
        }
    }

    private String formatExpression(String expression) {
        return expression == null ? "" : expression.replace("/", ".");
    }


    public ElementMatcher<TypeDescription> parseTypeExpr(TypeWorld typeWorld, String typeExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        typeExpression = validateExpression(typeExpression);

        try {
            typeExpression = replaceBooleanOperators(typeExpression);
            TypePattern typePattern = new PatternParserV2(typeExpression).parseTypePattern();

            IScope scope = new SimpleScope(typeWorld.getWorld(), new FormalBinding[0]);
            typePattern = typePattern.resolveBindings(scope, Bindings.NONE, false, false);

            return new TypeExpr(typeExpression, typeWorld, typePattern);
        } catch (Exception e) {
            ExprParser.handleException(typeExpression, e);
            return null;
        }
    }


    public Pointcut parsePointcutExpr(TypeWorld typeWorld, String pointcutExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        pointcutExpression = validateExpression(pointcutExpression);

        return new PointcutParser(typeWorld).parsePointcut(pointcutExpression);
    }

    public Pointcut parsePointcutExpr(TypeWorld typeWorld, Set<PointcutPrimitive> supportedPointcutKinds, String pointcutExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        pointcutExpression = validateExpression(pointcutExpression);

        return new PointcutParser(typeWorld, supportedPointcutKinds).parsePointcut(pointcutExpression);
    }

    public Pointcut parsePointcutExpr(TypeWorld typeWorld, 
            String pointcutExpression, TypeDescription pointcutDeclarationScope, 
            Map<String, ? extends TypeDefinition> pointcutParameters) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        pointcutExpression = validateExpression(pointcutExpression);

        return new PointcutParser(typeWorld).parsePointcut(pointcutExpression, pointcutDeclarationScope, pointcutParameters);
    }

    public Pointcut parsePointcutExpr(TypeWorld typeWorld, Set<PointcutPrimitive> supportedPointcutKinds, 
            String pointcutExpression, TypeDescription pointcutDeclarationScope, 
            Map<String, ? extends TypeDefinition> pointcutParameters) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        pointcutExpression = validateExpression(pointcutExpression);

        return new PointcutParser(typeWorld, supportedPointcutKinds).parsePointcut(pointcutExpression, pointcutDeclarationScope, pointcutParameters);
    }


    public boolean hasType(TypeWorld typeWorld, String typeExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        typeExpression = validateExpression(typeExpression);

        try {
            typeExpression = replaceBooleanOperators(typeExpression);
            TypePattern typePattern = new HasPatternParser(typeExpression).parseTypePattern();

            IScope resolutionScope = buildResolutionScope(typeWorld, TypeDescription.ForLoadedType.of(Object.class), Collections.emptyMap());
            Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
            typePattern = typePattern.resolveBindings(resolutionScope, bindingTable, false, false);

            return typePattern.matchesStatically(null);
        } catch (Exception e) {
            ExprParser.handleException(typeExpression, e);
            return false;
        }
    }

    public boolean hasField(TypeWorld typeWorld, String fieldExpression)
            throws ExprParseException, ExprLintException, ExprUnknownException {
        fieldExpression = validateExpression(fieldExpression);

        try {
            fieldExpression = replaceBooleanOperators(fieldExpression);
            ISignaturePattern signaturePattern = new HasPatternParser(fieldExpression).parseCompoundFieldSignaturePattern();

            IScope resolutionScope = buildResolutionScope(typeWorld, TypeDescription.ForLoadedType.of(Object.class), Collections.emptyMap());
            Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
            signaturePattern = signaturePattern.resolveBindings(resolutionScope, bindingTable);

            return signaturePattern.matches(null, typeWorld.getWorld(), true);
        } catch (Exception e) {
            ExprParser.handleException(fieldExpression, e);
            return false;
        }
    }


    public boolean hasConstructor(TypeWorld typeWorld, String constructorExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        return hasMethodOrConstructor(typeWorld, constructorExpression, false);
    }

    public boolean hasMethod(TypeWorld typeWorld, String methodExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        return hasMethodOrConstructor(typeWorld, methodExpression, true);
    }

    private boolean hasMethodOrConstructor(TypeWorld typeWorld, String expression, boolean isMethod) {
        try {
            expression = replaceBooleanOperators(expression);
            ISignaturePattern signaturePattern = new HasPatternParser(expression).parseCompoundMethodOrConstructorSignaturePattern(isMethod);

            IScope resolutionScope = buildResolutionScope(typeWorld, TypeDescription.ForLoadedType.of(Object.class), Collections.emptyMap());
            Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
            signaturePattern = signaturePattern.resolveBindings(resolutionScope, bindingTable);

            return signaturePattern.matches(null, typeWorld.getWorld(), true);
        } catch (Exception e) {
            ExprParser.handleException(expression, e);
            return false;
        }
    }


    public MethodDescription findMethod(TypeWorld typeWorld, String methodExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        try {
            methodExpression = replaceBooleanOperators(methodExpression);
            ISignaturePattern signaturePattern = new PatternParserV2(methodExpression).parseMethodOrConstructorSignaturePattern();

            IScope resolutionScope = buildResolutionScope(typeWorld, TypeDescription.ForLoadedType.of(Object.class), Collections.emptyMap());
            Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
            signaturePattern = signaturePattern.resolveBindings(resolutionScope, bindingTable);

            List<ExactTypePattern> exactTypePatterns = signaturePattern.getExactDeclaringTypes();
            Assert.isTrue(exactTypePatterns != null && exactTypePatterns.size() == 1, 
                    "Only one signature should be defined in " + methodExpression);

            ResolvedType resolvedType = exactTypePatterns.get(0).getResolvedExactType(typeWorld.getWorld());
            TypeDescription typeDescription = typeWorld.describeType(resolvedType.getName());

            for (MethodDescription methodDescription : MethodUtils.getAllMethodDescriptions(typeDescription)) {
                if (signaturePattern.matches(typeWorld.resolve(methodDescription), typeWorld.getWorld(), false))
                    return methodDescription;
            }
            return null;
        } catch (Exception e) {
            ExprParser.handleException(methodExpression, e);
            return null;
        }
    }


    /**
     * If a pointcut expression has been specified in XML, the user cannot
     * write {@code and} as "&&" (though &amp;&amp; will work).
     * We also allow {@code and} between two pointcut sub-expressions.
     * <p>This method converts back to {@code &&} for the AspectJ pointcut parser.
     */
    public static String replaceBooleanOperators(String pointcutExpression) {
        String result = StringUtils.replace(pointcutExpression, " and ", " && ");
        result = StringUtils.replace(result, " or ", " || ");
        result = StringUtils.replace(result, " not ", " ! ");
        return result;
    }

    public static IScope buildResolutionScope(TypeWorld typeWorld, 
            TypeDescription pointcutDeclarationScope, Map<String, ? extends TypeDefinition> pointcutParameters) {
        if (pointcutParameters == null) {
            pointcutParameters = Collections.emptyMap();
        }

        FormalBinding[] formalBindings = new FormalBinding[pointcutParameters.size()];
        int i = 0;
        for (Entry<String, ? extends TypeDefinition> entry : pointcutParameters.entrySet()) {
            formalBindings[i] = new FormalBinding.ImplicitFormalBinding(toUnresolvedType(entry.getValue()), entry.getKey(), i++);
        }

        if (pointcutDeclarationScope == null) {
            return new SimpleScope(typeWorld.getWorld(), formalBindings);
        } else {
            ResolvedType inType = typeWorld.resolve(pointcutDeclarationScope);
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


    private static UnresolvedType toUnresolvedType(TypeDefinition typeDefinition) {
        if (typeDefinition.isArray()) {
            return UnresolvedType.forSignature(typeDefinition.getTypeName().replace('.', '/'));
        } else {
            return UnresolvedType.forName(typeDefinition.getTypeName());
        }
    }

    public static void handleException(String expression, Exception exp) {
        if (exp instanceof ParserException) {
            throw new ExprParseException(expression, (ParserException) exp);
        } else if (exp instanceof TypeWorld.WorldLintException) {
            throw new ExprLintException(expression, (TypeWorld.WorldLintException) exp);
        } else if (exp instanceof ExprParseException || exp instanceof ExprLintException 
                || exp instanceof ExprUnknownException || exp instanceof RuntimeException) {
            throw (RuntimeException) exp;
        }else {
            throw new ExprUnknownException(expression, exp);
        }
    }


    public static class ExprParseException extends FrameworkException {

        private static final long serialVersionUID = -7800478366126303384L;

        private final String expression;


        public ExprParseException(String expression, ParserException cause) {
            super(buildUserMessageFromParserException(expression, cause), cause);

            this.expression = expression;
        }

        private static String buildUserMessageFromParserException(String expression, ParserException ex) {
            StringBuffer msg = new StringBuffer();

            msg.append("Expression is not well-formed: expecting '");
            msg.append(ex.getMessage());
            msg.append("'");

            IHasPosition location = ex.getLocation();
            msg.append(" at character position ");
            msg.append(location.getStart());
            msg.append("\n");
            msg.append(expression);
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

        public String getExpression() {
            return expression;
        }
    }


    public static class ExprLintException extends FrameworkException {

        private static final long serialVersionUID = -7800478366126303384L;

        private final String expression;


        public ExprLintException(String expression, WorldLintException cause) {
            super(cause.getMessage(), cause);

            this.expression = expression;
        }

        public String getExpression() {
            return expression;
        }
    }


    public static class ExprUnknownException extends FrameworkException {

        private static final long serialVersionUID = 816600136638029684L;

        private final String expression;


        public ExprUnknownException(String expression, Throwable cause) {
            super(cause);

            this.expression = expression;
        }

        public String getExpression() {
            return expression;
        }
    }
}
