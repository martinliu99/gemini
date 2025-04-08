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
package io.gemini.aspectj.weaver;

import net.bytebuddy.description.type.TypeDescription;

/**
 *
 *
 * @author   martin.liu
 * @since	 1.0
 */
public class ParameterBinding {

    private final String name;
    private final TypeDescription type;
    private final BindingType bindingType;
    private final int argIndex;

    public ParameterBinding(String name, TypeDescription type, BindingType bindingType) {
        this(name, type, bindingType, -1);
    }

    public ParameterBinding(String name, TypeDescription type, BindingType bindingType, int argIndex) {
        this.name = name;
        this.type = type;
        this.bindingType = bindingType;
        this.argIndex = argIndex;
    }

    public String getName() {
        return name;
    }

    public TypeDescription getType() {
        return type;
    }

    public BindingType getBindingType() {
        return bindingType;
    }

    public int getArgIndex() {
        return argIndex;
    }
}
