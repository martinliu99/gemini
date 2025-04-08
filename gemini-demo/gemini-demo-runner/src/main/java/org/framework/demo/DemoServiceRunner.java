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
package org.framework.demo;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoServiceRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoServiceRunner.class);

    public static void main(String[] args) {
        long startedAt = System.nanoTime();
        LOGGER.info("started to init class loader");

        for(int i = 0; i < 2; i++) {
            DemoServiceRunner demoServiceRunner = new DemoServiceRunner();
    
            RunnerClassLoader classLoader = new RunnerClassLoader(loadResource(), DemoServiceRunner.class.getClassLoader());
            demoServiceRunner.doInvoke(classLoader);

            LOGGER.info("started application by classloader '{}' in {} seconds.", classLoader, (System.nanoTime() - startedAt) / 1e9);
        }

        {
            startedAt = System.nanoTime();

            DemoServiceRunner demoServiceRunner = new DemoServiceRunner();
    
            RunnerClassLoader classLoader = new RunnerClassLoader2(loadResource(), DemoServiceRunner.class.getClassLoader());
            demoServiceRunner.doInvoke(classLoader);

            LOGGER.info("started application by classloader '{}' in {} seconds.", classLoader, (System.nanoTime() - startedAt) / 1e9);
        }
    }

    private void doInvoke(RunnerClassLoader classLoader) {
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            Class<?> requestType = classLoader.loadClass("org.framework.demo.api.Request");
            LOGGER.info("get class {}", requestType);

            List<String> input = new ArrayList<>(2);
            input.add("Hello");
            input.add("World");
            Object request = requestType.getDeclaredConstructor(List.class).newInstance(input);

            Class<?> type = classLoader.loadClass("org.framework.demo.service.DemoServiceImpl");
            LOGGER.info("get class {}", requestType);
            {
                Method method = type.getDeclaredMethod("process", requestType);
                LOGGER.info("get method {}", method);

                Object service = type.getDeclaredConstructor().newInstance();
                LOGGER.info("newInstance {}", service);

                LOGGER.info("started to call process()");
                Object result = method.invoke(service, request);
                LOGGER.info("call process(): {}", result );
            }
        
            {
                Method method = type.getDeclaredMethod("process2", String.class);
                Object service = type.getDeclaredConstructor().newInstance();

                LOGGER.info("started to call process2()");
                Object result = method.invoke(service, "runner");
                LOGGER.info("call process2(): {}", result );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static URL[] loadResource() {
        List<URL> urls = new ArrayList<>();
        try {
            String pathname = "../../gemini-releases/demoapps/gemini-demo-service";
            File lib = new File(pathname + "/lib").getCanonicalFile();;
            if(lib.exists() == false) {
                throw new RuntimeException("unexisted dir: " + lib.getAbsolutePath());
            }

            if(lib.isDirectory()) {
                File[] jarFiles = lib.listFiles(new FilenameFilter() {  
                    public boolean accept(File dir, String name) {  
                        return name.endsWith(".jar") || name.endsWith(".zip");  
                    }  
                });

                for(int i=0; i<jarFiles.length; i++) {
                    try {
                        urls.add(jarFiles[i].toURI().toURL());
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return urls.toArray(new URL[] {});
    }
}
