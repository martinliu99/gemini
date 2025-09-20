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
package io.gemini.aop.factory;

import io.gemini.aop.AdvisorFactory;
import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.core.util.Assert;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AdvisorFactories {


    public static AdvisorFactory createAdvisorFactory(AopContext aopContext) {
        Assert.notNull(aopContext, "'aopContext' must not be null.");

        long startedAt = System.nanoTime();

        CompoundAdvisorFactory advisorFactory = new CompoundAdvisorFactory(aopContext);

        // record metrics
        AopMetrics.BootstraperMetrics bootstraperMetrics = aopContext.getAopMetrics().getBootstraperMetrics();

        bootstraperMetrics.setAdvisorSpecs(advisorFactory.getAdvisorSpecNum());
        bootstraperMetrics.setAdvisorFactoryCreationTime(System.nanoTime() - startedAt);

        return advisorFactory;
    }

}
