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
package io.gemini.aspectj.weaver.bcel;

import java.io.File;
import java.util.Collections;

import org.aspectj.bridge.IMessageHandler;
import org.aspectj.bridge.ISourceLocation;
import org.aspectj.bridge.SourceLocation;
import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.BindingScope;
import org.aspectj.weaver.IHasPosition;
import org.aspectj.weaver.ISourceContext;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.bcel.BcelObjectType;
import org.aspectj.weaver.bcel.BcelShadow;
import org.aspectj.weaver.bcel.BcelWorld;
import org.aspectj.weaver.bcel.LazyClassGen;
import org.aspectj.weaver.bcel.LazyMethodGen;
import org.aspectj.weaver.patterns.Bindings;
import org.aspectj.weaver.patterns.FastMatchInfo;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.ISignaturePattern;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.SimpleScope;
import org.aspectj.weaver.tools.PointcutParameter;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.patterns.PatternParserV2;
import io.gemini.aspectj.weaver.patterns.SelfTestPatternParser;
import io.gemini.aspectj.weaver.patterns.SelfTestPatternParserTests;
import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

/**
 * 
 */
public class PointcutTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfTestPatternParserTests.class);

    @Test
    public void test() {
        ClassLoader classLoader = PointcutTests.class.getClassLoader();
        BcelWorld world = createWorld(classLoader);

        String pointcutExpression = "execution(* io.gemini.aspectj.weaver.patterns.SelfTestPatternParserTests.replace(..))";
        Pointcut pointcut = createPointcut(world, pointcutExpression, world.resolve(Object.class.getName()), new PointcutParameter[]{});

        String name = "io.gemini.aspectj.weaver.patterns.SelfTestPatternParserTests";
        ResolvedType resolvedType = world.resolve(name);

        {
            FastMatchInfo fastMatch = new FastMatchInfo(resolvedType, null, world);
            FuzzyBoolean result = pointcut.fastMatch(fastMatch);
            LOGGER.info("Matched: {}, expr: {}", result, pointcutExpression);
        }

        BcelObjectType bcelObjectType = BcelWorld.getBcelObjectType(resolvedType);
        LazyClassGen lazyClassGen = bcelObjectType.getLazyClassGen();
        for(LazyMethodGen method : lazyClassGen.getMethodGens()) {
            BcelShadow shadow = BcelShadow.makeMethodExecution(world, method);
            FuzzyBoolean result = pointcut.match(shadow);
            LOGGER.info("Matched: {}, method name: {}, method signature: {}", result, method.getName(), method.getSignature());
        }

        LOGGER.info("");

        TypePool typePool =  createTypePool(classLoader);
        TypeDescription typeDescription = typePool.describe(name).resolve();
        for(MethodDescription methodDescription : typeDescription.getDeclaredMethods()) {
            LazyMethodGen method = lazyClassGen.getLazyMethodGen(methodDescription.getInternalName(), methodDescription.getDescriptor());

            BcelShadow shadow = BcelShadow.makeMethodExecution(world, method);
            FuzzyBoolean result = pointcut.match(shadow);
            LOGGER.info("Matched: {}, method: {}", result, method.toTraceString());
        }
    }

    private BcelWorld createWorld(ClassLoader classLoader) {
        BcelWorld world = new BcelWorld(classLoader, IMessageHandler.THROW, null);
        return world;
    }

    private TypePool createTypePool(ClassLoader classLoader) {
        PoolStrategy poolStrategy = PoolStrategy.ClassLoading.FAST;
        TypePool typePool = poolStrategy.typePool(ClassFileLocator.NoOp.INSTANCE, classLoader);
        return typePool;
    }

    private BytebuddyWorld createWorld(TypePool typePool, PlaceholderHelper placeholderHelper) {
        PoolStrategy poolStrategy = PoolStrategy.ClassLoading.FAST;

        BytebuddyWorld world = new BytebuddyWorld(typePool, placeholderHelper);
        world.setBehaveInJava5Way(true);
        return world;
    }

    private Pointcut createPointcut(BcelWorld world, String pointcutExpression, ResolvedType resolvedType, PointcutParameter[] params) {
        PatternParserV2 patternParser = new PatternParserV2(pointcutExpression);

        Pointcut pointcut = patternParser.parsePointcut();
        IScope scope = buildResolutionScope(world, resolvedType, params);
        return pointcut.resolve(scope);
    }


    @Test
    public void testSignatue() {
        ClassLoader classLoader = PointcutTests.class.getClassLoader();
        BcelWorld world = createWorld(classLoader);

        String data = "* io.gemini.aspectj.weaver.patterns.SelfTestPatternParserTests.testSignatue() && java.util.List<java.lang.String> io.gemini.aspectj.weaver.patterns.SelfTestPatternParserTests.replace(..)";
        PatternParser parser = new SelfTestPatternParser(data, world);

        parser.setPointcutDesignatorHandlers(Collections.emptySet(), world);

        ISignaturePattern s = parser.parseCompoundMethodOrConstructorSignaturePattern(true);
        IScope resolutionScope = buildResolutionScope(world, world.resolve(Object.class.getName()), new PointcutParameter[]{});
        Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
        ISignaturePattern s2 = s.resolveBindings(resolutionScope, bindingTable);

        boolean result = s2.matches(null, world, false);
        LOGGER.info("@Condition(result:  {}): {}.", result, data);
    }

    private IScope buildResolutionScope(BcelWorld world, ResolvedType resolvedType, PointcutParameter[] formalParameters) {
        if (formalParameters == null) {
            formalParameters = new PointcutParameter[0];
        }

        FormalBinding[] formalBindings = new FormalBinding[formalParameters.length];
        for (int i = 0; i < formalBindings.length; i++) {
            formalBindings[i] = new FormalBinding(toUnresolvedType(formalParameters[i].getType()), formalParameters[i].getName(), i);
        }
        if (world == null) {
            return new SimpleScope(world, formalBindings);
        } else {
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
            return new BindingScope(resolvedType, sourceContext, formalBindings);
        }
    }

    private UnresolvedType toUnresolvedType(Class<?> clazz) {
        String className = clazz.getName();
        if (clazz.isArray()) {
            return UnresolvedType.forSignature(className.replace('.', '/'));
        } else {
            return UnresolvedType.forName(className);
        }
    }


//    @Test
//    public void testBinding() {
//        ClassLoader classLoader = PointcutTests.class.getClassLoader();
//        BcelWorld world = createWorld(classLoader);
//
//        PointcutParameter[] parameters = new PointcutParameter[3];
//        parameters[0] = new PointcutParameterImpl("targetObject", TargetArgumentBinding_Object.class);
//        parameters[1] = new PointcutParameterImpl("_long", long.class);
//        parameters[2] = new PointcutParameterImpl("string", String.class);
//
//
//        String pointcutExpression = 
//                "pointcut(targetObject, _long, string)";
////                TargetArgumentBinding_Aspect.BIND_TARGET_ARGUMENT_POINTCUT;
//
//        TypePool typePool =  createTypePool(classLoader);
//
//        PlaceholderHelper placeholderHelper = 
////                null;
//                new PlaceholderHelper.Builder().build(Collections.singletonMap("test", TargetArgumentBinding_Aspect.BIND_TARGET_ARGUMENT_POINTCUT));
//        BytebuddyWorld typeWorld = createWorld(typePool, placeholderHelper);
//
//        ResolvedType type = world.resolve(TargetArgumentBinding_Aspect.class.getName());
////        world.getBcelObjectType(type)
//
//        ResolvedType object = typeWorld.resolve(Object.class.getName());
//        ReferenceType objectRef = (ReferenceType) object;
//
//        ReferenceTypeDelegate ret = new MyReferenceTypeDelegateImpl(TypeDescription.ForLoadedType.of(TargetArgumentBinding_Aspect.class), typeWorld, objectRef);
//
//        objectRef.setDelegate(ret);
//
//        Pointcut pointcut = createPointcut(world, pointcutExpression, type, parameters);
//
//        String name = "io.gemini.aop.integration.TargetArgumentBinding_Object";
//        ResolvedType resolvedType = world.resolve(name);
//
//        {
//            FastMatchInfo fastMatch = new FastMatchInfo(resolvedType, null, world);
//            FuzzyBoolean result = pointcut.fastMatch(fastMatch);
//            LOGGER.info("Matched: {}, expr: {}", result, pointcutExpression);
//        }
//
//        BcelObjectType bcelObjectType = BcelWorld.getBcelObjectType(resolvedType);
//        LazyClassGen lazyClassGen = bcelObjectType.getLazyClassGen();
//        for(LazyMethodGen method : lazyClassGen.getMethodGens()) {
//            BcelShadow shadow = BcelShadow.makeMethodExecution(world, method);
//            FuzzyBoolean result = pointcut.match(shadow);
//            LOGGER.info("Matched: {}, method name: {}, method signature: {}", result, method.getName(), method.getSignature());
//        }
//
//        LOGGER.info("");
//
//
//        TypeDescription typeDescription = typePool.describe(name).resolve();
//        for(MethodDescription methodDescription : typeDescription.getDeclaredMethods()) {
//            LazyMethodGen method = lazyClassGen.getLazyMethodGen(methodDescription.getInternalName(), methodDescription.getDescriptor());
//
//            BcelShadow shadow = BcelShadow.makeMethodExecution(world, method);
//            FuzzyBoolean result = pointcut.match(shadow);
//            LOGGER.info("Matched: {}, method: {}", result, method.toTraceString());
//
//            if(result.maybeTrue()) {
//                ExposedState state = new ExposedState(parameters.length);
//                pointcut.findResidue(shadow, state);
//                LOGGER.info("Matched: {}, state: {}", result, state);
//            }
//
//        }
//    }

}
