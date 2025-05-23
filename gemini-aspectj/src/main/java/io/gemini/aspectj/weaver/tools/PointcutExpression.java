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
package io.gemini.aspectj.weaver.tools;

import org.aspectj.weaver.tools.MatchingContext;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;

/**
 * Represents an AspectJ pointcut expression and provides convenience methods to determine
 * whether or not the pointcut matches join points specified in terms of the
 * java.lang.reflect interfaces.
 */
public interface PointcutExpression {

    /**
     * Set the matching context to be used for
     * subsequent calls to match.
     * @see MatchingContext
     */
    void setMatchingContext(MatchingContext aMatchContext);
    
    /**
     * Determine whether or not this pointcut could ever match a join point in the given class.
     * @param aClass  the candidate class
     * @return true iff this pointcut <i>may</i> match a join point within(aClass), and false otherwise 
     */
    boolean couldMatchJoinpointsInType(TypeDescription aClass);
    
    /**
     * Returns true iff this pointcut contains any expression that might necessitate a dynamic test
     * at some join point (e.g. args)
     */
    boolean mayNeedDynamicTest();
    
    /**
     * Determine whether or not this pointcut matches the execution of a given method.
     * @param aMethod the method being executed
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matches join points representing the execution of the method. 
     */
    ShadowMatch matchesMethodExecution(MethodDescription aMethod );
    
    /**
     * Determine whether or not this pointcut matches the execution of a given constructor.
     * @param aConstructor the constructor being executed
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matches join points representing the execution of the constructor. 
     */
    ShadowMatch matchesConstructorExecution(MethodDescription aConstructor);

    /**
     * Determine whether or not this pointcut matches the static initialization
     * of the given class.
     * @param aClass the class being statically initialized
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matchs join points representing the static initialization of the given type
     */
    ShadowMatch matchesStaticInitialization(TypeDescription aClass);
    
    /**
     * Determine whether or not this pointcut matches the execution of a given piece of advice.
     * @param anAdviceMethod a method representing the advice being executed
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matches join points representing the execution of the advice. 
     */        
    ShadowMatch matchesAdviceExecution(MethodDescription anAdviceMethod);
    
    /**
     * Determine whether or not this pointcut matches the initialization of an
     * object initiated by a call to the given constructor.
     * @param aConstructor the constructor initiating the initialization
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matches join points representing initialization via the given constructor. 
     */    
    ShadowMatch matchesInitialization(MethodDescription aConstructor);
    
    /**
     * Determine whether or not this pointcut matches the pre-initialization of an
     * object initiated by a call to the given constructor.
     * @param aConstructor the constructor initiating the initialization
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matches join points representing pre-initialization via the given constructor. 
     */    
    ShadowMatch matchesPreInitialization(MethodDescription aConstructor);
    
    /**
     * Determine whether or not this pointcut matches a method call to the given method, made during 
     * the execution of the given method or constructor.
     * @param aMethod the method being called
     * @param withinCode the Method or Constructor from within which the call is made
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matches join points representing a call to this method during the execution of the given member.
     */
    ShadowMatch matchesMethodCall(MethodDescription aMethod, MethodDescription withinCode);

    /**
     * Determine whether or not this pointcut matches a method call to the given method, made outside 
     * of the scope of any method or constructor, but within the callerType (for example, during
     * static initialization of the type).
     * @param aMethod the method being called
     * @param callerType the declared type of the caller
     * @param receiverType the declared type of the recipient of the call
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matches join points representing a call to this method during the execution of the given member.
     */
    ShadowMatch matchesMethodCall(MethodDescription aMethod, TypeDescription callerType);

    /**
     * Determine whether or not this pointcut matches a method call to the given constructor, made during 
     * the execution of the given method or constructor.
     * @param aConstructor the constructor being called
     * @param withinCode the Method or Constructor from within which the call is made
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matches join points representing a call to this constructor during the execution of the given member.
     */
    ShadowMatch matchesConstructorCall(MethodDescription aConstructor, MethodDescription withinCode);

    /**
     * Determine whether or not this pointcut matches a method call to the given constructor, made outside 
     * of the scope of any method or constructor, but within the callerType.
     * @param aConstructor the cosstructor being called
     * @param callerType the declared type of the caller
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or never
     * matches join points representing a call to this constructor during the execution of the given member.
     */
    ShadowMatch matchesConstructorCall(MethodDescription aConstructor, TypeDescription callerType);

    /**
     * Determine whether or not this pointcut matches the execution of a given exception
     * handler within the given method or constructor
     * @param exceptionType the static type of the exception being handled
     * @param withinCode the method or constructor in which the catch block is declared
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or 
     * never matches join points representing the handling of the given exception
     */    
    ShadowMatch matchesHandler(TypeDescription exceptionType, MethodDescription withinCode);
    
    /**
     * Determine whether or not this pointcut matches the execution of a given exception
     * handler outside of the scope of any method or constructor, but within the handling type.
     * @param exceptionType the static type of the exception being handled
     * @param handlingType the type in which the handler block is executing
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or 
     * never matches join points representing the handling of the given exception
     */    
    ShadowMatch matchesHandler(TypeDescription exceptionType, TypeDescription handlingType);
    
    /**
     * Determine whether or not this pointcut matches a set of the given field from within the given
     * method or constructor.
     * @param aField the field being updated
     * @param withinCode the Method or Constructor owning the call site
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or
     * never matches field set join points for the given field and call site.
     */
    ShadowMatch matchesFieldSet(FieldDescription aField, MethodDescription withinCode);
    
    /**
     * Determine whether or not this pointcut matches a set of the given field outside of the
     * scope of any method or constructor, but within the given type (for example, during
     * static initialization).
     * @param aField the field being updated
     * @param withinType the type owning the call site
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or
     * never matches field set join points for the given field and call site.
     */
    ShadowMatch matchesFieldSet(FieldDescription aField, TypeDescription withinType);
    
    /**
     * Determine whether or not this pointcut matches a get of the given field from within the given
     * method or constructor.
     * @param aField the field being updated
     * @param withinCode the Method or Constructor owning the call site
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or
     * never matches field get join points for the given field and call site.
     */
    ShadowMatch matchesFieldGet(FieldDescription aField, MethodDescription withinCode);

    /**
     * Determine whether or not this pointcut matches a get of the given field outside of the
     * scope of any method or constructor, but within the given type (for example, during
     * static initialization).
     * @param aField the field being accessed
     * @param withinType the type owning the call site
     * @return a ShadowMatch indicating whether the pointcut always, sometimes, or
     * never matches field get join points for the given field and call site.
     */
    ShadowMatch matchesFieldGet(FieldDescription aField, TypeDescription withinType);
    
    /**
     * Return a string representation of this pointcut expression.
     */
    String getPointcutExpression();
}
