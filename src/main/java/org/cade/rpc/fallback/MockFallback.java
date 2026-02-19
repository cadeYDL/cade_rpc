package org.cade.rpc.fallback;

import org.cade.rpc.excpetion.RPCException;
import org.cade.rpc.metrics.RPCCallMetrics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockFallback implements Fallback {
    Map<Class<?>, Object> mockObjectMap = new ConcurrentHashMap<>();

    @Override
    public Object fallback(RPCCallMetrics metrics) throws Exception {
        Method method = metrics.getMethod();
        RPCFallback annotation = method.getDeclaringClass().getAnnotation(RPCFallback.class);
        if (annotation == null) {
            throw new RPCException("cannot be downgraded");
        }
        Class<?> methodClass = annotation.implement();
        if (!method.getDeclaringClass().isAssignableFrom(methodClass)) {
            throw new RPCException("cannot be downgraded call:" + method + "downgraded is:" + methodClass);
        }
        Object object = mockObjectMap.computeIfAbsent(methodClass, this::createMockObject);
        return method.invoke(object, metrics.getArgs());
    }

    private Object createMockObject(Class<?> methodClass) {
        try {
            return methodClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RPCException("create mock object fail",e);
        }
    }
}
