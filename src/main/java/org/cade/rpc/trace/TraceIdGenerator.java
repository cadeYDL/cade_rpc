package org.cade.rpc.trace;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TraceID 生成器
 * <p>
 * 生成全局唯一的 TraceID，格式：timestamp-hostId-sequence-random
 * <p>
 * 示例：1708502400000-192168001100-000001-a3f2
 * <p>
 * 组成部分：
 * <ul>
 *   <li>timestamp: 当前时间戳（毫秒）</li>
 *   <li>hostId: 主机标识（IP 地址的数字形式）</li>
 *   <li>sequence: 递增序列号（6位十进制，每秒重置）</li>
 *   <li>random: 4位随机十六进制数</li>
 * </ul>
 * <p>
 * 线程安全，支持高并发场景
 */
public class TraceIdGenerator {

    /**
     * 主机标识（从 IP 地址生成）
     */
    private static final String HOST_ID;

    /**
     * 序列号计数器（每秒重置）
     */
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    /**
     * 上次重置序列号的时间戳（秒）
     */
    private static volatile long lastResetTime = 0;

    /**
     * 序列号最大值（999999）
     */
    private static final long MAX_SEQUENCE = 999999;

    static {
        HOST_ID = generateHostId();
    }

    /**
     * 生成主机标识
     * <p>
     * 从本机 IP 地址生成唯一标识
     *
     * @return 主机标识字符串（12位数字）
     */
    private static String generateHostId() {
        try {
            InetAddress address = InetAddress.getLocalHost();
            byte[] ipBytes = address.getAddress();

            // 将 IP 地址的每个字节转换为 3 位数字
            StringBuilder sb = new StringBuilder(12);
            for (byte b : ipBytes) {
                int value = b & 0xFF;
                sb.append(String.format("%03d", value));
            }
            return sb.toString();
        } catch (UnknownHostException e) {
            // 如果无法获取 IP，使用随机数
            long random = ThreadLocalRandom.current().nextLong(1000000000000L);
            return String.format("%012d", random);
        }
    }

    /**
     * 生成全局唯一的 TraceID
     * <p>
     * 格式：timestamp-hostId-sequence-random
     * <p>
     * 线程安全，支持高并发
     *
     * @return TraceID 字符串
     */
    public static String generate() {
        long timestamp = System.currentTimeMillis();
        long currentSecond = timestamp / 1000;

        // 检查是否需要重置序列号（每秒重置一次）
        if (currentSecond > lastResetTime) {
            synchronized (TraceIdGenerator.class) {
                if (currentSecond > lastResetTime) {
                    SEQUENCE.set(0);
                    lastResetTime = currentSecond;
                }
            }
        }

        // 获取序列号（原子递增）
        long sequence = SEQUENCE.getAndIncrement();
        if (sequence > MAX_SEQUENCE) {
            // 如果序列号超过最大值，回绕到 0
            SEQUENCE.compareAndSet(sequence + 1, 0);
            sequence = sequence % (MAX_SEQUENCE + 1);
        }

        // 生成随机数（4位十六进制）
        int random = ThreadLocalRandom.current().nextInt(0x10000);

        // 组装 TraceID
        return String.format("%d-%s-%06d-%04x",
                timestamp,      // 时间戳
                HOST_ID,        // 主机标识
                sequence,       // 序列号
                random          // 随机数
        );
    }

    /**
     * 验证 TraceID 格式是否合法
     *
     * @param traceId TraceID 字符串
     * @return true 表示格式合法，false 表示不合法
     */
    public static boolean isValid(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return false;
        }

        // 格式：timestamp-hostId-sequence-random
        String[] parts = traceId.split("-");
        if (parts.length != 4) {
            return false;
        }

        try {
            // 验证时间戳
            Long.parseLong(parts[0]);
            // 验证主机标识（12位数字）
            if (parts[1].length() != 12) {
                return false;
            }
            Long.parseLong(parts[1]);
            // 验证序列号（6位数字）
            if (parts[2].length() != 6) {
                return false;
            }
            Long.parseLong(parts[2]);
            // 验证随机数（4位十六进制）
            if (parts[3].length() != 4) {
                return false;
            }
            Integer.parseInt(parts[3], 16);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 从 TraceID 中提取时间戳
     *
     * @param traceId TraceID 字符串
     * @return 时间戳（毫秒），如果格式不合法则返回 -1
     */
    public static long extractTimestamp(String traceId) {
        if (!isValid(traceId)) {
            return -1;
        }
        String[] parts = traceId.split("-");
        return Long.parseLong(parts[0]);
    }
}
