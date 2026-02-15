package org.cade.rpc.provider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProviderRegistry {
    private final Map<String, Invocation<?>> serviceMap = new ConcurrentHashMap<>();

    public <I> void register(Class<I> interfaceClass,I serviceInstance){
        if(!interfaceClass.isInterface()){
            throw new IllegalArgumentException("serviceInstance must be interface");
        }

        if(serviceMap.putIfAbsent(interfaceClass.getName(),new Invocation<>(interfaceClass,serviceInstance))!=null){
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

        public Invocation(Class<I> interfaceClass, I serviceInstance) {
            this.serviceInstance = serviceInstance;
            this.interfaceClass = interfaceClass;
        }

        public Object invoke(String methodName,Class<?>[] paramsClass, Object[] args) throws Exception {
            Method invokeMethod = interfaceClass.getDeclaredMethod(methodName,paramsClass);
            return invokeMethod.invoke(serviceInstance,args);
        }
    }
}
