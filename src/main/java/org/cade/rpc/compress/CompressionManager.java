package org.cade.rpc.compress;

import java.util.HashMap;
import java.util.Map;

/**
 * 压缩管理器，负责管理和提供不同类型的压缩实现。
 * <p>
 * 使用方式：
 * <pre>{@code
 * CompressionManager manager = new CompressionManager();
 * Compression compression = manager.getCompression(1); // 获取 GZIP 压缩器
 * byte[] compressed = compression.compress(data);
 * }</pre>
 */
public class CompressionManager {
    private final Map<Integer, Compression> compressionMap = new HashMap<>();

    public CompressionManager() {
        init();
    }

    /**
     * 根据压缩类型代码获取对应的压缩实现。
     *
     * @param compressionType 压缩类型代码（0=NONE, 1=GZIP, 2=ZSTD）
     * @return 对应的压缩实现，若类型不存在则返回 null
     */
    public Compression getCompression(int compressionType) {
        return compressionMap.get(compressionType);
    }

    private void init() {
        for (Compression.CompressionType type : Compression.CompressionType.values()) {
            compressionMap.put(type.getTypeCode(), type.getCompression());
        }
    }
}
