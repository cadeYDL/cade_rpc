package org.cade.rpc.provider;

import org.cade.rpc.interceptor.InterceptorChain;
import org.cade.rpc.interceptor.InterceptorConfig;
import org.cade.rpc.interceptor.InvocationContext;
import org.cade.rpc.trace.TraceContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProviderRegistry {
    private final Map<String, Invocation<?>> serviceMap = new ConcurrentHashMap<>();

    public <I> void register(Class<I> interfaceClass,I serviceInstance){
        register(interfaceClass, serviceInstance, new InterceptorConfig());
    }

    public <I> void register(Class<I> interfaceClass, I serviceInstance, InterceptorConfig config){
        if(!interfaceClass.isInterface()){
            throw new IllegalArgumentException("serviceInstance must be interface");
        }

        if(serviceMap.putIfAbsent(interfaceClass.getName(),new Invocation<>(interfaceClass,serviceInstance, config))!=null){
            throw new IllegalArgumentException(interfaceClass.getName()+"serviceInstance already exists");
        }
    }

    public List<String> allServiceNames(){
        return new ArrayList<>(serviceMap.keySet());
    }

    public Invocation getService(String serviceName) {
        return serviceMap.get(serviceName);
    }

    public static class Invocation<I> {
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

        public Object invoke(String methodName,Class<?>[] paramsClass, Object[] args) throws Exception {
            Method invokeMethod = interfaceClass.getDeclaredMethod(methodName,paramsClass);

            // 获取预编译的拦截器链
            InterceptorChain chain = interceptorConfig.getChain(invokeMethod);

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
