package org.cade.rpc.breaker;

import org.cade.rpc.metrics.RPCCallMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

public class ResponseTimeCircuitBreaker implements CircuitBreaker {
    private final long breakTimeMS = 100000;
    private volatile long breakStartTime = 0;
    private final AtomicReference<State> stateRef = new AtomicReference<>(State.CLOSED);
    private final long windowMS;
    private final double failRatio;
    private final int minRequstCount =10;
    private final long slotIntervalMS = 5000;
    private final Function<RPCCallMetrics, Boolean> isFail = (metrics ->
            !metrics.isComplete() || metrics.getThrowable() != null || metrics.getDurationMS() > 2000);
    private final RingBuffer<Slot> buffer;

    public ResponseTimeCircuitBreaker(long windowsSec, double slowRequestBreakRatio) {
        this.windowMS = windowsSec*1000;
        buffer = new RingBuffer<>((int) (windowMS / slotIntervalMS) + 1);
        // 初始化第一个时间槽，避免首次 getLastNode() 返回 null
        buffer.add(new Slot());
        this.failRatio = slowRequestBreakRatio;
    }

    @Override
    public boolean allowRequest() {
        if (stateRef.get() == State.CLOSED) {
            return true;
        }
        if (stateRef.get() == State.HALFEN) {
            return false;
        }
        if (System.currentTimeMillis() - breakStartTime < breakTimeMS) {
            return false;
        }
        return stateRef.compareAndSet(State.OPEN, State.HALFEN);
    }

    @Override
    public void recordRPC(RPCCallMetrics metrics) {
        Slot lastNode = buffer.getLastNode();
        if (lastNode.startTime + slotIntervalMS < System.currentTimeMillis()) {
            buffer.compareAndSet(lastNode, new Slot());
            lastNode = buffer.getLastNode();
        }
        lastNode.totalRequest.incrementAndGet();
        boolean fail =isFail.apply(metrics);
        if (fail) {
            lastNode.failRequest.incrementAndGet();
        }
        switch (stateRef.get()){
            case OPEN -> processOPEN(fail);
            case CLOSED -> processCLOSED(fail);
            case HALFEN -> processHALFEN(fail);
        }
    }

    private void processHALFEN(boolean fail) {
        if(!fail){
            stateRef.compareAndSet(State.HALFEN, State.CLOSED);
        }
        stateRef.compareAndSet(State.HALFEN, State.OPEN);
    }

    private void processCLOSED(boolean fail) {
        if(!fail){
            return;
        }
        long now = System.currentTimeMillis();
        int total =0;
        int failCount =0;
        for(Slot slot:buffer.snapshot()){
            if (slot.startTime>=now-windowMS){
                total+=slot.totalRequest.get();
                failCount+=slot.failRequest.get();
            }
        }
        if(total<minRequstCount){
            return;
        }
        if((double) failCount /total>=failRatio&&stateRef.compareAndSet(State.CLOSED, State.OPEN)){
            this.breakStartTime = System.currentTimeMillis();
        }
    }

    private void processOPEN(boolean fail) {

    }


    private static class Slot {
        final long startTime = System.currentTimeMillis();
        final AtomicInteger totalRequest = new AtomicInteger(0);
        final AtomicInteger failRequest = new AtomicInteger(0);
    }

    /**
     * 并发安全、容量可配置的环形缓冲区。
     *
     * <p>并发安全保证：
     * <ul>
     *   <li>{@link AtomicReferenceArray} 为数组每个槽位提供 volatile 可见性。</li>
     *   <li>{@link AtomicInteger#getAndIncrement()} 原子占槽，不同线程的并发写入
     *       永远不会落在同一个数组下标。</li>
     *   <li>{@link #tail} 持有最新写入元素的引用，{@link #getLastNode()} 和
     *       {@link #compareAndSet} 均操作此字段，避免通过 writeIndex 反算下标时的
     *       可见性窗口。</li>
     * </ul>
     *
     * @param <T> 存储的元素类型
     */
    static class RingBuffer<T> {

        private final int capacity;
        // 每个槽位具有 volatile 语义，保证跨线程可见性
        private final AtomicReferenceArray<T> buffer;
        // 单调递增写指针
        private final AtomicInteger writeIndex = new AtomicInteger(0);
        // 累计写入次数，用于计算有效元素数量
        private final AtomicInteger totalWrites = new AtomicInteger(0);
        // 最新写入的元素引用；getLastNode/compareAndSet 操作此字段
        private final AtomicReference<T> tail = new AtomicReference<>(null);

        RingBuffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
            this.buffer = new AtomicReferenceArray<>(capacity);
        }

        /**
         * 写入一个元素。当缓冲区已满时覆盖最旧的槽位，并更新 tail。
         *
         * <p>溢出防护：{@code & Integer.MAX_VALUE} 屏蔽符号位，确保 writeIndex
         * 溢出为负数后取模结果仍然合法。
         */
        void add(T element) {
            writeToBuffer(element);
            tail.set(element);
        }

        /**
         * 返回最近一次写入的元素。
         *
         * <p>直接读取 {@link #tail}，无需通过 writeIndex 反算下标，
         * 不存在与写入操作之间的可见性窗口。
         *
         * @return 最新元素，若缓冲区为空则返回 null
         */
        public T getLastNode() {
            return tail.get();
        }

        /**
         * 以 CAS 方式将当前最新槽从 {@code expected} 替换为 {@code update}。
         *
         * <p>典型场景：多线程并发检测到当前时间槽已过期，均尝试推进到新槽。
         * CAS 保证只有一个线程真正完成推进并写入环形数组，其余线程 CAS 失败后
         * 直接通过 {@link #getLastNode()} 取得胜出线程写入的新槽，不产生重复写入。
         *
         * @param expected 期望的当前最新元素（引用相等性判断）
         * @param update   若 CAS 成功，写入的新元素
         * @return CAS 是否成功
         */
        public boolean compareAndSet(T expected, T update) {
            // CAS 保证只有一个线程完成时间槽轮转
            if (tail.compareAndSet(expected, update)) {
                // 胜出线程将新槽写入环形数组，以便 snapshot() 统计历史数据
                writeToBuffer(update);
                return true;
            }
            return false;
        }

        /**
         * 返回当前窗口内所有有效元素的快照（无序）。
         * 快照为独立副本，不受后续写入影响。
         */
        List<T> snapshot() {
            int size = size();
            List<T> result = new ArrayList<>(size);
            for (int i = 0; i < capacity; i++) {
                T element = buffer.get(i);
                if (element != null) {
                    result.add(element);
                }
            }
            return result;
        }

        /**
         * 当前窗口内的有效元素数量：未满时等于实际写入次数，已满后恒等于 capacity。
         */
        int size() {
            return Math.min(totalWrites.get(), capacity);
        }

        /**
         * 缓冲区是否已经被填满过（至少写入过 capacity 次）。
         */
        boolean isFull() {
            return totalWrites.get() >= capacity;
        }

        /**
         * 重置缓冲区，清空所有槽位、计数器和 tail 引用。
         */
        void reset() {
            for (int i = 0; i < capacity; i++) {
                buffer.set(i, null);
            }
            writeIndex.set(0);
            totalWrites.set(0);
            tail.set(null);
        }

        /** 将元素写入环形数组的下一个槽位（不更新 tail）。 */
        private void writeToBuffer(T element) {
            int rawIdx = writeIndex.getAndIncrement();
            int slot = (rawIdx & Integer.MAX_VALUE) % capacity;
            buffer.set(slot, element);
            totalWrites.incrementAndGet();
        }
    }
}
