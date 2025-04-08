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

import java.lang.instrument.Instrumentation;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.gemini.aop.agent.classloader.AgentClassLoader;

public class AgentStarter {

//    private boolean launched = false;

    // TODO: launch at runtime or multiple time
    public void bootstrapAop(String agentArgs, Instrumentation inst) {
        System.out.println("Launching Gemini Agent at " + this.currentDate());


        try {
            // initialize AopBootstraperConfig
            AopBootstraperConfig aopBootstraprConfig = new AopBootstraperConfig(agentArgs);

            // initialize class loader
            @SuppressWarnings("resource")
            AgentClassLoader agentClassLoader = new AgentClassLoader(aopBootstraprConfig.getAgentResources(), this.getClass().getClassLoader());

            // load bootstraper class
            String bootstraperClassName = aopBootstraprConfig.getBootstraperClassName();
            Class<?> bootstraperClass = agentClassLoader.loadClass(bootstraperClassName);

            AopBootstraper starter = (AopBootstraper) bootstraperClass.getDeclaredConstructor().newInstance();
            starter.start(inst, aopBootstraprConfig);
        } catch (Throwable t) {
            // TODO:
//            throw new WeaverException("", t);
            System.out.println("Failed to start Gemini Agent at " + this.currentDate() + ", error reason: " + t);
            t.printStackTrace();
        }
    }

    private String currentDate() {
        SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
        Date date = new Date(System.currentTimeMillis());
        return formatter.format(date);
    }
}
