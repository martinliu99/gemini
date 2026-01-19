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
package io.gemini.core.bootstrap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * {@code BootstrapClassProvider} is a mark annotation, and annotated classes 
 * will be injected into bootstrap ClassLoader by the AOP framework.
 * 
 * 
 * @author   martin.liu
 * @since    1.0
 */
@Target( {ElementType.TYPE} )
@Retention(RetentionPolicy.CLASS)
public @interface BootstrapClassProvider {

    /**
     * define destination package to place annotated class, and type scope to fetch 
     * private Lookup under JDK 9+
     * 
     * @return
     */
    Class<?> scopeType();

}
