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
package io.gemini.activation.support;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.gemini.activation.util.FileUtils;

/**
 * Defines the contract for scanning the Gemini AOP launcher directory and collecting its classpath URLs.
 * <p>
 * The launcher directory typically contains a {@code conf/} folder and a {@code lib/} folder
 * with the core AOP framework JARs and their dependencies.
 * </p>
 *
 * @author   martin.liu
 */
public interface LauncherScanner {

    /**
     * Scans the launcher directory and returns the resolved classpath URLs.
     *
     * @return array of URLs representing the launcher classpath
     * @throws IOException if an I/O error occurs during scanning
     */
    URL[] scanClassPathURLs() throws IOException;


    /**
     * Default {@link LauncherScanner} implementation that scans the {@code conf/} and
     * {@code lib/} subdirectories of the launcher path.
     */
    class Default implements LauncherScanner {

        private final Path launchPath;
        private final Path launchFile;


        public Default(Path launchPath, Path launchFile) {
            this.launchPath = launchPath;
            this.launchFile = launchFile;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public URL[] scanClassPathURLs() throws IOException {
            List<Path> launchClassPaths = new ArrayList<>();

            if (launchFile != null)
                launchClassPaths.add(launchFile);

            // 1.include conf folder
            Path confPath = launchPath.resolve("conf");
            if (Files.exists(confPath))
                launchClassPaths.add( confPath );

            // 2.scan lib folder
            Path libPath = launchPath.resolve("lib");
            if (Files.exists(libPath)) {
                try (Stream<Path> stream = Files.list( libPath )) {
                    stream.filter( Files::isRegularFile )
                    .sorted( Comparator.comparing(p -> p.getFileName().toString()) )    // sort by filename
                    .forEach( p -> launchClassPaths.add(p) );
                }
            }

            return FileUtils.toURL(launchClassPaths);
        }
    }
}
