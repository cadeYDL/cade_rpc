package org.cade.rpc.comsumer;

import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.breaker.CircuitBreaker;
import org.cade.rpc.breaker.CircuitBreakerManager;
import org.cade.rpc.excpetion.RPCException;
import org.cade.rpc.fallback.CacheFallback;
import org.cade.rpc.fallback.DefaultFallback;
import org.cade.rpc.fallback.Fallback;
import org.cade.rpc.fallback.MockFallback;
import org.cade.rpc.loadbalance.LoadBalancer;
import org.cade.rpc.loadbalance.LoadBalancerManager;
import org.cade.rpc.message.Request;
import org.cade.rpc.message.Response;
import org.cade.rpc.metrics.RPCCallMetrics;
import org.cade.rpc.register.DefaultServiceRegister;
import org.cade.rpc.register.Metadata;
import org.cade.rpc.register.ServiceRegister;
import org.cade.rpc.retry.*;
import org.cade.rpc.serialize.JSONSerializer;
import org.cade.rpc.serialize.Serializer;
import org.cade.rpc.trace.TraceContext;
import org.cade.rpc.utils.BaseType;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
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
    private final Fallback fallback;
    private final RetryManager retryManager;
    private final LoadBalancerManager loadBalancerManager;
    private final Serializer jsonSerializer;


    public ConsumerProxyFactory(ConsumerProperties properties) throws Exception {
        this.jsonSerializer = new JSONSerializer();
        this.inflightRequestManager = new InflightRequestManager(properties);
        this.retryManager = new RetryManager();
        this.loadBalancerManager = new LoadBalancerManager();
        this.connectionManager = new ConnectionManager(inflightRequestManager, properties);
        this.serviceRegister = new DefaultServiceRegister(properties.getRegistryConfig());
        this.circuitBreakerManager = new CircuitBreakerManager(properties);

        this.properties = properties;
        this.fallback = new DefaultFallback(new CacheFallback(), new MockFallback());
    }


    private LoadBalancer createLoadBalancer() {
        LoadBalancer loadBalancer = this.loadBalancerManager.getLoadBalancer(properties.getLoadBalancePolicy());
        if (loadBalancer == null) {
            throw new RPCException("unknown load balance policy: " + properties.getLoadBalancePolicy());
        }
        return loadBalancer;
    }


    public <I> I getConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{interfaceClass}, new ConsumerInvocationHandler<>(interfaceClass, createLoadBalancer(), createRetryPolicy(properties.getRetryPolicy())));
    }

    private RetryPolicy createRetryPolicy(String retryPolicyName) {
        RetryPolicy retryPolicy = this.retryManager.getRetryPolicy(retryPolicyName);
        if (retryPolicy == null) {
            throw new IllegalArgumentException(properties.getRetryPolicy() + " this retry policy not exist");
        }
        return retryPolicy;
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
            boolean genericInvoke = isGenericInvoke(method);
            String serviceName = genericInvoke?args[0].toString() : interfaceClass.getName();


            List<Metadata> metadataList = new ArrayList<>(serviceRegister.fetchServicelist(serviceName));
            Metadata service = decideProvider(metadataList);
            RPCCallMetrics metrics = RPCCallMetrics.create(service, method, args);
            if (service == null) {
                return fallback.fallback(metrics);
            }
            Response response;

            CircuitBreaker breaker = circuitBreakerManager.getCircuitBreaker(service);
            try {
                CompletableFuture<Response> future = callRPCAsync(metrics.getMethod(), metrics.getArgs(), service);
                response = future.get(properties.getRequestTimeoutMS(), TimeUnit.MILLISECONDS);
                metrics.complete(response);
                return processResponse(response,method,args);
            } catch (Exception e) {
                metrics.complete(e);
            } finally {
                breaker.recordRPC(metrics);
                fallback.recordMetrics(metrics);
            }
            try {
                return processResponse(doRetry(metrics, metadataList),method,args);
            } catch (Exception e) {
                return fallback.fallback(metrics);
            }
        }

        private CompletableFuture<Response> callRPCAsync(Method method, Object[] args, Metadata provider) {
            Request request = buildRequest(method, args);
            Channel channel = connectionManager.getChannel(provider);
            CompletableFuture<Response> responseFuture = inflightRequestManager.inFlightRequest(request, properties.getRequestTimeoutMS(), provider);
            if (channel == null) {
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

        private Metadata decideProvider(List<Metadata> metadataList) throws Exception {
            while (!metadataList.isEmpty()) {
                Metadata service = loadBalancer.select(metadataList);
                CircuitBreaker breaker = circuitBreakerManager.getCircuitBreaker(service);
                if (breaker != null && breaker.allowRequest()) {
                    return service;
                }
                metadataList.remove(service);
            }
            return null;

        }

        private Response doRetry(RPCCallMetrics metrics, List<Metadata> metadataList) throws Exception {
            if (metrics.getThrowable() instanceof ExecutionException ee && ee.getCause() instanceof RPCException rpcException && !rpcException.retry()) {
                throw rpcException;
            }
            Response response;
            long functionMS = properties.getFunctionTimeoutMS() - metrics.getDurationMS();
            if (functionMS <= 0) {
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
            retryContext.setRetry(provider -> {
                CircuitBreaker breaker = circuitBreakerManager.getCircuitBreaker(metrics.getProvider());
                if (!breaker.allowRequest()) {
                    CompletableFuture<Response> breakFuture = new CompletableFuture<>();
                    breakFuture.completeExceptionally(new RPCException("provider is break provider:" + provider.toString()));
                    return breakFuture;
                }
                CompletableFuture<Response> requestFuture = callRPCAsync(metrics.getMethod(), metrics.getArgs(), provider);
                RPCCallMetrics retryMetrics = RPCCallMetrics.create(provider, metrics.getMethod(), metrics.getArgs());
                requestFuture.whenComplete((r, e) -> {
                    if (e != null) {
                        retryMetrics.complete(e);
                    } else {
                        retryMetrics.complete(r);
                    }
                    breaker.recordRPC(retryMetrics);
                    fallback.recordMetrics(retryMetrics);
                });
                return requestFuture;
            });
            return retryContext;
        }

        private Object processResponse(Response response, Method method, Object[] args) {

            if (response.getCode() != 0) {
                throw new RPCException(response.getMessage());
            }
            boolean genericInvoke = isGenericInvoke(method);
            Object result = response.getResult();
            Class<?> returnClass = method.getReturnType();
            if(!genericInvoke && returnClass!=void.class && !BaseType.is(returnClass)){
                result = jsonSerializer.deserialize(result.toString().getBytes(StandardCharsets.UTF_8),returnClass);
            }
            return result;
        }

        private @NonNull Request buildRequest(Method method, Object[] args) {
            boolean genericInvoke = isGenericInvoke(method);
            Request request = new Request();

            // 设置 TraceID（从 TraceContext 获取或生成新的）
            String traceId = TraceContext.getOrCreate();
            request.setTraceId(traceId);

            if (genericInvoke) {
                request.setGenericInvoke(true);
                request.setServiceName(args[0].toString());
                String[] requestParamsType =(String[]) args[2];
                request.setParamsTypeStr(requestParamsType);
                request.setParams(prepareRequestParams((Object[]) args[3],requestParamsType));
                request.setMethodName(args[1].toString());
            }else{
                request.setServiceName(interfaceClass.getName());
                request.setParamsType(method.getParameterTypes());
                request.setParams(prepareRequestParams(args,method.getParameterTypes()));
                request.setMethodName(method.getName());

            }

            return request;
        }

        private Object[] prepareRequestParams(Object[] requestParams, String[] requestParamsType) {
            return requestParams;
        }

        private Object[] prepareRequestParams(Object[] requestParams, Class<?>[] requestParamsType) {
            Object[] res = new Object[requestParams.length];
            for(int i=0;i<requestParams.length;i++) {
                if(BaseType.is(requestParamsType[i])){
                    res[i] = requestParams[i];
                    continue;
                }
                res[i] = new String(jsonSerializer.serialize(requestParams[i]),StandardCharsets.UTF_8);
            }
            return res;
        }

        private static boolean isGenericInvoke(Method method) {
            boolean genericInvoke = method.getName().equals("$invoke");
            return genericInvoke;
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
