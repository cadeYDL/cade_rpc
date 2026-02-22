package org.cade.rpc.provider;

import org.cade.rpc.interceptor.*;
import org.cade.rpc.trace.TraceContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProviderRegistry {
    private final Map<String, Invocation<?>> serviceMap = new ConcurrentHashMap<>();
    // 使用线程安全的列表来存储全局拦截器
    private final List<Interceptor> globalInterceptors = new CopyOnWriteArrayList<>();

    /**
     * 添加一个全局拦截器。
     * 该拦截器将对所有后续的服务调用生效。
     *
     * @param interceptor 要添加的全局拦截器。
     */
    public void addGlobalInterceptor(Interceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("全局拦截器不能为null");
        }
        // 避免重复添加同一个实例
        if (!this.globalInterceptors.contains(interceptor)) {
            this.globalInterceptors.add(interceptor);
        }
    }

    public <I> void register(Class<I> interfaceClass,I serviceInstance){
        register(interfaceClass, serviceInstance, new InterceptorConfig());
    }

    public <I> void register(Class<I> interfaceClass, I serviceInstance, InterceptorConfig programmaticConfig) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("服务实例必须是接口类型");
        }

        // 1. 从接口和接口方法中解析注解
        InterceptorConfig interfaceAnnotationConfig = InterceptorAnnotationUtil.parseAnnotations(interfaceClass);

        // 2. 仅从实现类上解析类级别注解
        InterceptorConfig classAnnotationConfig = InterceptorAnnotationUtil.parseClassLevelAnnotations(serviceInstance.getClass());

        // 3. 合并编程式和注解配置
        InterceptorConfig tempMergedConfig = InterceptorAnnotationUtil.merge(programmaticConfig, interfaceAnnotationConfig);
        InterceptorConfig finalMergedConfig = InterceptorAnnotationUtil.merge(tempMergedConfig, classAnnotationConfig);

        // 4. 使用合并后的配置注册服务
        if (serviceMap.putIfAbsent(interfaceClass.getName(), new Invocation<>(interfaceClass, serviceInstance, finalMergedConfig)) != null) {
            throw new IllegalArgumentException(interfaceClass.getName() + " 服务实例已存在");
        }
    }

    public List<String> allServiceNames(){
        return new ArrayList<>(serviceMap.keySet());
    }

    public Invocation getService(String serviceName) {
        return serviceMap.get(serviceName);
    }

    public class Invocation<I> {
        private final I serviceInstance;
        private final Class<I> interfaceClass;
        private final InterceptorConfig interceptorConfig;

        public Invocation(Class<I> interfaceClass, I serviceInstance) {
            this(interfaceClass, serviceInstance, new InterceptorConfig());
        }

        public Invocation(Class<I> interfaceClass, I serviceInstance, InterceptorConfig config) {
            this.serviceInstance = serviceInstance;
            this.interfaceClass = interfaceClass;
            this.interceptorConfig = config;
        }

        public InterceptorConfig getInterceptorConfig() {
            return interceptorConfig;
        }

        public Object invoke(String methodName,Class<?>[] paramsClass, Object[] args) throws Exception {
            Method invokeMethod = interfaceClass.getDeclaredMethod(methodName,paramsClass);

            // 1. 创建临时的全局配置
            InterceptorConfig globalConfig = new InterceptorConfig();
            globalInterceptors.forEach(globalConfig::addInterfaceInterceptor); // 使用外部类的 globalInterceptors

            // 2. 合并全局配置和服务特定配置
            InterceptorConfig finalConfig = InterceptorAnnotationUtil.merge(globalConfig, this.interceptorConfig);

            // 3. 获取最终的拦截器链
            InterceptorChain chain = finalConfig.getChain(invokeMethod);

            if (chain.isEmpty()) {
                // 快速路径：无拦截器，直接调用
                return invokeMethod.invoke(serviceInstance,args);
            }

            // 构建调用上下文
            InvocationContext context = InvocationContext.builder()
                    .serviceName(interfaceClass.getName())
                    .methodName(methodName)
                    .method(invokeMethod)
                    .parameterTypes(paramsClass)
                    .arguments(args)
                    .serviceInstance(serviceInstance)
                    .traceID(TraceContext.getOrCreate())
                    .build();

            // 执行拦截器链
            return chain.execute(context, () -> invokeMethod.invoke(serviceInstance, args));
        }
    }
}
