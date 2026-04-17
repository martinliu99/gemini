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
package io.gemini.core.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.gemini.core.util.Assert;
import io.gemini.core.util.StringUtils;

/**
 * Defines the contract for a single configuration source (e.g., a properties file or a map).
 * <p>
 * Implementations include {@link Dummy} (empty), {@link MapConfigSource} (backed by a {@link java.util.Map}),
 * and {@link Compound} (aggregates multiple sources with first-match semantics).
 * </p>
 *
 * @author   martin.liu
 */
public interface ConfigSource {

    /**
     * Returns all configuration keys in this source.
     *
     * @return collection of all keys
     */
    Collection<String> keys();

    /**
     * Returns {@code true} if this source contains the given key.
     *
     * @param key the configuration key to check
     * @return {@code true} if the key exists
     */
    boolean containsKey(String key);

    /**
     * Returns the raw value for the given key, or {@code null} if absent.
     *
     * @param key the configuration key
     * @return the raw value, or {@code null}
     */
    Object getValue(String key);


    // from -D, system property, or ENV


    /** 
     * Abstract base class for {@link ConfigSource} implementations. 
     */
    abstract class AbstractBase implements ConfigSource {}

 
    /**
     * A no-op {@link ConfigSource} that always returns empty results.
     * Used as a safe default when no real configuration source is available.
     */
    enum Dummy implements ConfigSource {

        INSTANCE;

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<String> keys() {
            return Collections.emptyList();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean containsKey(String key) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getValue(String key) {
            return null;
        }

    }

    /**
     * A {@link ConfigSource} backed by a {@link java.util.Map}.
     *
     * @param <T> the value type of the backing map
     */
    class MapConfigSource<T> extends AbstractBase {

        private final String sourceName;
        private final Map<String, T> settings;

        /**
         * Constructs a {@code MapConfigSource} with the given source name and backing map.
         *
         * @param sourceName a human-readable name for this configuration source
         * @param settings   the map of configuration key-value pairs
         */
        protected MapConfigSource(String sourceName, Map<String, T> settings) {
            this.sourceName = StringUtils.hasText(sourceName) ? sourceName : "";

            Assert.notNull(settings, "'settings' must not be null");
            this.settings = settings;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<String> keys() {
            return this.settings.keySet();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean containsKey(String key) {
            return this.settings.containsKey(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getValue(String key) {
            return this.settings.get(key);
        }

        /**
         * Returns the name of this configuration source.
         *
         * @return the source name
         */
        public String getConfigSource() {
            return sourceName;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return sourceName;
        }
    }

    /**
     * A {@link ConfigSource} that aggregates multiple sources with first-match semantics.
     * Keys are looked up in each source in order; the first source containing the key wins.
     */
    class Compound extends AbstractBase {
    
        private final List<ConfigSource> configSources;

        /**
         * Constructs a {@code Compound} source from a list of delegate sources.
         *
         * @param configSources the list of sources to aggregate (may be {@code null})
         */
        protected Compound(List<ConfigSource> configSources) {
            this.configSources = configSources == null 
                    ? Collections.emptyList() : new ArrayList<>(configSources);
        }

        /**
         * Constructs a {@code Compound} source from a varargs array of delegate sources.
         *
         * @param configSources the sources to aggregate
         */
        protected Compound(ConfigSource... configSources) {
            this.configSources = new ArrayList<>(configSources.length);
            for (ConfigSource configSource : configSources) {
                this.configSources.add(configSource);
            }
        }

        /**
         * Returns an unmodifiable view of the delegate config sources.
         *
         * @return the list of delegate sources
         */
        public List<ConfigSource> getConfigSources() {
            return Collections.unmodifiableList(this.configSources);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<String> keys() {
            Set<String> keys = new LinkedHashSet<>();
            for (ConfigSource configSource : this.configSources) {
                keys.addAll(configSource.keys());
            }
            return keys;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean containsKey(String key) {
            for (ConfigSource source : this.configSources) {
                if (source.containsKey(key))
                    return true;
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getValue(String key) {
            for (ConfigSource source : this.configSources) {
                if (source.containsKey(key))
                    return source.getValue(key);
            }

            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return this.configSources.toString();
        }
    }
}