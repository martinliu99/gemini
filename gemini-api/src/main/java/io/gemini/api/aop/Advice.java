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
package io.gemini.api.aop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.Joinpoint.ProceedingJoinpoint;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface Advice {


    interface Before<T, E extends Throwable> extends Advice {

        void before(MutableJoinpoint<T, E> joinpoint) throws Throwable;

    }


    interface After<T, E extends Throwable> extends Advice {

        void after(MutableJoinpoint<T, E> joinpoint) throws Throwable;

    }


    interface Around<T, E extends Throwable> extends Advice {

        T invoke(ProceedingJoinpoint<T, E> joinpoint) throws E;

    }


    abstract class AbstractBefore<T, E extends Throwable> implements Before<T, E> {

        protected static final Logger LOGGER = LoggerFactory.getLogger(Advice.class);

    }

    abstract class AbstractAfter<T, E extends Throwable> implements After<T, E> {

        protected static final Logger LOGGER = LoggerFactory.getLogger(Advice.class);

    }

    abstract class AbstractBeforeAfter<T, E extends Throwable> extends AbstractBefore<T, E> implements After<T, E> {

    }
}
