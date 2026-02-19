package org.cade.rpc.fallback;

import org.cade.rpc.metrics.RPCCallMetrics;

public interface Fallback {
    Object fallback(RPCCallMetrics metrics) throws Exception;

    default void recordMetrics(RPCCallMetrics metrics){

    }
}
