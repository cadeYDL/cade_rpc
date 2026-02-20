package org.cade.rpc.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class HeartbeatRequest implements Serializable {
    private final long requestTime = System.currentTimeMillis();
}
