package org.cade.rpc.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceContext 多线程场景测试
 */
public class TraceContextTest {

    @AfterEach
    public void cleanup() {
        TraceContext.clear();
    }

    /**
     * 测试 InheritableThreadLocal 在 new Thread() 场景下的自动继承
     */
    @Test
    public void testInheritableThreadLocal_NewThread() throws InterruptedException {
        // 父线程创建 TraceID
        String parentTraceId = TraceContext.start();
        System.out.println("Parent TraceID: " + parentTraceId);

        CountDownLatch latch = new CountDownLatch(1);
        String[] childTraceIdHolder = new String[1];

        // 子线程（自动继承）
        Thread childThread = new Thread(() -> {
            childTraceIdHolder[0] = TraceContext.getTraceId();
            System.out.println("Child TraceID: " + childTraceIdHolder[0]);
            latch.countDown();
        });
        childThread.start();
        latch.await();

        // 验证子线程继承了父线程的 TraceID
        assertEquals(parentTraceId, childTraceIdHolder[0],
                "子线程应该继承父线程的 TraceID");
    }

    /**
     * 测试普通线程池场景（未包装）- TraceID 丢失
     */
    @Test
    public void testThreadPool_WithoutWrapper() throws Exception {
        // 父线程创建 TraceID
        String parentTraceId = TraceContext.start();
        System.out.println("Parent TraceID: " + parentTraceId);

        // 使用普通线程池（未包装）
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<String> future = executor.submit(() -> {
            String childTraceId = TraceContext.getTraceId();
            System.out.println("Child TraceID (unwrapped): " + childTraceId);
            return childTraceId;
        });

        String childTraceId = future.get();

        // 验证：由于线程池复用，TraceID 可能为 null 或不一致
        System.out.println("⚠️ 警告：线程池场景下 TraceID 可能丢失");
        System.out.println("Parent: " + parentTraceId);
        System.out.println("Child: " + childTraceId);

        executor.shutdown();
    }

    /**
     * 测试 TraceExecutors 包装线程池 - TraceID 正确传递
     */
    @Test
    public void testThreadPool_WithWrapper() throws Exception {
        // 父线程创建 TraceID
        String parentTraceId = TraceContext.start();
        System.out.println("Parent TraceID: " + parentTraceId);

        // 使用 TraceExecutors 包装线程池
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
            assertEquals(parentTraceId, childTraceId,
                    "线程池中的任务应该继承父线程的 TraceID");
        }

        System.out.println("✅ 所有子线程的 TraceID 与父线程一致");
        executor.shutdown();
    }

    /**
     * 测试 TraceExecutors 包装单个任务
     */
    @Test
    public void testWrapSingleTask() throws Exception {
        String parentTraceId = TraceContext.start();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 包装 Runnable
        CountDownLatch latch1 = new CountDownLatch(1);
        String[] runnableTraceId = new String[1];

        Runnable task = () -> {
            runnableTraceId[0] = TraceContext.getTraceId();
            latch1.countDown();
        };
        executor.submit(TraceExecutors.wrap(task));
        latch1.await();

        assertEquals(parentTraceId, runnableTraceId[0],
                "包装后的 Runnable 应该继承 TraceID");

        // 包装 Callable
        Callable<String> callable = () -> TraceContext.getTraceId();
        Future<String> future = executor.submit(TraceExecutors.wrap(callable));
        String callableTraceId = future.get();

        assertEquals(parentTraceId, callableTraceId,
                "包装后的 Callable 应该继承 TraceID");

        executor.shutdown();
    }

    /**
     * 测试 CompletableFuture 场景
     */
    @Test
    public void testCompletableFuture() throws Exception {
        String parentTraceId = TraceContext.start();
        System.out.println("Parent TraceID: " + parentTraceId);

        // 使用默认线程池（ForkJoinPool.commonPool()）
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            String asyncTraceId = TraceContext.getTraceId();
            System.out.println("Async TraceID: " + asyncTraceId);
            return asyncTraceId;
        });

        String asyncTraceId = future.get();

        // InheritableThreadLocal 在 CompletableFuture 默认线程池中也能工作
        assertEquals(parentTraceId, asyncTraceId,
                "CompletableFuture 应该继承 TraceID");
    }

    /**
     * 测试 CompletableFuture 使用自定义线程池
     */
    @Test
    public void testCompletableFutureWithCustomExecutor() throws Exception {
        String parentTraceId = TraceContext.start();

        // 使用包装后的线程池
        ExecutorService executor = TraceExecutors.wrap(
                Executors.newFixedThreadPool(2)
        );

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            return TraceContext.getTraceId();
        }, executor);

        String asyncTraceId = future.get();
        assertEquals(parentTraceId, asyncTraceId,
                "CompletableFuture 使用包装线程池应该继承 TraceID");

        executor.shutdown();
    }

    /**
     * 测试嵌套异步调用
     */
    @Test
    public void testNestedAsyncCalls() throws Exception {
        String rootTraceId = TraceContext.start();
        System.out.println("Root TraceID: " + rootTraceId);

        ExecutorService executor = TraceExecutors.wrap(
                Executors.newFixedThreadPool(3)
        );

        // 第一层异步
        Future<String> level1Future = executor.submit(() -> {
            String level1TraceId = TraceContext.getTraceId();
            System.out.println("Level 1 TraceID: " + level1TraceId);

            // 第二层异步
            Future<String> level2Future = executor.submit(() -> {
                String level2TraceId = TraceContext.getTraceId();
                System.out.println("Level 2 TraceID: " + level2TraceId);

                // 第三层异步
                Future<String> level3Future = executor.submit(() -> {
                    String level3TraceId = TraceContext.getTraceId();
                    System.out.println("Level 3 TraceID: " + level3TraceId);
                    return level3TraceId;
                });

                try {
                    return level3Future.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            try {
                return level2Future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        String finalTraceId = level1Future.get();

        // 验证所有层级的 TraceID 都相同
        assertEquals(rootTraceId, finalTraceId,
                "嵌套异步调用应该保持相同的 TraceID");

        executor.shutdown();
    }

    /**
     * 测试 TraceID 生成的唯一性
     */
    @Test
    public void testTraceIdUniqueness() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        Set<String> traceIds = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(100);

        // 并发生成 100 个 TraceID
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                String traceId = TraceIdGenerator.generate();
                traceIds.add(traceId);
                latch.countDown();
            });
        }

        latch.await();

        // 验证所有 TraceID 都是唯一的
        assertEquals(100, traceIds.size(),
                "应该生成 100 个唯一的 TraceID");

        System.out.println("✅ 成功生成 100 个唯一的 TraceID");
        executor.shutdown();
    }

    /**
     * 测试 TraceID 格式验证
     */
    @Test
    public void testTraceIdValidation() {
        String traceId = TraceIdGenerator.generate();
        System.out.println("Generated TraceID: " + traceId);

        // 验证格式
        assertTrue(TraceIdGenerator.isValid(traceId),
                "生成的 TraceID 应该是有效的");

        // 验证无效的 TraceID
        assertFalse(TraceIdGenerator.isValid(null));
        assertFalse(TraceIdGenerator.isValid(""));
        assertFalse(TraceIdGenerator.isValid("invalid-trace-id"));
        assertFalse(TraceIdGenerator.isValid("123-456-789"));

        // 提取时间戳
        long timestamp = TraceIdGenerator.extractTimestamp(traceId);
        assertTrue(timestamp > 0, "应该能够提取时间戳");
        System.out.println("Extracted timestamp: " + timestamp);
    }

    /**
     * 测试 TraceContext 清理
     */
    @Test
    public void testTraceContextCleanup() {
        // 设置 TraceID
        String traceId = TraceContext.start();
        assertNotNull(TraceContext.getTraceId());
        assertTrue(TraceContext.exists());

        // 清理
        TraceContext.clear();
        assertNull(TraceContext.getTraceId());
        assertFalse(TraceContext.exists());
    }
}
