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

import io.gemini.api.BaseException;
import io.gemini.core.config.ConfigSource.Compound;
import io.gemini.core.config.ConfigSource.MapConfigSource;
import io.gemini.core.config.ConfigView.ConfigException.ConfigNotFoundException;
import io.gemini.core.converter.ConversionService;
import io.gemini.core.converter.Converter;
import io.gemini.core.converter.Converter.StringToBoolean;
import io.gemini.core.converter.Converter.StringToInteger;
import io.gemini.core.converter.Converter.StringToStringArray;
import io.gemini.core.converter.Converter.StringToStringList;
import io.gemini.core.converter.Converter.StringToStringSet;
import io.gemini.core.converter.Converters;
import io.gemini.core.util.Assert;
import io.gemini.core.util.OrderedProperties;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription.Generic;

public interface ConfigView {


    Collection<String> keys();

    default Collection<String> keys(String keyPrefix) {
        if (keyPrefix == null)
            return Collections.emptyList();

        Set<String> keys = new LinkedHashSet<>();
        for (String key : this.keys()) {
            if (key != null && key.startsWith(keyPrefix)) {
                keys.add(key);
            }
        }
        return keys;
    }


    boolean containsKey(String key);


    default <T> T getValue(String key, Class<T> targetType) {
        return getValue(key, true, TypeDefinition.Sort.describe(targetType));
    }

    default <T> T getValue(String key, T defaultValue, Class<T> targetType) {
        return getValue(key, defaultValue, true, TypeDefinition.Sort.describe(targetType));
    }

    default <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Class<T> targetType) {
        return this.getValue(key, defaultValue, resolvePlaceholders, TypeDefinition.Sort.describe(targetType));
    }


    <T> T getValue(String key, boolean resolvePlaceholders, Generic targetType);

    <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Generic targetType);


    <T> T getValue(String key, boolean resolvePlaceholders, Converter<?, ?> converter);

    <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Converter<?, ?> converter);


    default Boolean getAsBoolean(String key) {
        return this.getValue(key, true, StringToBoolean.INSTANCE);
    }

    default Boolean getAsBoolean(String key, Boolean defaultValue) {
        return this.getValue(key, defaultValue, true, StringToBoolean.INSTANCE);
    }


    default String getAsString(String key) {
        String value = this.getValue(key, true, TypeDefinition.Sort.describe(String.class));
        return value == null ? null : value.trim();

    }

    default String getAsString(String key, String defaultValue) {
        String value =this.getValue(key, defaultValue, true, TypeDefinition.Sort.describe(String.class));
        return value == null ? null : value.trim();
    }


    default List<String> getAsStringList(String key) {
        return this.getValue(key, true, StringToStringList.INSTANCE);
    }

    default List<String> getAsStringList(String key, List<String> defaultValue) {
        return this.getValue(key, defaultValue, true, StringToStringList.INSTANCE);
    }


    default Set<String> getAsStringSet(String key) {
        return this.getValue(key, true, StringToStringSet.INSTANCE);
    }

    default Set<String> getAsStringSet(String key, Set<String> defaultValue) {
        return this.getValue(key, defaultValue, true, StringToStringSet.INSTANCE);
    }



    default String[] getAsStrings(String key) {
        return this.getValue(key, true, StringToStringArray.INSTANCE);
    }

    default String[] getAsStrings(String key, String[] defaultValue) {
        return this.getValue(key, defaultValue, true, StringToStringArray.INSTANCE);
    }


    default Integer getAsInteger(String key) {
        return this.getValue(key, true, StringToInteger.INSTANCE);
    }

    default Integer getAsInteger(String key, Integer defaultValue) {
        return this.getValue(key, defaultValue, true, StringToInteger.INSTANCE);
    }


    default <T> Class<T> getAsClass(String key) {
        return this.getValue(key, true, TypeDefinition.Sort.describe(Class.class));
    }

    default <T> Class<T> getAsClass(String key, Class<T> defaultValue) {
        return this.getValue(key, defaultValue, true, TypeDefinition.Sort.describe(Class.class));
    }


    public class ConfigException extends BaseException {

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

            private static final long serialVersionUID = 5539369009779917061L;

            public ConfigNotFoundException(String key) {
                super("Config '" + key + "' does not find");
            }

        }
    }


    abstract class AbstractBase implements ConfigView {

        private final ConversionService conversionService;
        private final PlaceholderHelper placeholderHelper;


        protected AbstractBase(ConversionService conversionService) {
            this.conversionService = conversionService != null 
                    ? conversionService : ConversionService.createConversionService();

            this.placeholderHelper = PlaceholderHelper.create(this);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T getValue(String key, boolean resolvePlaceholders, Generic targetType) {
            Assert.notNull(targetType, "'targetType' must not be null.");

            Object value = getValue(key, resolvePlaceholders);

            return conversionService.convert(value, targetType);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Generic targetType) {
            Assert.notNull(targetType, "'targetType' must not be null.");

            Object value = null;
            try {
                value = getValue(key, resolvePlaceholders);
            } catch (ConfigNotFoundException e) {
                return (T) defaultValue;
            }

            return conversionService.convert(value, targetType);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T getValue(String key, boolean resolvePlaceholders, Converter<?, ?> converter) {
            Object value = getValue(key, resolvePlaceholders);

            return conversionService.convert(value, converter);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Converter<?, ?> converter) {
            Object value = null;
            try {
                value = getValue(key, resolvePlaceholders);
            } catch (ConfigNotFoundException e) {
                return (T) defaultValue;
            }

            return conversionService.convert(value, converter);
        }


        private Object getValue(String key, boolean resolvePlaceholders) throws ConfigNotFoundException {
            if (this.containsKey(key) == false)
                throw new ConfigNotFoundException(key);

            Object value = this.doGetValue(key);
            if (null == value || "".equals(value))
                throw new ConfigNotFoundException(key);

            // try to replace placeholders
            if (resolvePlaceholders == true && value instanceof String) {
                value = this.placeholderHelper.replace( (String)value );
            }

            return value;
        }

        protected abstract Object doGetValue(String key);
    }


    class Default extends AbstractBase {

        private final ConfigSource configSource;


        protected Default(ConversionService conversionService, ConfigSource... configSources) {
            super(conversionService);

            this.configSource = new ConfigSource.Compound(configSources);
        }

        protected Default(ConversionService conversionService, List<ConfigSource> configSources) {
            super(conversionService);

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
        private Set<String> keys;


        protected Hirarchical(ConfigView parent, ConversionService conversionService, ConfigSource... configSources) {
            super(conversionService, configSources);

            Assert.notNull(parent, "'parent' must not be null.");
            this.parent = parent;
        }

        protected Hirarchical(ConfigView parent, ConversionService conversionService, List<ConfigSource> configSources) {
            super(conversionService, configSources);

            Assert.notNull(parent, "'parent' must not be null.");
            this.parent = parent;
        }

        @Override
        public Set<String> keys() {
            if (keys != null)
                return keys;

            Set<String> keys = new LinkedHashSet<>();
            keys.addAll(super.keys());
            keys.addAll(parent.keys());

            return (this.keys = keys);
        }

        @Override
        public boolean containsKey(String key) {
            if (super.containsKey(key) == true)
                return true;

            return parent.containsKey(key);
        }


        @Override
        public <T> T getValue(String key, boolean resolvePlaceholders, Generic targetType) {
            if (super.containsKey(key))
                return super.getValue(key, resolvePlaceholders, targetType);

            return parent.getValue(key, resolvePlaceholders, targetType);
        }

        @Override
        public <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Generic targetType) {
            if (super.containsKey(key))
                return super.getValue(key, defaultValue, resolvePlaceholders, targetType);

            return parent.getValue(key, defaultValue, resolvePlaceholders, targetType);
        }


        @Override
        public <T> T getValue(String key, boolean resolvePlaceholders, Converter<?, ?> converter) {
            if (super.containsKey(key))
                return super.getValue(key, resolvePlaceholders, converter);

            return parent.getValue(key, resolvePlaceholders, converter);
        }

        @Override
        public <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Converter<?, ?> converter) {
            if (super.containsKey(key))
                return super.getValue(key, defaultValue, resolvePlaceholders, converter);

            return parent.getValue(key, defaultValue, resolvePlaceholders, converter);
        }
    }


    class Builder {

        private ConfigView parent;
        private ConversionService conversionService;
        private List<ConfigSource> configSources = new ArrayList<>();

        public Builder parent(ConfigView parent) {
            this.parent = parent;
            return this;
        }

        public Builder conversionService(ConversionService conversionService) {
            this.conversionService = conversionService;
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
                    ? new Default(conversionService, configSources)
                    : new Hirarchical(parent, conversionService, configSources);
        }
    }
}