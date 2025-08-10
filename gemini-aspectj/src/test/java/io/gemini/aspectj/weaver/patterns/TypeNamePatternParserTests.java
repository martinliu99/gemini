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

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.patterns.TypePattern;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import jdk.proxy2.$Proxy27;
import net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

public class TypeNamePatternParserTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypeNamePatternParserTests.class);


    @Test
    public void testTypeName() {
        BytebuddyWorld world = this.createWorld();

        {
            TypeNamePatternParser patternParser = new TypeNamePatternParser("io.gemini.aspectj..patterns.*TypeNamePatternParserTest*");
            TypePattern typePattern = patternParser.parseTypePattern();
    
            ResolvedType type = world.resolve(TypeDescription.ForLoadedType.of(getClass()));
            boolean result = typePattern.matchesStatically(type);
            LOGGER.info("type name match result: " + result);
            assertThat(result).isEqualTo(true);

            ResolvedType type2 = world.resolve(TypeDescription.ForLoadedType.of(TypeNamePatternParserTests.InnerClass.class));
            boolean result2 = typePattern.matchesStatically(type2);
            assertThat(result2).isEqualTo(true);
        }

        {
            TypeNamePatternParser patternParser = new TypeNamePatternParser("io.gemini.aspectj..patterns.TypeNamePatternParserTest*$InnerClass");
            TypePattern typePattern = patternParser.parseTypePattern();

            ResolvedType type = world.resolve(TypeDescription.ForLoadedType.of(getClass()));
            boolean result = typePattern.matchesStatically(type);
            LOGGER.info("type name match result: " + result);
            assertThat(result).isEqualTo(false);

            ResolvedType type2 = world.resolve(TypeDescription.ForLoadedType.of(TypeNamePatternParserTests.InnerClass.class));
            boolean result2 = typePattern.matchesStatically(type2);
            assertThat(result2).isEqualTo(true);
        }

        {
            TypeNamePatternParser patternParser = new TypeNamePatternParser("io.gemini.aspectj..patterns.TypeNamePatternParserTests* && !io.gemini.aspectj.weaver.patterns.TypeNamePatternParserTests$NestClass");
            TypePattern typePattern = patternParser.parseTypePattern();

            ResolvedType type = world.resolve(TypeDescription.ForLoadedType.of(getClass()));
            boolean result = typePattern.matchesStatically(type);
            LOGGER.info("type name match result: " + result);
            assertThat(result).isEqualTo(true);

            ResolvedType type2 = world.resolve(TypeDescription.ForLoadedType.of(TypeNamePatternParserTests.InnerClass.class));
            boolean result2 = typePattern.matchesStatically(type2);
            assertThat(result2).isEqualTo(true);

            ResolvedType type3 = world.resolve(TypeDescription.ForLoadedType.of(TypeNamePatternParserTests.NestClass.class));
            boolean result3 = typePattern.matchesStatically(type3);
            assertThat(result3).isEqualTo(false);
        }

        {
            TypeNamePatternParser patternParser = new TypeNamePatternParser(" !*..$Proxy*");
            TypePattern typePattern = patternParser.parseTypePattern();
    
            ResolvedType type = world.resolve(TypeDescription.ForLoadedType.of(getClass()));
            boolean result = typePattern.matchesStatically(type);
            LOGGER.info("type name match result: " + result);
            assertThat(result).isEqualTo(true);

            ResolvedType type2 = world.resolve(TypeDescription.ForLoadedType.of($Proxy27.class));
            boolean result2 = typePattern.matchesStatically(type2);
            assertThat(result2).isEqualTo(false);
        }

    }


    private BytebuddyWorld createWorld() {
        PoolStrategy poolStrategy = PoolStrategy.ClassLoading.FAST;
        TypePool typePool = poolStrategy.typePool(ClassFileLocator.NoOp.INSTANCE, this.getClass().getClassLoader());

        BytebuddyWorld world = new BytebuddyWorld(typePool, null);
        world.setBehaveInJava5Way(true);
        return world;
    }

    public static class InnerClass {}

    public class NestClass {}
}
