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
/**
 * 
 */
package io.gemini.core.converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.gemini.core.converter.Converter.ConversionException;
import io.gemini.core.converter.Converter.StringToBoolean;
import io.gemini.core.converter.Converter.StringToByte;
import io.gemini.core.converter.Converter.StringToDouble;
import io.gemini.core.converter.Converter.StringToFloat;
import io.gemini.core.converter.Converter.StringToInteger;
import io.gemini.core.converter.Converter.StringToLong;
import io.gemini.core.converter.Converter.StringToStringArray;
import io.gemini.core.converter.Converter.StringToStringList;
import io.gemini.core.converter.Converter.StringToStringSet;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * 
 */
public interface ConversionService {

    void addConverter(Converter<?, ?> converter);

    void addConverter(Class<?> sourceType, Class<?> targetType, Converter<?, ?> converter);

    <T> T convert(Object source, Class<T> targetType);

    <T> T convert(Object source, Generic targetType);


    <T> T convert(Object source, Converter<?, ?> converter);


    static ConversionService createConversionService() {
        return createConversionService(null);
    }

    static ConversionService createConversionService(List<Converter<?, ?>> converters) {
        return new Default(converters);
    }


    class Default implements ConversionService {

        private final Map<ConverterCacheKey, Converter<?, ?>> converterMap;


        Default(List<Converter<?, ?>> converters) {
            this.converterMap = new LinkedHashMap<>();
            if (converters != null) {
                for (Converter<?, ?> converter : converters)
                    this.addConverter(converter);
            }

            registerBuiltinConverter();
        }

        private void registerBuiltinConverter() {
            this.addConverter(String.class, boolean.class, StringToBoolean.INSTANCE);
            this.addConverter(String.class, Boolean.class, StringToBoolean.INSTANCE);

            this.addConverter(StringToStringList.INSTANCE);
            this.addConverter(StringToStringSet.INSTANCE);
            this.addConverter(StringToStringArray.INSTANCE);

            this.addConverter(String.class, byte.class, StringToByte.INSTANCE);
            this.addConverter(String.class, Byte.class, StringToByte.INSTANCE);

            this.addConverter(String.class, int.class, StringToInteger.INSTANCE);
            this.addConverter(String.class, Integer.class, StringToInteger.INSTANCE);

            this.addConverter(String.class, long.class, StringToLong.INSTANCE);
            this.addConverter(String.class, Long.class, StringToLong.INSTANCE);

            this.addConverter(String.class, float.class, StringToFloat.INSTANCE);
            this.addConverter(String.class, Float.class, StringToFloat.INSTANCE);

            this.addConverter(String.class, double.class, StringToDouble.INSTANCE);
            this.addConverter(String.class, double.class, StringToDouble.INSTANCE);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addConverter(Converter<?, ?> converter) {
            Assert.notNull(converter, "'converter' must not be null.");

            TypeList.Generic typeArguments = getConverterTypeArguments(converter);

            this.converterMap.put(
                    new ConverterCacheKey(typeArguments.get(0), typeArguments.get(1)),
                    converter
            );
        }

        private TypeList.Generic getConverterTypeArguments(Converter<?, ?> converter) {
            Generic convterType = TypeDefinition.Sort.describe(converter.getClass());

            // TODO: iterate type hierarchy

            return convterType
                    .getInterfaces()
                    .filter( ElementMatchers.erasure( ElementMatchers.is(Converter.class) ) )
                    .get(0)
                    .getTypeArguments();
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public void addConverter(Class<?> sourceType, Class<?> targetType, Converter<?, ?> converter) {
            this.converterMap.put(
                    new ConverterCacheKey(
                            TypeDefinition.Sort.describe(sourceType), 
                            TypeDescription.Sort.describe(targetType)
                    ), 
                    converter
            );
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public <T> T convert(Object source, Class<T> targetType) {
            Assert.notNull(targetType, "'targetType' must not be null.");

            return convert(source, TypeDefinition.Sort.describe(targetType));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public <T> T convert(Object source, Generic targetType) {
            if (source == null)
                return null;

            Assert.notNull(targetType, "'targetType' must not be null.");


            Generic sourceType = TypeDefinition.Sort.describe(source.getClass());
            ConverterCacheKey cacheKey = new ConverterCacheKey(
                    sourceType,
                    targetType
            );

            Converter<Object, Object> converter = (Converter<Object, Object>) this.converterMap.get(cacheKey);
            if (converter != null)
                return (T) converter.convert(source);

            if (ClassUtils.isAssignableFrom(targetType, sourceType))
                return (T) source;

            throw new ConversionException("Cannot convert from [" + source.getClass() + "] to [" + targetType + "]");
        }


        /* {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public <T> T convert(Object source, Converter<?, ?> converter) {
            if (source == null)
                return null;

            Assert.notNull(converter, "'converter' must not be null.");

            try {
                return (T) ((Converter<Object, Object>) converter).convert(source);
            } catch (Exception e) {
                // failback to default converters.
                TypeList.Generic typeArguments = getConverterTypeArguments(converter);
                if (typeArguments != null)
                    return convert(source, typeArguments.get(1));

                Throwables.propagate(e);
                return null;
            }
        }


        static class ConverterCacheKey {

            private final Generic sourceTpe;
            private final Generic targetTpe;

            ConverterCacheKey(Generic sourceTpe, Generic targetTpe) {
                this.sourceTpe = sourceTpe;
                this.targetTpe = targetTpe;
            }

            public Generic getSourceTpe() {
                return sourceTpe;
            }

            public Generic getTargetTpe() {
                return targetTpe;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result = prime * result + ((sourceTpe == null) ? 0 : sourceTpe.hashCode());
                result = prime * result + ((targetTpe == null) ? 0 : targetTpe.hashCode());
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj)
                    return true;
                if (obj == null)
                    return false;
                if (getClass() != obj.getClass())
                    return false;
                ConverterCacheKey other = (ConverterCacheKey) obj;
                if (sourceTpe == null) {
                    if (other.sourceTpe != null)
                        return false;
                } else if (!sourceTpe.equals(other.sourceTpe))
                    return false;
                if (targetTpe == null) {
                    if (other.targetTpe != null)
                        return false;
                } else if (!targetTpe.equals(other.targetTpe))
                    return false;
                return true;
            }
        }
    }
}
