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
package io.gemini.core.util;

import java.util.regex.Pattern;

import nonapi.io.github.classgraph.scanspec.AcceptReject;
import nonapi.io.github.classgraph.utils.JarUtils;

/**
 * Utility class for identifying the Gemini AOP framework JARs on the classpath.
 *
 * @author   martin.liu
 */
public abstract class GeminiUtils {

    private static final Pattern GEMINI_JAR_PATTERN = AcceptReject.globToPattern("gemini*.jar", true);


    /**
     * Returns {@code true} if the given path is a Gemini AOP framework JAR
     * (matches the {@code gemini*.jar} glob pattern).
     *
     * @param path the file path to check
     * @return {@code true} if the path is a Gemini JAR
     */
    public static boolean isGeminiJar(String path) {
        return GEMINI_JAR_PATTERN.matcher(JarUtils.leafName(path)).matches();
    }

}
