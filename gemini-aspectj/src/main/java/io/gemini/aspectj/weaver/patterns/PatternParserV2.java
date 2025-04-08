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
package io.gemini.aspectj.weaver.patterns;

import org.aspectj.weaver.ISourceContext;
import org.aspectj.weaver.patterns.ITokenSource;
import org.aspectj.weaver.patterns.KindedPointcut;
import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.Pointcut;

public class PatternParserV2 extends PatternParser {

    public PatternParserV2(String data) {
        super(data);
    }

    public PatternParserV2(ITokenSource tokenSource) {
        super(tokenSource);
    }

    public PatternParserV2(String data, ISourceContext context) {
        super(data, context);
    }


    public Pointcut parseSinglePointcut() {
        Pointcut pointcut = super.parseSinglePointcut();
        if(pointcut instanceof KindedPointcut == false)
            return pointcut;

        return new KindedPointcutV2( (KindedPointcut)pointcut );
    }
}
