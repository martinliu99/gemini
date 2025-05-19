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
import java.util.Iterator;
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

import io.gemini.aop.AopContext;
import io.gemini.aop.Aspect;
import io.gemini.aop.weaver.Joinpoints.Descriptor;
import io.gemini.api.classloader.ThreadContext;
import io.gemini.core.OrderComparator;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.ClassUtils;
import io.gemini.core.util.CollectionUtils;
import net.bytebuddy.description.method.MethodDescription;

class WeaverCache implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeaverCache.class);

    private final AopContext aopContext;
    private final WeaverContext weaverContext;

    private final ConcurrentMap<ClassLoader, ConcurrentMap<String /* typeName */, TypeCache>> classLoaderTypeCache;


    WeaverCache(AopContext aopContext, WeaverContext weaverContext) {
        this.aopContext = aopContext;
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
        ClassLoader existingClassLoader = ThreadContext.getContextClassLoader();

        Class<?> thisClass = lookup.lookupClass();
        String typeName = thisClass.getName();
        ClassLoader joinpointClassLoader = thisClass.getClassLoader();
        try {
            ThreadContext.setContextClassLoader(joinpointClassLoader);  // set joinpointClassLoader

            if(aopContext.isDiagnosticClass(typeName)) {
                LOGGER.info("^Creating joinpoint descriptor for type '{}' loaded by ClassLoader '{}'. \n  {}", 
                        typeName, joinpointClassLoader, methodSignature);
            }

            return getTypeCache(lookup.lookupClass().getClassLoader(), lookup.lookupClass().getName())
                    .getJoinpointDescriptor(lookup, methodSignature);
        } catch(Throwable t) {
            LOGGER.warn("Failed to create joinpoint descriptor for type '{}' loaded by ClassLoader '{}'. \n  {}", 
                    typeName, joinpointClassLoader, methodSignature, t);
            return null;
        } finally {
            ThreadContext.setContextClassLoader(existingClassLoader);
        }
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


        Joinpoints.Descriptor getJoinpointDescriptor(Lookup lookup, String methodSignature) {
            // create or fetch from cache
            if(this.joinpointDescriptors.size() == 0 && this.methodSignatureAspectsMap.size() > 0) {
                initializeJoinpointDescriptors(lookup);
            }

            return joinpointDescriptors.get(methodSignature);
        }


        protected void initializeJoinpointDescriptors(Lookup lookup) {
            ConcurrentMap<String /* methodSignature */, Joinpoints.Descriptor> joinpointDescriptors = new ConcurrentHashMap<>(this.methodSignatureAspectsMap.size());
            for(Iterator<Entry<String, MethodDescription>> iterator = this.methodSignatureMap.entrySet().iterator(); iterator.hasNext(); ) {
                Entry<String, MethodDescription> entry = iterator.next();
                String methodSignature = entry.getKey();

                List<? extends Aspect> aspectChain = processAspects( this.methodSignatureAspectsMap.get(methodSignature) );
                if(CollectionUtils.isEmpty(aspectChain) == true) {
                    iterator.remove();
                    this.methodSignatureAspectsMap.remove(methodSignature);
                };

                Descriptor joinpointDescriptor = this.createJoinpointDescriptor(lookup, methodSignature, entry.getValue(), aspectChain);
                if(joinpointDescriptor == null) {
                    iterator.remove();
                    this.methodSignatureAspectsMap.remove(methodSignature);
                };

                joinpointDescriptors.put(methodSignature, joinpointDescriptor);
            }

            this.joinpointDescriptors = joinpointDescriptors;
            LOGGER.info("Matched type '{}' loaded by ClassLoader '{}' with below methods and advices. \n{}\n", 
                    typeName, lookup.lookupClass().getClassLoader(),
                    this.methodSignatureAspectsMap.entrySet().stream()
                        .map( 
                            e -> "  " + e.getKey() + "\n" 
                                + e.getValue().stream().map( a -> "    " + a.getAspectName() ).collect( Collectors.joining("\n")) )
                        .collect( Collectors.joining("\n") ) 
            );
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
                String methodSignature, MethodDescription methodDescription,
                List<? extends Aspect> aspectChain) {
            if(methodDescription.isTypeInitializer()) {
                return new Joinpoints.Descriptor(lookup, methodSignature, null, aspectChain);
            }

            try {
                Class<?> thisClass = lookup.lookupClass();
                AccessibleObject accessibleObject = ClassUtils.getAccessibleObject(thisClass, methodDescription);

                String accessibleName = thisClass.getName() 
                        + "."
                        + ( methodDescription.isTypeInitializer() ? MethodDescription.TYPE_INITIALIZER_INTERNAL_NAME
                                : methodDescription.isConstructor() 
                                ? MethodDescription.CONSTRUCTOR_INTERNAL_NAME : methodDescription.getName() )
                        + "(..)";

                return new Joinpoints.Descriptor(lookup, accessibleName, accessibleObject, aspectChain);
            } catch (Exception e) {
                LOGGER.warn("Failed to create Joinpoint.Descriptor for '{}'.", methodSignature, e);
                return null;
            }
        }

        void clear() {
            this.methodSignatureMap.clear();
            this.methodSignatureAspectsMap.clear();
            this.joinpointDescriptors.clear();
        }
    }
}
