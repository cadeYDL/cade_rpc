package org.cade.rpc.breaker;

import org.cade.rpc.metrics.RPCCallMetrics;

public interface CircuitBreaker {
    boolean allowRequest();
    void recordRPC(RPCCallMetrics metrics);

    enum State{
        OPEN,
        HALFEN,
        CLOSED,
    }
}
