package org.cade.rpc.interceptor.impl;

import org.cade.rpc.interceptor.Interceptor;
import org.cade.rpc.interceptor.InvocationContext;
import org.cade.rpc.interceptor.ShortCircuitResult;

/**
 * After 拦截器的抽象基类。
 * 在方法执行后执行（成功或失败），可以访问结果、异常和执行时间。
 *
 * <p>使用场景：
 * <ul>
 *   <li>执行结果日志记录</li>
 *   <li>性能监控</li>
 *   <li>响应转换</li>
 *   <li>错误处理和恢复</li>
 *   <li>指标收集</li>
 * </ul>
 *
 * <p>After 拦截器无论方法成功还是失败都会执行，
 * 非常适合用于清理和监控任务。
 *
 * <p>实现示例：
 * <pre>
 * public class LoggingInterceptor extends AfterInterceptor {
 *     protected Object after(InvocationContext context) throws Exception {
 *         log.info("方法: {}.{}, 耗时: {}ms, 成功: {}",
 *             context.getServiceName(),
 *             context.getMethodName(),
 *             context.getDurationMillis(),
 *             context.isSuccess());
 *
 *         if (context.hasException()) {
 *             log.error("异常: ", context.getException());
 *         }
 *
 *         return null; // 不修改结果
 *     }
 * }
 * </pre>
 */
public abstract class AfterInterceptor implements Interceptor {

    /**
     * 获取拦截器类型（始终为 AFTER）。
     */
    @Override
    public final InterceptorType getType() {
        return InterceptorType.AFTER;
    }

    /**
     * 拦截方法调用（最终实现）。
     * 委托给 {@link #after(InvocationContext)}。
     */
    @Override
    public final Object intercept(InvocationContext context) throws Exception {
        return after(context);
    }

    /**
     * 执行 after 逻辑。
     * 在方法执行后调用（成功或失败）。
     *
     * <p>上下文提供以下访问：
     * <ul>
     *   <li>{@link InvocationContext#getResult()} - 方法结果（如果成功）</li>
     *   <li>{@link InvocationContext#getException()} - 异常（如果失败）</li>
     *   <li>{@link InvocationContext#getDurationMillis()} - 执行时长</li>
     *   <li>{@link InvocationContext#isSuccess()} - 是否执行成功</li>
     * </ul>
     *
     * @param context 调用上下文（可以访问结果、异常、时长）
     * @return 返回 {@link ShortCircuitResult} 可覆盖结果/异常，返回 null 则保持原值
     * @throws Exception 如果 after 逻辑失败
     */
    protected abstract Object after(InvocationContext context) throws Exception;
}
