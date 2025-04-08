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
package io.gemini.aop.reflection;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.matcher.ElementMatchers;

public class TypeVariableTest<T> {
    public TypeVariableTest() {
    }
 
    public <E> TypeVariableTest(E e,String info) {
    }
 
    public Map<T, String> genericField1;
    public String NonGenericField;
    private static int layer = 0;
 
    public <B> Map<Integer, String>[] genericMethod(Set<T> GenericSet, List<? extends Integer> list1,
            List<String> list2, String str, B[] tArr) throws IOException, NoSuchMethodException {
        return null;
    }
 
    public static void main(String[] args) throws Exception {
        Class<?> clazz = TypeVariableTest.class;
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(clazz);
 
        // 如果字段类型是泛型: getType()只会返回泛型尖括号前面的类型，也就是集合的类型
        // 而getGenericType()会同时返回集合类型和尖括号里面的泛型类型。
        System.out.println("一．  泛型成员变量的参数");
        Field field1 = clazz.getField("genericField1");
        Type fieldGenericType = field1.getGenericType();
        System.out.println("getType is " + field1.getType());
        System.out.println("getGenericType is " + field1.getGenericType());

        FieldDescription fieldDescription = typeDescription.getDeclaredFields().filter(ElementMatchers.named("genericField1")).get(0);
        System.out.println("fieldDescription getType is " + fieldDescription.getType());
        System.out.println("fieldDescription getGenericType is " + fieldDescription.getGenericSignature());
        Generic fieldType = fieldDescription.getType();
        
        instanceActualTypeVariables(fieldGenericType, fieldType);
        System.out.println();

        
        System.out.println("二．  非泛型成员变量的参数");
        Field field2 = clazz.getField("NonGenericField");
        Type fieldGenericType2 = field2.getGenericType();
        System.out.println("getType is " + field2.getType());
        System.out.println("getGenericType is " + field2.getGenericType());
        
        FieldDescription fieldDescription2 = typeDescription.getDeclaredFields().filter(ElementMatchers.named("NonGenericField")).get(0);
        System.out.println("fieldDescription getType is " + fieldDescription2.getType());
        System.out.println("fieldDescription getGenericType is " + fieldDescription2.getGenericSignature());
        Generic fieldType2 = fieldDescription.getType();
        
        instanceActualTypeVariables(fieldGenericType2, fieldType2);
        System.out.println();
 
        
        System.out.println("三．  成员方法返回值的泛型参数。");
        // new Class<?>[]数组是指原始方法参数的类型列表，这里的类型列表必须是完整的不能有遗漏或错误，否则会报错：java.lang.NoSuchMethodException
        Method method = clazz.getMethod("genericMethod",
                new Class<?>[]{Set.class,List.class, List.class, String.class, Object[].class});
        Type genericReturnType = method.getGenericReturnType();
        
        MethodDescription methodDescription = typeDescription.getDeclaredMethods().filter(ElementMatchers.named("genericMethod")).get(0);
        Generic returnType = methodDescription.getReturnType();
        
        instanceActualTypeVariables(genericReturnType, returnType);
        System.out.println();
 
        System.out.println("四．  成员方法入参的泛型参数。");
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        ParameterList<?> parameters = methodDescription.getParameters();
        
        for (int i = 0; i < genericParameterTypes.length; i++) {
            System.out.println("--------------------------------------------");
            System.out.println("该方法的第" + (i + 1) + "个参数：");
            
            ParameterDescription parameterDescription = parameters.get(i);
            System.out.println("该方法的第" + (i + 1) + "个参数名：" + parameterDescription.getActualName());
            
            instanceActualTypeVariables(genericParameterTypes[i], parameterDescription.getType());
        }
        System.out.println();
 
        System.out.println("五．  构造方法参数类型的泛型参数。");
        Constructor<?> constructor = clazz.getConstructor(new Class<?>[]{Object.class,String.class});
        Type[] constructorParameterTypes = constructor.getGenericParameterTypes();
        
        MethodDescription constructorDescription = typeDescription.getDeclaredMethods().filter(ElementMatchers.isConstructor()).get(0);
        ParameterList<?> parameters2 = constructorDescription.getParameters();
        
        for (int i = 0; i < constructorParameterTypes.length; i++) {
            Type constructorParameterType = constructorParameterTypes[i];
            System.out.println("该构造方法的第" + (i + 1) + "个参数："  + constructorParameterType );
            
            ParameterDescription parameterDescription = parameters2.get(i);
            
            instanceActualTypeVariables(constructorParameterType, parameterDescription.getType());
        }
        System.out.println();
    }
 
   // 实例化具体类型
    private static void instanceActualTypeVariables(Type type, Generic elementType) throws Exception {
        if (layer > 0) {
            System.out.println(String.format("进入递归调用第%s层", layer));
        }
        System.out.println("该类型是" + type);
        System.out.println("Generic该类型是" + elementType.getTypeName());
        
        // 判断是否为泛型参数类型：即带尖括号的类型： 类名<var1,var2...>
        if (type instanceof ParameterizedType) {
            System.out.println(String.format("%s类型是泛型！", type));
           //  获取泛型尖括号<>里面的实际数据类型，因为可能存在多个泛型，比如 SuperClass<T, V>，所以会返回 Type[] 数组
            Type[] TypeList = ((ParameterizedType) type).getActualTypeArguments();
            System.out.println("泛型参数列表： " + Arrays.asList(TypeList));
            for (int i = 0; i < TypeList.length; i++) {
                // instanceof TypeVariable判断Type[]数组中的类型变量是否是泛型变量，比如:T,V这样的
                if (TypeList[i] instanceof TypeVariable) {
                    System.out.println(String.format("%s是泛型变量",TypeList[i]));
                    System.out.println("第" + (i + 1) + "个参数类型是泛型类型变量" + TypeList[i] + "，无法实例化。");
                }
                // 通配符类型
                else if (TypeList[i] instanceof WildcardType) {
                    System.out.println("第" + (i + 1) + "个参数类型是泛型通配符表达式" + TypeList[i] + "，无法实例化。");
                }
                // 具体类型非泛型的类型，比如String,Integer这样的类型
                else if (TypeList[i] instanceof Class) {
                    System.out.println("第" + (i + 1) + "个参数是具体类型:" + TypeList[i] + "，可以直接实例化对象");
                }
            }
 
            if (layer > 0) {
                System.out.println("递归调用结束，将递归层数layer重置为0");
                layer = 0; // 此时应将layer重置，因为layer是static变量，如不重置为0会影响其他情况的判断
            }
 
 
            // 参数是泛型数组的情况，比如List<String>,T[], Class<T>[] 这几个都是GenericArrayType：泛型数组
            // 泛型数组：首先它是一个数组，然后数组的成员变量是泛型
        } else if (type instanceof GenericArrayType) {
            System.out.println("该泛型类型是泛型数组，可以获取其原始类型。");
            // getGenericComponentType获取泛型数组中成员变量的类型
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            // 判断泛型数组的成员变量是否是泛型变量，TypeVariable指的是泛型变量本身，比如T,V之类的
            if (componentType instanceof TypeVariable) {
                System.out.println("该泛型数组的成员是泛型变量" + componentType + "，无法实例化。");
            }  else {
                // 因为泛型数组成员变量又是一个带类型变量的泛型（类名<var1,var2...>），需要递归调用方法自身直到解构出具体的泛型类型
                System.out.println("------------------------------------------------------");
                System.out.println("因为是带类型变量的泛型（类名<var1,var2...>），需要继续递归调用直到解构出具体的泛型类型");
                System.out.println("------------------------------------------------------");
                layer++;
                instanceActualTypeVariables(componentType, elementType.getComponentType()); // 递归调用直到解构出TypeVariable类型
            }
        } else if (type instanceof TypeVariable) {
            System.out.println(String.format("该类型是类型变量:%s，无法完成实例化",type));
        } else if (type instanceof WildcardType) {
            System.out.println("该类型是通配符表达式");
        } else if (type instanceof Class) {
            System.out.println("该类型不是泛型类型,可以完成实例化");
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
