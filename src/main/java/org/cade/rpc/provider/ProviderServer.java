package org.cade.rpc.provider;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.codec.MsgDecoder;
import org.cade.rpc.message.Request;
import org.cade.rpc.codec.ResponseEncoder;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.DefaultServiceRegister;
import org.cade.rpc.register.Metadata;
import org.cade.rpc.register.ServiceRegister;

@Slf4j(topic = "provider")
public class ProviderServer {

    private final NioEventLoopGroup connNioEventLoopGroup =   new NioEventLoopGroup();
    private final NioEventLoopGroup workerNioEventLoopGroup =   new NioEventLoopGroup(4);
    private final ServiceRegister serviceRegister;
    private final ProviderRegistry registry;
    private final ProviderProperties properties;

    public <I> void register(Class<I> interfaceClass,I serviceInstance){
        registry.register(interfaceClass,serviceInstance);
    }


    public ProviderServer(ProviderProperties properties) throws Exception {
        this.properties = properties;
        registry = new ProviderRegistry();
        this.serviceRegister = new DefaultServiceRegister(properties.getRegistryConfig());
    }

    public void start(){

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(connNioEventLoopGroup, workerNioEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() { // Change here
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        nioSocketChannel.pipeline().addLast(new MsgDecoder()).addLast(new ResponseEncoder())
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