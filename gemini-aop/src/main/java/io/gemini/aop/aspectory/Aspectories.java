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
package io.gemini.aop.aspectory;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.gemini.aop.AopContext;
import io.gemini.aop.AopMetrics;
import io.gemini.aop.AspectFactory;
import io.gemini.core.util.Assert;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class Aspectories {


    public static AspectFactory createAspectFactory(AopContext aopContext) {
        Assert.notNull(aopContext, "'aopContext' must not be null.");

        long startedAt = System.nanoTime();

        CompoundAspectFactory aspectFactory = new CompoundAspectFactory(aopContext);

        // record metrics
        AopMetrics.BootstraperMetrics bootstraperMetrics = aopContext.getAopMetrics().getBootstraperMetrics();
        bootstraperMetrics.setAspectFactoryCreationTime(System.nanoTime() - startedAt);

        Map<String, Integer> aspectSpecs = aspectFactory.getAspectSpecs().entrySet().stream()
                .map( e -> 
                    new SimpleEntry<>(e.getKey(), e.getValue().size()) )
                .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );
        bootstraperMetrics.setAspectSpecs(aspectSpecs);

        return aspectFactory;
    }

}
