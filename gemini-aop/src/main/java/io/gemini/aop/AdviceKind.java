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
import java.util.Map;

public interface AdviceKind {

    interface Managed {}


    enum PojoAdviceKind implements AdviceKind, Managed {

        BEFORE(true, false, false), 
        AFTER(false, true, false), 
        BEFORE_AFTER(true, true, false), 
        AROUND(false, false, true);


        private final boolean before;
        private final boolean after;
        private final boolean around;


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


        public static AspectJAdviceKind parse(String value) {
            value = value == null ? "" : value.trim().toUpperCase();
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


    enum ByteBuddyAdviceKind implements AdviceKind {

        ON_METHOD_ENTER(true, false),
        ON_METHOD_EXIT(false, true),
        ON_METHOD_ENTER_EXIT(true, true)
        ;


        private final boolean onMethodEnter;
        private final boolean onMethodExit;

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
