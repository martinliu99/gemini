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
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.gemini.aop.Aspect;
import io.gemini.core.concurrent.ConcurrentReferenceHashMap;
import io.gemini.core.util.ClassLoaderUtils;
import io.gemini.core.util.ClassUtils;
import net.bytebuddy.description.method.MethodDescription;

class WeaverCache implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeaverCache.class);

    private final WeaverContext weaverContext;

    private final ConcurrentMap<ClassLoader, ConcurrentMap<String /* typeName */, TypeCache>> classLoaderTypeCache;

    private final ConcurrentMap<Class<?>, ConcurrentMap<String /* methodSignature */, Joinpoints.Descriptor>> typeJoinpointDescriptorCache;


    WeaverCache(WeaverContext weaverContext) {
        this.weaverContext = weaverContext;

        classLoaderTypeCache = new ConcurrentReferenceHashMap<>();
        typeJoinpointDescriptorCache = new ConcurrentReferenceHashMap<>();
    }

    public TypeCache getTypeCache(ClassLoader classLoader, String typeName) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

        // ignore excluded ClassLoader
        if(weaverContext.isExcludedClassLoader(ClassLoaderUtils.getClassLoaderName(classLoader)) == true)
            return TypeCache.DUMMY_TYPE_CACHE;


        if(this.classLoaderTypeCache.containsKey(cacheKey) == false) {
            this.classLoaderTypeCache.putIfAbsent(cacheKey, new ConcurrentHashMap<>());
        }
        ConcurrentMap<String /* typeName */, TypeCache> typeCaches = this.classLoaderTypeCache.get(cacheKey);

        if(typeCaches.containsKey(typeName) == false) {
            typeCaches.putIfAbsent(typeName, new TypeCache());
        }
        return typeCaches.get(typeName);
    }

    public TypeCache removeTypeCache(ClassLoader classLoader, String typeName) {
        ClassLoader cacheKey = ClassLoaderUtils.maskNull(classLoader);

        if(this.classLoaderTypeCache.containsKey(cacheKey) == false)
            return null;
        ConcurrentMap<String /* typeName */, TypeCache> typeCaches = this.classLoaderTypeCache.get(cacheKey);

        return typeCaches.containsKey(typeName) == false ? null: typeCaches.remove(typeName);
    }


    public Joinpoints.Descriptor getJoinpointDescriptor(Lookup lookup, String methodSignature) {
        Class<?> type = lookup.lookupClass();

        ClassLoader classLoader = type.getClassLoader();
        String typeName = type.getTypeName();
        TypeCache typeCache = removeTypeCache(classLoader, typeName);
        // TODO: dummy?

        // create or fetch from cache
        if(this.typeJoinpointDescriptorCache.containsKey(type) == false) {
            this.typeJoinpointDescriptorCache.computeIfAbsent(
                    type, 
                    key -> {
                        ConcurrentMap<String /* methodSignature */, Joinpoints.Descriptor> joinpointDescriptors = new ConcurrentHashMap<>();

                        for(Entry<String, MethodDescription> entry : typeCache.getMethodSignatureMap().entrySet()) {
                            String signature = entry.getKey();
                            joinpointDescriptors.put(signature, 
                                    this.doCreateJoinpointDescriptor(lookup, signature, entry.getValue(), typeCache.getMethodSignatureAspectsMap().get(signature)));
                        }

                        return joinpointDescriptors;
                    }
            );
        }

        ConcurrentMap<String, Joinpoints.Descriptor> joinpointDescriptors = this.typeJoinpointDescriptorCache.get(type);
        return joinpointDescriptors.get(methodSignature);
    }

    protected Joinpoints.Descriptor doCreateJoinpointDescriptor(Lookup lookup, 
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
                            : methodDescription.isConstructor() ? MethodDescription.CONSTRUCTOR_INTERNAL_NAME : methodDescription.getName() )
                    + "(..)";
            return new Joinpoints.Descriptor(lookup, accessibleName, accessibleObject, aspectChain);
        } catch (Exception e) {
            LOGGER.warn("Failed to create Joinpoint.Descriptor for '{}'.", methodSignature, e);
            return null;
        }
    }


    @Override
    public void close() throws IOException {
        this.classLoaderTypeCache.clear();
        this.typeJoinpointDescriptorCache.clear();
        
    }


    public static class TypeCache {

        protected final static TypeCache DUMMY_TYPE_CACHE = new TypeCache();

        // cache matching result per ClassLoader and Type in local storage for future transformation
        private Map<String /* methodSignature */, MethodDescription> methodSignatureMap;
        private Map<String /* methodSignature */, List<? extends Aspect>> methodSignatureAspectsMap;

        // cache transformation result per ClassLoader and Type in local storage
        private boolean transformed;


        TypeCache() {
            methodSignatureMap = Collections.emptyMap();
            methodSignatureAspectsMap = Collections.emptyMap();
        }


        public boolean isMatched() {
            return methodSignatureAspectsMap.size() > 0;
        }

        public boolean isTransformed() {
            return transformed;
        }

        public boolean setTransformed(boolean transformed) {
            boolean preResult = this.transformed;
            this.transformed = transformed;
            return preResult;
        }

        public void setMethodDescriptionAspects(Map<? extends MethodDescription, List<? extends Aspect>> methodDescriptionAspects) {
            final Map<String /* methodSignature */, MethodDescription> methodSignatureMap = new HashMap<>(methodDescriptionAspects.size());
            Map<String /* methodSignature */, List<? extends Aspect>> methodSignatureAspectMap = methodDescriptionAspects.entrySet().stream()
                    .map(e -> {
                        String methodSignature = e.getKey().toGenericString();
                        methodSignatureMap.put(methodSignature, e.getKey());
                        return new SimpleEntry<String, List<? extends Aspect>>(methodSignature, e.getValue());
                    } )
                    .collect( Collectors.toMap(Entry::getKey, Entry::getValue) );

            this.methodSignatureMap = methodSignatureMap;
            this.methodSignatureAspectsMap = methodSignatureAspectMap;
        }

        public Map<String, MethodDescription> getMethodSignatureMap() {
            return Collections.unmodifiableMap( methodSignatureMap );
        }

        public Map<String, List<? extends Aspect>> getMethodSignatureAspectsMap() {
            return Collections.unmodifiableMap( methodSignatureAspectsMap );
        }
    }
}
