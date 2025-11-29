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
package io.gemini.aop.factory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.AdvisorFactory;
import io.gemini.aop.AopContext;
import io.gemini.core.util.MethodUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;


class CompoundAdvisorFactory implements AdvisorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompoundAdvisorFactory.class);


    private final FactoriesContext factoriesContext;

    private final Map<FactoryContext, DefaultAdvisorFactory> advisorFactoryMap;


    public CompoundAdvisorFactory(AopContext aopContext) {
        long startedAt = System.nanoTime();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("^Creating CompoundAdvisorFactory.");


        this.factoriesContext = new FactoriesContext(aopContext);

        Map<String, FactoryContext> factoryContextMap = factoriesContext.getFactoryContextMap();

        this.advisorFactoryMap = createAdvisorFactoryMap(aopContext, factoriesContext, factoryContextMap);


        if (aopContext.getDiagnosticLevel().isSimpleEnabled() && LOGGER.isInfoEnabled())
            LOGGER.info("$Took '{}' seconds to create CompoundAdvisorFactory, {}", 
                    (System.nanoTime() - startedAt) / 1e9,
                    StringUtils.join(getAdvisorSpecNum().entrySet(), 
                            entry -> entry.getKey() + ": " + entry.getValue() + " AdvisorSpecs", "\n  ", "\n  ", "\n") 
            );
    }

    private Map<FactoryContext, DefaultAdvisorFactory> createAdvisorFactoryMap(AopContext aopContext, 
            FactoriesContext factoriesContext,
            Map<String, FactoryContext> factoryContextMap) {
        Map<FactoryContext, DefaultAdvisorFactory> advisorFactoryMap = new LinkedHashMap<>();
        for (FactoryContext factoryContext : factoryContextMap.values()) {
            String factoryName = factoryContext.getFactoryName();

            // filter aspect
            if (factoriesContext.isEnabledFactory(factoryName) == false) 
                return null;

            // create AdvisorFactory
            DefaultAdvisorFactory advisorFactory = new DefaultAdvisorFactory(factoryContext);
            advisorFactoryMap.put(factoryContext, advisorFactory);
        }

        return advisorFactoryMap;
    }



    @Override
    public Map<String, Integer> getAdvisorSpecNum() {
        return this.advisorFactoryMap.values().stream()
                .flatMap( e -> 
                    e.getAdvisorSpecNum().entrySet().stream() )
                .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );
    }


    @Override
    public Map<? extends MethodDescription, List<? extends Advisor>> getAdvisors(TypeDescription typeDescription, 
            ClassLoader joinpointClassLoader, JavaModule javaModule) {
        Map<MethodDescription, List<Advisor>> methodAdvisorMap = new LinkedHashMap<>();
        // collect advisors per method
        for (Entry<FactoryContext, DefaultAdvisorFactory> entry: advisorFactoryMap.entrySet()) {
            FactoryContext factoryContext = entry.getKey();
            AdvisorFactory advisorFactory = entry.getValue();

            // diagnostic log
            String typeName = typeDescription.getTypeName();
            if (factoryContext.getAopContext().isDiagnosticClass(typeName) && LOGGER.isInfoEnabled())
                LOGGER.info("Getting Advisors for type '{}' loaded by ClassLoader '{}' from AdvisorFactory '{}'.", 
                        typeName, joinpointClassLoader, factoryContext.getFactoryName());

            // get advisors per AdvisorFactory
            Map<? extends MethodDescription, List<? extends Advisor>> advisorMap = advisorFactory
                    .getAdvisors(typeDescription, joinpointClassLoader, javaModule);

            if (factoryContext.getAopContext().isDiagnosticClass(typeName) && LOGGER.isInfoEnabled()) {
                if (advisorMap.size() == 0)
                    LOGGER.info("Did not get Advisors for type '{}' loaded by ClassLoader '{}' from AdvisorFactory '{}'.",
                            typeName, joinpointClassLoader, factoryContext.getFactoryName()
                    );
                else
                    LOGGER.info("Got Advisors for type '{}' in AdvisorFactory, \n"
                            + "  AdvisorFactory: {} \n"
                            + "  ClassLoader: {} \n"
                            + "  {} ",
                            typeName, 
                            factoryContext.getFactoryName(),
                            joinpointClassLoader, 
                            StringUtils.join(
                                    advisorMap.entrySet(), 
                                    methodAdvisorEntry -> 
                                        new StringBuilder("Method: ")
                                            .append( MethodUtils.getMethodSignature( methodAdvisorEntry.getKey() ) )
                                            .append("\n  Advices: ")
                                            .append( StringUtils.join(methodAdvisorEntry.getValue(), Advisor::getAdvisorName, "\n    ", "\n    ", "\n") ),
                                    "\n  "
                            )
                    );
            }


            // merge advisors
            for (Entry<? extends MethodDescription, List<? extends Advisor>> methodAdvisorEntry : advisorMap.entrySet()) {
                methodAdvisorMap
                .computeIfAbsent(methodAdvisorEntry.getKey(), key -> new ArrayList<>() )
                .addAll(methodAdvisorEntry.getValue());
            }
        }

        return new LinkedHashMap<MethodDescription, List<? extends Advisor>>(methodAdvisorMap);
    }

    @Override
    public void close() throws IOException {
        for (Closeable closeable : advisorFactoryMap.values()) {
            closeable.close();
        }

        this.factoriesContext.close();
    }
}
