# TraceID 传递快速参考

## 🎯 核心问题

使用线程池时，子线程无法获取父线程的 TraceID，导致链路追踪断链。

## ✅ 解决方案速查表

| 场景 | 解决方案 | 代码示例 |
|------|---------|---------|
| **new Thread()** | 自动继承（无需修改） | `new Thread(() -> { /* TraceID 自动继承 */ }).start();` |
| **CompletableFuture** | 自动继承（默认线程池） | `CompletableFuture.runAsync(() -> { /* TraceID 自动继承 */ });` |
| **自定义线程池** | 使用 TraceExecutors 包装 | `ExecutorService executor = TraceExecutors.wrap(Executors.newFixedThreadPool(10));` |
| **Spring @Async** | 配置 TaskExecutor | `return TraceExecutors.wrap(executor.getThreadPoolExecutor());` |
| **单个任务** | 包装 Runnable/Callable | `executor.submit(TraceExecutors.wrap(task));` |
| **复杂场景** | 使用 TTL + JVM Agent | `java -javaagent:ttl.jar -jar app.jar` |

---

## 📦 方案一：包装线程池（推荐）

### 步骤 1：创建线程池时包装

```java
import org.cade.rpc.trace.TraceExecutors;

// 创建并包装线程池
ExecutorService executor = TraceExecutors.wrap(
    Executors.newFixedThreadPool(10)
);

// 正常使用，TraceID 自动传递
executor.submit(() -> {
    String traceId = TraceContext.getTraceId();
    log.info("TraceID: {}", traceId);  // ✅ 与父线程相同
});
```

### 步骤 2：在 Spring 中配置

```java
@Configuration
public class ExecutorConfig {
    @Bean("taskExecutor")
    public ExecutorService taskExecutor() {
        return TraceExecutors.wrap(
            Executors.newFixedThreadPool(20)
        );
    }
}
```

---

## 📦 方案二：包装单个任务

如果不想包装整个线程池，可以只包装单个任务：

```java
ExecutorService executor = Executors.newFixedThreadPool(10);

// 包装 Runnable
executor.submit(TraceExecutors.wrap(() -> {
    log.info("TraceID: {}", TraceContext.getTraceId());  // ✅ 正确
}));

// 包装 Callable
Future<String> future = executor.submit(TraceExecutors.wrap(() -> {
    return TraceContext.getTraceId();  // ✅ 正确
}));
```

---

## 📦 方案三：TTL（终极方案）

### 步骤 1：添加依赖

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>transmittable-thread-local</artifactId>
    <version>2.14.5</version>
</dependency>
```

### 步骤 2：修改 TraceContext

```java
import com.alibaba.ttl.TransmittableThreadLocal;

public class TraceContext {
    // 将 InheritableThreadLocal 改为 TransmittableThreadLocal
    private static final TransmittableThreadLocal<String> TRACE_ID_HOLDER =
        new TransmittableThreadLocal<>();

    // ... 其他代码不变
}
```

### 步骤 3a：包装线程池

```java
import com.alibaba.ttl.threadpool.TtlExecutors;

ExecutorService executor = Executors.newFixedThreadPool(10);
ExecutorService ttlExecutor = TtlExecutors.getTtlExecutorService(executor);

// 使用 ttlExecutor，TraceID 自动传递
```

### 步骤 3b：或使用 JVM Agent（推荐）

```bash
java -javaagent:transmittable-thread-local-2.14.5.jar \
     -jar your-app.jar
```

然后无需任何代码修改，所有线程池自动支持 TraceID 传递！

---

## 🔍 验证是否生效

### 测试代码

```java
// 父线程
String parentTraceId = TraceContext.start();
System.out.println("Parent: " + parentTraceId);

// 子线程
executor.submit(() -> {
    String childTraceId = TraceContext.getTraceId();
    System.out.println("Child: " + childTraceId);

    // 检查是否相同
    if (parentTraceId.equals(childTraceId)) {
        System.out.println("✅ TraceID 传递成功");
    } else {
        System.out.println("❌ TraceID 传递失败");
    }
});
```

### 查看日志

配置 logback.xml：

```xml
<pattern>%d [%X{traceId}] %-5level %logger - %msg%n</pattern>
```

查看日志输出：

```
2024-02-21 15:30:45.123 [1708502400000-192168001100-000001-a3f2] INFO Parent thread
2024-02-21 15:30:45.456 [1708502400000-192168001100-000001-a3f2] INFO Child thread
```

如果 TraceID 相同，说明传递成功！

---

## ⚠️ 常见错误

### ❌ 错误 1：直接使用线程池（未包装）

```java
ExecutorService executor = Executors.newFixedThreadPool(10);
executor.submit(() -> {
    String traceId = TraceContext.getTraceId();  // ❌ 可能为 null
});
```

**修复：**
```java
ExecutorService executor = TraceExecutors.wrap(
    Executors.newFixedThreadPool(10)
);
```

### ❌ 错误 2：CompletableFuture 使用自定义线程池（未包装）

```java
ExecutorService executor = Executors.newFixedThreadPool(10);
CompletableFuture.runAsync(() -> {
    // ❌ TraceID 丢失
}, executor);
```

**修复：**
```java
ExecutorService executor = TraceExecutors.wrap(
    Executors.newFixedThreadPool(10)
);
CompletableFuture.runAsync(() -> {
    // ✅ TraceID 正确
}, executor);
```

### ❌ 错误 3：忘记清理 TraceContext

```java
TraceContext.start();
// 业务逻辑
// ❌ 忘记清理，导致内存泄漏
```

**修复：**
```java
try {
    TraceContext.start();
    // 业务逻辑
} finally {
    TraceContext.clear();  // ✅ 确保清理
}
```

---

## 📊 方案选择建议

| 应用规模 | 推荐方案 | 理由 |
|---------|---------|------|
| 小型应用 | 方案一（TraceExecutors） | 简单，无额外依赖 |
| 中型应用 | 方案一（TraceExecutors） | 适用大部分场景 |
| 大型应用 | 方案三（TTL + Agent） | 一劳永逸，无侵入 |
| 开源框架 | 方案一（TraceExecutors） | 避免强制依赖 TTL |

---

## 🚀 快速集成步骤

### 1. 基础使用（无线程池）

✅ 无需任何配置，开箱即用

### 2. 有线程池场景

```java
// 步骤 1：导入
import org.cade.rpc.trace.TraceExecutors;

// 步骤 2：包装线程池
ExecutorService executor = TraceExecutors.wrap(yourExecutor);

// 步骤 3：正常使用
executor.submit(() -> {
    // TraceID 自动传递
});
```

### 3. Spring Boot 集成

```java
@Configuration
public class TraceConfig {
    @Bean
    public ExecutorService taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.initialize();

        return TraceExecutors.wrap(executor.getThreadPoolExecutor());
    }
}
```

---

## 📚 相关文档

- 完整使用指南：[TRACE_GUIDE.md](TRACE_GUIDE.md)
- 多线程详解：[TRACE_THREAD_POOL_GUIDE.md](TRACE_THREAD_POOL_GUIDE.md)
- API 文档：查看 `TraceContext` 和 `TraceExecutors` JavaDoc

---

## 🆘 问题排查

### 子线程 TraceID 为 null？

1. 检查是否使用了线程池
2. 检查是否包装了线程池或任务
3. 查看日志确认 TraceID 格式

### TraceID 不一致？

1. 检查是否在多个地方调用了 `TraceContext.start()`
2. 检查是否正确清理了 `TraceContext`

### 需要帮助？

查看测试用例：`src/test/java/org/cade/rpc/trace/TraceContextTest.java`
