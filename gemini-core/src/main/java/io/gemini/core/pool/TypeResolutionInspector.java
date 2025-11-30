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
/**
 * 
 */
package io.gemini.core.pool;

/**
 * This class collects TypeDescription resolution information that is used to analyze
 * Pointcut matching performance impact.
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface TypeResolutionInspector {

    void resetInspection();

    ResolutionLevel getResolutionLevel();

    void setResolutionLevel(ResolutionLevel resolutionLevel);


    static enum ResolutionLevel {

        NO_RESOLUTION,
        TYPE_RESOLUTION,
        SUPER_TYPE_RESOLUTION;
    }
}
