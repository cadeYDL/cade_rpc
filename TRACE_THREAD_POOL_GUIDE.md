# TraceID 在多线程场景下的传递方案

## 问题背景

使用普通 `ThreadLocal` 存储 TraceID 时，如果业务代码中使用多线程（如线程池），子线程无法获取父线程的 TraceID，导致链路追踪断链。

```java
// ❌ 问题示例
String traceId = TraceContext.start();
log.info("Parent thread traceId: {}", traceId);  // 输出：1708502400000-...

executor.submit(() -> {
    String childTraceId = TraceContext.getTraceId();
    log.info("Child thread traceId: {}", childTraceId);  // 输出：null（断链！）
});
```

## 解决方案

本框架提供了三种解决方案，适用于不同场景：

---

## 方案一：InheritableThreadLocal（自动继承）

**适用场景：**
- ✅ 使用 `new Thread()` 创建线程
- ✅ 使用 `CompletableFuture` 的默认线程池
- ❌ 使用自定义线程池（不适用）

**特点：**
- 无需修改代码，自动继承
- JDK 自带，无需额外依赖
- 仅在线程创建时继承，线程池复用场景无效

**使用示例：**

```java
// 父线程
String traceId = TraceContext.start();
log.info("Parent: {}", traceId);

// 子线程（自动继承）
new Thread(() -> {
    String childTraceId = TraceContext.getTraceId();
    log.info("Child: {}", childTraceId);  // ✅ 与父线程相同
}).start();

// CompletableFuture（自动继承）
CompletableFuture.runAsync(() -> {
    String asyncTraceId = TraceContext.getTraceId();
    log.info("Async: {}", asyncTraceId);  // ✅ 与父线程相同
});
```

---

## 方案二：TraceExecutors（线程池装饰器）

**适用场景：**
- ✅ 使用自定义线程池
- ✅ 使用 `Executors.newXxx()` 创建的线程池
- ✅ 需要细粒度控制 TraceID 传递

**特点：**
- 完美支持线程池场景
- 无需引入额外依赖
- 需要显式包装 ExecutorService

### 用法 1：包装 ExecutorService

```java
// 创建线程池
ExecutorService executor = Executors.newFixedThreadPool(10);

// 包装为支持 TraceID 传递的 ExecutorService
ExecutorService tracedExecutor = TraceExecutors.wrap(executor);

// 使用包装后的线程池
String traceId = TraceContext.start();
log.info("Parent: {}", traceId);

tracedExecutor.submit(() -> {
    String childTraceId = TraceContext.getTraceId();
    log.info("Child: {}", childTraceId);  // ✅ 与父线程相同
});

tracedExecutor.execute(() -> {
    String execTraceId = TraceContext.getTraceId();
    log.info("Execute: {}", execTraceId);  // ✅ 与父线程相同
});
```

### 用法 2：包装单个任务

如果不想包装整个 ExecutorService，可以只包装单个任务：

```java
ExecutorService executor = Executors.newFixedThreadPool(10);

String traceId = TraceContext.start();

// 包装 Runnable
Runnable task = () -> {
    log.info("Task traceId: {}", TraceContext.getTraceId());
};
executor.submit(TraceExecutors.wrap(task));  // ✅ TraceID 传递

// 包装 Callable
Callable<String> callable = () -> {
    String childTraceId = TraceContext.getTraceId();
    return "Result with " + childTraceId;
};
Future<String> future = executor.submit(TraceExecutors.wrap(callable));
```

### 完整示例

```java
public class UserService {
    // 使用包装后的线程池
    private final ExecutorService executor = TraceExecutors.wrap(
        Executors.newFixedThreadPool(10)
    );

    public void processUser(User user) {
        // 主线程
        String traceId = TraceContext.getOrCreate();
        log.info("Processing user: {}", user.getId());

        // 异步处理（TraceID 自动传递）
        executor.submit(() -> {
            log.info("Async processing user: {}", user.getId());
            // 这里的日志会包含相同的 TraceID
            updateDatabase(user);
        });

        // 并行处理多个任务（TraceID 自动传递）
        List<Callable<Void>> tasks = user.getOrders().stream()
            .map(order -> (Callable<Void>) () -> {
                log.info("Processing order: {}", order.getId());
                // 每个任务都有相同的 TraceID
                processOrder(order);
                return null;
            })
            .toList();

        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## 方案三：TransmittableThreadLocal (TTL)（推荐）

**适用场景：**
- ✅ 使用自定义线程池
- ✅ 复杂的多线程场景
- ✅ 需要最优雅的解决方案

**特点：**
- 阿里巴巴开源，经过大规模生产验证
- 完美支持线程池场景
- 自动传递，无侵入
- 需要引入额外依赖

### 步骤 1：添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>transmittable-thread-local</artifactId>
    <version>2.14.5</version>
</dependency>
```

### 步骤 2：修改 TraceContext

```java
package org.cade.rpc.trace;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.slf4j.MDC;

public class TraceContext {
    private static final String MDC_TRACE_ID_KEY = "traceId";

    // 使用 TransmittableThreadLocal 替代 InheritableThreadLocal
    private static final TransmittableThreadLocal<String> TRACE_ID_HOLDER =
        new TransmittableThreadLocal<>();

    // ... 其他方法保持不变
}
```

### 步骤 3：包装线程池

#### 方式 1：使用 TtlExecutors 包装

```java
import com.alibaba.ttl.threadpool.TtlExecutors;

ExecutorService executor = Executors.newFixedThreadPool(10);
ExecutorService ttlExecutor = TtlExecutors.getTtlExecutorService(executor);

// 使用 ttlExecutor，TraceID 自动传递
ttlExecutor.submit(() -> {
    String traceId = TraceContext.getTraceId();  // ✅ 自动获取
    log.info("TraceId: {}", traceId);
});
```

#### 方式 2：使用 TtlAgent（JVM 启动参数）

最优雅的方式，无需修改代码：

```bash
java -javaagent:transmittable-thread-local-2.14.5.jar -jar your-app.jar
```

然后直接使用任何线程池，TraceID 自动传递：

```java
// 无需包装，直接使用
ExecutorService executor = Executors.newFixedThreadPool(10);

executor.submit(() -> {
    String traceId = TraceContext.getTraceId();  // ✅ 自动获取
    log.info("TraceId: {}", traceId);
});
```

---

## 方案对比

| 方案 | 适用场景 | 优点 | 缺点 | 推荐度 |
|------|---------|------|------|--------|
| **InheritableThreadLocal** | new Thread() | 无需修改代码 | 不支持线程池 | ⭐⭐⭐ |
| **TraceExecutors** | 自定义线程池 | 无额外依赖，完美支持线程池 | 需要包装 ExecutorService | ⭐⭐⭐⭐ |
| **TTL** | 复杂多线程 | 最优雅，自动传递 | 需要额外依赖 | ⭐⭐⭐⭐⭐ |

---

## 推荐实践

### 场景 1：简单应用（无线程池）

使用方案一（InheritableThreadLocal），无需修改任何代码。

### 场景 2：使用线程池（小型应用）

使用方案二（TraceExecutors），包装 ExecutorService：

```java
@Configuration
public class ExecutorConfig {
    @Bean
    public ExecutorService taskExecutor() {
        return TraceExecutors.wrap(
            Executors.newFixedThreadPool(10)
        );
    }
}
```

### 场景 3：复杂多线程（大型应用）

使用方案三（TTL），添加 JVM 参数：

```bash
java -javaagent:transmittable-thread-local-2.14.5.jar \
     -jar my-rpc-app.jar
```

---

## 常见问题

### Q1：为什么子线程的 TraceID 是 null？

**A：** 使用了线程池，但没有使用 TraceExecutors 包装或 TTL。

**解决：**
```java
// 方式 1：包装线程池
ExecutorService executor = TraceExecutors.wrap(
    Executors.newFixedThreadPool(10)
);

// 方式 2：包装任务
executor.submit(TraceExecutors.wrap(() -> {
    // 任务代码
}));
```

### Q2：使用 CompletableFuture 时 TraceID 丢失？

**A：** CompletableFuture 使用自定义线程池时需要包装。

**解决：**
```java
// 方式 1：使用 InheritableThreadLocal（默认线程池）
CompletableFuture.runAsync(() -> {
    // TraceID 自动继承
});

// 方式 2：使用包装后的线程池
ExecutorService executor = TraceExecutors.wrap(
    Executors.newFixedThreadPool(10)
);
CompletableFuture.runAsync(() -> {
    // TraceID 自动传递
}, executor);
```

### Q3：Spring @Async 注解场景如何处理？

**A：** 配置 Spring 的 TaskExecutor：

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.initialize();

        // 包装为支持 TraceID 传递的线程池
        return TraceExecutors.wrap(executor.getThreadPoolExecutor());
    }
}
```

### Q4：使用 Parallel Stream 时 TraceID 丢失？

**A：** Parallel Stream 使用 ForkJoinPool.commonPool()，建议使用 TTL 方案或自定义 ForkJoinPool。

```java
// 方式 1：使用 TTL + JVM Agent（推荐）
// 启动时添加：-javaagent:transmittable-thread-local-2.14.5.jar

// 方式 2：自定义 ForkJoinPool
ForkJoinPool customPool = new ForkJoinPool(10);
customPool.submit(() -> {
    list.parallelStream().forEach(item -> {
        // 使用 TTL 后 TraceID 自动传递
        log.info("Processing: {}", item);
    });
}).get();
```

---

## 验证方式

### 测试代码

```java
@Test
public void testTraceIdPropagation() throws Exception {
    // 开始一个新的 Trace
    String parentTraceId = TraceContext.start();
    System.out.println("Parent TraceID: " + parentTraceId);

    // 创建包装后的线程池
    ExecutorService executor = TraceExecutors.wrap(
        Executors.newFixedThreadPool(3)
    );

    // 提交多个任务
    List<Future<String>> futures = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
        final int taskId = i;
        futures.add(executor.submit(() -> {
            String childTraceId = TraceContext.getTraceId();
            System.out.println("Task " + taskId + " TraceID: " + childTraceId);
            return childTraceId;
        }));
    }

    // 验证所有任务的 TraceID 都与父线程相同
    for (Future<String> future : futures) {
        String childTraceId = future.get();
        assert parentTraceId.equals(childTraceId) : "TraceID 不一致！";
    }

    System.out.println("✅ 所有子线程的 TraceID 与父线程一致");
    executor.shutdown();
}
```

**预期输出：**
```
Parent TraceID: 1708502400000-192168001100-000001-a3f2
Task 0 TraceID: 1708502400000-192168001100-000001-a3f2
Task 1 TraceID: 1708502400000-192168001100-000001-a3f2
Task 2 TraceID: 1708502400000-192168001100-000001-a3f2
Task 3 TraceID: 1708502400000-192168001100-000001-a3f2
Task 4 TraceID: 1708502400000-192168001100-000001-a3f2
✅ 所有子线程的 TraceID 与父线程一致
```

---

## 总结

1. **默认情况**：框架已使用 `InheritableThreadLocal`，支持 `new Thread()` 场景
2. **线程池场景**：使用 `TraceExecutors.wrap()` 包装线程池
3. **复杂场景**：引入 TTL 依赖，添加 JVM Agent
4. **最佳实践**：统一使用 TTL 方案，一劳永逸

选择合适的方案，确保 TraceID 在整个调用链路中完整传递！
