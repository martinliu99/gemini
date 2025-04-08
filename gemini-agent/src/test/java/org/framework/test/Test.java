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
package org.framework.test;

public class Test extends SuperTest {


    // super type?
    // super method
    
    public Test() {
        System.out.println("constructor Test()");
    }
    
    public String doTest(String in) {
        System.err.println("test with args: " + in);
        return "Test [] " + staticMethod(in);
    }

    
    private static String staticMethod(String in) {
        return in + "1";
    }
}
