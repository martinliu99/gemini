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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class CollectionUtils {

    public static boolean isEmpty(Collection<?> collection) {
        return (collection == null || collection.isEmpty());
    }
    
    public static boolean isEmpty(Object... collection) {
        return (collection == null || collection.length == 0);
    }
    
    public static boolean isEmpty(Map<?, ?> map) {
        return (map == null || map.isEmpty());
    }



    public static <T> List<T> merge(List<? extends T> list1, List<? extends T> list2) {
        list1 = list1 == null ? Collections.emptyList() : list1;
        list2 = list2 == null ? Collections.emptyList() : list2;

        List<T> all = new ArrayList<>(list1.size() + list2.size());
        all.addAll(list1);
        all.addAll(list2);

        return all;
    }
}
