package org.cade.rpc.retry;

import lombok.Data;
import org.cade.rpc.loadbalance.LoadBalancer;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.Metadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Data
public class RetryContext {
    private Metadata failService;
    private List<Metadata> allService;
    private long functionTimeoutMS;
    private long requestTimeout;
    private LoadBalancer loadBalancer;
    private Function<Metadata,CompletableFuture<Response>> retry;

    public CompletableFuture<Response> doRPC(Metadata service){
        return retry.apply(service);
    }
}
