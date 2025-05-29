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
public interface AspectAppScanner {

    Map<String /* AspectAppName */, URL[]> scanClassPathURLs() throws IOException;


    public class Default implements AspectAppScanner {

        private final Path aspectAppsPath;


        public Default(Path aspectAppsPath) {
            if(Files.exists(aspectAppsPath) == false || Files.isDirectory(aspectAppsPath) == false)
                throw new IllegalArgumentException("Illegal aspectAppsPath:" + aspectAppsPath);

            this.aspectAppsPath = aspectAppsPath;
        }

        @Override
        public Map<String, URL[]> scanClassPathURLs() throws IOException {
            Map<Path, List<Path>> aspectClassPaths = new LinkedHashMap<>();
            for(Iterator<Path> iterator = Files.list(aspectAppsPath).filter( Files::isDirectory ).iterator(); iterator.hasNext(); ) {
                Path aspectAppPath = iterator.next();
                aspectClassPaths.put(aspectAppPath, this.getAspectAppClassPaths(aspectAppPath));
            }

            return FileUtils.toURL(aspectClassPaths);
        }

        private List<Path> getAspectAppClassPaths(Path aspectAppPath) throws IOException {
            List<Path> classPaths = new ArrayList<>();

            Path confPath = aspectAppPath.resolve("conf");
            if(Files.exists(confPath))
                classPaths.add( confPath );

            Path aspectsPath = aspectAppPath.resolve("aspects");
            if(Files.exists(aspectsPath))
                scanPath(Files.list(aspectsPath), classPaths);

            Path libPath = aspectAppPath.resolve("lib");
            if(Files.exists(libPath))
                scanPath(Files.list(libPath), classPaths);

            return classPaths;
        }

        private void scanPath(Stream<Path> pathStream, List<Path> resourcePaths) {
            pathStream.filter( Files::isRegularFile )
            .sorted( Comparator.comparing(p -> p.getFileName().toString()) )
            .collect( Collectors.toCollection(() -> resourcePaths) );
        }
    }


    public class ClassesFolder implements AspectAppScanner {

        private final Set<String> scannedClassFolders;


        public ClassesFolder( ) {
            this.scannedClassFolders = new LinkedHashSet<>();
            this.scannedClassFolders.add("/classes");
            this.scannedClassFolders.add("/test-classes");
        }

        @Override
        public Map<String, URL[]> scanClassPathURLs() throws IOException {
//          // retrieve folder from classpath
          List<Path> candidatePaths = new ArrayList<>();
          for(String classPath : getClassPaths()) {
              for(String suffix : scannedClassFolders) {
                  if(classPath.endsWith(suffix))
                      candidatePaths.add( Paths.get(classPath) );
              }
          }

          return candidatePaths == null ? Collections.emptyMap() : 
              Collections.singletonMap("Classes-Folder-AspectApp", FileUtils.toURL(candidatePaths));
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


    public class Compound implements AspectAppScanner {

        private final AspectAppScanner[] aspectAppScanners;


        private Compound(AspectAppScanner... aspectAppScanners) {
            this.aspectAppScanners = aspectAppScanners;
        }

        public static AspectAppScanner of(AspectAppScanner... aspectAppScanners) {
            return new Compound(aspectAppScanners);
        }

        @Override
        public Map<String, URL[]> scanClassPathURLs() throws IOException {
            Map<String, URL[]> classPathURLs = new LinkedHashMap<>();
            for(AspectAppScanner aspectAppScanner : aspectAppScanners) {
                classPathURLs.putAll( aspectAppScanner.scanClassPathURLs() );
            }

            return classPathURLs;
        }
    }
}
