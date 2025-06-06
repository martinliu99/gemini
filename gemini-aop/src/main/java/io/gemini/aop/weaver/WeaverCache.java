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
package io.gemini.aop.weaver;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Aspect;
import io.gemini.aop.weaver.Joinpoints.Descriptor;
import io.gemini.api.classloader.ThreadContext;
import io.gemini.core.OrderComparator;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import io.gemini.core.util.StringUtils;
import net.bytebuddy.description.method.MethodDescription;

class WeaverCache implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeaverCache.class);


    private final WeaverContext weaverContext;

    private final ConcurrentMap<ClassLoader, ConcurrentMap<String /* typeName */, TypeCache>> classLoaderTypeCache;


    WeaverCache(WeaverContext weaverContext) {
        this.weaverContext = weaverContext;

        this.classLoaderTypeCache = new ConcurrentReferenceHashMap<>();
    }

    public TypeCache getTypeCache(ClassLoader classLoader, String typeName) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

        // ignore excluded ClassLoader
        if(weaverContext.isExcludedClassLoader(ClassLoaderUtils.getClassLoaderName(classLoader)) == true)
            return TypeCache.DUMMY_TYPE_CACHE;

        return this.classLoaderTypeCache
                .computeIfAbsent(
                        cacheKey, 
                        cl -> new ConcurrentHashMap<>()
                )
                .computeIfAbsent( 
                        typeName, 
                        name -> new TypeCache(name)
                );
    }

    public void removeTypeCache(ClassLoader classLoader, String typeName) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);
        ConcurrentMap<String /* typeName */, TypeCache> typeCaches = this.classLoaderTypeCache.get(cacheKey);
        if(typeCaches == null)
            return;

        TypeCache typeCache = typeCaches.remove(typeName);
        if(typeCache == null)
            return;

        typeCache.clear();
    }

    public Joinpoints.Descriptor getJoinpointDescriptor(Lookup lookup, String methodSignature) {
        Class<?> thisClass = lookup.lookupClass();
        TypeCache typeCache = getTypeCache(thisClass.getClassLoader(), thisClass.getName());

        return typeCache == null
                ? null
                : typeCache.getJoinpointDescriptor(lookup, methodSignature, thisClass);
    }


    @Override
    public void close() throws IOException {
        for(ConcurrentMap<String /* typeName */, TypeCache> typeCaches : this.classLoaderTypeCache.values()) {
            for(TypeCache typeCache : typeCaches.values())
                typeCache.clear();
        }

        this.classLoaderTypeCache.clear();
    }


    public static class TypeCache {

        protected final static TypeCache DUMMY_TYPE_CACHE = new TypeCache("");

        private final String typeName;

        // cache matching result per ClassLoader and Type in local storage for future transformation
        private Map<String /* methodSignature */, MethodDescription> methodSignatureMap;
        private Map<String /* methodSignature */, List<? extends Aspect>> methodSignatureAspectsMap;

        // cache transformation result per ClassLoader and Type in local storage
        private AtomicBoolean transformed;

        // cache joinpoint descriptors
        private Map<String /* methodSignature */, Joinpoints.Descriptor> joinpointDescriptors;


        TypeCache(String typeName) {
            this.typeName = typeName;

            this.methodSignatureMap = Collections.emptyMap();
            this.methodSignatureAspectsMap = Collections.emptyMap();

            this.transformed = new AtomicBoolean(false);

            this.joinpointDescriptors = Collections.emptyMap();
        }


        public void setMethodDescriptionAspects(Map<? extends MethodDescription, List<? extends Aspect>> methodDescriptionAspects) {
            Map<String /* methodSignature */, MethodDescription> methodSignatureMap = new HashMap<>(methodDescriptionAspects.size());
            Map<String /* methodSignature */, List<? extends Aspect>> methodSignatureAspectMap = new HashMap<>(methodDescriptionAspects.size());
            for(Entry<? extends MethodDescription, List<? extends Aspect>> e : methodDescriptionAspects.entrySet()) {
                String methodSignature = e.getKey().toGenericString();

                methodSignatureMap.put(methodSignature, e.getKey());
                methodSignatureAspectMap.put(methodSignature, e.getValue());
            }
            this.methodSignatureMap = methodSignatureMap;
            this.methodSignatureAspectsMap = methodSignatureAspectMap;

            this.joinpointDescriptors = new ConcurrentHashMap<>(this.methodSignatureAspectsMap.size());
        }


        public String getTypeName() {
            return typeName;
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


        protected Joinpoints.Descriptor getJoinpointDescriptor(Lookup lookup, String methodSignature, Class<?> thisClass) {
            return joinpointDescriptors.computeIfAbsent(
                    methodSignature, 
                    signature -> createJoinpointDescriptor(lookup, signature, thisClass)
            );
        }

        private Joinpoints.Descriptor createJoinpointDescriptor(Lookup lookup, String methodSignature, Class<?> thisClass) {
            ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();
            ClassLoader joinpointClassLoader = thisClass.getClassLoader();
            try {
                ThreadContext.setContextClassLoader(joinpointClassLoader);  // set joinpointClassLoader

                List<? extends Aspect> aspectChain = processAspects( this.methodSignatureAspectsMap.get(methodSignature) );

                Descriptor joinpointDescriptor = CollectionUtils.isEmpty(aspectChain)
                        ? null
                        : this.createJoinpointDescriptor(lookup, methodSignature, thisClass, this.methodSignatureMap.get(methodSignature), aspectChain);

                LOGGER.info("Created joinpoint descriptor for type '{}' loaded by ClassLoader '{}'. \n  {} \n{} \n", 
                        typeName, lookup.lookupClass().getClassLoader(),
                        methodSignature,
                        StringUtils.join(aspectChain, a -> "    " + a.getAspectName(), "\n")
                );

                return joinpointDescriptor;
            } catch(Throwable t) {
                LOGGER.warn("Failed to create joinpoint descriptor for type '{}' loaded by ClassLoader '{}'. \n  Method: {}", 
                        typeName, joinpointClassLoader, methodSignature, t);
                return null;
            } finally {
                ThreadContext.setContextClassLoader(existingClassLoader);
            }
        }

        private List<? extends Aspect> processAspects(List<? extends Aspect> candidates) {
            // remove null, or duplicate advice classes
            Set<Class<?>> adviceClasses = new HashSet<>();
            List<Aspect> aspects = candidates.stream()
            .filter( aspect -> 
                    aspect.getAdviceClass() != null 
                    && adviceClasses.add(aspect.getAdviceClass()) 
                    && (aspect.isPerInstance() || aspect.getAdvice() != null)
            )
            .collect( Collectors.toList() );

            // sort aspect
            OrderComparator.sort(aspects);

            return aspects;
        }

        private Joinpoints.Descriptor createJoinpointDescriptor(Lookup lookup, 
                String methodSignature, Class<?> thisClass, MethodDescription methodDescription,
                List<? extends Aspect> aspectChain) throws ClassNotFoundException, NoSuchMethodException, SecurityException {
            if(methodDescription.isTypeInitializer()) {
                return new Joinpoints.Descriptor(lookup, methodSignature, null, aspectChain);
            }

            AccessibleObject accessibleObject = ClassUtils.getAccessibleObject(thisClass, methodDescription);
            String accessibleName = methodSignature;
            return new Joinpoints.Descriptor(lookup, accessibleName, accessibleObject, aspectChain);
        }

        void clear() {
            this.methodSignatureMap.clear();
            this.methodSignatureAspectsMap.clear();
            this.joinpointDescriptors.clear();
        }
    }
}
