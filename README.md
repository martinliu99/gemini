# Gemini AOP

[English](#gemini-aop-english) | [中文](#gemini-aop-中文)

---

<a name="gemini-aop-english"></a>
# Gemini AOP — English

## What Is This

Gemini is a runtime AOP framework based on **Java Agent**. Users organize a group of code-isolated Advisors as an AspectApp (e.g., distributed tracing, security, architecture governance). At class-loading time, the framework matches target classes via pointcut expressions or `ElementMatcher`, then weaves Advice into target classes non-invasively by modifying Java bytecode.

### What Can It Do

- **Non-invasive load-time weaving**: Zero changes to business code — activate AOP simply via the `-javaagent` parameter
- **Multi-AspectApp isolation**: Each AspectApp has its own isolated `AspectClassLoader`, fully separated from the application class loader
- **Bootstrap class weaving**: Supports weaving into JDK core classes such as `java.lang.Thread`
- **Multiple Pointcut styles**: Supports POJO Pointcut via ByteBuddy `ElementMatcher`, or AspectJ pointcut expressions
- **Multiple Advice styles**: Supports class implemented BeforeAdvice and/or AfterAdvice interface, or class annotated with AspectJ `@Before/@After/@AfterReturning/@AfterThrowing`, and class annotated with ByteBuddy `@Advice.OnMethodEnter/@Advice.OnMethodExit`
- **Conditional activation**: Use `@ConditionalOnType`, `@ConditionalOnMethod`, `@ConditionalOnClassLoader`, etc. to activate Advisors on demand, avoiding unnecessary pointcut matching overhead
- **Performance observability**: Built-in detailed startup timing breakdown and weaving metrics, with three diagnostic levels: DISABLED / SIMPLE / DEBUG

### Project Modules

```
gemini/
├── gemini-api/           # Public interfaces: AopLauncher, Advice, Pointcut, Joinpoint, and annotations
├── gemini-core/          # Core utilities: ClassScanner, ObjectFactory, ConfigView, TaskExecutor
├── gemini-aspectj/       # AspectJ integration: TypeWorld, pointcut expression parsing and matching
├── gemini-aop/           # AOP main implementation: DefaultAopLauncher, DefaultAdvisorFactory, DefaultAopWeaver
├── gemini-activator/     # Java Agent entry: AopAgent, AopActivator, DefaultAopClassLoader
├── gemini-test/          # Test utilities
├── gemini-toolkit/       # Extension tools
├── gemini-aspects/       # Built-in aspects (e.g., ApplicationStartupAspect)
└── gemini-demo/          # Sample project
    ├── gemini-demo-aspects/   # Sample aspect definitions
    ├── gemini-demo-runner/    # Sample application entry
    └── gemini-demo-service/   # Sample business service
└── gemini-release/       # Build and packaging output
```

---

## How to Use

### 1. Add Dependency

Using `gemini-demo/gemini-demo-aspects` as an example, add the Gemini API to your aspect application's `pom.xml`:

```xml
<dependency>
    <groupId>io.gemini</groupId>
    <artifactId>gemini-api</artifactId>
    <version>${gemini.version}</version>
    <scope>provided</scope>
</dependency>
```

### 2. Define an Advisor

#### Option 1: PojoPointcut (ByteBuddy ElementMatcher Pointcut)

Use ByteBuddy's `ElementMatcher` to match target types and methods — suitable for complex matching logic:

```java
@PojoPointcut(pointcutClass = MyAdvice.class)
public class MyAdvice extends Advice.AbstractBeforeAfter<Response<String>, RuntimeException> implements Pointcut {

    @Override
    public ElementMatcher<TypeDescription> getTypeMatcher() {
        return named("org.framework.demo.service.DemoServiceImpl");
    }

    @Override
    public ElementMatcher<MethodDescription> getMethodMatcher() {
        return named("process");
    }

    @Override
    public void before(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        // before logic
    }

    @Override
    public void after(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        // after logic
    }
}
```

#### Option 2: ExprPointcut (AspectJ Pointcut Expression)

The most concise approach — declare an AspectJ pointcut expression directly on the Advice class with `@ExprPointcut`:

```java
@ExprPointcut(pointcutExpression =
    "execution(java.lang.String org.framework.demo.service.DemoServiceImpl.process2(java.lang.String))")
public class MyAdvice extends Advice.AbstractBeforeAfter<String, RuntimeException> {

    @Override
    public void before(MutableJoinpoint<String, RuntimeException> joinpoint) throws Throwable {
        // Modify arguments before target method executes
        String request = (String) joinpoint.getArguments()[0];
        joinpoint.getArguments()[0] = "modified-" + request;
    }

    @Override
    public void after(MutableJoinpoint<String, RuntimeException> joinpoint) throws Throwable {
        // Handle return value after target method executes
        String result = joinpoint.getReturning();
        // ...
    }
}
```

#### Option 3: AspectJ Annotation Style (@Aspect)

Compatible with standard AspectJ annotations — ideal for migrating from Spring AOP:

```java
@Aspect
public class MyAspect {

    @Pointcut("execution(* org.framework.demo.service..*Impl.process(org.framework.demo.api.Request))")
    public void process() {}

    @Before("process()")
    public void before(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        // before logic
    }

    @AfterReturning(pointcut = "process()", returning = "returning")
    public Object after(MutableJoinpoint<Response<String>, RuntimeException> joinpoint,
                        Response<String> returning) throws Throwable {
        // after returning logic
        return returning;
    }
}
```

Advice defined via Option 1 or Option 2 can also implement `io.gemini.api.aop.advice.BeforeAdvice` / `io.gemini.api.aop.advice.AfterAdvice` interfaces, or use `@Advice.OnMethodEnter` / `@Advice.OnMethodExit` annotations.

### 3. Build and Deploy AspectApp

```bash
mvn package
```

Copy the aspect and dependency JARs to the corresponding directories:

```
gemini/
├── gemini-activator.jar
├── gemini-release/
│   ├── aspectapps/
│   │   └── gemini-demo-aspects/
│   │       ├── aspects/
│   │       ├── conf/
│   │       └── lib/
```

### 4. Attach Java Agent on Startup

```bash
java -javaagent:/path/to/gemini-activator.jar \
     -jar your-application.jar
```

Gemini automatically scans the `aspectapps/` directory, loads, and activates all Advisors.

### 5. Conditional Activation

Use conditional annotations to control whether an Advisor takes effect, avoiding pointcut matching overhead in unsupported environments:

```java
// Activate only under Bootstrap ClassLoader (for weaving JDK core classes)
@ConditionalOnClassLoader(isBootstrapClassLoader = true)
@PojoPointcut(pointcutClass = ThreadAdvice.class)
public class ThreadAdvice extends Advice.AbstractBeforeAfter<Void, RuntimeException>
        implements Pointcut {

    @Override
    public ElementMatcher<TypeDescription> getTypeMatcher() {
        return named("java.lang.Thread");
    }

    @Override
    public ElementMatcher<MethodDescription> getMethodMatcher() {
        return named("start");
    }
    // ...
}
```

Other conditional annotations:
- `@ConditionalOnType(typeExpression = "com.example.SomeClass")` — activates when the specified type exists in the target class loader
- `@ConditionalOnMethod(methodExpression = "...")` — activates when the specified method exists in the target class loader
- `@ConditionalOnField(fieldExpression = "...")` — activates when the specified field exists in the target class loader

### 6. Advanced Features

**Circularity breaker** (prevents infinite recursion when Advice triggers the same Joinpoint internally):

```java
@EnableCircularityBreaker
@ExprPointcut(pointcutExpression = "execution(* com.example..*(..))")
public class MyAdvice extends Advice.AbstractBeforeAfter<Object, RuntimeException> {
    // ...
}
```

**Per-Instance Advice** (creates an independent Advice instance for each target object instance):

```java
@EnablePerInstance
@ExprPointcut(pointcutExpression = "execution(* com.example..*(..))")
public class StatefulAdvice extends Advice.AbstractBeforeAfter<Object, RuntimeException> {
    private int callCount = 0; // independent counter per target instance
    // ...
}
```

**Custom Advisor name**:

```java
@AdvisorName("my-custom-advisor")
@ExprPointcut(pointcutExpression = "execution(* com.example..*(..))")
public class MyAdvice extends Advice.AbstractBeforeAfter<Object, RuntimeException> {
    // ...
}
```

### 7. Configuration

Override default settings in your aspect application's `userconf/aop.properties`:

```properties
# Log level
aop.logger.allLogLevel = INFO

# Class scanning scope (improves performance)
aop.classScanner.acceptPackages = com.example.aspects*

# Diagnostic mode: DISABLED | SIMPLE | DEBUG
aop.launcher.diagnosticLevel = DISABLED

# Whether to share AspectClassLoader
aop.factory.shareAspectClassLoader = true

# Conflicting target ClassLoaders (may load the same class)
aop.factory.conflictTargetClassLoaders = com.example.RunnerClassLoader, com.example.RunnerClassLoader2
```

---

## Running the Demo

### Prerequisites

- JDK 8+
- Maven 3.6+

### Build the Project

```bash
# Clone the repository
git clone https://github.com/martinliu99/gemini.git
cd gemini

# Build all modules
mvn clean package -DskipTests

# Build and run tests
mvn clean verify
```

### Run the Demo Application

```bash
# Enter the demo runner directory
cd gemini-demo/gemini-demo-runner

# Start with gemini-activator as Java Agent
java -javaagent:../../gemini-release/gemini-activator.jar \
     -jar target/gemini-demo-runner-*.jar
```

The demo application loads `DemoServiceImpl` via a custom ClassLoader and triggers three different Advisor styles to intercept the `process()` and `process2()` methods.

---

## License

This project is open-sourced under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

```
Copyright © 2023 - present, the original author or authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## Contributing

Issues and Pull Requests are welcome. Before submitting code, please ensure:

1. All existing tests pass: `mvn verify`
2. New features include corresponding unit tests
3. Code follows the existing style

---

[Back to Top / 回到顶部](#gemini-aop)

---

<a name="gemini-aop-中文"></a>
# Gemini AOP — 中文

## 这是什么

Gemini 是一个基于 **Java Agent** 的运行时 AOP 框架。使用者以 AspectApp 为单位，组织一组代码互相隔离的切面 Advisor，例如链路追踪、安全、架构治理等，框架在类加载时根据切面表达式、或者 ElementMatcher 匹配目标类，通过修改 Java 字节码，将切面 Advice 无侵入地织入目标类。

### 可以做什么

- **无侵入类加载时切面织入**：业务代码零修改，通过 `-javaagent` 参数即可激活 AOP 能力
- **多 AspectApp 隔离**：每个 AspectApp 拥有独立的、互相隔离的 `AspectClassLoader`，与业务类加载器隔离
- **Bootstrap 类织入**：支持 JDK 核心类，如 `java.lang.Thread`，切面织入
- **多种 Pointcut 定义**：支持基于 ByteBuddy ElementMatcher 的 POJO Pointcut、或者 AspectJ 切面表达式等
- **多种 Advice 定义**：支持 POJO Before/After、AspectJ `@Before/@After/@AfterReturning/@AfterThrowing`、以及 ByteBuddy `@Advice.OnMethodEnter/@Advice.OnMethodExit` 等
- **条件激活**：通过 `@ConditionalOnType`、`@ConditionalOnMethod`、`@ConditionalOnClassLoader` 等注解，按需激活 Advisor，避免不必要的切面匹配开销
- **性能可观测**：内置详细的启动耗时分解和织入指标，支持 DISABLED / SIMPLE / DEBUG 三级诊断模式

### 项目模块

```
gemini/
├── gemini-api/           # 公共接口：AopLauncher、Advice、Pointcut、Joinpoint、注解
├── gemini-core/          # 核心工具：ClassScanner、ObjectFactory、ConfigView、TaskExecutor
├── gemini-aspectj/       # AspectJ 集成：TypeWorld、Pointcut 表达式解析
├── gemini-aop/           # AOP 主实现：DefaultAopLauncher、AdvisorFactory、DefaultAopWeaver
├── gemini-activator/     # Java Agent 入口：AopAgent、AopActivator、DefaultAopClassLoader
├── gemini-test/          # 测试工具
├── gemini-toolkit/       # 扩展工具
├── gemini-aspects/       # 内置切面（如 ApplicationStartupAspect）
└── gemini-demo/          # 示例工程
    ├── gemini-demo-aspects/   # 示例切面定义
    ├── gemini-demo-runner/    # 示例应用入口
    └── gemini-demo-service/   # 示例业务服务
└── gemini-release/       # 编译打包目录
```

---

## 怎么使用

### 1. 添加依赖

以 `gemini-demo/gemini-demo-aspects` 为例，在切面应用的 `pom.xml` 中引入 Gemini API：

```xml
<dependency>
    <groupId>io.gemini</groupId>
    <artifactId>gemini-api</artifactId>
    <version>${gemini.version}</version>
    <scope>provided</scope>
</dependency>
```

### 2. 定义 Advisor

#### 方式一：PojoPointcut（ByteBuddy ElementMatcher Pointcut）

使用 ByteBuddy 的 `ElementMatcher` 匹配目标类型和方法，适合需要复杂匹配逻辑的场景：

```java
@PojoPointcut(pointcutClass = MyAdvice.class)
public class MyAdvice extends Advice.AbstractBeforeAfter<Response<String>, RuntimeException>
        implements Pointcut {

    @Override
    public ElementMatcher<TypeDescription> getTypeMatcher() {
        return named("org.framework.demo.service.DemoServiceImpl");
    }

    @Override
    public ElementMatcher<MethodDescription> getMethodMatcher() {
        return named("process");
    }

    @Override
    public void before(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        // before logic
    }

    @Override
    public void after(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        // after logic
    }
}
```

#### 方式二：ExprPointcut（AspectJ 切面表达式）

最简洁的方式，直接在 Advice 类上用 `@ExprPointcut` 声明 AspectJ 切面表达式：

```java
@ExprPointcut(pointcutExpression =
    "execution(java.lang.String org.framework.demo.service.DemoServiceImpl.process2(java.lang.String))")
public class MyAdvice extends Advice.AbstractBeforeAfter<String, RuntimeException> {

    @Override
    public void before(MutableJoinpoint<String, RuntimeException> joinpoint) throws Throwable {
        // 在目标方法执行前修改参数
        String request = (String) joinpoint.getArguments()[0];
        joinpoint.getArguments()[0] = "modified-" + request;
    }

    @Override
    public void after(MutableJoinpoint<String, RuntimeException> joinpoint) throws Throwable {
        // 在目标方法执行后处理返回值
        String result = joinpoint.getReturning();
        // ...
    }
}
```

#### 方式三：AspectJ 注解风格（@Aspect）

兼容标准 AspectJ 注解，适合从 Spring AOP 迁移的场景：

```java
@Aspect
public class MyAspect {

    @Pointcut("execution(* org.framework.demo.service..*Impl.process(org.framework.demo.api.Request))")
    public void process() {}

    @Before("process()")
    public void before(MutableJoinpoint<Response<String>, RuntimeException> joinpoint) throws Throwable {
        // before logic
    }

    @AfterReturning(pointcut = "process()", returning = "returning")
    public Object after(MutableJoinpoint<Response<String>, RuntimeException> joinpoint,
                        Response<String> returning) throws Throwable {
        // after returning logic
        return returning;
    }
}
```

其中方式一、方式二定义的 Advice，可以实现 `io.gemini.api.aop.advice.BeforeAdvice`、`io.gemini.api.aop.advice.AfterAdvice` 接口定义，或者声明 `@Advice.OnMethodEnter`/`@Advice.OnMethodExit` 注解定义。

### 3. 编译、部署 AspectApp

```bash
mvn package
```

运行 maven 打包命令，把切面和依赖的二进制 jar 包复制到如下对应目录：

```
gemini/
├── gemini-activator.jar
├── gemini-release/
│   ├── aspectapps/
│   │   └── gemini-demo-aspects/
│   │       ├── aspects/
│   │       ├── conf/
│   │       └── lib/
```

### 4. 启动应用时挂载 Java Agent

```bash
java -javaagent:/path/to/gemini-activator.jar \
     -jar your-application.jar
```

Gemini 会自动扫描 `aspectapps/` 目录下的切面应用，加载并激活所有 Advisor。

### 5. 条件激活

通过条件注解控制 Advisor 是否生效，避免在不满足条件的环境中产生切面匹配开销：

```java
// 仅在 Bootstrap ClassLoader 环境下激活（用于织入 JDK 核心类）
@ConditionalOnClassLoader(isBootstrapClassLoader = true)
@PojoPointcut(pointcutClass = ThreadAdvice.class)
public class ThreadAdvice extends Advice.AbstractBeforeAfter<Void, RuntimeException>
        implements Pointcut {

    @Override
    public ElementMatcher<TypeDescription> getTypeMatcher() {
        return named("java.lang.Thread");
    }

    @Override
    public ElementMatcher<MethodDescription> getMethodMatcher() {
        return named("start");
    }
    // ...
}
```

其他条件注解：
- `@ConditionalOnType(typeExpression = "com.example.SomeClass")` — 目标类加载器中存在指定类型时激活
- `@ConditionalOnMethod(methodExpression = "...")` — 目标类加载器中存在指定方法时激活
- `@ConditionalOnField(fieldExpression = "...")` — 目标类加载器中存在指定字段时激活

### 6. 高级特性

**循环调用保护**（防止 Advice 内部触发同一 Joinpoint 的无限递归）：

```java
@EnableCircularityBreaker
@ExprPointcut(pointcutExpression = "execution(* com.example..*(..))")
public class MyAdvice extends Advice.AbstractBeforeAfter<Object, RuntimeException> {
    // ...
}
```

**Per-Instance Advice**（为每个目标对象实例创建独立的 Advice 实例）：

```java
@EnablePerInstance
@ExprPointcut(pointcutExpression = "execution(* com.example..*(..))")
public class StatefulAdvice extends Advice.AbstractBeforeAfter<Object, RuntimeException> {
    private int callCount = 0; // 每个目标实例独立计数
    // ...
}
```

**自定义 Advisor 名称**：

```java
@AdvisorName("my-custom-advisor")
@ExprPointcut(pointcutExpression = "execution(* com.example..*(..))")
public class MyAdvice extends Advice.AbstractBeforeAfter<Object, RuntimeException> {
    // ...
}
```

### 7. 配置文件

在切面应用的 `userconf/aop.properties` 中可覆盖默认配置：

```properties
# 日志级别
aop.logger.allLogLevel = INFO

# 类扫描范围（提升性能）
aop.classScanner.acceptPackages = com.example.aspects*

# 诊断模式：DISABLED | SIMPLE | DEBUG
aop.launcher.diagnosticLevel = DISABLED

# 是否共享 AspectClassLoader
aop.factory.shareAspectClassLoader = true

# 存在冲突的目标 ClassLoader（可能加载同一个类）
aop.factory.conflictTargetClassLoaders = com.example.RunnerClassLoader, com.example.RunnerClassLoader2
```

---

## 运行 Demo

### 前置条件

- JDK 8+
- Maven 3.6+

### 编译项目

```bash
# 克隆仓库
git clone https://github.com/martinliu99/gemini.git
cd gemini

# 编译所有模块
mvn clean package -DskipTests

# 编译并运行测试
mvn clean verify
```

### 运行 Demo 应用

```bash
# 进入 demo runner 目录
cd gemini-demo/gemini-demo-runner

# 使用 gemini-activator 作为 Java Agent 启动
java -javaagent:../../gemini-release/gemini-activator.jar \
     -jar target/gemini-demo-runner-*.jar
```

Demo 应用会通过自定义 ClassLoader 加载 `DemoServiceImpl`，并触发三种不同风格的 Advisor 对 `process()` 和 `process2()` 方法的拦截。

---

## 开源协议

本项目基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源。

```
Copyright © 2023 - present, the original author or authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 贡献

欢迎提交 Issue 和 Pull Request。在提交代码前，请确保：

1. 通过所有现有测试：`mvn verify`
2. 新功能附带对应的单元测试
3. 遵循现有代码风格

---

[Back to Top / 回到顶部](#gemini-aop)