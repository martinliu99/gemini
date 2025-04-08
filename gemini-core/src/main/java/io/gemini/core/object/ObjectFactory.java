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
package io.gemini.core.object;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.core.util.Assert;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface ObjectFactory extends Closeable {

    void start();

    ClassScanner getClassScanner();

    void registerSingleton(String objectName, Object existingObject);

    Class<?> loadClass(String className) throws ObjectsException;

    <T> List<T> createObjectsImplementing(Class<T> clazz) throws ObjectsException;

    <T> T createObject(Class<T> clazz) throws ObjectsException;

    void close();


    abstract class AbstractBase implements ObjectFactory {

        protected static final Logger LOGGER = LoggerFactory.getLogger(ObjectFactory.class);

        private final ClassLoader classLoader;
        private final ClassScanner classScanner;


        protected AbstractBase(ClassLoader classLoader,
                ClassScanner classScanner) {
            Assert.notNull(classLoader, "'classLoader' must not be null");
            this.classLoader = classLoader;

            Assert.notNull(classScanner, "'classScanner' must not be null");
            this.classScanner = classScanner;
        }

        protected ClassLoader getClassLoader() {
            return this.classLoader;
        }


        public ClassScanner getClassScanner() {
            return classScanner;
        }

        @Override
        public Class<?> loadClass(String className) throws ObjectsException {
            Assert.hasText(className, "'className' must not be empty.");

            try {
                return this.getClassLoader().loadClass(className);
            } catch (ClassNotFoundException e) {
                throw new ObjectsException(e);
            }
        }


        @Override
        public <T> T createObject(Class<T> clazz) throws ObjectsException {
            if(clazz == null 
                    || clazz.isInterface() 
                    || clazz.isAnnotation()
                    || clazz.isEnum()
                    || Modifier.isAbstract(clazz.getModifiers()) == true 
                    || Modifier.isPublic(clazz.getModifiers()) == false ) 
                throw new IllegalArgumentException("Could not instantiate illegal type '" + clazz + "'");

            return this.doCreateObject(clazz);
        }

        protected abstract <T> T doCreateObject(Class<T> clazz) throws ObjectsException;


        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> createObjectsImplementing(Class<T> clazz) throws ObjectsException {
            List<String> classNames = classScanner.getClassNamesImplementing(clazz.getName());

            List<T> objects = new ArrayList<>(classNames.size());
            for(String className : classNames) {
                Class<T> canidateType = (Class<T>) this.loadClass(className);
                if(canidateType.isInterface() || Modifier.isAbstract(canidateType.getModifiers()) ) 
                    continue;

                objects.add(this.createObject(canidateType));
            }

            return objects;
        }
    }


    class Simple extends AbstractBase {

        private static final Set<String> INJECTION_ANNOTATION;

        private ConcurrentMap<String, Object> objectMap;

        static {
            INJECTION_ANNOTATION = new HashSet<>();
            INJECTION_ANNOTATION.add("javax.inject.Inject");
            INJECTION_ANNOTATION.add("jakarta.inject.Inject");
            INJECTION_ANNOTATION.add("javax.annotation.Resource");
            INJECTION_ANNOTATION.add("jakarta.annotation.Resource");
            INJECTION_ANNOTATION.add("org.springframework.beans.factory.annotation.Autowired");
        }

        protected Simple(ClassLoader classLoader, ClassScanner classScanner) {
            super(classLoader, classScanner);

            this.objectMap = new ConcurrentHashMap<>();
        }

        @Override
        public void start() {
        }

        @Override
        public void registerSingleton(String objectName, Object existingObject) {
            this.objectMap.put(objectName, existingObject);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <T> T doCreateObject(Class<T> clazz) throws ObjectsException {
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();

            try {
                Constructor<?> defaultConstructor = null;
                for(Constructor<?> constructor : constructors) {
                    if(Modifier.isPublic(constructor.getModifiers()) == false)
                        continue;

                    if(constructor.getParameterCount() == 0) {
                        defaultConstructor = constructor;
                        continue;
                    }

                    int i = 0;
                    Object[] arguments = new Object[constructor.getParameterCount()];
                    boolean candidate = false;
                    for(Parameter parameter : constructor.getParameters()) {
                        // check annotation
                        for(Annotation annotation : parameter.getAnnotations()) {
                            if(INJECTION_ANNOTATION.contains(annotation.annotationType().getName()) == true) {
                                candidate = true;
                                break;
                            }
                        }
                        if(candidate == false)
                            continue;

                        // check parameter name and type
                        String paramName = parameter.getName();
                        if(this.objectMap.containsKey(paramName) == false) {
                            candidate = false;
                            break;
                        }

                        Object depentObj = this.objectMap.get(paramName);
                        if(depentObj == null || parameter.getType() != depentObj.getClass()) {
                            candidate = false;
                            break;
                        }

                        arguments[i++] = depentObj;
                    }

                    // try to instantiate object with candidate constructor
                    if(candidate) {
                        T object = (T) constructor.newInstance(arguments);
                        if(LOGGER.isDebugEnabled())
                            LOGGER.debug("Instantiated '{}' type with constructor '{}.", clazz, constructor);

                        return this.initializeObject(clazz, object);
                    }
                }

                // try to instantiate object with default constructor
                if(defaultConstructor != null) {
                    T object = (T) defaultConstructor.newInstance();
                    if(LOGGER.isDebugEnabled())
                        LOGGER.debug("Instantiated '{}' type with default constructor.", clazz);

                    return this.initializeObject(clazz, object);
                }
            } catch(Exception e) {
                throw new ObjectsException(e);
            }

            LOGGER.warn("Failed to instantiate '{}' type with below constructors,\n  {}.\n", clazz, Arrays.asList(constructors));
            throw new IllegalArgumentException("Could not instantiate type '" + clazz + "' with required constructor, ");
        }

        private <T> T initializeObject(Class<T> clazz, T object) throws Exception {
            // inject property
            for(Method method : clazz.getDeclaredMethods()) {
                if(Modifier.isAbstract(method.getModifiers()))
                    continue;
                if(Modifier.isPublic(method.getModifiers()) == false)
                    continue;
                if(method.getParameterCount() != 1) 
                    continue;

                Parameter parameter = method.getParameters()[0];

                boolean candidate = false;
                for(Annotation annotation : method.getParameters()[0].getAnnotations()) {
                    if(INJECTION_ANNOTATION.contains(annotation.annotationType().getName()) == true) {
                        candidate = true;
                        break;
                    }
                }
                if(candidate == false) 
                    continue;

                // check parameter name and type
                String paramName = parameter.getName();
                if(this.objectMap.containsKey(paramName) == false) {
                    continue;
                }

                Object depentObj = this.objectMap.get(paramName);
                if(depentObj == null || parameter.getType() != depentObj.getClass()) {
                    continue;
                }

                method.setAccessible(true);
                method.invoke(object, depentObj);
            }

            // inject field
            for(Field field : clazz.getDeclaredFields()) {
                if(Modifier.isFinal(field.getModifiers()))
                    continue;

                boolean candidate = false;
                for(Annotation annotation : field.getAnnotations()) {
                    if(INJECTION_ANNOTATION.contains(annotation.annotationType().getName()) == true) {
                        candidate = true;
                        continue;
                    }
                }
                if(candidate == false) 
                    continue;

                // check field name and type
                String fieldName = field.getName();
                if(this.objectMap.containsKey(fieldName) == false) {
                    continue;
                }

                Object depentObj = this.objectMap.get(fieldName);
                if(depentObj == null || field.getType() != depentObj.getClass()) {
                    continue;
                }

                field.setAccessible(true);
                field.set(object, depentObj);
            }

            return object;
        }

        @Override
        public void close() {
            this.objectMap.clear();
        }

    }


    class Builder {

        private static final Logger LOGGER = LoggerFactory.getLogger(Builder.class);

        private ClassLoader classLoader;
        private ClassScanner classScanner;

        public Builder classScanner(ClassScanner classScanner) {
            Assert.notNull(classScanner, "'classScanner' must not be null.");
            this.classScanner = classScanner;

            return this;
        }

        public Builder classLoader(ClassLoader classLoader) {
            Assert.notNull(classLoader, "'classLoader' must not be null.");
            this.classLoader = classLoader;

            return this;
        }

        public ObjectFactory build(boolean simple) {
            if(simple)
                return new Simple(classLoader, classScanner);

            // find ObjectFactory implementation
            List<String> classNames = this.classScanner.getClassNamesImplementing( ObjectFactory.class.getName() );
            for(String className : classNames) {
                // find and return first ObjectFactory implementation
                ObjectFactory objectFactory = null;
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    if(clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()) ) {
                        continue;
                    }

                    Constructor<?> constructor = clazz.getDeclaredConstructor(ClassLoader.class, ClassScanner.class);
                    if(constructor == null) {
                        continue;
                    }

                    objectFactory = (ObjectFactory) constructor.newInstance(classLoader, this.classScanner);
                    objectFactory.start();

                    return objectFactory;
                } catch (Exception e) {
                    LOGGER.info("Failed to start ObjectFactory '{}'.", className, e);
                }
            }

            return new Simple(classLoader, classScanner);
        }
    }
}
