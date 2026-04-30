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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Ordered properties keeps the line order in properties file with given charset, such as UTF-8.
 * This class is used internally to load properties file.
 * 
 * @author martin.liu
 *
 */
public class OrderedProperties extends Properties {

    /**
     * 
     */
    private static final long serialVersionUID = 4824964602875251275L;

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    protected OrderedProperties orderedDefaults;
    protected Set<Object> keys = new LinkedHashSet<>();


    /**
     * Constructs an empty {@code OrderedProperties}.
     */
    public OrderedProperties() { super(); }

    /**
     * Constructs an {@code OrderedProperties} with the given default properties.
     *
     * @param defaultProps the default properties
     */
    public OrderedProperties(OrderedProperties defaultProps) {
        super(defaultProps); // super.defaults = defaultProps;
        this.orderedDefaults = defaultProps;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Enumeration<?> propertyNames() {
        return keys();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> stringPropertyNames() {
        Set<String> allKeys = new LinkedHashSet<>();
        for (Object key : this.keys) {
            if (key instanceof String)
                allKeys.add( (String) key);
        }

        return allKeys;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Enumeration<Object> keys() {
        return Collections.enumeration(this.keys);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Object> keySet() {
        return Collections.unmodifiableSet(this.keys);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Object put(Object key, Object value) {
        keys.add(key);
        return super.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Object remove(Object key) {
        keys.remove(key);
        return super.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void putAll(Map<?, ?> values) {
        keys.addAll(values.keySet());
        super.putAll(values);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void clear() {
        keys.clear();
        super.clear();
    }

    /**
     * Loads properties from the given input stream using UTF-8 encoding.
     *
     * @param inStream the input stream to load from (may be {@code null})
     * @throws IOException if an I/O error occurs
     */
    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        if (inStream == null)
            return;

        this.load(inStream, UTF_8);
    }

    /**
     * load input stream with given charset
     * 
     * @param inStream      input steam contains properties
     * @param charset       charset used to read input stream
     * @throws IOException  possible exceptions when reading input stream
     */
    public synchronized void load(InputStream inStream, Charset charset) throws IOException {
        if (inStream == null)
            return;

        if (charset == null)
            charset = UTF_8;

        Reader reader = new InputStreamReader(inStream, charset);

        this.load(reader);
    }
}