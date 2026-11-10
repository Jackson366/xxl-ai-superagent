package com.xxl.ai.api.framework.util;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进程内缓存存储（local profile）：替代 Redis，过期条目读取时剔除、写入时按阈值批量清理；仅单机，重启即失效
 *
 * @author xxl-ai 2026-11-10
 */
@Component
@Profile("local")
public class LocalCacheStore implements CacheStore {

    /** 清理阈值：条目数超过该值时，写入前批量清理过期条目 */
    private static final int PRUNE_THRESHOLD = 1000;

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public <T> void setObject(String key, T value, long timeout, TimeUnit unit) {
        pruneExpired();
        cache.put(key, new CacheEntry(value, expireAt(timeout, unit)));
    }

    @Override
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        pruneExpired();
        long now = System.currentTimeMillis();
        AtomicBoolean written = new AtomicBoolean(false);
        cache.compute(key, (k, old) -> {
            // 已存在且未过期：保留原值
            if (old != null && !old.expired(now)) {
                return old;
            }
            written.set(true);
            return new CacheEntry(value, expireAt(timeout, unit));
        });
        return written.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getObject(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        // 已过期：剔除并视为不存在
        if (entry.expired(System.currentTimeMillis())) {
            cache.remove(key, entry);
            return null;
        }
        return (T) entry.value();
    }

    @Override
    public boolean delete(String key) {
        return cache.remove(key) != null;
    }

    /**
     * 条目数超过阈值时清理过期条目，避免只写不读的条目（如未使用的验证码）堆积
     */
    private void pruneExpired() {
        if (cache.size() > PRUNE_THRESHOLD) {
            long now = System.currentTimeMillis();
            cache.values().removeIf(entry -> entry.expired(now));
        }
    }

    /**
     * 计算过期时刻（毫秒）
     */
    private static long expireAt(long timeout, TimeUnit unit) {
        return System.currentTimeMillis() + unit.toMillis(timeout);
    }

    /**
     * 缓存条目：值 + 过期时刻
     */
    private record CacheEntry(Object value, long expireAt) {
        boolean expired(long now) {
            return expireAt <= now;
        }
    }

}
