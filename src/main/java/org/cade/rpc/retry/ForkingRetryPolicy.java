package org.cade.rpc.retry;

import org.cade.rpc.excpetion.RPCException;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.Metadata;
import org.cade.rpc.spi.SPI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SPI("forking")
public class ForkingRetryPolicy implements RetryPolicy{
    @Override
    public Response retry(RetryContext context) throws Exception {
        List<Metadata> metadataList = new ArrayList<>(context.getAllService());
        metadataList.remove(context.getFailService());
        if(metadataList.isEmpty()){
            throw new RPCException("no service");
        }
        CompletableFuture[] allFuture = new CompletableFuture[metadataList.size()];
        for(int i=0;i<metadataList.size();i++){
            allFuture[i] = context.doRPC(metadataList.get(i));
        }
        CompletableFuture<Object> mainFuture = CompletableFuture.anyOf(allFuture);
        return  (Response)mainFuture.get(Math.min(context.getRequestTimeout(),context.getFunctionTimeoutMS()), TimeUnit.MILLISECONDS);
    }
}
