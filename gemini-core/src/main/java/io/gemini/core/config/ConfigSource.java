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

public interface ConfigSource {

    Collection<String> keys();

    boolean containsKey(String key);

    Object getValue(String key);

    // from -D, system property, or ENV

    abstract class AbstractBase implements ConfigSource {}

    enum Dummy implements ConfigSource {

        INSTANCE;

        @Override
        public Collection<String> keys() {
            return Collections.emptyList();
        }

        @Override
        public boolean containsKey(String key) {
            return false;
        }

        @Override
        public Object getValue(String key) {
            return null;
        }

    }

    class MapConfigSource<T> extends AbstractBase {

        private final String sourceName;
        private final Map<String, T> settings;

        protected MapConfigSource(String sourceName, Map<String, T> settings) {
            this.sourceName = StringUtils.hasText(sourceName) ? sourceName : "";

            Assert.notNull(settings, "'settings' must not be null");
            this.settings = settings;
        }

        @Override
        public Collection<String> keys() {
            return this.settings.keySet();
        }

        @Override
        public boolean containsKey(String key) {
            return this.settings.containsKey(key);
        }

        @Override
        public Object getValue(String key) {
            return this.settings.get(key);
        }

        public String getConfigSource() {
            return sourceName;
        }

        @Override
        public String toString() {
            return sourceName;
        }
    }

    class Compound extends AbstractBase {
    
        private final List<ConfigSource> configSources;

        protected Compound(List<ConfigSource> configSources) {
            Assert.notEmpty(configSources, "'configSources' must not be empty.");

            this.configSources = new ArrayList<>(configSources);
        }

        protected Compound(ConfigSource... configSources) {
            Assert.notEmpty(configSources, "'configSources' must not be empty.");

            this.configSources = new ArrayList<>(configSources.length);
            for (ConfigSource configSource : configSources) {
                this.configSources.add(configSource);
            }
        }

        public List<ConfigSource> getConfigSources() {
            return Collections.unmodifiableList(this.configSources);
        }

        @Override
        public Collection<String> keys() {
            Set<String> keys = new LinkedHashSet<>();
            for (ConfigSource configSource : this.configSources) {
                keys.addAll(configSource.keys());
            }
            return keys;
        }

        @Override
        public boolean containsKey(String key) {
            for (ConfigSource source : this.configSources) {
                if (source.containsKey(key))
                    return true;
            }
            return false;
        }

        @Override
        public Object getValue(String key) {
            for (ConfigSource source : this.configSources) {
                if (source.containsKey(key))
                    return source.getValue(key);
            }

            return null;
        }

        @Override
        public String toString() {
            return this.configSources.toString();
        }
    }
}