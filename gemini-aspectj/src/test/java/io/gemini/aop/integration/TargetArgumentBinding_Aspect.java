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
package io.gemini.aop.integration;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public  class TargetArgumentBinding_Aspect {

    public static final String BIND_TARGET_ARGUMENT_POINTCUT = 
            "execution(!private long io.gemini.aop.integration.TargetArgumentBinding_Object.bindTargetArgument(..)) && args(_long, string) && this(targetObject)";

    @Pointcut("${test}")
//    @Pointcut(BIND_TARGET_ARGUMENT_POINTCUT)
    public void pointcut(TargetArgumentBinding_Object targetObject, long _long, String string) {}
    
    @After(value = "pointcut(targetObject, _long, string)", argNames = "targetObject, _long, string")
//    @After(value = BIND_TARGET_ARGUMENT_POINTCUT, argNames = "targetObject, _long, string")
    public void bindTargetArgument_afterAdvice(TargetArgumentBinding_Object targetObject, long _long, String string) {

    }

}
