package org.cade.rpc.trace;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * 支持 TraceID 传递的 ExecutorService 装饰器
 * <p>
 * 用于装饰现有的 ExecutorService，确保提交的任务能够自动继承父线程的 TraceID
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 * ExecutorService executor = Executors.newFixedThreadPool(10);
 * ExecutorService tracedExecutor = TraceExecutors.wrap(executor);
 *
 * // 提交任务时，TraceID 会自动传递到子线程
 * tracedExecutor.submit(() -> {
 *     // 子线程可以获取父线程的 TraceID
 *     String traceId = TraceContext.getTraceId();
 *     log.info("Processing with traceId: {}", traceId);
 * });
 * }</pre>
 */
public class TraceExecutors {

    /**
     * 包装 ExecutorService，使其支持 TraceID 自动传递
     *
     * @param executor 原始的 ExecutorService
     * @return 支持 TraceID 传递的 ExecutorService
     */
    public static ExecutorService wrap(ExecutorService executor) {
        return new TraceExecutorService(executor);
    }

    /**
     * 包装 Runnable，使其在执行时继承父线程的 TraceID
     *
     * @param runnable 原始的 Runnable
     * @return 支持 TraceID 传递的 Runnable
     */
    public static Runnable wrap(Runnable runnable) {
        String parentTraceId = TraceContext.getTraceId();
        return () -> {
            String oldTraceId = TraceContext.getTraceId();
            try {
                if (parentTraceId != null) {
                    TraceContext.setTraceId(parentTraceId);
                }
                runnable.run();
            } finally {
                if (oldTraceId != null) {
                    TraceContext.setTraceId(oldTraceId);
                } else {
                    TraceContext.clear();
                }
            }
        };
    }

    /**
     * 包装 Callable，使其在执行时继承父线程的 TraceID
     *
     * @param callable 原始的 Callable
     * @param <V>      返回值类型
     * @return 支持 TraceID 传递的 Callable
     */
    public static <V> Callable<V> wrap(Callable<V> callable) {
        String parentTraceId = TraceContext.getTraceId();
        return () -> {
            String oldTraceId = TraceContext.getTraceId();
            try {
                if (parentTraceId != null) {
                    TraceContext.setTraceId(parentTraceId);
                }
                return callable.call();
            } finally {
                if (oldTraceId != null) {
                    TraceContext.setTraceId(oldTraceId);
                } else {
                    TraceContext.clear();
                }
            }
        };
    }

    /**
     * 支持 TraceID 传递的 ExecutorService 实现
     */
    private static class TraceExecutorService implements ExecutorService {
        private final ExecutorService delegate;

        public TraceExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(wrap(command));
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(wrap(task));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegate.submit(wrap(task), result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.submit(wrap(task));
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return delegate.invokeAll(wrapCallables(tasks));
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(wrapCallables(tasks), timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return delegate.invokeAny(wrapCallables(tasks));
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.invokeAny(wrapCallables(tasks), timeout, unit);
        }

        private <T> Collection<Callable<T>> wrapCallables(Collection<? extends Callable<T>> tasks) {
            return tasks.stream().map(TraceExecutors::wrap).toList();
        }
    }
}
