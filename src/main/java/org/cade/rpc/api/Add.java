package org.cade.rpc.api;

import org.cade.rpc.fallback.RPCFallback;

@RPCFallback(implement = ConsumerAdd.class)
public interface Add {
    int add(int a, int b);
    int mul(int a, int b);
}
