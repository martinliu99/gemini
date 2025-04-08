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
package io.gemini.aspectj.weaver.world;

import java.util.HashMap;
import java.util.Map;

import org.aspectj.bridge.AbortException;
import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.IMessageHandler;
import org.aspectj.weaver.IWeavingSupport;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ReferenceTypeDelegate;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.TypeVariable;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;

import io.gemini.aspectj.weaver.world.internal.AnnotationFinderImpl;
import io.gemini.aspectj.weaver.world.internal.ReferenceTypeDelegateFactoryImpl;
import io.gemini.aspectj.weaver.world.internal.WeakTypePoolReference;
import io.gemini.core.util.PlaceholderHelper;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;

public class TypeWorld extends World {

    private WeakTypePoolReference typePoolReference;
    private AnnotationFinder annotationFinder;
    private PlaceholderHelper placeholderHelper;

    private final Map<TypeDescription, TypeVariable[]> workInProgress1 = new HashMap<>();


    public TypeWorld(TypePool typePool, PlaceholderHelper placeholderHelper) {
        super();
        this.setMessageHandler(new ExceptionBasedMessageHandler());
//        setBehaveInJava5Way(LangUtil.is15VMOrGreater());
        setBehaveInJava5Way(true);

        typePoolReference = new WeakTypePoolReference(typePool);
        annotationFinder = makeAnnotationFinderIfAny(typePoolReference.getTypePool(), this);
        this.placeholderHelper = placeholderHelper;
    }

    public PlaceholderHelper getPlaceholderHelper() {
        return placeholderHelper;
    }

    public static AnnotationFinder makeAnnotationFinderIfAny(TypePool typePool, TypeWorld world) {
        AnnotationFinder annotationFinder = new AnnotationFinderImpl();
        annotationFinder.setTypeWorld(world);

        return annotationFinder;
//        try {
////            if (LangUtil.is15VMOrGreater()) {
//                Class<?> java15AnnotationFinder = Class.forName("io.gemini.aspectj.pattern.support.Java15AnnotationFinder");
//                annotationFinder = (AnnotationFinder) java15AnnotationFinder.newInstance();
//                annotationFinder.setTypePool(typePool);
//                annotationFinder.setWorld(world);
////            }
//        } catch (ClassNotFoundException ex) {
//            // must be on 1.4 or earlier
//        } catch (IllegalAccessException ex) {
//            // not so good
//            throw new BCException("AspectJ internal error", ex);
//        } catch (InstantiationException ex) {
//            throw new BCException("AspectJ internal error", ex);
//        }
//        return annotationFinder;
    }
    

    public TypePool getTypePool() {
        return typePoolReference.getTypePool();
    }

    @Override
    protected ReferenceTypeDelegate resolveDelegate(ReferenceType ty) {
        return ReferenceTypeDelegateFactoryImpl.createDelegate(ty, this);
    }

    @Override
    public IWeavingSupport getWeavingSupport() {
        return null;
    }

    @Override
    public boolean isLoadtimeWeaving() {
        return true;
    }


    public ResolvedType resolve(TypeDefinition typeDefinition) {
        return resolve(this, typeDefinition);
    }
    
    public static ResolvedType resolve(World world, TypeDefinition typeDefinition) {
        // classes that represent arrays return a class name that is the
        // signature of the array type, ho-hum...
        String className = typeDefinition.asErasure().getName();
        if (typeDefinition.isArray()) {
            return world.resolve(UnresolvedType.forSignature(className.replace('.', '/')));
        } else {
            return world.resolve(className);
        }
    }


    public AnnotationFinder getAnnotationFinder() {
        return this.annotationFinder;
    }

    public TypeVariable[] getTypeVariablesCurrentlyBeingProcessed(TypeDescription baseClass) {
        return workInProgress1.get(baseClass);
    }

    public void recordTypeVariablesCurrentlyBeingProcessed(TypeDescription baseClass, TypeVariable[] typeVariables) {
        workInProgress1.put(baseClass, typeVariables);
    }

    public void forgetTypeVariablesCurrentlyBeingProcessed(TypeDescription baseClass) {
        workInProgress1.remove(baseClass);
    }

    private static class ExceptionBasedMessageHandler implements IMessageHandler {

        public boolean handleMessage(IMessage message) throws AbortException {
            throw new ByteCodeWorldException(message.toString());
        }

        public boolean isIgnoring(org.aspectj.bridge.IMessage.Kind kind) {
            if (kind == IMessage.INFO) {
                return true;
            } else {
                return false;
            }
        }

        public void dontIgnore(org.aspectj.bridge.IMessage.Kind kind) {
            // empty
        }

        public void ignore(org.aspectj.bridge.IMessage.Kind kind) {
            // empty
        }

    }


    public static class ByteCodeWorldException extends RuntimeException {

        /**
         * 
         */
        private static final long serialVersionUID = 816600136638029684L;

        public ByteCodeWorldException(String message) {
            super(message);
        }
    }

}
