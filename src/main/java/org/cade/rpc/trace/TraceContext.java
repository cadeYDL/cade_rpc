package org.cade.rpc.trace;

import org.slf4j.MDC;

/**
 * TraceContext - 分布式链路追踪上下文
 * <p>
 * 使用 InheritableThreadLocal 存储当前线程的 TraceID，实现无侵入式的链路追踪。
 * <p>
 * 同时集成 SLF4J 的 MDC，自动将 TraceID 添加到日志中。
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 * // 开始一个新的 Trace
 * TraceContext.start();
 * try {
 *     // 业务逻辑
 *     // 所有日志都会自动包含 TraceID
 *     log.info("Processing request");
 * } finally {
 *     // 清理 TraceContext
 *     TraceContext.clear();
 * }
 *
 * // 获取当前 TraceID
 * String traceId = TraceContext.getTraceId();
 *
 * // 设置外部传入的 TraceID
 * TraceContext.setTraceId("external-trace-id");
 * }</pre>
 * <p>
 * <b>并发安全：</b>
 * <ul>
 *   <li>使用 InheritableThreadLocal 实现线程隔离和自动继承</li>
 *   <li>子线程自动继承父线程的 TraceID（仅限 new Thread()）</li>
 *   <li>每个线程拥有独立的 TraceID 副本</li>
 *   <li>不存在线程安全问题</li>
 * </ul>
 * <p>
 * <b>多线程场景：</b>
 * <ul>
 *   <li>new Thread()：自动继承，无需额外处理</li>
 *   <li>CompletableFuture：使用默认线程池时自动继承</li>
 *   <li>线程池：需要使用 TraceExecutors.wrap() 包装，参见 {@link TraceExecutors}</li>
 *   <li>复杂场景：建议使用 TransmittableThreadLocal (TTL)</li>
 * </ul>
 *
 * @see TraceExecutors
 * @see TraceIdGenerator
 */
public class TraceContext {

    /**
     * MDC 中 TraceID 的 Key
     */
    private static final String MDC_TRACE_ID_KEY = "traceId";

    /**
     * InheritableThreadLocal 存储当前线程的 TraceID
     * <p>
     * 使用 InheritableThreadLocal 而非 ThreadLocal，确保子线程能够继承父线程的 TraceID
     * <p>
     * <b>注意：</b>
     * <ul>
     *   <li>适用于 new Thread() 创建的线程</li>
     *   <li>对于线程池场景，建议使用 TransmittableThreadLocal</li>
     * </ul>
     */
    private static final InheritableThreadLocal<String> TRACE_ID_HOLDER = new InheritableThreadLocal<>();

    /**
     * 私有构造函数，防止实例化
     */
    private TraceContext() {
    }

    /**
     * 开始一个新的 Trace
     * <p>
     * 生成新的 TraceID 并设置到当前线程
     *
     * @return 新生成的 TraceID
     */
    public static String start() {
        String traceId = TraceIdGenerator.generate();
        setTraceId(traceId);
        return traceId;
    }

    /**
     * 获取当前线程的 TraceID
     * <p>
     * 如果当前线程没有 TraceID，则返回 null
     *
     * @return TraceID 字符串，如果不存在则返回 null
     */
    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }

    /**
     * 获取当前线程的 TraceID，如果不存在则生成一个新的
     * <p>
     * 此方法保证返回值非 null
     *
     * @return TraceID 字符串（保证非 null）
     */
    public static String getOrCreate() {
        String traceId = TRACE_ID_HOLDER.get();
        if (traceId == null) {
            traceId = TraceIdGenerator.generate();
            setTraceId(traceId);
        }
        return traceId;
    }

    /**
     * 设置当前线程的 TraceID
     * <p>
     * 同时会将 TraceID 设置到 MDC 中，以便在日志中使用
     *
     * @param traceId TraceID 字符串
     */
    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            clear();
            return;
        }

        TRACE_ID_HOLDER.set(traceId);
        // 同时设置到 MDC，方便日志输出
        MDC.put(MDC_TRACE_ID_KEY, traceId);
    }

    /**
     * 清理当前线程的 TraceID
     * <p>
     * 在请求处理完成后调用，避免 ThreadLocal 内存泄漏
     * <p>
     * <b>重要：</b>建议在 finally 块中调用此方法
     */
    public static void clear() {
        TRACE_ID_HOLDER.remove();
        MDC.remove(MDC_TRACE_ID_KEY);
    }

    /**
     * 判断当前线程是否存在 TraceID
     *
     * @return true 表示存在，false 表示不存在
     */
    public static boolean exists() {
        return TRACE_ID_HOLDER.get() != null;
    }

    /**
     * 继承父线程的 TraceID
     * <p>
     * 用于异步任务场景，将父线程的 TraceID 传递给子线程
     *
     * @param parentTraceId 父线程的 TraceID
     */
    public static void inherit(String parentTraceId) {
        if (parentTraceId != null && !parentTraceId.isEmpty()) {
            setTraceId(parentTraceId);
        }
    }
}
