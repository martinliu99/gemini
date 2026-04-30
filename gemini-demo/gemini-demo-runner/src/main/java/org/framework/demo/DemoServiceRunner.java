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
package org.framework.demo;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import org.framework.demo.classloader.RunnerClassLoader;
import org.framework.demo.classloader.RunnerClassLoader2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demo runner that exercises the AOP-instrumented {@code DemoServiceImpl} via custom class loaders.
 * <p>
 * Creates multiple {@link RunnerClassLoader} and {@link RunnerClassLoader2} instances to simulate
 * a multi-ClassLoader environment, verifying that Gemini correctly instruments classes loaded
 * by different class loaders.
 * </p>
 *
 * @author   martin.liu
 */
public class DemoServiceRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoServiceRunner.class);


    public static void main(String[] args) {
        long startedAt = System.nanoTime();
        if (LOGGER.isInfoEnabled())
            LOGGER.info("started to init class loader");

        // 1.instrument target classes multiple times with different class loader instance.
        for (int i = 0; i < 2; i++) {
            DemoServiceRunner demoServiceRunner = new DemoServiceRunner();
    
            RunnerClassLoader classLoader = new RunnerClassLoader(loadResource(), DemoServiceRunner.class.getClassLoader());
            demoServiceRunner.doInvoke(classLoader);

            if (LOGGER.isInfoEnabled())
                LOGGER.info("started application by classloader '{}' in {} seconds. \n", 
                        classLoader, (System.nanoTime() - startedAt) / 1e9);
        }

        {
            startedAt = System.nanoTime();

            DemoServiceRunner demoServiceRunner = new DemoServiceRunner();
    
            RunnerClassLoader classLoader = new RunnerClassLoader2(loadResource(), DemoServiceRunner.class.getClassLoader());
            demoServiceRunner.doInvoke(classLoader);

            if (LOGGER.isInfoEnabled())
                LOGGER.info("started application by classloader '{}' in {} seconds. \n", 
                        classLoader, (System.nanoTime() - startedAt) / 1e9);
        }


        // 2.instrument special types
        // JDK classes
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        Future<?> task = threadPoolExecutor.submit( new Runnable() {

            @Override
            public void run() {
                if (LOGGER.isInfoEnabled())
                    LOGGER.info("Executing task...");
            }
        } );
        try {
            task.get();
        } catch (Exception e) {
            /* do nothing */
        } finally {
            threadPoolExecutor.shutdownNow();
        }


        // 3.instrument special methods
        // native method
        byte[] data = "This is a long text that needs compression".getBytes(Charset.defaultCharset());

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();

        deflater.end();
    }

    private void doInvoke(RunnerClassLoader classLoader) {
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            // prepare arguments
            List<String> input = new ArrayList<>(2);
            input.add("Hello");
            input.add("World");

            Class<?> requestType = classLoader.loadClass("org.framework.demo.api.Request");
            Object request = requestType.getDeclaredConstructor(List.class).newInstance(input);

            Class<?> serviceType = classLoader.loadClass("org.framework.demo.service.DemoServiceImpl");

            {
                Method method = serviceType.getDeclaredMethod("process", requestType);
                Object service = serviceType.getDeclaredConstructor().newInstance();
                method.invoke(service, request);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not invoke target class.", e);
        }
    }


    private static URL[] loadResource() {
        List<URL> urls = new ArrayList<>();
        try {
            String pathname = "../../gemini-release/demoapps/gemini-demo-service";
            File lib = new File(pathname + "/lib").getCanonicalFile();;
            if (lib.exists() == false) {
                throw new RuntimeException("unexisted dir: " + lib.getAbsolutePath());
            }

            if (lib.isDirectory()) {
                File[] jarFiles = lib.listFiles(new FilenameFilter() {  
                    @Override
                    public boolean accept(File dir, String name) {  
                        return name.endsWith(".jar") || name.endsWith(".zip");  
                    }  
                });

                for (int i=0; i<jarFiles.length; i++) {
                    File jarFile = jarFiles[i];
                    try {
                        urls.add(jarFile.toURI().toURL());
                    } catch (MalformedURLException e) {
                        LOGGER.warn("Could not load resoulce '{}'.", jarFile, e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not invoke target class.", e);
        }

        return urls.toArray(new URL[] {});
    }
}
