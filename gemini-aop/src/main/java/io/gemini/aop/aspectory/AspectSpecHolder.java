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

import io.gemini.api.aspect.AspectSpec;


public class AspectSpecHolder<T extends AspectSpec> {

    private final String aspectName;

    private final T aspectSpec;


    public AspectSpecHolder(String aspectName, T aspectSpec) {
        super();
        this.aspectName = aspectName;
        this.aspectSpec = aspectSpec;
    }

    public String getAspectName() {
        return aspectName;
    }

    public T getAspectSpec() {
        return aspectSpec;
    }

    @Override
    public String toString() {
        return aspectName;
    }
}