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

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.patterns.FastMatchInfo;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.SimpleScope;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

public class PatternParserV2Tests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfTestPatternParserTests.class);


    @Test
    public void testSignatue() {
        try {
            BytebuddyWorld world = this.createWorld();
            Pointcut pointcut = createPointcut(world, "execution(* io.gemini.*.core.JoinPointMethodInvocationFactoryImpl.of2(..))");
            boolean result = fastMatchType(world, pointcut, "io.gemini.aop.framework.DefaultAspectFactory");
            LOGGER.info("fast match result: " + result);
        } catch(Throwable t) {
            t.printStackTrace(System.out);
        }
    }


    private BytebuddyWorld createWorld() {
        PoolStrategy poolStrategy = PoolStrategy.ClassLoading.FAST;
        TypePool typePool = poolStrategy.typePool(ClassFileLocator.NoOp.INSTANCE, this.getClass().getClassLoader());

        BytebuddyWorld world = new BytebuddyWorld(typePool, null);
        world.setBehaveInJava5Way(true);
        return world;
    }

    private Pointcut createPointcut(BytebuddyWorld world, String pointcutExpression) {
        PatternParserV2 patternParser = new PatternParserV2(pointcutExpression);

        Pointcut pointcut = patternParser.parsePointcut();
        IScope scope = new SimpleScope(world, new FormalBinding[0]);
        return pointcut.resolve(scope);
    }

    private boolean fastMatchType(BytebuddyWorld world, Pointcut pointcut, String className) {
        ResolvedType resolvedType = world.resolve(className);

        FastMatchInfo info = new FastMatchInfo(resolvedType, null, world);
        return pointcut.fastMatch(info).maybeTrue();
    }
}
