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

/**
 * Provides a unified, type-safe view over one or more {@link ConfigSource} instances.
 * <p>
 * Supports placeholder resolution (e.g., {@code ${key}}), type conversion via
 * {@link io.gemini.core.converter.ConversionService}, and hierarchical lookup
 * (child settings override parent settings in {@link Hirarchical}).
 * </p>
 * <p>
 * Use {@link Builder} to construct instances programmatically.
 * </p>
 *
 * @author   martin.liu
 */
public interface ConfigView {


    /**
     * Returns all configuration keys in this view.
     *
     * @return collection of all keys
     */
    Collection<String> keys();

    /**
     * Returns all keys that start with the given prefix.
     *
     * @param keyPrefix the prefix to filter by
     * @return collection of matching keys
     */
    default Collection<String> keys(String keyPrefix) {
        if (keyPrefix == null)
            return new ArrayList<>();

        Set<String> keys = new LinkedHashSet<>();
        for (String key : this.keys()) {
            if (key != null && key.startsWith(keyPrefix)) {
                keys.add(key);
            }
        }
        return keys;
    }


    /**
     * Returns {@code true} if this view contains the given key.
     *
     * @param key the configuration key to check
     * @return {@code true} if the key exists
     */
    boolean containsKey(String key);

    /**
     * Returns the value for the given key, converted to the specified type.
     *
     * @param key        the configuration key
     * @param targetType the target Java type
     * @param <T>        the target type
     * @return the converted value
     */
    default <T> T getValue(String key, Class<T> targetType) {
        return getValue(key, true, TypeDefinition.Sort.describe(targetType));
    }

    /**
     * Returns the value for the given key, converted to the specified type.
     *
     * @param key        the configuration key
     * @param targetType the target Java type
     * @param <T>        the target type
     * @return the converted value, or {@code defaultValue} if absent
     */
    default <T> T getValue(String key, T defaultValue, Class<T> targetType) {
        return getValue(key, defaultValue, true, TypeDefinition.Sort.describe(targetType));
    }

    /**
     * Returns the value for the given key, converted to the specified type, with optional
     * placeholder resolution.
     *
     * @param key                 the configuration key
     * @param defaultValue        the value to return if the key is absent
     * @param resolvePlaceholders whether to resolve {@code ${key}} placeholders in the value
     * @param targetType          the target Java type
     * @param <T>                 the target type
     * @return the converted value, or {@code defaultValue} if absent
     */
    default <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Class<T> targetType) {
        return this.getValue(key, defaultValue, resolvePlaceholders, TypeDefinition.Sort.describe(targetType));
    }


    /**
     * Returns the value for the given key, converted to the specified type.
     *
     * @param key        the configuration key
     * @param targetType the target Java type
     * @param <T>        the target type
     * @return the converted value, or {@code defaultValue} if absent
     */
    @SuppressWarnings("TypeParameterUnusedInFormals")
    <T> T getValue(String key, boolean resolvePlaceholders, Generic targetType);

    /**
     * Returns the value for the given key, converted to the specified type, with optional
     * placeholder resolution.
     *
     * @param key                 the configuration key
     * @param defaultValue        the value to return if the key is absent
     * @param resolvePlaceholders whether to resolve {@code ${key}} placeholders in the value
     * @param targetType          the target Java type
     * @param <T>                 the target type
     * @return the converted value, or {@code defaultValue} if absent
     */
    <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Generic targetType);


    /**
     * Returns the value for the given key, converted using the given {@link Converter},
     * with optional placeholder resolution.
     *
     * @param key                 the configuration key
     * @param resolvePlaceholders whether to resolve {@code ${key}} placeholders in the value
     * @param converter           the converter to apply to the raw value
     * @param <T>                 the target type
     * @return the converted value
     */
    <T> T getValue(String key, boolean resolvePlaceholders, Converter<?, T> converter);

    /**
     * Returns the value for the given key, converted using the given {@link Converter},
     * with optional placeholder resolution, returning a default if absent.
     *
     * @param key                 the configuration key
     * @param defaultValue        the value to return if the key is absent
     * @param resolvePlaceholders whether to resolve {@code ${key}} placeholders in the value
     * @param converter           the converter to apply to the raw value
     * @param <T>                 the target type
     * @return the converted value, or {@code defaultValue} if absent
     */
    <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Converter<?, T> converter);


    /**
     * Returns the value for the given key as a {@link Boolean}, or {@code null} if absent.
     *
     * @param key the configuration key
     * @return the boolean value, or {@code null}
     */
    default Boolean getAsBoolean(String key) {
        return this.getValue(key, true, StringToBoolean.INSTANCE);
    }

    /**
     * Returns the value for the given key as a {@link Boolean}, or {@code defaultValue} if absent.
     *
     * @param key the configuration key
     * @return the boolean value, or {@code null}
     */
    default Boolean getAsBoolean(String key, Boolean defaultValue) {
        return this.getValue(key, defaultValue, true, StringToBoolean.INSTANCE);
    }


    /**
     * Returns the value for the given key as a trimmed {@link String}, or {@code null} if absent.
     *
     * @param key the configuration key
     * @return the string value, or {@code null}
     */
    default String getAsString(String key) {
        String value = this.getValue(key, true, TypeDefinition.Sort.describe(String.class));
        return value == null ? null : value.trim();

    }

    /**
     * Returns the value for the given key as a trimmed {@link String}, or {@code defaultValue} if absent.
     *
     * @param key the configuration key
     * @return the string value, or {@code null}
     */
    default String getAsString(String key, String defaultValue) {
        String value =this.getValue(key, defaultValue, true, TypeDefinition.Sort.describe(String.class));
        return value == null ? null : value.trim();
    }


    /**
     * Returns the value for the given key as a {@link java.util.List} of strings, or {@code null} if absent.
     *
     * @param key the configuration key (comma-separated values)
     * @return the list of string values, or {@code null}
     */
    default List<String> getAsStringList(String key) {
        return this.getValue(key, true, StringToStringList.INSTANCE);
    }

    /**
     * Returns the value for the given key as a {@link java.util.List} of strings, or {@code defaultValue} if absent.
     *
     * @param key the configuration key (comma-separated values)
     * @return the list of string values, or {@code null}
     */
    default List<String> getAsStringList(String key, List<String> defaultValue) {
        return this.getValue(key, defaultValue, true, StringToStringList.INSTANCE);
    }


    /**
     * Returns the value for the given key as a {@link java.util.Set} of strings, or {@code null} if absent.
     *
     * @param key the configuration key (comma-separated values)
     * @return the set of string values, or {@code null}
     */
    default Set<String> getAsStringSet(String key) {
        return this.getValue(key, true, StringToStringSet.INSTANCE);
    }

    /**
     * Returns the value for the given key as a {@link java.util.Set} of strings, or {@code defaultValue} if absent.
     *
     * @param key the configuration key (comma-separated values)
     * @return the set of string values, or {@code null}
     */
    default Set<String> getAsStringSet(String key, Set<String> defaultValue) {
        return this.getValue(key, defaultValue, true, StringToStringSet.INSTANCE);
    }


    /**
     * Returns the value for the given key as a {@code String[]} array, or {@code null} if absent.
     *
     * @param key the configuration key (comma-separated values)
     * @return the string array, or {@code null}
     */
    default String[] getAsStrings(String key) {
        return this.getValue(key, true, StringToStringArray.INSTANCE);
    }

    /**
     * Returns the value for the given key as a {@code String[]} array, or {@code defaultValue} if absent.
     *
     * @param key the configuration key (comma-separated values)
     * @return the string array, or {@code null}
     */
    default String[] getAsStrings(String key, String[] defaultValue) {
        return this.getValue(key, defaultValue, true, StringToStringArray.INSTANCE);
    }


    /**
     * Returns the value for the given key as an {@link Integer}, or {@code null} if absent.
     *
     * @param key the configuration key
     * @return the integer value, or {@code null}
     */
    default Integer getAsInteger(String key) {
        return this.getValue(key, true, StringToInteger.INSTANCE);
    }

    /**
     * Returns the value for the given key as an {@link Integer}, or {@code defaultValue} if absent.
     *
     * @param key the configuration key
     * @return the integer value, or {@code null}
     */
    default Integer getAsInteger(String key, Integer defaultValue) {
        return this.getValue(key, defaultValue, true, StringToInteger.INSTANCE);
    }


    /**
     * Returns the value for the given key as a {@link Class}, or {@code null} if absent.
     *
     * @param key the configuration key (fully-qualified class name)
     * @param <T> the expected class type
     * @return the loaded class, or {@code null}
     */
    default <T> Class<T> getAsClass(String key) {
        return this.getValue(key, true, TypeDefinition.Sort.describe(Class.class));
    }

    /**
     * Returns the value for the given key as a {@link Class}, or {@code defaultValue} if absent.
     *
     * @param key the configuration key (fully-qualified class name)
     * @param <T> the expected class type
     * @return the loaded class, or {@code null}
     */
    default <T> Class<T> getAsClass(String key, Class<T> defaultValue) {
        return this.getValue(key, defaultValue, true, TypeDefinition.Sort.describe(Class.class));
    }


    /**
     * Thrown when a required configuration key is not found in any source.
     */
    public static class ConfigException extends BaseException {

        private static final long serialVersionUID = -4610045854862146968L;

        /**
         * Constructs a {@code ConfigException} with the given message.
         *
         * @param message the detail message
         */
        public ConfigException(String message) {
            super(message);
        }

        /**
         * Constructs a {@code ConfigException} wrapping the given cause.
         *
         * @param t the cause
         */
        public ConfigException(Throwable t) {
            super(t);
        }

        /**
         * Constructs a {@code ConfigException} with the given message and cause.
         *
         * @param message the detail message
         * @param t       the cause
         */
        public ConfigException(String message, Throwable t) {
            super(message, t);
        }


        /**
         * Thrown when a required configuration key is not found in any source.
         */
        public static class ConfigNotFoundException extends ConfigException {

            private static final long serialVersionUID = 5539369009779917061L;

            /**
             * Constructs a {@code ConfigNotFoundException} for the given key.
             *
             * @param key the missing configuration key
             */
            public ConfigNotFoundException(String key) {
                super("Config '" + key + "' does not find");
            }

        }
    }


    /**
     * Abstract base implementation of {@link ConfigView} providing type conversion and
     * placeholder resolution. Subclasses implement {@link #doGetValue(String)} to supply
     * the raw value for a given key.
     */
    abstract class AbstractBase implements ConfigView {

        private final ConversionService conversionService;
        private final PlaceholderHelper placeholderHelper;


        /**
         * Constructs an {@code AbstractBase} with the given conversion service.
         * If {@code null}, a default conversion service is created.
         *
         * @param conversionService the conversion service to use, or {@code null} for the default
         */
        protected AbstractBase(ConversionService conversionService) {
            this.conversionService = conversionService != null 
                    ? conversionService : ConversionService.createConversionService();

            this.placeholderHelper = PlaceholderHelper.create(this);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("TypeParameterUnusedInFormals")
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
        public <T> T getValue(String key, boolean resolvePlaceholders, Converter<?, T> converter) {
            Object value = getValue(key, resolvePlaceholders);

            return conversionService.convert(value, converter);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Converter<?, T> converter) {
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


    /**
     * Default {@link ConfigView} implementation backed by a {@link ConfigSource.Compound}.
     */
    class Default extends AbstractBase {

        private final ConfigSource configSource;


        /**
         * Constructs a {@code Default} view from a varargs array of config sources.
         *
         * @param conversionService the conversion service to use
         * @param configSources     the config sources to aggregate
         */
        protected Default(ConversionService conversionService, ConfigSource... configSources) {
            super(conversionService);

            this.configSource = new ConfigSource.Compound(configSources);
        }

        /**
         * Constructs a {@code Default} view from a list of config sources.
         *
         * @param conversionService the conversion service to use
         * @param configSources     the config sources to aggregate
         */
        protected Default(ConversionService conversionService, List<ConfigSource> configSources) {
            super(conversionService);

            this.configSource = new ConfigSource.Compound(configSources);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<String> keys() {
            return this.configSource.keys();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean containsKey(String key) {
            Assert.hasText(key, "'key' must not be empty");

            return this.configSource.containsKey(key);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        protected Object doGetValue(String key) {
            return this.configSource.getValue(key);
        }
    }


    /**
     * A hierarchical {@link ConfigView} that checks child sources first, then falls back
     * to a parent {@link ConfigView}. Child settings override parent settings.
     */
    class Hirarchical extends Default {

        private final ConfigView parent;
        private Set<String> keys;


        /**
         * Constructs a {@code Hirarchical} view with a parent and varargs config sources.
         *
         * @param parent            the parent config view to fall back to
         * @param conversionService the conversion service to use
         * @param configSources     the child config sources
         */
        protected Hirarchical(ConfigView parent, ConversionService conversionService, ConfigSource... configSources) {
            super(conversionService, configSources);

            Assert.notNull(parent, "'parent' must not be null.");
            this.parent = parent;
        }

        /**
         * Constructs a {@code Hirarchical} view with a parent and a list of config sources.
         *
         * @param parent            the parent config view to fall back to
         * @param conversionService the conversion service to use
         * @param configSources     the child config sources
         */
        protected Hirarchical(ConfigView parent, ConversionService conversionService, List<ConfigSource> configSources) {
            super(conversionService, configSources);

            Assert.notNull(parent, "'parent' must not be null.");
            this.parent = parent;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<String> keys() {
            if (keys != null)
                return keys;

            Set<String> keys = new LinkedHashSet<>();
            keys.addAll(super.keys());
            keys.addAll(parent.keys());

            return (this.keys = keys);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean containsKey(String key) {
            if (super.containsKey(key) == true)
                return true;

            return parent.containsKey(key);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("TypeParameterUnusedInFormals")
        public <T> T getValue(String key, boolean resolvePlaceholders, Generic targetType) {
            if (super.containsKey(key))
                return super.getValue(key, resolvePlaceholders, targetType);

            return parent.getValue(key, resolvePlaceholders, targetType);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Generic targetType) {
            if (super.containsKey(key))
                return super.getValue(key, defaultValue, resolvePlaceholders, targetType);

            return parent.getValue(key, defaultValue, resolvePlaceholders, targetType);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T getValue(String key, boolean resolvePlaceholders, Converter<?, T> converter) {
            if (super.containsKey(key))
                return super.getValue(key, resolvePlaceholders, converter);

            return parent.getValue(key, resolvePlaceholders, converter);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T getValue(String key, T defaultValue, boolean resolvePlaceholders, Converter<?, T> converter) {
            if (super.containsKey(key))
                return super.getValue(key, defaultValue, resolvePlaceholders, converter);

            return parent.getValue(key, defaultValue, resolvePlaceholders, converter);
        }
    }


    /**
     * Fluent builder for constructing {@link ConfigView} instances.
     * Produces a {@link Default} or {@link Hirarchical} view depending on whether a parent is set.
     */
    class Builder {

        private ConfigView parent;
        private ConversionService conversionService;
        private List<ConfigSource> configSources = new ArrayList<>();

        /**
         * Sets the parent {@link ConfigView} for hierarchical lookup.
         *
         * @param parent the parent view
         * @return this builder
         */
        public Builder parent(ConfigView parent) {
            this.parent = parent;
            return this;
        }

        /**
         * Sets the {@link ConversionService} to use for type conversion.
         *
         * @param conversionService the conversion service
         * @return this builder
         */
        public Builder conversionService(ConversionService conversionService) {
            this.conversionService = conversionService;
            return this;
        }

        /**
         * Adds a map-backed config source with the given name.
         *
         * @param sourceName     a human-readable name for the source
         * @param configSettings the map of configuration key-value pairs
         * @param <T>            the value type
         * @return this builder
         */
        public <T> Builder configSource(String sourceName, Map<String, T> configSettings) {
            Assert.notNull(configSettings, "'configSettings' must not be null.");
            this.configSources.add(
                    new MapConfigSource<T>(sourceName, configSettings) );

            return this;
        }

        /**
         * Adds an {@link OrderedProperties}-backed config source with the given name.
         *
         * @param sourceName     a human-readable name for the source
         * @param configSettings the properties to use as the source
         * @return this builder
         */
        public Builder configSource(String sourceName, OrderedProperties configSettings) {
            Assert.notNull(configSettings, "'configSettings' must not be null.");
            this.configSources.add(
                    new MapConfigSource<Object>(sourceName, Converters.to(configSettings) ) );

            return this;
        }

        /**
         * Adds an existing {@link ConfigSource} (unwrapping {@link Compound} sources).
         *
         * @param configSource the config source to add
         * @return this builder
         */
        public Builder configSource(ConfigSource configSource) {
            Assert.notNull(configSource, "'configSource' must not be null");
            if (configSource instanceof Compound) {
                this.configSources.addAll( ((Compound)configSource).getConfigSources() );
            } else {
                this.configSources.add(configSource);
            }

            return this;
        }

        /**
         * Builds and returns the configured {@link ConfigView}.
         * Returns a {@link Hirarchical} view if a parent was set, otherwise a {@link Default} view.
         *
         * @return the constructed config view
         */
        public ConfigView build() {
            return parent == null 
                    ? new Default(conversionService, configSources)
                    : new Hirarchical(parent, conversionService, configSources);
        }
    }
}