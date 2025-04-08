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
package io.gemini.aop.aspectapp;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import io.gemini.aop.AopContext;
import io.gemini.aop.classloader.AspectClassLoader;
import io.gemini.aspectj.weaver.world.TypeWorld;
import io.gemini.aspectj.weaver.world.TypeWorldFactory;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.object.Closeable;
import io.gemini.core.object.ObjectFactory;
import io.gemini.core.pool.AspectTypePool;
import io.gemini.core.util.Assert;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.pool.TypePool.CacheProvider;
import net.bytebuddy.utility.JavaModule;

/**
 * 
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class AspectEnvironment {

    private final AspectContext aspectContext;

    private final String joinpointClassLoaderName;
    private final JavaModule javaModule;

    private final ClassLoader classLoader;
    private final ObjectFactory objectFactory;
    private final TypePool typePool;
    private final TypeWorld typeWorld;

    private final PlaceholderHelper placeholderHelper;

    private final ElementMatcher<String> defaultClassLoaderMatcher;

    private final boolean validateEnvironment;

    private final boolean asmAutoCompute;


    protected AspectEnvironment(AspectContext aspectContext, 
            String joinpointClassLoaderName, JavaModule javaModule,
            ClassLoader classLoader, ObjectFactory objectFactory, TypePool typePool, TypeWorld typeWorld,
            ElementMatcher<String> defaultClassLoaderMatcher,
            boolean validateEnvironment) {
        this.aspectContext = aspectContext;

        this.joinpointClassLoaderName = joinpointClassLoaderName;
        this.javaModule = javaModule;

        this.classLoader = classLoader;
        this.objectFactory = objectFactory;
        this.typePool = typePool;
        this.typeWorld = typeWorld;

        this.placeholderHelper = this.aspectContext.getPlaceholderHelper();

        this.defaultClassLoaderMatcher = defaultClassLoaderMatcher;

        this.validateEnvironment = validateEnvironment;

        this.asmAutoCompute = aspectContext.getAopContext().isASMAutoCompute();
    }


    public String getJoinpointClassLoaderName() {
        return joinpointClassLoaderName;
    }

    public JavaModule getJavaModule() {
        return javaModule;
    }


    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public TypePool getTypePool() {
        return typePool;
    }

    public TypeWorld getTypeWorld() {
        return typeWorld;
    }


    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }


    public ElementMatcher<String> getDefaultClassLoaderMatcher() {
        return defaultClassLoaderMatcher;
    }

    public boolean isValidateEnvironment() {
        return validateEnvironment;
    }

    public boolean isASMAutoCompute() {
        return asmAutoCompute;
    }


    public static class Factory implements Closeable {

        private static final Set<ClassLoader> SYSTEM_CLASSLOADERS;

        private final TypeWorldFactory typeWorldFactory = new TypeWorldFactory.Prototype();

        private final AopContext aopContext;
        private final AspectContext aspectContext;

        private final ConcurrentMap<ClassLoader, AspectEnvironment> aspectEnvironmentMap;


        static {
            SYSTEM_CLASSLOADERS = new HashSet<>();
            SYSTEM_CLASSLOADERS.add(ClassLoaderUtils.BOOTSTRAP_CLASSLOADER);
            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
            while (classLoader != null) {
                SYSTEM_CLASSLOADERS.add(classLoader);
                classLoader = classLoader.getParent();
            }
        }

        public Factory(AspectContext aspectContext) {
            Assert.notNull(aspectContext, "'aspectContext' must not be null.");
            this.aspectContext = aspectContext;
            this.aopContext = aspectContext.getAopContext();

            this.aspectEnvironmentMap = new ConcurrentReferenceHashMap<>();
        }


        public AspectEnvironment createAspectEnvironment(ClassLoader joinpointClassLoader, JavaModule javaModule) {
            return createAspectEnvironment(joinpointClassLoader, javaModule, false);
        }

        public AspectEnvironment createAspectEnvironment(ClassLoader joinpointClassLoader, JavaModule javaModule, boolean validateEnvironment) {
            ClassLoader cacheKey = ClassLoaderUtils.maskNull(joinpointClassLoader);

            boolean sharedMode = useSharedAspectClassLoader(cacheKey);
            if(sharedMode == true) {
                if(this.aspectEnvironmentMap.containsKey(cacheKey) == false) {
                    this.aspectEnvironmentMap.computeIfAbsent(
                            cacheKey, 
                            key -> doCreateAspectFactoryContext(joinpointClassLoader, javaModule, sharedMode, validateEnvironment)
                    );
                }
            } else {
                AspectEnvironment aspectEnvironment = doCreateAspectFactoryContext(joinpointClassLoader, javaModule, sharedMode, validateEnvironment);
                // memory leak?
                this.aspectEnvironmentMap.put(cacheKey, aspectEnvironment);  // overwrite existing AspectEnvironment
            }

            return this.aspectEnvironmentMap.get(cacheKey);
        }

        private boolean useSharedAspectClassLoader(ClassLoader joinpointClassLoader) {
            // 1.use existing AspectClassLoader
            if(aspectEnvironmentMap.containsKey(joinpointClassLoader) == true)
                return true;


            // 2.used shared AspectClassLoader for system ClassLoaders
            if(SYSTEM_CLASSLOADERS.contains(joinpointClassLoader) == true)
                return true;


            // 3.check shareAspectClassLoader flag
            if(aspectContext.isShareAspectClassLoader() == false) 
                return false;


            // 4.check potentially class loading conflict
            // exist ClassLoader is same instance of the joinpointClassLoader
            Class<? extends ClassLoader> classLoaderClass = joinpointClassLoader.getClass();
            for(ClassLoader existingCL : aspectEnvironmentMap.keySet()) {
                if(existingCL.getClass() == classLoaderClass)
                    return false;
            }

            // exist ClassLoader might conflict with the joinpintClassLoader 
            String joinpointCLClassName = ClassLoaderUtils.getClassLoaderName(joinpointClassLoader);
            List<Set<String>> conflictJoinpointClassLoaders = aspectContext.getConflictJoinpointClassLoaders().stream()
                    .filter( classLoaders -> classLoaders.contains(joinpointCLClassName) )
                    .collect( Collectors.toList() );

            for(ClassLoader existingCL : aspectEnvironmentMap.keySet()) {
                String existingCLClassName = ClassLoaderUtils.getClassLoaderName(existingCL);

                for(Set<String> classLoaders : conflictJoinpointClassLoaders) {
                    if(classLoaders.contains(existingCLClassName))
                        return false;
                }
            }


            // 5.no conflict, used shared AspectClassLoader
            return true;
        }


        protected AspectEnvironment doCreateAspectFactoryContext(ClassLoader joinpointClassLoader, JavaModule javaModule, boolean sharedMode, 
                boolean validateEnvironment) {
            // create AspectClassLoader & objectFactory per ClassLoader
            AspectClassLoader aspectClassLoader = this.aspectContext.getAspectClassLoader();
            ObjectFactory objectFactory = this.aspectContext.getObjectFactory();
            if(sharedMode == false) {
                aspectClassLoader = new AspectClassLoader.WithJoinpointCL(
                        this.aspectContext.getAppName(), 
                        this.aspectContext.getAppResources().getResourceUrls(), 
                        aopContext.getAgentClassLoader(), 
                        joinpointClassLoader);

                aspectClassLoader.setJoinpointTypeMatcher(aspectContext.getJoinpointTypeMatcher());
                aspectClassLoader.setJoinpointResourceMatcher(aspectContext.getJoinpointResourceMatcher());

                objectFactory = this.aspectContext.createObjectFactory(this.aspectContext.getAppResources(), 
                        aspectClassLoader, this.aspectContext.getClassScanner());
            }

            // create typePool per ClassLoader
            AspectTypePool typePool = new AspectTypePool(
                    new CacheProvider.Simple(),
                    this.aspectContext.getAspectTypePool(),
                    this.aopContext.getTypePoolFactory().getExplicitTypePool(joinpointClassLoader)
            );
            typePool.setJoinpointTypeMatcher(aspectContext.getJoinpointTypeMatcher());

            TypeWorld typeWorld = typeWorldFactory.createTypeWorld(
                    joinpointClassLoader, javaModule,
                    typePool, aspectContext.getPlaceholderHelper());

            return new AspectEnvironment(this.aspectContext,
                    ClassLoaderUtils.getClassLoaderName(joinpointClassLoader), javaModule,
                    aspectClassLoader, objectFactory, typePool, typeWorld,
                    this.aspectContext.getDefaultClassLoaderMatcher(),
                    validateEnvironment);
        }


        /* @see io.gemini.core.object.Closeable#close() 
         */
        @Override
        public void close() {
            this.aspectEnvironmentMap.values()
            .forEach( aspectEnvironment-> {
                 aspectEnvironment.getObjectFactory().close();
                 aspectEnvironment.getTypePool().clear();
            });
        }
    }
}