package org.cade.rpc.provider;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.codec.MsgEncoder;
import org.cade.rpc.codec.MsgDecoder;
import org.cade.rpc.compress.CompressionManager;
import org.cade.rpc.handler.HeartbeatHandler;
import org.cade.rpc.handler.ParamConvertHandler;
import org.cade.rpc.handler.ResultConvertHandler;
import org.cade.rpc.handler.TrafficRecordHandler;
import org.cade.rpc.limit.ConcurrencyLimiter;
import org.cade.rpc.limit.Limiter;
import org.cade.rpc.limit.RateLimiter;
import org.cade.rpc.message.Request;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.DefaultServiceRegister;
import org.cade.rpc.register.Metadata;
import org.cade.rpc.register.ServiceRegister;
import org.cade.rpc.serialize.SerializerManager;
import org.cade.rpc.trace.TraceContext;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j(topic = "provider")
public class ProviderServer {

    private final NioEventLoopGroup connNioEventLoopGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup workerNioEventLoopGroup = new NioEventLoopGroup(4);
    private final ServiceRegister serviceRegister;
    private final ProviderRegistry registry;
    private final ProviderProperties properties;
    private final Limiter globelLimter;
    private final SerializerManager serializerManger;
    private final CompressionManager compressionManager;
    private final ThreadPoolExecutor invokeExcutor;

    public <I> void register(Class<I> interfaceClass, I serviceInstance) {
        registry.register(interfaceClass, serviceInstance);
    }

    public <I> void register(Class<I> interfaceClass, I serviceInstance, org.cade.rpc.interceptor.InterceptorConfig config) {
        registry.register(interfaceClass, serviceInstance, config);
    }


    public ProviderServer(ProviderProperties properties) throws Exception {
        this.properties = properties;
        registry = new ProviderRegistry();
        this.serviceRegister = new DefaultServiceRegister(properties.getRegistryConfig());
        globelLimter = new ConcurrencyLimiter(properties.getGlobelMaxRequest());
        this.serializerManger = new SerializerManager();
        this.compressionManager = new CompressionManager();
        this.invokeExcutor = new ThreadPoolExecutor(4, 4, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024));
    }

    public void start() {

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(connNioEventLoopGroup, workerNioEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() { // Change here
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        nioSocketChannel.pipeline()
                                .addLast(new TrafficRecordHandler())
                                .addLast(new MsgDecoder())
                                .addLast(new MsgEncoder())
                                .addLast(new IdleStateHandler(30, 5, 0, TimeUnit.SECONDS))
                                .addLast(new HeartbeatHandler())
                                .addLast(new LimitHandler())
                                .addLast(new ParamConvertHandler())
                                .addLast(new ResultConvertHandler())
                                .addLast(new ProviderHandler());
                    }
                });
        try {
            serverBootstrap.bind(this.properties.getPort()).sync();
            registry.allServiceNames().stream().map(this::buildMetadata).forEach(serviceRegister::register);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        if (this.connNioEventLoopGroup != null) {
            this.connNioEventLoopGroup.shutdownGracefully();
        }
        if (this.workerNioEventLoopGroup != null) {
            this.workerNioEventLoopGroup.shutdownGracefully();
        }
    }

    private Metadata buildMetadata(String serviceName) {
        Metadata metadata = new Metadata();
        metadata.setHost(properties.getHost());
        metadata.setPort(properties.getPort());
        metadata.setServiceName(serviceName);
        return metadata;
    }

    private class LimitHandler extends ChannelDuplexHandler {
        private static final AttributeKey<Limiter> CHANNEL_LIMITER_KEY = AttributeKey.valueOf("channel_limiter_key");
        private static final AttributeKey<AtomicInteger> GLOBEL_PERMITS = AttributeKey.valueOf("globel_permits");


        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Request request = (Request) msg;
            if (!globelLimter.tryAcquire()) {
                ctx.writeAndFlush(Response.error("globel provider limiter", request.getRequestID()));
                return;
            } else {
                ctx.channel().attr(GLOBEL_PERMITS).get().incrementAndGet();
            }

            Limiter limiter = ctx.channel().attr(CHANNEL_LIMITER_KEY).get();
            if (!limiter.tryAcquire()) {
                globelLimter.release();
                ctx.writeAndFlush(Response.error("channel provider limiter", request.getRequestID()));
                return;
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

            promise.addListener(f -> {
                ctx.channel().attr(CHANNEL_LIMITER_KEY).get().release();
                if (ctx.channel().attr(GLOBEL_PERMITS).get().getAndDecrement() > 0) {
                    globelLimter.release();
                }
            });
            ctx.write(msg, promise);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            int remain = ctx.channel().attr(GLOBEL_PERMITS).get().getAndSet(0);
            globelLimter.release(remain);
            ctx.fireChannelInactive();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.channel().attr(CHANNEL_LIMITER_KEY).set(new RateLimiter(properties.getPreConsumerMax()));
            ctx.channel().attr(GLOBEL_PERMITS).set(new AtomicInteger(0));
            ctx.fireChannelActive();
        }
    }

    private class ProviderHandler extends SimpleChannelInboundHandler<Request> {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Exception", cause);
            ctx.channel().close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("Client:{} close", ctx.channel().remoteAddress());
            ctx.channel().attr(MsgEncoder.SERIALIZE_KEY).set(null);
            ctx.channel().attr(MsgEncoder.SERIALIZER_MANGER_ATTRIBUTE_KEY).set(null);
            ctx.channel().attr(MsgEncoder.COMPRESSION_KEY).set(null);
            ctx.channel().attr(MsgEncoder.COMPRESSION_MANAGER_ATTRIBUTE_KEY).set(null);
            ctx.fireChannelInactive();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("New Client:{}", ctx.channel().remoteAddress());
            ctx.channel().attr(MsgEncoder.SERIALIZE_KEY).set(properties.getSerializer());
            ctx.channel().attr(MsgEncoder.SERIALIZER_MANGER_ATTRIBUTE_KEY).set(serializerManger);

            ctx.channel().attr(MsgEncoder.COMPRESSION_KEY).set(properties.getCompress());
            ctx.channel().attr(MsgEncoder.COMPRESSION_MANAGER_ATTRIBUTE_KEY).set(compressionManager);

            ctx.fireChannelActive();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Request request) throws Exception {
            ProviderRegistry.Invocation service = registry.getService(request.getServiceName());
            if (service == null) {
                ctx.writeAndFlush(Response.error(String.format("No such service %s", request.getServiceName()), request.getRequestID()));
                return;
            }
            invokeExcutor.execute(new InvokeTask(request, ctx, service));


        }

        private class InvokeTask implements Runnable {
            private final Request request;
            private final ChannelHandlerContext ctx;
            private final ProviderRegistry.Invocation invocation;

            InvokeTask(Request request, ChannelHandlerContext ctx, ProviderRegistry.Invocation service) {
                this.request = request;
                this.ctx = ctx;
                this.invocation = service;
            }

            @Override
            public void run() {
                EventLoop eventLoop = ctx.channel().eventLoop();

                // 初始化 TraceContext
                String traceId = request.getTraceId();
                if (traceId == null || traceId.isEmpty()) {
                    // 如果请求中没有 traceId，生成一个新的
                    traceId = TraceContext.start();
                    log.debug("Generated new traceId: {}", traceId);
                } else {
                    // 使用请求中的 traceId
                    TraceContext.setTraceId(traceId);
                    log.debug("Inherited traceId: {}", traceId);
                }

                try {
                    // 参数已经在 ParamConvertHandler 中转换完成，直接使用
                    Object result = invocation.invoke(
                            request.getMethodName(),
                            request.getParamsType(),
                            request.getParams()
                    );
                    log.info("Request:{} result:{}", request, result);

                    // 创建响应并设置 traceId
                    // 结果将在 ResultConvertHandler 中转换
                    Response response = Response.ok(result, request.getRequestID());
                    response.setTraceId(traceId);
                    eventLoop.execute(() -> ctx.writeAndFlush(response));
                } catch (Exception e) {
                    log.error("Request processing failed", e);
                    // 创建错误响应并设置 traceId
                    Response errorResponse = Response.error(String.format("Call Function Fail err:%s", e), request.getRequestID());
                    errorResponse.setTraceId(traceId);
                    eventLoop.execute(() -> ctx.writeAndFlush(errorResponse));
                } finally {
                    // 清理 TraceContext，防止 ThreadLocal 内存泄漏
                    TraceContext.clear();
                }
            }
        }

        private class FastFailResponseHandler implements RejectedExecutionHandler {

            @Override
            public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
                if (task instanceof InvokeTask invokeTask) {
                    Response fastFail = Response.error("service busy", invokeTask.request.getRequestID());
                    invokeTask.ctx.channel().eventLoop().execute(() -> invokeTask.ctx.writeAndFlush(fastFail));
                    return;
                }
                throw new RuntimeException("unexpected task");
            }
        }
    }

}