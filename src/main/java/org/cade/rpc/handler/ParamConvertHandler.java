package org.cade.rpc.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.codec.MsgEncoder;
import org.cade.rpc.message.Request;
import org.cade.rpc.serialize.SerializerManager;
import org.cade.rpc.utils.BaseType;

import java.nio.charset.StandardCharsets;

/**
 * 参数转换处理器
 * <p>
 * 负责将 Request 中的参数转换为实际的方法参数类型。
 * 对于基础类型（int、String等），直接使用原值；
 * 对于复杂类型（如 User 对象），使用 JSON 反序列化为对应的类型。
 * <p>
 * 此 Handler 在 ProviderHandler 之前执行，完成参数转换后，
 * ProviderHandler 可以直接使用转换好的参数进行方法调用。
 */
@Slf4j(topic = "param-convert")
public class ParamConvertHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Request)) {
            ctx.fireChannelRead(msg);
            return;
        }

        Request request = (Request) msg;

        try {
            // 解析参数类型
            Class<?>[] paramsType = resolveMethodParams(request);

            // 转换参数
            Object[] convertedParams = convertMethodParams(ctx, request, paramsType);

            // 将转换后的参数和类型设置回 Request
            request.setParams(convertedParams);
            request.setParamsType(paramsType);

            log.debug("Params converted for request: {}", request.getRequestID());

            // 传递给下一个 handler
            ctx.fireChannelRead(msg);
        } catch (Exception e) {
            log.error("Failed to convert params for request: {}", request.getRequestID(), e);
            throw e;
        }
    }

    /**
     * 解析方法参数类型
     */
    private Class<?>[] resolveMethodParams(Request request) throws ClassNotFoundException {
        if (!request.isGenericInvoke()) {
            return request.getParamsType();
        }

        String[] paramsClassStr = request.getParamsTypeStr();
        Class<?>[] paramsClass = new Class<?>[paramsClassStr.length];
        for (int i = 0; i < paramsClassStr.length; i++) {
            String classStr = paramsClassStr[i];
            paramsClass[i] = analysisFromString(classStr);
        }
        return paramsClass;
    }

    /**
     * 从字符串解析类型
     */
    private Class<?> analysisFromString(String classStr) throws ClassNotFoundException {
        // 首先尝试从基础类型中查找
        Class<?> baseType = BaseType.getClass(classStr);
        if (baseType != null) {
            return baseType;
        }
        // 如果不是基础类型，则使用 Class.forName 加载
        return Class.forName(classStr);
    }

    /**
     * 转换方法参数
     * <p>
     * 对于基础类型，直接使用原值；
     * 对于复杂类型，使用 JSON 反序列化
     */
    @SuppressWarnings("all")
    private Object[] convertMethodParams(ChannelHandlerContext ctx, Request request, Class<?>[] paramsType) {
        Object[] params = request.getParams();
        Object[] result = new Object[params.length];

        // 获取序列化管理器
        SerializerManager serializerManager = ctx.channel().attr(MsgEncoder.SERIALIZER_MANGER_ATTRIBUTE_KEY).get();
        if (serializerManager == null) {
            throw new IllegalStateException("SerializerManager not found in channel attributes");
        }

        for (int i = 0; i < params.length; i++) {
            Class<?> paramType = paramsType[i];
            if (BaseType.is(paramType)) {
                // 基础类型直接使用
                result[i] = params[i];
            } else {
                // 复杂类型进行反序列化
                result[i] = serializerManager.getSerializer("json")
                        .deserialize(params[i].toString().getBytes(StandardCharsets.UTF_8), paramType);
            }
        }
        return result;
    }
}
