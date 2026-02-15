package org.cade.rpc.loadbalance;

import org.cade.rpc.register.Metadata;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalancer implements LoadBalancer {
    private final AtomicInteger lastInx = new AtomicInteger(0);
    @Override
    public Metadata select(List<Metadata> metadataList) {
        if (metadataList.isEmpty()){
            return null;
        }
        int inx = Math.abs(lastInx.getAndIncrement());
        return metadataList.get(inx % metadataList.size());
    }
}
