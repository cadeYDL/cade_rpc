package org.cade.rpc.interceptor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 传递给拦截器的上下文对象，包含所有调用信息。
 * 提供对方法元数据、参数、结果、异常和执行时间的访问。
 *
 * <p>除了可变状态字段（result、exception、timing）外都是不可变的，这些字段在执行期间设置。
 *
 * <p>使用示例：
 * <pre>
 * InvocationContext context = InvocationContext.builder()
 *     .serviceName("com.example.UserService")
 *     .methodName("getUser")
 *     .method(method)
 *     .parameterTypes(new Class[]{Long.class})
 *     .arguments(new Object[]{123L})
 *     .build();
 * </pre>
 */
public class InvocationContext {

    // 不可变元数据
    private final String serviceName;
    private final String methodName;
    private final Method method;
    private final Class<?>[] parameterTypes;
    private final Object[] arguments;
    private final Object serviceInstance; // 仅在 Provider 侧使用，Consumer 侧为 null
    private final String traceID;

    // 可变状态（在执行期间设置）
    private Object result;
    private Throwable exception;
    private long startTimeMillis;
    private long endTimeMillis;

    // 拦截器间通信的属性
    private final Map<String, Object> attributes;

    private InvocationContext(Builder builder) {
        this.serviceName = builder.serviceName;
        this.methodName = builder.methodName;
        this.method = builder.method;
        this.parameterTypes = builder.parameterTypes;
        this.arguments = builder.arguments;
        this.serviceInstance = builder.serviceInstance;
        this.traceID = builder.traceID;
        this.attributes = new HashMap<>();
        this.startTimeMillis = 0;
        this.endTimeMillis = 0;
    }

    // 不可变元数据的 Getters

    /**
     * 获取服务名称（接口全限定名）。
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 获取方法名称。
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * 获取方法对象。
     */
    public Method getMethod() {
        return method;
    }

    /**
     * 获取参数类型。
     */
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    /**
     * 获取方法参数。
     */
    public Object[] getArguments() {
        return arguments;
    }

    /**
     * 获取服务实例（仅 Provider 侧，Consumer 侧为 null）。
     */
    public Object getServiceInstance() {
        return serviceInstance;
    }

    // 可变状态的 Getters 和 Setters

    /**
     * 获取方法执行结果。
     */
    public Object getResult() {
        return result;
    }

    /**
     * 设置方法执行结果。
     * 通常由拦截器链在方法执行后调用。
     */
    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * 获取方法执行期间抛出的异常（如果有）。
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * 设置方法执行期间抛出的异常。
     * 通常由拦截器链在方法抛出异常时调用。
     */
    public void setException(Throwable exception) {
        this.exception = exception;
    }

    /**
     * 获取开始时间（毫秒）。
     */
    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    /**
     * 设置开始时间（毫秒）。
     * 通常由拦截器链在执行前调用。
     */
    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    /**
     * 获取结束时间（毫秒）。
     */
    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    /**
     * 设置结束时间（毫秒）。
     * 通常由拦截器链在执行后调用。
     */
    public void setEndTimeMillis(long endTimeMillis) {
        this.endTimeMillis = endTimeMillis;
    }

    // 计算属性

    /**
     * 获取方法执行时长（毫秒）。
     * 如果执行尚未完成，返回到目前为止的已用时间。
     */
    public long getDurationMillis() {
        if (endTimeMillis == 0) {
            return System.currentTimeMillis() - startTimeMillis;
        }
        return endTimeMillis - startTimeMillis;
    }

    /**
     * 检查方法执行是否成功（无异常）。
     */
    public boolean isSuccess() {
        return exception == null;
    }

    /**
     * 检查方法执行是否抛出异常。
     */
    public boolean hasException() {
        return exception != null;
    }

    // 拦截器间通信的属性管理

    /**
     * 设置属性，用于拦截器间通信。
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取前一个拦截器设置的属性。
     *
     * @param key 属性键
     * @return 属性值，如果未设置则返回 null
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 移除属性。
     *
     * @param key 属性键
     * @return 之前的值，如果未设置则返回 null
     */
    public Object removeAttribute(String key) {
        return attributes.remove(key);
    }

    /**
     * 检查属性是否存在。
     *
     * @param key 属性键
     * @return 如果属性已设置则返回 true
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    /**
     * 创建新的构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getTraceID() {
        return traceID;
    }

    /**
     * InvocationContext 的构建器。
     */
    public static class Builder {
        public String traceID;
        private String serviceName;
        private String methodName;
        private Method method;
        private Class<?>[] parameterTypes;
        private Object[] arguments;
        private Object serviceInstance;

        private Builder() {
        }

        public Builder traceID(String traceID) {
            this.traceID = traceID;
            return this;
        }

        /**
         * 设置服务名称（接口全限定名）。
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * 设置方法名称。
         */
        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        /**
         * 设置方法对象。
         */
        public Builder method(Method method) {
            this.method = method;
            return this;
        }

        /**
         * 设置参数类型。
         */
        public Builder parameterTypes(Class<?>[] parameterTypes) {
            this.parameterTypes = parameterTypes;
            return this;
        }

        /**
         * 设置方法参数。
         */
        public Builder arguments(Object[] arguments) {
            this.arguments = arguments;
            return this;
        }

        /**
         * 设置服务实例（仅 Provider 侧）。
         */
        public Builder serviceInstance(Object serviceInstance) {
            this.serviceInstance = serviceInstance;
            return this;
        }

        /**
         * 构建 InvocationContext。
         */
        public InvocationContext build() {
            return new InvocationContext(this);
        }
    }

    @Override
    public String toString() {
        return "InvocationContext{" +
                "serviceName='" + serviceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", parameterTypes=" + (parameterTypes != null ? parameterTypes.length : 0) +
                ", arguments=" + (arguments != null ? arguments.length : 0) +
                ", result=" + result +
                ", exception=" + exception +
                ", duration=" + getDurationMillis() + "ms" +
                '}';
    }
}
