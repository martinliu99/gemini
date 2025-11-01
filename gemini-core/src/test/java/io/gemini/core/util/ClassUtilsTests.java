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
package io.gemini.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.description.type.TypeDescription;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class ClassUtilsTests {

    private static Logger LOGGER = LoggerFactory.getLogger(ClassUtilsTests.class);

    @Test
    public void name() {
        for (Class<?> clazz : Arrays.asList(int.class, int[].class, int[][][].class, String.class, String[][].class, List.class)) {
            TypeDescription type = TypeDescription.ForLoadedType.of(clazz);
            LOGGER.info("Class'{}' details: name '{}', descriptor '{}', type name '{}, canonical name '{}', internal name '{}', actual name '{}'", 
                    clazz, type.getName(), type.getDescriptor(), type.getTypeName(), type.getCanonicalName(), type.getInternalName(), type.getActualName());
        }
    }

    @Test
    public void forName() {
        for (Class<?> expectedType : new Class<?>[] {
            int.class, int[][][].class,
            String.class, String[].class,
            List.class,
        }) {
            String typeName = TypeDescription.ForLoadedType.of(expectedType).getTypeName();
            Class<?> loadedType = null;

            try {
                loadedType = ClassUtils.forName(typeName, false, null);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            LOGGER.info("Type '{}' is loaded, '{}'.", typeName, loadedType);

            assertThat(loadedType).isEqualTo(expectedType);
        }
    }
}
