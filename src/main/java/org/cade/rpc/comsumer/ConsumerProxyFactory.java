package org.cade.rpc.comsumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.codec.MsgDecoder;
import org.cade.rpc.codec.RequestEncoder;
import org.cade.rpc.excpetion.RPCException;
import org.cade.rpc.loadbalance.LoadBalancer;
import org.cade.rpc.loadbalance.RandomLoadBalancer;
import org.cade.rpc.loadbalance.RoundRobinLoadBalancer;
import org.cade.rpc.message.Request;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.DefaultServiceRegister;
import org.cade.rpc.register.Metadata;
import org.cade.rpc.register.ServiceRegister;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// 感觉ConsumerProxyFactory中的inFlightRequestTable和ConnectionManager应该交由外部去维护
// 为啥这里需要动态代理？一个简单的模板方法也能够解决这个问题
@Slf4j(topic = "consumer_proxy_factory")
public class ConsumerProxyFactory {
    private final Map<Integer, CompletableFuture<Response>> inFlightRequestTable ;
    private final ConnectionManager connectionManager;
    private final ServiceRegister serviceRegister;
    private final ConsumerProperties properties;

    ConsumerProxyFactory(ConsumerProperties properties) throws Exception {
        this.connectionManager = new ConnectionManager(createBootstrap(properties));
        this.serviceRegister = new DefaultServiceRegister(properties.getRegistryConfig());
        this.inFlightRequestTable = new ConcurrentHashMap<>();
        this.properties = properties;
    }

    private Bootstrap createBootstrap(ConsumerProperties properties) {
        Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup(properties.getWorkThreadNum());
        bootstrap.group(nioEventLoopGroup).channel(NioSocketChannel.class).option(ChannelOption.CONNECT_TIMEOUT_MILLIS,properties.getConnectTimeoutMS()).handler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                nioSocketChannel.pipeline().addLast(new MsgDecoder())
                        .addLast(new RequestEncoder())
                        .addLast(new ConsumerHnadler());
            }
        });
        return bootstrap;
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

    private class ConsumerHnadler extends SimpleChannelInboundHandler<Response>{

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
            CompletableFuture<Response> f = inFlightRequestTable.remove(response.getRequestId());
            if (f == null) {
                log.warn("can not find request id:{}", response.getRequestId());
            }
            f.complete(response);
        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Exception",cause);
            ctx.channel().close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("Service{} close",ctx.channel().remoteAddress());
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("New Service:{}",ctx.channel().remoteAddress());
        }
    }

    public <I> I getConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{interfaceClass}, new ConsumerInvocationHandler<>(interfaceClass,createLoadBalancer()));
    }

    public class ConsumerInvocationHandler<I> implements InvocationHandler {
        private final Class<I> interfaceClass;
        private final LoadBalancer loadBalancer;
        ConsumerInvocationHandler(Class<I> interfaceClass, LoadBalancer loadBalancer) {
            this.interfaceClass = interfaceClass;
            this.loadBalancer = loadBalancer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            Request request = buildRequest(method, args);
            CompletableFuture<Response> future = new CompletableFuture<>();
            List<Metadata> metadataList = serviceRegister.fetchServicelist(interfaceClass.getName());
            if (metadataList.isEmpty()) {
                throw new RPCException("can not find service");
            }
            try {
                Metadata service = loadBalancer.select(metadataList);
                Channel channel = connectionManager.getChannel(service.getHost(), service.getPort());
                if (channel == null) {
                    throw new RPCException("can not connect to server");
                }
                inFlightRequestTable.put(request.getRequestID(), future);
                channel.writeAndFlush(request).addListener(f -> {
                    if (!f.isSuccess()) {
                        inFlightRequestTable.remove(request.getRequestID(), future);
                        future.completeExceptionally(f.cause());
                    }
                });
                Response res = future.get(properties.getRequestTimeoutMS(), TimeUnit.MILLISECONDS);
                return processResponse(res);
            } catch (Exception e) {
                inFlightRequestTable.remove(request.getRequestID());
                throw new RuntimeException(e);
            }
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
