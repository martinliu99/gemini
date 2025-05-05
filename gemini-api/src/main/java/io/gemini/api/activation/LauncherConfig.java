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
package io.gemini.api.activation;

import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface LauncherConfig {

    long getLaunchedAt();

    Path getLaunchPath();

    Map<String, String> getLaunchArgs();


    String getActiveProfile();

    boolean isDefaultProfile();


    String getInternalConfigLocation();

    String getUserDefinedConfigLocation();


    URL[] getLaunchResourceURLs();

    boolean isScanClassesFolder();

    Map<String /* AspectoryName */, URL[]> getAspectoryResourceURLs();
}
