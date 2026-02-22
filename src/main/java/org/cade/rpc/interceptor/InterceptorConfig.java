package org.cade.rpc.interceptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for interceptors at interface and method level.
 * Supports efficient lookup with pre-compiled chain caching.
 *
 * <p>Interceptors can be configured at two levels:
 * <ul>
 *   <li>Interface-level: Applied to all methods of the interface</li>
 *   <li>Method-level: Applied only to specific methods</li>
 * </ul>
 *
 * <p>Performance optimization:
 * <ul>
 *   <li>Pre-compiled chains cached by method signature</li>
 *   <li>Thread-safe using ConcurrentHashMap</li>
 *   <li>Lazy chain building on first access</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * InterceptorConfig config = new InterceptorConfig();
 * config.addInterfaceInterceptor(new LoggingInterceptor());
 * config.addMethodInterceptor("deleteUser", new PermissionInterceptor());
 *
 * InterceptorChain chain = config.getChain(method);
 * Object result = chain.execute(context, () -> method.invoke(instance, args));
 * </pre>
 */
public class InterceptorConfig {

    // Interface-level interceptors (applied to all methods)
    private final List<Interceptor> interfaceInterceptors;

    // Method-level interceptors: methodName -> interceptors
    private final Map<String, List<Interceptor>> methodInterceptors;

    // Pre-compiled chains cache: method signature -> chain
    // Key format: "methodName(param1Type,param2Type)"
    private final Map<String, InterceptorChain> chainCache;

    public InterceptorConfig() {
        this.interfaceInterceptors = new ArrayList<>();
        this.methodInterceptors = new ConcurrentHashMap<>();
        this.chainCache = new ConcurrentHashMap<>();
    }

    /**
     * Add interface-level interceptor.
     * Applied to all methods of the interface.
     *
     * @param interceptor Interceptor to add
     */
    public void addInterfaceInterceptor(Interceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("Interceptor cannot be null");
        }
        interfaceInterceptors.add(interceptor);
        chainCache.clear(); // Invalidate cache
    }

    /**
     * Add method-level interceptor.
     * Applied only to the specified method.
     *
     * @param methodName  Method name
     * @param interceptor Interceptor to add
     */
    public void addMethodInterceptor(String methodName, Interceptor interceptor) {
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("Method name cannot be null or empty");
        }
        if (interceptor == null) {
            throw new IllegalArgumentException("Interceptor cannot be null");
        }

        methodInterceptors.computeIfAbsent(methodName, k -> new ArrayList<>()).add(interceptor);
        chainCache.clear(); // Invalidate cache
    }

    /**
     * Get pre-compiled interceptor chain for a method.
     * Uses cache for performance.
     *
     * @param method Method to get chain for
     * @return InterceptorChain (may be empty if no interceptors configured)
     */
    public InterceptorChain getChain(Method method) {
        if (method == null) {
            return new InterceptorChain(new ArrayList<>());
        }

        String signature = getMethodSignature(method);
        return chainCache.computeIfAbsent(signature, k -> buildChain(method));
    }

    /**
     * Build interceptor chain for a method by combining interface-level
     * and method-level interceptors.
     */
    private InterceptorChain buildChain(Method method) {
        List<Interceptor> combined = new ArrayList<>();

        // 添加接口级别拦截器
        combined.addAll(interfaceInterceptors);

        // 添加方法级别拦截器
        List<Interceptor> methodLevel = methodInterceptors.get(method.getName());
        if (methodLevel != null) {
            combined.addAll(methodLevel);
        }

        return new InterceptorChain(combined);
    }

    /**
     * Generate method signature for caching.
     * Format: "methodName(param1Type,param2Type)"
     */
    private String getMethodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName()).append("(");

        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(params[i].getName());
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Check if configuration is empty (no interceptors configured).
     *
     * @return true if no interceptors configured
     */
    public boolean isEmpty() {
        return interfaceInterceptors.isEmpty() && methodInterceptors.isEmpty();
    }

    /**
     * Get number of interface-level interceptors.
     */
    public int getInterfaceInterceptorCount() {
        return interfaceInterceptors.size();
    }

    /**
     * Get number of methods with method-level interceptors.
     */
    public int getMethodInterceptorCount() {
        return methodInterceptors.size();
    }

    /**
     * Clear all interceptors and cache.
     */
    public void clear() {
        interfaceInterceptors.clear();
        methodInterceptors.clear();
        chainCache.clear();
    }

    /**
     * Clear cache only (keeps interceptors).
     * Useful when you want to force chain rebuild.
     */
    public void clearCache() {
        chainCache.clear();
    }

    @Override
    public String toString() {
        return "InterceptorConfig{" +
                "interfaceInterceptors=" + interfaceInterceptors.size() +
                ", methodInterceptors=" + methodInterceptors.size() +
                ", cachedChains=" + chainCache.size() +
                '}';
    }

    /**
     * 获取接口级别拦截器的不可修改视图。
     *
     * @return 拦截器的不可修改列表。
     */
    public List<Interceptor> getInterfaceInterceptors() {
        return Collections.unmodifiableList(interfaceInterceptors);
    }

    /**
     * 获取方法级别拦截器的不可修改视图。
     *
     * @return 方法拦截器的不可修改映射。
     */
    public Map<String, List<Interceptor>> getMethodInterceptors() {
        return Collections.unmodifiableMap(methodInterceptors);
    }
}
