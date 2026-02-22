package org.cade.rpc.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.codec.MsgEncoder;
import org.cade.rpc.message.Response;
import org.cade.rpc.serialize.SerializerManager;
import org.cade.rpc.utils.BaseType;

import java.nio.charset.StandardCharsets;

/**
 * 结果转换处理器
 * <p>
 * 负责将 Response 中的结果对象转换为可序列化的格式。
 * 对于基础类型（int、String等），直接使用原值；
 * 对于复杂类型（如 User 对象），序列化为 JSON 字符串。
 * <p>
 * 此 Handler 在 ProviderHandler 之后、MsgEncoder 之前执行，
 * 确保返回结果已经转换为可序列化的格式。
 */
@Slf4j(topic = "result-convert")
public class ResultConvertHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof Response)) {
            ctx.write(msg, promise);
            return;
        }

        Response response = (Response) msg;

        try {
            // 只有成功的响应才需要转换结果
            if (response.getCode() != null && response.getCode() == 0 && response.getResult() != null) {
                Object convertedResult = convertResult(ctx, response.getResult());
                response.setResult(convertedResult);
                log.debug("Result converted for response: {}", response.getRequestId());
            }

            // 传递给下一个 handler
            ctx.write(msg, promise);
        } catch (Exception e) {
            log.error("Failed to convert result for response: {}", response.getRequestId(), e);
            throw e;
        }
    }

    /**
     * 转换结果对象
     * <p>
     * 对于基础类型，直接返回原值；
     * 对于复杂类型，序列化为 JSON 字符串
     */
    private Object convertResult(ChannelHandlerContext ctx, Object result) {
        Class<?> resultClass = result.getClass();

        // 基础类型直接返回
        if (BaseType.is(resultClass)) {
            return result;
        }

        // 获取序列化管理器
        SerializerManager serializerManager = ctx.channel().attr(MsgEncoder.SERIALIZER_MANGER_ATTRIBUTE_KEY).get();
        if (serializerManager == null) {
            throw new IllegalStateException("SerializerManager not found in channel attributes");
        }

        // 复杂类型序列化为 JSON 字符串
        byte[] serialized = serializerManager.getSerializer("json").serialize(result);
        return new String(serialized, StandardCharsets.UTF_8);
    }
}
