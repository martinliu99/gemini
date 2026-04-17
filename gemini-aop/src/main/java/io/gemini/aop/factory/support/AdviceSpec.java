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
 * Describes the metadata of an advice method or class parsed from an aspect application.
 * <p>
 * Three sub-interfaces cover the three advice styles:
 * <ul>
 *   <li>{@link PojoAdviceSpec} – POJO before/after/around advice</li>
 *   <li>{@link AspectJAdviceSpec} – AspectJ annotation-based advice with pointcut parameter binding</li>
 *   <li>{@link ByteBuddyAdviceSpec} – raw ByteBuddy {@code @Advice} class</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public interface AdviceSpec {

    /**
     * Returns the advice kind (POJO, AspectJ, or ByteBuddy).
     *
     * @return the {@link AdviceKind}
     */
    AdviceKind getAdviceKind();

    /**
     * Returns the type that declares the advice method or class.
     *
     * @return the declaring {@link TypeDescription}
     */
    TypeDescription getDeclaringType();

    /**
     * Returns the fully-qualified class name of the advice class.
     *
     * @return the advice class name
     */
    String getAdviceClassName();

    /**
     * Returns the advice method description, or {@code null} for ByteBuddy advice.
     *
     * @return the advice {@link MethodDescription}, or {@code null}
     */
    MethodDescription getAdviceMethod();

    /**
     * Returns the parameterized return type of the advice method's joinpoint parameter,
     * or {@code null} if the joinpoint is not parameterized.
     *
     * @return the parameterized returning type, or {@code null}
     */
    Generic getParameterizedReturningType();

    /**
     * Returns the parameterized throwing type of the advice method's joinpoint parameter,
     * or {@code null} if the joinpoint is not parameterized.
     *
     * @return the parameterized throwing type, or {@code null}
     */
    Generic getParameterizedThrowingType();


    /**
     * Abstract base providing common advice spec properties: advice kind, declaring type,
     * advice class name, advice method, and parameterized return/throw types.
     */
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

        /**
         * Sets the advice kind. Called by subclasses during initialization.
         *
         * @param adviceKind the advice kind to set
         */
        protected void setAdviceKind(AdviceKind adviceKind) {
            this.adviceKind = adviceKind;
        }

        /**
         * {@inheritDoc}
         */
        public TypeDescription getDeclaringType() {
            return declaringType;
        }

        /**
         * Sets the declaring type. Called by subclasses during initialization.
         *
         * @param declaringType the declaring type to set
         */
        protected void setDeclaringType(TypeDescription declaringType) {
            this.declaringType = declaringType;
        }

        /**
         * {@inheritDoc}
         */
        public String getAdviceClassName() {
            return adviceClassName;
        }

        /**
         * Sets the advice class name. Called by subclasses during initialization.
         *
         * @param adviceClassName the fully-qualified advice class name
         */
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

        /**
         * Sets the advice method. Called by subclasses during initialization.
         *
         * @param adviceMethod the advice method description
         */
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

        /**
         * Sets the parameterized returning type. Called by subclasses during initialization.
         *
         * @param parameterizedReturningType the generic returning type
         */
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

        /**
         * Sets the parameterized throwing type. Called by subclasses during initialization.
         *
         * @param parameterizedThrowingType the generic throwing type
         */
        protected void setParameterizedThrowingType(Generic parameterizedThrowingType) {
            this.parameterizedThrowingType = parameterizedThrowingType;
        }
    }


    /** 
     * POJO-style advice spec for before/after/around advice implementations. 
     */
    interface PojoAdviceSpec extends AdviceSpec {

        /**
         * Returns the POJO advice kind (BEFORE, AFTER, BEFORE_AFTER, or AROUND).
         *
         * @return the {@link PojoAdviceKind}
         */
        PojoAdviceKind getAdviceKind();


        /**
         * Default {@link PojoAdviceSpec} implementation for POJO before/after/around advice.
         */
        class Default extends AbstractBase implements PojoAdviceSpec {

            /**
             * Creates a POJO advice spec.
             *
             * @param adviceKind                 the POJO advice kind
             * @param declaringType              the declaring type
             * @param adviceClassName            the fully-qualified advice class name
             * @param adviceMethod               the advice method (before/after/invoke)
             * @param parameterizedReturningType the parameterized return type of the joinpoint, or {@code null}
             * @param parameterizedThrowingType  the parameterized throw type of the joinpoint, or {@code null}
             */
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


    /**
     * AspectJ annotation-based advice spec with pointcut parameter binding metadata.
     * Carries the advice annotation, returning/throwing parameter types, and the
     * unloaded generated advice class.
     */
    interface AspectJAdviceSpec extends AdviceSpec, PointcutParameterMatcher {

        /** 
         * Returns the AspectJ advice kind (@Before, @After, @AfterReturning, @AfterThrowing, @Around). 
         */
        AspectJAdviceKind getAdviceKind();

        /** 
         * Returns the AspectJ advice annotation (e.g., {@code @Before}, {@code @Around}). 
         */
        AnnotationDescription getAdviceAnnotation();

        /**
         * Returns the generic type of the {@code returning} parameter in the advice method,
         * or {@code null} if no returning parameter is declared.
         *
         * @return the advice returning parameter type, or {@code null}
         */
        Generic getAdviceReturningParameterType();

        /**
         * Returns the generic type of the {@code throwing} parameter in the advice method,
         * or {@code null} if no throwing parameter is declared.
         *
         * @return the advice throwing parameter type, or {@code null}
         */
        Generic getAdviceThrowingParameterType();

        /**
         * Returns the map of pointcut parameter names to their generic types,
         * used for parameter binding in the generated advice class.
         *
         * @return map of parameter name to generic type
         */
        Map<String, Generic> getPointcutParameterTypes();

        /**
         * Returns the resolved named pointcut parameters with their binding categories
         * (e.g., args, this, target, returning, throwing).
         *
         * @return map of parameter name to {@link NamedPointcutParameter}
         */
        Map<String, NamedPointcutParameter> getNamedPointcutParameters();

        /**
         * Returns {@code true} if the target method returns {@code void},
         * which affects how the returning parameter is handled in the generated advice.
         *
         * @return {@code true} if the target method is void-returning
         */
        boolean isVoidReturning();

        /**
         * Returns the dynamically generated, unloaded {@link Advice} class that wraps
         * the AspectJ advice method for use with the Gemini AOP framework dispatch mechanism.
         *
         * @return the unloaded advice class
         */
        DynamicType.Unloaded<? extends Advice> getUnloadedAdviceClass();
    }


    /** 
     * Raw ByteBuddy {@code @Advice} class spec — no advice method, just the class itself. 
     */
    interface ByteBuddyAdviceSpec extends AdviceSpec {

        /**
         * Default {@link ByteBuddyAdviceSpec} implementation for raw ByteBuddy {@code @Advice} classes.
         */
        class Default extends AbstractBase implements ByteBuddyAdviceSpec {

            /**
             * Creates a ByteBuddy advice spec from the given advice class type.
             *
             * @param adviceKind    the ByteBuddy advice kind (OnMethodEnter, OnMethodExit, or both)
             * @param declaringType the ByteBuddy {@code @Advice} class type description
             */
            public Default(ByteBuddyAdviceKind adviceKind, TypeDescription declaringType) {
                super(adviceKind,
                        declaringType, declaringType.getTypeName(), null,
                        null, null);
            }


            /** 
             * Returns the Buddy advice kind (OnMethodEnter, OnMethodExit, or both). 
             */
            public ByteBuddyAdviceKind getAdviceKind() {
                return (ByteBuddyAdviceKind) super.getAdviceKind();
            }
        }
    }
}