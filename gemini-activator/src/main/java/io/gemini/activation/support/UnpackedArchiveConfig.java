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
package io.gemini.activation.support;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import io.gemini.api.activation.LauncherConfig;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class UnpackedArchiveConfig implements LauncherConfig {

    private static final String AOP_LAUNCH_ACTIVEPROFILE_DEFAULT = ""; 
    private static final String AOP_INTERNAL_PROPERTIES = "META-INF/aop-internal.properties";

    private static final String FOLDER_ASPECTORIES = "aspectories";

    private final long launchedAt;

    private final Path launchPath;
    private final Map<String, String> launchArgs;

    private final String activeProfile;

    private URL[] launchResourceURLs;

    private final boolean scanClassesFolder;
    private Map<String /* AspectoryName */, URL[]> aspectoryResourceURLs;


    public UnpackedArchiveConfig(Path launchPath, Path launchFile, String launchArgsStr) throws IOException {
        this(launchPath, launchFile, launchArgsStr, 
                new LauncherScanner.Default(launchPath, launchFile), 
                false,
                new AspectoryScanner.Default( launchPath.resolve(FOLDER_ASPECTORIES) ) );
    }

    public UnpackedArchiveConfig(Path launchPath, Path launchFile, String launchArgsStr, 
            LauncherScanner launcherScanner, boolean scanClassesFolder, AspectoryScanner aspectoryScanner) throws IOException {
        this.launchedAt = System.nanoTime();

        this.launchPath = launchPath;
        if(Files.exists(launchPath) == false || Files.isDirectory(launchPath) == false)
            throw new IllegalArgumentException("Illegal launchPath: " + this.launchPath);

        this.launchArgs = Collections.unmodifiableMap(
                parseLaunchArgs(launchArgsStr) );

        this.activeProfile = this.parseActiveProfile(launchArgs);

        if(launcherScanner == null)
            launcherScanner = new LauncherScanner.Default(launchPath, launchFile);
        this.launchResourceURLs = launcherScanner.scanClassPathURLs();

        this.scanClassesFolder = scanClassesFolder;

        if(aspectoryScanner == null)
            aspectoryScanner = new AspectoryScanner.Default( launchPath.resolve(FOLDER_ASPECTORIES) );
        this.aspectoryResourceURLs = aspectoryScanner.scanClassPathURLs();
    }

    private Map<String, String> parseLaunchArgs(String launchArgsStr) {
        if(launchArgsStr == null)
            return Collections.emptyMap();

        Map<String, String> agentArgs = new LinkedHashMap<>();

        // 1.try to load from JVM -javaagent:jar=key1=value1,key2=value2
        StringTokenizer st = new StringTokenizer(launchArgsStr, ",");
        while(st.hasMoreTokens()) {
            String keyValuePair = st.nextToken();

            int pos = keyValuePair.indexOf("=");
            if(pos == -1 || pos >= keyValuePair.length())
                continue;   // ignore

            String key = keyValuePair.substring(0, pos).trim();
            String value = keyValuePair.substring(pos+1).trim();
            agentArgs.put(key, value);
        }

        return agentArgs;
    }

    private String parseActiveProfile(Map<String, String> launchArgs) {
        String activeProfile = launchArgs.get("aop.launcher.activeProfile");
        return activeProfile == null ? AOP_LAUNCH_ACTIVEPROFILE_DEFAULT : activeProfile.trim();
    }


    public long getLaunchedAt() {
        return launchedAt;
    }

    public Path getLaunchPath() {
        return launchPath;
    }

    public Map<String, String> getLaunchArgs() {
        return launchArgs;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public boolean isDefaultProfile() {
        return AOP_LAUNCH_ACTIVEPROFILE_DEFAULT.equals(activeProfile);
    }


    public String getInternalConfigLocation() {
        return AOP_INTERNAL_PROPERTIES;
    }

    public String getUserDefinedConfigLocation() {
        return "aop" + (this.isDefaultProfile() ? "" : "-" + this.getActiveProfile()) + ".properties";
    }


    public URL[] getLaunchClassPathURLs() {
        return this.launchResourceURLs;
    }

    @Override
    public boolean isScanClassesFolder() {
        return scanClassesFolder;
    }

    @Override
    public Map<String, URL[]> getAspectoryClassPathURLs() {
        return this.aspectoryResourceURLs;
    }
}
