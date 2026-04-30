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
package io.gemini.aop;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Enumerates the supported advice kinds in the Gemini AOP framework.
 * <p>
 * Three advice style families are supported, each with their own enum:
 * <ul>
 *   <li>{@link PojoAdviceKind} – plain Java advice with before/after/around semantics</li>
 *   <li>{@link AspectJAdviceKind} – AspectJ-annotation-based advice (@Before, @After, @Around, etc.)</li>
 *   <li>{@link ByteBuddyAdviceKind} – low-level ByteBuddy @Advice.OnMethodEnter / @Advice.OnMethodExit</li>
 * </ul>
 * </p>
 *
 * @author   martin.liu
 */
public interface AdviceKind {

    /** 
     * Marker interface for advice kinds that are managed by the Gemini AOP framework lifecycle. 
     */
    interface Managed {}


    /** 
     * POJO-style advice kinds: before, after, before+after, and around. 
     */
    enum PojoAdviceKind implements AdviceKind, Managed {

        BEFORE(true, false, false), 
        AFTER(false, true, false), 
        BEFORE_AFTER(true, true, false), 
        AROUND(false, false, true);


        private final boolean before;
        private final boolean after;
        private final boolean around;


        /**
         * Parses the combination of before/after/around flags into the corresponding enum constant.
         *
         * @param beforeAdvice {@code true} if before advice is present
         * @param afterAdvice  {@code true} if after advice is present
         * @param aroundAdvice {@code true} if around advice is present
         * @return the matching {@link PojoAdviceKind}
         */
        public static PojoAdviceKind parse(boolean beforeAdvice, boolean afterAdvice, boolean aroundAdvice) {
            if (aroundAdvice)
                return AROUND;
            else if (beforeAdvice && afterAdvice)
                return BEFORE_AFTER;
            else if (beforeAdvice == false)
                return AFTER;
            else
                return BEFORE;
        }

        private PojoAdviceKind(boolean before, boolean after, boolean around) {
            this.before = before;
            this.after = after;
            this.around = around;
        }

        public boolean isBefore() {
            return before;
        }

        public boolean isAfter() {
            return after;
        }

        public boolean isAround() {
            return around;
        }
    }


    /** 
     * AspectJ annotation-based advice kinds: @Before, @After, @AfterReturning, @AfterThrowing, @Around. 
     */
    enum AspectJAdviceKind implements AdviceKind, Managed {

        BEFORE(true, false, false, false, false),
        AFTER(false, true, false, false, false),
        AFTER_RETURNING(false, false, true, false, false),
        AFTER_THROWING(false, false, false, true, false),
        AROUND(false, false, false, false, true);


        private static Map<String, AspectJAdviceKind> VALUE_MAP;
        static {
            VALUE_MAP = new HashMap<>(AspectJAdviceKind.values().length);
            for (AspectJAdviceKind kind : AspectJAdviceKind.values())
                VALUE_MAP.put(kind.toString().replace("_", ""), kind);
        }


        private final boolean before;

        private final boolean after;
        private final boolean afterReturning;
        private final boolean afterThrowing;

        private final boolean around;


        /**
         * Parses a string value (e.g., {@code "BEFORE"}, {@code "AFTERRETURNING"}) into the
         * corresponding enum constant, ignoring underscores and case.
         *
         * @param value the string to parse
         * @return the matching {@link AspectJAdviceKind}
         * @throws IllegalArgumentException if the value is not recognized
         */
        public static AspectJAdviceKind parse(String value) {
            value = value == null ? "" : value.trim().toUpperCase(Locale.ENGLISH);
            AspectJAdviceKind kind = VALUE_MAP.get(value);
            if (kind != null)
                return kind;

            throw new IllegalArgumentException("Unsupported AspectJAdviceKind [" + value + "]");
        }

        private AspectJAdviceKind(boolean before, boolean after, 
                boolean afterReturning, boolean afterThrowing,
                boolean around) {
            this.before = before;
            this.after = after;
            this.afterReturning = afterReturning;
            this.afterThrowing = afterThrowing;
            this.around = around;
        }

        public boolean isBefore() {
            return before;
        }

        public boolean isAfter() {
            return after;
        }

        public boolean isAfterReturning() {
            return afterReturning;
        }

        public boolean isAfterThrowing() {
            return afterThrowing;
        }

        public boolean isAround() {
            return around;
        }
    }


    /** 
     * ByteBuddy low-level advice kinds: OnMethodEnter, OnMethodExit, or both. 
     */
    enum ByteBuddyAdviceKind implements AdviceKind {

        ON_METHOD_ENTER(true, false),
        ON_METHOD_EXIT(false, true),
        ON_METHOD_ENTER_EXIT(true, true)
        ;


        private final boolean onMethodEnter;
        private final boolean onMethodExit;

        /**
         * Parses the combination of enter/exit flags into the corresponding enum constant.
         *
         * @param onMethodEnter {@code true} if {@code @Advice.OnMethodEnter} is present
         * @param onMethodExit  {@code true} if {@code @Advice.OnMethodExit} is present
         * @return the matching {@link ByteBuddyAdviceKind}
         */
        public static ByteBuddyAdviceKind parse(boolean onMethodEnter, boolean onMethodExit) {
            if (onMethodEnter && onMethodExit)
                return ON_METHOD_ENTER_EXIT;
            else if (onMethodEnter == false)
                return ON_METHOD_EXIT;
            else
                return ON_METHOD_ENTER;
        }

        private ByteBuddyAdviceKind(boolean onMethodEnter, boolean onMethodExit) {
            this.onMethodEnter = onMethodEnter;
            this.onMethodExit = onMethodExit;
        }

        public boolean isOnMethodEnter() {
            return onMethodEnter;
        }

        public boolean isOnMethodExit() {
            return onMethodExit;
        }
    }
}
