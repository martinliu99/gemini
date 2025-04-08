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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ResourcesTests {

    private static Logger LOGGER = LoggerFactory.getLogger(ResourcesTests.class);

    @Test
    public void testNonRecursiveScan() {
        List<String> resources = getResourceLocations();
        List<String> extNames = getExtNames();

        List<URL> urls = Resources.scanResource(resources, extNames, false);

        LOGGER.info("{}", urls);
        assertTrue(urls.size() == 1);
    }

    @Test
    public void testRecursiveScan() {
        List<String> resources = getResourceLocations();
        List<String> extNames = getExtNames();

        List<URL> urls = Resources.scanResource(resources, extNames, true);

        LOGGER.info("{}", urls);
        assertTrue(urls.size() == 4);
    }

    private List<String> getResourceLocations() {
        String rootFolder = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        String resource1 = rootFolder + "io/gemini/core/object/resource1.txt";
        String resourceDir = rootFolder + "io/gemini/core/object/resource";
        List<String> resources = new ArrayList<>();
        resources.add(resource1);
        resources.add(resourceDir);
        return resources;
    }

    private List<String> getExtNames() {
        List<String> extNames = new ArrayList<>();
        extNames.add(".txt");
        return extNames;
    }

}
