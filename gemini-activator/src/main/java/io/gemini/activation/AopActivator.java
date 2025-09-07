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
package io.gemini.activation;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

import io.gemini.activation.classloader.DefaultAopClassLoader;
import io.gemini.activation.support.UnpackedArchiveConfig;
import io.gemini.activation.util.ServiceLoaders;
import io.gemini.api.activation.AopLauncher;
import io.gemini.api.activation.LauncherConfig;
import io.gemini.api.classloader.AopClassLoader;

public class AopActivator {

//    private boolean launched = false;

    /**
     * 
     */
    private static final Class<AopActivator> ACTIVATOR_CLASS = AopActivator.class;


    public static void activateAop(String agentArgs, Instrumentation instrumentation) {
        wrap( () -> {
            doActivateAop(agentArgs, instrumentation, null, null);
            return null;
        });
    }

    public static void activateAop(String agentArgs, Instrumentation instrumentation, 
            LauncherConfig launchConfig, AopClassLoader aopClassLoader) {
        wrap( () -> {
            doActivateAop(agentArgs, instrumentation, launchConfig, aopClassLoader);
            return null;
        });
    }

    // TODO: launch at runtime or multiple time
    private static void doActivateAop(String agentArgs, Instrumentation instrumentation, 
            LauncherConfig launchConfig, AopClassLoader aopClassLoader) throws URISyntaxException, IOException {
        // initialize LaunchConfig
        if(launchConfig == null) {
            Path launchFile = Paths.get(ACTIVATOR_CLASS.getProtectionDomain().getCodeSource().getLocation().toURI());
            launchConfig = new UnpackedArchiveConfig(launchFile.getParent(), launchFile, agentArgs);
        }

        // initialize class loader
        aopClassLoader = aopClassLoader != null
                ? aopClassLoader : new DefaultAopClassLoader(launchConfig.getLaunchClassPathURLs(), ACTIVATOR_CLASS.getClassLoader());

        // load launcher class
        AopLauncher aopLauncher = ServiceLoaders.loadClass(AopLauncher.class, aopClassLoader);
        aopLauncher.start(instrumentation, launchConfig, aopClassLoader);
    }

    private static void wrap(Callable<Void> callable) {
        System.out.println("Activating Gemini at " + currentDate() 
            + "(launched JVM at " + formatDate(ManagementFactory.getRuntimeMXBean().getStartTime()) + ")");

        try {
            callable.call();
        } catch (Throwable t) {
            System.out.println("Failed to activate Gemini at " + currentDate() + ", error reason: " + t);
            t.printStackTrace();
        }
    }

    private static String currentDate() {
        return formatDate(System.currentTimeMillis());
    }

    private static String formatDate(long timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                .withZone(ZoneId.systemDefault());
        return formatter.format( Instant.ofEpochMilli(timestamp) );
    }
}
