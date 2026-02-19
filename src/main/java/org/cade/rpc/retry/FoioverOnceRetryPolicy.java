package org.cade.rpc.retry;

import org.cade.rpc.excpetion.RPCException;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.Metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FoioverOnceRetryPolicy implements RetryPolicy {
    @Override
    public Response retry(RetryContext context) throws Exception {
        List<Metadata> metadataList = new ArrayList<>(context.getAllService());
        metadataList.remove(context.getFailService());
        if(metadataList.isEmpty()){
            throw new RPCException("no service");
        }
        Metadata metadata = context.getLoadBalancer().select(metadataList);
        CompletableFuture<Response> future = context.doRPC(metadata);
        return future.get(Math.min(context.getRequestTimeout(),context.getFunctionTimeoutMS()), TimeUnit.MILLISECONDS);
    }
}
