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
package org.framework.demo.service;

import java.io.File;
import java.util.Arrays;

import org.framework.demo.api.DemoService;
import org.framework.demo.api.Request;
import org.framework.demo.api.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoServiceImpl implements DemoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoServiceImpl.class);

    public DemoServiceImpl() {
        super();
        LOGGER.info("getting classpathstr");
        String classpathStr = System.getProperty("java.class.path");
        LOGGER.info("classpathstr {}", classpathStr);
        classpathStr = classpathStr.replace('\\', '/');
        String[] classPathValues = classpathStr.split(File.pathSeparator);
        LOGGER.info("classpath {}", Arrays.asList(classPathValues));
    }

    @Override
    @AspectJ
    public Response<String> process(Request request) {
        LOGGER.info("DemoServiceImpl's input '{}'", request.toString());

        String str = request.getInput().toString();
        return new Response<String>("01", "OK", str);
    }

    @Override
    public String replace(String input) {
        return input;
    }

    @Override
    public String replace(String input, int pos, char c) {
        return input;
    }

    @Override
    public String process2(String request) {
        LOGGER.info("DemoServiceImpl's input '{}'", request);
        return "echo: " + request;
    }

}
