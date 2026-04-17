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
package org.framework.demo.service;

/**
 * Marker annotation used in the demo service to mark methods that should be intercepted
 * by the AspectJ-style aspect ({@link Sample01_DemoServiceAspectJAspect}).
 * Demonstrates how Gemini supports {@code @annotation()} pointcut designators.
 *
 * @author   martin.liu
 */
public @interface AspectJ {

}
