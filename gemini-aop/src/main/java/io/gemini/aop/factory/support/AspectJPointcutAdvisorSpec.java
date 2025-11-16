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
package io.gemini.aop.factory.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.gemini.aop.matcher.ExprPointcut.PointcutParameterMatcher;
import io.gemini.api.annotation.NoScanning;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.aspectj.weaver.PointcutParameter.NamedPointcutParameter;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;


@NoScanning
interface AspectJPointcutAdvisorSpec extends AdvisorSpec.ExprPointcutSpec, PointcutParameterMatcher {

    TypeDescription getAspectJType();

    MethodDescription getAspectJMethod();


    AdviceCategory getAdviceCategory();

    Map<String, Generic> getPointcutParameterTypes();

    Map<String, NamedPointcutParameter> getNamedPointcutParameters();


    Generic getAdviceReturningParameterType();

    Generic getAdviceThrowingParameterType();


    boolean isVoidReturningOfTargetMethod();


    /** 
     * {@inheritDoc}
     */
    @Override
    boolean match(MethodDescription methodDescription, List<NamedPointcutParameter> pointcutParameters);


    enum AdviceCategory {

        BEFORE(true, false, false, false, false),
        AFTER(false, true, false, false, false),
        AFTER_RETURNING(false, false, true, false, false),
        AFTER_THROWING(false, false, false, true, false),
        AROUND(false, false, false, false, true);

        private final boolean before;

        private final boolean after;
        private final boolean afterReturning;
        private final boolean afterThrowing;

        private final boolean around;

        private static Map<String, AdviceCategory> VALUE_MAP;
        static {
            VALUE_MAP = new HashMap<>(AdviceCategory.values().length);
            for (AdviceCategory adviceCategory : AdviceCategory.values())
                VALUE_MAP.put(adviceCategory.toString().replace("_", ""), adviceCategory);
        }

        public static AdviceCategory parse(String value) {
            String category = value == null ? "" : value.trim().toUpperCase();
            AdviceCategory adviceCategory = VALUE_MAP.get(category);
            if (adviceCategory != null)
                return adviceCategory;

            throw new IllegalArgumentException("Unsupported AdviceCategory [" + value + "]");
        }

        private AdviceCategory(boolean before, boolean after, 
                boolean afterReturning, boolean afterThrowing,
                boolean around) {
            this.before = before;
            this.after = after;
            this.afterReturning = afterReturning;
            this.afterThrowing = afterThrowing;
            this.around = around;
        }

        public boolean isBefore() {
            return before;
        }

        public boolean isAfter() {
            return after;
        }

        public boolean isAfterReturning() {
            return afterReturning;
        }

        public boolean isAfterThrowing() {
            return afterThrowing;
        }

        public boolean isAround() {
            return around;
        }
    }
}