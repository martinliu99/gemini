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
package io.gemini.aspectj.weaver.patterns;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import org.aspectj.bridge.ISourceLocation;
import org.aspectj.bridge.SourceLocation;
import org.aspectj.weaver.BindingScope;
import org.aspectj.weaver.IHasPosition;
import org.aspectj.weaver.ISourceContext;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.SimpleScope;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.description.type.TypeDescription;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class ClassLoaderPatternParserTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassLoaderPatternParserTests.class);


    @Test
    public void testClassLoaderPattern() {
//        String data = "BootstrapClassLoader";
//
//        ClassLoader classLoader = ClassLoaderPatternParserTests.class.getClassLoader();
//        ReflectionWorld world = new ReflectionWorld(classLoader);
//
//        ClassLoaderPatternParser parser = new ClassLoaderPatternParser(data);
//
//        TypePattern s = parser.parseTypePattern(true, true);
//        IScope resolutionScope = buildResolutionScope(world, TypeDescription.ForLoadedType.of(Object.class), new PointcutParameter[]{});
//        Bindings bindingTable = new Bindings(resolutionScope.getFormalCount());
//        TypePattern s2 = s.resolveBindings(resolutionScope, bindingTable, false, false);
//
//        LOGGER.info("class loader name {}", classLoader);
//        boolean result = s.matchesStatically( world.resolve( classLoader.getParent().getClass() ) );
//        LOGGER.info("@Condition(result:  {}): {}.", result, data);
//
//        Class<?> clazz = null;
//        result = s.matchesStatically( world.resolve( ClassLoaderUtils.BOOTSTRAP_CLASSLOADER.getClass().getName() ) );
//        LOGGER.info("@Condition(result:  {}): {}.", result, data);
//
//        Class<ClassLoader[]> classLoaderClass = ClassLoader[].class;
//        LOGGER.info("array class {}, typeDescription: {}", classLoaderClass.getName(), TypeDescription.ForLoadedType.of(classLoaderClass) );
    }


    private IScope buildResolutionScope(ReflectionWorld world, TypeDescription inScope, Map<String, TypeDescription> formalParameters) {
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

    private UnresolvedType toUnresolvedType(TypeDescription clazz) {
        if (clazz.isArray()) {
            return UnresolvedType.forSignature(clazz.getName().replace('.', '/'));
        } else {
            return UnresolvedType.forName(clazz.getName());
        }
    }
}
