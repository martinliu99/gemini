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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.factory.AdvisorContext;
import io.gemini.aop.factory.FactoryContext;
import io.gemini.aop.matcher.AdviceMethodMatcher;
import io.gemini.api.aop.AdvisorSpec;
import io.gemini.api.aop.Pointcut;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.type.TypeDescription;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public interface AdvisorRepositoryResolver {

    static final Logger LOGGER = LoggerFactory.getLogger(AdvisorRepositoryResolver.class);


    boolean supports(AdvisorSpec advisorSpec);

    AdvisorRepository resolve(FactoryContext factoryContext, AdvisorSpec advisorSpec);


    static List<AdvisorRepository> resolveRepository(FactoryContext factoryContext, AdvisorContext validationContext, AdvisorSpec advisorSpec) {
        List<AdvisorRepositoryResolver> advisorRepositoryResolvers = factoryContext.getAdvisorRepositoryResolvers();

        List<AdvisorRepository> advisorRepositories = new ArrayList<>(advisorRepositoryResolvers.size());
        for(AdvisorRepositoryResolver advisorRepositoryResolver : advisorRepositoryResolvers) {
            if(advisorRepositoryResolver.supports(advisorSpec) == false)
                continue;

            AdvisorRepository advisorRepository  = advisorRepositoryResolver.resolve(factoryContext, advisorSpec);
            if(advisorRepository == null) 
                continue;

            // validate advisor creation
            try {
                advisorRepository.create(validationContext);
            } catch(Exception e) {
                continue;
            }

            advisorRepositories.add(advisorRepository);
        }

        return advisorRepositories;
    }


    abstract class AbstractBase<S extends AdvisorSpec> implements AdvisorRepositoryResolver {

        protected String resolverName = this.getClass().getName();

        @SuppressWarnings("unchecked")
        @Override
        public AdvisorRepository resolve(FactoryContext factoryContext, AdvisorSpec advisorSpec) {
            if(advisorSpec == null || verify(advisorSpec) == false) 
                return null;

            try {
                return doResolve(factoryContext, (S) advisorSpec);
            } catch(Throwable t) {
                LOGGER.warn("Failed to resolve AdvisorSpec '{}' via '{}'.", advisorSpec, resolverName, t);
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private boolean verify(AdvisorSpec advisorSpec) {
            if(advisorSpec == null || supports(advisorSpec) == false)
                return false;

            // check advice definition
            if(StringUtils.hasText(advisorSpec.getAdviceClassName()) == false) {
                LOGGER.warn("Ignored AdvisorSpec with empty adviceClassName. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return doVerify( (S) advisorSpec);
        }

        protected abstract String getSpecType();

        protected abstract boolean doVerify(S advisorSpec);

        protected abstract AdvisorRepository doResolve(FactoryContext factoryContext, S advisorSpec);
    }


    class ForPojoPointcut extends AbstractBase<AdvisorSpec.PojoPointcutSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean supports(AdvisorSpec advisorSpec) {
            return advisorSpec != null && advisorSpec instanceof AdvisorSpec.PojoPointcutSpec;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AdvisorSpec.PojoPointcutSpec.class.getSimpleName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doVerify(AdvisorSpec.PojoPointcutSpec advisorSpec) {
            Pointcut pointcut = advisorSpec.getPointcut();
            if(pointcut == null ||
                    (pointcut.getTypeMatcher() == null && pointcut.getMethodMatcher() == null)) {
                LOGGER.warn("Ignored AdvisorSpec with null pointuct. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected AdvisorRepository doResolve(FactoryContext factoryContext, AdvisorSpec.PojoPointcutSpec advisorSpec) {
            TypeDescription adviceType = factoryContext.getTypePool()
                    .describe(advisorSpec.getAdviceClassName())
                    .resolve();

            return new AdvisorRepository.ForPojoPointcut(
                    advisorSpec, 
                    AdviceMethodMatcher.Parser.parse(advisorSpec, adviceType));
        }
    }


    class ForExprPointcut extends AbstractBase<AdvisorSpec.ExprPointcutSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean supports(AdvisorSpec advisorSpec) {
            return advisorSpec != null && advisorSpec instanceof AdvisorSpec.ExprPointcutSpec;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AdvisorSpec.ExprPointcutSpec.class.getSimpleName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doVerify(AdvisorSpec.ExprPointcutSpec advisorSpec) {
            if(StringUtils.hasText(advisorSpec.getPointcutExpression()) == false) {
                LOGGER.warn("Ignored AdvisorSpec with empty pointcutExpression. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected AdvisorRepository doResolve(FactoryContext factoryContext, AdvisorSpec.ExprPointcutSpec advisorSpec) {
            TypeDescription adviceType = factoryContext.getTypePool()
                    .describe(advisorSpec.getAdviceClassName())
                    .resolve();

            return new AdvisorRepository.ForExprPointcut(
                    advisorSpec,
                    AdviceMethodMatcher.Parser.parse(advisorSpec, adviceType));
        }
    }


    class ForAspectJ extends AbstractBase<AspectJAdvisorSpec> {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean supports(AdvisorSpec advisorSpec) {
            return advisorSpec != null && advisorSpec instanceof AspectJAdvisorSpec;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String getSpecType() {
            return AspectJAdvisorSpec.class.getSimpleName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean doVerify(AspectJAdvisorSpec advisorSpec) {
            if(advisorSpec.getAspectJType() == null) {
                LOGGER.warn("Ignored AdvisorSpec with empty aspectJClassName. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            if(StringUtils.hasText(advisorSpec.getPointcutExpression()) == false) {
                LOGGER.warn("Ignored AdvisorSpec with empty pointcutExpression. \n  {}: {} \n", 
                        getSpecType(), advisorSpec.getAdvisorName() );
                return false;
            }

            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected AdvisorRepository doResolve(FactoryContext factoryContext, AspectJAdvisorSpec advisorSpec) {
            return new AdvisorRepository.ForAspectJAdvice(
                    advisorSpec, 
                    AdviceMethodMatcher.Parser.parse(advisorSpec, advisorSpec.getAspectJMethod(), 
                            advisorSpec.getAdviceReturningParameterType(), advisorSpec.getAdviceThrowingParameterType()));
        }
    }
}
