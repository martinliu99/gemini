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
package io.gemini.aspectj.weaver.world.internal;

import java.lang.ref.WeakReference;

import net.bytebuddy.pool.TypePool;

public class WeakTypePoolReference {
    
    protected final int hashcode;

    private final WeakReference<TypePool> poolRef;

    public WeakTypePoolReference(TypePool typePool) {
        poolRef = new WeakReference<>(typePool);
        if(typePool == null){
            // Bug: 363962 
            // Check that ClassLoader is not null, for instance when loaded from BootStrapClassLoader
            hashcode = System.identityHashCode(this);
        }else{
            hashcode = typePool.hashCode() * 37;
        }
    }

    public TypePool getTypePool() {
        TypePool instance = (TypePool) poolRef.get();
        // Assert instance!=null
        return instance;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof WeakTypePoolReference))
            return false;
        WeakTypePoolReference other = (WeakTypePoolReference) obj;
        return (other.hashcode == hashcode);
    }

    public int hashCode() {
        return hashcode;
    }
}
