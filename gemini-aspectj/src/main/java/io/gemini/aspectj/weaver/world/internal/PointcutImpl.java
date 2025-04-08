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

import java.util.StringTokenizer;

import org.aspectj.internal.lang.reflect.PointcutExpressionImpl;
import org.aspectj.lang.reflect.PointcutExpression;

import io.gemini.aspectj.weaver.world.Pointcut;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;

/**
 * @author colyer
 *
 */
public class PointcutImpl implements Pointcut {

    private final String name;
    private final PointcutExpression pc;
    private final MethodDescription baseMethod;
    private final TypeDescription declaringType;
    private String[] parameterNames = new String[0];
    
    public PointcutImpl(String name, String pc, MethodDescription method, TypeDescription declaringType, String pNames) {
        this.name = name;
        this.pc = new PointcutExpressionImpl(pc);
        this.baseMethod = method;
        this.declaringType = declaringType;
        this.parameterNames = splitOnComma(pNames);
    }
    
    /* (non-Javadoc)
     * @see org.aspectj.lang.reflect.Pointcut#getPointcutExpression()
     */
    public PointcutExpression getPointcutExpression() {
        return pc;
    }
    
    public String getName() {
        return name;
    }

    public int getModifiers() {
        return baseMethod.getModifiers();
    }

    public TypeDescription[] getParameterTypes() {
//        Class<?>[] baseParamTypes =  baseMethod.getParameterTypes();
//        AjType<?>[] ajParamTypes = new AjType<?>[baseParamTypes.length];
//        for (int i = 0; i < ajParamTypes.length; i++) {
//            ajParamTypes[i] = AjTypeSystem.getAjType(baseParamTypes[i]);
//        }
//        return ajParamTypes;

        ParameterList<?> baseParamTypes =  baseMethod.getParameters();
        TypeDescription[] ajParamTypes = new TypeDescription[baseParamTypes.size()];
        for (int i = 0; i < ajParamTypes.length; i++) {
            ajParamTypes[i] = baseParamTypes.get(i).getType().asErasure();
        }
        return ajParamTypes;

    }

    public TypeDescription getDeclaringType() {
        return declaringType;
    }
    
    public String[] getParameterNames() {
        return parameterNames;
    }

    private String[] splitOnComma(String s) {
        StringTokenizer strTok = new StringTokenizer(s,",");
        String[] ret = new String[strTok.countTokens()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = strTok.nextToken().trim();
        }
        return ret;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getName());
        sb.append("(");
        TypeDescription[] ptypes = getParameterTypes();
        for (int i = 0; i < ptypes.length; i++) {
            sb.append(ptypes[i].getName());
            if (this.parameterNames != null && this.parameterNames[i] != null) {
                sb.append(" ");
                sb.append(this.parameterNames[i]);
            }
            if (i+1 < ptypes.length) sb.append(",");
        }
        sb.append(") : ");
        sb.append(getPointcutExpression().asString());
        return sb.toString();
    }
}
