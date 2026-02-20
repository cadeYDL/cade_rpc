package org.cade.rpc.message;

import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class Request implements Serializable {
    private static final AtomicInteger idGenerator = new AtomicInteger(1);

    private String serviceName;
    private boolean genericInvoke;
    private String methodName;
    private Class<?>[] paramsType;
    private String[] paramsTypeStr;
    private Object[] params;
    private int requestID = idGenerator.getAndIncrement();
}
