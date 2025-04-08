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
package io.gemini.aop.agent.bootstraper;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class AopBootstraperConfig {

    private static final String PATH_SEPERATOR = File.separator;


	private final long agentStartedAt;
	private final String agentArgs;
    private final String agentCodeLocation;


    public AopBootstraperConfig() {
        this("");
    }

    public AopBootstraperConfig(String agentArgs) {
        this(agentArgs, () -> {
            CodeSource codeSource = AopBootstraperConfig.class.getProtectionDomain().getCodeSource();
            try {
                return new File(codeSource.getLocation().toURI()).getParentFile().getPath() + PATH_SEPERATOR;
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to find agent code location", t);
            }
        });
    }

    public AopBootstraperConfig(String agentArgs, String agentCodeLocation) {
        this(agentArgs, () -> agentCodeLocation);
    }

    private AopBootstraperConfig(String agentArgs, Supplier<String> agentCodeLocation) {
        this.agentStartedAt = System.nanoTime();
        this.agentArgs = agentArgs;
        this.agentCodeLocation = agentCodeLocation.get();
    }

    public long getAgentStartedAt() {
        return agentStartedAt;
    }

    public String getAgentArgs() {
        return agentArgs;
    }

    public String getAgentCodeLocation() {
    	return this.agentCodeLocation;
    }

    public URL[] getAgentResources() {
        List<URL> libFiles = new ArrayList<>();

        try {
            // 1.add conf folder
            this.addAgentConfFolder(libFiles);

            // 2.scan lib folder
            this.doScanAgentLibFolder(this.getAgentLibLocation(), libFiles);
        } catch(Exception e) {
            System.err.println("Failed to scan agent resources, error reason: " + e);
            e.printStackTrace();
        }

        return libFiles.toArray(new URL[] {});
    }

    private String getAgentConfLocation() {
        return this.agentCodeLocation + PATH_SEPERATOR + "conf";
    }

    private String getAgentLibLocation() {
        return this.agentCodeLocation + PATH_SEPERATOR + "lib";
    }

    private void addAgentConfFolder(List<URL> libFiles) throws MalformedURLException {
        // 1.get config path from settings
        String confLocation = this.getAgentConfLocation();
        File confPath = new File(confLocation);
        libFiles.add( confPath.toURI().toURL() );
    }

    protected void doScanAgentLibFolder(String location, List<URL> libFiles) throws MalformedURLException {
        File file = new File(location);
        if(file.exists() == false) {
            return;
        }   

        if(file.isFile()) {
            libFiles.add(file.toURI().toURL());
        }

        // load files under directory
        if(file.isDirectory()) {
            File[] jarFiles = file.listFiles(new FilenameFilter() {  
                public boolean accept(File dir, String name) {  
                    return true;
                }  
            });

            if(jarFiles != null) {
                for(int i=0; i<jarFiles.length; i++) {
                    libFiles.add(jarFiles[i].toURI().toURL());
                }
            }
        }
    }

    public String getBootstraperClassName() {
        return "io.gemini.aop.bootstraper.DefaultAopBootstraper";
    }
}
