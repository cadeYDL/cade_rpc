package org.cade.rpc.provider;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import org.cade.rpc.codec.MsgDecoder;
import org.cade.rpc.message.Request;
import org.cade.rpc.codec.ResponseEncoder;
import org.cade.rpc.message.Response;


public class ProviderServer {
    private final int port;
    private final NioEventLoopGroup connNioEventLoopGroup =   new NioEventLoopGroup();
    private final NioEventLoopGroup workerNioEventLoopGroup =   new NioEventLoopGroup(4);
    public ProviderServer(int port) {
        this.port = port;
    }

    public void start(){
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(connNioEventLoopGroup, workerNioEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() { // Change here
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        nioSocketChannel.pipeline().addLast(new MsgDecoder()).addLast(new ResponseEncoder())
                                .addLast(new SimpleChannelInboundHandler<Request>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Request request) throws Exception {
                                        System.out.println(request);
                                        Response resp = new Response();
                                        resp.setResult(1);
                                        channelHandlerContext.writeAndFlush(resp);
                                    }
                                });
                    }
                });
        try {
            serverBootstrap.bind(this.port).sync();
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

    public static void main(String[] args) throws Exception {

    }

    private static int add(int a, int b) {
        return a + b;
    }
}