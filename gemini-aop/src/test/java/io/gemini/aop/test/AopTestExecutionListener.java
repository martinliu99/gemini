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
package io.gemini.aop.test;

import javax.annotation.Resource;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import io.gemini.aop.AopContext;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AopTestExecutionListener implements TestExecutionListener {

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        ExecutionMemento.clearMemento();
    }


    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
    }


    @Aspect
    static class ApplicationStartup_Aspect {

        @Resource
        private AopContext aopContext;

        @After("execution(public void io.gemini.aop.test.AopTestExecutionListener.testPlanExecutionFinished(..))")
        public void after(MutableJoinpoint<Void, RuntimeException> joinpoint) throws Throwable {
            this.aopContext.getAopMetrics().startupApplication();
        }
    }
}
