/*
 * Copyright © 2023 - present, the original author or authors. All Rights Reserved.
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

import io.gemini.api.BaseException;
import io.gemini.aspectj.weaver.TypeWorld.WorldLintException;
import io.gemini.aspectj.weaver.patterns.HasPatternParser;
import io.gemini.aspectj.weaver.patterns.PatternParserV2;
import io.gemini.aspectj.weaver.patterns.TypeNamePatternParser;
import io.gemini.aspectj.weaver.world.ElementExpr.ClassLoaderExpr;
import io.gemini.aspectj.weaver.world.ElementExpr.ResourceNameExpr;
import io.gemini.aspectj.weaver.world.ElementExpr.TypeExpr;
import io.gemini.aspectj.weaver.world.ElementExpr.TypeNameExpr;
import io.gemini.aspectj.weaver.world.PointcutParser;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.util.Assert;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Central expression parser for the Gemini AOP framework, bridging ByteBuddy matchers
 * and AspectJ pattern parsing.
 * <p>
 * Provides methods to parse:
 * <ul>
 *   <li>Class loader name expressions → {@link ElementMatcher}&lt;ClassLoader&gt;</li>
 *   <li>Type name expressions → {@link ElementMatcher}&lt;String&gt;</li>
 *   <li>Resource name expressions → {@link ElementMatcher}&lt;String&gt;</li>
 *   <li>Type expressions → {@link ElementMatcher}&lt;TypeDescription&gt;</li>
 *   <li>AspectJ pointcut expressions → {@link org.aspectj.weaver.patterns.Pointcut}</li>
 *   <li>Structural presence checks (hasType, hasField, hasConstructor, hasMethod)</li>
 * </ul>
 * Also provides boolean operator normalization ({@code and}/{@code or}/{@code not} → {@code &&}/{@code ||}/{@code !}).
 * </p>
 *
 * @author   martin.liu
 */
public enum ExprParser {

    INSTANCE;


    /**
     * Parses a class loader name expression into a ByteBuddy {@link ElementMatcher} for {@link ClassLoader}.
     *
     * @param classLoaderExpression the expression (e.g., {@code "org.springframework.*"})
     * @return a matcher that tests class loader class names
     * @throws ExprParseException   if the expression has a syntax error
     * @throws ExprLintException    if the expression has a lint warning
     * @throws ExprUnknownException if an unexpected error occurs
     */
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

    private String validateExpression(String expression) {
        Assert.hasText(expression, "'expression' must not be empty");
        return expression.trim();
    }


    /**
     * Parses a type name expression into a ByteBuddy {@link ElementMatcher} for {@link String} type names.
     *
     * @param typeNameExpression the expression (e.g., {@code "com.example.*"})
     * @return a matcher that tests fully-qualified type names
     * @throws ExprParseException   if the expression has a syntax error
     * @throws ExprLintException    if the expression has a lint warning
     * @throws ExprUnknownException if an unexpected error occurs
     */
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


    /**
     * Parses a resource name expression into a ByteBuddy {@link ElementMatcher} for resource path strings.
     * Slashes in the expression are converted to dots before parsing.
     *
     * @param resourceNameExpression the expression (e.g., {@code "com/example/*"})
     * @return a matcher that tests resource path strings
     * @throws ExprParseException   if the expression has a syntax error
     * @throws ExprLintException    if the expression has a lint warning
     * @throws ExprUnknownException if an unexpected error occurs
     */
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


    /**
     * Parses a type expression into a ByteBuddy {@link ElementMatcher} for {@link TypeDescription}.
     *
     * @param typeWorld      the type world for resolving type bindings
     * @param typeExpression the AspectJ type pattern expression
     * @return a matcher that tests type descriptions
     * @throws ExprParseException   if the expression has a syntax error
     * @throws ExprLintException    if the expression has a lint warning
     * @throws ExprUnknownException if an unexpected error occurs
     */
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


    /**
     * Parses an AspectJ pointcut expression using the default supported primitives.
     *
     * @param typeWorld          the type world for resolving types
     * @param pointcutExpression the AspectJ pointcut expression string
     * @return the resolved and concretized {@link Pointcut}
     * @throws ExprParseException   if the expression has a syntax error
     * @throws ExprLintException    if the expression has a lint warning
     * @throws ExprUnknownException if an unexpected error occurs
     */
    public Pointcut parsePointcutExpr(TypeWorld typeWorld, String pointcutExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        pointcutExpression = validateExpression(pointcutExpression);

        return new PointcutParser(typeWorld).parsePointcut(pointcutExpression);
    }

    /**
     * Parses an AspectJ pointcut expression with a specific set of supported primitives.
     *
     * @param typeWorld              the type world for resolving types
     * @param supportedPointcutKinds the set of allowed pointcut primitives
     * @param pointcutExpression     the AspectJ pointcut expression string
     * @return the resolved and concretized {@link Pointcut}
     */
    public Pointcut parsePointcutExpr(TypeWorld typeWorld, Set<PointcutPrimitive> supportedPointcutKinds, String pointcutExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        pointcutExpression = validateExpression(pointcutExpression);

        return new PointcutParser(typeWorld, supportedPointcutKinds).parsePointcut(pointcutExpression);
    }

    /**
     * Parses an AspectJ pointcut expression scoped to a declaring type with parameter bindings.
     *
     * @param typeWorld                  the type world for resolving types
     * @param pointcutExpression         the AspectJ pointcut expression string
     * @param pointcutDeclarationScope   the declaring type scope (used for reference pointcuts)
     * @param pointcutParameters         map of parameter name to type definition
     * @return the resolved and concretized {@link Pointcut}
     */
    public Pointcut parsePointcutExpr(TypeWorld typeWorld, 
            String pointcutExpression, TypeDescription pointcutDeclarationScope, 
            Map<String, ? extends TypeDefinition> pointcutParameters) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        pointcutExpression = validateExpression(pointcutExpression);
        pointcutDeclarationScope = pointcutDeclarationScope == null
                ? TypeDescription.ForLoadedType.of(Object.class) : pointcutDeclarationScope;

        String existingAspectType = ThreadContext.getContextAspectType();
        try {
            ThreadContext.setContextAspectType(pointcutDeclarationScope.getTypeName());
            ResolvedType pointcutDeclarationScopeType = typeWorld.resolve(pointcutDeclarationScope);

            return new PointcutParser(typeWorld).parsePointcut(
                    pointcutExpression, pointcutDeclarationScopeType, pointcutParameters);
        } finally {
            ThreadContext.setContextAspectType(existingAspectType);
        }
    }

    /**
     * Parses an AspectJ pointcut expression with a specific set of supported primitives,
     * scoped to a declaring type with parameter bindings.
     *
     * @param typeWorld                the type world for resolving types
     * @param supportedPointcutKinds   the set of allowed pointcut primitives
     * @param pointcutExpression       the AspectJ pointcut expression string
     * @param pointcutDeclarationType  the declaring type scope (used for reference pointcuts)
     * @param pointcutParameters       map of parameter name to type definition
     * @return the resolved and concretized {@link Pointcut}
     * @throws ExprParseException   if the expression has a syntax error
     * @throws ExprLintException    if the expression has a lint warning
     * @throws ExprUnknownException if an unexpected error occurs
     */
    public Pointcut parsePointcutExpr(TypeWorld typeWorld, Set<PointcutPrimitive> supportedPointcutKinds, 
            String pointcutExpression, TypeDescription pointcutDeclarationType, 
            Map<String, ? extends TypeDefinition> pointcutParameters) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        pointcutExpression = validateExpression(pointcutExpression);
        pointcutDeclarationType = pointcutDeclarationType == null
                ? TypeDescription.ForLoadedType.of(Object.class) : pointcutDeclarationType;

        String existingAspectType = ThreadContext.getContextAspectType();
        try {
            ThreadContext.setContextAspectType(pointcutDeclarationType.getTypeName());
            ResolvedType pointcutDeclarationScopeType = typeWorld.resolve(pointcutDeclarationType);

            return new PointcutParser(typeWorld, supportedPointcutKinds).parsePointcut(
                    pointcutExpression, pointcutDeclarationScopeType, pointcutParameters);
        } finally {
            ThreadContext.setContextAspectType(existingAspectType);
        }
    }


    /**
     * Returns {@code true} if the given type expression matches at least one type in the type world.
     *
     * @param typeWorld      the type world to search
     * @param typeExpression the AspectJ type pattern expression
     * @return {@code true} if a matching type exists
     */
    public boolean hasType(TypeWorld typeWorld, String typeExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        typeExpression = validateExpression(typeExpression);

        try {
            typeExpression = replaceBooleanOperators(typeExpression);
            TypePattern typePattern = new HasPatternParser(typeExpression).parseTypePattern();

            IScope resolutionScope = buildResolutionScope(typeWorld, null, Collections.emptyMap());
            Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
            typePattern = typePattern.resolveBindings(resolutionScope, bindingTable, false, false);

            return typePattern.matchesStatically(null);
        } catch (Exception e) {
            ExprParser.handleException(typeExpression, e);
            return false;
        }
    }

    /**
     * Returns {@code true} if the given field expression matches at least one field in the type world.
     *
     * @param typeWorld       the type world to search
     * @param fieldExpression the AspectJ field signature pattern expression
     * @return {@code true} if a matching field exists
     */
    public boolean hasField(TypeWorld typeWorld, String fieldExpression)
            throws ExprParseException, ExprLintException, ExprUnknownException {
        fieldExpression = validateExpression(fieldExpression);

        try {
            fieldExpression = replaceBooleanOperators(fieldExpression);
            ISignaturePattern signaturePattern = new HasPatternParser(fieldExpression).parseCompoundFieldSignaturePattern();

            IScope resolutionScope = buildResolutionScope(typeWorld, null, Collections.emptyMap());
            Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
            signaturePattern = signaturePattern.resolveBindings(resolutionScope, bindingTable);

            return signaturePattern.matches(null, typeWorld.getWorld(), true);
        } catch (Exception e) {
            ExprParser.handleException(fieldExpression, e);
            return false;
        }
    }


    /**
     * Returns {@code true} if the given constructor expression matches at least one constructor in the type world.
     *
     * @param typeWorld              the type world to search
     * @param constructorExpression  the AspectJ constructor signature pattern expression
     * @return {@code true} if a matching constructor exists
     */
    public boolean hasConstructor(TypeWorld typeWorld, String constructorExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        return hasMethodOrConstructor(typeWorld, constructorExpression, false);
    }

    /**
     * Returns {@code true} if the given method expression matches at least one method in the type world.
     *
     * @param typeWorld       the type world to search
     * @param methodExpression the AspectJ method signature pattern expression
     * @return {@code true} if a matching method exists
     */
    public boolean hasMethod(TypeWorld typeWorld, String methodExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        return hasMethodOrConstructor(typeWorld, methodExpression, true);
    }

    private boolean hasMethodOrConstructor(TypeWorld typeWorld, String expression, boolean isMethod) {
        try {
            expression = replaceBooleanOperators(expression);
            ISignaturePattern signaturePattern = new HasPatternParser(expression).parseCompoundMethodOrConstructorSignaturePattern(isMethod);

            IScope resolutionScope = buildResolutionScope(typeWorld, null, Collections.emptyMap());
            Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
            signaturePattern = signaturePattern.resolveBindings(resolutionScope, bindingTable);

            return signaturePattern.matches(null, typeWorld.getWorld(), true);
        } catch (Exception e) {
            ExprParser.handleException(expression, e);
            return false;
        }
    }


    /**
     * Finds and returns the {@link MethodDescription} matching the given method expression.
     *
     * @param typeWorld        the type world for resolving types
     * @param methodExpression the AspectJ method signature expression (must include declaring type)
     * @return the matching {@link MethodDescription}, or {@code null} if not found
     */
    public MethodDescription findMethod(TypeWorld typeWorld, String methodExpression) 
            throws ExprParseException, ExprLintException, ExprUnknownException {
        try {
            methodExpression = replaceBooleanOperators(methodExpression);
            ISignaturePattern signaturePattern = new PatternParserV2(methodExpression).parseMethodOrConstructorSignaturePattern();

            IScope resolutionScope = buildResolutionScope(typeWorld, null, Collections.emptyMap());
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

    /**
     * Builds an AspectJ {@link IScope} for resolving pointcut bindings.
     *
     * @param typeWorld                      the type world
     * @param pointcutDeclarationScopeType   the declaring type scope, or {@code null} for anonymous scope
     * @param pointcutParameters             map of parameter name to type definition
     * @return the resolution scope
     */
    public static IScope buildResolutionScope(TypeWorld typeWorld, 
            ResolvedType pointcutDeclarationScopeType, Map<String, ? extends TypeDefinition> pointcutParameters) {
        if (pointcutParameters == null) {
            pointcutParameters = Collections.emptyMap();
        }

        FormalBinding[] formalBindings = new FormalBinding[pointcutParameters.size()];
        int i = 0;
        for (Entry<String, ? extends TypeDefinition> entry : pointcutParameters.entrySet()) {
            formalBindings[i] = new FormalBinding.ImplicitFormalBinding(toUnresolvedType(entry.getValue()), entry.getKey(), i++);
        }

        if (pointcutDeclarationScopeType == null)
            return new SimpleScope(typeWorld.getWorld(), formalBindings);

        ISourceContext sourceContext = new ISourceContext() {

            @Override
            public ISourceLocation makeSourceLocation(IHasPosition position) {
                return new SourceLocation(new File(""), 0);
            }

            @Override
            public ISourceLocation makeSourceLocation(int line, int offset) {
                return new SourceLocation(new File(""), line);
            }

            @Override
            public int getOffset() {
                return 0;
            }

            @Override
            public void tidy() {
            }
        };
        return new BindingScope(pointcutDeclarationScopeType, sourceContext, formalBindings);
    }


    private static UnresolvedType toUnresolvedType(TypeDefinition typeDefinition) {
        if (typeDefinition.isArray()) {
            return UnresolvedType.forSignature(typeDefinition.getTypeName().replace('.', '/'));
        } else {
            return UnresolvedType.forName(typeDefinition.getTypeName());
        }
    }

    /**
     * Dispatches the given exception to the appropriate typed exception.
     * Converts {@link ParserException} to {@link ExprParseException},
     * {@link WorldLintException} to {@link ExprLintException}, and any other
     * exception to {@link ExprUnknownException}.
     *
     * @param expression the expression that caused the error
     * @param exp        the exception to handle
     * @throws ExprParseException   if {@code exp} is a {@link ParserException}
     * @throws ExprLintException    if {@code exp} is a {@link WorldLintException}
     * @throws ExprUnknownException for any other exception
     */
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


    /**
     * Thrown when an expression cannot be parsed due to a syntax error.
     */
    public static class ExprParseException extends BaseException {

        private static final long serialVersionUID = -7800478366126303384L;

        private final String expression;


        /**
         * Creates a new {@code ExprParseException} for the given expression and underlying parser error.
         *
         * @param expression the expression that failed to parse
         * @param cause      the underlying {@link ParserException}
         */
        public ExprParseException(String expression, ParserException cause) {
            super(buildUserMessageFromParserException(expression, cause), cause);

            this.expression = expression;
        }

        private static String buildUserMessageFromParserException(String expression, ParserException ex) {
            StringBuilder msg = new StringBuilder();

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

        /**
         * Returns the expression that caused this parse error.
         *
         * @return the original expression string
         */
        public String getExpression() {
            return expression;
        }
    }


    /**
     * Thrown when an expression triggers a lint warning that is treated as an error.
     */
    public static class ExprLintException extends BaseException {

        private static final long serialVersionUID = -7800478366126303384L;

        private final String expression;


        /**
         * Creates a new {@code ExprLintException} for the given expression and underlying lint error.
         *
         * @param expression the expression that triggered the lint warning
         * @param cause      the underlying {@link WorldLintException}
         */
        public ExprLintException(String expression, WorldLintException cause) {
            super(cause.getMessage(), cause);

            this.expression = expression;
        }

        /**
         * Returns the expression that caused this lint error.
         *
         * @return the original expression string
         */
        public String getExpression() {
            return expression;
        }
    }


    /**
     * Thrown when an expression fails for an unexpected or unknown reason.
     */
    public static class ExprUnknownException extends BaseException {

        private static final long serialVersionUID = 816600136638029684L;

        private final String expression;


        /**
         * Creates a new {@code ExprUnknownException} for the given expression and underlying cause.
         *
         * @param expression the expression that caused the error
         * @param cause      the underlying throwable
         */
        public ExprUnknownException(String expression, Throwable cause) {
            super(cause);

            this.expression = expression;
        }

        /**
         * Returns the expression that caused this error.
         *
         * @return the original expression string
         */
        public String getExpression() {
            return expression;
        }
    }
}
