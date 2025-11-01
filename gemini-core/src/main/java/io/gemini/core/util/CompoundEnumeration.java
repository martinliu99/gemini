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

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class CompoundEnumeration<E> implements Enumeration<E> {

    private final Iterator<Enumeration<E>> iterator;
    private Enumeration<E> currentEnum;

    public CompoundEnumeration(Collection<Enumeration<E>> enums) {
        this.iterator = enums.iterator();
    }

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

    public E nextElement() {
        if (!next()) {
            throw new NoSuchElementException();
        }
        return currentEnum.nextElement();
    }
}