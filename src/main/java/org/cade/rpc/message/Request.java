package org.cade.rpc.message;

import lombok.Data;

@Data
public class Request  {
    private String serviceName;
    private String methodName;
    private String[] paramsType;
    private Object[] params;
}
