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

import org.aspectj.bridge.IMessageHandler;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.bcel.BcelWorld;
import org.aspectj.weaver.patterns.FastMatchInfo;
import org.aspectj.weaver.patterns.FormalBinding;
import org.aspectj.weaver.patterns.IScope;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.Pointcut;
import org.aspectj.weaver.patterns.SimpleScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatternParserV2Tests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfTestPatternParserTests.class);

    private World world;

    private Pointcut pointcut;

    public static void main(String[] args) {
        PatternParserV2Tests tests = new PatternParserV2Tests();
        tests.testSignatue();
    }

    
    public void testSignatue() {
        try {
            createPointcut("execution(* io.gemini.*.core.JoinPointMethodInvocationFactoryImpl.of2(..))");
            boolean result = fastMatchType("io.gemini.aop.framework.DefaultAspectFactory");
            LOGGER.info("fast match result: " + result);
        } catch(Throwable t) {
            t.printStackTrace(System.out);
        }
    }

    private void createPointcut(String pointcutExpression) {
        this.world = new BcelWorld(this.getClass().getClassLoader().getSystemClassLoader(), IMessageHandler.THROW, null);
        this.world.setBehaveInJava5Way(true);

        PatternParser patternParser = new PatternParser(pointcutExpression);

        Pointcut pointcut = patternParser.parsePointcut();
        IScope scope = new SimpleScope(this.world, new FormalBinding[0]);
        this.pointcut = pointcut.resolve(scope);
    }

    private boolean fastMatchType(String className) {
        ResolvedType resolvedType = this.world.resolve(className);

        FastMatchInfo info = new FastMatchInfo(resolvedType, null, this.world);
        return this.pointcut.fastMatch(info).maybeTrue();
    }
}
