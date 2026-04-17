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
package io.gemini.activation.util;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Utility class for loading a single SPI service implementation via {@link java.util.ServiceLoader}.
 * Throws {@link IllegalStateException} if zero or more than one implementation is found.
 *
 * @author   martin.liu
 */
public class ServiceLoaders {

    /**
     * Loads exactly one implementation of {@code serviceClass} from the given {@code classLoader}.
     *
     * @param <T>          the service type
     * @param serviceClass the service interface or abstract class
     * @param classLoader  the class loader to search for implementations
     * @return the single service implementation
     * @throws IllegalStateException if zero or more than one implementation is found
     */
    public static <T> T loadClass(Class<T> serviceClass, ClassLoader classLoader) {
        Set<T> services = new LinkedHashSet<>();
        for (Iterator<T> it = ServiceLoader.load(serviceClass, classLoader).iterator(); it.hasNext(); ) {
            services.add(it.next());
        }

        if (services.size() == 0 ) {
            throw new IllegalStateException("Cannot find class implements [" + serviceClass.getName() + "]");
        }
        if (services.size() > 1) {
            throw new IllegalStateException("Found more than one class " + services + " implements [" + serviceClass.getName() + "]");
        }

        return services.iterator().next();
    }
}
