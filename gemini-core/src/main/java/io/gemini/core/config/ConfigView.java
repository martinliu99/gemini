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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import io.gemini.core.config.ConfigSource.Compound;
import io.gemini.core.config.ConfigSource.MapConfigSource;
import io.gemini.core.config.ConfigView.ConfigException.ConfigNotFoundException;
import io.gemini.core.config.ConfigView.Converter.ToBoolean;
import io.gemini.core.config.ConfigView.Converter.ToInteger;
import io.gemini.core.config.ConfigView.Converter.ToString;
import io.gemini.core.config.ConfigView.Converter.ToStringArray;
import io.gemini.core.config.ConfigView.Converter.ToStringList;
import io.gemini.core.config.ConfigView.Converter.ToStringSet;
import io.gemini.core.util.Assert;
import io.gemini.core.util.Converters;
import io.gemini.core.util.OrderedProperties;
import io.gemini.core.util.PlaceholderHelper;
import io.gemini.core.util.StringUtils;

public interface ConfigView {

    Collection<String> keys();

    Collection<String> keys(String keyPrefix);

    boolean containsKey(String key);


    <T> T getValue(String key, Converter<T> converter);

    <T> T getValue(String key, T defaultValue, Converter<T> converter);

    <T> T getValue(String key, Converter<T> converter, boolean resolvePlaceholders);

    <T> T getValue(String key, T defaultValue, Converter<T> converter, boolean resolvePlaceholders);


    Boolean getAsBoolean(String key);

    Boolean getAsBoolean(String key, Boolean defaultValue);

    String getAsString(String key);

    String getAsString(String key, String defaultValue);

    List<String> getAsStringList(String key);

    List<String> getAsStringList(String key, List<String> defaultValue);

    Set<String> getAsStringSet(String key);

    Set<String> getAsStringSet(String key, Set<String> defaultValue);

    String[] getAsStrings(String key);

    String[] getAsStrings(String key, String[] defaultValue);

    Integer getAsInteger(String key);

    Integer getAsInteger(String key, Integer defaultValue);


    interface Converter<T> {

        String VALUE_DELIMITER = ",";

        T convert(Object object);

        enum ToBoolean implements Converter<Boolean> {

            INSTANCE;

            @Override
            public Boolean convert(Object object) {
                
                if (object instanceof Boolean) return (Boolean) object;
                return Boolean.valueOf( object.toString().trim() );
            }
        }

        enum ToString implements Converter<String> {

            INSTANCE;

            @Override
            public String convert(Object object) {
                if (object instanceof String) return (String) object;
                return object.toString().trim();
            }
        }

        enum ToStringList implements Converter<List<String>> {

            INSTANCE;

            @Override
            public List<String> convert(Object object) {
                StringTokenizer st = new StringTokenizer(object.toString(), VALUE_DELIMITER);
                List<String> list = new ArrayList<>(st.countTokens());

                while (st.hasMoreTokens()) {
                    String element = st.nextToken().trim();

                    if (StringUtils.hasText(element))
                        list.add(element);
                }

                return list;
            }
        }

        enum ToStringSet implements Converter<Set<String>> {

            INSTANCE;

            @Override
            public Set<String> convert(Object object) {
                StringTokenizer st = new StringTokenizer(object.toString(), VALUE_DELIMITER);
                Set<String> list = new LinkedHashSet<>(st.countTokens());

                while (st.hasMoreTokens()) {
                    String element = st.nextToken().trim();

                    if (StringUtils.hasText(element))
                        list.add(element);
                }

                return list;
            }
        }

        enum ToStringArray implements Converter<String[]> {

            INSTANCE;

            @Override
            public String[] convert(Object object) {
                StringTokenizer st = new StringTokenizer(object.toString(), VALUE_DELIMITER);
                List<String> value = new ArrayList<>(st.countTokens());

                while (st.hasMoreTokens()) {
                    String element = st.nextToken().trim();

                    if (StringUtils.hasText(element))
                        value.add(element);
                }

                return value.toArray(new String[] {});
            }
        }

        enum ToInteger implements Converter<Integer> {

            INSTANCE;

            @Override
            public Integer convert(Object object) {
                if (object instanceof Integer) return (Integer) object;
                return Integer.valueOf( object.toString().trim() );
            }
        }
    }


    public class ConfigException extends RuntimeException {

        /**
         * 
         */
        private static final long serialVersionUID = -4610045854862146968L;


        public ConfigException(String message) {
            super(message);
        }

        public ConfigException(Throwable t) {
            super(t);
        }

        public ConfigException(String message, Throwable t) {
            super(message, t);
        }


        public static class ConfigNotFoundException extends ConfigException {

            /**
             * 
             */
            private static final long serialVersionUID = 5539369009779917061L;

            public ConfigNotFoundException(String key) {
                super("Config '" + key + "' does not find");
            }

        }
    }


    abstract class AbstractBase implements ConfigView {

        private final PlaceholderHelper placeholderHelper;


        protected AbstractBase() {
            this.placeholderHelper = PlaceholderHelper.create(this);
        }


        @Override
        public abstract Collection<String> keys();

        @Override
        public Collection<String> keys(String keyPrefix) {
            Assert.hasText(keyPrefix, "'keyPrefix' must not be empty");

            Set<String> keys = new LinkedHashSet<>();
            for (String key : this.keys()) {
                if (key != null && key.startsWith(keyPrefix)) {
                    keys.add(key);
                }
            }
            return keys;
        }

        @Override
        public abstract boolean containsKey(String key);


        @Override
        public <T> T getValue(String key, Converter<T> converter) {
            return this.getValue(key, converter, true);
        }

        @Override
        public <T> T getValue(String key, T defaultValue, Converter<T> converter) {
            return this.getValue(key, defaultValue, converter, true);
        }

        @Override
        public <T> T getValue(String key, Converter<T> converter, boolean resolvePlaceholders) {
            if (this.containsKey(key) == false)
                throw new ConfigNotFoundException(key);

            Object value = this.doGetValue(key);
            if (null == value || "".equals(value))
                throw new ConfigNotFoundException(key);

            // try to replace placeholders
            if (resolvePlaceholders == true && value instanceof String) {
                value = this.placeholderHelper.replace( (String)value );
            }

            Assert.notNull(converter, "'converter' must not be null.");
            return converter.convert(value);
        }

        @Override
        public <T> T getValue(String key, T defaultValue, Converter<T> converter, boolean resolvePlaceholders) {
            if (this.containsKey(key) == false)
                return defaultValue;

            Object value = this.doGetValue(key);

            // try to replace placeholders
            if (resolvePlaceholders == true && value instanceof String) {
                value = this.placeholderHelper.replace( (String)value );
            }

            Assert.notNull(converter, "'converter' must not be null.");
            return null == value || "".equals(value) ? defaultValue : converter.convert(value);
        }

        protected abstract Object doGetValue(String key);


        @Override
        public Boolean getAsBoolean(String key) {
            return this.getValue(key, ToBoolean.INSTANCE);
        }

        @Override
        public Boolean getAsBoolean(String key, Boolean defaultValue) {
            return this.getValue(key, defaultValue, ToBoolean.INSTANCE);
        }

        @Override
        public String getAsString(String key) {
            return this.getValue(key, ToString.INSTANCE);
        }

        @Override
        public String getAsString(String key, String defaultValue) {
            return this.getValue(key, defaultValue, ToString.INSTANCE);
        }

        @Override
        public List<String> getAsStringList(String key) {
            return this.getValue(key, ToStringList.INSTANCE);
        }

        @Override
        public List<String> getAsStringList(String key, List<String> defaultValue) {
            return this.getValue(key, defaultValue, ToStringList.INSTANCE);
        }

        @Override
        public Set<String> getAsStringSet(String key) {
            return this.getValue(key, ToStringSet.INSTANCE);
        }

        @Override
        public Set<String> getAsStringSet(String key, Set<String> defaultValue) {
            return this.getValue(key, defaultValue, ToStringSet.INSTANCE);
        }

        @Override
        public String[] getAsStrings(String key) {
            return this.getValue(key, ToStringArray.INSTANCE);
        }

        @Override
        public String[] getAsStrings(String key, String[] defaultValue) {
            return this.getValue(key, defaultValue, ToStringArray.INSTANCE);
        }

        public Integer getAsInteger(String key) {
            return this.getValue(key, ToInteger.INSTANCE);
        }

        public Integer getAsInteger(String key, Integer defaultValue) {
            return this.getValue(key, defaultValue, ToInteger.INSTANCE);
        }
    }


    class Default extends AbstractBase {

        private final ConfigSource configSource;


        protected Default(ConfigSource... configSources) {
            this.configSource = new ConfigSource.Compound(configSources);
        }

        protected Default(List<ConfigSource> configSources) {
            this.configSource = new ConfigSource.Compound(configSources);
        }

        @Override
        public Collection<String> keys() {
            return this.configSource.keys();
        }

        @Override
        public boolean containsKey(String key) {
            Assert.hasText(key, "'key' must not be empty");

            return this.configSource.containsKey(key);
        }


        @Override
        protected Object doGetValue(String key) {
            return this.configSource.getValue(key);
        }
    }


    /**
     * child-first setting retrieval
     */
    class Hirarchical extends Default {

        private final ConfigView parent;


        protected Hirarchical(ConfigView parent, ConfigSource... configSources) {
            super(configSources);

            Assert.notNull(parent, "'parent' must not be null.");
            this.parent = parent;
        }

        protected Hirarchical(ConfigView parent, List<ConfigSource> configSources) {
            super(configSources);

            Assert.notNull(parent, "'parent' must not be null.");
            this.parent = parent;
        }

        @Override
        public Set<String> keys() {
            Set<String> keys = new LinkedHashSet<>();
            keys.addAll(super.keys());
            keys.addAll(parent.keys());

            return keys;
        }

        @Override
        public boolean containsKey(String key) {
            if (super.containsKey(key) == true)
                return true;

            return parent.containsKey(key);
        }

        @Override
        public <T> T getValue(String key, Converter<T> converter, boolean resolvePlaceholders) {
            if (super.containsKey(key))
                return super.getValue(key, converter, resolvePlaceholders);

            return parent.getValue(key, converter, resolvePlaceholders);
        }

        @Override
        public <T> T getValue(String key, T defaultValue, Converter<T> converter, boolean resolvePlaceholders) {
            if (super.containsKey(key))
                return super.getValue(key, defaultValue, converter, resolvePlaceholders);

            return parent.getValue(key, defaultValue, converter, resolvePlaceholders);
        }
    }


    class Builder {

        private ConfigView parent;
        private List<ConfigSource> configSources = new ArrayList<>();

        public Builder parent(ConfigView parent) {
            this.parent = parent;
            return this;
        }

        public <T> Builder configSource(String sourceName, Map<String, T> configSettings) {
            Assert.notNull(configSettings, "'configSettings' must not be null.");
            this.configSources.add(
                    new MapConfigSource<T>(sourceName, configSettings) );

            return this;
        }

        public Builder configSource(String sourceName, OrderedProperties configSettings) {
            Assert.notNull(configSettings, "'configSettings' must not be null.");
            this.configSources.add(
                    new MapConfigSource<Object>(sourceName, Converters.to(configSettings) ) );

            return this;
        }

        public Builder configSource(ConfigSource configSource) {
            Assert.notNull(configSource, "'configSource' must not be null");
            if (configSource instanceof Compound) {
                this.configSources.addAll( ((Compound)configSource).getConfigSources() );
            } else {
                this.configSources.add(configSource);
            }

            return this;
        }

        public ConfigView build() {
            return parent == null 
                    ? new Default(configSources)
                    : new Hirarchical(parent, configSources);
        }
    }
}