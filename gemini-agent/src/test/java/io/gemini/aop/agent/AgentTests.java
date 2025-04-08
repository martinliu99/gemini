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
package io.gemini.aop.agent;

import org.framework.test.Test;

public class AgentTests {

        
    public static void main(String[] args) throws InterruptedException, ClassNotFoundException {

        {
            Test t = new Test();
            System.out.println("t is: " + t.doTest("before"));
//
//            Runnable str = new Runnable() {
//                @Override
//                public void run() {
//                    System.out.println("run");
//                }
//            }; 
//            str.run();
//            System.out.println("str is: " + str.toString());
//
//            System.out.println("\n\n");
        }
//        
//        Instrumentation inst = ByteBuddyAgent.install();
//        ByteCodeWeaver weaver = new ByteCodeWeaver(inst, new WeaverBootstrapConfig());
//        weaver.injectBootstrap();
//        weaver.instrumentClasses(inst);
        
//        
//        System.out.println("\n\n");
        
        
        // warm up
//        ClassLoader classLoader = ByteCodeWeaver_AgentTests.class.getClassLoader();
//        classLoader.loadClass("org.framework.test.Test");
//        classLoader.loadClass("java.util.concurrent.ThreadPoolExecutor");
//        classLoader.loadClass("java.lang.Runnable");
        
        
//        LOGGER.info("\n\n\n");
//        Test t = new Test();
//        LOGGER.info("result is: " + t.doTest("after"));


//        Runnable str = new Runnable() {
//            @Override
//            public void run() { 
//                LOGGER.info("run");
//            }
//        };
//        str.run();
//        LOGGER.info("str is: " + str.toString());
        
//        new Thread(
//                 new Runnable() {
//                        @Override
//                        public void run() {
//                            System.out.println("thread");
//                        }
//                    }).start();;
//        
//        ExecutorService e = new ThreadPoolExecutor(1, 1,
//                0L, TimeUnit.MILLISECONDS,
//                new LinkedBlockingQueue<>());
//            
//    
//        e.execute( new Runnable() {
//            @Override
//            public void run() {
//                System.out.println("threadpool");
//            }
//        });
//        Thread.sleep(1000);
//        e.shutdownNow();
    }

}
