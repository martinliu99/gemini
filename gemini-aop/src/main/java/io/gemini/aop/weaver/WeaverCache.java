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
package io.gemini.aop.weaver;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Advisor;
import io.gemini.aop.AopContext;
import io.gemini.core.OrderComparator;
import io.gemini.core.classloader.ThreadContext;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;
import io.gemini.core.util.Throwables;
import net.bytebuddy.description.method.MethodDescription;

class WeaverCache implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeaverCache.class);


    private final WeaverContext weaverContext;
    private final ConcurrentMap<ClassLoader, ConcurrentMap<String /* typeName */, TargetTypeCache>> classLoaderTypeCache;


    WeaverCache(WeaverContext weaverContext) {
        this.weaverContext = weaverContext;
        this.classLoaderTypeCache = new ConcurrentReferenceHashMap<>();
    }

    public TargetTypeCache createTargetTypeCache(ClassLoader targetClassLoader, 
            String targetTypeName, boolean loaded,
            Map<? extends MethodDescription, List<? extends Advisor>> targetMethodAdvisors) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(targetClassLoader);
        return this.classLoaderTypeCache
                .computeIfAbsent(
                        cacheKey, 
                        key -> new ConcurrentHashMap<>()
                )
                .computeIfAbsent( 
                        targetTypeName, 
                        key -> weaverContext.getAopContext().getDiagnosticLevel().isSimpleEnabled() == false
                                ? new TargetTypeCache(weaverContext.getAopContext(), targetTypeName, loaded, targetMethodAdvisors)
                                : new TargetTypeCache.Diagnostic(weaverContext.getAopContext(), targetTypeName, loaded, targetMethodAdvisors)
                );
    }

    public TargetTypeCache getTargetTypeCache(ClassLoader targetClassLoader, String targetTypeName) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(targetClassLoader);
        ConcurrentMap<String /* typeName */, TargetTypeCache> targetTypeCaches = this.classLoaderTypeCache.get(cacheKey);
        if (targetTypeCaches == null)
            return null;

        return targetTypeCaches.get(targetTypeName);
    }


    public Joinpoints.Descriptor getJoinpointDescriptor(Lookup targetLookup, String targetMethodSignature) {
        Class<?> targetClass = targetLookup.lookupClass();
        TargetTypeCache targetTypeCache = getTargetTypeCache(targetClass.getClassLoader(), targetClass.getName());

        return targetTypeCache == null
                ? null
                : targetTypeCache.getJoinpointDescriptor(targetLookup, targetMethodSignature, targetClass);
    }


    @Override
    public void close() throws IOException {
        for (ConcurrentMap<String /* typeName */, TargetTypeCache> targetTypeCaches : this.classLoaderTypeCache.values()) {
            for (TargetTypeCache targetTypeCache : targetTypeCaches.values())
                targetTypeCache.clear();
        }

        this.classLoaderTypeCache.clear();
    }


    static class TargetTypeCache {

        private final AopContext aopContext;

        private final String typeName;
        private final boolean loaded;

        // cache transformation result per ClassLoader and Type in local storage
        private AtomicBoolean transformed;

        // cache matching result per ClassLoader and Type in local storage for future transformation
        private Map<String /* methodSignature */, MethodDescription> methodSignatureMap;
        private Map<String /* methodSignature */, List<? extends Advisor>> methodSignatureAdvisorsMap;

        // cache joinpoint descriptors
        private Map<String /* methodSignature */, Joinpoints.Descriptor> joinpointDescriptors;


        public TargetTypeCache(AopContext aopContext, String typeName, boolean loaded,
                Map<? extends MethodDescription, List<? extends Advisor>> targetMethodAdvisors) {
            this.aopContext = aopContext;

            this.typeName = typeName;
            this.loaded = loaded;

            this.transformed = new AtomicBoolean(false);

            this.setMethodDescriptionAdvisors(targetMethodAdvisors);
        }


        private void setMethodDescriptionAdvisors(
                Map<? extends MethodDescription, List<? extends Advisor>> targetMethodAdvisors) {
            Map<String /* methodSignature */, MethodDescription> methodSignatureMap = new LinkedHashMap<>(targetMethodAdvisors.size());
            Map<String /* methodSignature */, List<? extends Advisor>> methodSignatureAdvisorMap = new LinkedHashMap<>(targetMethodAdvisors.size());
            for (Entry<? extends MethodDescription, List<? extends Advisor>> e : targetMethodAdvisors.entrySet()) {
                String methodSignature = e.getKey().toGenericString();

                methodSignatureMap.put(methodSignature, e.getKey());
                methodSignatureAdvisorMap.put(methodSignature, e.getValue());
            }
            this.methodSignatureMap = methodSignatureMap;
            this.methodSignatureAdvisorsMap = methodSignatureAdvisorMap;

            this.joinpointDescriptors = new ConcurrentHashMap<>(this.methodSignatureAdvisorsMap.size());
        }


        protected AopContext getAopContext() {
            return aopContext;
        }


        public String getTypeName() {
            return typeName;
        }

        public boolean isLoaded() {
            return loaded;
        }


        public Map<String, MethodDescription> getMethodSignatureMap() {
            return Collections.unmodifiableMap( methodSignatureMap );
        }

        public boolean isMatched() {
            return methodSignatureMap.size() > 0;
        }

        public boolean isTransformed() {
            return transformed.get();
        }

        public boolean setTransformed(boolean transformed) {
            return this.transformed.getAndSet(transformed);
        }


        protected Joinpoints.Descriptor getJoinpointDescriptor(Lookup targetLookup, 
                String targetMethodSignature, Class<?> targetClass) {
            return joinpointDescriptors.computeIfAbsent(
                    targetMethodSignature, 
                    signature -> doCreateJoinpointDescriptor(targetLookup, signature, targetClass)
            );
        }

        protected Joinpoints.Descriptor doCreateJoinpointDescriptor(Lookup targetLookup, 
                String targetMethodSignature, Class<?> targetClass) {
            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
            ClassLoader targetClassLoader = targetClass.getClassLoader();
            try {
                ThreadContext.setContextClassLoader(targetClassLoader);  // set targetClassLoader

                List<? extends Advisor> advisorChain = processAdvisors( 
                        targetClassLoader, targetMethodSignature,
                        this.methodSignatureAdvisorsMap.get(targetMethodSignature) );

                return CollectionUtils.isEmpty(advisorChain)
                        ? null
                        : this.createJoinpointDescriptor(
                                targetLookup, targetMethodSignature, targetClass, 
                                this.methodSignatureMap.get(targetMethodSignature), advisorChain);
            } catch (Throwable t) {
                if (LOGGER.isWarnEnabled())
                    LOGGER.warn("Could not create joinpoint descriptor for type '{}' loaded by ClassLoader '{}'. \n"
                            + "  Method: {}", 
                            typeName, targetClassLoader, 
                            targetMethodSignature, t
                    );

                Throwables.throwIfRequired(t);
                return null;
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }

        private List<? extends Advisor> processAdvisors(ClassLoader targetClassLoader, 
                String targetMethodSignature, List<? extends Advisor> candidates) {
            // remove null, or duplicate advice classes
            Set<Class<?>> adviceClasses = new LinkedHashSet<>();
            List<String> ignoredAdvisors = new ArrayList<>();
            List<Advisor> advisors = new ArrayList<>(candidates.size());
            for (Advisor advisor : candidates) {
                Class<?> adviceClass = advisor.getAdviceClass();
                if (adviceClass == null)
                    continue;

                if (adviceClasses.add(adviceClass) == false) {
                    ignoredAdvisors.add(advisor.getAdvisorName());
                    continue;
                }

                if (advisor.isPerInstance() == false && advisor.getAdvice() == null)
                    continue;

                advisors.add(advisor);
            }

            if (LOGGER.isWarnEnabled() && ignoredAdvisors.size() > 0)
                LOGGER.warn("Removed duplicate Advice for type '{}', \n"
                        + "  ClassLoader: {} \n"
                        + "  Method: {} \n"
                        + "  RemovedAdvices: \n"
                        + "    {} \n", 
                        typeName, 
                        targetClassLoader,
                        targetMethodSignature,
                        StringUtils.join(ignoredAdvisors, "\n    ")
                );

            // sort advisor
            OrderComparator.sort(advisors);

            return advisors;
        }

        private Joinpoints.Descriptor createJoinpointDescriptor(Lookup lookup, 
                String targetMethodSignature, Class<?> targetClass, MethodDescription targetMethod,
                List<? extends Advisor> advisorChain) throws ClassNotFoundException, NoSuchMethodException, SecurityException {
            if (targetMethod.isTypeInitializer()) {
                return new Joinpoints.Descriptor(lookup, targetMethodSignature, null, advisorChain);
            }

            AccessibleObject accessibleObject = ClassUtils.getAccessibleObject(targetClass, targetMethod);
            String accessibleName = targetMethodSignature;
            return new Joinpoints.Descriptor(lookup, accessibleName, accessibleObject, advisorChain);
        }

        void clear() {
            this.methodSignatureMap.clear();
            this.methodSignatureAdvisorsMap.clear();
            this.joinpointDescriptors.clear();
        }


        @Override
        public String toString() {
            return typeName;
        }


        static class Diagnostic extends TargetTypeCache {

            private static final Logger LOGGER = LoggerFactory.getLogger(Diagnostic.class);


            public Diagnostic(AopContext aopContext, String targetTypeName, boolean loaded,
                    Map<? extends MethodDescription, List<? extends Advisor>> targetMethodAdvisors) {
                super(aopContext, targetTypeName, loaded, targetMethodAdvisors);
            }


            @Override
            protected Joinpoints.Descriptor doCreateJoinpointDescriptor(Lookup lookup, 
                    String targetMethodSignature, Class<?> targetClass) {
                Joinpoints.Descriptor descriptor = super.doCreateJoinpointDescriptor(lookup, targetMethodSignature, targetClass);

                if (LOGGER.isInfoEnabled() && getAopContext().isDiagnosticType(getTypeName()) )
                    LOGGER.info("Created joinpoint descriptor for type '{}', \n"
                            + "  ClassLoader: {} \n"
                            + "  Method: {} \n"
                            + "  Advices: \n"
                            + "    {} \n", 
                            getTypeName(), 
                            targetClass.getClassLoader(),
                            targetMethodSignature,
                            StringUtils.join(descriptor.getAdvisorChain(), Advisor::getAdvisorName, "\n    ")
                    );

                return descriptor;
            }
        }
    }
}
