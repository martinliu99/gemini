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
package io.gemini.aop;

import java.util.function.Supplier;

import io.gemini.api.aop.Advice;
import io.gemini.api.aop.Pointcut;
import io.gemini.core.Ordered;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ObjectUtils;
import io.gemini.core.util.StringUtils;

public interface Advisor {

    String getAdvisorName();

    Advice getAdvice();

    Class<? extends Advice> getAdviceClass();


    /**
     * Return whether this advice is associated with a particular instance
     * (for example, creating a mixin) or shared with all instances of
     * the advised class obtained from the same Spring bean factory.
     * <p><b>Note that this method is not currently used by the framework.</b>
     * Typical Aspect implementations always return {@code true}.
     * Use singleton/prototype bean definitions or appropriate programmatic
     * proxy creation to ensure that Aspects have the correct lifecycle model.
     * @return whether this advice is associated with a particular target instance
     */
    boolean isPerInstance();


    interface PointcutAdvisor extends Advisor, Ordered {

        Pointcut getPointcut();


        abstract class AbstractBase implements PointcutAdvisor {

            @Override
            public boolean isPerInstance() {
                return false;
            }

            @Override
            public String toString() {
                return this.getAdvisorName() + "@" + ObjectUtils.getIdentityHexString(this);
            }
        }

        class Default extends AbstractBase {

            private final String advisorName;

            private final boolean perInstance;

            private final Supplier<Class<? extends Advice>> adviceClassSupplier;
            private final Supplier<? extends Advice> adviceSupplier;
            private Advice advice;

            private final Pointcut pointcut;
            private final int order;

            public Default(String advisorName, 
                    boolean perInstance, 
                    Supplier<Class<? extends Advice>> adviceClassSupplier, 
                    Supplier<? extends Advice> adviceSupplier,
                    Pointcut pointcut,
                    int order) {
                this.advisorName = StringUtils.hasText(advisorName) ? advisorName : super.toString();

                this.perInstance = perInstance;

                Assert.notNull(adviceClassSupplier, "'adviceClassSupplier' must not be null");
                this.adviceClassSupplier = adviceClassSupplier;

                Assert.notNull(adviceSupplier, "'adviceSupplier' must not be null");
                this.adviceSupplier = adviceSupplier;

                Assert.notNull(pointcut, "'pointcut' must not be null");
                this.pointcut = pointcut;

                this.order = order;
            }


            @Override
            public String getAdvisorName() {
                return advisorName;
            }

            @Override
            public boolean isPerInstance() {
                return perInstance;
            }

            @Override
            public Class<? extends Advice> getAdviceClass() {
                return this.adviceClassSupplier.get();
            }

            @Override
            public Advice getAdvice() {
                // for prototype
                if(this.perInstance == true) {
                    return doCreateAdvice();
                }

                // for singleton
                if(this.advice != null)
                    return this.advice;
                else
                    this.advice = this.doCreateAdvice();

                return this.advice;
            }

            private Advice doCreateAdvice() {
                return this.adviceSupplier.get();
            }

            @Override
            public Pointcut getPointcut() {
                return this.pointcut;
            }

            @Override
            public int getOrder() {
                return this.order;
            }

            @Override
            public String toString() {
                return advisorName;
            }
        }
    }
}
