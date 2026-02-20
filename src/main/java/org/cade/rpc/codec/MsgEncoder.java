package org.cade.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.cade.rpc.compress.Compression;
import org.cade.rpc.compress.CompressionManager;
import org.cade.rpc.message.Message;
import org.cade.rpc.serialize.Serializer;
import org.cade.rpc.serialize.SerializerManager;

/**
 * RPC 消息编码器，负责将消息对象序列化、压缩后写入网络。
 * <p>
 * 性能优化：第一次编码时从 Channel 属性中读取序列化器和压缩器配置并缓存到实例字段，
 * 后续编码操作直接使用缓存值，避免重复查找。
 * <p>
 * 智能压缩：每次编码时根据数据大小动态判断是否需要压缩。对于小数据，即使用户配置了压缩，
 * 也会跳过压缩步骤（压缩代码回退到 0），避免压缩开销大于收益。
 * <p>
 * 注意：此 Handler 未标注 @Sharable，每个 Channel 拥有独立实例。
 */
@Slf4j(topic = "encoder")
public class MsgEncoder extends MessageToByteEncoder<Object> {
    public static final AttributeKey<String> SERIALIZE_KEY = AttributeKey.valueOf("serializeKey");
    public static final AttributeKey<SerializerManager> SERIALIZER_MANGER_ATTRIBUTE_KEY = AttributeKey.valueOf("serializerMangerKey");
    public static final AttributeKey<String> COMPRESSION_KEY = AttributeKey.valueOf("compressionKey");
    public static final AttributeKey<CompressionManager> COMPRESSION_MANAGER_ATTRIBUTE_KEY = AttributeKey.valueOf("compressionManagerKey");

    // 缓存的序列化器，使用 volatile 保证跨线程可见性
    private volatile Serializer serializer;
    private volatile String serializeKey;

    // 缓存的压缩器：用户配置的压缩器
    private volatile Compression configuredCompression;
    private volatile String configuredCompressionCode;

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) {
        Message.MessageType messageType = Message.getMessageType(msg.getClass());
        if (messageType == null) {
            log.warn("{} not supported serialization", msg.getClass().getName());
            return;
        }

        // 懒加载：第一次编码时初始化序列化器和压缩器
        if (serializer == null) {
            initializeCodecs(ctx);
        }

        byte[] magic = Message.Magic;
        byte[] version = Message.Version;

        // 序列化
        byte[] payload = serializer.serialize(msg);

        // 根据数据大小动态判断是否需要压缩
        int actualCompressionCode=0;
        if (configuredCompression.needCompress(payload)) {
            // 执行压缩
            actualCompressionCode = configuredCompression.code();
            payload = configuredCompression.compress(payload);
        }

        // 动态计算序列化和压缩类型字节：前4位为序列化类型，后4位为压缩类型
        byte serializeAndCompressionByte = (byte) ((serializer.code() << 4) | actualCompressionCode);

        // 计算总长度
        int length = magic.length + Byte.BYTES * 2 + version.length + payload.length;

        // 写入数据
        out.writeInt(length);
        out.writeBytes(magic);
        out.writeBytes(version);
        out.writeByte(messageType.getCode());
        out.writeByte(serializeAndCompressionByte);
        out.writeBytes(payload);
    }

    /**
     * 从 Channel 属性中读取序列化器和压缩器配置，并缓存到实例字段。
     * <p>
     * 此方法只在第一次编码时调用一次，后续编码操作直接使用缓存值。
     */
    private void initializeCodecs(ChannelHandlerContext ctx) {
        // 获取序列化类型代码
        String serializeCodeObj = ctx.channel().attr(SERIALIZE_KEY).get();
        if (serializeCodeObj == null) {
            throw new IllegalArgumentException("Serialize type not set in channel attributes");
        }
        this.serializeKey = serializeCodeObj;

        // 获取压缩类型代码，默认无压缩
        String compressionCodeObj = ctx.channel().attr(COMPRESSION_KEY).get();
        if (compressionCodeObj == null) {
            compressionCodeObj = "none";
        }
        this.configuredCompressionCode = compressionCodeObj;

        // 获取序列化器
        SerializerManager serializerManger = ctx.channel().attr(SERIALIZER_MANGER_ATTRIBUTE_KEY).get();
        if (serializerManger == null) {
            throw new IllegalArgumentException("SerializerManger not set in channel attributes");
        }
        this.serializer = serializerManger.getSerializer(serializeKey);
        if (this.serializer == null) {
            throw new IllegalArgumentException("Unsupported serialize type: " + serializeKey);
        }

        // 获取压缩器
        CompressionManager compressionManager = ctx.channel().attr(COMPRESSION_MANAGER_ATTRIBUTE_KEY).get();
        if (compressionManager == null) {
            compressionManager = new CompressionManager();
        }
        this.configuredCompression = compressionManager.getCompression(configuredCompressionCode);
        if (this.configuredCompression == null) {
            throw new IllegalArgumentException("Unsupported compression type: " + configuredCompressionCode);
        }

        log.debug("Encoder initialized: serialize={}, configuredCompression={}",
                serializeKey, configuredCompressionCode);
    }
}
