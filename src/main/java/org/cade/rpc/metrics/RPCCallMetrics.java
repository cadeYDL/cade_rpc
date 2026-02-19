package org.cade.rpc.metrics;

import lombok.Data;
import org.cade.rpc.register.Metadata;

import java.lang.reflect.Method;

@Data
public class RPCCallMetrics {
    private boolean complete;
    private Throwable throwable;
    private long durationMS;
    private long startTime;
    private Method method;
    private Metadata provider;
    private Object[] args;

    private RPCCallMetrics() {
    }

    public static RPCCallMetrics create(Metadata metadata, Method method, Object[] args) {
        RPCCallMetrics metrics = new RPCCallMetrics();
        metrics.startTime = System.currentTimeMillis();
        metrics.method = method;
        metrics.provider = metadata;
        metrics.args = args;
        return metrics;
    }

    public void complete() {
        this.complete = true;
        this.durationMS = System.currentTimeMillis() - startTime;

    }

    public void complete(Throwable throwable) {
        this.complete = true;
        this.throwable = throwable;
        this.durationMS = System.currentTimeMillis() - startTime;
    }
}
