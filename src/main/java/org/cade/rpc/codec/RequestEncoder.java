package org.cade.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.cade.rpc.message.Message;
import org.cade.rpc.message.Request;

import java.nio.charset.StandardCharsets;

public class RequestEncoder extends MessageToByteEncoder<Request> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Request request, ByteBuf out) throws Exception {
        byte[] magic = Message.Magic;
        Message.MessageType messageType = Message.MessageType.REQUEST;
        byte[] version = Message.Version;
        byte[] payload = serializeRequest(request);
        int length = magic.length+Byte.BYTES+version.length+payload.length;
        out.writeInt(length);
        out.writeBytes(magic);
        out.writeBytes(version);
        out.writeByte(messageType.getCode());
        out.writeBytes(payload);
    }

    private byte[] serializeRequest(Request request){
        return JSONObject.toJSONString(request).getBytes(StandardCharsets.UTF_8);
    }
}
