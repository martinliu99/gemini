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
package io.gemini.activation.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class FileUtils {


    public static Map<String, URL[]> toURL( Map<Path, List<Path>> pathResources) throws MalformedURLException {
        Map<String, URL[]> result = new LinkedHashMap<>(pathResources.size());
        for(Entry<Path, List<Path>> entry : pathResources.entrySet()) {
            result.put(entry.getKey().getFileName().toString(), toURL(entry.getValue()));
        }

        return result;
    }

    /**
     * @param resources
     * @return
     * @throws MalformedURLException 
     */
    public static URL[] toURL(List<Path> resources) throws MalformedURLException {
        URL[] urls = new URL[resources.size()];
        int i = 0;
        for(Path resource : resources) {
            urls[i++] = resource.toUri().toURL();
        }
        return urls;
    }
}