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
package io.gemini.core;

/**
 * 
 * @author egale.martin
 *
 */
public enum DiagnosticLevel {

    DISABLED(false, false),
    SIMPLE(true, false),
    DEBUG(true, true)
    ;

    private final boolean simpleEnabled;
    private final boolean debugEnabled;

    DiagnosticLevel(boolean simpleEnabled, boolean debugEnabled) {
        this.simpleEnabled = simpleEnabled;
        this.debugEnabled = debugEnabled;
    }

    public boolean isSimpleEnabled() {
        return simpleEnabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }
}
