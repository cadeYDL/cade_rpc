package org.cade.rpc.message;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class Request  {
    private static final AtomicInteger idGenerator = new AtomicInteger(1);

    private String serviceName;
    private String methodName;
    private Class<?>[] paramsType;
    private Object[] params;
    private int requestID = idGenerator.getAndIncrement();
}
