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
package io.gemini.aspectj.weaver;

import net.bytebuddy.description.type.TypeDescription.Generic;

/**
 * Describes a single parameter in an AspectJ pointcut binding.
 * <p>
 * Each parameter has a category ({@link ParamCategory}) indicating its role
 * (e.g., joinpoint, returning value, args binding), an optional argument index,
 * and — for named parameters — a name and generic type.
 * </p>
 *
 * @author   martin.liu
 */
public interface PointcutParameter {

    int INVALID_ARGS_INDEX = -1;


    /**
     * Returns the category of this pointcut parameter, indicating its role
     * (e.g., joinpoint, args binding, annotation variable).
     *
     * @return the parameter category; never {@code null}
     */
    ParamCategory getParamCategory();

    /**
     * Returns the index of this parameter in the method's argument list,
     * or {@link #INVALID_ARGS_INDEX} if not applicable.
     *
     * @return the argument index, or {@code -1} if not an args-bound parameter
     */
    int getArgsIndex();


    /**
     * Enumerates parameter categories.
     */
    enum ParamCategory {

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


    /**
     * Describes a named {@code PointcutParameter} with parameter type.
     */
    interface NamedPointcutParameter extends PointcutParameter {

        /**
         * Returns the name of this parameter as declared in the pointcut expression.
         *
         * @return the parameter name; never {@code null}
         */
        String getParamName();

        /**
         * Returns the generic type of this parameter.
         *
         * @return the parameter's generic type; never {@code null}
         */
        Generic getParamType();
    }


    /**
     * Default implementation of {@code NamedPointcutParameter}.
     */
    class Default implements NamedPointcutParameter {

        private final String paramName;
        private final Generic paramType;
        private final ParamCategory paramCategory;
        private final int argsIndex;

        /**
         * Creates a {@code Default} parameter with no args index (set to {@link #INVALID_ARGS_INDEX}).
         *
         * @param paramName     the parameter name
         * @param paramType     the parameter's generic type
         * @param paramCategory the parameter category
         */
        public Default(String paramName, Generic paramType, 
                ParamCategory paramCategory) {
            this.paramName = paramName;
            this.paramType = paramType;
            this.paramCategory = paramCategory;
            this.argsIndex = INVALID_ARGS_INDEX;
        }

        /**
         * Creates a {@code Default} parameter by copying the category and args index
         * from an existing {@link PointcutParameter}.
         *
         * @param paramName          the parameter name
         * @param paramType          the parameter's generic type
         * @param pointcutParameter  the source parameter to copy category and args index from
         */
        public Default(String paramName, Generic paramType,
                PointcutParameter pointcutParameter) {
            this.paramName = paramName;
            this.paramType = paramType;
            this.paramCategory = pointcutParameter.getParamCategory();
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
         * {@inheritDoc}
         */
        @Override
        public Generic getParamType() {
            return paramType;
        }

        /**
         *  {@inheritDoc} 
         */
        @Override
        public ParamCategory getParamCategory() {
            return this.paramCategory;
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
