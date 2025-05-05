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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.gemini.activation.util.FileUtils;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AspectoryScanner {

    Map<String /* AspectoryName */, URL[]> scanResources() throws IOException;


    public class Default implements AspectoryScanner {

        private final Path aspectoriesPath;


        public Default(Path aspectoriesPath) {
            if(Files.exists(aspectoriesPath) == false || Files.isDirectory(aspectoriesPath) == false)
                throw new IllegalArgumentException("Illegal aspectoriesPath:" + aspectoriesPath);

            this.aspectoriesPath = aspectoriesPath;
        }

        @Override
        public Map<String, URL[]> scanResources() throws IOException {
            Map<Path, List<Path>> aspectoryResourcePaths = new LinkedHashMap<>();
            for(Iterator<Path> iterator = Files.list(aspectoriesPath).filter( Files::isDirectory ).iterator(); iterator.hasNext(); ) {
                Path aspectoryPath = iterator.next();
                aspectoryResourcePaths.put(aspectoryPath, this.getAspectoryResourcePaths(aspectoryPath));
            }

            return FileUtils.toURL(aspectoryResourcePaths);
        }

        private List<Path> getAspectoryResourcePaths(Path aspectoryPath) throws IOException {
            List<Path> resourcePaths = new ArrayList<>();

            Path confPath = aspectoryPath.resolve("conf");
            if(Files.exists(confPath))
                resourcePaths.add( confPath );

            Path aspectsPath = aspectoryPath.resolve("aspects");
            if(Files.exists(aspectsPath))
                scanPath(Files.list(aspectsPath), resourcePaths);

            Path libPath = aspectoryPath.resolve("lib");
            if(Files.exists(libPath))
                scanPath(Files.list(libPath), resourcePaths);

            return resourcePaths;
        }

        private void scanPath(Stream<Path> pathStream, List<Path> resourcePaths) {
            pathStream.filter( Files::isRegularFile )
            .sorted( Comparator.comparing(p -> p.getFileName().toString()) )
            .collect( Collectors.toCollection(() -> resourcePaths) );
        }
    }


    public class ClassesFolder implements AspectoryScanner {

        private final Set<String> scannedClassFolders;


        public ClassesFolder( ) {
            this.scannedClassFolders = new LinkedHashSet<>();
            this.scannedClassFolders.add("/classes");
            this.scannedClassFolders.add("/test-classes");
        }

        /* 
         * @see io.gemini.bootstrap.support.AspectoryScanner#scanResources() 
         */
        @Override
        public Map<String, URL[]> scanResources() throws IOException {
//          // retrieve folder from classpath
          List<Path> candidatePaths = new ArrayList<>();
          for(String classPath : getClassPaths()) {
              for(String suffix : scannedClassFolders) {
                  if(classPath.endsWith(suffix))
                      candidatePaths.add( Paths.get(classPath) );
              }
          }

          return candidatePaths == null ? Collections.emptyMap() : 
              Collections.singletonMap("Classes-Folder-Aspectory", FileUtils.toURL(candidatePaths));
        }

        private List<String> getClassPaths() {
            String classpathStr = System.getProperty("java.class.path");
            classpathStr = classpathStr.replace('\\', '/');
            String[] classPathValues = classpathStr.split(File.pathSeparator);

            List<String> classPaths = new ArrayList<>(classPathValues.length);
            for(String classPath : classPathValues) {
                classPath = classPath.trim();
                classPath = classPath.charAt(classPath.length()-1) == File.pathSeparatorChar ? classPath.substring(0, classPath.length()-1) : classPath;
                classPaths.add(classPath);
            }

            return classPaths;
        }
    }


    public class Compound implements AspectoryScanner {

        private final AspectoryScanner[] aspectoryScanners;


        private Compound(AspectoryScanner... aspectoryScanners) {
            this.aspectoryScanners = aspectoryScanners;
        }

        public static AspectoryScanner of(AspectoryScanner... aspectoryScanners) {
            return new Compound(aspectoryScanners);
        }

        /* 
         * @see io.gemini.bootstrap.support.AspectoryScanner#scanResources() 
         */
        @Override
        public Map<String, URL[]> scanResources() throws IOException {
            Map<String, URL[]> resources = new LinkedHashMap<>();
            for(AspectoryScanner aspectoryScanner : aspectoryScanners) {
                resources.putAll( aspectoryScanner.scanResources() );
            }

            return resources;
        }
    }
}
