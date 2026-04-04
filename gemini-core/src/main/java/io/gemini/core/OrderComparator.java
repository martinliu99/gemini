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
package io.gemini.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.gemini.api.annotation.Order;

public enum OrderComparator implements Comparator<Object> {

    INSTANCE;


    @Override
    public int compare( Object o1,  Object o2) {
        return doCompare(o1, o2);
    }

    private int doCompare( Object o1,  Object o2) {
        int i1 = getOrder(o1);
        int i2 = getOrder(o2);

        return (i1 < i2) ? -1 : (i1 > i2) ? 1 : 0;
    }

    @SuppressWarnings("rawtypes")
    protected int getOrder(Object obj) {
        if (obj == null)
            return Order.LOWEST_PRECEDENCE;

        if (obj instanceof Ordered)
            return ((Ordered) obj).getOrder();
        else if (obj instanceof Class)
            return getOrder( (Class)obj );
        else
            return getOrder( obj.getClass() );
    }

    private int getOrder(Class<?> clazz) {
        Order orderAnnotation = clazz.getAnnotation(Order.class);
        return orderAnnotation != null ? orderAnnotation.value() : Order.LOWEST_PRECEDENCE;
    }

    public static void sort(List<?> list) {
        if (list.size() > 1) {
            Collections.sort(list, INSTANCE);
        }
    }

    public static void sort(Object[] array) {
        if (array.length > 1) {
            Arrays.sort(array, INSTANCE);
        }
    }

    public static void sortIfNecessary(Object value) {
        if (value instanceof Object[]) {
            sort( (Object[]) value);
        }
        else if (value instanceof List<?>) {
            sort( (List<?>) value );
        }
    }
}