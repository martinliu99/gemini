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
package io.gemini.aop.agent.classloader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import io.gemini.aop.agent.bootstraper.AopBootstraperConfig;

public class AgentClassLoaderTests {

    public static void main(String[] args) throws InterruptedException, ClassNotFoundException, IOException {
        TestAopBootstraperConfig aopBootstraperConfig = new TestAopBootstraperConfig();
        AgentClassLoader agentClassLoader = new AgentClassLoader(aopBootstraperConfig.scanAgentLibFolder("C:\\_martin_projects\\infra\\gemini\\gemini-releases\\lib"), 
                AgentClassLoaderTests.class.getClassLoader());

        Enumeration<URL> urls = agentClassLoader.getResources("META-INF/INDEX.LIST");
        while(urls.hasMoreElements()) {
            System.out.println("Found: " + urls.nextElement());
        }

    }

    static class TestAopBootstraperConfig extends AopBootstraperConfig {

        public URL[] scanAgentLibFolder(String location) throws MalformedURLException {
            List<URL> libFiles = new ArrayList<>();
            this.doScanAgentLibFolder(location, libFiles);
            return libFiles.toArray(new URL[] {});
        }
    }
}
