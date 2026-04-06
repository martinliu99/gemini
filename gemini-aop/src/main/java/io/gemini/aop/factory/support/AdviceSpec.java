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
package io.gemini.aop.factory.support;

import java.util.Map;

import io.gemini.aop.AdviceKind;
import io.gemini.aop.AdviceKind.AspectJAdviceKind;
import io.gemini.aop.AdviceKind.ByteBuddyAdviceKind;
import io.gemini.aop.AdviceKind.PojoAdviceKind;
import io.gemini.aop.matcher.ExprPointcut.PointcutParameterMatcher;
import io.gemini.api.aop.Advice;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.dynamic.DynamicType;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdviceSpec {

    AdviceKind getAdviceKind();

    TypeDescription getDeclaringType();

    String getAdviceClassName();

    MethodDescription getAdviceMethod();

    Generic getParameterizedReturningType();

    Generic getParameterizedThrowingType();


    abstract class AbstractBase implements AdviceSpec {

        private AdviceKind adviceKind;

        private TypeDescription declaringType;

        private String adviceClassName;

        private MethodDescription adviceMethod;

        private Generic parameterizedReturningType;

        private Generic parameterizedThrowingType;


        public AbstractBase() {}

        public AbstractBase(AdviceKind adviceKind,
                TypeDescription declaringType, String adviceClassName, MethodDescription adviceMethod, 
                Generic parameterizedReturningType, Generic parameterizedThrowingType) {
            this.adviceKind = adviceKind;

            this.declaringType = declaringType;
            this.adviceClassName = adviceClassName;

            this.adviceMethod = adviceMethod;
            this.parameterizedReturningType = parameterizedReturningType;
            this.parameterizedThrowingType = parameterizedThrowingType;
        }

        /**
         * {@inheritDoc}
         */
        public AdviceKind getAdviceKind() {
            return adviceKind;
        }

        protected void setAdviceKind(AdviceKind adviceKind) {
            this.adviceKind = adviceKind;
        }

        /**
         * {@inheritDoc}
         */
        public TypeDescription getDeclaringType() {
            return declaringType;
        }

        protected void setDeclaringType(TypeDescription declaringType) {
            this.declaringType = declaringType;
        }

        /**
         * {@inheritDoc}
         */
        public String getAdviceClassName() {
            return adviceClassName;
        }

        protected void setAdviceClassName(String adviceClassName) {
            this.adviceClassName = adviceClassName;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MethodDescription getAdviceMethod() {
            return adviceMethod;
        }

        protected void setAdviceMethod(MethodDescription adviceMethod) {
            this.adviceMethod = adviceMethod;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Generic getParameterizedReturningType() {
            return parameterizedReturningType;
        }

        protected void setParameterizedReturningType(Generic parameterizedReturningType) {
            this.parameterizedReturningType = parameterizedReturningType;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Generic getParameterizedThrowingType() {
            return parameterizedThrowingType;
        }

        protected void setParameterizedThrowingType(Generic parameterizedThrowingType) {
            this.parameterizedThrowingType = parameterizedThrowingType;
        }
    }


    interface PojoAdviceSpec extends AdviceSpec {

        PojoAdviceKind getAdviceKind();


        class Default extends AbstractBase implements PojoAdviceSpec {

            public Default(PojoAdviceKind adviceKind,
                    TypeDescription declaringType, String adviceClassName, MethodDescription adviceMethod,
                    Generic parameterizedReturningType, Generic parameterizedThrowingType) {
                super(adviceKind,
                        declaringType, adviceClassName, adviceMethod,
                        parameterizedReturningType, parameterizedThrowingType);

            }


            public PojoAdviceKind getAdviceKind() {
                return (PojoAdviceKind) super.getAdviceKind();
            }
        }
    }


    interface AspectJAdviceSpec extends AdviceSpec, PointcutParameterMatcher {

        AspectJAdviceKind getAdviceKind();

        AnnotationDescription getAdviceAnnotation();

        Generic getAdviceReturningParameterType();

        Generic getAdviceThrowingParameterType();

        Map<String, Generic> getPointcutParameterTypes();


        Map<String, NamedPointcutParameter> getNamedPointcutParameters();

        boolean isVoidReturning();


        DynamicType.Unloaded<? extends Advice> getUnloadedAdviceClass();
    }


    interface ByteBuddyAdviceSpec extends AdviceSpec {

        class Default extends AbstractBase implements ByteBuddyAdviceSpec {

            public Default(ByteBuddyAdviceKind adviceKind, TypeDescription declaringType) {
                super(adviceKind,
                        declaringType, declaringType.getTypeName(), null,
                        null, null);
            }


            public ByteBuddyAdviceKind getAdviceKind() {
                return (ByteBuddyAdviceKind) super.getAdviceKind();
            }
        }
    }
}