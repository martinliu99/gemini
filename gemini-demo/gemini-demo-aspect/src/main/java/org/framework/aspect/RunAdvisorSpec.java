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
package org.framework.aspect;

import io.gemini.aop.matcher.MethodMatcher;
import io.gemini.aop.matcher.TypeMatcher;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Pointcut;


public class RunAdvisorSpec extends AdvisorSpec.PojoPointcutSpec.Default {

    
    /**
     */
    public RunAdvisorSpec() {
        super(
                context -> context.isBootstrapClassLoader(),
                false, RunAdvice.class.getName(), 
                new Pointcut.Default(
                        TypeMatcher.nameStartsWith("io.gemini.weaver.transformer").and( TypeMatcher.isExtendedFrom("java.lang.Runnable")),
                        MethodMatcher.nameEquals("run") ), 1);
    }

}
    