package org.cade.rpc.provider;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.codec.MsgDecoder;
import org.cade.rpc.limit.ConcurrencyLimiter;
import org.cade.rpc.limit.Limiter;
import org.cade.rpc.limit.RateLimiter;
import org.cade.rpc.message.Request;
import org.cade.rpc.codec.ResponseEncoder;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.DefaultServiceRegister;
import org.cade.rpc.register.Metadata;
import org.cade.rpc.register.ServiceRegister;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j(topic = "provider")
public class ProviderServer {

    private final NioEventLoopGroup connNioEventLoopGroup =   new NioEventLoopGroup();
    private final NioEventLoopGroup workerNioEventLoopGroup =   new NioEventLoopGroup(4);
    private final ServiceRegister serviceRegister;
    private final ProviderRegistry registry;
    private final ProviderProperties properties;
    private final Limiter globelLimter;

    public <I> void register(Class<I> interfaceClass,I serviceInstance){
        registry.register(interfaceClass,serviceInstance);
    }


    public ProviderServer(ProviderProperties properties) throws Exception {
        this.properties = properties;
        registry = new ProviderRegistry();
        this.serviceRegister = new DefaultServiceRegister(properties.getRegistryConfig());
        globelLimter = new ConcurrencyLimiter(properties.getGlobelMaxRequest());
    }

    public void start(){

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(connNioEventLoopGroup, workerNioEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() { // Change here
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        nioSocketChannel.pipeline()
                                .addLast(new MsgDecoder())
                                .addLast(new ResponseEncoder())
                                .addLast(new LimitHandler())
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

    public void stop(){
        if(this.connNioEventLoopGroup!=null){
            this.connNioEventLoopGroup.shutdownGracefully();
        }
        if (this.workerNioEventLoopGroup!=null){
            this.workerNioEventLoopGroup.shutdownGracefully();
        }
    }

    private Metadata buildMetadata(String serviceName){
        Metadata metadata = new Metadata();
        metadata.setHost(properties.getHost());
        metadata.setPort(properties.getPort());
        metadata.setServiceName(serviceName);
        return metadata;
    }

    private class LimitHandler extends ChannelDuplexHandler {
        private static final AttributeKey<Limiter> CHANNEL_LIMITER_KEY = AttributeKey.valueOf("channel_limiter_key");
        private static final AttributeKey<AtomicInteger>  GLOBEL_PERMITS = AttributeKey.valueOf("globel_permits");


        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Request request = (Request) msg;
            if(!globelLimter.tryAcquire()){
                ctx.writeAndFlush(Response.error("globel provider limiter",request.getRequestID()));
                return;
            }else{
                ctx.channel().attr(GLOBEL_PERMITS).get().incrementAndGet();
            }

            Limiter limiter = ctx.channel().attr(CHANNEL_LIMITER_KEY).get();
            if(!limiter.tryAcquire()){
                globelLimter.release();
                ctx.writeAndFlush(Response.error("channel provider limiter",request.getRequestID()));
                return;
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

            promise.addListener(f->{
                ctx.channel().attr(CHANNEL_LIMITER_KEY).get().release();
                if(ctx.channel().attr(GLOBEL_PERMITS).get().getAndDecrement()>0){
                    globelLimter.release();
                }
            });
            ctx.write(msg,promise);
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

    private class ProviderHandler extends SimpleChannelInboundHandler<Request>{
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Exception",cause);
            ctx.channel().close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("Client:{} close",ctx.channel().remoteAddress());
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
           log.info("New Client:{}",ctx.channel().remoteAddress());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Request request) throws Exception {
            ProviderRegistry.Invocation service = registry.getService(request.getServiceName());
            if(service==null){
                channelHandlerContext.writeAndFlush(Response.error(String.format("No such service %s",request.getServiceName()),request.getRequestID()));
                return;
            }
            try{
                Object result = service.invoke(request.getMethodName(),request.getParamsType(),request.getParams());
                log.info("Request:{} result:{}",request,result);
                channelHandlerContext.writeAndFlush(Response.ok(result,request.getRequestID()));
            }catch (Exception e){
                channelHandlerContext.writeAndFlush(Response.error(String.format("Call Function Fail err:%s",e),request.getRequestID()));
            }

        }
    }

}