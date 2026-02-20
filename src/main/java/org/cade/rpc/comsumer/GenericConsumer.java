package org.cade.rpc.comsumer;

public interface GenericConsumer {
    Object $invoke(String serviceName,String methodName,String[] paramsType, Object[] args);
}
