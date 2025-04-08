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

import org.aspectj.lang.reflect.PointcutExpression;

import net.bytebuddy.description.type.TypeDescription;

public interface Pointcut {
    
    /**
     * The declared name of the pointcut.
     */
    String getName();
    
    /**
     * The modifiers associated with the pointcut declaration. 
     * Use java.lang.reflect.Modifier to interpret the return value
     */
    int getModifiers();
    
    /**
     * The pointcut parameter types.
     */
    TypeDescription[] getParameterTypes();
    
    /**
     * The pointcut parameter names. Returns an array of empty strings
     * of length getParameterTypes().length if parameter names are not
     * available at runtime.
     */
    String[] getParameterNames();
    
    /**
     * The type that declared this pointcut
     */
    TypeDescription getDeclaringType();

    /**
     * The pointcut expression associated with this pointcut.
     */
    PointcutExpression getPointcutExpression();

}
