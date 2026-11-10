package com.xxl.ai.api.business.harness.chat;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 对话流队列进程内实现（local profile）：任务队列为有界阻塞队列，结果流为按 key 分流的内存列表（条目 id 为 1 起的序号）
 *
 * 仅单机：任务出队即视为确认、无超时认领；重启后未完成的任务与结果流丢失。
 *
 * @author xxl-ai 2026-11-10
 */
@Component
@Profile("local")
public class LocalChatStreamQueue implements ChatStreamQueue {

    /** 任务队列容量 */
    private static final int TASK_QUEUE_CAPACITY = 5000;
    /** 结果流默认保留时长(ms)：结果流创建后尚未设置过期时间时使用 */
    private static final long DEFAULT_RESULT_TTL_MILLIS = 3600_000L;
    /** 清理阈值：结果流数量超过该值时，写入前清理已过期结果流 */
    private static final int PRUNE_THRESHOLD = 200;

    private final BlockingQueue<Entry> tasks = new LinkedBlockingQueue<>(TASK_QUEUE_CAPACITY);
    private final AtomicLong taskSeq = new AtomicLong();
    private final ConcurrentMap<String, ResultStream> results = new ConcurrentHashMap<>();

    @Override
    public void initTaskQueue() {
        // 内存队列随实例创建，无需初始化
    }

    @Override
    public boolean recoverTaskQueue() {
        // 内存队列不会丢失
        return false;
    }

    @Override
    public void addTask(Map<String, String> fields) {
        if (!tasks.offer(new Entry(String.valueOf(taskSeq.incrementAndGet()), Map.copyOf(fields)))) {
            throw new IllegalStateException("对话任务队列已满，请稍后重试");
        }
    }

    @Override
    public List<Entry> pollTask(String consumer, long blockMillis) {
        try {
            Entry task = tasks.poll(blockMillis, TimeUnit.MILLISECONDS);
            return task == null ? Collections.emptyList() : List.of(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    @Override
    public void ackTask(String id) {
        // 出队即确认
    }

    @Override
    public List<Entry> reclaimTasks(String consumer, Duration minIdle) {
        // 单机无跨节点宕机场景，无需认领
        return Collections.emptyList();
    }

    @Override
    public void appendResult(String key, Map<String, String> fields) {
        pruneExpired();
        ResultStream stream = results.computeIfAbsent(key, k -> new ResultStream());
        synchronized (stream) {
            stream.entries.add(new Entry(String.valueOf(stream.entries.size() + 1), Map.copyOf(fields)));
            stream.notifyAll();
        }
    }

    @Override
    public void expireResult(String key, long ttlSeconds) {
        ResultStream stream = results.get(key);
        if (stream != null) {
            stream.expireAt = System.currentTimeMillis() + ttlSeconds * 1000;
        }
    }

    @Override
    public List<Entry> readResults(String key, String fromId, long count, long blockMillis) {
        // 结果流可能尚未产生（worker 未开始写入），先建空流再等待
        ResultStream stream = results.computeIfAbsent(key, k -> new ResultStream());
        int from = parseSeq(fromId);
        long deadline = System.currentTimeMillis() + blockMillis;
        synchronized (stream) {
            while (true) {
                // 有新条目：返回 fromId 之后最多 count 条
                int size = stream.entries.size();
                if (size > from) {
                    return new ArrayList<>(stream.entries.subList(from, (int) Math.min(size, from + count)));
                }
                long waitMillis = deadline - System.currentTimeMillis();
                if (waitMillis <= 0) {
                    return Collections.emptyList();
                }
                try {
                    stream.wait(waitMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Collections.emptyList();
                }
            }
        }
    }

    /**
     * 结果流数量超过阈值时清理已过期结果流
     */
    private void pruneExpired() {
        if (results.size() > PRUNE_THRESHOLD) {
            long now = System.currentTimeMillis();
            results.values().removeIf(stream -> stream.expireAt <= now);
        }
    }

    /**
     * 解析续传断点序号：空值或非序号（如 Redis 模式下的条目 id）从头读取
     */
    private static int parseSeq(String fromId) {
        try {
            return Math.max(0, Integer.parseInt(fromId));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 结果流：条目列表（读写均在自身监视器内）+ 过期时刻
     */
    private static final class ResultStream {
        private final List<Entry> entries = new ArrayList<>();
        private volatile long expireAt = System.currentTimeMillis() + DEFAULT_RESULT_TTL_MILLIS;
    }

}
