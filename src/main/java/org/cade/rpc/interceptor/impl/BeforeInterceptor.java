package org.cade.rpc.interceptor.impl;

import org.cade.rpc.interceptor.Interceptor;
import org.cade.rpc.interceptor.InvocationContext;
import org.cade.rpc.interceptor.ShortCircuitResult;

/**
 * Before 拦截器的抽象基类。
 * 在方法执行前执行，可以访问方法参数并阻断执行。
 *
 * <p>使用场景：
 * <ul>
 *   <li>参数验证</li>
 *   <li>权限检查</li>
 *   <li>执行前日志记录</li>
 *   <li>请求预处理</li>
 * </ul>
 *
 * <p>实现示例：
 * <pre>
 * public class PermissionInterceptor extends BeforeInterceptor {
 *     protected Object before(InvocationContext context) throws Exception {
 *         if (!hasPermission(context.getArguments()[0])) {
 *             // 阻断并返回错误响应
 *             return ShortCircuitResult.of(Response.error("无权限", -1));
 *         }
 *         return null; // 继续执行
 *     }
 * }
 * </pre>
 */
public abstract class BeforeInterceptor implements Interceptor {

    /**
     * 获取拦截器类型（始终为 BEFORE）。
     */
    @Override
    public final InterceptorType getType() {
        return InterceptorType.BEFORE;
    }

    /**
     * 拦截方法调用（最终实现）。
     * 委托给 {@link #before(InvocationContext)}。
     */
    @Override
    public final Object intercept(InvocationContext context) throws Exception {
        return before(context);
    }

    /**
     * 执行 before 逻辑。
     * 在方法执行前调用。
     *
     * @param context 调用上下文（可以访问方法参数）
     * @return 返回 {@link ShortCircuitResult} 可阻断执行并返回自定义值，返回 null 则继续执行
     * @throws Exception 如果 before 逻辑失败
     */
    protected abstract Object before(InvocationContext context) throws Exception;
}
