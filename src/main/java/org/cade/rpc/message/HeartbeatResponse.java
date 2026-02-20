package org.cade.rpc.message;

import lombok.Data;

import java.io.Serializable;

/**
 * 心跳响应消息。
 * <p>
 * 用于响应心跳请求，确认连接仍然活跃。
 */
@Data
public class HeartbeatResponse implements Serializable {
    private final long requestTime;
}
