/*
 * Copyright © 2023 - present, the original author or authors. All Rights Reserved.
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
package io.gemini.api.aop.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Assigns a unique name to an advisor, overriding the default class-name-based name.
 * 
 * <p>
 * Can be placed on an advice class or an AspectJ advice method.
 * </p>
 *
 * @author   martin.liu
 */
@Target( {ElementType.TYPE, ElementType.METHOD} )
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdvisorName {

    /**
     * unique advisor name
     * 
     * @return  advisor name
     */
    String value();
}
