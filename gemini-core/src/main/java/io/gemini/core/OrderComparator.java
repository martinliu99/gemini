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
package io.gemini.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OrderComparator implements Comparator<Object> {

    /**
     * Shared default instance of {@code OrderComparator}.
     */
    public static final OrderComparator INSTANCE = new OrderComparator();


    @Override
    public int compare( Object o1,  Object o2) {
        return doCompare(o1, o2);
    }

    private int doCompare( Object o1,  Object o2) {
        int i1 = getOrder(o1);
        int i2 = getOrder(o2);

        return (i1 < i2) ? -1 : (i1 > i2) ? 1 : 0;
    }


    /**
     * Determine the order value for the given object.
     * <p>The default implementation checks against the {@link Ordered} interface
     * through delegating to {@link #findOrder}. Can be overridden in subclasses.
     * @param obj the object to check
     * @return the order value, or {@code Ordered.LOWEST_PRECEDENCE} as fallback
     */
    protected int getOrder( Object obj) {
        if (obj != null) {
            Integer order = findOrder(obj);
            if (order != null) {
                return order;
            }
        }
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Find an order value indicated by the given object.
     * <p>The default implementation checks against the {@link Ordered} interface.
     * Can be overridden in subclasses.
     * @param obj the object to check
     * @return the order value, or {@code null} if none found
     */
    
    protected Integer findOrder(Object obj) {
        return (obj instanceof Ordered) ? ((Ordered) obj).getOrder() : null;
    }


    /**
     * Sort the given List with a default OrderComparator.
     * <p>Optimized to skip sorting for lists with size 0 or 1,
     * in order to avoid unnecessary array extraction.
     * @param list the List to sort
     */
    public static void sort(List<?> list) {
        if (list.size() > 1) {
            Collections.sort(list, INSTANCE);
        }
    }

    /**
     * Sort the given array with a default OrderComparator.
     * <p>Optimized to skip sorting for lists with size 0 or 1,
     * in order to avoid unnecessary array extraction.
     * @param array the array to sort
     * @see java.util.Arrays#sort(Object[], java.util.Comparator)
     */
    public static void sort(Object[] array) {
        if (array.length > 1) {
            Arrays.sort(array, INSTANCE);
        }
    }

    /**
     * Sort the given array or List with a default OrderComparator,
     * if necessary. Simply skips sorting when given any other value.
     * <p>Optimized to skip sorting for lists with size 0 or 1,
     * in order to avoid unnecessary array extraction.
     * @param value the array or List to sort
     * @see java.util.Arrays#sort(Object[], java.util.Comparator)
     */
    public static void sortIfNecessary(Object value) {
        if (value instanceof Object[]) {
            sort( (Object[]) value);
        }
        else if (value instanceof List<?>) {
            sort( (List<?>) value );
        }
    }

}