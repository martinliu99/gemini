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
package io.gemini.aop.aspectapp.support;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.agent.classloader.AgentClassLoader;
import io.gemini.aop.classloader.AspectClassLoader;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.object.Resources;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.GeminiUtils;

public class AspectContextHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AspectContextHelper.class);

    private static final String PATH_SEPERATOR = File.separator;

    private static final String APP_CONF_FOLDERNAME = "conf";
    private static final String APP_LIB_FOLDERNAME = "lib";
    private static final String APP_ASPECTS_FOLDERNAME = "aspects";


    private final DiagnosticLevel diagnoticLevel;

    private final String aspectAppsLocation;

    private final boolean scanGeminiLibs;
    private final URL[] agentResources;

    private final boolean scanClassesFolder;
    private final Set<String> scannedClassFolders;


    private Map<String /* AspectAppName */, Resources> appResourcesMap = null;


    public AspectContextHelper(DiagnosticLevel diagnoticLevel, String aspectAppsLocation, 
            boolean scanGeminiLibs, URL[] agentResources, 
            boolean scanClassesFolder, Set<String> scannedClassFolders) {
        this.diagnoticLevel = diagnoticLevel == null ? DiagnosticLevel.DISABLED : diagnoticLevel;

        Assert.hasText(aspectAppsLocation, "'aspectAppsLocation' must not be empty.");
        this.aspectAppsLocation = aspectAppsLocation;

        this.scanGeminiLibs = scanGeminiLibs;
        this.agentResources = agentResources == null ? new URL[] {} : agentResources;

        this.scanClassesFolder = scanClassesFolder;
        this.scannedClassFolders = scannedClassFolders == null ? Collections.emptySet() : scannedClassFolders;
    }

    public Map<String /* AspectAppName */, Resources> findAppResources() {
        if(this.appResourcesMap != null)
            return this.appResourcesMap;

        boolean simpleDiagnosticEnabled = diagnoticLevel.isSimpleEnabled();
        if(simpleDiagnosticEnabled == true) {
            LOGGER.info("^Scanning AspectApps.");
        }

        // 1.scan AspectApp under aspectApps folder
        List<File> paths = this.listAspectAppPaths(simpleDiagnosticEnabled);
        Map<String, Resources> appResourcesMap = new LinkedHashMap<>(paths.size() + 2);

        // include given aspect applications
        for(File path : paths) {
            appResourcesMap.put(path.getName(), this.getAspectAppResources(path));
        }


        // 2.scan AspectApp under gemini lib path
        if(this.scanGeminiLibs == true) {
            List<String> agentLibPaths = new ArrayList<>();
            try {
                for(URL url : this.agentResources) {
                    String path = url.getPath();
                    if(GeminiUtils.isGeminiJar(path)) {
                        agentLibPaths.add(path);
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("Failed to scan gemini classes.", t);
            }

            String appName = "Builtin-Aspects";
            appResourcesMap.put(appName, Resources.getJarReousrces(agentLibPaths));
        } else {
            if(simpleDiagnosticEnabled)
                LOGGER.info("  Ignored Gemini Libs scanning since isScanGeminiLibs is false.");
        }


        // 3.scan AspectApp under classes folder
        if(this.scanClassesFolder == true) {
            // retrieve folder from classpath
            List<String> candidatePaths = new ArrayList<>();
            for(String classPath : ClassLoaderUtils.getClassPaths()) {
                for(String suffix : scannedClassFolders) {
                    if(classPath.endsWith(suffix))
                        candidatePaths.add(classPath);
                }
            }

            if(CollectionUtils.isEmpty(candidatePaths) == false) {
                String appName = "Classes-Folder-Aspects";

                appResourcesMap.put(appName, Resources.getResources(candidatePaths));
            }
        } else {
            if(simpleDiagnosticEnabled)
                LOGGER.info("  Ignored class folders scanning since isScanClassFolder is false.");
        }

        if(appResourcesMap.size() == 0) {
            LOGGER.warn("$No AspectApps found.\n");
        } else {
            if(simpleDiagnosticEnabled == true)
                LOGGER.info("$Found AspectApps: {}", appResourcesMap.keySet());
        }

        this.appResourcesMap = appResourcesMap;
        return appResourcesMap;
    }

    private List<File> listAspectAppPaths(boolean simpleDiagnosticEnabled) {
        List<File> aspectAppPaths = new ArrayList<>();

        // try to load aspect app under code source
        String location = aspectAppsLocation;
        File path = new File(location);
        if(path.exists() == true && path.isDirectory()) {
            File[] subPaths = path.listFiles(new FileFilter() {
                @Override
                public boolean accept(File path) {
                    return path.isDirectory();
                }
            });

            if(subPaths != null) {
                for(int i=0; i<subPaths.length; i++) {
                    aspectAppPaths.add(subPaths[i]);
                }
            }
        }

        if(simpleDiagnosticEnabled) {
            if(aspectAppPaths.size() == 0)
                LOGGER.info("  Did not find AspectApp under '{}'.", aspectAppsLocation);
        }

        return aspectAppPaths;
    }

    private Resources getAspectAppResources(File aspectAppPath) {
        // scan aspects and lib folder
        File[] subPaths = aspectAppPath.listFiles(new FileFilter() {
            @Override
            public boolean accept(File path) {
                if(path.isDirectory() == false)
                    return false;

                return APP_LIB_FOLDERNAME.equals(path.getName())
                        || APP_ASPECTS_FOLDERNAME.equals(path.getName());
            }  
        });

        List<URL> urls = new ArrayList<>();
        if(subPaths != null) {
            List<String> paths = new ArrayList<>(subPaths.length);
            for(File file : subPaths) {
                paths.add( file.getAbsolutePath() );
            }
            urls.addAll(Resources.scanResourceNonRecursive(paths));
        }

        // add config folder
        try {
            urls.add(new File(aspectAppPath.getAbsolutePath() + PATH_SEPERATOR + APP_CONF_FOLDERNAME).toURI().toURL());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return new Resources(urls);
    }


    public Map<String /* AspectAppName */, AspectClassLoader> createAspectClassLoaders(
            Map<String, Resources> appResourcesMap, final AgentClassLoader agentClassLoader) {
        Map<String, AspectClassLoader> aspectClassLoaderMap = new HashMap<>();

        for(Entry<String, Resources> entry : appResourcesMap.entrySet()) {
            String appName = entry.getKey();
            aspectClassLoaderMap.put(
                    appName, 
                    new AspectClassLoader.WithThreadContextCL(appName, entry.getValue().getResourceUrls(), agentClassLoader)
            );
        }

        return aspectClassLoaderMap;
    }
}
