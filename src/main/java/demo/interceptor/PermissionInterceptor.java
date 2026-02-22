package demo.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.interceptor.InvocationContext;
import org.cade.rpc.interceptor.ShortCircuitResult;
import org.cade.rpc.interceptor.impl.BeforeInterceptor;
import org.cade.rpc.message.Response;

/**
 * 权限检查拦截器示例，在方法执行前检查访问权限。
 * 演示了 BEFORE 拦截器的使用及阻断能力。
 */
@Slf4j(topic = "permission_interceptor")
public class PermissionInterceptor extends BeforeInterceptor {

    @Override
    protected Object before(InvocationContext context) throws Exception {
        // 示例：检查方法是否允许执行
        // 实际场景中，你需要检查用户权限、角色等

        String methodName = context.getMethodName();

        // 示例：阻止所有 "delete" 方法
        if (methodName.toLowerCase().contains("delete")) {
            log.warn("[权限检查{}] 方法 {} 被限制，拒绝访问",context.getTraceID(), methodName);

            // 阻断并返回错误响应
            return ShortCircuitResult.of(
                    Response.error("方法无权限: " + methodName, -403)
            );
        }

        log.info("[权限检查{}] 方法 {} 权限检查通过",context.getTraceID(), methodName);
        return null; // 继续执行
    }

    @Override
    public int getOrder() {
        return 0; // 首先执行（最高优先级）
    }
}
