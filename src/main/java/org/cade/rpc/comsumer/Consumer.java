package org.cade.rpc.comsumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.cade.rpc.codec.MsgDecoder;
import org.cade.rpc.message.Request;
import org.cade.rpc.codec.RequestEncoder;
import org.cade.rpc.message.Response;

import java.util.concurrent.CompletableFuture;

public class Consumer {
    public int add(int a,int b) throws Exception {
        CompletableFuture<Response> future = new CompletableFuture<>();
        Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup(4);
        bootstrap.group(nioEventLoopGroup).channel(NioSocketChannel.class).handler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                nioSocketChannel.pipeline().addLast(new MsgDecoder())
                        .addLast(new RequestEncoder())
                        .addLast(new SimpleChannelInboundHandler<Response>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
                                System.out.println(response);
                                future.complete(response);
                                channelHandlerContext.close();
                            }
                        });
            }
        });
        ChannelFuture channelFuture = bootstrap.connect("localhost", 10086).sync();
        Request request = new Request();
        request.setMethodName("aaa");
        request.setServiceName("bbb");

        channelFuture.channel().writeAndFlush(request);
        Response res = future.get();
        nioEventLoopGroup.shutdownGracefully();
        return res.hashCode();
    }
}
