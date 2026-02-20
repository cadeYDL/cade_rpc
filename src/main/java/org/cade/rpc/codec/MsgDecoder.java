package org.cade.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.AttributeKey;
import org.cade.rpc.compress.Compression;
import org.cade.rpc.compress.CompressionManager;
import org.cade.rpc.message.Message;
import org.cade.rpc.serialize.Serializer;
import org.cade.rpc.serialize.SerializerManager;

import java.util.Arrays;

/**
 * RPC 消息解码器，负责从网络读取数据并反序列化、解压为消息对象。
 * <p>
 * 性能优化：第一次解码时从 Channel 属性中读取序列化器管理器和压缩器管理器并缓存到实例字段，
 * 后续解码操作直接使用缓存值，避免重复查找。
 * <p>
 * 注意：此 Handler 未标注 @Sharable，每个 Channel 拥有独立实例。
 */
public class MsgDecoder extends LengthFieldBasedFrameDecoder {
    private final static int MaxLength = 1024 * 1024;
    private static final AttributeKey<SerializerManager> SERIALIZER_MANGER_ATTRIBUTE_KEY = AttributeKey.valueOf("serializerMangerKey");
    private static final AttributeKey<CompressionManager> COMPRESSION_MANAGER_ATTRIBUTE_KEY = AttributeKey.valueOf("compressionManagerKey");

    // 缓存的管理器实例，使用 volatile 保证跨线程可见性
    private volatile SerializerManager serializerManger;
    private volatile CompressionManager compressionManager;

    public MsgDecoder() {
        super(MaxLength, 0, Integer.BYTES, 0, Integer.BYTES);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // 懒加载：第一次解码时从 Channel 属性中获取管理器
        if (serializerManger == null) {
            initializeManagers(ctx);
        }

        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        try {
            byte[] magic = new byte[Message.Magic.length];
            frame.readBytes(magic);
            if (!Arrays.equals(magic, Message.Magic)) {
                throw new IllegalArgumentException("magic error");
            }
            byte[] version = new byte[Message.Version.length];
            frame.readBytes(version);

            byte messageType = frame.readByte();

            // 读取序列化和压缩类型字节
            byte serializeAndCompressionByte = frame.readByte();
            // 前4位为序列化类型
            int serializeCode = (serializeAndCompressionByte >>> 4) & 0x0F;
            // 后4位为压缩类型
            int compressionCode = serializeAndCompressionByte & 0x0F;

            // 读取 payload
            byte[] payload = new byte[frame.readableBytes()];
            frame.readBytes(payload);

            // 解压
            Compression compression = compressionManager.getCompression(compressionCode);
            if (compression == null) {
                throw new IllegalArgumentException("不支持的压缩类型: " + compressionCode);
            }
            payload = compression.decompress(payload);

            // 反序列化
            Serializer serializer = serializerManger.getSerializer(serializeCode);
            if (serializer == null) {
                throw new IllegalArgumentException("不支持的序列化类型: " + serializeCode);
            }

            // 根据消息类型代码获取消息类型
            Message.MessageType msgType = Message.getMessageTypeFromCode((int) messageType);
            if (msgType == null) {
                throw new IllegalArgumentException("不支持的msgType: " + messageType);
            }

            // 直接反序列化为对应的消息类型
            return serializer.deserialize(payload, msgType.getMessageClass());
        } finally {
            frame.release();
        }
    }

    /**
     * 从 Channel 属性中读取序列化器管理器和压缩器管理器，并缓存到实例字段。
     * <p>
     * 此方法只在第一次解码时调用一次，后续解码操作直接使用缓存值。
     */
    private void initializeManagers(ChannelHandlerContext ctx) {
        // 从 Channel 属性中获取 SerializerManger
        this.serializerManger = ctx.channel().attr(SERIALIZER_MANGER_ATTRIBUTE_KEY).get();
        if (this.serializerManger == null) {
            // 如果没有设置，创建默认实例
            this.serializerManger = new SerializerManager();
        }

        // 从 Channel 属性中获取 CompressionManager
        this.compressionManager = ctx.channel().attr(COMPRESSION_MANAGER_ATTRIBUTE_KEY).get();
        if (this.compressionManager == null) {
            // 如果没有设置，创建默认实例
            this.compressionManager = new CompressionManager();
        }
    }
}
