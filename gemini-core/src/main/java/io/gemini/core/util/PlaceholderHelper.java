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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.lookup.LookupResult;
import org.apache.logging.log4j.core.lookup.StrLookup;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

import ch.qos.logback.core.spi.PropertyContainer;
import ch.qos.logback.core.spi.ScanException;
import ch.qos.logback.core.subst.NodeToStringTransformer;
import io.gemini.core.config.ConfigView;
import io.gemini.core.config.ConfigView.Converter;


public interface PlaceholderHelper {

    String replace(String placeholder);


    interface PlaceholderResolver {

        String getValue(String key);

    }


    static <T> PlaceholderHelper create(Map<String, T> valueMap) {
        return new Default( new Default.WithMap(valueMap) );
    }

    static PlaceholderHelper create(ConfigView configView) {
        return new Default( new Default.WithConfigView(configView) );
    }


    class Default implements PlaceholderHelper {

        private final PropertyContainer propertyContainer;


        Default(PropertyContainer propertyContainer) {
            this.propertyContainer = propertyContainer;
        }

        @Override
        public String replace(String placeholder) {
            try {
                return NodeToStringTransformer.substituteVariable(placeholder, propertyContainer, null);
            } catch (ScanException e) {
                e.printStackTrace();
                return placeholder;
            }
        }


        private static abstract class AbstractBase implements PropertyContainer {

            /**
             * {@inheritDoc}
             */
            @Override
            public abstract String getProperty(String key);

            /**
             * {@inheritDoc}
             */
            @Override
            public Map<String, String> getCopyOfPropertyMap() {
                return null;
            }
        }


        private static class WithMap extends AbstractBase {

            private final Map<String, String> stringValueMap;

            <T> WithMap(Map<String, T> valueMap) {
                Map<String, String> stringValueMap = new HashMap<>();
                for(Entry<String, T> entry : valueMap.entrySet()) {
                    String value = entry.getValue() == null ? null : entry.getValue().toString();
                    stringValueMap.put(entry.getKey(), value);
                }

                this.stringValueMap = stringValueMap;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getProperty(String key) {
                return stringValueMap.get(key);
            }
        }


        private static class WithConfigView extends AbstractBase {

            private ConfigView configView;

            WithConfigView(ConfigView configView) {
                this.configView = configView;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getProperty(String key) {
                return configView.getValue(key, null, Converter.ToString.INSTANCE, false);
            }
        }
    }


    class Builder {

        private static final char ESCAPE = '$';
        private static final String PLACEHOLDER_PREFIX = ESCAPE + "{";
        private static final String PLACEHOLDER_SUFFIX = "}";
        private static final String DEFAULT_VALUE_DELIMITER = ":";


        private String placeholderPrefix = PLACEHOLDER_PREFIX;
        private String placeholderSuffix = PLACEHOLDER_SUFFIX;

        private char escape = ESCAPE;
        private String defaultValueDelimiter = DEFAULT_VALUE_DELIMITER;

        private Builder() {}

        public Builder withEscape(char escape) {
            this.escape = escape;

            return this;
        }

        public Builder withPrefix(String placeholderPrefix) {
            Assert.hasText(placeholderPrefix, "'placeholderPrefix' must not be null.");
            this.placeholderPrefix = placeholderPrefix;

            return null;
        }

        public Builder withSuffix(String placeholderSuffix) {
            Assert.hasText(placeholderSuffix, "'placeholderSuffix' must not be null.");
            this.placeholderSuffix = placeholderSuffix;

            return null;
        }

        public Builder withDelimiter(String defaultValueDelimiter) {
            Assert.hasText(defaultValueDelimiter, "'defaultValueDelimiter' must not be null.");
            this.defaultValueDelimiter = defaultValueDelimiter;

            return null;
        }


        public <T> PlaceholderHelper build(final Map<String, T> valueMap) {
            Assert.notNull(valueMap, "'valueMap' must not be null.");

            Map<String, String> _valueMap = new HashMap<>();
            for(Entry<String, T> entry : valueMap.entrySet()) {
                String value = entry.getValue() == null ? null : entry.getValue().toString();
                _valueMap.put(entry.getKey(), value);
            }

            return createPlaceholderHelper(
                    new StrSubstitutor(_valueMap) );
        }

        public PlaceholderHelper build(final OrderedProperties properties) {
            Assert.notNull(properties, "'properties' must not be null.");

            return createPlaceholderHelper(
                    new StrSubstitutor(properties) );
        }

        public PlaceholderHelper build(final PlaceholderResolver placeholderResolver) {
            Assert.notNull(placeholderResolver, "'placeholderResolver' must not be null.");

            StrSubstitutor strSubstitutor = new StrSubstitutor(new StrLookup() {
                @Override
                public String lookup(String key) {
                    return this.lookup(null, key);
                }

                @Override
                public String lookup(LogEvent event, String key) {
                    return placeholderResolver.getValue(key);
                }

                public LookupResult evaluate(LogEvent event, String key) {
                    final String value = lookup(event, key);

                    return value == null ? null : new PlaceholderLookupResult(value);
                }
            });

            return createPlaceholderHelper(strSubstitutor);
        }

        public PlaceholderHelper build(final ConfigView configView) {
            Assert.notNull(configView, "'configView' must not be null.");

            StrSubstitutor strSubstitutor = new StrSubstitutor(new StrLookup() {
                @Override
                public String lookup(String key) {
                    return this.lookup(null, key);
                }

                @Override
                public String lookup(LogEvent event, String key) {
                    return configView.getAsString(key, null);
                }

                public LookupResult evaluate(LogEvent event, String key) {
                    final String value = lookup(event, key);

                    return value == null ? null : new PlaceholderLookupResult(value);
                }
            });

            return createPlaceholderHelper(strSubstitutor);
        }

        private PlaceholderHelper createPlaceholderHelper(StrSubstitutor strSubstitutor) {
            strSubstitutor.setEnableSubstitutionInVariables(true);

            strSubstitutor.setVariablePrefix(placeholderPrefix);
            strSubstitutor.setVariableSuffix(placeholderSuffix);

            strSubstitutor.setEscapeChar(escape);
            strSubstitutor.setValueDelimiter(defaultValueDelimiter);

            return new PlaceholderHelper() {

                @Override
                public String replace(String placeholder) {
                    return strSubstitutor.replace(placeholder);
                }
            };
        }

        private static class PlaceholderLookupResult implements LookupResult {

            private final String value;

            public PlaceholderLookupResult(String value) {
                this.value = value;
            }

            @Override
            public String value() {
                return value;
            }

            public boolean isLookupEvaluationAllowedInValue() {
                // evaluate variables in return value
                return true;
            }
        }
    }
}
