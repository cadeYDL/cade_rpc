package org.cade.rpc.compress;

import com.github.luben.zstd.Zstd;
import org.cade.rpc.excpetion.CompressionException;
import org.cade.rpc.spi.SPI;

/**
 * Zstandard (zstd) 压缩实现。
 * <p>
 * Zstd 是由 Facebook 开源的高性能压缩算法，提供：
 * <ul>
 *   <li>高压缩率（接近 LZMA）</li>
 *   <li>高压缩/解压速度（远超 GZIP）</li>
 *   <li>可调压缩级别（1-22，默认 3）</li>
 * </ul>
 * <p>
 * <b>依赖要求：</b>需要在 pom.xml 中添加：
 * <pre>{@code
 * <dependency>
 *     <groupId>com.github.luben</groupId>
 *     <artifactId>zstd-jni</artifactId>
 *     <version>1.5.5-11</version>
 * </dependency>
 * }</pre>
 */
public class ZstdCompression implements Compression {

    /**
     * 压缩阈值：小于此字节数的数据不值得压缩。
     * <p>
     * Zstd 头部约 5-9 字节，但对于小数据压缩收益仍可能较小。
     */
    private static final int COMPRESSION_THRESHOLD = 256;

    /**
     * 压缩级别（1-22），默认 3。
     * <ul>
     *   <li>1-3: 快速压缩，适合实时场景</li>
     *   <li>3-6: 平衡模式（推荐）</li>
     *   <li>7-22: 高压缩率，耗时更长</li>
     * </ul>
     */
    private final int compressionLevel;

    public ZstdCompression() {
        this(3); // 默认级别 3，平衡速度和压缩率
    }

    public ZstdCompression(int compressionLevel) {
        if (compressionLevel < 1 || compressionLevel > 22) {
            throw new IllegalArgumentException("Zstd compression level must be between 1 and 22");
        }
        this.compressionLevel = compressionLevel;
    }

    @Override
    public String getName() {
        return "zstd";
    }

    @Override
    public int code() {
        return 2;
    }

    @Override
    public boolean needCompress(byte[] data) {
        return data != null && data.length >= COMPRESSION_THRESHOLD;
    }

    @Override
    public byte[] compress(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        try {
            return Zstd.compress(data, compressionLevel);
        } catch (Exception e) {
            throw new CompressionException("Zstd compression failed", e);
        }
    }

    @Override
    public byte[] decompress(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        try {
            // 获取原始数据大小（Zstd 会在压缩数据头部存储）
            long decompressedSize = Zstd.decompressedSize(data);
            if (decompressedSize == 0) {
                // 无法确定大小时，使用动态缓冲
                return Zstd.decompress(data, (int) (data.length * 3));
            }
            return Zstd.decompress(data, (int) decompressedSize);
        } catch (Exception e) {
            throw new CompressionException("Zstd decompression failed", e);
        }
    }
}
