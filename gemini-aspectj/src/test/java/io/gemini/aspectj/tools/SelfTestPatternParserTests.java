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
package io.gemini.aspectj.tools;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aspectj.bridge.IMessageHandler;
import org.aspectj.bridge.ISourceLocation;
import org.aspectj.bridge.SourceLocation;
import org.aspectj.weaver.BindingScope;
import org.aspectj.weaver.IHasPosition;
import org.aspectj.weaver.ISourceContext;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.bcel.BcelWorld;
import org.aspectj.weaver.patterns.Bindings;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.ISignaturePattern;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.SimpleScope;
import org.aspectj.weaver.tools.ContextBasedMatcher;
import org.aspectj.weaver.tools.DefaultMatchingContext;
import org.aspectj.weaver.tools.PointcutDesignatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.patterns.SelfTestPatternParser;
import io.gemini.aspectj.weaver.tools.PointcutParameter;
import io.gemini.aspectj.weaver.world.TypeWorld;
import io.gemini.aspectj.weaver.world.internal.ShadowImpl;
import net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

public class SelfTestPatternParserTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfTestPatternParserTests.class);

    private World world;

    private Pointcut pointcut;


    public static void main(String[] args) {
        SelfTestPatternParserTests tests = new SelfTestPatternParserTests();

//        tests.testSignatue();
        tests.testExists2();
    }

    public void testExists() {
        String data = "existsMethod(* io.gemini.aspectj.tools.SelfTestPatternParserTests1.testSignatue(..)) && existsMethod(java.util.List<java.lang.String> io.gemini.aspectj.tools.SelfTestPatternParserTests.replace(..))";
        boolean result = false;
        try {
            data = data.replace("existsMethod(", "execution(");
            createPointcut(data);
            result = fastMatchType("io.gemini.aspectj.tools.SelfTestPatternParserTests");
            LOGGER.info("fast match result: " + result);
        } catch(Throwable t) {
            t.printStackTrace(System.out);
        }
        LOGGER.info("@Condition(result:  {}): {}.", result, data);
    }


    private void createPointcut(String pointcutExpression) {
        this.world = new BcelWorld(this.getClass().getClassLoader().getSystemClassLoader(), IMessageHandler.THROW, null);
        this.world.setBehaveInJava5Way(true);

        PatternParser patternParser = new SelfTestPatternParser(pointcutExpression);

        Pointcut pointcut = patternParser.parsePointcut();
        this.pointcut = pointcut;
        TypeWorld world = new TypeWorld(doCreateTypePools(), null);
        IScope scope = new SimpleScope(world, new FormalBinding[0]);
        this.pointcut = pointcut.resolve(scope);
        
    }

    public void testExists2() {
        String data = "existsMethod(* io.gemini.aspectj.tools.SelfTestPatternParserTests1.testSignatue(..)) && existsMethod(java.util.List<java.lang.String> io.gemini.aspectj.tools.SelfTestPatternParserTests.replace(..))";
        boolean result = false;
        try {
//            data = data.replace("existsMethod(", "execution(");
            createPointcut2(data);
            result = fastMatchType("io.gemini.aspectj.tools.SelfTestPatternParserTests");
            LOGGER.info("fast match result: " + result);
        } catch(Throwable t) {
            t.printStackTrace(System.out);
        }
        LOGGER.info("@Condition(result:  {}): {}.", result, data);
    }


    private void createPointcut2(String pointcutExpression) {
        this.world = new BcelWorld(this.getClass().getClassLoader().getSystemClassLoader(), IMessageHandler.THROW, null);
        this.world.setBehaveInJava5Way(true);

        PatternParser patternParser = new SelfTestPatternParser(pointcutExpression);
        Set<PointcutDesignatorHandler> handlers = new HashSet<>();
        handlers.add(new ExistsPointcutDesignatorHandler());
        patternParser.setPointcutDesignatorHandlers(handlers, this.world);


        Pointcut pointcut = patternParser.parsePointcut();
        this.pointcut = pointcut;
        TypeWorld world = new TypeWorld(doCreateTypePools(), null);
        IScope scope = new SimpleScope(world, new FormalBinding[0]);
        this.pointcut = pointcut.resolve(scope);
        
    }

    private class ExistsPointcutDesignatorHandler implements PointcutDesignatorHandler {

        private static final String BEAN_DESIGNATOR_NAME = "existsMethod";

        @Override
        public String getDesignatorName() {
            return BEAN_DESIGNATOR_NAME;
        }

        @Override
        public ContextBasedMatcher parse(String expression) {
//            return new ExistsContextMatcher(expression);
            return null;
        }
    }

    private boolean fastMatchType(String className) {
        ResolvedType resolvedType = this.world.resolve(className);

//        FastMatchInfo info = new FastMatchInfo(resolvedType, null, this.world);

        MethodDescription m = null;
        try {
            m = new MethodDescription.ForLoadedMethod(Object.class.getMethod("toString"));
        } catch (NoSuchMethodException | SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        TypeWorld world = new TypeWorld(doCreateTypePools(), null);
        Shadow s = ShadowImpl.makeExecutionShadow(world, m, new DefaultMatchingContext());
        return this.pointcut.match(s).maybeTrue();
    }

    // exists(io.gemini.aspectj.pattern.PointcutParserTests) && 
    public void testSignatue() {
        String data = "(* io.gemini.aspectj.tools.SelfTestPatternParserTests.testSignatue()) && (java.util.List<java.lang.String> io.gemini.aspectj.tools.SelfTestPatternParserTests.replace(..));";

        PatternParser parser = new SelfTestPatternParser(data);

        TypePool typePool = doCreateTypePools();
        TypeWorld world = new TypeWorld(typePool, null);
        parser.setPointcutDesignatorHandlers(Collections.emptySet(), world);

        ISignaturePattern s = parser.parseCompoundMethodOrConstructorSignaturePattern(true);
        IScope resolutionScope = buildResolutionScope(TypeDescription.ForLoadedType.of(Object.class), new PointcutParameter[]{});
        Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
        ISignaturePattern s2 = s.resolveBindings(resolutionScope, bindingTable);

        boolean result = s2.matches(null, world, false);
        LOGGER.info("@Condition(result:  {}): {}.", result, data);
    }
    
    private IScope buildResolutionScope(TypeDescription inScope, PointcutParameter[] formalParameters) {
        if (formalParameters == null) {
            formalParameters = new PointcutParameter[0];
        }
        
        TypePool typePool = doCreateTypePools();
        TypeWorld world = new TypeWorld(typePool, null);

        
        FormalBinding[] formalBindings = new FormalBinding[formalParameters.length];
        for (int i = 0; i < formalBindings.length; i++) {
            formalBindings[i] = new FormalBinding(toUnresolvedType(formalParameters[i].getType()), formalParameters[i].getName(), i);
        }
        if (inScope == null) {
            return new SimpleScope(world, formalBindings);
        } else {
            ResolvedType inType = world.resolve(inScope.getName());
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
    
    private TypePool doCreateTypePools() {
        PoolStrategy poolStrategy = PoolStrategy.ClassLoading.FAST;
        TypePool typePool = poolStrategy.typePool(ClassFileLocator.NoOp.INSTANCE, this.getClass().getClassLoader());
        return typePool;
    }
    
    
//    protected String replace(String arg) {
//        return null;
//    }
//    
//    protected String replace(String arg, int count) {
//        return null;
//    }

    public List<String> replace(String arg, int count, String t) {
        return null;
    }
    
}
