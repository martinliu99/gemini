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
/**
 * 
 */
package io.gemini.core.pool;

/**
 * Tracks the level of type resolution performed during pointcut matching.
 * <p>
 * Used by {@link io.gemini.core.pool.TypePools.DelegatedTypeDescription.TyepResolutionDetector}
 * to record whether a type description required full resolution (loading superclass/interface
 * information) during advisor fast-match or method-match phases.
 * </p>
 *
 * @author   martin.liu
 */
public interface TypeResolutionInspector {

    /**
     * Resets the recorded resolution level to {@link ResolutionLevel#NO_RESOLUTION}.
     */
    void resetInspection();

    /**
     * Returns the current resolution level recorded during matching.
     *
     * @return the resolution level
     */
    ResolutionLevel getResolutionLevel();

    /**
     * Updates the resolution level if the given level is higher than the current one.
     *
     * @param resolutionLevel the resolution level to set
     */
    void setResolutionLevel(ResolutionLevel resolutionLevel);


    /**
     * Enumeration of type resolution levels, ordered from least to most expensive.
     */
    static enum ResolutionLevel {

        NO_RESOLUTION(0),
        TYPE_RESOLUTION(10),
        SUPER_TYPE_RESOLUTION(20);


        private final int levelCode;

        ResolutionLevel(int levelCode) {
            this.levelCode = levelCode;
        }

        public int getLevelCode() {
            return levelCode;
        }
    }
}
