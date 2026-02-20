package org.cade.rpc.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流量记录处理器，记录每个连接的上行和下行流量。
 * <p>
 * 此 Handler 应该放在 pipeline 的最前面（紧跟在编解码器之后），
 * 以便准确统计实际网络传输的字节数。
 * <p>
 * 连接建立后，每30秒自动打印一次流量统计信息。
 */
@Slf4j(topic = "traffic")
public class TrafficRecordHandler extends ChannelDuplexHandler {

    // 下行流量计数器（接收的字节数）
    private final AtomicLong bytesRead = new AtomicLong(0);

    // 上行流量计数器（发送的字节数）
    private final AtomicLong bytesWritten = new AtomicLong(0);

    // 接收的消息数
    private final AtomicLong messagesRead = new AtomicLong(0);

    // 发送的消息数
    private final AtomicLong messagesWritten = new AtomicLong(0);

    // 定时打印任务
    private ScheduledFuture<?> reportTask;

    // 统计报告间隔（秒）
    private static final int REPORT_INTERVAL_SECONDS = 30;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Connection {} established, starting traffic monitoring", ctx.channel().remoteAddress());

        // 启动定时任务，每30秒打印一次流量统计
        reportTask = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> printTrafficStats(ctx),
                REPORT_INTERVAL_SECONDS,
                REPORT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        ctx.fireChannelActive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 记录下行流量
        if (msg instanceof ByteBuf buf) {
            long bytes = buf.readableBytes();
            bytesRead.addAndGet(bytes);
            messagesRead.incrementAndGet();
        }

        // 传递给下一个 Handler
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // 记录上行流量
        if (msg instanceof ByteBuf buf) {
            long bytes = buf.readableBytes();
            bytesWritten.addAndGet(bytes);
            messagesWritten.incrementAndGet();
        }

        // 传递给下一个 Handler
        ctx.write(msg, promise);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 取消定时任务
        if (reportTask != null) {
            reportTask.cancel(false);
        }

        // 连接关闭时记录最终统计信息
        log.info("Connection {} closed. Final traffic stats: read {} ({} msgs), written {} ({} msgs)",
                ctx.channel().remoteAddress(),
                formatBytes(bytesRead.get()),
                messagesRead.get(),
                formatBytes(bytesWritten.get()),
                messagesWritten.get());

        ctx.fireChannelInactive();
    }

    /**
     * 打印当前流量统计信息
     */
    private void printTrafficStats(ChannelHandlerContext ctx) {
        try {
            long currentBytesRead = bytesRead.get();
            long currentBytesWritten = bytesWritten.get();
            long currentMessagesRead = messagesRead.get();
            long currentMessagesWritten = messagesWritten.get();

            log.info("Connection {} - Traffic report: read {} ({} msgs), written {} ({} msgs)",
                    ctx.channel().remoteAddress(),
                    formatBytes(currentBytesRead),
                    currentMessagesRead,
                    formatBytes(currentBytesWritten),
                    currentMessagesWritten);
        } catch (Exception e) {
            log.error("Error printing traffic stats", e);
        }
    }

    /**
     * 获取接收的字节数
     */
    public long getBytesRead() {
        return bytesRead.get();
    }

    /**
     * 获取发送的字节数
     */
    public long getBytesWritten() {
        return bytesWritten.get();
    }

    /**
     * 获取接收的消息数
     */
    public long getMessagesRead() {
        return messagesRead.get();
    }

    /**
     * 获取发送的消息数
     */
    public long getMessagesWritten() {
        return messagesWritten.get();
    }

    /**
     * 获取流量统计摘要
     */
    public TrafficStats getStats() {
        return new TrafficStats(
                bytesRead.get(),
                bytesWritten.get(),
                messagesRead.get(),
                messagesWritten.get()
        );
    }

    /**
     * 重置统计信息
     */
    public void reset() {
        bytesRead.set(0);
        bytesWritten.set(0);
        messagesRead.set(0);
        messagesWritten.set(0);
    }

    /**
     * 格式化字节数为易读格式
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2fKB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2fMB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 流量统计数据
     */
    public static class TrafficStats {
        public final long bytesRead;
        public final long bytesWritten;
        public final long messagesRead;
        public final long messagesWritten;

        public TrafficStats(long bytesRead, long bytesWritten, long messagesRead, long messagesWritten) {
            this.bytesRead = bytesRead;
            this.bytesWritten = bytesWritten;
            this.messagesRead = messagesRead;
            this.messagesWritten = messagesWritten;
        }

        @Override
        public String toString() {
            return String.format("Traffic{read=%d bytes (%d msgs), written=%d bytes (%d msgs)}",
                    bytesRead, messagesRead, bytesWritten, messagesWritten);
        }
    }
}
