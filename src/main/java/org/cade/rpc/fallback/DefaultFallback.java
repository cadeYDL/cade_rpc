package org.cade.rpc.fallback;

import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.metrics.RPCCallMetrics;

@Slf4j(topic = "default_fallback")
public class DefaultFallback implements Fallback {
    private Fallback cacheFallback,mockFallback;
    public DefaultFallback(CacheFallback cacheFallback,MockFallback mockFallback){
        this.cacheFallback = cacheFallback;
        this.mockFallback = mockFallback;
    }
    @Override
    public Object fallback(RPCCallMetrics metrics) throws Exception {
        try{
            return cacheFallback.fallback(metrics);
        }catch (Exception e){
            log.warn("cacheFallback error",e);
            return mockFallback.fallback(metrics);
        }
    }

    @Override
    public void recordMetrics(RPCCallMetrics metrics) {
        this.cacheFallback.recordMetrics(metrics);
        this.mockFallback.recordMetrics(metrics);
    }
}
