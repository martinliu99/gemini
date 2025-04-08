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
package io.gemini.aop.aspectj;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.aspectj.ExprPointcut.AspectJExprPointcut;
import io.gemini.aspectj.weaver.world.TypeWorld;
import net.bytebuddy.agent.builder.AgentBuilder.PoolStrategy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

public class AspectJExprPointcutTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectJExprPointcutTests.class);

    public static void main(String[] args) {
        AspectJExprPointcutTests tests = new AspectJExprPointcutTests();

        tests.testKindedExpression();
    }

    public void testKindedExpression() {
        try {
            AspectJExprPointcut pointcut = new AspectJExprPointcut();

            TypePool typePool = doCreateTypePools();
            pointcut.setTypeWorld(new TypeWorld(typePool, null));

            // 
            //"execution(java.util.List<java.lang.String> io.gemini..*.PointcutParserTests.replace(..))";
            String expression = "execution(* io.gemini.aop.aspectj.AspectJExprPointcutTests.replace(..)) || exists(io.gemini.aop.aspectj.AspectJExprPointcutTests.replace(..))";
            pointcut.setPointcutExpr(expression);

            TypeDescription type = TypeDescription.ForLoadedType.of(AspectJExprPointcutTests.class);
            boolean result = pointcut.getTypeMatcher().matches(type);
            LOGGER.info("Matched '{}' for type {}", result, type);
            
            for(MethodDescription.InDefinedShape method : type.getDeclaredMethods()) {
                result = pointcut.getMethodMatcher().matches(method);

                LOGGER.info("Matched '{}' for method '{}' with expression '{}' ", result, method, expression);
            }
        } catch(Throwable t) {
            t.printStackTrace(System.out);            
        }
        
    }

    private TypePool doCreateTypePools() {
        PoolStrategy poolStrategy = PoolStrategy.ClassLoading.FAST;
        TypePool typePool = poolStrategy.typePool(ClassFileLocator.NoOp.INSTANCE, this.getClass().getClassLoader());
        return typePool;
    }


    protected String replace(String arg) {
        return null;
    }

    protected String replace(String arg, int count) {
        return null;
    }

    public List<String> replace(String arg, int count, String t) {
        return null;
    }
}
