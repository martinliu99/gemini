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

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.patterns.FastMatchInfo;
import org.aspectj.weaver.patterns.Pointcut;
import org.junit.jupiter.api.Test;

import io.gemini.aspectj.weaver.world.BytebuddyWorld;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;

public class PointcutExprTests extends AbstractTests {


    @Test
    public void testPointcut() {
        BytebuddyWorld typeWorld = this.doCreateTypeWorld();

        Pointcut pointcut = ExprParser.INSTANCE.parsePointcutExpr(typeWorld, 
                "execution(* io.gemini.*.*.PointcutExprTests.matchType(..))");

        {
            TypeDescription typeDescription = TypeDescription.ForLoadedType.of(ElementExprTests.class);

            assertThat( matchType(typeWorld, pointcut, typeDescription)).isFalse();
            assertThat( matchSignature(typeWorld, pointcut, typeDescription)).isFalse();
        }

        {
            TypeDescription typeDescription = TypeDescription.ForLoadedType.of(PointcutExprTests.class);

            assertThat( matchType(typeWorld, pointcut, typeDescription)).isTrue();
            assertThat( matchSignature(typeWorld, pointcut, typeDescription)).isTrue();
        }
    }

    private boolean matchType(BytebuddyWorld typeWorld, Pointcut pointcut, TypeDescription typeDescription) {
        ResolvedType resolvedType = typeWorld.resolve(typeDescription);

        FastMatchInfo info = new FastMatchInfo(resolvedType, null, typeWorld);
        return pointcut.fastMatch(info).maybeTrue();
    }

    private boolean matchSignature(BytebuddyWorld typeWorld, Pointcut pointcut, TypeDescription typeDescription) {
        for (MethodDescription MethodDescription : typeDescription.getDeclaredMethods()) {
            if (pointcut.match(typeWorld.makeShadow(MethodDescription)).alwaysTrue())
                return true;
        }
        return false;
    }
}
