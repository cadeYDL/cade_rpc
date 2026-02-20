package org.cade.rpc.compress;

import lombok.Getter;

public interface Compression {
    byte[] compress(byte[] data);

    byte[] decompress(byte[] data);

    /**
     * 判断给定的数据是否有必要压缩。
     * <p>
     * 对于较小的数据（如几百字节），压缩算法的开销可能大于压缩收益，
     * 压缩后的体积甚至可能更大。此方法用于决定是否跳过压缩步骤。
     *
     * @param data 待压缩的数据
     * @return true 表示应该压缩；false 表示应该跳过压缩
     */
    default boolean needCompress(byte[] data) {
        return true;
    }

    enum CompressionType {
        NONE(0, new NoneCompression()),
        GZIP(1, new GzipCompression()),
        ZSTD(2, new ZstdCompression());

        @Getter
        private final int typeCode;
        @Getter
        private final Compression compression;

        CompressionType(int typeCode, Compression compression) {
            this.typeCode = typeCode;
            this.compression = compression;
        }

        static CompressionType getCompressionTypeFromCode(int code) {
            switch (code) {
                case 0:
                    return NONE;
                case 1:
                    return GZIP;
                case 2:
                    return ZSTD;
                default:
                    throw new IllegalArgumentException("No such compression type: " + code);
            }
        }
    }
}
