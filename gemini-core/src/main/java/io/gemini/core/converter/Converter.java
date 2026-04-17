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
package io.gemini.core.converter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import io.gemini.api.BaseException;
import io.gemini.api.annotation.NoScanning;
import io.gemini.core.util.StringUtils;


/**
 * Defines the contract for type converters used by {@link ConversionService}.
 * <p>
 * Built-in implementations cover common {@code String} to primitive/wrapper/collection conversions.
 * The {@link ToClass} implementation loads a class by name using a given {@link ClassLoader}.
 * </p>
 *
 * @param <S> the source type
 * @param <T> the target type
 *
 * @author   martin.liu
 */
public interface Converter<S, T> {

    String VALUE_DELIMITER = ",";


    /**
     * Converts the given source value to the target type.
     *
     * @param source the source value to convert
     * @return the converted value
     * @throws ConversionException if the conversion fails
     */
    T convert(S source) throws ConversionException;


    /**
     * Thrown when a type conversion fails (e.g., unparseable string value).
     */
    class ConversionException extends BaseException {

        private static final long serialVersionUID = 4609381072789618230L;

        /**
         * Constructs a {@code ConversionException} with the given message.
         *
         * @param message the detail message
         */
        public ConversionException(String message) {
            super(message);
        }

        /**
         * Constructs a {@code ConversionException} with the given message and cause.
         *
         * @param message the detail message
         * @param cause   the cause
         */
        public ConversionException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a {@code ConversionException} wrapping the given cause.
         *
         * @param cause the cause
         */
        public ConversionException(Throwable cause) {
            super(cause);
        }
    }


    /** 
     * Converts a string to a {@link Boolean} value. 
     */
    enum StringToBoolean implements Converter<String, Boolean> {

        INSTANCE;

        @Override
        public Boolean convert(String source) throws ConversionException {
            return Boolean.valueOf( source.trim() );
        }
    }


    /** 
     * Converts a comma-delimited string to a {@link java.util.List} of trimmed strings. 
     */
    enum StringToStringList implements Converter<String, List<String>> {

        INSTANCE;

        @Override
        public List<String> convert(String source) throws ConversionException {
            StringTokenizer st = new StringTokenizer(source, VALUE_DELIMITER);
            List<String> list = new ArrayList<>(st.countTokens());

            while (st.hasMoreTokens()) {
                String element = st.nextToken().trim();

                if (StringUtils.hasText(element))
                    list.add(element);
            }

            return list;
        }
    }


    /** 
     * Converts a comma-delimited string to a {@link java.util.Set} of trimmed strings. 
     */
    enum StringToStringSet implements Converter<String, Set<String>> {

        INSTANCE;

        @Override
        public Set<String> convert(String source) throws ConversionException {
            StringTokenizer st = new StringTokenizer(source, VALUE_DELIMITER);
            Set<String> list = new LinkedHashSet<>(st.countTokens());

            while (st.hasMoreTokens()) {
                String element = st.nextToken().trim();

                if (StringUtils.hasText(element))
                    list.add(element);
            }

            return list;
        }
    }


    /** 
     * Converts a comma-delimited string to a {@code String[]} array of trimmed strings.
     */
    enum StringToStringArray implements Converter<String, String[]> {

        INSTANCE;

        @Override
        public String[] convert(String source) throws ConversionException {
            StringTokenizer st = new StringTokenizer(source, VALUE_DELIMITER);
            List<String> value = new ArrayList<>(st.countTokens());

            while (st.hasMoreTokens()) {
                String element = st.nextToken().trim();

                if (StringUtils.hasText(element))
                    value.add(element);
            }

            return value.toArray(new String[] {});
        }
    }


    /** 
     * Converts a string to a {@link Byte} value. 
     */
    enum StringToByte implements Converter<String, Byte> {

        INSTANCE;

        @Override
        public Byte convert(String source) throws ConversionException {
            return Byte.valueOf( source.trim() );
        }
    }


    /** 
     * Converts a string to an {@link Integer} value. 
     */
    enum StringToInteger implements Converter<String, Integer> {

        INSTANCE;

        @Override
        public Integer convert(String source) throws ConversionException {
            return Integer.valueOf( source.trim() );
        }
    }


    /** 
     * Converts a string to a {@link Long} value. 
     */
    enum StringToLong implements Converter<String, Long> {

        INSTANCE;

        @Override
        public Long convert(String source) throws ConversionException {
            return Long.valueOf( source.trim() );
        }
    }


    /** 
     * Converts a string to a {@link Float} value. 
     */
    enum StringToFloat implements Converter<String, Float> {

        INSTANCE;

        @Override
        public Float convert(String source) throws ConversionException {
            return Float.valueOf( source.trim() );
        }
    }


    /** 
     * Converts a string to a {@link Double} value. 
     */
    enum StringToDouble implements Converter<String, Double> {

        INSTANCE;

        @Override
        public Double convert(String source) throws ConversionException {
            return Double.valueOf( source.trim() );
        }
    }


    /**
     * Loads a class by name using the given {@link ClassLoader}.
     * Used to convert string class names from configuration properties to {@link Class} objects.
     */
    @NoScanning
    class ToClass implements Converter<String, Class<?>> {

        private final ClassLoader classLoader;


        /**
         * Constructs a {@code ToClass} converter using the given class loader.
         *
         * @param classLoader the class loader used to load classes by name
         */
        public ToClass(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<?> convert(String source) throws ConversionException {
            String className = source;
            try {
                return Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException e) {
                throw new ConversionException("Cannot load Class [" + className + "] with ClassLoader [" + classLoader + "]", e);
            }
        }
    }
}
