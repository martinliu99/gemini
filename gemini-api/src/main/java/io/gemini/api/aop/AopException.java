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

import io.gemini.api.BaseException;

public class AopException extends BaseException {

    private static final long serialVersionUID = 1580062864289647476L;


    public AopException(String message) {
        super(message);
    }

    public AopException(Throwable t) {
        super(t);
    }

    public AopException(String message, Throwable t) {
        super(message, t);
    }


    public static class WrappedException extends AopException {

        private static final long serialVersionUID = 5044738870345851991L;


        public WrappedException(String message) {
            super(message);
        }

        public WrappedException(String message, Throwable cause) {
            super(message, cause);
        }

        public WrappedException(Throwable cause) {
            super(cause);
        }
    }
}
