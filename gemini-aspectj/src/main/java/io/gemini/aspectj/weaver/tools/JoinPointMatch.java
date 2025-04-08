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
package io.gemini.aspectj.weaver.tools;

/**
 * @author colyer
 * The result of asking a ShadowMatch to match at a given join point.
 */
public interface JoinPointMatch {

    /**
     * True if the pointcut expression has matched at this join point, and false 
     * otherwise
     */
    boolean matches();
    
    /**
     * Get the parameter bindings at the matched join point. 
     * If the join point was not matched an empty array is returned.
     */
    PointcutParameter[] getParameterBindings();
}
