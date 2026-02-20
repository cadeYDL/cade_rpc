package org.cade.rpc.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.cade.rpc.message.HeartbeatRequest;
import org.cade.rpc.message.HeartbeatResponse;

public class HeartbeatHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HeartbeatRequest requst){
            ctx.writeAndFlush(new HeartbeatResponse(requst.getRequestTime()));
            return;
        }
        if(msg instanceof HeartbeatResponse response){
            long duration = System.currentTimeMillis()-response.getRequestTime();
            System.out.println("receive heartbeat response:"+duration+"ms");
            return;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleStateEvent) {
            IdleState state = idleStateEvent.state();
            if(state==IdleState.READER_IDLE){
                ctx.channel().close();
            }else if (state==IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(new HeartbeatRequest());
            }

        }
        ctx.fireUserEventTriggered(evt);
    }
}
