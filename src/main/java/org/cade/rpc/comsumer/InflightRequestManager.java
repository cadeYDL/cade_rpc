package org.cade.rpc.comsumer;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.excpetion.LimitException;
import org.cade.rpc.limit.ConcurrencyLimiter;
import org.cade.rpc.limit.Limiter;
import org.cade.rpc.limit.RateLimiter;
import org.cade.rpc.message.Request;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.Metadata;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@Slf4j(topic = "inflight_resust_manager")
public class InflightRequestManager {
    private final Map<Integer, CompletableFuture<Response>> inFlightRequestTable ;
    private final HashedWheelTimer timeoutTimer;
    private final Limiter globelLimiter;
    private final Map<Metadata,Limiter> channelLimiter;
    private final ConsumerProperties properties;

    InflightRequestManager(ConsumerProperties properties){
        this.properties = properties;
        this.globelLimiter = new ConcurrencyLimiter(properties.getRpcPreSecond());
        this.timeoutTimer = new HashedWheelTimer();
        this.inFlightRequestTable = new ConcurrentHashMap<>();
        this.channelLimiter =  new ConcurrentHashMap<>();
    }

    public void clearChannel(Metadata metadata){
        channelLimiter.remove(metadata);
    }

    public CompletableFuture<Response> inFlightRequest(Request request,long timeoutMS,Metadata metadata) {
        CompletableFuture<Response> responseFuture = new CompletableFuture<>();
        if(!globelLimiter.tryAcquire()){
            throw new LimitException("globel limiter too many inflight request");
        }

        Limiter limiter = channelLimiter.computeIfAbsent(metadata,k->new RateLimiter(properties.getRpcPreChannelSecond()));
        if(!limiter.tryAcquire()){
            throw new LimitException("channel limiter too many inflight request");
        }
        inFlightRequestTable.put(request.getRequestID(), responseFuture);
        Timeout timeout = timeoutTimer.newTimeout((t)->responseFuture.completeExceptionally(new TimeoutException()),timeoutMS, TimeUnit.MILLISECONDS);
        responseFuture.whenComplete((r,e)->{
            inFlightRequestTable.remove(request.getRequestID());
            globelLimiter.release();
            limiter.release();
            timeout.cancel();
        });
        return responseFuture;
    }

    public boolean completeRequst(int reqsutID,Response response){
        CompletableFuture<Response> future = inFlightRequestTable.remove(reqsutID);
        if(future == null){
            log.warn("can not find request id:{}", response.getRequestId());
            return false;
        }
        return future.complete(response);
    }

    public boolean completeExceptionRequst(int requestID,Exception exception){
        CompletableFuture<Response> future = inFlightRequestTable.remove(requestID);
        if(future == null){
            log.warn("can not find request id:{}", requestID);
            return false;
        }
        return future.completeExceptionally(exception);
    }
}
