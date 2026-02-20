package org.cade.rpc.compress;

import org.cade.rpc.spi.SPI;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;

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
    private static final Logger logger = Logger.getLogger(CompressionManager.class.getName());
    private final Map<Integer, Compression> compressionMap = new HashMap<>();
    private final Map<String, Compression> compressionNameMap = new HashMap<>();

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

    /**
     * 根据压缩名称获取对应的压缩实现。
     *
     * @param name 压缩名称（例如：gzip, zstd, none）
     * @return 对应的压缩实现，若名称不存在则返回 null
     */
    public Compression getCompression(String name) {
        return compressionNameMap.get(name.toUpperCase(Locale.ROOT));
    }

    private void init() {
        ServiceLoader<Compression> loader = ServiceLoader.load(Compression.class);
        for (Compression compression : loader) {
            int code = compression.code();
            String name = compression.getName();

            if (code >= 16) {
                throw new IllegalArgumentException("compressionType must be less than 16");
            }
            if (compressionMap.put(code, compression) != null) {
                throw new IllegalArgumentException("compressionType must be unique");
            }
            if (compressionNameMap.put(name.toUpperCase(Locale.ROOT), compression) != null) {
                throw new IllegalArgumentException("compressionName must be unique");
            }
        }
    }
}
