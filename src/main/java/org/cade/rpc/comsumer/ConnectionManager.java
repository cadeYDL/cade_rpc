package org.cade.rpc.comsumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j(topic = "connection_manager")
public class ConnectionManager {
    private final Map<String, ChannelWrapper> channelTable;
    private final Bootstrap bootstrap;

    public ConnectionManager(Bootstrap bootstrap){
        channelTable = new ConcurrentHashMap<>();
        this.bootstrap = bootstrap;
    }

    private String getKey(String host, int port){
        return host + ":" + port;
    }

    public Channel getChannel(String host, int port){
        String key = getKey(host, port);
        ChannelWrapper cw = channelTable.computeIfAbsent(key,(k)->{
            Channel channel=null;
            try {
                ChannelFuture cf = bootstrap.connect(host, port).sync();
                channel = cf.channel();
                channel.closeFuture().addListener(future->{
                channelTable.remove(key);
                });
            } catch (InterruptedException e) {
                log.error("connect error {}:{} err:{}",host,port,e);
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
}
