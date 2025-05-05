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

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class SingleEnumeration<E> implements Enumeration<E> {

    private E element;

    public SingleEnumeration(E element) {
        this.element = element;
    }


    public boolean hasMoreElements() {
        return element != null;
    }

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
