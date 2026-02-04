package org.cade.rpc.codec;

import com.alibaba.fastjson2.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.cade.rpc.message.Message;
import org.cade.rpc.message.Response;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ResponseEncoder extends MessageToByteEncoder<Response> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Response response, ByteBuf out) throws Exception {
        byte[] magic = Message.Magic;
        Message.MessageType messageType = Message.MessageType.RESPONSE;
        byte[] version = Message.Version;
        byte[] payload = serializeRespnse(response);
        int length = magic.length+Byte.BYTES+version.length+payload.length;
        out.writeInt(length);
        out.writeBytes(magic);
        out.writeBytes(version);
        out.writeByte(messageType.getCode());
        out.writeBytes(payload);
    }

    private byte[] serializeRespnse(Response response){
        return JSONObject.toJSONString(response).getBytes(StandardCharsets.UTF_8);
    }
}
