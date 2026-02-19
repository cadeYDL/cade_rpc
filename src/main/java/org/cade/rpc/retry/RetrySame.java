package org.cade.rpc.retry;

import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.excpetion.RPCException;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.Metadata;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@Slf4j(topic = "retry_same")
public class RetrySame implements RetryPolicy{
    final int retryTimes = 3;
    final long retryInterval = 100;
    private Random random=new Random();

    @Override
    public Response retry(RetryContext retryContext)throws Exception {
        long startTime = System.currentTimeMillis();
        int retryCount = 0;
        while (retryCount++ < retryTimes){
            long nextDelay = nextDelay(retryCount);
            long methodTimeout = retryContext.getFunctionTimeoutMS()-(System.currentTimeMillis() - startTime);
            if(methodTimeout<=0||nextDelay>=methodTimeout){
                throw new TimeoutException();
            }
            Thread.sleep(nextDelay);
            try{
                CompletableFuture<Response> future = retryContext.doRPC(getService(retryContext,retryContext.getFailService()));
                return  future.get(retryContext.getFunctionTimeoutMS(), TimeUnit.MILLISECONDS);
            }catch (Exception e){
                log.error("retry fail, retryCount:{} e:{}", retryCount,e);
            }
        }
        throw new RPCException("retry fail");


    }

    protected Metadata getService(RetryContext retryContext,Metadata failService) {
        return failService;
    }

    private long nextDelay(int retryCount) {
        return retryInterval*(1L<<(retryCount-1))+random.nextInt(0,50);
    }
}
