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
package io.gemini.core.object;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.util.Assert;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class Resources {

    private static final Logger LOGGER = LoggerFactory.getLogger(Resources.class);
    private static final List<String> EXT_NAMES;

    private List<URL> resourceUrls;

    static {
        List<String> extNames = new ArrayList<>();
        extNames.add(".jar");
        extNames.add(".zip");
        extNames.add(".properties");

        EXT_NAMES = extNames;
    }

    public static Resources getJarReousrces(String... resourceLocations) {
        Assert.notEmpty(resourceLocations, "'resourceLocations' must not be empty");

        return getJarReousrces(Arrays.asList(resourceLocations));
    }

    public static Resources getJarReousrces(URL[] resourceLocations) {
        Assert.notEmpty(resourceLocations, "'resourceLocations' must not be empty");

        List<String> locations = new ArrayList<>(resourceLocations.length);
        for(URL url : resourceLocations) {
            locations.add(url.getFile());
        }

        return getJarReousrces(locations);
    }

    public static Resources getJarReousrces(List<String> resourceLocations) {
        Assert.notEmpty(resourceLocations, "'resourceLocations' must not be empty");

        return new Resources(scanResourceNonRecursive(resourceLocations));
    }

    public static Resources getResources(String... resourceLocations) {
        Assert.notEmpty(resourceLocations, "'resourceLocations' must not be empty");

        return getResources(Arrays.asList(resourceLocations));
    }

    public static Resources getResources(List<String> resourceLocations) {
        Assert.notEmpty(resourceLocations, "'resourceLocations' must not be empty");

        List<URL> urls = new ArrayList<>(resourceLocations.size());
        for(String resourceLocation : resourceLocations) {
            File resource = new File(resourceLocation);
            if(resource.exists() == false) {
                continue;
            }

            try {
                urls.add(resource.toURI().toURL());
            } catch (Exception e) {
                LOGGER.warn("Failed to resource '{}''s URL", resourceLocation, e);
            }
        }
        return new Resources(urls);
    }

    public static List<URL> scanResourceNonRecursive(List<String> resourceLocations) {
        return scanResource(resourceLocations, EXT_NAMES, false);
    }


    public Resources(List<URL> resourceUrls) {
        Assert.notEmpty(resourceUrls, "'resourceUrls' must not be empty");

        this.resourceUrls = new ArrayList<>(resourceUrls);
    }


    public URL[] getResourceUrls() {
        return resourceUrls.toArray(new URL[] {});
    }

    public List<URL> getResourceUrlList() {
        return Collections.unmodifiableList( resourceUrls );
    }

    public String[] getResource() {
        String[] resources = new String[this.resourceUrls.size()];
        int i = 0;
        for(URL url : this.resourceUrls) {
            resources[i++] = url.getPath();
        }

        return resources;
    }


    public static List<URL> scanResource(List<String> resourceLocations, List<String> extNames, boolean recursive) {
        List<URL> resourceUrls = new ArrayList<>(resourceLocations.size());
        for(String resourceLocation : resourceLocations) {
            File resource = new File(resourceLocation);

            scanResource(null, resource, extNames, recursive, resourceUrls);
        }

        if(LOGGER.isDebugEnabled())
            LOGGER.debug("Found '{}' resources under folder '{}'.", resourceUrls.size(), resourceLocations);

        return resourceUrls;
    }

    private static void scanResource(File resourceDir, File subResource, List<String> extNames, boolean recursive, List<URL> urls) {
        if(subResource.exists() == false)
            throw new IllegalArgumentException("resource '" + subResource + "' does not exist.");

        // scan sub resource folder
        if(subResource.isDirectory() && (resourceDir == null || recursive == true))
            scanDir(subResource, extNames, recursive, urls);

        // load sub resource file
        if(subResource.isFile())
            scanFile(resourceDir, subResource, extNames, urls);
    }


    private static void scanFile(File resourceDir, File resourceFile, final List<String> extNames, 
            List<URL> urls) {
        boolean candidate = false;
        if(extNames.size() == 0)
            candidate = true;

        // filter by extName
        String fileName = resourceFile.getName();
        for(String extName : extNames) {
            if(fileName.endsWith(extName)) {
                candidate = true;
                break;
            }
        }

        if(candidate == true) {
            try {
                urls.add(resourceFile.toURI().toURL());    
            } catch (MalformedURLException e) {
                if(resourceDir != null)
                    LOGGER.info("Failed to loader resource '{}' under dir '{}'.", resourceFile, resourceDir);
                else
                    LOGGER.info("Failed to loader resource '{}'.", resourceFile);
            }
        }
    }

    /** 
     * Iterate folder, and sort by filename
     * @param resourceDir
     * @param recursive 
     * @return
     */
    private static void scanDir(File resourceDir, final List<String> extNames, 
            boolean recursive, List<URL> urls) {
        File[] subResources = resourceDir.listFiles();
        if(subResources == null || subResources.length == 0)
            return;

        // force to sort by alphabetical order of filename
        Arrays.sort(subResources,  new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                if (o1.isDirectory() && o2.isFile())
                    return -1;
                if (o1.isFile() && o2.isDirectory())
                    return 1;
                return o1.getName().compareTo(o2.getName());
            }
        } );

        for(int i=0; i<subResources.length; i++) {
            File subResource = subResources[i];

            scanResource(resourceDir, subResource, extNames, recursive, urls);
        }
    }
}