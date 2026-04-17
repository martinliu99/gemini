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

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * An {@link Enumeration} that wraps a single element.
 * <p>
 * Returns the element once, then returns {@code false} from {@link #hasMoreElements()}.
 * Used by {@link io.gemini.core.bootstrap.BootstrapClassConfigurer} to wrap single
 * resource URLs in an enumeration.
 * </p>
 *
 * @param <E> the element type
 *
 * @author   martin.liu
 */
public class SingleEnumeration<E> implements Enumeration<E> {

    private E element;

    /**
     * Constructs a {@code SingleEnumeration} wrapping the given element.
     *
     * @param element the single element to wrap (may be {@code null})
     */
    public SingleEnumeration(E element) {
        this.element = element;
    }


    /**
     * Returns {@code true} if the element has not yet been returned.
     *
     * @return {@code true} if the element is available
     */
    public boolean hasMoreElements() {
        return element != null;
    }

    /**
     * Returns the single element and marks it as consumed.
     *
     * @return the element
     * @throws NoSuchElementException if the element has already been returned
     */
    public E nextElement() {
        if (element == null) {
            throw new NoSuchElementException();
        } else {
            E local = element;
            element = null;
            return local;
        }
    }
}
