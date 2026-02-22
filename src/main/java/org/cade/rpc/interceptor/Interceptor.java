package org.cade.rpc.interceptor;

/**
 * RPC 方法调用的基础拦截器接口。
 * 支持 before、after 和 around 三种 AOP 切面模式。
 *
 * <p>拦截器可以应用于接口级别或方法级别：
 * <ul>
 *   <li>Before: 在方法执行前调用，可以阻断执行</li>
 *   <li>After: 在方法执行后调用（成功或失败），可以访问结果、异常和执行时间</li>
 *   <li>Around: 包裹方法执行，完全控制执行流程</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * public class LoggingInterceptor implements Interceptor {
 *     public InterceptorType getType() { return InterceptorType.AFTER; }
 *
 *     public Object intercept(InvocationContext context) {
 *         log.info("方法 {} 耗时 {}ms",
 *             context.getMethodName(),
 *             context.getDurationMillis());
 *         return null;
 *     }
 * }
 * </pre>
 */
public interface Interceptor {

    /**
     * 拦截方法调用。
     *
     * @param context 调用上下文，包含方法信息、参数、结果、异常等
     * @return 返回 ShortCircuitResult 可阻断执行并返回自定义值，返回 null 则继续执行
     * @throws Exception 如果拦截失败
     */
    Object intercept(InvocationContext context) throws Exception;

    /**
     * 获取拦截器类型。
     *
     * @return 拦截器类型（BEFORE、AFTER 或 AROUND）
     */
    InterceptorType getType();

    /**
     * 获取执行顺序。
     * 数值越小优先级越高（越早执行）。
     *
     * @return 顺序值，默认为 0
     */
    default int getOrder() {
        return 0;
    }

    /**
     * 拦截器类型枚举。
     */
    enum InterceptorType {
        /**
         * 在方法执行前执行。
         * 可以访问方法参数并阻断执行。
         */
        BEFORE,

        /**
         * 在方法执行后执行（成功或失败）。
         * 可以访问方法结果、异常和执行时间。
         */
        AFTER,

        /**
         * 包裹方法执行。
         * 完全控制方法执行，可以继续执行或阻断。
         */
        AROUND
    }
}
