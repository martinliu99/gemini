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
package io.gemini.core;

/**
 * Defines an ordering contract for the Gemin AOP framework components.
 * <p>
 * Components with lower order values have higher priority.
 * Use {@link io.gemini.api.annotation.Order} to specify the order value.
 * </p>
 *
 * @author   martin.liu
 */
public interface Ordered {

    /**
     * Returns the order value of this component. Lower values indicate higher priority.
     * <p>
     * Use {@link io.gemini.api.annotation.Order#HIGHEST_PRECEDENCE} for the highest
     * priority and {@link io.gemini.api.annotation.Order#LOWEST_PRECEDENCE} for the lowest.
     * </p>
     *
     * @return the order value; lower means higher priority
     */
    int getOrder();
}
