package org.cade.rpc.limit;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Cade
 * @date 2024/7/19
 * @Description: 基于令牌桶算法的限流器（无锁并发安全实现）
 */
public class RateLimiter implements Limiter {

    // 桶容量
    private final long capacity;
    // 每个令牌对应的纳秒数（= 1s / rate）
    private final long nanosPerToken;
    // 当前令牌数量
    private final AtomicLong tokens;
    // 上次令牌生成时间（纳秒），只推进"已消耗令牌"对应的时间，保留余量精度
    private final AtomicLong lastRefillTimestamp;

    public RateLimiter(long capacity) {
        if ( capacity <= 0) {
            throw new IllegalArgumentException("rate and capacity must be positive");
        }
        this.capacity = capacity;
        this.nanosPerToken = TimeUnit.SECONDS.toNanos(1) / capacity;
        this.tokens = new AtomicLong(capacity);
        this.lastRefillTimestamp = new AtomicLong(System.nanoTime());
    }


    @Override
    public boolean tryAcquire() {
        refill();
        // CAS 自旋消费令牌，避免加锁
        while (true) {
            long current = tokens.get();
            if (current < 1) {
                return false;
            }
            if (tokens.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    /**
     * 补充令牌。
     * <p>
     * 时间戳只前进"已生成令牌所对应的纳秒数"，而非推进到 now，
     * 从而保留不足一个令牌的时间余量，避免精度损失。
     * <p>
     * 使用 CAS 保证同一时间只有一个线程真正执行补充，其余线程
     * CAS 失败后直接返回（此时令牌已由胜出线程补充完毕）。
     */
    private void refill() {
        long now = System.nanoTime();
        long lastRefill = lastRefillTimestamp.get();
        long elapsed = now - lastRefill;

        long newTokens = elapsed / nanosPerToken;
        if (newTokens <= 0) {
            return;
        }

        // 只推进"已消耗令牌"对应的纳秒，保留余量给下一次补充
        long newLastRefill = lastRefill + newTokens * nanosPerToken;
        if (lastRefillTimestamp.compareAndSet(lastRefill, newLastRefill)) {
            // 成功占据补充权，将令牌加入桶中（不超过容量上限）
            tokens.updateAndGet(current -> Math.min(capacity, current + newTokens));
        }
        // CAS 失败说明其他线程已完成本轮补充，无需重复操作
    }

    @Override
    public void release() {
        // No need to implement
    }

    @Override
    public void release(int remain) {

    }
}
