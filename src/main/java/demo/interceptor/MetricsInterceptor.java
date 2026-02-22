package demo.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.interceptor.InvocationContext;
import org.cade.rpc.interceptor.impl.AfterInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能指标收集拦截器示例。
 * 演示了 AFTER 拦截器用于监控和指标收集的用法。
 */
@Slf4j(topic = "metrics_interceptor")
public class MetricsInterceptor extends AfterInterceptor {

    // 简单的内存指标存储
    private final Map<String, MethodMetrics> metricsMap = new ConcurrentHashMap<>();

    @Override
    protected Object after(InvocationContext context) throws Exception {
        String methodKey = context.getServiceName() + "." + context.getMethodName();

        MethodMetrics metrics = metricsMap.computeIfAbsent(methodKey, k -> new MethodMetrics());

        // 更新指标
        metrics.totalCalls.incrementAndGet();
        if (context.isSuccess()) {
            metrics.successCalls.incrementAndGet();
        } else {
            metrics.failedCalls.incrementAndGet();
        }
        metrics.totalDuration.addAndGet(context.getDurationMillis());

        // 每 10 次调用记录一次日志
        if (metrics.totalCalls.get() % 10 == 0) {
            long avgDuration = metrics.totalDuration.get() / metrics.totalCalls.get();
            log.info("[指标{}] 方法: {}, 总计: {}, 成功: {}, 失败: {}, 平均耗时: {}ms",
                    context.getTraceID(),
                    methodKey,
                    metrics.totalCalls.get(),
                    metrics.successCalls.get(),
                    metrics.failedCalls.get(),
                    avgDuration);
        }

        return null; // 不修改结果
    }

    @Override
    public int getOrder() {
        return 50; // 在日志拦截器之前执行
    }

    /**
     * 获取特定方法的指标。
     */
    public MethodMetrics getMetrics(String methodKey) {
        return metricsMap.get(methodKey);
    }

    /**
     * 清除所有指标。
     */
    public void clearMetrics() {
        metricsMap.clear();
    }

    /**
     * 指标数据容器。
     */
    public static class MethodMetrics {
        public final AtomicInteger totalCalls = new AtomicInteger(0);
        public final AtomicInteger successCalls = new AtomicInteger(0);
        public final AtomicInteger failedCalls = new AtomicInteger(0);
        public final AtomicLong totalDuration = new AtomicLong(0);

        public double getSuccessRate() {
            int total = totalCalls.get();
            return total == 0 ? 0.0 : (double) successCalls.get() / total * 100;
        }

        public long getAverageDuration() {
            int total = totalCalls.get();
            return total == 0 ? 0 : totalDuration.get() / total;
        }
    }
}
