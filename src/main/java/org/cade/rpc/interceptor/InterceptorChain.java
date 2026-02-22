package org.cade.rpc.interceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * 拦截器链，按顺序执行拦截器：
 * BEFORE → AROUND → 方法执行 → AFTER
 *
 * <p>执行顺序：
 * <ol>
 *   <li>执行所有 BEFORE 拦截器（按优先级排序）</li>
 *   <li>执行 AROUND 拦截器（嵌套，外层优先）或实际方法</li>
 *   <li>执行所有 AFTER 拦截器（按优先级排序）</li>
 * </ol>
 *
 * <p>阻断支持：
 * 任何拦截器都可以返回 {@link ShortCircuitResult} 来中止执行
 * 并立即返回自定义值。
 *
 * <p>异常处理：
 * <ul>
 *   <li>BEFORE 异常：向上传播，方法不执行，AFTER 不执行</li>
 *   <li>方法/AROUND 异常：被捕获，AFTER 仍然执行，然后重新抛出</li>
 *   <li>AFTER 异常：作为抑制异常添加到原异常</li>
 * </ul>
 *
 * <p>此类在构造后是线程安全且不可变的。
 */
public class InterceptorChain {

    private final List<Interceptor> beforeInterceptors;
    private final List<Interceptor> aroundInterceptors;
    private final List<Interceptor> afterInterceptors;

    /**
     * 从拦截器列表创建拦截器链。
     * 拦截器会自动按优先级排序并按类型分组。
     *
     * @param interceptors 拦截器列表
     */
    public InterceptorChain(List<Interceptor> interceptors) {
        this.beforeInterceptors = filterAndSort(interceptors, Interceptor.InterceptorType.BEFORE);
        this.aroundInterceptors = filterAndSort(interceptors, Interceptor.InterceptorType.AROUND);
        this.afterInterceptors = filterAndSort(interceptors, Interceptor.InterceptorType.AFTER);
    }

    /**
     * 在实际方法调用周围执行拦截器链。
     *
     * @param context          调用上下文
     * @param actualInvocation 实际的方法调用（Callable 用于异常处理）
     * @return 方法结果或拦截器结果
     * @throws Exception 如果执行失败
     */
    public Object execute(InvocationContext context, Callable<Object> actualInvocation) throws Exception {
        context.setStartTimeMillis(System.currentTimeMillis());

        try {
            // 阶段 1: 执行 BEFORE 拦截器
            Object beforeResult = executeBefore(context);
            if (beforeResult instanceof ShortCircuitResult) {
                return ((ShortCircuitResult) beforeResult).getValue();
            }

            // 阶段 2: 执行 AROUND 拦截器（或实际调用）
            Object result;
            if (!aroundInterceptors.isEmpty()) {
                result = executeAround(context, actualInvocation);
            } else {
                result = actualInvocation.call();
            }

            // 检查 around 拦截器的阻断
            if (result instanceof ShortCircuitResult) {
                return ((ShortCircuitResult) result).getValue();
            }

            context.setResult(result);

            // 阶段 3: 执行 AFTER 拦截器（成功情况）
            Object afterResult = executeAfter(context);
            if (afterResult instanceof ShortCircuitResult) {
                return ((ShortCircuitResult) afterResult).getValue();
            }

            // 返回原始结果（after 拦截器可以修改 context.result）
            return context.getResult();

        } catch (Exception e) {
            // 在上下文中设置异常，供 after 拦截器使用
            context.setException(e);

            // 执行 AFTER 拦截器（异常情况）
            try {
                Object afterResult = executeAfter(context);
                if (afterResult instanceof ShortCircuitResult) {
                    // After 拦截器可以用阻断覆盖异常
                    return ((ShortCircuitResult) afterResult).getValue();
                }
            } catch (Exception afterException) {
                // 将 after 异常作为抑制异常添加到原异常
                e.addSuppressed(afterException);
            }

            // 重新抛出原异常
            throw e;

        } finally {
            context.setEndTimeMillis(System.currentTimeMillis());
        }
    }

    /**
     * 按顺序执行 BEFORE 拦截器。
     */
    private Object executeBefore(InvocationContext context) throws Exception {
        for (Interceptor interceptor : beforeInterceptors) {
            Object result = interceptor.intercept(context);
            if (result instanceof ShortCircuitResult) {
                return result;
            }
        }
        return null;
    }

    /**
     * 以嵌套方式执行 AROUND 拦截器。
     * 每个 around 拦截器包裹下一个，形成链。
     */
    private Object executeAround(InvocationContext context, Callable<Object> actualInvocation) throws Exception {
        // 递归构建 around 链（最后一个拦截器调用实际方法）
        Callable<Object> chain = actualInvocation;

        // 逆序迭代，使第一个拦截器包裹所有其他拦截器
        for (int i = aroundInterceptors.size() - 1; i >= 0; i--) {
            Interceptor interceptor = aroundInterceptors.get(i);
            Callable<Object> next = chain;
            chain = () -> {
                // 在上下文中存储 next 供 AroundInterceptor 访问
                context.setAttribute("next", next);
                return interceptor.intercept(context);
            };
        }

        return chain.call();
    }

    /**
     * 按顺序执行 AFTER 拦截器。
     * After 拦截器可以访问结果、异常和时长。
     */
    private Object executeAfter(InvocationContext context) throws Exception {
        for (Interceptor interceptor : afterInterceptors) {
            Object result = interceptor.intercept(context);
            if (result instanceof ShortCircuitResult) {
                return result;
            }
        }
        return null;
    }

    /**
     * 按类型过滤拦截器并按顺序排序。
     */
    private List<Interceptor> filterAndSort(List<Interceptor> interceptors, Interceptor.InterceptorType type) {
        if (interceptors == null || interceptors.isEmpty()) {
            return Collections.emptyList();
        }

        List<Interceptor> filtered = interceptors.stream()
                .filter(i -> i.getType() == type)
                .sorted(Comparator.comparingInt(Interceptor::getOrder))
                .collect(Collectors.toList());

        // 返回不可变列表以保证线程安全
        return Collections.unmodifiableList(filtered);
    }

    /**
     * 检查链是否为空（无拦截器）。
     */
    public boolean isEmpty() {
        return beforeInterceptors.isEmpty()
                && aroundInterceptors.isEmpty()
                && afterInterceptors.isEmpty();
    }

    /**
     * 获取拦截器总数。
     */
    public int size() {
        return beforeInterceptors.size()
                + aroundInterceptors.size()
                + afterInterceptors.size();
    }

    @Override
    public String toString() {
        return "InterceptorChain{" +
                "before=" + beforeInterceptors.size() +
                ", around=" + aroundInterceptors.size() +
                ", after=" + afterInterceptors.size() +
                '}';
    }
}
