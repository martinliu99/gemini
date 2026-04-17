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
package io.gemini.api.activation;

import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

/**
 * Defines the runtime configuration supplied to {@link AopLauncher} during startup.
 * <p>
 * Provides paths, active profile, configuration file locations, and classpath URLs
 * for both the launcher itself and each discovered aspect application.
 * </p>
 *
 * @author   martin.liu
 */
public interface LauncherConfig {

    /** Returns the timestamp (nanoseconds) when the launcher was activated. */
    long getLaunchedAt();

    /** Returns the root directory of the unpacked AOP agent archive. */
    Path getLaunchPath();

    /** Returns the parsed key=value launch arguments passed to the agent. */
    Map<String, String> getLaunchArgs();

    /** Returns the active configuration profile name (e.g., {@code dev}), or empty string for default. */
    String getActiveProfile();

    /** Returns {@code true} if the default (empty) profile is active. */
    boolean isDefaultProfile();

    /** Returns the classpath-relative location of the built-in internal properties file. */
    String getInternalConfigLocation();

    /** Returns the classpath-relative location of the user-defined properties file. */
    String getUserDefinedConfigLocation();

    /** Returns the classpath URLs for the AOP launcher itself. */
    URL[] getLaunchClassPathURLs();

    /** Returns {@code true} if the {@code /classes} and {@code /test-classes} folders should be scanned for aspects. */
    boolean isClassesFolderScanned();

    /** Returns a map of aspect-application name to its classpath URLs. */
    Map<String /* AspectAppName */, URL[]> getAspectAppClassPathURLs();
}
