package org.cade.rpc.loadbalance;

import org.cade.rpc.register.Metadata;

import java.util.List;

public interface LoadBalancer {
    Metadata select(List<Metadata> metadataList);
}
