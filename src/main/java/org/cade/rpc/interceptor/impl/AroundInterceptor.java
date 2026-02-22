package org.cade.rpc.interceptor.impl;

import org.cade.rpc.interceptor.Interceptor;
import org.cade.rpc.interceptor.InvocationContext;
import org.cade.rpc.interceptor.ShortCircuitResult;

import java.util.concurrent.Callable;

/**
 * Around 拦截器的抽象基类。
 * 包裹方法执行，完全控制执行流程。
 *
 * <p>Around 拦截器提供最大的灵活性：
 * <ul>
 *   <li>在方法前后执行逻辑</li>
 *   <li>决定是否继续执行方法</li>
 *   <li>捕获和处理异常</li>
 *   <li>修改参数和返回值</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>缓存（执行前检查缓存，执行后存储结果）</li>
 *   <li>重试逻辑（捕获异常，带退避重试）</li>
 *   <li>事务管理（执行前开启，执行后提交/回滚）</li>
 *   <li>请求/响应转换</li>
 *   <li>熔断器模式</li>
 * </ul>
 *
 * <p>实现示例：
 * <pre>
 * public class CacheInterceptor extends AroundInterceptor {
 *     protected Object around(InvocationContext context, Callable&lt;Object&gt; proceed) throws Exception {
 *         String cacheKey = buildCacheKey(context);
 *
 *         // 执行前检查缓存
 *         Object cached = cache.get(cacheKey);
 *         if (cached != null) {
 *             return ShortCircuitResult.of(cached); // 返回缓存，跳过执行
 *         }
 *
 *         // 执行方法
 *         Object result = proceed.call();
 *
 *         // 执行后存储到缓存
 *         cache.put(cacheKey, result);
 *
 *         return result;
 *     }
 * }
 * </pre>
 */
public abstract class AroundInterceptor implements Interceptor {

    /**
     * 获取拦截器类型（始终为 AROUND）。
     */
    @Override
    public final InterceptorType getType() {
        return InterceptorType.AROUND;
    }

    /**
     * 拦截方法调用（最终实现）。
     * 从上下文提取 proceed 并委托给 {@link #around(InvocationContext, Callable)}。
     */
    @Override
    @SuppressWarnings("unchecked")
    public final Object intercept(InvocationContext context) throws Exception {
        // 拦截器链在上下文中存储下一个 callable
        Callable<Object> next = (Callable<Object>) context.getAttribute("next");
        if (next == null) {
            throw new IllegalStateException("Around 拦截器需要上下文中包含 'next'");
        }
        return around(context, next);
    }

    /**
     * 执行 around 逻辑。
     * 完全控制方法执行。
     *
     * <p>必须调用 {@code proceed.call()} 才能继续执行链。
     * 可以选择不调用 proceed 来阻断执行。
     *
     * <p>常见模式示例：
     * <pre>
     * // 模式 1: 前后执行
     * protected Object around(InvocationContext context, Callable&lt;Object&gt; proceed) throws Exception {
     *     // 前置逻辑
     *     log.info("方法执行前");
     *
     *     // 执行
     *     Object result = proceed.call();
     *
     *     // 后置逻辑
     *     log.info("方法执行后");
     *     return result;
     * }
     *
     * // 模式 2: 条件执行
     * protected Object around(InvocationContext context, Callable&lt;Object&gt; proceed) throws Exception {
     *     if (shouldSkip(context)) {
     *         return ShortCircuitResult.of(defaultValue);
     *     }
     *     return proceed.call();
     * }
     *
     * // 模式 3: 异常处理
     * protected Object around(InvocationContext context, Callable&lt;Object&gt; proceed) throws Exception {
     *     try {
     *         return proceed.call();
     *     } catch (Exception e) {
     *         log.error("方法失败", e);
     *         return ShortCircuitResult.of(fallbackValue);
     *     }
     * }
     *
     * // 模式 4: 重试逻辑
     * protected Object around(InvocationContext context, Callable&lt;Object&gt; proceed) throws Exception {
     *     int attempts = 0;
     *     while (attempts &lt; maxRetries) {
     *         try {
     *             return proceed.call();
     *         } catch (Exception e) {
     *             attempts++;
     *             if (attempts &gt;= maxRetries) throw e;
     *             Thread.sleep(100 * attempts);
     *         }
     *     }
     *     throw new IllegalStateException("不应到达此处");
     * }
     * </pre>
     *
     * @param context 调用上下文
     * @param proceed 继续执行的 Callable（下一个拦截器或实际方法）
     * @return 方法结果或 {@link ShortCircuitResult} 阻断执行
     * @throws Exception 如果执行失败
     */
    protected abstract Object around(InvocationContext context, Callable<Object> proceed) throws Exception;
}
