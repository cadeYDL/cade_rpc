package org.cade.rpc.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class Response implements Serializable {
    private Object result;
    private Integer code;
    private String message;
    private int RequestId;

    /**
     * 分布式链路追踪 ID
     */
    private String traceId;

    public static Response ok(Object result, int RequestId) {
        Response response = new Response();
        response.setCode(0);
        response.setResult(result);
        response.setRequestId(RequestId);
        return response;
    }

    public static Response error(String message, int RequestId) {
        Response response = new Response();
        response.setCode(-1);
        response.setMessage(message);
        response.setRequestId(RequestId);
        return response;
    }
}
