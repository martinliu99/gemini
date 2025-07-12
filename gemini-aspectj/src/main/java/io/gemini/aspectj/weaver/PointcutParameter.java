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
package io.gemini.aspectj.weaver;


public interface PointcutParameter {

    int INVALID_ARGS_INDEX = -1;


    ParamType getParamType();

    int getArgsIndex();


    enum ParamType {

        JOINPOINT_PARAM,
        MUTABLE_JOINPOINT_PARAM,
        PROCEDDING_JOINPOINT_PARAM,

        STATIC_PART_PARAM,
        RETURNING_ANNOTATION,
        THROWING_ANNOTATION,

        ARGS_CONVERTION,

        THIS_VAR,
        TARGET_VAR,
        ARGS_VAR,
        AT_THIS_VAR,
        AT_TARGET_VAR,
        AT_ARGS_VAR,
        AT_WITHIN_VAR,
        AT_WITHINCODE_VAR,
        AT_ANNOTATION_VAR,
        ;
    }


    interface NamedPointcutParameter extends PointcutParameter {

        /**
         * The name of this parameter
         */
        String getParamName();
    }


    class Default implements NamedPointcutParameter {

        private final String paramName;
        private final ParamType paramType;
        private final int argsIndex;

        public Default(String paramName, ParamType paramType) {
            this.paramName = paramName;
            this.paramType = paramType;
            this.argsIndex = INVALID_ARGS_INDEX;
        }

        public Default(String paramName, PointcutParameter pointcutParameter) {
            this.paramName = paramName;
            this.paramType = pointcutParameter.getParamType();
            this.argsIndex = pointcutParameter.getArgsIndex();
        }

        /** 
         * {@inheritDoc} 
         */
        @Override
        public String getParamName() {
            return this.paramName;
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        public ParamType getParamType() {
            return this.paramType;
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        public int getArgsIndex() {
            return argsIndex;
        }
    }
}
