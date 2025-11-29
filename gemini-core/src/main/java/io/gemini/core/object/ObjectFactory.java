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

import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.BaseException;
import io.gemini.api.annotation.Initializer;
import io.gemini.core.DiagnosticLevel;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.ReflectionUtils;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface ObjectFactory extends Closeable {

    void start();

    void registerSingleton(String objectName, Object existingObject);


    <T> Class<T> loadClass(String className) throws ObjectsException;

    <T> boolean isInstantiatable(Class<T> clazz);


    <T> T createObject(Class<T> clazz, Map<String, Object> arguments) throws ObjectsException;

    <T> T createObject(Class<T> clazz) throws ObjectsException;

    <T> List<T> createObjectsImplementing(Class<T> clazz) throws ObjectsException;


    void close() throws IOException;


    public class ObjectsException extends BaseException {

        private static final long serialVersionUID = 7286124443140436785L;

        public ObjectsException(String message) {
            super(message);
        }

        public ObjectsException(Throwable t) {
            super(t);
        }

        public ObjectsException(String message, Throwable t) {
            super(message, t);
        }
    }


    abstract class AbstractBase implements ObjectFactory {

        protected static final Logger LOGGER = LoggerFactory.getLogger(ObjectFactory.class);

        private final DiagnosticLevel diagnosticLevel;

        private final ClassLoader classLoader;
        private final ClassScanner classScanner;


        protected AbstractBase(DiagnosticLevel diagnosticLevel,
                ClassLoader classLoader, ClassScanner classScanner) {
            Assert.notNull(classLoader, "'diagnosticLevel' must not be null");
            this.diagnosticLevel = diagnosticLevel == null ? DiagnosticLevel.DISABLED : diagnosticLevel;

            Assert.notNull(classLoader, "'classLoader' must not be null");
            this.classLoader = classLoader;

            Assert.notNull(classScanner, "'classScanner' must not be null");
            this.classScanner = classScanner;
        }

        protected DiagnosticLevel getDiagnosticLevel() {
            return diagnosticLevel;
        }

        protected ClassLoader getClassLoader() {
            return this.classLoader;
        }

        protected ClassScanner getClassScanner() {
            return classScanner;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Class<T> loadClass(String className) throws ObjectsException {
            Assert.hasText(className, "'className' must not be empty.");

            try {
                return (Class<T>) this.getClassLoader().loadClass(className);
            } catch (Throwable t) {
                Throwables.throwIfRequired(t);

                throw new ObjectsException(t);
            }
        }

        @Override
        public <T> boolean isInstantiatable(Class<T> clazz) {
            // top level or nested, concrete class
            return (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()) 
                        && !clazz.isEnum() && !clazz.isAnnotation() )
                    && ((clazz.getEnclosingConstructor() == null && clazz.getEnclosingMethod() == null)
                        || Modifier.isStatic(clazz.getModifiers()) )
            ;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public <T> T createObject(Class<T> clazz, Map<String, Object> arguments) throws ObjectsException {
            Assert.notNull(clazz, "'clazz' must not be null.");
            arguments =  arguments == null ? Collections.emptyMap() : arguments;


            // find constructor with @Initializer
            Constructor<T> candidateConstructor = null;
            for (Constructor<T> constructor : (Constructor<T>[]) clazz.getDeclaredConstructors()) {
                if (constructor.getAnnotationsByType(Initializer.class) == null)
                    continue;

                if (candidateConstructor == null)
                    candidateConstructor = constructor;
                else
                    throw new ObjectsException("Class [" + clazz.getName() + "] contains multiple constructors annotated with @" + Initializer.class.getName());
            }

            if (candidateConstructor == null)
                return doCreateObject(clazz);


            // prepare arguments
            Object[] invocationArgs = new Object[candidateConstructor.getParameterCount()];
            int i = 0;
            for (Parameter parameter : candidateConstructor.getParameters()) {
                String parameterName = parameter.getName();
                Class<?> parameterType = parameter.getType();

                Object argument = arguments.get(parameterName);
                if (argument == null || ClassUtils.isAssignableFrom(parameterType, argument.getClass()) == false)
                    throw new ObjectsException("Illegal argument type [" + (argument == null ? "null" : argument.getClass())
                            + "] for parameter [" + parameterName 
                            + "] of constrcutor [" + candidateConstructor + "]");

                invocationArgs[i++] = argument;
            }

            // instantiate and class
            try {
                return doInstantiateObject(clazz, candidateConstructor, invocationArgs);
            } catch (Exception e) {
                throw new ObjectsException("Cannot instantiate class [" + clazz.getName() + "]", e);
            }
        }

        protected <T> T doInstantiateObject(Class<T> clazz, Constructor<T> constructor, Object[] arguments) 
                throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            ReflectionUtils.makeAccessible(clazz, constructor);

            T object = arguments == null 
                    ? (T) constructor.newInstance() : (T) constructor.newInstance(arguments);

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Instantiated '{}' type with constructor '{}.", clazz, constructor);

            return object;
        }


        @Override
        public <T> T createObject(Class<T> clazz) throws ObjectsException {
            Assert.notNull(clazz, "'clazz must not be null.'");
            Assert.isTrue(isInstantiatable(clazz), "clazz '" + clazz + "' must be top-level or nested, concrete class.");

            return this.doCreateObject(clazz);
        }

        @Override
        public <T> List<T> createObjectsImplementing(Class<T> clazz) throws ObjectsException {
            Assert.notNull(clazz, "'clazz must not be null.'");

            List<String> classNames = classScanner.getClassNamesImplementing(clazz.getName());

            List<T> objects = new ArrayList<>(classNames.size());
            for (String className : classNames) {
                Class<T> canidateType = this.loadClass(className);
                if (isInstantiatable(canidateType) == false) 
                    continue;

                objects.add(this.doCreateObject(canidateType));
            }

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Instantiated '{}' objects implemeting '{}'. {}", 
                        objects.size(), clazz.getName(),
                        StringUtils.join(objects, obj -> obj.toString(), "\n  ", "\n  ", "\n")
                );

            return objects;
        }

        protected abstract <T> T doCreateObject(Class<T> clazz) throws ObjectsException;
    }


    class Simple extends AbstractBase {

        private static final Set<String> INJECTION_ANNOTATION;

        private ConcurrentMap<String, Object> objectMap;

        static {
            INJECTION_ANNOTATION = new LinkedHashSet<>();
            INJECTION_ANNOTATION.add("javax.inject.Inject");
            INJECTION_ANNOTATION.add("jakarta.inject.Inject");
            INJECTION_ANNOTATION.add("javax.annotation.Resource");
            INJECTION_ANNOTATION.add("jakarta.annotation.Resource");
            INJECTION_ANNOTATION.add("org.springframework.beans.factory.annotation.Autowired");
        }

        protected Simple(DiagnosticLevel diagnosticLevel, ClassLoader classLoader, ClassScanner classScanner) {
            super(diagnosticLevel, classLoader, classScanner);

            this.objectMap = new ConcurrentHashMap<>();
        }

        @Override
        public void start() {
        }

        @Override
        public void registerSingleton(String objectName, Object existingObject) {
            this.objectMap.put(objectName, existingObject);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <T> T doCreateObject(Class<T> clazz) throws ObjectsException {
            Constructor<T>[] constructors = (Constructor<T>[]) clazz.getDeclaredConstructors();

            try {
                Constructor<T> candidateConstructor = null;
                for (Constructor<T> constructor : constructors) {
                    if (constructor.getParameterCount() == 0) {
                        candidateConstructor = constructor;
                        continue;
                    }

                    int i = 0;
                    Object[] arguments = new Object[constructor.getParameterCount()];
                    boolean candidate = false;
                    for (Parameter parameter : constructor.getParameters()) {
                        // check annotation
                        for (Annotation annotation : parameter.getAnnotations()) {
                            if (INJECTION_ANNOTATION.contains(annotation.annotationType().getName()) == true) {
                                candidate = true;
                                break;
                            }
                        }
                        if (candidate == false)
                            continue;

                        // check parameter name and type
                        String paramName = parameter.getName();
                        if (this.objectMap.containsKey(paramName) == false) {
                            candidate = false;
                            break;
                        }

                        Object depentObj = this.objectMap.get(paramName);
                        if (depentObj == null || parameter.getType() != depentObj.getClass()) {
                            candidate = false;
                            break;
                        }

                        arguments[i++] = depentObj;
                    }

                    // try to instantiate object with candidate constructor
                    if (candidate) {
                        T object = this.doInstantiateObject(clazz, constructor, arguments);

                        return this.initializeObject(clazz, object);
                    }
                }

                // try to instantiate object with default constructor
                if (candidateConstructor != null) {
                    T object = this.doInstantiateObject(clazz, candidateConstructor, null);

                    return this.initializeObject(clazz, object);
                }
            } catch (Throwable t) {
                Throwables.throwIfRequired(t);

                throw new ObjectsException(t);
            }

            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Could not instantiate '{}' type with below constructors,\n"
                        + "  {}.\n", clazz, Arrays.asList(constructors));

            throw new IllegalArgumentException("Cannot instantiate class [" + clazz + "] with required constructor");
        }

        private <T> T initializeObject(Class<T> clazz, T object) throws Exception {
            // inject property
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getParameterCount() != 1) 
                    continue;

                Parameter parameter = method.getParameters()[0];

                boolean candidate = false;
                for (Annotation annotation : method.getParameters()[0].getAnnotations()) {
                    if (INJECTION_ANNOTATION.contains(annotation.annotationType().getName()) == true) {
                        candidate = true;
                        break;
                    }
                }
                if (candidate == false) 
                    continue;

                // check parameter name and type
                String paramName = parameter.getName();
                if (this.objectMap.containsKey(paramName) == false) {
                    continue;
                }

                Object depentObj = this.objectMap.get(paramName);
                if (depentObj == null || parameter.getType() != depentObj.getClass()) {
                    continue;
                }

                ReflectionUtils.makeAccessible(clazz, method);
                method.invoke(object, depentObj);
            }

            // inject field
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isFinal(field.getModifiers()))
                    continue;

                boolean candidate = false;
                for (Annotation annotation : field.getAnnotations()) {
                    if (INJECTION_ANNOTATION.contains(annotation.annotationType().getName()) == true) {
                        candidate = true;
                        continue;
                    }
                }
                if (candidate == false) 
                    continue;

                // check field name and type
                String fieldName = field.getName();
                if (this.objectMap.containsKey(fieldName) == false) {
                    continue;
                }

                Object depentObj = this.objectMap.get(fieldName);
                if (depentObj == null || field.getType() != depentObj.getClass()) {
                    continue;
                }

                ReflectionUtils.makeAccessible(clazz, field);
                field.set(object, depentObj);
            }

            return object;
        }


        @Override
        public void close() throws IOException {
            this.objectMap.clear();
        }
    }


    class Builder {

        private static final Logger LOGGER = LoggerFactory.getLogger(Builder.class);

        private DiagnosticLevel diagnosticLevel;

        private ClassLoader classLoader;
        private ClassScanner classScanner;


        public Builder diagnosticLevel(DiagnosticLevel diagnosticLevel) {
            this.diagnosticLevel = diagnosticLevel;

            return this;
        }

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
            if (simple)
                return new Simple(diagnosticLevel, classLoader, classScanner);

            // find ObjectFactory implementation
            List<String> classNames = this.classScanner.getClassNamesImplementing( ObjectFactory.class.getName() );
            for (String className : classNames) {
                // find and return first ObjectFactory implementation
                ObjectFactory objectFactory = null;
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()) ) {
                        continue;
                    }

                    Constructor<?> constructor = clazz.getDeclaredConstructor(
                            DiagnosticLevel.class, ClassLoader.class, ClassScanner.class);
                    if (constructor == null) {
                        continue;
                    }

                    objectFactory = (ObjectFactory) constructor.newInstance(
                            diagnosticLevel, classLoader, this.classScanner);
                    objectFactory.start();

                    return objectFactory;
                } catch (Throwable t) {
                    if (LOGGER.isWarnEnabled())
                        LOGGER.warn("Could not start ObjectFactory '{}'.", className, t);

                    Throwables.throwIfRequired(t);
                }
            }

            return new Simple(diagnosticLevel, classLoader, classScanner);
        }
    }
}
