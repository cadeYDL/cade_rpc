package org.cade.rpc.comsumer;

import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.breaker.CircuitBreaker;
import org.cade.rpc.breaker.CircuitBreakerManager;
import org.cade.rpc.excpetion.RPCException;
import org.cade.rpc.loadbalance.LoadBalancer;
import org.cade.rpc.loadbalance.RandomLoadBalancer;
import org.cade.rpc.loadbalance.RoundRobinLoadBalancer;
import org.cade.rpc.message.Request;
import org.cade.rpc.message.Response;
import org.cade.rpc.metrics.RPCCallMetrics;
import org.cade.rpc.register.DefaultServiceRegister;
import org.cade.rpc.register.Metadata;
import org.cade.rpc.register.ServiceRegister;
import org.cade.rpc.retry.*;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

// 感觉ConsumerProxyFactory中的inFlightRequestTable和ConnectionManager应该交由外部去维护
// 为啥这里需要动态代理？一个简单的模板方法也能够解决这个问题
@Slf4j(topic = "consumer_proxy_factory")
public class ConsumerProxyFactory {


    private final ServiceRegister serviceRegister;
    private final ConsumerProperties properties;
    private final ConnectionManager connectionManager;
    private final InflightRequestManager inflightRequestManager;
    private final CircuitBreakerManager circuitBreakerManager;


    ConsumerProxyFactory(ConsumerProperties properties) throws Exception {
        this.inflightRequestManager = new InflightRequestManager(properties);
        this.connectionManager = new ConnectionManager(inflightRequestManager,properties);
        this.serviceRegister = new DefaultServiceRegister(properties.getRegistryConfig());
        this.circuitBreakerManager = new CircuitBreakerManager(properties);

        this.properties = properties;
    }



    private LoadBalancer createLoadBalancer(){
        switch (properties.getLoadBalancePolicy()){
            case "random":
                return new RandomLoadBalancer();
            case "roundrobin":
                return new RoundRobinLoadBalancer();
            default:
                throw new RPCException("unknow load balance policy");
        }
    }



    public <I> I getConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{interfaceClass}, new ConsumerInvocationHandler<>(interfaceClass,createLoadBalancer(),createRetryPolicy()));
    }

    private RetryPolicy createRetryPolicy() {
        switch (properties.getRetryPolicy()){
            case "same":
                return new RetrySame();
            case "faiover":
                return new FaioverRetryPolicy();
            case "forking":
                return new ForkingRetryPolicy();
            case "foiover_once":
                return new FoioverOnceRetryPolicy();
        }
        throw new IllegalArgumentException(properties.getRetryPolicy()+" this retry policy not exist");
    }

    public class ConsumerInvocationHandler<I> implements InvocationHandler {
        private final Class<I> interfaceClass;
        private final LoadBalancer loadBalancer;
        private final RetryPolicy retryPolicy;
        ConsumerInvocationHandler(Class<I> interfaceClass, LoadBalancer loadBalancer, RetryPolicy retryPolicy) {
            this.interfaceClass = interfaceClass;
            this.loadBalancer = loadBalancer;
            this.retryPolicy = retryPolicy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            List<Metadata> metadataList = new ArrayList<>(serviceRegister.fetchServicelist(interfaceClass.getName()));
            Metadata service = dicideProvider(metadataList);
            Response response;
            RPCCallMetrics metrics = RPCCallMetrics.create(service,method,args);
            CircuitBreaker breaker = circuitBreakerManager.getCircuitBreaker(service);
            try{
                CompletableFuture<Response> future = callRPCAsync(metrics.getMethod(),metrics.getArgs(),service);
                response = future.get(properties.getRequestTimeoutMS(), TimeUnit.MILLISECONDS);
                metrics.complete();
            }catch (Exception e){
                metrics.complete(e);
                response = doRetry(metrics, metadataList);
            }finally {
                breaker.recordRPC(metrics);
            }
            return processResponse(response);
        }

        private CompletableFuture<Response> callRPCAsync(Method method,Object[] args,Metadata provider){
            Request request = buildRequest(method,args);
            Channel channel = connectionManager.getChannel(provider);
            CompletableFuture<Response> responseFuture = inflightRequestManager.inFlightRequest(request,properties.getRequestTimeoutMS(),provider);
            if(channel == null){
                responseFuture.completeExceptionally(new RPCException("provider connection failed"));
                return responseFuture;
            }
            channel.writeAndFlush(request).addListener(f -> {
                if (!f.isSuccess()) {
                    responseFuture.completeExceptionally(f.cause());
                }
            });
            return responseFuture;
        }

        private Metadata dicideProvider(List<Metadata> metadataList) throws Exception {
            while (!metadataList.isEmpty()){
                Metadata service = loadBalancer.select(metadataList);
                CircuitBreaker breaker = circuitBreakerManager.getCircuitBreaker(service);
                if(breaker!=null&&breaker.allowRequest()){
                    return service;
                }
                metadataList.remove(service);
            }
            throw new RPCException("can not find service");



        }

        private Response doRetry(RPCCallMetrics metrics, List<Metadata> metadataList) throws Exception {
            if(metrics.getThrowable() instanceof ExecutionException ee && ee.getCause() instanceof RPCException rpcException && !rpcException.retry()){
                throw rpcException;
            }
            Response response;
            long functionMS = properties.getFunctionTimeoutMS()-metrics.getDurationMS();
            if (functionMS<=0){
                throw new TimeoutException();
            }
            RetryContext retryContext = createRetryContext(metrics, metadataList, functionMS);
            response = retryPolicy.retry(retryContext);
            return response;
        }

        private @NonNull RetryContext createRetryContext(RPCCallMetrics metrics, List<Metadata> metadataList, long functionMS) {
            RetryContext retryContext = new RetryContext();
            retryContext.setFailService(metrics.getProvider());
            retryContext.setAllService(metadataList);
            retryContext.setFunctionTimeoutMS(functionMS);
            retryContext.setLoadBalancer(loadBalancer);
            retryContext.setRequestTimeout(properties.getRequestTimeoutMS());
            retryContext.setRetry(provider-> {
                CircuitBreaker breaker = circuitBreakerManager.getCircuitBreaker(metrics.getProvider());
                if(!breaker.allowRequest()){
                    CompletableFuture<Response> breakFuture = new CompletableFuture<>();
                    breakFuture.completeExceptionally(new RPCException("provider is break provider:"+provider.toString()));
                    return breakFuture;
                }
                CompletableFuture<Response> requestFuture = callRPCAsync(metrics.getMethod(), metrics.getArgs(), provider);
                RPCCallMetrics retryMetrics = RPCCallMetrics.create(provider, metrics.getMethod(), metrics.getArgs());
                requestFuture.whenComplete((r,e)->{
                    retryMetrics.complete(e);
                    breaker.recordRPC(retryMetrics);
                });
                return requestFuture;
            });
            return retryContext;
        }

        private Object processResponse(Response response) {
            if (response.getCode() != 0) {
                throw new RPCException(response.getMessage());
            }
            return response.getResult();
        }

        private @NonNull Request buildRequest(Method method, Object[] args) {
            Request request = new Request();
            request.setMethodName(method.getName());
            request.setServiceName(interfaceClass.getName());
            request.setParamsType(method.getParameterTypes());
            request.setParams(args);
            return request;
        }

        private @NonNull Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("toString")) {
                return "cade ConsumerProxyFactory" + interfaceClass.getName();
            }
            if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (method.getName().equals("equals")) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException("proxy obj unsupported this method" + method.getName());
        }
    }

}
