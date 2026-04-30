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
package io.gemini.core.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility class providing a formatted current timestamp string.
 *
 * @author   martin.liu
 */
public abstract class DateUtils {

    /**
     * Returns the current date and time formatted as {@code "yyyy-MM-dd HH:mm:ss,SSS"}.
     *
     * @return the formatted timestamp string
     */
    public static String now() {
        ZoneId systemDefault = ZoneId.systemDefault();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                .withZone( systemDefault );
        return formatter.format( LocalDate.now( systemDefault ) );
    }
}
