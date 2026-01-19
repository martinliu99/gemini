
TODO:
cat classpath

classloader

1. warm-up and verify
2. aspectj expression/around
3. class and method matching

service manager
configuration



A. feature list
1.AOP support -- aspect
1) define aspect																					O-good
    a) support aspectj style?
    b) support spring component interceptor style					OK
	c) support introduction and class file structure change?
	d) line number change											OK
	e) before advice and after advice, no around advice
2) enhance same type and same method only one time, and can transform at runtime multiple times		OK-good
  	a) how to identify same class
  	  check TypeDescription
	  get joinpoint class name to warm up ???
  	b) how to identify same method
  	  check MethodDescription
  	c) inject invocation chain
	d) check witness class/method, etc
3) signature																						OK
    a) joinpoint: object constructor/method and class method
    b) change arguments and result

https://github.com/raphw/byte-buddy/issues/746


2.instrumentation support -- weaver
aspectj/sermant/etc

1) use advice for better performance and runtime class transformation								OK -good
	a) trigger call chain
	b) lambda support for platform thread, and virtual thread?
	c) bytebuddy vs bytekit and aspectj
	   refer cat agent, how to define classloader etc
2) when to load aspect class																				O -good
    agent startup			x
    method invoke			x
    a) class loading/warm up	loading stage to fetch candidate class's classloader				OK
	   use candidate class's classloader as parent loader
	   aspect classes will not impact business classes
3) where to load aspect class
	a) load from SPI/config file, annotation, etc
	b) try to find candidate class's classloader and warm up candidates								O -good
4) how to load aspect class
    a) weaver class loader
       spring boot class loader
    b) aspect class loader
    c) ignore aspect defined in application class loader to avoid duplicate weaving
5) jdk classes support, such as thread, thread pool, etc											OK
	a) redefine ignore rules
	b) inject spy into bootstrap
       load class with ByteBuddy().load																OK
    c) initialize factory instance with agent classloader and referred by spy						OK
6) witness
7) warmup
8) do and redo transformation
	a) avoid to trigger twice, such as fetch original byte code, and transform on it
9) JDK9 module support

bytebuddy之advice详解 & 注解详解
https://blog.csdn.net/wanxiaoderen/article/details/107367250



3.reliable bytecode instrumentation
1) startup hook 
   a) validate agent is loader 
   b) monitor redefinition process and result.
   c) trigger candidates warm-up
2) warm up candidate class loading & verify aspect instrumentation successfully with correct class loader
   pre-main
   pre-listener
   a) try to load candidate classes, find web class loader
3) aspect global exception handling
4) dedicated class loaders to avoid conflicting with application classes



4.framework support
1) load configuration 
   a) from config source 
   b) from apollo
2) load aspect
   a) component scan
   b) spi/config file scan
3) monitoring
   a) log to application log file or dedicated log file
4) action: embedded web server to accept runtime instructions
    a) integration check
    b) startup and health check
5) integration mode
   a) agent, or framework via attaching
   check integrated with classloader?




B.meaningful scenarios,
1. service enhancement
   a) adjust log level and component at runtime 
   b) record trace information in log file
   c) adjust component default settings

2. service life-cycle
   a) application startup
   b) graceful shutdown
   c) health check
   d) application standby

3. middle-ware governance
   a) redis hot key

4. micro-service governance
   a) multi-tenant support
   b) dual-live support
   c) multiple environment support

5. testing
   a)failure simulation similar to chao-blade




C.design
service-buddy/infra

weave-ware

concerns 
  normalizer  mesher/sidecar governor  defender/security		
platform
			  weaver

1.module structure and naming
a) root 
   advisor definition
   exception
b) classloader
   advisor loader
   weaver loader
   module/aspect
c) core


