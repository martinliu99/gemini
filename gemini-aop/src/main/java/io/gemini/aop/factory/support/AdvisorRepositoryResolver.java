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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.matcher.AdviceMethodMatcher;
import io.gemini.api.aop.AdvisorSpec;
import net.bytebuddy.description.type.TypeDescription;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorRepositoryResolver {

    AdvisorRepository resolve(FactoryContext factoryContext, AdvisorSpec advisorSpec);


    abstract class AbstractBase implements AdvisorRepositoryResolver {

        protected static final Logger LOGGER = LoggerFactory.getLogger(AdvisorRepositoryResolver.class);

        protected String resolverName = this.getClass().getName();


        @Override
        public AdvisorRepository resolve(FactoryContext factoryContext, AdvisorSpec advisorSpec) {
            if(advisorSpec == null) return null;

            try {
                return doResolve(factoryContext, advisorSpec);
            } catch(Throwable t) {
                LOGGER.warn("Failed to resolve AdvisorSpec '{}' via '{}'.", advisorSpec, resolverName, t);
                return null;
            }
        }

        protected abstract AdvisorRepository doResolve(FactoryContext factoryContext, AdvisorSpec advisorSpec);
    }


    class ForPojoPointcut extends AbstractBase {

        /**
         * {@inheritDoc}
         */
        @Override
        protected AdvisorRepository doResolve(FactoryContext factoryContext, AdvisorSpec advisorSpec) {
            if(advisorSpec == null || advisorSpec instanceof AdvisorSpec.PojoPointcutSpec == false)
                return null;

            AdvisorSpec.PojoPointcutSpec pojoPointcutSpec = (AdvisorSpec.PojoPointcutSpec) advisorSpec;
            TypeDescription adviceType = factoryContext.getTypePool()
                    .describe(advisorSpec.getAdviceClassName())
                    .resolve();

            return new AdvisorRepository.ForPojoPointcut(
                    pojoPointcutSpec, 
                    AdviceMethodMatcher.create(pojoPointcutSpec, adviceType));
        }
    }


    class ForExprPointcut extends AbstractBase {

        /**
         * {@inheritDoc}
         */
        @Override
        protected AdvisorRepository doResolve(FactoryContext factoryContext, AdvisorSpec advisorSpec) {
            if(advisorSpec == null || advisorSpec instanceof AdvisorSpec.ExprPointcutSpec == false)
                return null;

            AdvisorSpec.ExprPointcutSpec exprPointcutSpec = (AdvisorSpec.ExprPointcutSpec) advisorSpec;
            TypeDescription adviceType = factoryContext.getTypePool()
                    .describe(advisorSpec.getAdviceClassName())
                    .resolve();

            return new AdvisorRepository.ForExprPointcut(
                    exprPointcutSpec,
                    AdviceMethodMatcher.create(exprPointcutSpec, adviceType));
        }
    }


    class ForAspectJ extends AbstractBase {

        /**
         * {@inheritDoc}
         */
        @Override
        protected AdvisorRepository doResolve(FactoryContext factoryContext, AdvisorSpec advisorSpec) {
            if(advisorSpec == null || advisorSpec instanceof AspectJAdvisorSpec == false)
                return null;

            AspectJAdvisorSpec aspectJAdvisorSpec = (AspectJAdvisorSpec) advisorSpec;

            return new AdvisorRepository.ForAspectJAdvice(
                    aspectJAdvisorSpec, 
                    AdviceMethodMatcher.create(aspectJAdvisorSpec, aspectJAdvisorSpec.getAspectJMethod(), 
                            aspectJAdvisorSpec.getAdviceReturningParameterType(), aspectJAdvisorSpec.getAdviceThrowingParameterType()));
        }
    }
}
