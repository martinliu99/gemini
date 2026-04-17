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

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An {@link Enumeration} that chains multiple {@link Enumeration} instances together.
 * <p>
 * Used by {@link io.gemini.aop.factory.classloader.AspectClassLoader} to merge resource
 * enumerations from multiple class loaders into a single sequence.
 * </p>
 *
 * @param <E> the element type
 *
 * @author   martin.liu
 */
public class CompoundEnumeration<E> implements Enumeration<E> {

    private final Iterator<Enumeration<E>> iterator;
    private Enumeration<E> currentEnum;

    /**
     * Constructs a {@code CompoundEnumeration} from the given collection of enumerations.
     *
     * @param enums the enumerations to chain together
     */
    public CompoundEnumeration(Collection<Enumeration<E>> enums) {
        this.iterator = enums.iterator();
    }

    /**
     * Returns {@code true} if any of the chained enumerations has more elements.
     *
     * @return {@code true} if more elements are available
     */
    public boolean hasMoreElements() {
        return next();
    }

    private boolean next() {
        if (currentEnum != null && currentEnum.hasMoreElements())
            return true;
        else if (iterator.hasNext()) {
            currentEnum = iterator.next();
            return next();
        } else
            return false;
    }

    /**
     * Returns the next element from the current enumeration, advancing to the next
     * enumeration in the chain if necessary.
     *
     * @return the next element
     * @throws NoSuchElementException if no more elements are available
     */
    public E nextElement() {
        if (!next()) {
            throw new NoSuchElementException();
        }
        return currentEnum.nextElement();
    }
}