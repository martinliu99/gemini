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
package org.framework.aspects;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.api.aop.Joinpoint.MutableJoinpoint;
import io.gemini.api.aop.annotation.ConditionalOnClassLoader;

/**
 * Demo advice intercepting native method of unloaded class.
 *
 * @author   martin.liu
 */
@Aspect
public class Sample03_11NativeMethod {

    private static final Logger LOGGER = LoggerFactory.getLogger(Sample03_11NativeMethod.class);

    private static final String POINTCUT_EXPR = "execution(private native static long java.util.zip.Deflater.init(int, int, boolean))";


    @ConditionalOnClassLoader(isBootstrapClassLoader = true)
    @Before(POINTCUT_EXPR)
    public void before(MutableJoinpoint<Long, RuntimeException> joinpoint) {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Entering Deflater#init {}.", joinpoint.getStaticPart());
    }

    @ConditionalOnClassLoader(isBootstrapClassLoader = true)
    @After(POINTCUT_EXPR)
    public void after(MutableJoinpoint<Long, RuntimeException> joinpoint) {
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Exiting Deflater#init {}.", joinpoint.getStaticPart());
    }
}
