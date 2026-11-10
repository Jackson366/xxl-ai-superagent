package com.xxl.ai.api.business.harness.chat;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 对话流队列 Redis 实现（默认）：任务队列 xxl:ai:chat:tasks 为 Redis Stream + 消费组（跨节点竞争消费、PEL 超时认领），
 * 结果流为按 key 分流的 Redis Stream
 *
 * @author xxl-ai 2026-11-10
 */
@Component
@Profile("!local")
public class RedisChatStreamQueue implements ChatStreamQueue {

    private static final Logger logger = LoggerFactory.getLogger(RedisChatStreamQueue.class);

    /** 任务队列：stream key */
    private static final String TASK_TOPIC = "xxl:ai:chat:tasks";
    /** 任务队列：消费组 group */
    private static final String TASK_CONSUME_GROUP = "default-group";
    /** 任务队列最大长度（近似裁剪） */
    private static final long TASK_STREAM_MAXLEN = 5000;
    /** 单次认领的未确认任务上限 */
    private static final long RECLAIM_BATCH_SIZE = 100;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 建任务消费组（幂等）：createGroup 自带 MKSTREAM，流缺失时自动建流；组已存在时以 BUSYGROUP 收敛
     */
    @Override
    public void initTaskQueue() {
        try {
            stringRedisTemplate.opsForStream().createGroup(TASK_TOPIC, ReadOffset.from("0"), TASK_CONSUME_GROUP);
        } catch (Exception e) {
            // BUSYGROUP：消费组已存在，正常（Spring 包装后该标识可能仅在 cause 链中）
            if (!isBusyGroup(e)) {
                logger.warn("Chat 任务消费组初始化失败, err={}", e.getMessage());
            }
        }
    }

    /**
     * Redis 数据被清空（flush/重启/误删）导致任务流与消费组丢失时重建
     */
    @Override
    public boolean recoverTaskQueue() {
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(TASK_TOPIC))) {
            initTaskQueue();
            return true;
        }
        return false;
    }

    /**
     * 投递任务：XADD 任务队列（近似裁剪到最大长度）
     */
    @Override
    public void addTask(Map<String, String> fields) {
        stringRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(fields).withStreamKey(TASK_TOPIC),
                RedisStreamCommands.XAddOptions
                        .maxlen(TASK_STREAM_MAXLEN)
                        .approximateTrimming(true)
        );
    }

    /**
     * 拉取未确认任务：XREAD GROUP，同组竞争消费，阻塞拉取 1 条
     */
    @Override
    public List<Entry> pollTask(String consumer, long blockMillis) {
        return toEntries(stringRedisTemplate.opsForStream().read(
                Consumer.from(TASK_CONSUME_GROUP, consumer),
                StreamReadOptions.empty()
                        .count(1)
                        .block(Duration.ofMillis(blockMillis)),
                StreamOffset.create(TASK_TOPIC, ReadOffset.lastConsumed())));
    }

    /**
     * 确认任务：XACK，使其从消费组 PEL 中移除
     */
    @Override
    public void ackTask(String id) {
        stringRedisTemplate.opsForStream().acknowledge(TASK_TOPIC, TASK_CONSUME_GROUP, RecordId.of(id));
    }

    /**
     * 认领超时任务：查询 PEL 中空闲超过 minIdle 的消息，XCLAIM 转移到当前消费者
     */
    @Override
    public List<Entry> reclaimTasks(String consumer, Duration minIdle) {
        try {
            PendingMessages pending = stringRedisTemplate.opsForStream().pending(
                    TASK_TOPIC,
                    TASK_CONSUME_GROUP,
                    Range.unbounded(),
                    RECLAIM_BATCH_SIZE,
                    minIdle
            );
            if (pending == null || pending.isEmpty()) {
                return Collections.emptyList();
            }

            RecordId[] ids = new RecordId[pending.size()];
            for (int i = 0; i < pending.size(); i++) {
                ids[i] = pending.get(i).getId();
            }
            return toEntries(stringRedisTemplate.opsForStream().claim(TASK_TOPIC, TASK_CONSUME_GROUP, consumer, minIdle, ids));
        } catch (Exception e) {
            logger.warn("Chat 任务认领失败, err={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 追加结果：XADD 结果流
     */
    @Override
    public void appendResult(String key, Map<String, String> fields) {
        stringRedisTemplate.opsForStream().add(StreamRecords.mapBacked(fields).withStreamKey(key));
    }

    /**
     * 结果流过期：EXPIRE
     */
    @Override
    public void expireResult(String key, long ttlSeconds) {
        stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 读取结果：XREAD 从 fromId 之后阻塞读取
     */
    @Override
    public List<Entry> readResults(String key, String fromId, long count, long blockMillis) {
        return toEntries(stringRedisTemplate.opsForStream().read(
                StreamReadOptions.empty()
                        .count(count)
                        .block(Duration.ofMillis(blockMillis)),
                StreamOffset.create(key, ReadOffset.from(fromId))));
    }

    /**
     * 判断异常链是否来自「消费组已存在」（BUSYGROUP）
     */
    private static boolean isBusyGroup(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (String.valueOf(t.getMessage()).contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stream 记录转队列条目
     */
    private static List<Entry> toEntries(List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        List<Entry> entries = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            Map<String, String> fields = new LinkedHashMap<>();
            record.getValue().forEach((k, v) -> fields.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
            entries.add(new Entry(record.getId().getValue(), fields));
        }
        return entries;
    }

}
