package com.xxl.ai.api.business.harness.chat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 对话流队列：任务队列（投递/竞争消费/确认/超时认领）+ 结果流（按 key 追加/续传读取/过期）
 *
 * 默认 Redis Stream 实现（RedisChatStreamQueue，支持多节点）；local profile 为进程内实现（LocalChatStreamQueue，仅单机）。
 *
 * @author xxl-ai 2026-11-10
 */
public interface ChatStreamQueue {

    /**
     * 队列条目：id 为条目标识（结果流中即续传断点 lastEventId），fields 为字段
     */
    record Entry(String id, Map<String, String> fields) {
    }

    /**
     * 初始化任务队列（幂等）
     */
    void initTaskQueue();

    /**
     * 任务队列丢失时重建（如 Redis 被清空）
     *
     * @return 发生重建返回 true
     */
    boolean recoverTaskQueue();

    /**
     * 投递任务
     */
    void addTask(Map<String, String> fields);

    /**
     * 阻塞拉取 1 条未消费任务，超时返回空列表
     *
     * @param consumer 消费者名
     * @param blockMillis 阻塞时长(ms)
     */
    List<Entry> pollTask(String consumer, long blockMillis);

    /**
     * 确认任务已处理
     */
    void ackTask(String id);

    /**
     * 认领空闲超过 minIdle 的未确认任务（消费者宕机兜底），无则返回空列表
     */
    List<Entry> reclaimTasks(String consumer, Duration minIdle);

    /**
     * 向结果流追加条目
     */
    void appendResult(String key, Map<String, String> fields);

    /**
     * 设置结果流过期时间
     */
    void expireResult(String key, long ttlSeconds);

    /**
     * 阻塞读取结果流中 fromId 之后的条目（fromId 为 "0" 时从头读取），超时返回空列表
     */
    List<Entry> readResults(String key, String fromId, long count, long blockMillis);

}
