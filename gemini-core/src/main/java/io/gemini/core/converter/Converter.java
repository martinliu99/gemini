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
package io.gemini.core.converter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import io.gemini.api.BaseException;
import io.gemini.api.annotation.NoScanning;
import io.gemini.core.util.StringUtils;


public interface Converter<S, T> {

    String VALUE_DELIMITER = ",";

    T convert(S source) throws ConversionException;


    class ConversionException extends BaseException {

        private static final long serialVersionUID = 4609381072789618230L;


        public ConversionException(String message) {
            super(message);
        }

        public ConversionException(String message, Throwable cause) {
            super(message, cause);
        }

        public ConversionException(Throwable cause) {
            super(cause);
        }
    }


    enum StringToBoolean implements Converter<String, Boolean> {

        INSTANCE;

        @Override
        public Boolean convert(String source) throws ConversionException {
            return Boolean.valueOf( source.trim() );
        }
    }


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


    enum StringToByte implements Converter<String, Byte> {

        INSTANCE;

        @Override
        public Byte convert(String source) throws ConversionException {
            return Byte.valueOf( source.trim() );
        }
    }


    enum StringToInteger implements Converter<String, Integer> {

        INSTANCE;

        @Override
        public Integer convert(String source) throws ConversionException {
            return Integer.valueOf( source.trim() );
        }
    }


    enum StringToLong implements Converter<String, Long> {

        INSTANCE;

        @Override
        public Long convert(String source) throws ConversionException {
            return Long.valueOf( source.trim() );
        }
    }


    enum StringToFloat implements Converter<String, Float> {

        INSTANCE;

        @Override
        public Float convert(String source) throws ConversionException {
            return Float.valueOf( source.trim() );
        }
    }


    enum StringToDouble implements Converter<String, Double> {

        INSTANCE;

        @Override
        public Double convert(String source) throws ConversionException {
            return Double.valueOf( source.trim() );
        }
    }


    @NoScanning
    class ToClass implements Converter<String, Class<?>> {

        private final ClassLoader classLoader;


        public ToClass(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

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
