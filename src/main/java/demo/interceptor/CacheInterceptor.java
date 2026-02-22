package demo.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.interceptor.InvocationContext;
import org.cade.rpc.interceptor.ShortCircuitResult;
import org.cade.rpc.interceptor.impl.AroundInterceptor;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存拦截器示例，缓存方法结果。
 * 演示了 AROUND 拦截器的使用，完全控制执行流程。
 */
@Slf4j(topic = "cache_interceptor")
public class CacheInterceptor extends AroundInterceptor {

    // 简单的内存缓存
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    @Override
    protected Object around(InvocationContext context, Callable<Object> proceed) throws Exception {
        String cacheKey = buildCacheKey(context);

        // 执行前检查缓存
        Object cached = cache.get(cacheKey);
        if (cached != null) {
            log.info("[缓存{}] 缓存命中，方法: {}.{}, key: {}",
                    context.getTraceID(),
                    context.getServiceName(),
                    context.getMethodName(),
                    cacheKey);
            return ShortCircuitResult.of(cached); // 返回缓存，跳过执行
        }

        log.info("[缓存{}] 缓存未命中，方法: {}.{}, key: {}",
                context.getTraceID(),
                context.getServiceName(),
                context.getMethodName(),
                cacheKey);

        // 执行方法
        Object result = proceed.call();

        // 执行后存储到缓存（仅缓存成功结果）
        if (!(result instanceof ShortCircuitResult)) {
            cache.put(cacheKey, result);
            log.info("[缓存{}] 已缓存结果，方法: {}.{}, key: {}",
                    context.getTraceID(),
                    context.getServiceName(),
                    context.getMethodName(),
                    cacheKey);
        }

        return result;
    }

    @Override
    public int getOrder() {
        return 10; // 尽早执行以避免不必要的工作
    }

    /**
     * 根据方法上下文构建缓存键。
     */
    private String buildCacheKey(InvocationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getServiceName())
                .append(".")
                .append(context.getMethodName())
                .append("(");

        Object[] args = context.getArguments();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(args[i]);
            }
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * 清除缓存。
     */
    public void clearCache() {
        cache.clear();
        log.info("[缓存] 缓存已清空");
    }

    /**
     * 获取缓存大小。
     */
    public int getCacheSize() {
        return cache.size();
    }
}
