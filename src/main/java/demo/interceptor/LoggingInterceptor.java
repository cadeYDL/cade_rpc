package demo.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.interceptor.InvocationContext;
import org.cade.rpc.interceptor.impl.AfterInterceptor;

/**
 * 日志记录拦截器示例，记录方法执行详情。
 * 演示了 AFTER 拦截器的使用，可以访问结果、异常和执行时间。
 */
@Slf4j(topic = "logging_interceptor")
public class LoggingInterceptor extends AfterInterceptor {

    @Override
    protected Object after(InvocationContext context) throws Exception {
        if (context.isSuccess()) {
            log.info("[RPC-日志{}] 方法: {}.{}, 耗时: {}ms, 成功: true",
                    context.getTraceID(),
                    context.getServiceName(),
                    context.getMethodName(),
                    context.getDurationMillis());
        } else {
            log.error("[RPC-日志{}] 方法: {}.{}, 耗时: {}ms, 成功: false, 异常: {}",
                    context.getTraceID(),
                    context.getServiceName(),
                    context.getMethodName(),
                    context.getDurationMillis(),
                    context.getException().getMessage());
        }

        return null; // 不修改结果
    }

    @Override
    public int getOrder() {
        return 100; // 在指标拦截器之后执行
    }
}
