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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceholderHelperTests {

    private static Logger LOGGER = LoggerFactory.getLogger(PlaceholderHelperTests.class);

//    @Test
    public void test() {
        Map<String, String> valueMap = new HashMap<>();
        valueMap.put("var1", "${var11}+1");
        valueMap.put("var11", "11");
        valueMap.put("var2", "${var1}");
        valueMap.put("2", "2");

        PlaceholderHelper placeholderHelper = new PlaceholderHelper.Builder().build(valueMap);
        String template = "${var1} and ${var${2}}";

        String value = placeholderHelper.replace(template);
        LOGGER.info("test()");
        LOGGER.info("template: '{}'", template);
        LOGGER.info("value: '{}'", value);
        LOGGER.info("");
    }
    
    @Test
    public void testVarInDefaultValue() {
        Map<String, String> valueMap = new HashMap<>();
        valueMap.put("var1", "${var11:${var12}}/log");
        valueMap.put("var12", "12");

        PlaceholderHelper placeholderHelper = new PlaceholderHelper.Builder().build(valueMap);
        String template = "${var1}";

        String value = placeholderHelper.replace(template);
        LOGGER.info("testVarInDefaultValue()");
        LOGGER.info("template: '{}'", template);
        LOGGER.info("value: '{}'", value);
        LOGGER.info("");
    }

//    @Test
    public void testIllegalVariable() {
        Map<String, String> valueMap = new HashMap<>();

        PlaceholderHelper placeholderHelper = new PlaceholderHelper.Builder().build(valueMap);
        String template = "${var1} and ${var${2}}";

        String value = placeholderHelper.replace(template);
        LOGGER.info("testIllegalVariable()");
        LOGGER.info("template: '{}'", template);
        LOGGER.info("value: '{}'", value);
        LOGGER.info("");
    }
}
