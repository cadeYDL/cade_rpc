# 分布式链路追踪（Trace）使用指南

## 概述

本 RPC 框架已集成分布式链路追踪功能，支持全链路的请求追踪，帮助你快速定位问题、分析性能瓶颈。

## 核心特性

- ✅ **全局唯一 TraceID**：基于时间戳、主机标识、序列号和随机数生成
- ✅ **无侵入式设计**：无需修改业务代码，自动传递和记录
- ✅ **线程安全**：基于 ThreadLocal 实现线程隔离
- ✅ **日志集成**：自动集成 SLF4J MDC，所有日志自动包含 TraceID
- ✅ **并发安全**：支持高并发场景，无锁设计

## TraceID 格式

```
格式：timestamp-hostId-sequence-random
示例：1708502400000-192168001100-000001-a3f2

组成部分：
- timestamp: 当前时间戳（毫秒）- 13位
- hostId: 主机标识（IP 地址数字形式）- 12位
- sequence: 递增序列号（每秒重置）- 6位
- random: 随机数（十六进制）- 4位

总长度：约 40-45 个字符
```

## 工作原理

### 1. Consumer 端（客户端）

```
用户调用
  ↓
ConsumerInvocationHandler.invoke()
  ↓
buildRequest()
  ├─ TraceContext.getOrCreate()  ← 从 ThreadLocal 获取或生成新 TraceID
  └─ request.setTraceId(traceId) ← 设置到请求中
  ↓
发送请求到 Provider
```

### 2. Provider 端（服务端）

```
接收请求
  ↓
ProviderHandler.InvokeTask.run()
  ↓
初始化 TraceContext
  ├─ 从 request.getTraceId() 获取
  ├─ 如果不存在，生成新的 TraceID
  └─ TraceContext.setTraceId(traceId) ← 设置到 ThreadLocal
  ↓
执行业务逻辑（所有日志自动包含 TraceID）
  ↓
response.setTraceId(traceId) ← 响应中返回 TraceID
  ↓
TraceContext.clear() ← 清理 ThreadLocal
```

## 使用方式

### 方式一：自动模式（推荐）

**无需任何代码修改，框架自动处理**

```java
// Consumer 端
Add addService = factory.getConsumerProxy(Add.class);
int result = addService.add(10, 20);
// TraceID 会自动生成并传递到 Provider
```

框架会自动：
1. Consumer 调用时生成 TraceID
2. 将 TraceID 传递到 Provider
3. Provider 接收并使用该 TraceID
4. 所有日志自动包含 TraceID

### 方式二：手动模式（高级用法）

**适用于需要在业务代码中显式控制 TraceID 的场景**

```java
// 开始一个新的 Trace
String traceId = TraceContext.start();
try {
    // 业务逻辑
    Add addService = factory.getConsumerProxy(Add.class);
    int result = addService.add(10, 20);

    // 所有日志都会包含这个 TraceID
    log.info("Result: {}", result);
} finally {
    // 清理 TraceContext（重要！）
    TraceContext.clear();
}
```

### 方式三：继承父线程的 TraceID

**适用于异步调用场景**

```java
// 主线程
String parentTraceId = TraceContext.getOrCreate();

// 子线程
executor.submit(() -> {
    // 继承父线程的 TraceID
    TraceContext.inherit(parentTraceId);
    try {
        // 业务逻辑
        // 使用相同的 TraceID
    } finally {
        TraceContext.clear();
    }
});
```

## 日志配置

### Logback 配置（推荐）

在 `logback.xml` 中配置 Pattern，添加 `%X{traceId}`：

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

**日志输出示例：**
```
2024-02-21 15:30:45.123 [main] [1708502400000-192168001100-000001-a3f2] INFO  o.c.rpc.provider.ProviderHandler - Request processed
2024-02-21 15:30:45.456 [pool-1-thread-1] [1708502400000-192168001100-000001-a3f2] INFO  o.c.rpc.service.AddImpl - Calculating sum
```

### Log4j2 配置

```xml
<Configuration>
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] [%X{traceId}] %-5level %logger{36} - %msg%n"/>
        </Console>
    </Appenders>
</Configuration>
```

## API 参考

### TraceContext

```java
// 开始一个新的 Trace
String traceId = TraceContext.start();

// 获取当前 TraceID（可能为 null）
String traceId = TraceContext.getTraceId();

// 获取或创建 TraceID（保证非 null）
String traceId = TraceContext.getOrCreate();

// 设置 TraceID
TraceContext.setTraceId("custom-trace-id");

// 判断是否存在 TraceID
boolean exists = TraceContext.exists();

// 继承父线程的 TraceID
TraceContext.inherit(parentTraceId);

// 清理 TraceContext（重要！）
TraceContext.clear();
```

### TraceIdGenerator

```java
// 生成新的 TraceID
String traceId = TraceIdGenerator.generate();

// 验证 TraceID 格式
boolean valid = TraceIdGenerator.isValid(traceId);

// 从 TraceID 中提取时间戳
long timestamp = TraceIdGenerator.extractTimestamp(traceId);
```

## 最佳实践

### 1. 及时清理 TraceContext

**❌ 错误示例：**
```java
TraceContext.start();
// 业务逻辑
// 忘记清理，导致 ThreadLocal 内存泄漏
```

**✅ 正确示例：**
```java
TraceContext.start();
try {
    // 业务逻辑
} finally {
    TraceContext.clear();  // 确保清理
}
```

### 2. 异步调用场景

**❌ 错误示例：**
```java
// 主线程
executor.submit(() -> {
    // 子线程无法获取 TraceID
    log.info("Processing");  // TraceID 为空
});
```

**✅ 正确示例：**
```java
// 主线程
String parentTraceId = TraceContext.getOrCreate();

executor.submit(() -> {
    TraceContext.inherit(parentTraceId);
    try {
        log.info("Processing");  // TraceID 正确传递
    } finally {
        TraceContext.clear();
    }
});
```

### 3. 长时间运行的任务

```java
// 对于长时间运行的任务，保持 TraceContext 活跃
TraceContext.start();
try {
    while (running) {
        // 业务逻辑
        log.info("Processing...");  // 持续使用同一个 TraceID
    }
} finally {
    TraceContext.clear();
}
```

## 性能影响

### TraceID 生成性能

- **单次生成耗时**：< 1 微秒
- **并发性能**：支持每秒百万级生成
- **内存占用**：每个 TraceID 约 50 字节

### ThreadLocal 影响

- **空间复杂度**：O(线程数)
- **时间复杂度**：O(1)
- **内存泄漏风险**：已通过 `clear()` 方法避免

## 故障排查

### 问题 1：日志中没有 TraceID

**原因：** Logback 配置中未添加 `%X{traceId}`

**解决：** 修改 Pattern 配置
```xml
<pattern>%d [%X{traceId}] %-5level %logger - %msg%n</pattern>
```

### 问题 2：TraceID 在异步线程中丢失

**原因：** 子线程没有继承父线程的 TraceID

**解决：** 使用 `TraceContext.inherit(parentTraceId)`

```java
String parentTraceId = TraceContext.getTraceId();
executor.submit(() -> {
    TraceContext.inherit(parentTraceId);
    try {
        // 业务逻辑
    } finally {
        TraceContext.clear();
    }
});
```

### 问题 3：TraceID 在不同请求间混乱

**原因：** 忘记调用 `TraceContext.clear()`

**解决：** 在 finally 块中清理
```java
try {
    // 处理请求
} finally {
    TraceContext.clear();
}
```

## 高级特性

### 自定义 TraceID 生成策略

如果默认的 TraceID 格式不满足需求，可以继承 `TraceIdGenerator` 实现自定义策略：

```java
public class CustomTraceIdGenerator extends TraceIdGenerator {
    public static String generate() {
        // 自定义生成逻辑
        return UUID.randomUUID().toString();
    }
}
```

### 集成分布式追踪系统

可以将 TraceID 集成到 Zipkin、Jaeger、SkyWalking 等分布式追踪系统：

```java
// 在 TraceContext 中集成
TraceContext.start();
String traceId = TraceContext.getTraceId();

// 上报到 Zipkin
Span span = tracer.nextSpan().name("rpc-call");
span.tag("traceId", traceId);
span.start();
try {
    // 业务逻辑
} finally {
    span.finish();
    TraceContext.clear();
}
```

## 示例场景

### 场景 1：单次 RPC 调用

```java
// Consumer
Add addService = factory.getConsumerProxy(Add.class);
int result = addService.add(10, 20);
// 自动生成 TraceID：1708502400000-192168001100-000001-a3f2

// Provider 日志
// [1708502400000-192168001100-000001-a3f2] INFO - Received request: add(10, 20)
// [1708502400000-192168001100-000001-a3f2] INFO - Result: 30
```

### 场景 2：链式 RPC 调用

```java
// Service A
TraceContext.start();  // 生成 TraceID
try {
    // 调用 Service B
    serviceB.process();  // TraceID 自动传递
} finally {
    TraceContext.clear();
}

// Service B
// 收到请求，使用相同的 TraceID
log.info("Processing");  // 日志包含相同的 TraceID

// Service B 调用 Service C
serviceC.execute();  // TraceID 继续传递

// Service C
// 收到请求，使用相同的 TraceID
log.info("Executing");  // 日志包含相同的 TraceID
```

**完整链路日志：**
```
[1708502400000-192168001100-000001-a3f2] INFO ServiceA - Starting process
[1708502400000-192168001100-000001-a3f2] INFO ServiceB - Processing
[1708502400000-192168001100-000001-a3f2] INFO ServiceC - Executing
[1708502400000-192168001100-000001-a3f2] INFO ServiceB - Process completed
[1708502400000-192168001100-000001-a3f2] INFO ServiceA - Process finished
```

通过相同的 TraceID，可以轻松追踪整个调用链路！

## 总结

分布式链路追踪功能已完全集成到 RPC 框架中，无需修改业务代码即可使用。只需配置日志格式，即可在日志中看到完整的调用链路。

**关键要点：**
1. ✅ 无侵入式设计，自动传递 TraceID
2. ✅ 线程安全，支持高并发
3. ✅ 集成 SLF4J MDC，日志自动包含 TraceID
4. ✅ 记得在 finally 块中调用 `TraceContext.clear()`
5. ✅ 异步场景使用 `TraceContext.inherit()`
