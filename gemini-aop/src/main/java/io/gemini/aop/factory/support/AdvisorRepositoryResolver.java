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
import io.gemini.aop.factory.support.AdviceMethodSpec.AspectJMethodSpec;
import io.gemini.aop.factory.support.AspectJSpecs.AspectJAdvisorSpec;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.core.util.MethodUtils;

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

            return new AdvisorRepository.ForPojoPointcut( 
                    (AdvisorSpec.PojoPointcutSpec) advisorSpec);
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

            return new AdvisorRepository.ForExprPointcut(
                    (AdvisorSpec.ExprPointcutSpec) advisorSpec);
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
            AspectJMethodSpec aspectJMethodSpec;
            try {
                aspectJMethodSpec= new AspectJMethodSpec(aspectJAdvisorSpec);
                if(aspectJMethodSpec.isValid() == false)
                    return null;
            } catch(Throwable t) {
                LOGGER.warn("Failed to parse AspectJ advice method. \n  AdvisorSpec: {} \n  AdviceMethod: {} \n", 
                        advisorSpec.getAdvisorName(), MethodUtils.getMethodSignature(aspectJAdvisorSpec.getAspectJMethod()), t);
                return null;
            }

            return new AdvisorRepository.ForAspectJAdvice(aspectJAdvisorSpec, aspectJMethodSpec);
        }
    }
}
