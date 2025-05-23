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
package io.gemini.aop.java.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code BootstrapClassConsumer} is a mark annotation to indicate class accessing 
 * these bootstrap classes annotated via {@code BootstrapClassProvider}.
 * 
 * <p>
 * The AOP framework will scan annotated classes, and rename bootstrap class full name
 * from "xxx.yyy.java.***" to "java.***" when starting up.
 * 
 * 
 * @author   martin.liu
 * @since    1.0
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface BootstrapClassConsumer {

}
