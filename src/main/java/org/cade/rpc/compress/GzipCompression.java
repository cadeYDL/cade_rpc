package org.cade.rpc.compress;

import org.cade.rpc.excpetion.CompressionException;
import org.cade.rpc.spi.SPI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZIP 压缩实现。
 * <p>
 * 使用 Java 内置的 GZIP 算法（基于 DEFLATE），压缩率适中，兼容性好。
 * 适用于对带宽敏感但 CPU 资源充足的场景。
 */
public class GzipCompression implements Compression {

    /**
     * 压缩阈值：小于此字节数的数据不值得压缩。
     * <p>
     * GZIP 头部约 10-18 字节，对于小数据压缩收益可能小于开销。
     */
    private static final int COMPRESSION_THRESHOLD = 512;

    @Override
    public String getName() {
        return "gzip";
    }

    @Override
    public int code() {
        return 1;
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

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
             GZIPOutputStream gzipOut = new GZIPOutputStream(bos)) {
            gzipOut.write(data);
            gzipOut.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new CompressionException("GZIP compression failed", e);
        }
    }

    @Override
    public byte[] decompress(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             GZIPInputStream gzipIn = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new CompressionException("GZIP decompression failed", e);
        }
    }
}
