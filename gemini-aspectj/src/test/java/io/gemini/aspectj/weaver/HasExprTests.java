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
package io.gemini.aspectj.weaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.aspectj.bridge.ISourceLocation;
import org.aspectj.bridge.SourceLocation;
import org.aspectj.weaver.BindingScope;
import org.aspectj.weaver.IHasPosition;
import org.aspectj.weaver.ISourceContext;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.patterns.Bindings;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.SimpleScope;
import org.aspectj.weaver.patterns.TypePattern;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.patterns.HasPatternParser;
import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import net.bytebuddy.description.type.TypeDescription;

public class HasExprTests extends AbstractTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(HasExprTests.class);


    @Test
    public void testHasType() {
        BytebuddyWorld typeWorld = this.doCreateTypeWorld();

        assertThat(ExprParser.INSTANCE.hasType(typeWorld,
                "io.gemini.aspectj.weaver.HasExprTests && io.gemini.aspectj.weaver.PointcutExprTests")).isTrue();

        assertThat(ExprParser.INSTANCE.hasType(typeWorld,
                "io.gemini.aspectj.weaver.HasExprTests && io.gemini.aspectj.weaver.patterns.HasPatternParser$HasTypePattern")).isTrue();

    }


    @Test
    public void testHasField() {
        BytebuddyWorld typeWorld = this.doCreateTypeWorld();

        assertThat(ExprParser.INSTANCE.hasField(typeWorld,
                "org.slf4j.Logger io.gemini.aspectj.weaver.HasExprTests.LOGGER")).isTrue();

        assertThat(ExprParser.INSTANCE.hasField(typeWorld,
                "(org.slf4j.Logger io.gemini.aspectj.weaver.HasExprTests.LOGGER) && (boolean io.gemini.aspectj.weaver.patterns.HasPatternParser$HasTypePattern.existType)")).isTrue();
        assertThat(ExprParser.INSTANCE.hasField(typeWorld,
                "(org.slf4j.Logger io.gemini.aspectj.weaver.HasExprTests.LOGGER) && (boolean io.gemini.aspectj.weaver.patterns.HasPatternParser$HasTypePattern.existType1)")).isFalse();
    }


    @Test
    public void testHasMethod() {
        BytebuddyWorld typeWorld = this.doCreateTypeWorld();

        assertThat(ExprParser.INSTANCE.hasMethod(typeWorld,
                "* io.gemini.aspectj.weaver.HasExprTests.testHasMethod() && java.util.List<java.lang.String> io.gemini.aspectj.weaver.HasExprTests.replace(..)")).isTrue();
        assertThat(ExprParser.INSTANCE.hasConstructor(typeWorld,
                "io.gemini.aspectj.weaver.HasExprTests.new() && io.gemini.aspectj.weaver.patterns.HasPatternParser$HasTypePattern.new()")).isTrue();
    }


    @Test
    public void testHasMembers() {
        String data = "hasmethod(public void io.gemini.aspectj.weaver.HasExprTests.testMethod(..)) && hasmethod(java.util.List<java.lang.String> io.gemini.aspectj.weaver.HasExprTests.replace(..))";
        boolean result = false;
        try {
//            data = data.replace("existsMethod(", "execution(");
            World world = createReflectionWorld();
            TypePattern typePattern = createHasTypePatternt(world, data);

            ResolvedType resolvedType = world.resolve("io.gemini.aspectj.weaver.patterns.HasPatternParserTests");

            IScope resolutionScope = has_buildResolutionScope(world, resolvedType, Collections.emptyMap());
            Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
            typePattern = typePattern.resolveBindings(resolutionScope, bindingTable, true, true);

            result = typePattern.matchesStatically(resolvedType);
            LOGGER.info("fast match result: " + result);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        assertThat(result).isTrue();
        LOGGER.info("@Condition(result:  {}): {}.", result, data);
    }

    /**
     * @param world2
     * @param data
     * @return
     * @throws SecurityException 
     * @throws NoSuchFieldException 
     * @throws IllegalAccessException 
     * @throws IllegalArgumentException 
     */
    private TypePattern createHasTypePatternt(World world, String pointcutExpression) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        PatternParser patternParser = new HasPatternParser(pointcutExpression);

        Field field = PatternParser.class.getDeclaredField("allowHasTypePatterns");
        field.setAccessible(true);
        field.set(patternParser, true);

        return patternParser.parseSingleTypePattern(true);
    }


    private IScope has_buildResolutionScope(World world, ResolvedType inScope, Map<String, TypeDescription> formalParameters) {
        if (formalParameters == null) {
            formalParameters = Collections.emptyMap();
        }

        FormalBinding[] formalBindings = new FormalBinding[formalParameters.size()];
        int i = 0; 
        for (Entry<String, TypeDescription> entry : formalParameters.entrySet()) {
            formalBindings[i] = new FormalBinding(toUnresolvedType(entry.getValue()), entry.getKey(), i++);
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


    protected ReflectionWorld createReflectionWorld() {
        ReflectionWorld world = new ReflectionWorld(this.getClass().getClassLoader().getSystemClassLoader());
        world.setBehaveInJava5Way(true);
        world.setXHasMemberSupportEnabled(true);
        return world;
    }

    private UnresolvedType toUnresolvedType(TypeDescription clazz) {
        if (clazz.isArray()) {
            return UnresolvedType.forSignature(clazz.getName().replace('.', '/'));
        } else {
            return UnresolvedType.forName(clazz.getName());
        }
    }


    public List<String> replace(String arg, int count, String t) {
        return null;
    }
    
}
