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

import java.util.ArrayList;
import java.util.List;

import org.framework.demo.api.Request;
import org.framework.demo.api.Response;

import io.gemini.aop.matcher.MethodMatcher;
import io.gemini.aop.matcher.TypeMatcher;
import io.gemini.api.aop.Advice;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Joinpoint.MutableJoinpoint;

public class DemoServiceAdvice_process extends Advice.AbstractBeforeAfter<Response<String>, RuntimeException> 
        implements AdvisorSpec.PojoPointcutSpec.Factory {

    private static final String DEMO_SERVICE_ADVICE = "DemoServiceAdvice_process";

    @Override
    public void before(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        LOGGER.info("before '{}' with args: {}", this.getClass().getSimpleName(), joinpoint.getArguments());

        // update argument
        Request request = (Request) joinpoint.getArguments()[0];
        List<String> input = new ArrayList<>(request.getInput());
        input.add(DEMO_SERVICE_ADVICE);
        Request modified = new Request(input);
        
        // TODO:
    }

    @Override
    public void after(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        LOGGER.info("after '{}' with args: {}", this.getClass().getSimpleName(), joinpoint.getArguments());
    }

    @Override
    public AdvisorSpec.PojoPointcutSpec getAdvisorSpec() {
        return new AdvisorSpec.PojoPointcutSpec.Builder()
                .adviceClassName(this.getClass().getName())
                .typeMatcher( TypeMatcher.nameEquals("org.framework.demo.service.DemoServiceImpl") )
                .methodMatcher( MethodMatcher.nameEquals("process") )
                .builder();
    }
}
