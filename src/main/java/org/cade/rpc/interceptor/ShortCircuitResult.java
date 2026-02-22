package org.cade.rpc.interceptor;

/**
 * 特殊结果对象，用于通知拦截器链应该阻断执行。
 * 当拦截器返回此对象时，链的执行将被中止，包装的值将作为最终结果返回。
 *
 * <p>这不是基于异常的机制，而是正常的控制流程。
 * 阻断通常用于：
 * <ul>
 *   <li>权限检查（返回错误响应而不执行方法）</li>
 *   <li>缓存（返回缓存结果而不进行 RPC 调用）</li>
 *   <li>熔断器（当服务不可用时返回降级值）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * public class CacheInterceptor extends BeforeInterceptor {
 *     protected Object before(InvocationContext context) {
 *         Object cached = cache.get(context.getMethodName());
 *         if (cached != null) {
 *             return ShortCircuitResult.of(cached); // 返回缓存，跳过方法
 *         }
 *         return null; // 继续执行
 *     }
 * }
 * </pre>
 */
public class ShortCircuitResult {

    private final Object value;

    private ShortCircuitResult(Object value) {
        this.value = value;
    }

    /**
     * 获取要返回的包装值。
     */
    public Object getValue() {
        return value;
    }

    /**
     * 创建包含值的 ShortCircuitResult。
     *
     * @param value 要返回的值（可以为 null）
     * @return 包装该值的 ShortCircuitResult
     */
    public static ShortCircuitResult of(Object value) {
        return new ShortCircuitResult(value);
    }

    /**
     * 创建包含 null 值的 ShortCircuitResult。
     *
     * @return 包装 null 的 ShortCircuitResult
     */
    public static ShortCircuitResult empty() {
        return new ShortCircuitResult(null);
    }

    @Override
    public String toString() {
        return "ShortCircuitResult{value=" + value + '}';
    }
}
