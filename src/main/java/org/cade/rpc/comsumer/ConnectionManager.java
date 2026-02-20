package org.cade.rpc.comsumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.codec.MsgEncoder;
import org.cade.rpc.codec.MsgDecoder;
import org.cade.rpc.compress.Compression;
import org.cade.rpc.compress.CompressionManager;
import org.cade.rpc.handler.HeartbeatHandler;
import org.cade.rpc.handler.TrafficRecordHandler;
import org.cade.rpc.message.Response;
import org.cade.rpc.register.Metadata;
import org.cade.rpc.serialize.Serializer;
import org.cade.rpc.serialize.SerializerManger;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "connection_manager")
public class ConnectionManager {
    private final Map<String, ChannelWrapper> channelTable;
    private final Bootstrap bootstrap;
    private final InflightRequestManager inflightRequestManager;
    private final ConsumerProperties properties;
    private final SerializerManger serializerManger;
    private final CompressionManager compressionManager;

    public ConnectionManager(InflightRequestManager inflightRequestManager, ConsumerProperties properties){
        channelTable = new ConcurrentHashMap<>();
        this.bootstrap = createBootstrap(properties);
        this.inflightRequestManager = inflightRequestManager;
        this.properties = properties;
        this.serializerManger = new SerializerManger();
        this.compressionManager = new CompressionManager();
    }

    private Bootstrap createBootstrap(ConsumerProperties properties) {
        Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup(properties.getWorkThreadNum());
        bootstrap.group(nioEventLoopGroup).channel(NioSocketChannel.class).option(ChannelOption.CONNECT_TIMEOUT_MILLIS,properties.getConnectTimeoutMS()).handler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                nioSocketChannel.pipeline()
                        .addLast(new TrafficRecordHandler())
                        .addLast(new MsgDecoder())
                        .addLast(new MsgEncoder())
                        .addLast(new IdleStateHandler(30, 5, 0, TimeUnit.SECONDS))
                        .addLast(new HeartbeatHandler())
                        .addLast(new ConsumerHnadler());
            }
        });
        return bootstrap;
    }

    private String getKey(String host, int port){
        return host + ":" + port;
    }

    public Channel getChannel(Metadata metadata){
        String key = getKey(metadata.getHost(), metadata.getPort());
        ChannelWrapper cw = channelTable.computeIfAbsent(key,(k)->{
            Channel channel=null;
            try {
                ChannelFuture cf = bootstrap.connect(metadata.getHost(), metadata.getPort()).sync();
                channel = cf.channel();
                channel.closeFuture().addListener(future->{
                    channelTable.remove(key);
                    inflightRequestManager.clearChannel(metadata);
                });
            } catch (InterruptedException e) {
                log.error("connect error {}:{} err:{}",metadata.getHost(),metadata.getPort(),e);
            }
            return new ChannelWrapper(channel);
        });
        Channel channel = cw.channel;
        if (channel==null||!channel.isActive()){
            channelTable.remove(key);
            channel=null;
        }
        return channel;
    }

    private static class ChannelWrapper{
        private final Channel channel;
        ChannelWrapper(Channel channel){
            this.channel = channel;
        }
    }

    private class ConsumerHnadler extends SimpleChannelInboundHandler<Response> {

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
            inflightRequestManager.completeRequst(response.getRequestId(),response);
        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Exception",cause);
            ctx.channel().close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("Service{} close",ctx.channel().remoteAddress());
            ctx.channel().attr(MsgEncoder.SERIALIZE_KEY).set(null);
            ctx.channel().attr(MsgEncoder.SERIALIZER_MANGER_ATTRIBUTE_KEY).set(null);
            ctx.channel().attr(MsgEncoder.COMPRESSION_KEY).set(null);
            ctx.channel().attr(MsgEncoder.COMPRESSION_MANAGER_ATTRIBUTE_KEY).set(null);
            ctx.fireChannelInactive();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("New Service:{}",ctx.channel().remoteAddress());

            Serializer.SerializerType serializerType = Serializer.SerializerType.valueOf(properties.getSerializer().toUpperCase(Locale.ROOT));
            ctx.channel().attr(MsgEncoder.SERIALIZE_KEY).set(serializerType.getTypeCode());
            ctx.channel().attr(MsgEncoder.SERIALIZER_MANGER_ATTRIBUTE_KEY).set(serializerManger);

            // 设置压缩类型，默认为 NONE
            Compression.CompressionType compressionType;
            if (properties.getCompress() == null || properties.getCompress().isEmpty()) {
                compressionType = Compression.CompressionType.NONE;
            } else {
                compressionType = Compression.CompressionType.valueOf(properties.getCompress().toUpperCase(Locale.ROOT));
            }
            ctx.channel().attr(MsgEncoder.COMPRESSION_KEY).set(compressionType.getTypeCode());
            ctx.channel().attr(MsgEncoder.COMPRESSION_MANAGER_ATTRIBUTE_KEY).set(compressionManager);

            ctx.fireChannelActive();
        }
    }
}
