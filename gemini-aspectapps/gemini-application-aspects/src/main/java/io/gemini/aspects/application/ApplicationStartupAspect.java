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
package io.gemini.aspects.application;

import javax.annotation.Resource;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

import io.gemini.aop.aspect.Joinpoint.MutableJoinpoint;
import io.gemini.aop.aspectapp.AspectContext;


@Aspect
public class ApplicationStartupAspect {

    @Resource
    private AspectContext aspectContext;

    @Pointcut("${aop.aspectApp.applicationStartupPointcut}")
    public void applicationStartup() {}


    @Before("${aop.aspectApp.applicationStartupPointcut}")
    public void before(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
        aspectContext.getAopMetrics();
    }

    @After("applicationStartup()")
    public void after(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
        this.aspectContext.getAopMetrics().startupApplication();
    }
}
