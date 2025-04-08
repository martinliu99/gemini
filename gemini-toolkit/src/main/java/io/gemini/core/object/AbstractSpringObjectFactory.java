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

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.gemini.core.object.ObjectFactory.AbstractBase;

public abstract class AbstractSpringObjectFactory extends AbstractBase {

    private ConfigurableApplicationContext applicationContext;

    private ConfigurableBeanFactory beanFactory;


    public AbstractSpringObjectFactory(ClassLoader classLoader,
            ClassScanner classScanner) {
        super(classLoader, classScanner);
    }


    @Override
    public void start() {
        this.applicationContext = new AnnotationConfigApplicationContext("io.gemini.aspects");
        this.beanFactory = (ConfigurableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
    }

    protected abstract String[] getBasePackages();

    protected ConfigurableApplicationContext getApplicationContext() {
        return this.applicationContext;
    }

    @Override
    public void registerSingleton(String objectName, Object existingObject) {
        beanFactory.registerSingleton(objectName, existingObject);
    }

    @Override
    protected <T> T doCreateObject(Class<T> clazz) throws ObjectsException {
        AutowireCapableBeanFactory beanFactory = this.applicationContext.getAutowireCapableBeanFactory();

        return beanFactory.createBean(clazz);
    }

    @Override
    public void close() {
        this.applicationContext.close();
    }

}
