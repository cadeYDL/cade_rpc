package org.cade.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.cade.rpc.message.Message;
import org.cade.rpc.message.Request;
import org.cade.rpc.message.Response;

import java.util.Arrays;
import java.util.Objects;

public class MsgDecoder extends LengthFieldBasedFrameDecoder {
    private final static int MaxLength = 1024*1024;
    public MsgDecoder(){
        super(MaxLength,0,Integer.BYTES,0,Integer.BYTES);
    }
    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx,in);

        byte[] magic = new byte[Message.Magic.length];
        frame.readBytes(magic);
        if(!Arrays.equals(magic,Message.Magic)){
            throw new IllegalArgumentException("magic error");
        }
        byte[] version = new byte[Message.Version.length];
        frame.readBytes(version);

        byte messageType = frame.readByte();
        byte[] payload = new byte[frame.readableBytes()];
        frame.readBytes(payload);
        if(Message.MessageType.REQUEST.getCode()==messageType){
            return deserialzeRequst(payload);
        }
        if(Message.MessageType.RESPONSE.getCode()==messageType){
            return deserialzeResponse(payload);
        }

        throw new IllegalArgumentException("不支持的msgType"+messageType);
    }

    private Response deserialzeResponse(byte[] payload) {
        return JSONObject.parseObject(new String(payload),Response.class);
    }

    private Request deserialzeRequst(byte[] payload) {
        return JSONObject.parseObject(new String(payload),Request.class);
    }
}
