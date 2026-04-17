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
 * Controls the verbosity of the Gemini AOP framework diagnostic output.
 * <ul>
 *   <li>{@link #DISABLED} – no diagnostic output (production default)</li>
 *   <li>{@link #SIMPLE} – startup timing breakdown and weaving summary</li>
 *   <li>{@link #DEBUG} – full debug output including per-type matching details</li>
 * </ul>
 *
 * @author   martin.liu
 */
public enum DiagnosticLevel {

    DISABLED(false, false),
    SIMPLE(true, false),
    DEBUG(true, true)
    ;

    private final boolean enableSimple;
    private final boolean enableDebug;

    DiagnosticLevel(boolean enableSimple, boolean enableDebug) {
        this.enableSimple = enableSimple;
        this.enableDebug = enableDebug;
    }

    /**
     * Returns {@code true} if simple diagnostic output (startup timing and weaving
     * summary) is enabled for this level.
     *
     * @return {@code true} for {@link #SIMPLE} and {@link #DEBUG}; {@code false} for {@link #DISABLED}
     */
    public boolean isSimpleEnabled() {
        return enableSimple;
    }

    /**
     * Returns {@code true} if full debug diagnostic output (per-type matching details)
     * is enabled for this level.
     *
     * @return {@code true} only for {@link #DEBUG}; {@code false} otherwise
     */
    public boolean isDebugEnabled() {
        return enableDebug;
    }
}
